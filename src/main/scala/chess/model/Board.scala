package chess.model

final case class Board(
    squares: Vector[Vector[Option[Piece]]],
    lastMove: Option[(Square, Square)] = None
):

  def pieceAt(square: Square): Option[Piece] =
    squares(8 - square.rank.index)(square.file.index - 1)

  def move(from: Square, to: Square): MoveResult =
    pieceAt(from) match
      case None => MoveResult.Failed(this, MoveError.NoPiece)
      case Some(piece) =>
        val candidate = applyMoveUnchecked(from, to)
        if candidate eq this then MoveResult.Failed(this, MoveError.InvalidMove)
        else if candidate.isInCheck(piece.color) then
          MoveResult.Failed(this, MoveError.LeavesKingInCheck)
        else
          MoveResult.Moved(
            candidate,
            candidate.detectGameEvent(piece.color.opposite)
          )

  private[model] def applyMoveUnchecked(from: Square, to: Square): Board =
    // Check if source square has a piece
    pieceAt(from) match
      case None        => this
      case Some(piece) =>
        // Check if destination is occupied by own piece
        pieceAt(to) match
          case Some(targetPiece) if targetPiece.color == piece.color =>
            return this
          case _ => ()

        // Validate move based on piece type
        val isValid = piece.role match
          case Role.Pawn   => isValidPawnMove(from, to, piece.color)
          case Role.Knight => isValidKnightMove(from, to)
          case Role.Bishop => isValidBishopMove(from, to)
          case Role.Rook   => isValidRookMove(from, to)
          case Role.Queen  => isValidQueenMove(from, to)
          case Role.King   => isValidKingMove(from, to)

        if !isValid then return this

        // Apply the move
        def updateSquare(
            fs: Vector[Vector[Option[Piece]]],
            sq: Square,
            value: Option[Piece]
        ) =
          val rowIndex = 8 - sq.rank.index
          val colIndex = sq.file.index - 1
          val row = fs(rowIndex).updated(colIndex, value)
          fs.updated(rowIndex, row)

        var finalSquares = updateSquare(squares, from, None)
        finalSquares = updateSquare(finalSquares, to, Some(piece))

        // Handle en passant capture
        if piece.role == Role.Pawn && isEnPassantCapture(
            from,
            to,
            piece.color
          )
        then
          val capturedSquare = Square(to.file, from.rank)
          finalSquares = updateSquare(finalSquares, capturedSquare, None)

        copy(squares = finalSquares, lastMove = Some((from, to)))

  private def isValidPawnMove(
      from: Square,
      to: Square,
      color: Color
  ): Boolean =
    val direction = if color == Color.White then 1 else -1
    val startRank = if color == Color.White then Rank._2 else Rank._7

    // Pawn can only move forward
    val rankDiff = to.rank - from.rank
    val fileDiff = (to.file - from.file).abs

    // Check if moving forward in correct direction
    if rankDiff * direction <= 0 then return false

    // Single square forward (must be empty)
    if rankDiff * direction == 1 && fileDiff == 0 then
      return pieceAt(to).isEmpty

    // Two squares forward from starting position (must be empty)
    if rankDiff * direction == 2 && fileDiff == 0 && from.rank == startRank then
      val middleSquare =
        from.rank.offset(direction).map(r => Square(to.file, r))
      return middleSquare.exists(sq => pieceAt(sq).isEmpty) && pieceAt(
        to
      ).isEmpty

    // Diagonal capture (one square diagonally forward, must have opponent piece or en passant)
    if rankDiff * direction == 1 && fileDiff == 1 then
      // Regular capture
      pieceAt(to) match
        case Some(targetPiece) if targetPiece.color != color => return true
        case _                                               => ()

      // En passant capture
      if isEnPassantCapture(from, to, color) then return true

    false

  private def isEnPassantCapture(
      from: Square,
      to: Square,
      color: Color
  ): Boolean =
    val direction = if color == Color.White then 1 else -1
    val enPassantRank = if color == Color.White then Rank._5 else Rank._4

    // En passant only valid on specific ranks
    if from.rank != enPassantRank then return false

    // Must be diagonal move
    val rankDiff = to.rank - from.rank
    val fileDiff = (to.file - from.file).abs
    if rankDiff * direction != 1 || fileDiff != 1 then return false

    // Destination must be empty
    if pieceAt(to).isDefined then return false

    // Check if last move was opponent pawn moving 2 squares to the side
    lastMove match
      case Some((lastFrom, lastTo)) =>
        // Last move must be a pawn moving 2 squares
        val lastDirection = if color == Color.White then -1 else 1
        val lastStartRank =
          if color == Color.White then Rank._7 else Rank._2
        val lastRankDiff = lastTo.rank - lastFrom.rank

        // Check if opponent pawn moved 2 squares from starting position
        if lastFrom.rank != lastStartRank then return false
        if lastRankDiff != 2 * lastDirection then return false

        // After verifying a 2-square move from starting rank, the pawn
        // always lands on the same rank as the capturing pawn, so only
        // the file adjacency needs checking.
        if lastTo.file != to.file then return false

        // Verify there's an opponent pawn at the captured square
        val capturedSquare = Square(to.file, from.rank)
        pieceAt(capturedSquare) match
          case Some(capturedPiece)
              if capturedPiece.role == Role.Pawn && capturedPiece.color != color =>
            true
          case _ => false
      case None => false

  private def isValidKnightMove(from: Square, to: Square): Boolean =
    val fileDiff = (to.file - from.file).abs
    val rankDiff = (to.rank - from.rank).abs
    (fileDiff == 2 && rankDiff == 1) || (fileDiff == 1 && rankDiff == 2)

  private def isValidBishopMove(from: Square, to: Square): Boolean =
    val fileDiff = (to.file - from.file).abs
    val rankDiff = (to.rank - from.rank).abs
    if fileDiff != rankDiff || fileDiff == 0 then return false
    isPathClear(from, to)

  private def isValidRookMove(from: Square, to: Square): Boolean =
    // Same-square moves are already rejected by the own-piece capture check
    if from.file != to.file && from.rank != to.rank then return false
    isPathClear(from, to)

  private def isValidQueenMove(from: Square, to: Square): Boolean =
    val fileDiff = (to.file - from.file).abs
    val rankDiff = (to.rank - from.rank).abs

    // Queen moves like rook or bishop
    // Same-square moves are already rejected by the own-piece capture check
    if from.file != to.file && from.rank != to.rank && fileDiff != rankDiff then
      return false

    isPathClear(from, to)

  private def isValidKingMove(from: Square, to: Square): Boolean =
    val fileDiff = (to.file - from.file).abs
    val rankDiff = (to.rank - from.rank).abs
    fileDiff <= 1 && rankDiff <= 1 && (fileDiff != 0 || rankDiff != 0)

  private def isPathClear(from: Square, to: Square): Boolean =
    val fileStep = (to.file - from.file).sign
    val rankStep = (to.rank - from.rank).sign

    var currentFile = from.file.index + fileStep
    var currentRank = from.rank.index + rankStep

    while currentFile != to.file.index || currentRank != to.rank.index do
      // Indices are always valid here since we stay between from and to
      if squares(8 - currentRank)(currentFile - 1).isDefined then return false
      currentFile += fileStep
      currentRank += rankStep

    true

  def findKing(color: Color): Option[Square] =
    Square.all.find { sq =>
      pieceAt(sq) match
        case Some(piece) => piece.role == Role.King && piece.color == color
        case None        => false
    }

  def isAttackedBy(square: Square, attackerColor: Color): Boolean =
    Square.all.exists { from =>
      pieceAt(from) match
        case Some(piece) if piece.color == attackerColor =>
          piece.role match
            case Role.Pawn =>
              val direction = if attackerColor == Color.White then 1 else -1
              val fileDiff = (square.file - from.file).abs
              val rankDiff = square.rank - from.rank
              fileDiff == 1 && rankDiff == direction
            case Role.Knight => isValidKnightMove(from, square)
            case Role.Bishop => isValidBishopMove(from, square)
            case Role.Rook   => isValidRookMove(from, square)
            case Role.Queen  => isValidQueenMove(from, square)
            case Role.King   => isValidKingMove(from, square)
        case _ => false
    }

  def isInCheck(color: Color): Boolean =
    findKing(color) match
      case Some(kingSquare) => isAttackedBy(kingSquare, color.opposite)
      case None             => false

  def legalMoves(color: Color): Vector[(Square, Square)] =
    Square.all.flatMap { from =>
      pieceAt(from) match
        case Some(piece) if piece.color == color =>
          Square.all
            .filter { to =>
              val candidate = applyMoveUnchecked(from, to)
              !(candidate eq this) && !candidate.isInCheck(color)
            }
            .map(to => (from, to))
        case _ => Vector.empty
    }.toVector

  def isCheckmate(color: Color): Boolean =
    isInCheck(color) && legalMoves(color).isEmpty

  def isStalemate(color: Color): Boolean =
    !isInCheck(color) && legalMoves(color).isEmpty

  private def hasLegalMoves(color: Color): Boolean =
    Square.all.exists { from =>
      pieceAt(from) match
        case Some(piece) if piece.color == color =>
          Square.all.exists { to =>
            val candidate = applyMoveUnchecked(from, to)
            !(candidate eq this) && !candidate.isInCheck(color)
          }
        case _ => false
    }

  private def detectGameEvent(opponent: Color): GameEvent =
    val inCheck = isInCheck(opponent)
    if inCheck then
      if !hasLegalMoves(opponent) then GameEvent.Checkmate
      else GameEvent.Check
    else if !hasLegalMoves(opponent) then GameEvent.Stalemate
    else GameEvent.Moved

  override def toString: String =
    val files = "  a b c d e f g h"
    val rows = for {
      rank <- 8 to 1 by -1
      row = squares(8 - rank)
      pieces = row
        .map {
          case Some(piece) => piece.toString
          case None        => "·"
        }
        .mkString(" ")
    } yield s"$rank $pieces $rank"

    (files +: rows :+ files).mkString("\n")

object Board:
  private def backRank(color: Color): Vector[Piece] =
    Vector(
      Piece(Role.Rook, color),
      Piece(Role.Knight, color),
      Piece(Role.Bishop, color),
      Piece(Role.Queen, color),
      Piece(Role.King, color),
      Piece(Role.Bishop, color),
      Piece(Role.Knight, color),
      Piece(Role.Rook, color)
    )

  def initial: Board =
    val blackRank = backRank(Color.Black).map(Some(_))
    val blackPawns =
      Vector.fill(8)(Some(Piece(Role.Pawn, Color.Black)))
    val empty = Vector.fill(4)(Vector.fill(8)(Option.empty[Piece]))
    val whitePawns =
      Vector.fill(8)(Some(Piece(Role.Pawn, Color.White)))
    val whiteRank = backRank(Color.White).map(Some(_))

    Board(
      Vector(
        blackRank,
        blackPawns,
        empty(0),
        empty(1),
        empty(2),
        empty(3),
        whitePawns,
        whiteRank
      ),
      lastMove = None
    )
