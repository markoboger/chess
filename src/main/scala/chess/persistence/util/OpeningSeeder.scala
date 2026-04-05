package chess.persistence.util

import cats.effect.IO
import cats.implicits.*
import chess.controller.GameController
import chess.controller.io.{FenIO, PgnIO}
import chess.model.Board
import chess.persistence.model.Opening
import chess.persistence.OpeningRepository

import scala.io.Source
import scala.util.{Try, Using}

/** Utility for seeding the opening library from TSV or CSV files.
  *
  * Reads chess openings and populates the repository. Computes FEN positions by applying the PGN moves to a fresh
  * board.
  */
object OpeningSeeder:

  // ── Public seeding API ─────────────────────────────────────────────────────

  /** Seeds the opening repository from all Lichess TSV resources (a.tsv–e.tsv).
    *
    * @return
    *   Number of openings successfully seeded
    */
  def seedLichessOpenings(repository: OpeningRepository[IO]): IO[Int] =
    val tsvFiles = List("/openings/a.tsv", "/openings/b.tsv", "/openings/c.tsv", "/openings/d.tsv", "/openings/e.tsv")
    for
      allOpenings <- IO {
        deduplicate(
          tsvFiles.flatMap { path =>
            loadLinesFromResource(path).toOption.toList.flatten.flatMap(parseTsvLine)
          }
        )
      }
      count <-
        if allOpenings.isEmpty then IO.pure(0)
        else repository.saveAll(allOpenings)
    yield count

  /** Seeds the opening repository from a single TSV resource.
    */
  def seedFromTsvResource(repository: OpeningRepository[IO], resourcePath: String): IO[Int] =
    IO.fromTry(loadLinesFromResource(resourcePath)).flatMap { lines =>
      val openings = deduplicate(lines.flatMap(parseTsvLine))
      if openings.isEmpty then IO.pure(0)
      else repository.saveAll(openings)
    }

  /** Seeds the opening repository from the legacy CSV resource.
    */
  def seedFromCsvResource(repository: OpeningRepository[IO], resourcePath: String): IO[Int] =
    IO.fromTry(loadLinesFromResource(resourcePath)).flatMap { lines =>
      val openings = parseCsvOpenings(lines)
      if openings.isEmpty then IO.pure(0)
      else repository.saveAll(openings)
    }

  // ── TSV parsing (Lichess format: eco\tname\tpgn) ──────────────────────────

  /** Parses a single TSV line into an Opening. Skips the header line.
    */
  private[util] def parseTsvLine(line: String): Option[Opening] =
    if line.startsWith("eco\t") then return None // skip header
    val parts = line.split("\t", 3)
    if parts.length < 3 then return None
    val eco = parts(0).trim
    val name = parts(1).trim
    val pgn = parts(2).trim
    if eco.isEmpty || name.isEmpty || pgn.isEmpty then return None
    computeFenAndMoveCount(pgn).flatMap { case (fen, moveCount) =>
      Try(Opening.unsafe(eco, name, pgn, fen, moveCount)).toOption
    }

  // ── CSV parsing (legacy format: eco,name,moves with header) ────────────────

  private def parseCsvOpenings(lines: List[String]): List[Opening] =
    lines
      .drop(1) // Skip header
      .flatMap { line =>
        val parts = line.split(",", 3)
        if parts.length == 3 then
          val (eco, name, moves) = (parts(0).trim, parts(1).trim, parts(2).trim)
          computeFenAndMoveCount(moves).flatMap { case (fen, moveCount) =>
            Try(Opening.unsafe(eco, name, moves, fen, moveCount)).toOption
          }
        else None
      }

  // ── FEN computation ────────────────────────────────────────────────────────

  /** Computes FEN position and move count by applying PGN moves to initial board.
    */
  private[util] def computeFenAndMoveCount(pgnMoves: String): Option[(String, Int)] =
    Try {
      import chess.AppBindings.given

      val controller = GameController(Board.initial)

      // Split PGN moves and remove move numbers
      val moves = pgnMoves
        .replaceAll("\\d+\\.", "") // Remove move numbers like "1." or "12."
        .trim
        .split("\\s+")
        .filter(_.nonEmpty)
        .toList

      // Apply each move
      moves.foreach { move =>
        controller.applyPgnMove(move)
      }

      val fen = summon[FenIO].save(controller.board)
      (fen, moves.length)
    }.toOption

  // ── Resource loading ───────────────────────────────────────────────────────

  private def loadLinesFromResource(resourcePath: String): Try[List[String]] =
    Try {
      val stream = getClass.getResourceAsStream(resourcePath)
      if stream == null then throw new IllegalArgumentException(s"Resource not found: $resourcePath")
      Using.resource(Source.fromInputStream(stream, "UTF-8")) { source =>
        source.getLines().toList
      }
    }

  // ── In-memory parsing (for use without a repository) ───────────────────────

  /** Parses all Lichess TSV resources into Opening objects without saving to a database.
    */
  def parseLichessOpenings(): List[Opening] =
    val tsvFiles = List("/openings/a.tsv", "/openings/b.tsv", "/openings/c.tsv", "/openings/d.tsv", "/openings/e.tsv")
    deduplicate(
      tsvFiles.flatMap { path =>
        loadLinesFromResource(path).toOption.toList.flatten.flatMap(parseTsvLine)
      }
    )

  // ── Deduplication ──────────────────────────────────────────────────────────

  /** Deduplicates openings by (eco, name), keeping the entry with the fewest moves (i.e., the shortest/most canonical
    * line for transpositions).
    */
  private[util] def deduplicate(openings: List[Opening]): List[Opening] =
    openings
      .groupBy(o => (o.eco, o.name))
      .values
      .map(_.minBy(_.moveCount))
      .toList
      .sortBy(o => (o.eco, o.name))

  // ── Validation & statistics ────────────────────────────────────────────────

  /** Validates opening data quality.
    *
    * @return
    *   List of validation errors
    */
  def validateOpenings(openings: List[Opening]): List[String] =
    val errors = scala.collection.mutable.ListBuffer[String]()

    // Check for duplicate (eco, name) pairs
    val groups = openings.groupBy(o => (o.eco, o.name))
    groups.filter(_._2.length > 1).foreach { case ((eco, name), dups) =>
      errors += s"Duplicate (eco, name): ($eco, $name) — ${dups.length} entries"
    }

    // Check ECO code format
    openings.foreach { opening =>
      if !opening.eco.matches("[A-E][0-9]{2}") then errors += s"Invalid ECO format: ${opening.eco}"
    }

    // Check for empty names
    openings.foreach { opening =>
      if opening.name.isEmpty then errors += s"Empty name for ECO: ${opening.eco}"
    }

    // Check move count reasonableness (1-50 moves)
    openings.foreach { opening =>
      if opening.moveCount < 1 || opening.moveCount > 50 then
        errors += s"Unreasonable move count for ${opening.eco} ${opening.name}: ${opening.moveCount}"
    }

    errors.toList

  /** Prints opening statistics.
    */
  def printStatistics(openings: List[Opening]): Unit =
    if openings.isEmpty then
      println("No openings to report.")
      return

    println(s"Total openings: ${openings.length}")
    println(s"Unique ECO codes: ${openings.map(_.eco).distinct.length}")
    println(s"ECO range: ${openings.map(_.eco).min} – ${openings.map(_.eco).max}")
    println(f"Average moves: ${openings.map(_.moveCount).sum.toDouble / openings.length}%.1f")
    println(s"Move count range: ${openings.map(_.moveCount).min} – ${openings.map(_.moveCount).max}")

    val byCategory = openings.groupBy(_.eco.charAt(0))
    println("\nBy ECO category:")
    byCategory.toSeq.sortBy(_._1).foreach { case (category, ops) =>
      println(s"  $category: ${ops.length} openings")
    }
