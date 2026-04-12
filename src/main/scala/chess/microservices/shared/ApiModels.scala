package chess.microservices.shared

import io.circe.{Decoder, Encoder, HCursor}
import io.circe.generic.semiauto.*
import chess.model.GameSettings

/** Decoders apply case-class defaults when JSON omits keys (e.g. `{}` from the Vue client). */
private def boolOr(c: HCursor, field: String, default: Boolean): Decoder.Result[Boolean] =
  c.get[Boolean](field) match
    case Left(_)    => Right(default)
    case Right(v) => Right(v)

private def strOr(c: HCursor, field: String, default: String): Decoder.Result[String] =
  c.get[String](field) match
    case Left(_)    => Right(default)
    case Right(v) => Right(v)

given Decoder[GameSettings] = Decoder.instance { c =>
  for
    whiteIsHuman <- boolOr(c, "whiteIsHuman", default = true)
    blackIsHuman <- boolOr(c, "blackIsHuman", default = true)
    whiteStrategy <- strOr(c, "whiteStrategy", default = "opening-continuation")
    blackStrategy <- strOr(c, "blackStrategy", default = "opening-continuation")
    clockInitialMs <- c.get[Option[Long]]("clockInitialMs")
    clockIncrementMs <- c.get[Option[Long]]("clockIncrementMs")
    backendAutoplay <- boolOr(c, "backendAutoplay", default = false)
  yield GameSettings(
    whiteIsHuman,
    blackIsHuman,
    whiteStrategy,
    blackStrategy,
    clockInitialMs,
    clockIncrementMs,
    backendAutoplay
  )
}
given Encoder[GameSettings] = io.circe.generic.semiauto.deriveEncoder

/** Request to create a new game
  * @param startFen
  *   Optional FEN string for custom starting position
  * @param settings
  *   Game settings (player types, strategies, clock)
  */
case class CreateGameRequest(startFen: Option[String] = None, settings: GameSettings = GameSettings())

object CreateGameRequest:
  given Decoder[CreateGameRequest] = Decoder.instance { c =>
    val startFenR = c.get[Option[String]]("startFen")
    val settingsR = c.downField("settings").as[GameSettings]
    startFenR.flatMap { sf =>
      settingsR.fold(
        _ => Right(CreateGameRequest(sf, GameSettings())),
        st => Right(CreateGameRequest(sf, st))
      )
    }
  }
  given Encoder[CreateGameRequest] = deriveEncoder

/** Response when creating a game
  * @param gameId
  *   Unique identifier for the game
  * @param fen
  *   Current FEN position
  * @param settings
  *   The game settings that were applied
  */
case class CreateGameResponse(gameId: String, fen: String, settings: GameSettings)

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
  * @param settings
  *   The game settings
  */
case class GameStateResponse(gameId: String, fen: String, pgn: String, status: String, settings: GameSettings)

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

/** Opening lookup response
  * @param eco
  *   ECO code (e.g. "B20")
  * @param name
  *   Opening name (e.g. "Sicilian Defence")
  * @param moves
  *   PGN move sequence (e.g. "1. e4 c5")
  */
case class OpeningLookupResponse(eco: String, name: String, moves: String)

object OpeningLookupResponse:
  given Decoder[OpeningLookupResponse] = deriveDecoder
  given Encoder[OpeningLookupResponse] = deriveEncoder

/** Request to load a game from PGN move text into an existing session. */
case class LoadPgnRequest(pgn: String)

object LoadPgnRequest:
  given Decoder[LoadPgnRequest] = deriveDecoder
  given Encoder[LoadPgnRequest] = deriveEncoder

/** Response after loading a PGN into a session.
  * @param success Whether loading was successful
  * @param fen     The final position after replaying all moves
  * @param moves   Number of half-moves loaded
  */
case class LoadPgnResponse(success: Boolean, fen: String, moves: Int)

object LoadPgnResponse:
  given Decoder[LoadPgnResponse] = deriveDecoder
  given Encoder[LoadPgnResponse] = deriveEncoder

/** Request to compute an AI move for the current position.
  * @param strategy
  *   Strategy identifier: "random", "greedy", "material-balance", "piece-square", "minimax",
  *   "endgame-minimax", "quiescence", or "iterative-deepening"
  */
case class AiMoveRequest(strategy: String)

object AiMoveRequest:
  given Decoder[AiMoveRequest] = deriveDecoder
  given Encoder[AiMoveRequest] = deriveEncoder

/** Response after the backend computes an AI move.
  * @param move
  *   The chosen move in SAN notation, or None if no legal moves exist (game over)
  */
case class AiMoveResponse(move: Option[String])

object AiMoveResponse:
  given Decoder[AiMoveResponse] = deriveDecoder
  given Encoder[AiMoveResponse] = deriveEncoder

/** Summary of a single game session for the session list.
  * @param gameId
  *   Unique identifier for the game
  * @param status
  *   Human-readable game status (e.g. "White to move")
  * @param settings
  *   Game settings
  */
case class GameSummary(gameId: String, status: String, settings: GameSettings)

object GameSummary:
  given Decoder[GameSummary] = deriveDecoder
  given Encoder[GameSummary] = deriveEncoder

/** Response listing all active game sessions. */
case class ListGamesResponse(games: List[GameSummary])

object ListGamesResponse:
  given Decoder[ListGamesResponse] = deriveDecoder
  given Encoder[ListGamesResponse] = deriveEncoder

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
