package domain.models

import java.time.Instant

final case class IdentityProvider(
    id: Int,
    scheme: String,
    displayName: Option[String] = None,
    enabled: Boolean = true,
    `type`: String,
    properties: String,
    created: Instant = Instant.now(),
    updated: Option[Instant] = None,
    lastAccessed: Option[Instant] = None,
    nonEditable: Boolean = false
)
