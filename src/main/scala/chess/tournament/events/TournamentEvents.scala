package chess.tournament.events

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import java.time.Instant
import java.util.UUID

/** Event sourcing models for tournament system */
object TournamentEvents:

  /** Base trait for all tournament events */
  sealed trait TournamentEvent:
    def eventId: String
    def timestamp: Instant
    def tournamentId: String

  /** Tournament lifecycle events */
  case class TournamentCreated(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      name: String,
      tournamentType: TournamentType,
      maxPlayers: Int,
      timeControlSeconds: Int,
      createdBy: String
  ) extends TournamentEvent

  case class PlayerRegistered(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      playerId: String,
      playerName: String,
      rating: Option[Int]
  ) extends TournamentEvent

  case class TournamentStarted(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      totalPlayers: Int,
      totalRounds: Int
  ) extends TournamentEvent

  case class TournamentCompleted(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      winnerId: String,
      winnerName: String
  ) extends TournamentEvent

  /** Game events */
  case class GameScheduled(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      gameId: String,
      round: Int,
      whitePlayerId: String,
      blackPlayerId: String
  ) extends TournamentEvent

  case class GameStarted(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      gameId: String,
      whitePlayerId: String,
      blackPlayerId: String,
      initialFen: String
  ) extends TournamentEvent

  case class MoveMade(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      gameId: String,
      playerId: String,
      move: String, // UCI notation
      fen: String,  // Position after move
      moveNumber: Int
  ) extends TournamentEvent

  case class GameCompleted(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      gameId: String,
      result: GameResult,
      winnerId: Option[String],
      totalMoves: Int,
      durationSeconds: Long
  ) extends TournamentEvent

  /** Result types */
  enum GameResult:
    case WhiteWins
    case BlackWins
    case Draw
    case Abandoned

  enum TournamentType:
    case RoundRobin
    case Swiss
    case SingleElimination

  /** Leaderboard update event (derived/aggregated) */
  case class LeaderboardUpdated(
      eventId: String,
      timestamp: Instant,
      tournamentId: String,
      rankings: List[PlayerRanking]
  ) extends TournamentEvent

  case class PlayerRanking(
      playerId: String,
      playerName: String,
      rank: Int,
      points: Double,
      wins: Int,
      draws: Int,
      losses: Int,
      gamesPlayed: Int
  )

  /** JSON encoders/decoders */
  given Encoder[GameResult] = Encoder.encodeString.contramap(_.toString)
  given Decoder[GameResult] = Decoder.decodeString.map(GameResult.valueOf)

  given Encoder[TournamentType] = Encoder.encodeString.contramap(_.toString)
  given Decoder[TournamentType] = Decoder.decodeString.map(TournamentType.valueOf)

  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] = Decoder.decodeString.emapTry(s => scala.util.Try(Instant.parse(s)))

  given Encoder[PlayerRanking] = deriveEncoder[PlayerRanking]
  given Decoder[PlayerRanking] = deriveDecoder[PlayerRanking]

  given Encoder[TournamentCreated] = deriveEncoder[TournamentCreated]
  given Decoder[TournamentCreated] = deriveDecoder[TournamentCreated]

  given Encoder[PlayerRegistered] = deriveEncoder[PlayerRegistered]
  given Decoder[PlayerRegistered] = deriveDecoder[PlayerRegistered]

  given Encoder[TournamentStarted] = deriveEncoder[TournamentStarted]
  given Decoder[TournamentStarted] = deriveDecoder[TournamentStarted]

  given Encoder[TournamentCompleted] = deriveEncoder[TournamentCompleted]
  given Decoder[TournamentCompleted] = deriveDecoder[TournamentCompleted]

  given Encoder[GameScheduled] = deriveEncoder[GameScheduled]
  given Decoder[GameScheduled] = deriveDecoder[GameScheduled]

  given Encoder[GameStarted] = deriveEncoder[GameStarted]
  given Decoder[GameStarted] = deriveDecoder[GameStarted]

  given Encoder[MoveMade] = deriveEncoder[MoveMade]
  given Decoder[MoveMade] = deriveDecoder[MoveMade]

  given Encoder[GameCompleted] = deriveEncoder[GameCompleted]
  given Decoder[GameCompleted] = deriveDecoder[GameCompleted]

  given Encoder[LeaderboardUpdated] = deriveEncoder[LeaderboardUpdated]
  given Decoder[LeaderboardUpdated] = deriveDecoder[LeaderboardUpdated]

  /** Helper to create event IDs */
  def newEventId(): String = UUID.randomUUID().toString

  /** Helper to get current timestamp */
  def now(): Instant = Instant.now()
