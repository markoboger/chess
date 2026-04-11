package chess.matchrunner.domain

import java.time.Instant
import java.util.UUID

final case class Experiment(
    id: UUID,
    name: String,
    description: Option[String],
    createdAt: Instant,
    status: ExperimentStatus,
    requestedGames: Int,
    finishedAt: Option[Instant] = None,
    totalDurationMs: Option[Long] = None
)

object Experiment:
  def create(
      name: String,
      description: Option[String],
      requestedGames: Int,
      status: ExperimentStatus = ExperimentStatus.Draft
  ): Experiment =
    Experiment(
      id = UUID.randomUUID(),
      name = name,
      description = description,
      createdAt = Instant.now(),
      status = status,
      requestedGames = requestedGames
    )
