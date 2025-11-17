package chess.controller.parser

import chess.model.{Board, Piece, PieceType, PieceColor}
import scala.util.matching.Regex
import scala.util.{Try, Success, Failure}

object PGNParser {
  private val MovePattern: Regex = """([KQRBN]?)([a-h]?)([1-8]?)([xX]?)([a-h][1-8])(=[QRBN]?|\+|#|\+\+)?""".r
  private val CastlingPattern: Regex = "(O-O-O|O-O|0-0-0|0-0)".r

  /**
   * Parses a PGN move string and returns the corresponding move coordinates
   * @param pgnMove The PGN move string (e.g., "e4", "Nf3", "O-O")
   * @param board The current board state
   * @param isWhiteToMove Whether it's white's turn to move
   * @return A tuple of (fromFile, fromRank, toFile, toRank)
   */
  def parseMove(pgnMove: String, board: Board, isWhiteToMove: Boolean): Try[(Int, Int, Int, Int)] = {
    val move = pgnMove.replace("+", "").replace("#", "") // Remove check/mate symbols
    
    move match {
      case CastlingPattern(castle) => parseCastling(castle, isWhiteToMove)
      case MovePattern(piece, fileHint, rankHint, capture, target, promotion) => 
        parseStandardMove(piece, fileHint, rankHint, capture, target, promotion, board, isWhiteToMove)
      case _ => 
        Failure(new IllegalArgumentException(s"Invalid PGN move: $pgnMove"))
    }
  }

  private def parseCastling(castle: String, isWhiteToMove: Boolean): Try[(Int, Int, Int, Int)] = {
    val rank = if (isWhiteToMove) 1 else 8
    castle match {
      case "O-O" | "0-0" => 
        // Kingside castling
        Success((5, rank, 7, rank))
      case "O-O-O" | "0-0-0" => 
        // Queenside castling
        Success((5, rank, 3, rank))
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
  ): Try[(Int, Int, Int, Int)] = {
    val pieceType = pieceStr match {
      case "" => PieceType.Pawn
      case "N" => PieceType.Knight
      case "B" => PieceType.Bishop
      case "R" => PieceType.Rook
      case "Q" => PieceType.Queen
      case "K" => PieceType.King
      case _ => return Failure(new IllegalArgumentException(s"Invalid piece: $pieceStr"))
    }

    val color = if (isWhiteToMove) PieceColor.White else PieceColor.Black
    val targetFile = target(0) - 'a' + 1
    val targetRank = target(1).asDigit

    // Find the piece that can make this move
    findPieceMatchingMove(pieceType, color, fileHint, rankHint, targetFile, targetRank, board) match {
      case Some((fromFile, fromRank)) => Success((fromFile, fromRank, targetFile, targetRank))
      case None => Failure(new IllegalArgumentException(s"No valid move found for: $pieceStr$fileHint$rankHint${capture}$target$promotion"))
    }
  }

  private def findPieceMatchingMove(
    pieceType: PieceType,
    color: PieceColor,
    fileHint: String,
    rankHint: String,
    targetFile: Int,
    targetRank: Int,
    board: Board
  ): Option[(Int, Int)] = {
    // Find all pieces of the correct type and color
    val possiblePieces = for {
      rank <- 1 to 8
      file <- 1 to 8
      piece <- board.pieceAt(file, rank)
      if piece.pieceType == pieceType && piece.color == color
    } yield (file, rank)

    // Filter pieces that can move to the target square
    possiblePieces.find { case (file, rank) =>
      // Check if the piece matches the disambiguation hints
      val fileMatches = fileHint.isEmpty || (file == (fileHint(0) - 'a' + 1))
      val rankMatches = rankHint.isEmpty || (rank == rankHint.toInt)
      
      fileMatches && rankMatches && isValidMove(file, rank, targetFile, targetRank, pieceType, color, board)
    }
  }

  private def isValidMove(
    fromFile: Int,
    fromRank: Int,
    toFile: Int,
    toRank: Int,
    pieceType: PieceType,
    color: PieceColor,
    board: Board
  ): Boolean = {
    // This is a simplified version - a real implementation would need to handle all chess rules
    // including check detection, en passant, castling rights, etc.
    pieceType match {
      case PieceType.Pawn => 
        val direction = if (color == PieceColor.White) 1 else -1
        val startRank = if (color == PieceColor.White) 2 else 7
        val isCapture = math.abs(toFile - fromFile) == 1
        
        if (isCapture) {
          // Pawn capture
          (toRank == fromRank + direction) && 
          math.abs(toFile - fromFile) == 1 &&
          board.pieceAt(toFile, toRank).exists(_.color != color)
        } else {
          // Pawn move forward
          val isStartPosition = fromRank == startRank
          val singleMove = (toRank == fromRank + direction) && (toFile == fromFile) && board.pieceAt(toFile, toRank).isEmpty
          val doubleMove = isStartPosition && (toRank == fromRank + 2*direction) && (toFile == fromFile) && 
                          board.pieceAt(toFile, toRank).isEmpty && board.pieceAt(toFile, toRank - direction).isEmpty
          singleMove || doubleMove
        }
        
      case PieceType.Knight =>
        val dx = math.abs(toFile - fromFile)
        val dy = math.abs(toRank - fromRank)
        (dx == 2 && dy == 1) || (dx == 1 && dy == 2)
        
      case PieceType.Bishop =>
        val dx = (toFile - fromFile).abs
        val dy = (toRank - fromRank).abs
        dx == dy && isPathClear(fromFile, fromRank, toFile, toRank, board)
        
      case PieceType.Rook =>
        (fromFile == toFile || fromRank == toRank) && isPathClear(fromFile, fromRank, toFile, toRank, board)
        
      case PieceType.Queen =>
        val dx = (toFile - fromFile).abs
        val dy = (toRank - fromRank).abs
        ((dx == dy) || (fromFile == toFile || fromRank == toRank)) && 
        isPathClear(fromFile, fromRank, toFile, toRank, board)
        
      case PieceType.King =>
        val dx = (toFile - fromFile).abs
        val dy = (toRank - fromRank).abs
        dx <= 1 && dy <= 1
    }
  }
  
  private def isPathClear(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int, board: Board): Boolean = {
    val dx = (toFile - fromFile).sign
    val dy = (toRank - fromRank).sign
    var x = fromFile + dx
    var y = fromRank + dy
    
    while (x != toFile || y != toRank) {
      if (board.pieceAt(x, y).isDefined) return false
      x += dx
      y += dy
    }
    
    // Check the target square (can be occupied by opponent's piece)
    board.pieceAt(toFile, toRank).forall(_.color != board.pieceAt(fromFile, fromRank).get.color)
  }
}
