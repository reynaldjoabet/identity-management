package example

final case class FederatedGatewayOptions(
    port: Int,
    subgraphUrls: Map[String, String]
)
