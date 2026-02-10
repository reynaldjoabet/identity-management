package domain.models

//Authorization codes, refresh tokens, reference tokens, and user consent decisions are all stored as persisted grants.
final case class PersistedGrant(
    id: Long,
    key: String,
    `type`: String,
    subjectId: String,
    sessionId: String,
    clientId: String,
    description: Option[String] = None,
    creationTime: java.time.Instant,
    expiration: Option[java.time.Instant] = None,
    consumedTime: Option[java.time.Instant] = None,
    data: String
)
