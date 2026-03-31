package chess.api.server

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import chess.AppBindings.given

/** HTTP server for the Chess REST API
  *
  * Runs on port 8080 and provides endpoints for:
  *   - Creating and managing chess games
  *   - Making moves in PGN notation
  *   - Loading/saving positions in FEN notation
  *   - Retrieving move history
  */
object ChessServer extends IOApp.Simple:

  def run: IO[Unit] =
    val routes = ChessRoutes.routes

    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(routes.orNotFound)
      .build
      .use { server =>
        IO.println(s"Chess REST API server started at ${server.address}") *>
          IO.println("Available endpoints:") *>
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
