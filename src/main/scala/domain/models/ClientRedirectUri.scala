package domain.models

final case class ClientRedirectUri(
    id: Int,
    redirectUri: String,
    clientId: Int,
    client: Option[Client] = None
)
