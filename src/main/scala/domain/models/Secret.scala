package domain.models

abstract class Secret(
    id: Int,
    description: String,
    value: String,
    expiration: Option[java.time.Instant] = None,
    secretType: String = "SharedSecret",
    created: java.time.Instant = java.time.Instant.now()
)

object Secret {
  val DefaultType: String = "SharedSecret"

  enum SecretTypes {
    case SharedSecret
  }
}
