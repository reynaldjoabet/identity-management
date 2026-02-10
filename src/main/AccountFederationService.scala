object AccountFederationService {}

sealed abstract class AccountFederationService[F[_]] {
  def getAccountByFederation(
      issuer: String,
      subject: String
  ): F[Option[Account]]
  def getOrCreateFederatedUser(
      issuer: String,
      subject: String,
      userName: String
  ): F[Account]
  def createAccountFederation(
      issuer: String,
      subject: String,
      userName: String
  ): F[Unit]

  def hasAccountFederation(userName: String): F[Boolean]
}
