package chess.controller.io.pgn

import chess.controller.io.PgnIO
import _root_.scala.util.Try

/** PGN serialization implementation using FastParse. */
object FastParsePgnParser extends PgnIO {

  import fastparse.*
  import fastparse.NoWhitespace.*

  // --- individual parsers ---------------------------------------------------

  /** Whitespace: one or more spaces/tabs/newlines */
  private def ws[$: P]: P[Unit] = P(CharsWhileIn(" \t\r\n", 1))

  /** Optional whitespace */
  private def ows[$: P]: P[Unit] = P(CharsWhileIn(" \t\r\n", 0))

  /** A brace comment: {any text} */
  private def comment[$: P]: P[Unit] = P("{" ~ CharsWhile(_ != '}') ~ "}")

  /** A variation: (any text) — simplified, no nested parens */
  private def variation[$: P]: P[Unit] = P("(" ~ CharsWhile(_ != ')') ~ ")")

  /** A move number like "1.", "12.", or "1..." */
  private def moveNumber[$: P]: P[Unit] = P(
    CharsWhileIn("0-9", 1) ~ CharsWhileIn(".", 1)
  )

  /** A result marker: 1/2-1/2, 1-0, 0-1, or * */
  private def result[$: P]: P[Unit] = P("1/2-1/2" | "1-0" | "0-1" | "*")

  /** A move token character */
  private def moveChar(c: Char): Boolean =
    c.isLetterOrDigit || c == '+' || c == '#' || c == '=' || c == '-' || c == 'x'

  /** A move token: starts with a letter, continues with move chars */
  private def moveToken[$: P]: P[String] = P(
    (CharPred(_.isLetter) ~ CharsWhile(moveChar, 0)).!
  )

  /** Skippable noise: comment, variation, result, or move number */
  private def noise[$: P]: P[Unit] = P(
    comment | variation | result | moveNumber
  )

  /** A single PGN element: noise (skipped) or a move token */
  private def element[$: P]: P[Option[String]] = P(
    noise.map(_ => None) | moveToken.map(t => Some(t))
  )

  /** The full PGN text */
  private def pgn[$: P]: P[Vector[String]] = P(
    ows ~ element.rep(sep = ws) ~ ows ~ End
  ).map { elems =>
    elems.flatten.toVector
  }

  // --- public API -----------------------------------------------------------

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
      fastparse.parse(input.trim, pgn(using _)) match {
        case Parsed.Success(moves, _) =>
          if (moves.isEmpty)
            throw new IllegalArgumentException("No moves found in PGN text")
          moves
        case f: Parsed.Failure =>
          throw new IllegalArgumentException(
            s"PGN parse error: ${f.msg}"
          )
      }
    }
}
