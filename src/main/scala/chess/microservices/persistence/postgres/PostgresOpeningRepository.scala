package chess.microservices.persistence.postgres

import cats.effect.IO
import cats.syntax.all.*
import chess.microservices.persistence.OpeningRepository
import chess.microservices.persistence.domain.Opening
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

/** PostgreSQL implementation of OpeningRepository
  *
  * Uses Doobie for functional access to PostgreSQL with type-safe SQL queries.
  *
  * @param transactor
  *   Doobie transactor for database operations
  */
class PostgresOpeningRepository(transactor: Transactor[IO]) extends OpeningRepository[IO]:

  /** SQL to create the openings table
    */
  val createTableSql: Update0 = sql"""
    CREATE TABLE IF NOT EXISTS openings (
      eco VARCHAR(10) PRIMARY KEY,
      name VARCHAR(255) NOT NULL,
      moves TEXT NOT NULL,
      fen TEXT NOT NULL,
      variation VARCHAR(255)
    )
  """.update

  /** Create the openings table if it doesn't exist
    */
  def createTable(): IO[Unit] =
    createTableSql.run.transact(transactor).void

  /** Create indexes for efficient querying
    */
  def createIndexes(): IO[Unit] =
    val nameIndexSql = sql"""
      CREATE INDEX IF NOT EXISTS idx_openings_name ON openings(name)
    """.update

    val fenIndexSql = sql"""
      CREATE INDEX IF NOT EXISTS idx_openings_fen ON openings(fen)
    """.update

    (nameIndexSql.run *> fenIndexSql.run).transact(transactor).void

  override def save(opening: Opening): IO[Opening] =
    val upsertSql = sql"""
      INSERT INTO openings (eco, name, moves, fen, variation)
      VALUES (${opening.eco}, ${opening.name}, ${opening.moves}, ${opening.fen}, ${opening.variation})
      ON CONFLICT (eco)
      DO UPDATE SET
        name = EXCLUDED.name,
        moves = EXCLUDED.moves,
        fen = EXCLUDED.fen,
        variation = EXCLUDED.variation
    """.update

    upsertSql.run.transact(transactor).as(opening)

  override def saveAll(openings: Vector[Opening]): IO[Long] =
    val batch = Update[Opening](
      """
      INSERT INTO openings (eco, name, moves, fen, variation)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT (eco)
      DO UPDATE SET
        name = EXCLUDED.name,
        moves = EXCLUDED.moves,
        fen = EXCLUDED.fen,
        variation = EXCLUDED.variation
      """
    )

    batch
      .updateMany(openings)
      .transact(transactor)
      .map(_.toLong)

  override def findByEco(eco: String): IO[Option[Opening]] =
    sql"""
      SELECT eco, name, moves, fen, variation
      FROM openings
      WHERE eco = $eco
    """
      .query[Opening]
      .option
      .transact(transactor)

  override def findByName(name: String): IO[Vector[Opening]] =
    sql"""
      SELECT eco, name, moves, fen, variation
      FROM openings
      WHERE LOWER(name) LIKE LOWER(${"%" + name + "%"})
      ORDER BY name
    """
      .query[Opening]
      .to[Vector]
      .transact(transactor)

  override def findByFen(fen: String): IO[Vector[Opening]] =
    sql"""
      SELECT eco, name, moves, fen, variation
      FROM openings
      WHERE fen = $fen
    """
      .query[Opening]
      .to[Vector]
      .transact(transactor)

  override def findAll(): IO[Vector[Opening]] =
    sql"""
      SELECT eco, name, moves, fen, variation
      FROM openings
      ORDER BY eco
    """
      .query[Opening]
      .to[Vector]
      .transact(transactor)

  override def count(): IO[Long] =
    sql"""
      SELECT COUNT(*) FROM openings
    """
      .query[Long]
      .unique
      .transact(transactor)

  override def deleteAll(): IO[Long] =
    sql"""
      DELETE FROM openings
    """.update.run
      .transact(transactor)
      .map(_.toLong)

  override def findByMoveCount(moveCount: Int): IO[Vector[Opening]] =
    // Simple heuristic: count move numbers by splitting on '.'
    // Filter in application layer for simplicity
    findAll().map { openings =>
      openings.filter { opening =>
        val fullMoves = opening.moves.split('.').length - 1
        fullMoves <= moveCount
      }
    }

object PostgresOpeningRepository:
  /** Doobie Read instance for Opening
    */
  given Read[Opening] = Read[(String, String, String, String, Option[String])].map {
    case (eco, name, moves, fen, variation) =>
      Opening(eco, name, moves, fen, variation)
  }

  /** Doobie Write instance for Opening
    */
  given Write[Opening] =
    Write[(String, String, String, String, Option[String])].contramap { opening =>
      (opening.eco, opening.name, opening.moves, opening.fen, opening.variation)
    }

  /** Create a new PostgresOpeningRepository and initialize the table
    *
    * @param transactor
    *   Doobie transactor
    * @return
    *   New PostgresOpeningRepository instance with table and indexes created
    */
  def create(transactor: Transactor[IO]): IO[PostgresOpeningRepository] =
    val repo = new PostgresOpeningRepository(transactor)
    for
      _ <- repo.createTable()
      _ <- repo.createIndexes()
    yield repo
