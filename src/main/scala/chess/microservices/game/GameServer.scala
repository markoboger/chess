package chess.microservices.game

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import chess.AppBindings.given

/** Game Service microservice
  *
  * Runs on port 8081 and provides endpoints for chess game management. Part of the microservices
  * architecture, this service handles all chess-related business logic and state.
  *
  * Endpoints:
  *   - POST /games - Create new game
  *   - GET /games/:id - Get game state
  *   - DELETE /games/:id - Delete game
  *   - POST /games/:id/moves - Make a move
  *   - GET /games/:id/moves - Get move history
  *   - GET /games/:id/fen - Get current FEN
  *   - POST /games/:id/fen - Load FEN position
  *   - GET /health - Health check
  */
object GameServer extends IOApp.Simple:

  private val port = sys.env.getOrElse("PORT", "8081")

  def run: IO[Unit] =
    val routes = GameRoutes.routes

    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromString(port).getOrElse(port"8081"))
      .withHttpApp(routes.orNotFound)
      .build
      .use { server =>
        IO.println(s"Game Service started at ${server.address}") *>
          IO.println("") *>
          IO.println("Available endpoints:") *>
          IO.println("  GET    /health             - Health check") *>
          IO.println("  POST   /games              - Create new game") *>
          IO.println("  GET    /games/:id          - Get game state") *>
          IO.println("  DELETE /games/:id          - Delete game") *>
          IO.println("  POST   /games/:id/moves    - Make a move") *>
          IO.println("  GET    /games/:id/moves    - Get move history") *>
          IO.println("  GET    /games/:id/fen      - Get current FEN") *>
          IO.println("  POST   /games/:id/fen      - Load FEN position") *>
          IO.println("") *>
          IO.println("Press Ctrl+C to stop the server") *>
          IO.never
      }
