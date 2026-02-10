package domain.models

// CREATE TABLE client_secrets (
//     id serial PRIMARY KEY,
//     value varchar(4000) NOT NULL,
//     description varchar(2000),
//     expiration timestamptz,
//     type varchar(250) NOT NULL,
//     created timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
//     client_id int4 NOT NULL REFERENCES clients(id) ON DELETE CASCADE
// );

final case class ClientSecret(
    id: Int,
    value: String,
    secretType: String,
    created: java.time.Instant,
    description: Option[String] = None,
    expiration: Option[java.time.Instant] = None,
    clientId: Int,
    client: Option[Client] = None
) extends Secret(
      id,
      description.getOrElse(""),
      value,
      expiration,
      secretType,
      created
    )
