package repositories

trait IdentityProviderRepository[F[_]] {

  def getIdentityProviderById(id: String): F[Option[String]]

  def getAllIdentityProviders(): F[List[(String, String)]]
}
