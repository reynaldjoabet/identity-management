package domain.models

// CREATE TABLE client_claims (
//     id serial PRIMARY KEY,
//     type varchar(250) NOT NULL,
//     value varchar(250) NOT NULL,
//     client_id int4 NOT NULL REFERENCES clients(id) ON DELETE CASCADE
// );

final case class ClientClaim(
    id: Int,
    `type`: String,
    value: String,
    clientId: Int,
    client: Option[Client] = None
)
