package chess.microservices.persistence

import cats.effect.IO
import cats.syntax.all.*
import chess.microservices.persistence.domain.Opening
import chess.controller.GameController
import chess.AppBindings.given

import scala.io.Source

/** Utility for loading chess openings from CSV into a repository
  *
  * Reads ECO (Encyclopaedia of Chess Openings) data from CSV and persists to database.
  */
object OpeningSeeder:

  /** Load openings from CSV file
    *
    * Expected format: eco,name,moves,variation (with header row)
    *
    * @param csvPath
    *   Path to CSV file
    * @return
    *   Vector of Opening objects
    */
  def loadFromCsv(csvPath: String): IO[Vector[Opening]] =
    IO {
      val source = Source.fromResource(csvPath)
      try
        val lines = source.getLines().toVector
        lines.tail // Skip header
          .flatMap { line =>
            parseCsvLine(line) match
              case Some(opening) => Some(opening)
              case None =>
                println(s"Warning: Failed to parse line: $line")
                None
          }
      finally source.close()
    }

  /** Parse a single CSV line into an Opening
    *
    * @param line
    *   CSV line in format: eco,name,moves,variation
    * @return
    *   Some(Opening) if parsing succeeds, None otherwise
    */
  private def parseCsvLine(line: String): Option[Opening] =
    val parts = line.split(',').map(_.trim)
    if parts.length >= 3 then
      val eco = parts(0)
      val name = parts(1)
      val moves = parts(2)
      val variation = if parts.length > 3 && parts(3).nonEmpty then Some(parts(3)) else None

      // Compute FEN by applying moves to the initial board
      val fen = computeFen(moves)

      Some(Opening(eco, name, moves, fen, variation))
    else None

  /** Compute FEN position from PGN moves
    *
    * Uses GameController to apply moves and get resulting FEN.
    *
    * @param moves
    *   PGN moves string (e.g., "1. e4 e5 2. Nf3")
    * @return
    *   FEN string of resulting position
    */
  private def computeFen(moves: String): String =
    val initialFen = summon[chess.controller.io.FenIO].save(chess.model.Board.initial)
    if moves.isEmpty then initialFen
    else
      try
        val controller = GameController(chess.model.Board.initial)
        val pgnMoves = parsePgnMoves(moves)
        pgnMoves.foreach(controller.applyPgnMove)
        controller.getBoardAsFEN
      catch
        case e: Exception =>
          println(s"Warning: Failed to compute FEN for moves: $moves - ${e.getMessage}")
          initialFen

  /** Parse PGN move string into individual moves
    *
    * Example: "1. e4 e5 2. Nf3" -> Vector("e4", "e5", "Nf3")
    *
    * @param pgnString
    *   PGN moves string
    * @return
    *   Vector of individual moves
    */
  private def parsePgnMoves(pgnString: String): Vector[String] =
    pgnString
      .split("\\d+\\.")
      .flatMap(_.trim.split("\\s+"))
      .filter(_.nonEmpty)
      .toVector

  /** Seed a repository with openings from CSV
    *
    * @param repository
    *   The opening repository to populate
    * @param csvPath
    *   Path to CSV file (relative to resources)
    * @return
    *   Number of openings saved
    */
  def seedRepository(repository: OpeningRepository[IO], csvPath: String = "openings/eco-openings.csv"): IO[Long] =
    for
      _        <- IO.println(s"Loading openings from $csvPath...")
      openings <- loadFromCsv(csvPath)
      _        <- IO.println(s"Loaded ${openings.size} openings")
      _        <- IO.println("Saving to database...")
      count    <- repository.saveAll(openings)
      _        <- IO.println(s"Successfully saved $count openings")
    yield count

  /** Seed a repository and verify count
    *
    * @param repository
    *   The opening repository to populate
    * @param csvPath
    *   Path to CSV file
    * @return
    *   Success flag
    */
  def seedAndVerify(repository: OpeningRepository[IO], csvPath: String = "openings/eco-openings.csv"): IO[Boolean] =
    for
      _         <- seedRepository(repository, csvPath)
      count     <- repository.count()
      _         <- IO.println(s"Verification: Database contains $count openings")
      isSuccess <- IO.pure(count > 0)
    yield isSuccess
