package chess.controller.io.pgn

import chess.controller.io.PgnIO
import scala.util.{Try, Success, Failure}

/** PGN serialization implementation.
  *
  * Formats move lists as standard PGN move text and parses PGN text back into move tokens, stripping comments,
  * variations, and results.
  */
class PgnFileIO extends PgnIO:

  override def save(moves: Vector[String]): String =
    moves
      .grouped(2)
      .zipWithIndex
      .map { case (pair, i) =>
        pair match
          case Vector(w, b) => s"${i + 1}. $w $b"
          case Vector(w)    => s"${i + 1}. $w"
          case _            => ""
      }
      .mkString("\n")

  override def load(input: String): Try[Vector[String]] =
    Try {
      val tokens = input
        .replaceAll("\\{[^}]*\\}", "") // strip comments
        .replaceAll("\\([^)]*\\)", "") // strip variations
        .split("\\s+")
        .map(_.trim)
        .filter(_.nonEmpty)
        .filter(t => !t.matches("\\d+\\.+")) // strip move numbers
        .filter(t => !Set("1-0", "0-1", "1/2-1/2", "*").contains(t)) // strip results
        .toVector

      if (tokens.isEmpty)
        throw new IllegalArgumentException("No moves found in PGN text")

      tokens
    }
