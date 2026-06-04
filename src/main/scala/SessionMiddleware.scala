package example
import scala.concurrent.duration.FiniteDuration

import cats.data.{Kleisli, OptionT}
import cats.effect.kernel.Async
import cats.effect.std.{UUIDGen, SecureRandom}
import cats.syntax.all.*
import org.http4s.{HttpRoutes, Request, Response, ResponseCookie, SameSite}
import org.typelevel.ci.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.vault.Key

/** Per-request session, the analogue of `ISession`. Implementations are backed by an [[SessionStore]] (in-memory,
  * distributed cache, Redis, ...).
  */
trait Session[F[_]] {
  def id: String
  def isAvailable: F[Boolean]
  def keys: F[List[String]]
  def get(key: String): F[Option[Array[Byte]]]
  def set(key: String, value: Array[Byte]): F[Unit]
  def remove(key: String): F[Unit]
  def clear: F[Unit]

  /** Loads the session from the backing store (`LoadAsync`). */
  def load: F[Unit]

  /** Persists pending changes (`CommitAsync`). */
  def commit: F[Unit]
}
final case class SessionCookie(
    name: String,
    path: String = "/",
    domain: Option[String] = None,
    httpOnly: Boolean = true,
    secure: Boolean = true,
    sameSite: SameSite = SameSite.Lax
)

final case class SessionOptions(
    cookie: SessionCookie,
    idleTimeout: FiniteDuration,
    ioTimeout: FiniteDuration
)
final class SessionMiddleware[F[_]: SecureRandom](
    store: SessionStore[F],
    protector: DataProtector[F],
    options: SessionOptions,
    val sessionKey: Key[Session[F]]
)(using F: Async[F], loggerFactory: LoggerFactory[F]) {

  private val SessionKeyLength = 36 // "382c74c3-721d-4f34-80e5-57657b6cbc27"
  private val logger = loggerFactory.getLogger

  private final case class Prepared(
      session: Session[F],
      protectedCookie: String,
      isNewSessionKey: Boolean,
      shouldEstablish: F[Boolean]
  )

  def apply(routes: HttpRoutes[F]): HttpRoutes[F] =
    Kleisli { request =>
      OptionT {
        prepare(request).flatMap { prepared =>
          val enriched = request.withAttribute(sessionKey, prepared.session)
          routes(enriched).value.flatMap {
            case Some(response) => finalize(response, prepared).map(_.some)
            // No inner route matched: still commit (a no-op for an untouched
            // session) so behaviour matches the always-runs .NET pipeline.
            case None => commit(prepared.session).as(None)
          }
        }
      }
    }

  private def prepare(request: Request[F]): F[Prepared] = {
    val cookieValue =
      request.cookies.find(_.name == options.cookie.name).map(_.content)

    cookieValue.flatTraverse(protector.unprotect).flatMap { unprotected =>
      val existingKey = unprotected.filter { key =>
        key.trim.nonEmpty && key.length == SessionKeyLength
      }

      existingKey match {
        case Some(key) =>
          // Valid cookie: reuse the session, no cookie needs to be (re)emitted.
          store
            .create(key, options.idleTimeout, options.ioTimeout, F.pure(true), isNewSessionKey = false)
            .map(Prepared(_, protectedCookie = "", isNewSessionKey = false, shouldEstablish = F.pure(false)))

        case None =>
          for {
            key <- UUIDGen.fromSecureRandom[F].randomUUID.map(_.toString)
            protected_ <- protector.protect(key)
            // Set to `true` the first time the store writes the session; read
            // back when deciding whether to emit the cookie.
            established <- F.ref(false)
            tryEstablish = established.set(true).as(true)
            session <- store.create(
              key,
              options.idleTimeout,
              options.ioTimeout,
              tryEstablish,
              isNewSessionKey = true
            )
          } yield Prepared(session, protected_, isNewSessionKey = true, shouldEstablish = established.get)

      }
    }
  }

  private def finalize(response: Response[F], prepared: Prepared): F[Response[F]] =
    prepared.shouldEstablish.map { establish =>
      val withCommit = response.withBodyStream(response.body.onFinalize(commit(prepared.session)))
      if prepared.isNewSessionKey && establish then
        withCommit
          .addCookie(buildCookie(prepared.protectedCookie))
          .putHeaders(
            org.http4s.Header.Raw(ci"Cache-Control", "no-cache,no-store"),
            org.http4s.Header.Raw(ci"Pragma", "no-cache"),
            org.http4s.Header.Raw(ci"Expires", "-1")
          )
      else withCommit
    }

  private def buildCookie(value: String): ResponseCookie =
    ResponseCookie(
      name = options.cookie.name,
      content = value,
      path = Some(options.cookie.path),
      domain = options.cookie.domain,
      httpOnly = options.cookie.httpOnly,
      secure = options.cookie.secure,
      sameSite = Some(options.cookie.sameSite)
    )

  private def commit(session: Session[F]): F[Unit] =
    session.commit.handleErrorWith { ex =>
      logger.error(ex)("Error closing the session.")
    }
}

object SessionMiddleware {
  def make[F[_]: Async: SecureRandom: LoggerFactory](
      store: SessionStore[F],
      protector: DataProtector[F],
      options: SessionOptions
  ): F[SessionMiddleware[F]] =
    Key.newKey[F, Session[F]].map(new SessionMiddleware(store, protector, options, _))
}
