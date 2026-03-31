package chess.microservices.shared

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

/** Request to create a new game
  * @param startFen
  *   Optional FEN string for custom starting position
  */
case class CreateGameRequest(startFen: Option[String] = None)

object CreateGameRequest:
  given Decoder[CreateGameRequest] = deriveDecoder
  given Encoder[CreateGameRequest] = deriveEncoder

/** Response when creating a game
  * @param gameId
  *   Unique identifier for the game
  * @param fen
  *   Current FEN position
  */
case class CreateGameResponse(gameId: String, fen: String)

object CreateGameResponse:
  given Decoder[CreateGameResponse] = deriveDecoder
  given Encoder[CreateGameResponse] = deriveEncoder

/** Full game state response
  * @param gameId
  *   Unique identifier for the game
  * @param fen
  *   Current FEN position
  * @param pgn
  *   Move history in PGN format
  * @param status
  *   Game status (in_progress, checkmate, stalemate, etc.)
  */
case class GameStateResponse(gameId: String, fen: String, pgn: String, status: String)

object GameStateResponse:
  given Decoder[GameStateResponse] = deriveDecoder
  given Encoder[GameStateResponse] = deriveEncoder

/** Request to make a move
  * @param move
  *   Move in PGN algebraic notation (e.g., "e4", "Nf3")
  */
case class MakeMoveRequest(move: String)

object MakeMoveRequest:
  given Decoder[MakeMoveRequest] = deriveDecoder
  given Encoder[MakeMoveRequest] = deriveEncoder

/** Response after making a move
  * @param success
  *   Whether the move was successful
  * @param fen
  *   New FEN position after the move
  * @param event
  *   Game event (check, checkmate, stalemate, etc.) if any
  */
case class MakeMoveResponse(success: Boolean, fen: String, event: Option[String])

object MakeMoveResponse:
  given Decoder[MakeMoveResponse] = deriveDecoder
  given Encoder[MakeMoveResponse] = deriveEncoder

/** Response with move history
  * @param moves
  *   List of moves in PGN notation
  */
case class MoveHistoryResponse(moves: Vector[String])

object MoveHistoryResponse:
  given Decoder[MoveHistoryResponse] = deriveDecoder
  given Encoder[MoveHistoryResponse] = deriveEncoder

/** Response with FEN position
  * @param fen
  *   FEN position string
  */
case class FenResponse(fen: String)

object FenResponse:
  given Decoder[FenResponse] = deriveDecoder
  given Encoder[FenResponse] = deriveEncoder

/** Request to load a position from FEN
  * @param fen
  *   FEN position string
  */
case class LoadFenRequest(fen: String)

object LoadFenRequest:
  given Decoder[LoadFenRequest] = deriveDecoder
  given Encoder[LoadFenRequest] = deriveEncoder

/** Response after loading a FEN position
  * @param success
  *   Whether loading was successful
  * @param fen
  *   The loaded FEN position
  */
case class LoadFenResponse(success: Boolean, fen: String)

object LoadFenResponse:
  given Decoder[LoadFenResponse] = deriveDecoder
  given Encoder[LoadFenResponse] = deriveEncoder

/** Error response
  * @param error
  *   Error message
  * @param details
  *   Optional additional details
  */
case class ErrorResponse(error: String, details: Option[String] = None)

object ErrorResponse:
  given Decoder[ErrorResponse] = deriveDecoder
  given Encoder[ErrorResponse] = deriveEncoder

/** Health check response
  * @param status
  *   Health status ("ok" or "error")
  * @param service
  *   Service name
  */
case class HealthResponse(status: String, service: String)

object HealthResponse:
  given Decoder[HealthResponse] = deriveDecoder
  given Encoder[HealthResponse] = deriveEncoder
