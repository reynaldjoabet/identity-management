package domain.models

final case class ClientScope(
    id: Int,
    scope: String,
    clientId: Int,
    client: Option[Client] = None
)
