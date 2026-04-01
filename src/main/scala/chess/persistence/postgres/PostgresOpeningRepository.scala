package chess.persistence.postgres

import cats.effect.IO
import cats.implicits.*
import chess.persistence.model.Opening
import chess.persistence.repository.OpeningRepository
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import scala.util.Random

/** PostgreSQL implementation of OpeningRepository using Doobie.
  *
  * @param xa
  *   Doobie transactor for database connections
  */
class PostgresOpeningRepository(xa: Transactor[IO]) extends OpeningRepository[IO]:

  /** Ensures the openings table exists with proper schema. */
  def createTable(): IO[Unit] =
    sql"""
      CREATE TABLE IF NOT EXISTS openings (
        eco VARCHAR(3) PRIMARY KEY,
        name VARCHAR(255) NOT NULL,
        moves TEXT NOT NULL,
        fen TEXT NOT NULL,
        move_count INTEGER NOT NULL
      )
    """.update.run.transact(xa).void

  /** Creates indexes for efficient querying. */
  def createIndexes(): IO[Unit] =
    for
      _ <- sql"CREATE INDEX IF NOT EXISTS idx_openings_name ON openings(name)".update.run.transact(xa)
      _ <- sql"CREATE INDEX IF NOT EXISTS idx_openings_move_count ON openings(move_count)".update.run.transact(xa)
    yield ()

  override def save(opening: Opening): IO[Opening] =
    sql"""
      INSERT INTO openings (eco, name, moves, fen, move_count)
      VALUES (${opening.eco}, ${opening.name}, ${opening.moves}, ${opening.fen}, ${opening.moveCount})
      ON CONFLICT (eco) DO UPDATE SET
        name = EXCLUDED.name,
        moves = EXCLUDED.moves,
        fen = EXCLUDED.fen,
        move_count = EXCLUDED.move_count
    """.update.run.transact(xa).as(opening)

  override def saveAll(openings: List[Opening]): IO[Int] =
    if openings.isEmpty then IO.pure(0)
    else
      val sql = "INSERT INTO openings (eco, name, moves, fen, move_count) VALUES (?, ?, ?, ?, ?)"
      Update[Opening](sql)
        .updateMany(openings)
        .transact(xa)

  override def findByEco(eco: String): IO[Option[Opening]] =
    sql"""
      SELECT eco, name, moves, fen, move_count
      FROM openings
      WHERE eco = $eco
    """.query[Opening].option.transact(xa)

  override def findByName(nameQuery: String, limit: Int = 50): IO[List[Opening]] =
    sql"""
      SELECT eco, name, moves, fen, move_count
      FROM openings
      WHERE LOWER(name) LIKE ${s"%${nameQuery.toLowerCase}%"}
      ORDER BY eco
      LIMIT $limit
    """.query[Opening].to[List].transact(xa)

  override def findAll(limit: Int = 100, offset: Int = 0): IO[List[Opening]] =
    sql"""
      SELECT eco, name, moves, fen, move_count
      FROM openings
      ORDER BY eco
      LIMIT $limit OFFSET $offset
    """.query[Opening].to[List].transact(xa)

  override def findByMoveCount(maxMoves: Int, limit: Int = 100): IO[List[Opening]] =
    sql"""
      SELECT eco, name, moves, fen, move_count
      FROM openings
      WHERE move_count <= $maxMoves
      ORDER BY move_count
      LIMIT $limit
    """.query[Opening].to[List].transact(xa)

  override def findRandom(): IO[Option[Opening]] =
    for
      count <- count()
      randomOffset = if count > 0 then Random.nextInt(count.toInt) else 0
      opening <- sql"""
        SELECT eco, name, moves, fen, move_count
        FROM openings
        OFFSET $randomOffset
        LIMIT 1
      """.query[Opening].option.transact(xa)
    yield opening

  override def count(): IO[Long] =
    sql"SELECT COUNT(*) FROM openings"
      .query[Long]
      .unique
      .transact(xa)

  override def deleteAll(): IO[Long] =
    sql"DELETE FROM openings"
      .update
      .run
      .transact(xa)
      .map(_.toLong)

object PostgresOpeningRepository:
  /** Creates a new repository and initializes the database schema.
    */
  def create(xa: Transactor[IO]): IO[PostgresOpeningRepository] =
    val repo = new PostgresOpeningRepository(xa)
    for
      _ <- repo.createTable()
      _ <- repo.createIndexes()
    yield repo
