package repositories

//import models.Account

opaque type Account = String

trait AccountRepository[F[_]] {

  def createAccount(account: Account): F[Account]

  def getAccountById(id: Long): F[Option[Account]]

  def getAccountByUsername(username: String): F[Option[Account]]
  def getAccountBySubjectId(subjectId: String): F[Option[Account]]
  def updateAccount(account: Account): F[Account]

  def deleteAccount(id: Long): F[Unit]

  def getExternalAccountsByProvider(provider: String): F[List[Account]]

  def getExternalAcccountIdsByClientId(clientId: String): F[List[String]]
  def getAccountsByExternalAccoundId(
      externalAccountId: String
  ): F[List[Account]]
}
