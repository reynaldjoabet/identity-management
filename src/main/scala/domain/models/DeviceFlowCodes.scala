package domain.models

import java.time.Instant

final case class DeviceFlowCodes(
    deviceCode: String,
    userCode: String,
    subjectId: Option[String] = None,
    sessionId: Option[String] = None,
    clientId: String,
    description: Option[String] = None,
    creationTime: Instant,
    expiration: Option[Instant] = None,
    data: String
)
