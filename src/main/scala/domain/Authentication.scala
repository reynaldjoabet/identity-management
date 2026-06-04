package example.domain

/** Port of `System.Security.Claims.Claim`. */
final case class Claim(`type`: String, value: String)

/* An identity is "authenticated" iff it carries an
 * authentication type
 */
final case class ClaimsIdentity(
    authenticationType: Option[String],
    claims: List[Claim]
) {
  def isAuthenticated: Boolean = authenticationType.isDefined
}
/* the authenticated `HttpContext.User`. */
final case class ClaimsPrincipal(identities: List[ClaimsIdentity]) {
  def claims: List[Claim] = identities.flatMap(_.claims)
  def isAuthenticated: Boolean = identities.exists(_.isAuthenticated)
  def findFirst(claimType: String): Option[Claim] =
    claims.find(_.`type` == claimType)
}

object ClaimsPrincipal {
  val anonymous: ClaimsPrincipal = ClaimsPrincipal(Nil)
}
