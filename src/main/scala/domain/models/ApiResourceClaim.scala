package domain.models

final case class ApiResourceClaim(
    id: Int,
    claimType: String,
    apiResourceId: Int,
    apiResource: ApiResource
) extends UserClaim(id, claimType)
