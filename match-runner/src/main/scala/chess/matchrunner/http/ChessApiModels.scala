package chess.matchrunner.http

import chess.model.GameSettings
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

given Encoder[GameSettings] = deriveEncoder

// Use explicit field decoding with fallbacks so the match-runner stays compatible
// with older game-service binaries that predate certain GameSettings fields.
given Decoder[GameSettings] = Decoder.instance { c =>
  for
    whiteIsHuman     <- c.getOrElse[Boolean]("whiteIsHuman")(true)
    blackIsHuman     <- c.getOrElse[Boolean]("blackIsHuman")(true)
    whiteStrategy    <- c.getOrElse[String]("whiteStrategy")("opening-continuation")
    blackStrategy    <- c.getOrElse[String]("blackStrategy")("opening-continuation")
    clockInitialMs   <- c.getOrElse[Option[Long]]("clockInitialMs")(None)
    clockIncrementMs <- c.getOrElse[Option[Long]]("clockIncrementMs")(None)
    backendAutoplay  <- c.getOrElse[Boolean]("backendAutoplay")(false)
  yield GameSettings(
    whiteIsHuman, blackIsHuman,
    whiteStrategy, blackStrategy,
    clockInitialMs, clockIncrementMs,
    backendAutoplay
  )
}

final case class CreateGameRequest(
    startFen: Option[String] = None,
    settings: GameSettings = GameSettings()
)

object CreateGameRequest:
  given Encoder[CreateGameRequest] = deriveEncoder
  given Decoder[CreateGameRequest] = deriveDecoder

final case class CreateGameResponse(gameId: String, fen: String, settings: GameSettings)

object CreateGameResponse:
  given Encoder[CreateGameResponse] = deriveEncoder
  given Decoder[CreateGameResponse] = deriveDecoder

final case class GameStateResponse(
    gameId: String,
    fen: String,
    pgn: String,
    status: String,
    settings: GameSettings
)

object GameStateResponse:
  given Encoder[GameStateResponse] = deriveEncoder
  given Decoder[GameStateResponse] = deriveDecoder

final case class ErrorResponse(error: String, details: Option[String] = None)

object ErrorResponse:
  given Encoder[ErrorResponse] = deriveEncoder
  given Decoder[ErrorResponse] = deriveDecoder

final case class LoadPgnRequest(pgn: String)

object LoadPgnRequest:
  given Encoder[LoadPgnRequest] = deriveEncoder
  given Decoder[LoadPgnRequest] = deriveDecoder

final case class LoadPgnResponse(success: Boolean, fen: String, moves: Int)

object LoadPgnResponse:
  given Encoder[LoadPgnResponse] = deriveEncoder
  given Decoder[LoadPgnResponse] = deriveDecoder
