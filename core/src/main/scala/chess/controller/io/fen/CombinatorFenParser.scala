package chess.controller.io.fen

import chess.controller.io.FenIO
import chess.model.{Board, CastlingRights, Piece, Role, Color}
import _root_.scala.util.Try
import _root_.scala.util.parsing.combinator.RegexParsers

object CombinatorFenParser extends RegexParsers with FenIO {

  // Disable whitespace skipping — FEN is whitespace-sensitive
  override def skipWhitespace: Boolean = false

  // --- individual parsers ---------------------------------------------------

  private def whitePiece: Parser[Option[Piece]] =
    "P" ^^^ Some(Piece(Role.Pawn, Color.White)) |
      "N" ^^^ Some(Piece(Role.Knight, Color.White)) |
      "B" ^^^ Some(Piece(Role.Bishop, Color.White)) |
      "R" ^^^ Some(Piece(Role.Rook, Color.White)) |
      "Q" ^^^ Some(Piece(Role.Queen, Color.White)) |
      "K" ^^^ Some(Piece(Role.King, Color.White))

  private def blackPiece: Parser[Option[Piece]] =
    "p" ^^^ Some(Piece(Role.Pawn, Color.Black)) |
      "n" ^^^ Some(Piece(Role.Knight, Color.Black)) |
      "b" ^^^ Some(Piece(Role.Bishop, Color.Black)) |
      "r" ^^^ Some(Piece(Role.Rook, Color.Black)) |
      "q" ^^^ Some(Piece(Role.Queen, Color.Black)) |
      "k" ^^^ Some(Piece(Role.King, Color.Black))

  private def piece: Parser[Option[Piece]] = whitePiece | blackPiece

  private def emptySquares: Parser[Vector[Option[Piece]]] =
    "[1-8]".r ^^ { d => Vector.fill(d.toInt)(None) }

  private def rankElement: Parser[Vector[Option[Piece]]] =
    piece ^^ { p => Vector(p) } | emptySquares

  private def rank: Parser[Vector[Option[Piece]]] =
    rep1(rankElement) ^^ { elems =>
      val flat = elems.flatten
      if (flat.length != 8)
        throw new IllegalArgumentException(
          s"FEN rank must have 8 squares, got ${flat.length}"
        )
      flat.toVector
    }

  private def board: Parser[Vector[Vector[Option[Piece]]]] =
    rank ~ repN(7, "/" ~> rank) ^^ { case first ~ rest =>
      (first +: rest).toVector
    }

  /** Optional trailing FEN fields are skipped by the combinator parser; castling, en passant, and clock fields are
    * extracted from the raw string after the board is parsed.
    */
  private def trailingFields: Parser[Unit] =
    opt("\\s+.*".r) ^^^ (())

  private def boardOnly: Parser[Vector[Vector[Option[Piece]]]] =
    board <~ trailingFields

  // --- public API -----------------------------------------------------------

  /** Parses a FEN string and returns a Board wrapped in a Try. Handles all 6 FEN fields; trailing fields (castling, en
    * passant) are optional and default gracefully when absent.
    */
  def parseFEN(input: String): Try[Board] =
    Try(parseAll(boardOnly, input.trim)).flatMap {
      case Success(squares, _) =>
        val parts = input.trim.split("\\s+")
        val activeColorWhite = parts.lift(1).forall(_.equalsIgnoreCase("w"))
        val castlingRights = parts.lift(2).map(FullFen.parseCastling).getOrElse(CastlingRights())
        val lastMove = parts.lift(3).flatMap(FullFen.parseEnPassantTarget(_, activeColorWhite))
        _root_.scala.util.Success(Board(squares, castlingRights = castlingRights, lastMove = lastMove))
      case noSuccess =>
        _root_.scala.util.Failure(
          new IllegalArgumentException(s"FEN parse error: $noSuccess")
        )
    }

  /** Parses a full 6-field FEN string and returns a [[FullFenState]]. Fields 5–6 (halfmove clock, fullmove number)
    * default to 0 and 1 respectively when absent.
    */
  def parseFullFEN(input: String): Try[FullFenState] =
    for
      board <- parseFEN(input)
      parts = input.trim.split("\\s+")
      whiteToMove = parts.lift(1).forall(_.equalsIgnoreCase("w"))
      halfmoveClock = parts.lift(4).flatMap(_.toIntOption).getOrElse(0)
      fullmoveNumber = parts.lift(5).flatMap(_.toIntOption).getOrElse(1)
    yield FullFenState(
      board = board,
      whiteToMove = whiteToMove,
      halfmoveClock = halfmoveClock.max(0),
      fullmoveNumber = fullmoveNumber.max(1)
    )

  /** Converts a Board to FEN notation (board position only). Delegates to [[RegexFenParser.boardToFEN]] because
    * serialization does not benefit from parser combinators.
    */
  def boardToFEN(board: Board): String =
    RegexFenParser.boardToFEN(board)

  // --- FenIO interface ------------------------------------------------------

  override def save(board: Board): String = boardToFEN(board)

  override def load(input: String): Try[Board] = parseFEN(input)
}
