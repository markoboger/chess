package chess.model

final case class Board(
  squares: Vector[Vector[Option[Piece]]],
  lastMove: Option[(Int, Int, Int, Int)] = None  // (fromFile, fromRank, toFile, toRank)
):

  def pieceAt(file: Int, rank: Int): Option[Piece] =
    squares.lift(8 - rank).flatMap(_.lift(file - 1)).flatten

  def move(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Board =
    // Validate coordinates are on board
    if fromFile < 1 || fromFile > 8 || fromRank < 1 || fromRank > 8 then return this
    if toFile < 1 || toFile > 8 || toRank < 1 || toRank > 8 then return this
    
    // Check if source square has a piece
    pieceAt(fromFile, fromRank) match
      case None => this
      case Some(piece) =>
        // Check if destination is occupied by own piece
        pieceAt(toFile, toRank) match
          case Some(targetPiece) if targetPiece.color == piece.color => return this
          case _ => ()
        
        // Validate move based on piece type
        val isValidMove = piece.pieceType match
          case PieceType.Pawn => isValidPawnMove(fromFile, fromRank, toFile, toRank, piece.color)
          case PieceType.Knight => isValidKnightMove(fromFile, fromRank, toFile, toRank)
          case PieceType.Bishop => isValidBishopMove(fromFile, fromRank, toFile, toRank)
          case PieceType.Rook => isValidRookMove(fromFile, fromRank, toFile, toRank)
          case PieceType.Queen => isValidQueenMove(fromFile, fromRank, toFile, toRank)
          case PieceType.King => isValidKingMove(fromFile, fromRank, toFile, toRank)
        
        if !isValidMove then return this
        
        // Apply the move
        def updateSquare(fs: Vector[Vector[Option[Piece]]], file: Int, rank: Int, value: Option[Piece]) =
          val rowIndex = 8 - rank
          val colIndex = file - 1
          val row = fs(rowIndex).updated(colIndex, value)
          fs.updated(rowIndex, row)

        var finalSquares = updateSquare(squares, fromFile, fromRank, None)
        finalSquares = updateSquare(finalSquares, toFile, toRank, Some(piece))
        
        // Handle en passant capture
        if piece.pieceType == PieceType.Pawn && isEnPassantCapture(fromFile, fromRank, toFile, toRank, piece.color) then
          val capturedRank = fromRank
          finalSquares = updateSquare(finalSquares, toFile, capturedRank, None)
        
        copy(squares = finalSquares, lastMove = Some((fromFile, fromRank, toFile, toRank)))
  
  private def isValidPawnMove(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int, color: PieceColor): Boolean =
    val direction = if color == PieceColor.White then 1 else -1
    val startRank = if color == PieceColor.White then 2 else 7
    
    // Pawn can only move forward
    val rankDiff = toRank - fromRank
    val fileDiff = (toFile - fromFile).abs
    
    // Check if moving forward in correct direction
    if rankDiff * direction <= 0 then return false
    
    // Single square forward (must be empty)
    if rankDiff * direction == 1 && fileDiff == 0 then
      return pieceAt(toFile, toRank).isEmpty
    
    // Two squares forward from starting position (must be empty)
    if rankDiff * direction == 2 && fileDiff == 0 && fromRank == startRank then
      val middleRank = fromRank + direction
      return pieceAt(toFile, middleRank).isEmpty && pieceAt(toFile, toRank).isEmpty
    
    // Diagonal capture (one square diagonally forward, must have opponent piece or en passant)
    if rankDiff * direction == 1 && fileDiff == 1 then
      // Regular capture
      pieceAt(toFile, toRank) match
        case Some(targetPiece) if targetPiece.color != color => return true
        case _ => ()
      
      // En passant capture
      if isEnPassantCapture(fromFile, fromRank, toFile, toRank, color) then return true
    
    false
  
  private def isEnPassantCapture(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int, color: PieceColor): Boolean =
    val direction = if color == PieceColor.White then 1 else -1
    val enPassantRank = if color == PieceColor.White then 5 else 4
    
    // En passant only valid on specific ranks
    if fromRank != enPassantRank then return false
    
    // Must be diagonal move
    val rankDiff = toRank - fromRank
    val fileDiff = (toFile - fromFile).abs
    if rankDiff * direction != 1 || fileDiff != 1 then return false
    
    // Destination must be empty
    if pieceAt(toFile, toRank).isDefined then return false
    
    // Check if last move was opponent pawn moving 2 squares to the side
    lastMove match
      case Some((lastFromFile, lastFromRank, lastToFile, lastToRank)) =>
        // Last move must be a pawn moving 2 squares
        val lastDirection = if color == PieceColor.White then -1 else 1
        val lastStartRank = if color == PieceColor.White then 7 else 2
        val lastRankDiff = lastToRank - lastFromRank
        
        // Check if opponent pawn moved 2 squares from starting position
        if lastFromRank != lastStartRank then return false
        if lastRankDiff != 2 * lastDirection then return false
        
        // Check if the pawn is now on the same rank as our pawn and adjacent file
        if lastToRank != fromRank then return false
        if lastToFile != toFile then return false
        
        // Verify there's an opponent pawn at the captured square
        pieceAt(toFile, fromRank) match
          case Some(capturedPiece) if capturedPiece.pieceType == PieceType.Pawn && capturedPiece.color != color => true
          case _ => false
      case None => false
  
  private def isValidKnightMove(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Boolean =
    val fileDiff = (toFile - fromFile).abs
    val rankDiff = (toRank - fromRank).abs
    (fileDiff == 2 && rankDiff == 1) || (fileDiff == 1 && rankDiff == 2)
  
  private def isValidBishopMove(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Boolean =
    val fileDiff = (toFile - fromFile).abs
    val rankDiff = (toRank - fromRank).abs
    if fileDiff != rankDiff || fileDiff == 0 then return false
    isPathClear(fromFile, fromRank, toFile, toRank)
  
  private def isValidRookMove(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Boolean =
    if fromFile != toFile && fromRank != toRank then return false
    if fromFile == toFile && fromRank == toRank then return false
    isPathClear(fromFile, fromRank, toFile, toRank)
  
  private def isValidQueenMove(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Boolean =
    val fileDiff = (toFile - fromFile).abs
    val rankDiff = (toRank - fromRank).abs
    
    // Queen moves like rook or bishop
    if fromFile == toFile && fromRank == toRank then return false
    if fromFile != toFile && fromRank != toRank && fileDiff != rankDiff then return false
    
    isPathClear(fromFile, fromRank, toFile, toRank)
  
  private def isValidKingMove(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Boolean =
    val fileDiff = (toFile - fromFile).abs
    val rankDiff = (toRank - fromRank).abs
    fileDiff <= 1 && rankDiff <= 1 && (fileDiff != 0 || rankDiff != 0)
  
  private def isPathClear(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Boolean =
    val fileStep = if toFile > fromFile then 1 else if toFile < fromFile then -1 else 0
    val rankStep = if toRank > fromRank then 1 else if toRank < fromRank then -1 else 0
    
    var currentFile = fromFile + fileStep
    var currentRank = fromRank + rankStep
    
    while currentFile != toFile || currentRank != toRank do
      if pieceAt(currentFile, currentRank).isDefined then return false
      currentFile += fileStep
      currentRank += rankStep
    
    true

  override def toString: String =
    val files = "  a b c d e f g h"
    val rows = for {
      rank <- 8 to 1 by -1
      row = squares(8 - rank)
      pieces = row.map {
        case Some(piece) => piece.toString
        case None => "·"
      }.mkString(" ")
    } yield s"$rank $pieces $rank"
    
    (files +: rows :+ files).mkString("\n")

object Board:
  private def backRank(color: PieceColor): Vector[Piece] =
    Vector(
      Piece(PieceType.Rook, color),
      Piece(PieceType.Knight, color),
      Piece(PieceType.Bishop, color),
      Piece(PieceType.Queen, color),
      Piece(PieceType.King, color),
      Piece(PieceType.Bishop, color),
      Piece(PieceType.Knight, color),
      Piece(PieceType.Rook, color)
    )

  def initial: Board =
    val blackRank = backRank(PieceColor.Black).map(Some(_))
    val blackPawns = Vector.fill(8)(Some(Piece(PieceType.Pawn, PieceColor.Black)))
    val empty = Vector.fill(4)(Vector.fill(8)(Option.empty[Piece]))
    val whitePawns = Vector.fill(8)(Some(Piece(PieceType.Pawn, PieceColor.White)))
    val whiteRank = backRank(PieceColor.White).map(Some(_))

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
