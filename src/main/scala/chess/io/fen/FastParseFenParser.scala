package chess.io.fen

import chess.io.FenIO
import chess.model.{Board, Piece, Role, Color}
import _root_.scala.util.Try

object FastParseFenParser extends FenIO {

  import fastparse.*
  import fastparse.NoWhitespace.*

  // --- individual parsers ---------------------------------------------------

  private def whitePiece[$: P]: P[Option[Piece]] = P(
    "P".map(_ => Some(Piece(Role.Pawn, Color.White))) |
      "N".map(_ => Some(Piece(Role.Knight, Color.White))) |
      "B".map(_ => Some(Piece(Role.Bishop, Color.White))) |
      "R".map(_ => Some(Piece(Role.Rook, Color.White))) |
      "Q".map(_ => Some(Piece(Role.Queen, Color.White))) |
      "K".map(_ => Some(Piece(Role.King, Color.White)))
  )

  private def blackPiece[$: P]: P[Option[Piece]] = P(
    "p".map(_ => Some(Piece(Role.Pawn, Color.Black))) |
      "n".map(_ => Some(Piece(Role.Knight, Color.Black))) |
      "b".map(_ => Some(Piece(Role.Bishop, Color.Black))) |
      "r".map(_ => Some(Piece(Role.Rook, Color.Black))) |
      "q".map(_ => Some(Piece(Role.Queen, Color.Black))) |
      "k".map(_ => Some(Piece(Role.King, Color.Black)))
  )

  private def piece[$: P]: P[Option[Piece]] = P(whitePiece | blackPiece)

  private def emptySquares[$: P]: P[Seq[Option[Piece]]] = P(
    CharIn("1-8").!.map { d => Seq.fill(d.toInt)(None) }
  )

  private def rankElement[$: P]: P[Seq[Option[Piece]]] = P(
    piece.map(p => Seq(p)) | emptySquares
  )

  private def rank[$: P]: P[Vector[Option[Piece]]] = P(
    rankElement.rep(1).map { elems =>
      val flat = elems.flatten.toVector
      if (flat.length != 8)
        throw new IllegalArgumentException(
          s"FEN rank must have 8 squares, got ${flat.length}"
        )
      flat
    }
  )

  private def board[$: P]: P[Vector[Vector[Option[Piece]]]] = P(
    rank.rep(exactly = 8, sep = "/").map(_.toVector)
  )

  /** Optional trailing FEN fields (active color, castling, en passant, …) */
  private def trailingFields[$: P]: P[Unit] = P(
    (" " ~ AnyChar.rep).?
  )

  private def fen[$: P]: P[Board] = P(
    board ~ trailingFields ~ End
  ).map { squares => Board(squares) }

  // --- public API -----------------------------------------------------------

  /** Parses a FEN string and returns a Board wrapped in a Try. */
  def parseFEN(input: String): Try[Board] =
    Try {
      fastparse.parse(input.trim, fen(using _)) match {
        case Parsed.Success(board, _) => board
        case f: Parsed.Failure =>
          throw new IllegalArgumentException(
            s"FEN parse error: ${f.msg}"
          )
      }
    }

  /** Converts a Board to FEN notation (board position only). Delegates to
    * [[RegexFenParser.boardToFEN]] because serialization does not benefit from
    * parsing libraries.
    */
  def boardToFEN(board: Board): String =
    RegexFenParser.boardToFEN(board)

  // --- FenIO interface ------------------------------------------------------

  override def save(board: Board): String = boardToFEN(board)

  override def load(input: String): Try[Board] = parseFEN(input)
}
