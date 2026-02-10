package domain.models

import java.time.Instant

final case class IdentityResource(
    id: Int,
    enabled: Boolean = true,
    name: String,
    displayName: Option[String] = None,
    description: Option[String] = None,
    required: Boolean = false,
    emphasize: Boolean = false,
    showInDiscoveryDocument: Boolean = true,
    userClaims: List[IdentityResourceClaim] = Nil,
    properties: List[IdentityResourceProperty] = Nil,
    created: Instant = Instant.now(),
    updated: Option[Instant] = None,
    nonEditable: Boolean = false
)
