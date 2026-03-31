package chess.api.pekko.server

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.model.{StatusCodes, MediaTypes, ContentTypeRange, HttpEntity, ContentTypes}
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller, ToResponseMarshaller}
import org.apache.pekko.http.scaladsl.unmarshalling.{Unmarshaller, FromEntityUnmarshaller}
import io.circe.{Encoder, Decoder, Json}
import io.circe.syntax.*
import io.circe.parser.*
import chess.api.model.*
import chess.controller.io.{FenIO, PgnIO}
import scala.concurrent.ExecutionContext

/** HTTP routes for the Chess REST API using Pekko HTTP
  */
object ChessRoutesPekko:

  // Circe JSON marshallers for Pekko HTTP
  given [A: Encoder]: ToEntityMarshaller[A] =
    Marshaller.stringMarshaller(MediaTypes.`application/json`).compose(_.asJson.noSpaces)

  given [A: Decoder]: FromEntityUnmarshaller[A] = Unmarshaller.stringUnmarshaller.map { str =>
    decode[A](str) match
      case Right(value) => value
      case Left(error)  => throw new RuntimeException(s"JSON decode error: ${error.getMessage}")
  }

  def routes(using fenIO: FenIO, pgnIO: PgnIO, ec: ExecutionContext): Route =
    val gameService = GameServicePekko()

    pathPrefix("games") {
      concat(
        // POST /games - Create new game
        pathEnd {
          post {
            entity(as[CreateGameRequest]) { request =>
              onSuccess(gameService.createGame(request.startFen)) {
                case Right((gameId, fen)) =>
                  complete(StatusCodes.OK, CreateGameResponse(gameId, fen))
                case Left(error) =>
                  complete(StatusCodes.BadRequest, ErrorResponse(error))
              }
            }
          }
        },
        // Routes for /games/:id
        path(Segment) { gameId =>
          concat(
            // GET /games/:id - Get game state
            get {
              onSuccess(gameService.getGameState(gameId)) {
                case Some((fen, pgn, status)) =>
                  complete(StatusCodes.OK, GameStateResponse(gameId, fen, pgn, status))
                case None =>
                  complete(StatusCodes.NotFound, ErrorResponse("Game not found"))
              }
            },
            // DELETE /games/:id - Delete game
            delete {
              onSuccess(gameService.deleteGame(gameId)) { deleted =>
                if deleted then complete(StatusCodes.NoContent)
                else complete(StatusCodes.NotFound, ErrorResponse("Game not found"))
              }
            }
          )
        },
        // Routes for /games/:id/moves
        path(Segment / "moves") { gameId =>
          concat(
            // POST /games/:id/moves - Make a move
            post {
              entity(as[MakeMoveRequest]) { request =>
                onSuccess(gameService.makeMove(gameId, request.move)) {
                  case Right((fen, event)) =>
                    complete(StatusCodes.OK, MakeMoveResponse(success = true, fen, event))
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, ErrorResponse(error))
                }
              }
            },
            // GET /games/:id/moves - Get move history
            get {
              onSuccess(gameService.getMoveHistory(gameId)) {
                case Some(moves) =>
                  complete(StatusCodes.OK, MoveHistoryResponse(moves))
                case None =>
                  complete(StatusCodes.NotFound, ErrorResponse("Game not found"))
              }
            }
          )
        },
        // Routes for /games/:id/fen
        path(Segment / "fen") { gameId =>
          concat(
            // GET /games/:id/fen - Get current FEN position
            get {
              onSuccess(gameService.getFen(gameId)) {
                case Some(fen) =>
                  complete(StatusCodes.OK, FenResponse(fen))
                case None =>
                  complete(StatusCodes.NotFound, ErrorResponse("Game not found"))
              }
            },
            // POST /games/:id/fen - Load position from FEN
            post {
              entity(as[LoadFenRequest]) { request =>
                onSuccess(gameService.loadFen(gameId, request.fen)) {
                  case Right(fen) =>
                    complete(StatusCodes.OK, LoadFenResponse(success = true, fen))
                  case Left(error) =>
                    complete(StatusCodes.BadRequest, ErrorResponse(error))
                }
              }
            }
          )
        }
      )
    }
