package domain.models

final case class IdentityResourceClaim(
    id: Int,
    claimType: String,
    identityResourceId: Int,
    identityResource: Option[IdentityResource] = None
) extends UserClaim(id, claimType)
