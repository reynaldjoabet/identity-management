package domain.models

final case class ApiScopeClaim(
    id: Int,
    claimType: String,
    scopeId: Int,
    scope: Option[ApiScope] = None
) extends UserClaim(id, claimType)
