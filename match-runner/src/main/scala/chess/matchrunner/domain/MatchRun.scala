package chess.matchrunner.domain

import java.time.Instant
import java.util.UUID

final case class MatchRun(
    id: UUID,
    experimentId: UUID,
    chessGameId: String,
    whiteStrategy: String,
    blackStrategy: String,
    startedAt: Instant,
    finishedAt: Option[Instant],
    result: Option[MatchResult],
    winner: Option[String],
    moveCount: Option[Int],
    finalFen: Option[String],
    pgn: Option[String],
    errorMessage: Option[String],
    durationMs: Option[Long] = None
)

object MatchRun:
  def create(
      experimentId: UUID,
      chessGameId: String,
      whiteStrategy: String,
      blackStrategy: String
  ): MatchRun =
    MatchRun(
      id = UUID.randomUUID(),
      experimentId = experimentId,
      chessGameId = chessGameId,
      whiteStrategy = whiteStrategy,
      blackStrategy = blackStrategy,
      startedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
      finishedAt = None,
      result = None,
      winner = None,
      moveCount = None,
      finalFen = None,
      pgn = None,
      errorMessage = None
    )
