package chess.controller.lichess

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

/** Domain models for Lichess Bot API */
object LichessModels:

  /** Shared types */
  case class Variant(key: String, name: String)
  case class Clock(initial: Long, increment: Long)
  case class Player(id: Option[String], name: String, rating: Option[Int])

  given Decoder[Variant] = deriveDecoder[Variant]
  given Decoder[Clock] = deriveDecoder[Clock]
  given Decoder[Player] = deriveDecoder[Player]

  /** Bot account profile */
  case class BotProfile(
      id: String,
      username: String,
      online: Boolean,
      title: Option[String]
  )

  object BotProfile:
    given Decoder[BotProfile] = deriveDecoder[BotProfile]
    given Encoder[BotProfile] = deriveEncoder[BotProfile]

  /** Game challenge */
  case class Challenge(
      id: String,
      challenger: Challenger,
      timeControl: TimeControl,
      variant: Variant,
      rated: Boolean
  )

  case class Challenger(id: String, name: String, rating: Option[Int])
  case class TimeControl(
      `type`: String,
      limit: Option[Int],
      increment: Option[Int]
  )

  object Challenge:
    given Decoder[Challenger] = deriveDecoder[Challenger]
    given Decoder[TimeControl] = deriveDecoder[TimeControl]
    given Decoder[Challenge] = deriveDecoder[Challenge]

  /** Game state */
  case class GameState(
      `type`: String,
      moves: String,
      wtime: Long,
      btime: Long,
      winc: Long,
      binc: Long,
      status: String,
      winner: Option[String]
  )

  object GameState:
    given Decoder[GameState] = deriveDecoder[GameState]

  /** Game full event (initial state) */
  case class GameFull(
      id: String,
      variant: Variant,
      clock: Option[Clock],
      speed: String,
      white: Player,
      black: Player,
      initialFen: String,
      state: GameState
  )

  object GameFull:
    given Decoder[GameFull] = deriveDecoder[GameFull]

  /** Game event stream event */
  enum GameEvent:
    case GameStartEvent(game: GameStart)
    case ChallengeEvent(challenge: Challenge)
    case ChallengeCanceledEvent(challenge: Challenge)
    case ChallengeDeclinedEvent(challenge: Challenge)

  case class GameStart(
      gameId: String,
      fullId: String,
      color: String,
      fen: String,
      isMyTurn: Boolean
  )

  object GameStart:
    given Decoder[GameStart] = deriveDecoder[GameStart]

  /** Move request */
  case class MoveRequest(move: String)

  object MoveRequest:
    given Encoder[MoveRequest] = deriveEncoder[MoveRequest]

  /** API response wrapper */
  case class ApiResponse(ok: Boolean)

  object ApiResponse:
    given Decoder[ApiResponse] = deriveDecoder[ApiResponse]

  /** Challenge decision */
  enum ChallengeDecision:
    case Accept
    case Decline

  /** Bot configuration */
  case class BotConfig(
      apiToken: String,
      baseUrl: String = "https://lichess.org",
      acceptRated: Boolean = false,
      acceptCasual: Boolean = true,
      acceptVariants: Set[String] = Set("standard"),
      minTimeControl: Int = 60,
      maxTimeControl: Int = 3600,
      maxConcurrentGames: Int = 1
  )
