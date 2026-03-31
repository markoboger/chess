package chess.api.server

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.given
import chess.api.model.*
import chess.controller.io.{FenIO, PgnIO}

/** HTTP routes for the Chess REST API
  */
object ChessRoutes:

  def routes(using fenIO: FenIO, pgnIO: PgnIO): HttpRoutes[IO] =
    val gameService = GameService()

    HttpRoutes.of[IO] {

      // POST /games - Create new game
      case req @ POST -> Root / "games" =>
        req.asJsonDecode[CreateGameRequest].flatMap { request =>
          gameService.createGame(request.startFen).flatMap {
            case Right((gameId, fen)) =>
              Ok(CreateGameResponse(gameId, fen))
            case Left(error) =>
              BadRequest(ErrorResponse(error))
          }
        }

      // GET /games/:id - Get game state
      case GET -> Root / "games" / gameId =>
        gameService.getGameState(gameId).flatMap {
          case Some((fen, pgn, status)) =>
            Ok(GameStateResponse(gameId, fen, pgn, status))
          case None =>
            NotFound(ErrorResponse("Game not found"))
        }

      // DELETE /games/:id - Delete game
      case DELETE -> Root / "games" / gameId =>
        gameService.deleteGame(gameId).flatMap { deleted =>
          if deleted then NoContent()
          else NotFound(ErrorResponse("Game not found"))
        }

      // POST /games/:id/moves - Make a move
      case req @ POST -> Root / "games" / gameId / "moves" =>
        req.asJsonDecode[MakeMoveRequest].flatMap { request =>
          gameService.makeMove(gameId, request.move).flatMap {
            case Right((fen, event)) =>
              Ok(MakeMoveResponse(success = true, fen, event))
            case Left(error) =>
              BadRequest(ErrorResponse(error))
          }
        }

      // GET /games/:id/moves - Get move history
      case GET -> Root / "games" / gameId / "moves" =>
        gameService.getMoveHistory(gameId).flatMap {
          case Some(moves) =>
            Ok(MoveHistoryResponse(moves))
          case None =>
            NotFound(ErrorResponse("Game not found"))
        }

      // GET /games/:id/fen - Get current FEN position
      case GET -> Root / "games" / gameId / "fen" =>
        gameService.getFen(gameId).flatMap {
          case Some(fen) =>
            Ok(FenResponse(fen))
          case None =>
            NotFound(ErrorResponse("Game not found"))
        }

      // POST /games/:id/fen - Load position from FEN
      case req @ POST -> Root / "games" / gameId / "fen" =>
        req.asJsonDecode[LoadFenRequest].flatMap { request =>
          gameService.loadFen(gameId, request.fen).flatMap {
            case Right(fen) =>
              Ok(LoadFenResponse(success = true, fen))
            case Left(error) =>
              BadRequest(ErrorResponse(error))
          }
        }
    }
