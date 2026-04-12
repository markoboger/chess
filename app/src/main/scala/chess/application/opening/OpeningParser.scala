package chess.application.opening

import chess.controller.GameController
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.io.fen.RegexFenParser
import chess.controller.io.pgn.PgnFileIO
import chess.model.Board
import chess.model.Opening

import scala.io.Source
import scala.util.{Try, Using}

/** Classpath-based implementation of [[OpeningIO]].
  *
  * Parses Lichess TSV files and legacy CSV files from classpath resources into [[Opening]] values. Also exposes
  * lower-level parsing utilities used by the `seeder` subproject. For seeding parsed openings into a database, see
  * `chess.seeder.OpeningSeeder`.
  */
object OpeningParser extends OpeningIO:

  // ── In-memory parsing ──────────────────────────────────────────────────────

  /** Parses all five Lichess TSV resources (a–e) into [[Opening]] values.
    *
    * The result is cached for the lifetime of the JVM so repeated construction of opening-aware strategies (for
    * example one cache entry per game in the microservice) does not re-read and re-replay the full book each time.
    */
  def parseLichessOpenings(): List[Opening] = lichessOpeningsCache

  private lazy val lichessOpeningsCache: List[Opening] =
    val tsvFiles = List("/openings/a.tsv", "/openings/b.tsv", "/openings/c.tsv", "/openings/d.tsv", "/openings/e.tsv")
    deduplicate(
      tsvFiles.flatMap { path =>
        loadLinesFromResource(path).toOption.toList.flatten.flatMap(parseTsvLine)
      }
    )

  /** Parses a single TSV resource into [[Opening]] values. */
  def parseTsvResource(resourcePath: String): Try[List[Opening]] =
    loadLinesFromResource(resourcePath).map { lines =>
      deduplicate(lines.flatMap(parseTsvLine))
    }

  /** Parses a CSV resource (legacy format) into [[Opening]] values. */
  def parseCsvResource(resourcePath: String): Try[List[Opening]] =
    loadLinesFromResource(resourcePath).map(parseCsvOpenings)

  // ── TSV parsing (Lichess format: eco\tname\tpgn) ──────────────────────────

  /** Parses a single TSV line into an [[Opening]]. Returns None for header or malformed lines. */
  def parseTsvLine(line: String): Option[Opening] =
    if line.startsWith("eco\t") then return None
    val parts = line.split("\t", 3)
    if parts.length < 3 then return None
    val eco = parts(0).trim
    val name = parts(1).trim
    val pgn = parts(2).trim
    if eco.isEmpty || name.isEmpty || pgn.isEmpty then return None
    computeFenAndMoveCount(pgn).flatMap { case (fen, moveCount) =>
      Try(Opening.unsafe(eco, name, pgn, fen, moveCount)).toOption
    }

  // ── CSV parsing (legacy format: eco,name,moves with header) ───────────────

  def parseCsvOpenings(lines: List[String]): List[Opening] =
    lines.drop(1).flatMap { line =>
      val parts = line.split(",", 3)
      if parts.length == 3 then
        val (eco, name, moves) = (parts(0).trim, parts(1).trim, parts(2).trim)
        if eco.isEmpty || name.isEmpty || moves.isEmpty then None
        else
          computeFenAndMoveCount(moves).flatMap { case (fen, moveCount) =>
            Try(Opening.unsafe(eco, name, moves, fen, moveCount)).toOption
          }
      else None
    }

  // ── FEN computation ────────────────────────────────────────────────────────

  /** Computes the resulting FEN and half-move count by replaying PGN moves on an initial board. */
  def computeFenAndMoveCount(pgnMoves: String): Option[(String, Int)] =
    Try {
      given FenIO = RegexFenParser
      given PgnIO = PgnFileIO()

      val controller = GameController(Board.initial)
      val moves = pgnMoves
        .replaceAll("\\d+\\.", "")
        .trim
        .split("\\s+")
        .filter(_.nonEmpty)
        .toList

      moves.foreach(controller.applyPgnMove)

      val fen = RegexFenParser.save(controller.board)
      (fen, moves.length)
    }.toOption

  // ── Deduplication ─────────────────────────────────────────────────────────

  /** Deduplicates by (eco, name), keeping the shortest line for each key. */
  def deduplicate(openings: List[Opening]): List[Opening] =
    openings
      .groupBy(o => (o.eco, o.name))
      .values
      .map(_.minBy(_.moveCount))
      .toList
      .sortBy(o => (o.eco, o.name))

  // ── Validation & statistics ────────────────────────────────────────────────

  /** Validates opening data quality; returns a list of human-readable error messages. */
  def validateOpenings(openings: List[Opening]): List[String] =
    val errors = scala.collection.mutable.ListBuffer[String]()

    openings.groupBy(o => (o.eco, o.name)).filter(_._2.length > 1).foreach { case ((eco, name), dups) =>
      errors += s"Duplicate (eco, name): ($eco, $name) — ${dups.length} entries"
    }
    openings.foreach { o =>
      if !o.eco.matches("[A-E][0-9]{2}") then errors += s"Invalid ECO format: ${o.eco}"
      if o.name.isEmpty then errors += s"Empty name for ECO: ${o.eco}"
      if o.moveCount < 1 || o.moveCount > 50 then
        errors += s"Unreasonable move count for ${o.eco} ${o.name}: ${o.moveCount}"
    }
    errors.toList

  /** Prints summary statistics for a collection of openings. */
  def printStatistics(openings: List[Opening]): Unit =
    if openings.isEmpty then { println("No openings to report."); return }
    println(s"Total openings: ${openings.length}")
    println(s"Unique ECO codes: ${openings.map(_.eco).distinct.length}")
    println(s"ECO range: ${openings.map(_.eco).min} – ${openings.map(_.eco).max}")
    println(f"Average moves: ${openings.map(_.moveCount).sum.toDouble / openings.length}%.1f")
    println(s"Move count range: ${openings.map(_.moveCount).min} – ${openings.map(_.moveCount).max}")
    val byCategory = openings.groupBy(_.eco.charAt(0))
    println("\nBy ECO category:")
    byCategory.toSeq.sortBy(_._1).foreach { case (cat, ops) =>
      println(s"  $cat: ${ops.length} openings")
    }

  // ── Internal resource loading ──────────────────────────────────────────────

  private def loadLinesFromResource(resourcePath: String): Try[List[String]] =
    Try {
      val stream = getClass.getResourceAsStream(resourcePath)
      if stream == null then throw new IllegalArgumentException(s"Resource not found: $resourcePath")
      Using.resource(Source.fromInputStream(stream, "UTF-8"))(_.getLines().toList)
    }
