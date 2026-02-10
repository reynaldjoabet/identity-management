package example.domain

final case class OAuth2TokenResponse(
    access_token: String,
    token_type: String,
    expires_in: Int,
    refresh_token: Option[String],
    scope: Option[String]
)
