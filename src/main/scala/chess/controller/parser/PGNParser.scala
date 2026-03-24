package chess.controller.parser

import chess.model.{Board, Piece, Role, Color, Square, File, Rank}
import scala.util.matching.Regex
import scala.util.{Try, Success, Failure}

object PGNParser {
  private val MovePattern: Regex =
    """([KQRBN]?)([a-h]?)([1-8]?)([xX]?)([a-h][1-8])(=[QRBN]?|\+|#|\+\+)?""".r
  private val CastlingPattern: Regex = "(O-O-O|O-O|0-0-0|0-0)".r

  /** Parses a PGN move string and returns the corresponding move squares
    * @param pgnMove
    *   The PGN move string (e.g., "e4", "Nf3", "O-O")
    * @param board
    *   The current board state
    * @param isWhiteToMove
    *   Whether it's white's turn to move
    * @return
    *   A tuple of (from, to) Squares
    */
  def parseMove(
      pgnMove: String,
      board: Board,
      isWhiteToMove: Boolean
  ): Try[(Square, Square)] = {
    val move =
      pgnMove.replace("+", "").replace("#", "") // Remove check/mate symbols

    move match {
      case CastlingPattern(castle) => parseCastling(castle, isWhiteToMove)
      case MovePattern(piece, fileHint, rankHint, capture, target, promotion) =>
        parseStandardMove(
          piece,
          fileHint,
          rankHint,
          capture,
          target,
          promotion,
          board,
          isWhiteToMove
        )
      case _ =>
        Failure(new IllegalArgumentException(s"Invalid PGN move: $pgnMove"))
    }
  }

  private def parseCastling(
      castle: String,
      isWhiteToMove: Boolean
  ): Try[(Square, Square)] = {
    val rank = if (isWhiteToMove) Rank._1 else Rank._8
    castle match {
      case "O-O" | "0-0" =>
        // Kingside castling
        Success((Square(File.E, rank), Square(File.G, rank)))
      case "O-O-O" | "0-0-0" =>
        // Queenside castling
        Success((Square(File.E, rank), Square(File.C, rank)))
      case _ =>
        Failure(new IllegalArgumentException(s"Invalid castling move: $castle"))
    }
  }

  private def parseStandardMove(
      pieceStr: String,
      fileHint: String,
      rankHint: String,
      capture: String,
      target: String,
      promotion: String,
      board: Board,
      isWhiteToMove: Boolean
  ): Try[(Square, Square)] = {
    val pieceType = pieceStr match {
      case ""  => Role.Pawn
      case "N" => Role.Knight
      case "B" => Role.Bishop
      case "R" => Role.Rook
      case "Q" => Role.Queen
      case "K" => Role.King
      case _ =>
        return Failure(
          new IllegalArgumentException(s"Invalid piece: $pieceStr")
        )
    }

    val color = if (isWhiteToMove) Color.White else Color.Black
    val targetSquare = Square.fromString(target) match {
      case Some(sq) => sq
      case None =>
        return Failure(
          new IllegalArgumentException(s"Invalid target square: $target")
        )
    }

    // Find the piece that can make this move
    findPieceMatchingMove(
      pieceType,
      color,
      fileHint,
      rankHint,
      targetSquare,
      board
    ) match {
      case Some(fromSquare) => Success((fromSquare, targetSquare))
      case None =>
        Failure(
          new IllegalArgumentException(
            s"No valid move found for: $pieceStr$fileHint$rankHint${capture}$target$promotion"
          )
        )
    }
  }

  private def findPieceMatchingMove(
      pieceType: Role,
      color: Color,
      fileHint: String,
      rankHint: String,
      target: Square,
      board: Board
  ): Option[Square] = {
    // Find all pieces of the correct type and color
    val possiblePieces = for {
      square <- Square.all
      piece <- board.pieceAt(square)
      if piece.role == pieceType && piece.color == color
    } yield square

    // Filter pieces that can move to the target square
    possiblePieces.find { square =>
      // Check if the piece matches the disambiguation hints
      val fileMatches =
        fileHint.isEmpty || File.fromChar(fileHint(0)).contains(square.file)
      val rankMatches =
        rankHint.isEmpty || Rank.fromInt(rankHint.toInt).contains(square.rank)

      fileMatches && rankMatches && isValidMove(
        square,
        target,
        pieceType,
        color,
        board
      )
    }
  }

  private def isValidMove(
      from: Square,
      to: Square,
      pieceType: Role,
      color: Color,
      board: Board
  ): Boolean = {
    // This is a simplified version - a real implementation would need to handle all chess rules
    // including check detection, en passant, castling rights, etc.
    pieceType match {
      case Role.Pawn =>
        val direction = if (color == Color.White) 1 else -1
        val startRank = if (color == Color.White) Rank._2 else Rank._7
        val isCapture = (to.file - from.file).abs == 1

        if (isCapture) {
          // Pawn capture
          (to.rank - from.rank == direction) &&
          (to.file - from.file).abs == 1 &&
          board.pieceAt(to).exists(_.color != color)
        } else {
          // Pawn move forward
          val isStartPosition = from.rank == startRank
          val singleMove =
            (to.rank - from.rank == direction) && (to.file == from.file) && board
              .pieceAt(to)
              .isEmpty
          val middleSquare =
            from.rank.offset(direction).map(r => Square(to.file, r))
          val doubleMove =
            isStartPosition && (to.rank - from.rank == 2 * direction) && (to.file == from.file) &&
              board.pieceAt(to).isEmpty && middleSquare.exists(sq =>
                board.pieceAt(sq).isEmpty
              )
          singleMove || doubleMove
        }

      case Role.Knight =>
        val dx = (to.file - from.file).abs
        val dy = (to.rank - from.rank).abs
        (dx == 2 && dy == 1) || (dx == 1 && dy == 2)

      case Role.Bishop =>
        val dx = (to.file - from.file).abs
        val dy = (to.rank - from.rank).abs
        dx == dy && isPathClear(from, to, board)

      case Role.Rook =>
        (from.file == to.file || from.rank == to.rank) && isPathClear(
          from,
          to,
          board
        )

      case Role.Queen =>
        val dx = (to.file - from.file).abs
        val dy = (to.rank - from.rank).abs
        ((dx == dy) || (from.file == to.file || from.rank == to.rank)) &&
        isPathClear(from, to, board)

      case Role.King =>
        val dx = (to.file - from.file).abs
        val dy = (to.rank - from.rank).abs
        dx <= 1 && dy <= 1
    }
  }

  private def isPathClear(from: Square, to: Square, board: Board): Boolean = {
    val dx = (to.file - from.file).sign
    val dy = (to.rank - from.rank).sign
    var f = from.file.index + dx
    var r = from.rank.index + dy

    while (f != to.file.index || r != to.rank.index) {
      (for
        sq <- Square.fromCoords(f, r)
        _ <- board.pieceAt(sq)
      yield sq).foreach(_ => return false)
      f += dx
      r += dy
    }

    // Check the target square (can be occupied by opponent's piece)
    board.pieceAt(to).forall(_.color != board.pieceAt(from).get.color)
  }
}
