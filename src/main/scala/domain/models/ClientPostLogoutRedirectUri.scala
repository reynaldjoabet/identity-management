package domain.models

final case class ClientPostLogoutRedirectUri(
    id: Int,
    postLogoutRedirectUri: String,
    clientId: Int,
    client: Option[Client] = None
)
