package chess.matchrunner.data.postgres

import cats.effect.IO
import chess.matchrunner.data.MatchRunnerRepository
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchResult, MatchRun}
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant
import java.util.UUID

final class PostgresMatchRunnerRepository(xa: Transactor[IO]) extends MatchRunnerRepository[IO]:

  given Meta[ExperimentStatus] = Meta[String].imap(ExperimentStatus.fromString)(_.toString)
  given Meta[MatchResult] = Meta[String].imap(MatchResult.fromString)(_.toString)

  def createTables(): IO[Unit] =
    for
      _ <- sql"""
      CREATE TABLE IF NOT EXISTS match_experiments (
          id UUID PRIMARY KEY,
          name TEXT NOT NULL,
          description TEXT,
          created_at TIMESTAMPTZ NOT NULL,
          status VARCHAR(20) NOT NULL,
          requested_games INTEGER NOT NULL,
          finished_at TIMESTAMPTZ,
          total_duration_ms BIGINT
        )
      """.update.run.transact(xa)
      _ <- sql"""
        CREATE TABLE IF NOT EXISTS match_runs (
          id UUID PRIMARY KEY,
          experiment_id UUID NOT NULL REFERENCES match_experiments(id) ON DELETE CASCADE,
          chess_game_id TEXT NOT NULL,
          white_strategy TEXT NOT NULL,
          black_strategy TEXT NOT NULL,
          started_at TIMESTAMPTZ NOT NULL,
          finished_at TIMESTAMPTZ,
          result VARCHAR(20),
          winner TEXT,
          move_count INTEGER,
          final_fen TEXT,
          pgn TEXT,
          error_message TEXT,
          duration_ms BIGINT
        )
      """.update.run.transact(xa)
      _ <- sql"ALTER TABLE match_experiments ADD COLUMN IF NOT EXISTS finished_at TIMESTAMPTZ".update.run.transact(xa)
      _ <- sql"ALTER TABLE match_experiments ADD COLUMN IF NOT EXISTS total_duration_ms BIGINT".update.run.transact(xa)
      _ <- sql"ALTER TABLE match_runs ADD COLUMN IF NOT EXISTS duration_ms BIGINT".update.run.transact(xa)
    yield ()

  def createIndexes(): IO[Unit] =
    for
      _ <- sql"CREATE INDEX IF NOT EXISTS idx_match_experiments_status ON match_experiments(status)".update.run.transact(xa)
      _ <- sql"CREATE INDEX IF NOT EXISTS idx_match_runs_experiment_id ON match_runs(experiment_id)".update.run.transact(xa)
      _ <- sql"CREATE INDEX IF NOT EXISTS idx_match_runs_started_at ON match_runs(started_at DESC)".update.run.transact(xa)
    yield ()

  override def saveExperiment(experiment: Experiment): IO[Experiment] =
    sql"""
      INSERT INTO match_experiments (id, name, description, created_at, status, requested_games, finished_at, total_duration_ms)
      VALUES (${experiment.id}, ${experiment.name}, ${experiment.description}, ${experiment.createdAt}, ${experiment.status}, ${experiment.requestedGames}, ${experiment.finishedAt}, ${experiment.totalDurationMs})
      ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        status = EXCLUDED.status,
        requested_games = EXCLUDED.requested_games,
        finished_at = EXCLUDED.finished_at,
        total_duration_ms = EXCLUDED.total_duration_ms
    """.update.run.transact(xa).as(experiment)

  override def findExperiment(id: UUID): IO[Option[Experiment]] =
    sql"""
      SELECT id, name, description, created_at, status, requested_games, finished_at, total_duration_ms
      FROM match_experiments
      WHERE id = $id
    """.query[(UUID, String, Option[String], Instant, ExperimentStatus, Int, Option[Instant], Option[Long])]
      .map { case (eid, name, description, createdAt, status, requestedGames, finishedAt, totalDurationMs) =>
        Experiment(eid, name, description, createdAt, status, requestedGames, finishedAt, totalDurationMs)
      }
      .option
      .transact(xa)

  override def listExperiments(limit: Int = 100, offset: Int = 0): IO[List[Experiment]] =
    sql"""
      SELECT id, name, description, created_at, status, requested_games, finished_at, total_duration_ms
      FROM match_experiments
      ORDER BY created_at DESC
      LIMIT $limit OFFSET $offset
    """.query[(UUID, String, Option[String], Instant, ExperimentStatus, Int, Option[Instant], Option[Long])]
      .map { case (eid, name, description, createdAt, status, requestedGames, finishedAt, totalDurationMs) =>
        Experiment(eid, name, description, createdAt, status, requestedGames, finishedAt, totalDurationMs)
      }
      .to[List]
      .transact(xa)

  override def updateExperimentStatus(id: UUID, status: ExperimentStatus): IO[Boolean] =
    sql"""
      UPDATE match_experiments
      SET status = $status
      WHERE id = $id
    """.update.run.transact(xa).map(_ > 0)

  override def saveMatchRun(run: MatchRun): IO[MatchRun] =
    sql"""
      INSERT INTO match_runs (
        id, experiment_id, chess_game_id, white_strategy, black_strategy, started_at, finished_at,
        result, winner, move_count, final_fen, pgn, error_message, duration_ms
      )
      VALUES (
        ${run.id}, ${run.experimentId}, ${run.chessGameId}, ${run.whiteStrategy}, ${run.blackStrategy}, ${run.startedAt}, ${run.finishedAt},
        ${run.result}, ${run.winner}, ${run.moveCount}, ${run.finalFen}, ${run.pgn}, ${run.errorMessage}, ${run.durationMs}
      )
      ON CONFLICT (id) DO UPDATE SET
        finished_at = EXCLUDED.finished_at,
        result = EXCLUDED.result,
        winner = EXCLUDED.winner,
        move_count = EXCLUDED.move_count,
        final_fen = EXCLUDED.final_fen,
        pgn = EXCLUDED.pgn,
        error_message = EXCLUDED.error_message,
        duration_ms = EXCLUDED.duration_ms
    """.update.run.transact(xa).as(run)

  override def listRuns(experimentId: UUID): IO[List[MatchRun]] =
    sql"""
      SELECT id, experiment_id, chess_game_id, white_strategy, black_strategy, started_at, finished_at,
             result, winner, move_count, final_fen, pgn, error_message, duration_ms
      FROM match_runs
      WHERE experiment_id = $experimentId
      ORDER BY started_at ASC
    """.query[(UUID, UUID, String, String, String, Instant, Option[Instant], Option[MatchResult], Option[String], Option[Int], Option[String], Option[String], Option[String], Option[Long])]
      .map {
        case (id, expId, chessGameId, whiteStrategy, blackStrategy, startedAt, finishedAt, result, winner, moveCount, finalFen, pgn, errorMessage, durationMs) =>
          MatchRun(id, expId, chessGameId, whiteStrategy, blackStrategy, startedAt, finishedAt, result, winner, moveCount, finalFen, pgn, errorMessage, durationMs)
      }
      .to[List]
      .transact(xa)

  override def countRuns(experimentId: UUID): IO[Long] =
    sql"SELECT COUNT(*) FROM match_runs WHERE experiment_id = $experimentId".query[Long].unique.transact(xa)

  override def deleteAll(): IO[Long] =
    for
      runs <- sql"DELETE FROM match_runs".update.run.transact(xa).map(_.toLong)
      experiments <- sql"DELETE FROM match_experiments".update.run.transact(xa).map(_.toLong)
    yield runs + experiments

object PostgresMatchRunnerRepository:
  def create(xa: Transactor[IO]): IO[PostgresMatchRunnerRepository] =
    val repo = new PostgresMatchRunnerRepository(xa)
    for
      _ <- repo.createTables()
      _ <- repo.createIndexes()
    yield repo
