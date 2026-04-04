package chess.microservices.gateway

import org.http4s.Uri

/** Configuration for the API Gateway service discovery
  */
object GatewayConfig:

  /** URL of the Game Service */
  val gameServiceUrl: Uri = Uri.unsafeFromString(
    sys.env.getOrElse("GAME_SERVICE_URL", "http://localhost:8081")
  )

  /** URL of the UI Service */
  val uiServiceUrl: Uri = Uri.unsafeFromString(
    sys.env.getOrElse("UI_SERVICE_URL", "http://localhost:8082")
  )

  /** Port for the Gateway itself */
  val port: Int = sys.env.getOrElse("PORT", "8080").toInt
