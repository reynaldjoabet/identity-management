package domain.models

final case class ApiScopeProperty(
    id: Int,
    key: String,
    value: String,
    scopeId: Int,
    scope: Option[ApiScope] = None
) extends Property(id, key, value)
