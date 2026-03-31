package chess.api.client

import cats.effect.{IO, IOApp}
import org.http4s.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.implicits.*
import chess.api.model.*

/** HTTP client for interacting with the Chess REST API
  *
  * Provides methods to:
  *   - Create and manage games
  *   - Make moves
  *   - Load positions from FEN
  *   - Retrieve game state and history
  */
class ChessClient(baseUri: Uri):

  def createGame(startFen: Option[String] = None): IO[Either[ErrorResponse, CreateGameResponse]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](
        method = Method.POST,
        uri = baseUri / "games"
      ).withEntity(CreateGameRequest(startFen))

      client.run(request).use { resp =>
        if resp.status.isSuccess then resp.as[CreateGameResponse].map(Right(_))
        else resp.as[ErrorResponse].map(Left(_))
      }.handleErrorWith(err => IO.pure(Left(ErrorResponse(err.getMessage))))
    }

  def getGameState(gameId: String): IO[Either[ErrorResponse, GameStateResponse]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](
        method = Method.GET,
        uri = baseUri / "games" / gameId
      )

      client.run(request).use { resp =>
        if resp.status.isSuccess then resp.as[GameStateResponse].map(Right(_))
        else resp.as[ErrorResponse].map(Left(_))
      }.handleErrorWith(err => IO.pure(Left(ErrorResponse(err.getMessage))))
    }

  def deleteGame(gameId: String): IO[Either[ErrorResponse, Unit]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](
        method = Method.DELETE,
        uri = baseUri / "games" / gameId
      )

      client.run(request).use { resp =>
        if resp.status.isSuccess then IO.pure(Right(()))
        else resp.as[ErrorResponse].map(Left(_))
      }
    }

  def makeMove(gameId: String, move: String): IO[Either[ErrorResponse, MakeMoveResponse]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](
        method = Method.POST,
        uri = baseUri / "games" / gameId / "moves"
      ).withEntity(MakeMoveRequest(move))

      client.run(request).use { resp =>
        if resp.status.isSuccess then resp.as[MakeMoveResponse].map(Right(_))
        else resp.as[ErrorResponse].map(Left(_))
      }.handleErrorWith(err => IO.pure(Left(ErrorResponse(err.getMessage))))
    }

  def getMoveHistory(gameId: String): IO[Either[ErrorResponse, MoveHistoryResponse]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](
        method = Method.GET,
        uri = baseUri / "games" / gameId / "moves"
      )

      client.run(request).use { resp =>
        if resp.status.isSuccess then resp.as[MoveHistoryResponse].map(Right(_))
        else resp.as[ErrorResponse].map(Left(_))
      }.handleErrorWith(err => IO.pure(Left(ErrorResponse(err.getMessage))))
    }

  def getFen(gameId: String): IO[Either[ErrorResponse, FenResponse]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](
        method = Method.GET,
        uri = baseUri / "games" / gameId / "fen"
      )

      client.run(request).use { resp =>
        if resp.status.isSuccess then resp.as[FenResponse].map(Right(_))
        else resp.as[ErrorResponse].map(Left(_))
      }.handleErrorWith(err => IO.pure(Left(ErrorResponse(err.getMessage))))
    }

  def loadFen(gameId: String, fen: String): IO[Either[ErrorResponse, LoadFenResponse]] =
    EmberClientBuilder.default[IO].build.use { client =>
      val request = Request[IO](
        method = Method.POST,
        uri = baseUri / "games" / gameId / "fen"
      ).withEntity(LoadFenRequest(fen))

      client.run(request).use { resp =>
        if resp.status.isSuccess then resp.as[LoadFenResponse].map(Right(_))
        else resp.as[ErrorResponse].map(Left(_))
      }.handleErrorWith(err => IO.pure(Left(ErrorResponse(err.getMessage))))
    }

/** Example client demonstrating the Chess REST API
  *
  * Plays Scholar's Mate as a demonstration:
  *   1. e4 e5
  *   2. Bc4 Nc6
  *   3. Qh5 Nf6
  *   4. Qxf7# 1-0
  */
object ChessClientExample extends IOApp.Simple:

  def run: IO[Unit] =
    val client = ChessClient(uri"http://localhost:8080")

    for
      _ <- IO.println("=== Chess REST API Client Demo ===\n")

      // Create a new game
      _ <- IO.println("Creating a new game...")
      createResp <- client.createGame()
      gameId <- createResp match
        case Right(resp) =>
          IO.println(s"Game created! ID: ${resp.gameId}") *>
            IO.println(s"Starting position: ${resp.fen}\n") *>
            IO.pure(resp.gameId)
        case Left(err) =>
          IO.raiseError(new RuntimeException(s"Failed to create game: ${err.error}"))

      // Play Scholar's Mate
      _ <- IO.println("Playing Scholar's Mate...\n")
      moves = List("e4", "e5", "Bc4", "Nc6", "Qh5", "Nf6", "Qxf7")

      _ <- moves.foldLeft(IO.pure(1)) { (ioMoveNum, move) =>
        ioMoveNum.flatMap { moveNum =>
          client.makeMove(gameId, move).flatMap {
            case Right(resp) =>
              val eventStr = resp.event.map(e => s" ($e)").getOrElse("")
              IO.println(s"Move $moveNum: $move$eventStr") *>
                IO.println(s"  New position: ${resp.fen}") *>
                IO.pure(moveNum + 1)
            case Left(err) =>
              IO.println(s"Error making move '$move': ${err.error}") *>
                IO.pure(moveNum + 1)
          }
        }
      }

      // Get final game state
      _ <- IO.println("\nFinal game state:")
      stateResp <- client.getGameState(gameId)
      _ <- stateResp match
        case Right(state) =>
          IO.println(s"Status: ${state.status}") *>
            IO.println(s"FEN: ${state.fen}") *>
            IO.println(s"PGN: ${state.pgn}")
        case Left(err) =>
          IO.println(s"Error getting game state: ${err.error}")

      // Get move history
      _ <- IO.println("\nMove history:")
      histResp <- client.getMoveHistory(gameId)
      _ <- histResp match
        case Right(hist) =>
          IO.println(hist.moves.mkString(", "))
        case Left(err) =>
          IO.println(s"Error getting move history: ${err.error}")

      _ <- IO.println("\n=== Demo complete ===")
    yield ()
