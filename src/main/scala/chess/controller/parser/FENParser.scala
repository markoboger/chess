package chess.controller.parser

import chess.model.{Board, Piece, Role, Color}
import scala.util.{Try, Success, Failure}

object FENParser {

  /** Parses a FEN (Forsyth-Edwards Notation) string and returns a Board. FEN
    * format: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    *
    * @param fen
    *   The FEN string
    * @return
    *   A Board if parsing succeeds, or an error message
    */
  def parseFEN(fen: String): Try[Board] = {
    Try {
      val parts = fen.trim.split("\\s+")
      val boardPart = parts(0)
      if (boardPart.isEmpty) {
        throw new IllegalArgumentException("FEN string is empty")
      }
      parseBoardFromFEN(boardPart)
    }
  }

  /** Parses just the board position part of a FEN string (first component).
    * Format: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    */
  private def parseBoardFromFEN(boardPart: String): Board = {
    val ranks = boardPart.split("/")
    if (ranks.length != 8) {
      throw new IllegalArgumentException(
        s"FEN board must have 8 ranks, got ${ranks.length}"
      )
    }

    val squares = ranks.map(parseRank).toVector
    Board(squares)
  }

  /** Parses a single rank from FEN notation. Example: "rnbqkbnr" or "8" or
    * "r1bqkb1r"
    */
  private def parseRank(rankStr: String): Vector[Option[Piece]] = {
    val squares = scala.collection.mutable.ArrayBuffer[Option[Piece]]()

    for (char <- rankStr) {
      if (char.isDigit) {
        // Empty squares represented by digits 1-8
        val emptyCount = char.asDigit
        for (_ <- 0 until emptyCount) {
          squares += None
        }
      } else {
        // Piece character
        squares += parsePiece(char)
      }
    }

    if (squares.length != 8) {
      throw new IllegalArgumentException(
        s"FEN rank must have 8 squares, got ${squares.length}: $rankStr"
      )
    }

    squares.toVector
  }

  /** Parses a single piece character from FEN notation. Uppercase = White,
    * Lowercase = Black p/P = Pawn, n/N = Knight, b/B = Bishop, r/R = Rook, q/Q
    * \= Queen, k/K = King
    */
  private def parsePiece(char: Char): Option[Piece] = {
    val color = if (char.isUpper) Color.White else Color.Black
    val pieceType = char.toLower match {
      case 'p' => Role.Pawn
      case 'n' => Role.Knight
      case 'b' => Role.Bishop
      case 'r' => Role.Rook
      case 'q' => Role.Queen
      case 'k' => Role.King
      case _ =>
        throw new IllegalArgumentException(s"Invalid piece character: $char")
    }
    Some(Piece(pieceType, color))
  }

  /** Converts a Board to FEN notation (board position only). Example output:
    * "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    */
  def boardToFEN(board: Board): String = {
    board.squares
      .map(rankToFEN)
      .mkString("/")
  }

  /** Converts a single rank to FEN notation.
    */
  private def rankToFEN(rank: Vector[Option[Piece]]): String = {
    val sb = new StringBuilder()
    var emptyCount = 0

    for (square <- rank) {
      square match {
        case Some(piece) =>
          if (emptyCount > 0) {
            sb.append(emptyCount)
            emptyCount = 0
          }
          sb.append(pieceToFEN(piece))
        case None =>
          emptyCount += 1
      }
    }

    if (emptyCount > 0) {
      sb.append(emptyCount)
    }

    sb.toString()
  }

  /** Converts a piece to its FEN character representation.
    */
  private def pieceToFEN(piece: Piece): Char = {
    val char = piece.role match {
      case Role.Pawn   => 'p'
      case Role.Knight => 'n'
      case Role.Bishop => 'b'
      case Role.Rook   => 'r'
      case Role.Queen  => 'q'
      case Role.King   => 'k'
    }
    if (piece.color == Color.White) char.toUpper else char
  }
}
