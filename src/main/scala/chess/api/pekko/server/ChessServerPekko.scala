package chess.api.pekko.server

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import chess.AppBindings.given
import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

/** HTTP server for the Chess REST API using Pekko HTTP
  *
  * Runs on port 8081 and provides endpoints for:
  *   - Creating and managing chess games
  *   - Making moves in PGN notation
  *   - Loading/saving positions in FEN notation
  *   - Retrieving move history
  */
object ChessServerPekko:

  def main(args: Array[String]): Unit =
    given ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "ChessServerPekko")
    given ExecutionContextExecutor = summon[ActorSystem[?]].executionContext

    val routes: Route = ChessRoutesPekko.routes
    val bindingFuture = Http().newServerAt("0.0.0.0", 8081).bind(routes)

    bindingFuture.foreach { binding =>
      println(s"Chess REST API server (Pekko HTTP) started at ${binding.localAddress}")
      println("Available endpoints:")
      println("  POST   /games              - Create new game")
      println("  GET    /games/:id          - Get game state")
      println("  DELETE /games/:id          - Delete game")
      println("  POST   /games/:id/moves    - Make a move")
      println("  GET    /games/:id/moves    - Get move history")
      println("  GET    /games/:id/fen      - Get current FEN")
      println("  POST   /games/:id/fen      - Load FEN position")
      println("")
      println("Press ENTER to stop the server...")
    }

    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => summon[ActorSystem[?]].terminate())
