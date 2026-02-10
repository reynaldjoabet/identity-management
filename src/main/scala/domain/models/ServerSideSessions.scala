package domain.models
import java.time.Instant

//This table moves session management from a browser cookie to your database. This is vital for security and "Single Sign-Out."
final case class ServerSideSession(
    id: Long,
    key: String,
    scheme: String,
    subjectId: String,
    sessionId: String,
    displayName: String,
    created: Instant,
    renewed: Instant,
    expires: Option[Instant],
    data: String
)
