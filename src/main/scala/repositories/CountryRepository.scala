package repositories

trait CountryRepository[F[_]] {

  def getCountryByCode(code: String): F[Option[String]]

  def getAllCountries(): F[List[(String, String)]]

  def getMfaProviderByCountryId(countryId: String): F[Option[String]]
}
