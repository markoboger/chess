package chess.controller.io.pgn

import chess.controller.io.PgnIO
import _root_.scala.util.Try
import _root_.scala.util.parsing.combinator.RegexParsers

/** PGN serialization implementation using Scala Parser Combinators. */
object CombinatorPgnParser extends RegexParsers with PgnIO {

  // Enable whitespace skipping between tokens
  override def skipWhitespace: Boolean = true

  // --- individual parsers ---------------------------------------------------

  /** A brace comment: {any text} */
  private def comment: Parser[Unit] =
    """\{[^}]*\}""".r ^^^ (())

  /** A variation: (any text) — simplified, does not handle nested parens */
  private def variation: Parser[Unit] =
    """\([^)]*\)""".r ^^^ (())

  /** A move number like "1.", "12.", or "1..." */
  private def moveNumber: Parser[Unit] =
    """\d+\.+""".r ^^^ (())

  /** A result marker: 1-0, 0-1, 1/2-1/2, or * */
  private def result: Parser[Unit] =
    ("1/2-1/2" | "1-0" | "0-1" | "*") ^^^ (())

  /** A move token: any non-whitespace that isn't a comment, variation, move number, or result.
    */
  private def moveToken: Parser[String] =
    """[a-hA-Z][a-h1-8x+#=NBRQKO\-]+|[a-h][1-8]""".r

  /** A single PGN element: either skippable noise or a move token. */
  private def element: Parser[Option[String]] =
    (comment | variation | result | moveNumber) ^^^ None |
      moveToken ^^ (t => Some(t))

  /** The full PGN text: a sequence of elements. */
  private def pgn: Parser[Vector[String]] =
    rep(element) ^^ { elems =>
      elems.flatten.toVector
    }

  // --- public API -----------------------------------------------------------

  override def save(moves: Vector[String]): String =
    moves.zipWithIndex
      .map { case (move, i) =>
        if (i % 2 == 0) s"${i / 2 + 1}. $move" else move
      }
      .mkString(" ")

  override def load(input: String): Try[Vector[String]] =
    _root_.scala.util.Try(parseAll(pgn, input.trim)).flatMap {
      case Success(moves, _) =>
        if (moves.isEmpty)
          _root_.scala.util.Failure(
            new IllegalArgumentException("No moves found in PGN text")
          )
        else
          _root_.scala.util.Success(moves)
      case noSuccess =>
        _root_.scala.util.Failure(
          new IllegalArgumentException(s"PGN parse error: $noSuccess")
        )
    }
}
