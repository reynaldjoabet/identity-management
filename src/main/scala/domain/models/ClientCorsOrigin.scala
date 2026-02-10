package domain.models

// CREATE TABLE client_cors_origins (
//     id serial PRIMARY KEY,
//     origin varchar(150) NOT NULL,
//     client_id int4 NOT NULL REFERENCES clients(id) ON DELETE CASCADE
// );
final case class ClientCorsOrigin(
    id: Int,
    origin: String,
    clientId: Int,
    client: Option[Client] = None
)
