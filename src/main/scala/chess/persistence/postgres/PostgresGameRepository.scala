package chess.persistence.postgres

import cats.effect.IO
import cats.implicits.*
import chess.persistence.model.PersistedGame
import chess.persistence.GameRepository
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant
import java.util.UUID

/** PostgreSQL implementation of GameRepository using Doobie.
  *
  * @param xa
  *   Doobie transactor for database connections
  */
class PostgresGameRepository(xa: Transactor[IO]) extends GameRepository[IO]:

  /** Ensures the games table exists with proper schema. */
  def createTable(): IO[Unit] =
    sql"""
      CREATE TABLE IF NOT EXISTS games (
        id UUID PRIMARY KEY,
        fen_history TEXT[] NOT NULL,
        pgn_moves TEXT[] NOT NULL,
        current_turn VARCHAR(10) NOT NULL,
        status VARCHAR(50) NOT NULL,
        result VARCHAR(10),
        opening_eco VARCHAR(3),
        opening_name VARCHAR(255),
        created_at TIMESTAMP NOT NULL,
        updated_at TIMESTAMP NOT NULL
      )
    """.update.run.transact(xa).void

  /** Creates indexes for efficient querying. */
  def createIndexes(): IO[Unit] =
    for
      _ <- sql"CREATE INDEX IF NOT EXISTS idx_games_status ON games(status)".update.run.transact(xa)
      _ <- sql"CREATE INDEX IF NOT EXISTS idx_games_opening_eco ON games(opening_eco)".update.run.transact(xa)
      _ <- sql"CREATE INDEX IF NOT EXISTS idx_games_created_at ON games(created_at DESC)".update.run.transact(xa)
    yield ()

  override def save(game: PersistedGame): IO[PersistedGame] =
    val updatedGame = game.copy(updatedAt = Instant.now())
    sql"""
      INSERT INTO games (id, fen_history, pgn_moves, current_turn, status, result, opening_eco, opening_name, created_at, updated_at)
      VALUES (${updatedGame.id}, ${updatedGame.fenHistory.toArray}, ${updatedGame.pgnMoves.toArray},
              ${updatedGame.currentTurn}, ${updatedGame.status}, ${updatedGame.result},
              ${updatedGame.openingEco}, ${updatedGame.openingName}, ${updatedGame.createdAt}, ${updatedGame.updatedAt})
      ON CONFLICT (id) DO UPDATE SET
        fen_history = EXCLUDED.fen_history,
        pgn_moves = EXCLUDED.pgn_moves,
        current_turn = EXCLUDED.current_turn,
        status = EXCLUDED.status,
        result = EXCLUDED.result,
        opening_eco = EXCLUDED.opening_eco,
        opening_name = EXCLUDED.opening_name,
        updated_at = EXCLUDED.updated_at
    """.update.run.transact(xa).as(updatedGame)

  override def findById(id: UUID): IO[Option[PersistedGame]] =
    sql"""
      SELECT id, fen_history, pgn_moves, current_turn, status, result, opening_eco, opening_name, created_at, updated_at
      FROM games
      WHERE id = $id
    """.query[PersistedGame].option.transact(xa)

  override def findAll(limit: Int = 100, offset: Int = 0): IO[List[PersistedGame]] =
    sql"""
      SELECT id, fen_history, pgn_moves, current_turn, status, result, opening_eco, opening_name, created_at, updated_at
      FROM games
      ORDER BY created_at DESC
      LIMIT $limit OFFSET $offset
    """.query[PersistedGame].to[List].transact(xa)

  override def findByStatus(status: String, limit: Int = 100): IO[List[PersistedGame]] =
    sql"""
      SELECT id, fen_history, pgn_moves, current_turn, status, result, opening_eco, opening_name, created_at, updated_at
      FROM games
      WHERE status = $status
      ORDER BY created_at DESC
      LIMIT $limit
    """.query[PersistedGame].to[List].transact(xa)

  override def findByOpening(eco: String, limit: Int = 100): IO[List[PersistedGame]] =
    sql"""
      SELECT id, fen_history, pgn_moves, current_turn, status, result, opening_eco, opening_name, created_at, updated_at
      FROM games
      WHERE opening_eco = $eco
      ORDER BY created_at DESC
      LIMIT $limit
    """.query[PersistedGame].to[List].transact(xa)

  override def delete(id: UUID): IO[Boolean] =
    sql"DELETE FROM games WHERE id = $id".update.run
      .transact(xa)
      .map(_ > 0)

  override def count(): IO[Long] =
    sql"SELECT COUNT(*) FROM games"
      .query[Long]
      .unique
      .transact(xa)

  override def deleteAll(): IO[Long] =
    sql"DELETE FROM games".update.run
      .transact(xa)
      .map(_.toLong)

object PostgresGameRepository:
  /** Creates a new repository and initializes the database schema.
    */
  def create(xa: Transactor[IO]): IO[PostgresGameRepository] =
    val repo = new PostgresGameRepository(xa)
    for
      _ <- repo.createTable()
      _ <- repo.createIndexes()
    yield repo
