package example

import java.security.PKCS12Attribute

import java.security.spec
import java.security.interfaces

import java.security.cert
import java.security.Signature
import java.security.Security
import java.security.SecureRandomParameters
import java.security.PublicKey
import java.security.PrivateKey
import java.security.MessageDigest
import java.security.KeyStore
import java.security.KeyStore
import java.security.KeyPairGenerator
import java.security.KeyPair
import java.security.KeyFactorySpi //The provider implementation
import java.security.KeyFactory
import java.security

object Hello extends Greeting with App {
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "hello"
}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import java.time.{Instant, Duration => JavaDuration}

// --- Domain Models / Mocks ---

case class IdentityServerOptions(keyManagement: KeyManagementOptions)
case class KeyManagementOptions(
    allowedSigningAlgorithmNames: Set[String],
    rotationInterval: FiniteDuration,
    propagationTime: FiniteDuration,
    rsaKeySize: Int,
    keyRetirementAge: FiniteDuration
)

case class SigningAlgorithmOptions(
    name: String,
    isRsaKey: Boolean,
    isEcKey: Boolean,
    useX509Certificate: Boolean
)

trait KeyContainer {
  def id: String
  def algorithm: String
  def created: Instant
}

class RsaKeyContainer(rsa: Any, val algorithm: String, val created: Instant)
    extends KeyContainer { val id = "rsa-id" }
class X509KeyContainer(
    rsa: Any,
    val algorithm: String,
    val created: Instant,
    retirement: FiniteDuration,
    issuer: String
) extends KeyContainer { val id = "x509-id" }
class EcKeyContainer(ec: Any, val algorithm: String, val created: Instant)
    extends KeyContainer { val id = "ec-id" }

trait IClock {
  def utcNow: Instant
  def getAge(time: Instant): FiniteDuration = {
    FiniteDuration(
      JavaDuration.between(time, Instant.now()).toMillis,
      MILLISECONDS
    )
  }
}

trait IIssuerNameService {
  def getCurrentAsync(): Future[String]
}

trait CryptoHelper {
  def createRsaSecurityKey(size: Int): Any
  def createECDsaSecurityKey(curveName: String): Any
  def getCurveNameFromSigningAlgorithm(alg: String): String
}

trait ISigningKeyProtector {
  def protect(
      container: KeyContainer
  ): Any // Returns some serialized/protected type
}

trait ISigningKeyStore {
  def storeKeyAsync(key: Any): Future[Unit]
}

trait ILogger {
  def logTrace(message: String, args: Any*): Unit
  def logDebug(message: String, args: Any*): Unit
}

// --- Key Manager ---

class KeyManager(
    options: IdentityServerOptions,
    clock: IClock,
    issuerNameService: IIssuerNameService,
    cryptoHelper: CryptoHelper,
    protector: ISigningKeyProtector,
    store: ISigningKeyStore,
    logger: ILogger
)(implicit ec: ExecutionContext) {

  // Mock implementation of GetCurrentSigningKey logic
  private def getCurrentSigningKey(
      keys: Seq[KeyContainer]
  ): Option[KeyContainer] = keys.headOption

  def isKeyRotationRequired(allKeys: Seq[KeyContainer]): Boolean = {
    if (allKeys.isEmpty) return true

    val groupedKeys = allKeys.groupBy(_.algorithm)

    val requiredAlgorithms = options.keyManagement.allowedSigningAlgorithmNames

    val success = groupedKeys.size == requiredAlgorithms.size &&
      groupedKeys.keys.forall(requiredAlgorithms.contains)

    if (!success) return true

    for ((algName, keysForAlg) <- groupedKeys) {
      getCurrentSigningKey(keysForAlg) match {
        case None =>
          return true

        case Some(activeKeyFromCurrent) =>
          var activeKey = activeKeyFromCurrent

          // rotation is needed if: 1) if there are no other keys next in line (meaning younger).
          // and 2) the current activation key is near expiration (using the delay timeout)

          // get younger keys (which will also filter active key)
          val youngerKeys =
            keysForAlg.filter(_.created.isAfter(activeKey.created))

          if (youngerKeys.nonEmpty) {
            // there are younger keys, then they might also be within the window of the key activation delay
            // so find the youngest one and treat that one as if it's the active key.
            activeKey = youngerKeys.maxBy(_.created)
          }

          // if no younger keys, then check if we're nearing the expiration of active key
          // and see if that's within the window of activation delay.
          val age = clock.getAge(activeKey.created)
          val diff = options.keyManagement.rotationInterval - age
          val needed = diff <= options.keyManagement.propagationTime

          if (!needed) {
            logger.logTrace(
              "Key rotation not required for alg {}; New key expected to be created in {}",
              algName,
              diff - options.keyManagement.propagationTime
            )
          } else {
            logger.logTrace("Key rotation required now for alg {}.", algName)
            return true
          }
      }
    }

    false
  }

  def createAndStoreNewKeyAsync(
      alg: SigningAlgorithmOptions
  ): Future[KeyContainer] = {
    logger.logTrace("Creating new key.")
    val now = clock.utcNow

    val containerFuture: Future[KeyContainer] = if (alg.isRsaKey) {
      val rsa =
        cryptoHelper.createRsaSecurityKey(options.keyManagement.rsaKeySize)

      if (alg.useX509Certificate) {
        issuerNameService.getCurrentAsync().map { iss =>
          new X509KeyContainer(
            rsa,
            alg.name,
            now,
            options.keyManagement.keyRetirementAge,
            iss
          )
        }
      } else {
        Future.successful(new RsaKeyContainer(rsa, alg.name, now))
      }
    } else if (alg.isEcKey) {
      val curveName = cryptoHelper.getCurveNameFromSigningAlgorithm(alg.name)
      val ec = cryptoHelper.createECDsaSecurityKey(curveName)
      // X509 certs don't currently work with EC keys.
      Future.successful(new EcKeyContainer(ec, alg.name, now))
    } else {
      Future.failed(new Exception(s"Invalid alg '${alg.name}'"))
    }

    containerFuture.flatMap { container =>
      val protectedKey = protector.protect(container)
      store.storeKeyAsync(protectedKey).map { _ =>
        logger.logDebug("Created and stored new key with kid {}.", container.id)
        container
      }
    }
  }
}
