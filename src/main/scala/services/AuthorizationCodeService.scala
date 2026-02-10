package services

opaque type AuthorizationCode = String

trait AuthorizationCodeService[F[_]] {

  /** Stores an authorization code and returns the generated code value */
  def storeAuthorizationCode(code: AuthorizationCode): F[String]

  /** Retrieves an authorization code by its code value */
  def getAuthorizationCode(code: String): F[Option[AuthorizationCode]]

  /** Removes an authorization code */
  def removeAuthorizationCode(code: String): F[Unit]
}
