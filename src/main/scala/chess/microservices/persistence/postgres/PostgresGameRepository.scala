package chess.microservices.persistence.postgres

import cats.effect.IO
import cats.syntax.all.*
import chess.microservices.persistence.GameRepository
import chess.microservices.persistence.domain.PersistedGame
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant

/** PostgreSQL implementation of GameRepository
  *
  * Uses Doobie for functional access to PostgreSQL with type-safe SQL queries.
  *
  * @param transactor
  *   Doobie transactor for database operations
  */
class PostgresGameRepository(transactor: Transactor[IO]) extends GameRepository[IO]:

  /** SQL to create the games table
    *
    * This should be run during application initialization.
    */
  val createTableSql: Update0 = sql"""
    CREATE TABLE IF NOT EXISTS games (
      game_id VARCHAR(255) PRIMARY KEY,
      fen TEXT NOT NULL,
      pgn TEXT NOT NULL,
      status VARCHAR(50) NOT NULL,
      created_at BIGINT NOT NULL,
      updated_at BIGINT NOT NULL
    )
  """.update

  /** Create the games table if it doesn't exist
    */
  def createTable(): IO[Unit] =
    createTableSql.run.transact(transactor).void

  override def save(game: PersistedGame): IO[PersistedGame] =
    val upsertSql = sql"""
      INSERT INTO games (game_id, fen, pgn, status, created_at, updated_at)
      VALUES (${game.gameId}, ${game.fen}, ${game.pgn}, ${game.status},
              ${game.createdAt.toEpochMilli}, ${game.updatedAt.toEpochMilli})
      ON CONFLICT (game_id)
      DO UPDATE SET
        fen = EXCLUDED.fen,
        pgn = EXCLUDED.pgn,
        status = EXCLUDED.status,
        updated_at = EXCLUDED.updated_at
    """.update

    upsertSql.run.transact(transactor).as(game)

  override def findById(gameId: String): IO[Option[PersistedGame]] =
    sql"""
      SELECT game_id, fen, pgn, status, created_at, updated_at
      FROM games
      WHERE game_id = $gameId
    """
      .query[PersistedGame]
      .option
      .transact(transactor)

  override def delete(gameId: String): IO[Boolean] =
    sql"""
      DELETE FROM games WHERE game_id = $gameId
    """.update.run
      .transact(transactor)
      .map(_ > 0)

  override def findAll(): IO[Vector[PersistedGame]] =
    sql"""
      SELECT game_id, fen, pgn, status, created_at, updated_at
      FROM games
      ORDER BY created_at DESC
    """
      .query[PersistedGame]
      .to[Vector]
      .transact(transactor)

  override def findByStatus(status: String): IO[Vector[PersistedGame]] =
    sql"""
      SELECT game_id, fen, pgn, status, created_at, updated_at
      FROM games
      WHERE status = $status
      ORDER BY created_at DESC
    """
      .query[PersistedGame]
      .to[Vector]
      .transact(transactor)

  override def exists(gameId: String): IO[Boolean] =
    sql"""
      SELECT COUNT(*) FROM games WHERE game_id = $gameId
    """
      .query[Long]
      .unique
      .transact(transactor)
      .map(_ > 0)

  override def deleteAll(): IO[Long] =
    sql"""
      DELETE FROM games
    """.update.run
      .transact(transactor)
      .map(_.toLong)

object PostgresGameRepository:
  /** Doobie Read instance for PersistedGame
    *
    * Maps database columns to the domain model.
    */
  given Read[PersistedGame] = Read[(String, String, String, String, Long, Long)].map {
    case (gameId, fen, pgn, status, createdAt, updatedAt) =>
      PersistedGame(
        gameId = gameId,
        fen = fen,
        pgn = pgn,
        status = status,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
      )
  }

  /** Create a new PostgresGameRepository and initialize the table
    *
    * @param transactor
    *   Doobie transactor
    * @return
    *   New PostgresGameRepository instance with table created
    */
  def create(transactor: Transactor[IO]): IO[PostgresGameRepository] =
    val repo = new PostgresGameRepository(transactor)
    repo.createTable().as(repo)
