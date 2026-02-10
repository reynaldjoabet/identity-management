package domain.models

final case class ApiResourceProperty(
    id: Int,
    key: String,
    value: String,
    apiResourceId: Int,
    apiResource: Option[ApiResource] = None
) extends Property(id, key, value)
