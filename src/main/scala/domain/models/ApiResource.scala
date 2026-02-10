package domain.models

import java.time.Instant

final case class ApiResource(
    id: Int,
    enabled: Boolean = true,
    name: String,
    displayName: Option[String] = None,
    description: Option[String] = None,
    allowedAccessTokenSigningAlgorithms: Option[String] = None,
    showInDiscoveryDocument: Boolean = true,
    requireResourceIndicator: Boolean = false,
    secrets: List[ApiResourceSecret] = Nil,
    scopes: List[ApiResourceScope] = Nil,
    userClaims: List[ApiResourceClaim] = Nil,
    properties: List[ApiResourceProperty] = Nil,
    created: Instant = Instant.now(),
    updated: Option[Instant] = None,
    lastAccessed: Option[Instant] = None,
    nonEditable: Boolean = false
)
