package chess.microservices.game

import cats.effect.{IO, IOApp}
import chess.application.game.GameSessionService
import com.comcast.ip4s.*
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import chess.AppBindings.given
import chess.realtime.HttpGameEventPublisher

/** Game Service microservice
  *
  * Runs on port 8081 and provides endpoints for chess game management. Part of the microservices architecture, this
  * service handles all chess-related business logic and state.
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
  private val realtimeServiceUrl = sys.env.getOrElse("REALTIME_SERVICE_URL", "http://localhost:8083")

  def run: IO[Unit] =
    val realtimeUri = Uri.unsafeFromString(realtimeServiceUrl)

    EmberClientBuilder
      .default[IO]
      .build
      .flatMap { client =>
        val publisher = new HttpGameEventPublisher(client, realtimeUri)
        val gameSessions = new GameSessionService(publisher)
        val routes = GameRoutes.routes(gameSessions)

        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(Port.fromString(port).getOrElse(port"8081"))
          .withHttpApp(routes.orNotFound)
          .build
      }
      .use { server =>
        IO.println(s"Game Service started at ${server.address}") *>
          IO.println(s"Realtime publisher target: $realtimeServiceUrl") *>
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
          IO.println("  GET    /openings/lookup    - Look up opening by FEN (?fen=<piece-placement>)") *>
          IO.println("") *>
          IO.println("Press Ctrl+C to stop the server") *>
          IO.never
      }
