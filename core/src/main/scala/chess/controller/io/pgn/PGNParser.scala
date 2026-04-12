package chess.controller.io.pgn

import chess.model.{Board, Role, Color, Square, File, Rank}
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
    }

    val color = if (isWhiteToMove) Color.White else Color.Black
    // Target square is guaranteed valid by MovePattern regex
    val targetSquare = Square.fromString(target).get

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

    // Must use fully **legal** moves (not pseudo-legal). In check, `canMoveIgnoringCheck` can
    // still be true for moves that leave the king in check or fail to escape check — the opening
    // book then returns such a tuple, `Board.move` fails, and the AI session yields no move.
    possiblePieces.find { square =>
      val fileMatches =
        fileHint.isEmpty || File.fromChar(fileHint(0)).contains(square.file)
      val rankMatches =
        rankHint.isEmpty || Rank.fromInt(rankHint.toInt).contains(square.rank)

      fileMatches && rankMatches &&
      board.legalMoves(color).exists((from, to) => from == square && to == target)
    }
  }

  /** Convert a coordinate move to standard algebraic (PGN) notation.
    *
    * @param from
    *   source square
    * @param to
    *   target square
    * @param boardBefore
    *   board state before the move
    * @param boardAfter
    *   board state after the move
    * @param isWhite
    *   whether the moving side is white
    * @return
    *   the PGN string, e.g. "Nf3", "exd5", "O-O", "Qd1#"
    */
  def toAlgebraic(
      from: Square,
      to: Square,
      boardBefore: Board,
      boardAfter: Board,
      isWhite: Boolean
  ): String =
    val piece = boardBefore.pieceAt(from).get
    val opponent = if (isWhite) Color.Black else Color.White
    val isCapture = isCaptureMove(piece, from, to, boardBefore)
    val suffix = checkSuffix(boardAfter, opponent)
    castleNotation(piece, from, to, suffix).getOrElse {
      val sb = new StringBuilder
      appendPiecePrefix(sb, piece.role, from, isCapture)
      appendDisambiguation(sb, piece, from, to, boardBefore)
      if (isCapture) sb.append("x")
      sb.append(to.toString)
      appendPromotion(sb, piece.role, to, boardAfter)
      sb.append(suffix)
      sb.toString
    }

  private def isCaptureMove(piece: chess.model.Piece, from: Square, to: Square, boardBefore: Board): Boolean =
    boardBefore.pieceAt(to).isDefined || (piece.role == Role.Pawn && from.file != to.file)

  private def castleNotation(
      piece: chess.model.Piece,
      from: Square,
      to: Square,
      suffix: String
  ): Option[String] =
    Option.when(piece.role == Role.King && (to.file - from.file).abs == 2) {
      val base = if (to.file.index > from.file.index) "O-O" else "O-O-O"
      base + suffix
    }

  private def checkSuffix(boardAfter: Board, opponent: Color): String =
    if (boardAfter.isCheckmate(opponent)) "#"
    else if (boardAfter.isInCheck(opponent)) "+"
    else ""

  private def appendPiecePrefix(sb: StringBuilder, role: Role, from: Square, isCapture: Boolean): Unit =
    role match
      case Role.Pawn =>
        if (isCapture) sb.append(from.file.letter)
      case Role.Knight => sb.append("N")
      case Role.Bishop => sb.append("B")
      case Role.Rook   => sb.append("R")
      case Role.Queen  => sb.append("Q")
      case Role.King   => sb.append("K")

  private def appendDisambiguation(
      sb: StringBuilder,
      piece: chess.model.Piece,
      from: Square,
      to: Square,
      boardBefore: Board
  ): Unit =
    if piece.role != Role.Pawn && piece.role != Role.King then
      val others = competingPieces(piece, from, to, boardBefore)
      if others.nonEmpty then
        val sameFile = others.exists(_.file == from.file)
        val sameRank = others.exists(_.rank == from.rank)
        if (!sameFile) sb.append(from.file.letter)
        else if (!sameRank) sb.append(from.rank.index)
        else
          sb.append(from.file.letter)
          sb.append(from.rank.index)

  private def competingPieces(
      piece: chess.model.Piece,
      from: Square,
      to: Square,
      boardBefore: Board
  ): Vector[Square] =
    Square.all.filter { sq =>
      sq != from &&
      boardBefore
        .pieceAt(sq)
        .exists(p => p.role == piece.role && p.color == piece.color) &&
      boardBefore.canMoveIgnoringCheck(sq, to)
    }

  private def appendPromotion(
      sb: StringBuilder,
      role: Role,
      to: Square,
      boardAfter: Board
  ): Unit =
    if role == Role.Pawn then
      promotedPieceAt(to, boardAfter).foreach { promoted =>
        sb.append("=")
        sb.append(promoted)
      }

  private def promotedPieceAt(to: Square, boardAfter: Board): Option[String] =
    boardAfter.pieceAt(to).flatMap { arrived =>
      Option.when(arrived.role != Role.Pawn) {
        arrived.role match
          case Role.Queen  => "Q"
          case Role.Rook   => "R"
          case Role.Bishop => "B"
          case Role.Knight => "N"
          case _           => ""
      }.filter(_.nonEmpty)
    }

}
