package chess.persistence.util

import cats.effect.IO
import cats.implicits.*
import chess.controller.GameController
import chess.controller.io.{FenIO, PgnIO}
import chess.model.Board
import chess.persistence.model.Opening
import chess.persistence.repository.OpeningRepository

import scala.io.Source
import scala.util.{Try, Using}

/** Utility for seeding the opening library from CSV files.
  *
  * Reads chess openings from CSV format and populates the repository. Computes FEN positions by applying the PGN moves
  * to a fresh board.
  */
object OpeningSeeder:

  /** Seeds the opening repository from a CSV resource.
    *
    * @param repository
    *   The opening repository to populate
    * @param resourcePath
    *   Path to the CSV resource (e.g., "/openings/eco-openings.csv")
    * @return
    *   Number of openings successfully seeded
    */
  def seedFromResource(repository: OpeningRepository[IO], resourcePath: String): IO[Int] =
    IO.fromTry(loadCsvFromResource(resourcePath)).flatMap { lines =>
      seedFromCsvLines(repository, lines)
    }

  /** Seeds the opening repository from CSV file path.
    *
    * @param repository
    *   The opening repository to populate
    * @param filePath
    *   Path to the CSV file
    * @return
    *   Number of openings successfully seeded
    */
  def seedFromFile(repository: OpeningRepository[IO], filePath: String): IO[Int] =
    IO.fromTry(loadCsvFromFile(filePath)).flatMap { lines =>
      seedFromCsvLines(repository, lines)
    }

  /** Seeds the repository from parsed CSV lines.
    */
  private def seedFromCsvLines(repository: OpeningRepository[IO], lines: List[String]): IO[Int] =
    val openings = parseOpenings(lines)
    repository.saveAll(openings)

  /** Parses CSV lines into Opening objects.
    *
    * Expected CSV format: eco,name,moves (with header line)
    */
  private def parseOpenings(lines: List[String]): List[Opening] =
    lines
      .drop(1) // Skip header
      .flatMap { line =>
        parseCsvLine(line) match
          case Some((eco, name, moves)) =>
            computeFenAndMoveCount(moves) match
              case Some((fen, moveCount)) =>
                Try(Opening.unsafe(eco, name, moves, fen, moveCount)).toOption
              case None => None
          case None => None
      }

  /** Parses a single CSV line into (eco, name, moves).
    */
  private def parseCsvLine(line: String): Option[(String, String, String)] =
    val parts = line.split(",", 3)
    if parts.length == 3 then Some((parts(0).trim, parts(1).trim, parts(2).trim))
    else None

  /** Computes FEN position and move count by applying PGN moves to initial board.
    *
    * Uses GameController to apply moves and get resulting FEN position.
    */
  private def computeFenAndMoveCount(pgnMoves: String): Option[(String, Int)] =
    Try {
      import chess.AppBindings.given

      val controller = GameController(Board.initial)

      // Split PGN moves and remove move numbers
      val moves = pgnMoves
        .replaceAll("\\d+\\.", "") // Remove move numbers
        .trim
        .split("\\s+")
        .filter(_.nonEmpty)
        .toList

      // Apply each move
      moves.foreach { move =>
        controller.applyPgnMove(move)
      }

      val fen = summon[FenIO].save(controller.board)
      val moveCount = moves.length
      (fen, moveCount)
    }.toOption

  /** Loads CSV from resource file.
    */
  private def loadCsvFromResource(resourcePath: String): Try[List[String]] =
    Try {
      val stream = getClass.getResourceAsStream(resourcePath)
      if stream == null then
        throw new IllegalArgumentException(s"Resource not found: $resourcePath")
      Using.resource(Source.fromInputStream(stream)) { source =>
        source.getLines().toList
      }
    }

  /** Loads CSV from file system.
    */
  private def loadCsvFromFile(filePath: String): Try[List[String]] =
    Using(Source.fromFile(filePath)) { source =>
      source.getLines().toList
    }

  /** Validates opening data quality.
    *
    * @return
    *   List of validation errors
    */
  def validateOpenings(openings: List[Opening]): List[String] =
    val errors = scala.collection.mutable.ListBuffer[String]()

    // Check for duplicate ECO codes
    val ecoGroups = openings.groupBy(_.eco)
    ecoGroups.filter(_._2.length > 1).foreach { case (eco, duplicates) =>
      errors += s"Duplicate ECO code: $eco (${duplicates.length} entries)"
    }

    // Check ECO code format
    openings.foreach { opening =>
      if !opening.eco.matches("[A-E][0-9]{2}") then
        errors += s"Invalid ECO format: ${opening.eco}"
    }

    // Check for empty names
    openings.foreach { opening =>
      if opening.name.isEmpty then
        errors += s"Empty name for ECO: ${opening.eco}"
    }

    // Check move count reasonableness (1-50 moves)
    openings.foreach { opening =>
      if opening.moveCount < 1 || opening.moveCount > 50 then
        errors += s"Unreasonable move count for ${opening.eco}: ${opening.moveCount}"
    }

    errors.toList

  /** Prints opening statistics.
    */
  def printStatistics(openings: List[Opening]): Unit =
    println(s"Total openings: ${openings.length}")
    println(s"ECO range: ${openings.map(_.eco).min} - ${openings.map(_.eco).max}")
    println(s"Average moves: ${openings.map(_.moveCount).sum.toDouble / openings.length}")
    println(s"Move count range: ${openings.map(_.moveCount).min} - ${openings.map(_.moveCount).max}")

    val byCategory = openings.groupBy(_.eco.charAt(0))
    println("\nBy category:")
    byCategory.toSeq.sortBy(_._1).foreach { case (category, ops) =>
      println(s"  $category: ${ops.length} openings")
    }
