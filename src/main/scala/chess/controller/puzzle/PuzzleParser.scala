package chess.controller.puzzle

import chess.model.Puzzle
import scala.io.Source
import scala.util.Try

object PuzzleParser:

  /** Load puzzles from a classpath resource (e.g. "/puzzle/lichess_small_puzzle.csv"). */
  def fromResource(resourcePath: String): Vector[Puzzle] =
    val stream = getClass.getResourceAsStream(resourcePath)
    if stream == null then return Vector.empty
    val source = Source.fromInputStream(stream, "UTF-8")
    try
      source.getLines()
        .drop(1) // skip header row
        .flatMap(parseLine)
        .toVector
    finally
      source.close()

  /** Parse a single CSV line. Returns None on any parse failure. */
  def parseLine(line: String): Option[Puzzle] =
    // None of the fields in the Lichess puzzle CSV contain quoted commas,
    // so a plain split is safe.
    val f = line.split(",", -1)
    if f.length < 9 then return None
    Try(
      Puzzle(
        id              = f(0).trim,
        fen             = f(1).trim,
        moves           = f(2).trim.split(" ").filter(_.nonEmpty).toList,
        rating          = f(3).trim.toInt,
        ratingDeviation = f(4).trim.toInt,
        popularity      = f(5).trim.toInt,
        nbPlays         = f(6).trim.toInt,
        themes          = f(7).trim.split(" ").filter(_.nonEmpty).toList,
        gameUrl         = f(8).trim,
        openingTags     = if f.length > 9 then
                            f(9).trim.split(" ").filter(_.nonEmpty).toList
                          else Nil
      )
    ).toOption
