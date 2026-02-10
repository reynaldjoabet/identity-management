package domain.models

final case class Key(
    id: String,
    version: Int,
    created: java.time.Instant,
    use: String,
    algorithm: String,
    isX509Certificate: Boolean,
    dataProtected: Boolean,
    data: String
)
