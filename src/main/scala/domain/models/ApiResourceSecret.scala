package domain.models

final case class ApiResourceSecret(
    id: Int,
    description: String,
    value: String,
    expiration: Option[java.time.Instant],
    secretType: String,
    created: java.time.Instant,
    apiResourceId: Int,
    apiResource: Option[ApiResource] = None
) extends Secret(id, description, value, expiration, secretType, created)
