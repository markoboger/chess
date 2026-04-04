package chess.microservices.gateway

import cats.effect.IO
import cats.syntax.option.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.client.Client
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.given
import chess.microservices.shared.HealthResponse

object GatewayRoutes:

  def routes(client: Client[IO]): HttpRoutes[IO] =
    val proxy = ServiceProxy(client)

    HttpRoutes.of[IO] {

      case GET -> Root / "health" =>
        Ok(HealthResponse("ok", "api-gateway"))

      case req @ (POST | GET | DELETE) -> "api" /: "games" /: rest =>
        val path = s"/games/$rest"
        val targetUri = proxy.buildTargetUri(
          GatewayConfig.gameServiceUrl,
          path,
          req.uri.query.renderString.some.filter(_.nonEmpty)
        )
        proxy.proxy(req, targetUri)

      case req @ GET -> Root =>
        val targetUri = proxy.buildTargetUri(GatewayConfig.uiServiceUrl, "/")
        proxy.proxy(req, targetUri)

      case req @ GET -> rest =>
        val targetUri = proxy.buildTargetUri(
          GatewayConfig.uiServiceUrl,
          rest.toString,
          req.uri.query.renderString.some.filter(_.nonEmpty)
        )
        proxy.proxy(req, targetUri)
    }
