package domain.models

import java.time.Instant

final case class ApiScope(
    id: Int,
    enabled: Boolean = true,
    name: String,
    displayName: Option[String] = None,
    description: Option[String] = None,
    required: Boolean = false,
    emphasize: Boolean = false,
    showInDiscoveryDocument: Boolean = true,
    userClaims: List[ApiScopeClaim] = Nil,
    properties: List[ApiScopeProperty] = Nil,
    created: Instant = Instant.now(),
    updated: Option[Instant] = None,
    lastAccessed: Option[Instant] = None,
    nonEditable: Boolean = false
)
