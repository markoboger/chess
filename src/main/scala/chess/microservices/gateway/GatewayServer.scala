package chess.microservices.gateway

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder

object GatewayServer extends IOApp.Simple:

  def run: IO[Unit] =
    EmberClientBuilder.default[IO].build.use { client =>
      val routes = GatewayRoutes.routes(client)

      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(GatewayConfig.port).getOrElse(port"8080"))
        .withHttpApp(routes.orNotFound)
        .build
        .use { server =>
          IO.println(s"API Gateway started at ${server.address}") *>
            IO.println("") *>
            IO.println("Service Discovery:") *>
            IO.println(s"  Game Service: ${GatewayConfig.gameServiceUrl}") *>
            IO.println(s"  UI Service:   ${GatewayConfig.uiServiceUrl}") *>
            IO.println("") *>
            IO.println("Routing:") *>
            IO.println("  GET    /health        - Gateway health check") *>
            IO.println("  /api/games/*          - Proxied to Game Service") *>
            IO.println("  /*                    - Proxied to UI Service") *>
            IO.println("") *>
            IO.println("Press Ctrl+C to stop the server") *>
            IO.never
        }
    }
