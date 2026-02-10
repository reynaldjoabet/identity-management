package domain.models

// CREATE TABLE client_grant_types (
//     id serial PRIMARY KEY,
//     grant_type varchar(250) NOT NULL,
//     client_id int4 NOT NULL REFERENCES clients(id) ON DELETE CASCADE
// );

final case class ClientGrantType(
    id: Int,
    grantType: String,
    clientId: Int,
    client: Option[Client] = None
)
