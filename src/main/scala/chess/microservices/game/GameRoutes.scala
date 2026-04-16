package chess.microservices.game

import cats.effect.IO
import chess.application.game.GameSessionService
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.given
import chess.microservices.shared.*
import chess.controller.io.{FenIO, PgnIO}
import chess.persistence.OpeningRepository

/** HTTP routes for the Game Service microservice
  */
object GameRoutes:

  object FenQueryParam extends QueryParamDecoderMatcher[String]("fen")
  private val GameNotFound = "Game not found"

  def routes(gameSessions: GameSessionService)(using
      fenIO: FenIO,
      pgnIO: PgnIO,
      openingRepo: OpeningRepository[IO]
  ): HttpRoutes[IO] =

    HttpRoutes.of[IO] {

      // Health check endpoint
      case GET -> Root / "health" =>
        Ok(HealthResponse("ok", "game-service"))

      // GET /games - List all active game sessions
      case GET -> Root / "games" =>
        gameSessions.listGames.flatMap { list =>
          val summaries = list.map { case (id, status, settings) =>
            GameSummary(id, status, settings)
          }
          Ok(ListGamesResponse(summaries))
        }

      // POST /games - Create new game
      case req @ POST -> Root / "games" =>
        req.asJsonDecode[CreateGameRequest].flatMap { request =>
          gameSessions.createGame(request.startFen, request.settings).flatMap {
            case Right((gameId, fen)) =>
              Ok(CreateGameResponse(gameId, fen, request.settings))
            case Left(error) =>
              BadRequest(ErrorResponse(error))
          }
        }

      // GET /games/:id - Get game state
      case GET -> Root / "games" / gameId =>
        gameSessions.getGameState(gameId).flatMap {
          case Some((fen, pgn, status, settings)) =>
            Ok(GameStateResponse(gameId, fen, pgn, status, settings))
          case None =>
            NotFound(ErrorResponse(GameNotFound))
        }

      // DELETE /games - Delete all game sessions
      case DELETE -> Root / "games" =>
        gameSessions.deleteAllGames *> NoContent()

      // DELETE /games/:id - Delete game
      case DELETE -> Root / "games" / gameId =>
        gameSessions.deleteGame(gameId).flatMap { deleted =>
          if deleted then NoContent()
          else NotFound(ErrorResponse(GameNotFound))
        }

      // POST /games/:id/moves - Make a move
      case req @ POST -> Root / "games" / gameId / "moves" =>
        req.asJsonDecode[MakeMoveRequest].flatMap { request =>
          gameSessions.makeMove(gameId, request.move).flatMap {
            case Right((fen, event)) =>
              Ok(MakeMoveResponse(success = true, fen, event))
            case Left(error) =>
              BadRequest(ErrorResponse(error))
          }
        }

      // GET /games/:id/moves - Get move history
      case GET -> Root / "games" / gameId / "moves" =>
        gameSessions.getMoveHistory(gameId).flatMap {
          case Some(moves) =>
            Ok(MoveHistoryResponse(moves))
          case None =>
            NotFound(ErrorResponse(GameNotFound))
        }

      // GET /games/:id/fen - Get current FEN position
      case GET -> Root / "games" / gameId / "fen" =>
        gameSessions.getFen(gameId).flatMap {
          case Some(fen) =>
            Ok(FenResponse(fen))
          case None =>
            NotFound(ErrorResponse(GameNotFound))
        }

      // GET /openings/lookup?fen=<piece-placement> - Look up opening by board position
      case GET -> Root / "openings" / "lookup" :? FenQueryParam(fen) =>
        openingRepo.findByFen(fen).flatMap {
          case Some(opening) =>
            Ok(OpeningLookupResponse(opening.eco, opening.name, opening.moves))
          case None =>
            NotFound(ErrorResponse("Opening not found"))
        }

      // POST /games/:id/pgn - Replay a full PGN into an existing session
      case req @ POST -> Root / "games" / gameId / "pgn" =>
        req.asJsonDecode[LoadPgnRequest].flatMap { request =>
          gameSessions.loadPgn(gameId, request.pgn).flatMap {
            case Right((fen, moves)) =>
              Ok(LoadPgnResponse(success = true, fen, moves))
            case Left(error) =>
              BadRequest(ErrorResponse(error))
          }
        }

      // POST /games/:id/fen - Load position from FEN
      case req @ POST -> Root / "games" / gameId / "fen" =>
        req.asJsonDecode[LoadFenRequest].flatMap { request =>
          gameSessions.loadFen(gameId, request.fen).flatMap {
            case Right(fen) =>
              Ok(LoadFenResponse(success = true, fen))
            case Left(error) =>
              BadRequest(ErrorResponse(error))
          }
        }

      // POST /games/:id/ai-move - Compute an AI move using the specified strategy
      case req @ POST -> Root / "games" / gameId / "ai-move" =>
        req.asJsonDecode[AiMoveRequest].flatMap { request =>
          gameSessions.computeAiMove(gameId, request.strategy).flatMap {
            case Right(move) => Ok(AiMoveResponse(move))
            case Left(error) => BadRequest(ErrorResponse(error))
          }
        }
    }
