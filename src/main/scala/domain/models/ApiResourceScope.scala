package domain.models

final case class ApiResourceScope(
    id: Int,
    scope: String,
    apiResourceId: Int,
    apiResource: Option[ApiResource] = None
)
