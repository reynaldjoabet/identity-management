package example
import scala.concurrent.duration.FiniteDuration

trait SessionStore[F[_]] {
  def create(
      sessionKey: String,
      idleTimeout: FiniteDuration,
      ioTimeout: FiniteDuration,
      tryEstablishSession: F[Boolean],
      isNewSessionKey: Boolean
  ): F[Session[F]]

}
