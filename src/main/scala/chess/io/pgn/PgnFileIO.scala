package chess.io.pgn

import chess.io.PgnIO
import scala.util.{Try, Success, Failure}

/** PGN serialization implementation.
  *
  * Formats move lists as standard PGN move text and parses PGN text back into
  * move tokens, stripping comments, variations, and results.
  */
class PgnFileIO extends PgnIO:

  override def save(moves: Vector[String]): String =
    moves.zipWithIndex
      .map { case (move, i) =>
        if (i % 2 == 0) s"${i / 2 + 1}. $move" else move
      }
      .mkString(" ")

  override def load(input: String): Try[Vector[String]] =
    Try {
      val tokens = input
        .replaceAll("\\{[^}]*\\}", "") // strip comments
        .replaceAll("\\([^)]*\\)", "") // strip variations
        .split("\\s+")
        .map(_.trim)
        .filter(_.nonEmpty)
        .filter(t => !t.matches("\\d+\\.+")) // strip move numbers
        .filter(t =>
          !Set("1-0", "0-1", "1/2-1/2", "*").contains(t)
        ) // strip results
        .toVector

      if (tokens.isEmpty)
        throw new IllegalArgumentException("No moves found in PGN text")

      tokens
    }
