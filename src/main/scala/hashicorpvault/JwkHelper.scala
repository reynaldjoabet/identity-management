package hashicorpvault

import java.nio.charset.StandardCharsets
import java.security.KeyPair

import scala.util.control.NonFatal

import com.nimbusds.jose.jwk.{ECKey, OctetSequenceKey, RSAKey}

/** Parses JWK (JSON Web Key) documents into JCA key material.
  *
  * Replacement for the deprecated Java `JWKHelper`. Every operation is pure and total: a malformed or wrong-type JWK
  * yields a `Left[JwkError]` rather than a thrown exception, so failures are visible in the type and cannot be silently
  * dropped.
  *
  * Callers working in an effect `F[_]` can lift a result with `Sync[F].fromEither(...)`, since [[JwkError]] is a
  * `Throwable`.
  */
object JwkHelper {

  /** Raw symmetric key bytes from an `oct` JWK.
    *
    * This is the safe primitive for symmetric secrets: binary key material is not guaranteed to be valid text, so
    * prefer this over [[buildSecretFromJwk]] unless the secret is known to be a UTF-8 string (e.g. an HMAC passphrase).
    */
  def buildSecretBytesFromJwk(json: String): Either[JwkError, Array[Byte]] =
    guard(JwkError.OctSecret.apply)(OctetSequenceKey.parse(json).toByteArray)

  /** UTF-8 text of the symmetric key in an `oct` JWK.
    *
    * Behaviour-compatible with the legacy `buildSecretFromJwk`. Only meaningful when the secret is genuinely UTF-8
    * text; for arbitrary binary secrets use [[buildSecretBytesFromJwk]], as UTF-8 decoding is lossy.
    */
  def buildSecretFromJwk(json: String): Either[JwkError, String] =
    buildSecretBytesFromJwk(json).map(new String(_, StandardCharsets.UTF_8))

  /** RSA public/private key pair from an `RSA` JWK. */
  def buildRsaKeyPairFromJwk(json: String): Either[JwkError, KeyPair] =
    guard(JwkError.RsaKeyPair.apply)(RSAKey.parse(json).toKeyPair)

  /** Elliptic-curve public/private key pair from an `EC` JWK. */
  def buildEcKeyPairFromJwk(json: String): Either[JwkError, KeyPair] =
    guard(JwkError.EcKeyPair.apply)(ECKey.parse(json).toKeyPair)

  /** Runs a Nimbus parse, converting any non-fatal failure into a [[JwkError]]. The originating JSON is never captured,
    * so secret material cannot leak via the error.
    */
  private def guard[A](wrap: Throwable => JwkError)(thunk: => A): Either[JwkError, A] =
    try Right(thunk)
    catch { case NonFatal(e) => Left(wrap(e)) }

}

/** Failure to turn a JWK document into key material.
  *
  * Extends `Exception` (carrying the underlying Nimbus cause) so a caller can lift a `Left` straight into an effect via
  * `Sync[F].fromEither`. The message deliberately omits the source JSON, since an `oct` JWK contains the secret.
  */
enum JwkError(message: String, cause: Throwable) extends Exception(message, cause) {
  case OctSecret(underlying: Throwable) extends JwkError("Failed to parse oct (symmetric) JWK", underlying)
  case RsaKeyPair(underlying: Throwable) extends JwkError("Failed to parse RSA JWK", underlying)
  case EcKeyPair(underlying: Throwable) extends JwkError("Failed to parse EC JWK", underlying)
}
