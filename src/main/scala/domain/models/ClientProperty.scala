package domain.models

final case class ClientProperty(
    id: Int,
    key: String,
    value: String,
    clientId: Int,
    client: Option[Client] = None
) extends Property(id, key, value)
