package domain.models

final case class IdentityResourceProperty(
    id: Int,
    key: String,
    value: String,
    identityResourceId: Int,
    identityResource: Option[IdentityResource] = None
) extends Property(id, key, value)
