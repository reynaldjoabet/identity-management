package domain.models

final case class PushedAuthorizationRequest(
    id: Long,
    referenceValueHash: String,
    expiresAtUtc: java.time.Instant,
    parameters: String
)
