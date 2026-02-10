package domain.models

final case class ClientIdPRestriction(
    id: Int,
    provider: String,
    clientId: Int,
    client: Option[Client] = None
)
