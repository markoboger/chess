package chess.matchrunner.data

import cats.effect.IO
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchRun}

import java.util.UUID

trait MatchRunnerRepository[F[_]]:
  def saveExperiment(experiment: Experiment): F[Experiment]
  def findExperiment(id: UUID): F[Option[Experiment]]
  def listExperiments(limit: Int = 100, offset: Int = 0): F[List[Experiment]]
  def updateExperimentStatus(id: UUID, status: ExperimentStatus): F[Boolean]

  def saveMatchRun(run: MatchRun): F[MatchRun]
  def listRuns(experimentId: UUID): F[List[MatchRun]]
  def countRuns(experimentId: UUID): F[Long]

  def deleteAll(): F[Long]

object MatchRunnerRepository:
  type IORepo = MatchRunnerRepository[IO]
