package chess.controller.strategy

import chess.model.{Board, Color, Piece, Square, Role}

/** Shared position evaluator used by depth-based strategies.
  *
  * Returns a score in centipawns from `color`'s perspective: positive = `color` is ahead, negative = opponent is ahead.
  *
  * Score = Σ own(material + PST bonus) − Σ opponent(material + PST bonus)
  */
object Evaluator:

  enum GamePhase:
    case Middlegame, Endgame

  // ── Material values ───────────────────────────────────────────────────────
  val materialValue: Role => Int = {
    case Role.Pawn   => 100
    case Role.Knight => 320
    case Role.Bishop => 330
    case Role.Rook   => 500
    case Role.Queen  => 900
    case Role.King   => 20000
  }

  // ── Piece-square tables (row 0 = rank 8 for White) ───────────────────────
  private val pawnTable = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 50, 50, 50, 50, 50, 50, 50, 50, 10, 10, 20, 30, 30, 20, 10, 10, 5, 5, 10, 25, 25, 10, 5, 5,
    0, 0, 0, 20, 20, 0, 0, 0, 5, -5, -10, 0, 0, -10, -5, 5, 5, 10, 10, -20, -20, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0
  )
  private val knightTable = Array(
    -50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 0, 0, 0, -20, -40, -30, 0, 10, 15, 15, 10, 0, -30, -30, 5, 15,
    20, 20, 15, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 10, 15, 15, 10, 5, -30, -40, -20, 0, 5, 5, 0, -20, -40,
    -50, -40, -30, -30, -30, -30, -40, -50
  )
  private val bishopTable = Array(
    -20, -10, -10, -10, -10, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 5, 5, 10, 10,
    5, 5, -10, -10, 0, 10, 10, 10, 10, 0, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 5, 0, 0, 0, 0, 5, -10, -20, -10,
    -10, -10, -10, -10, -10, -20
  )
  private val rookTable = Array(
    0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0,
    0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0
  )
  private val queenTable = Array(
    -20, -10, -10, -5, -5, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 5, 5, 5, 0, -10, -5, 0, 5, 5, 5, 5, 0,
    -5, 0, 0, 5, 5, 5, 5, 0, -5, -10, 5, 5, 5, 5, 5, 0, -10, -10, 0, 5, 0, 0, 0, 0, -10, -20, -10, -10, -5, -5, -10,
    -10, -20
  )
  private val kingTable = Array(
    -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40,
    -30, -30, -40, -40, -50, -50, -40, -40, -30, -20, -30, -30, -40, -40, -30, -30, -20, -10, -20, -20, -20, -20, -20,
    -20, -10, 20, 20, 0, 0, 0, 0, 20, 20, 20, 30, 10, 0, 0, 10, 30, 20
  )
  private val kingEndgameTable = Array(
    -50, -30, -30, -30, -30, -30, -30, -50,
    -30, -10, -10, -10, -10, -10, -10, -30,
    -30, -10, 20, 25, 25, 20, -10, -30,
    -30, -10, 25, 35, 35, 25, -10, -30,
    -30, -10, 25, 35, 35, 25, -10, -30,
    -30, -10, 20, 25, 25, 20, -10, -30,
    -30, -20, -10, -10, -10, -10, -20, -30,
    -50, -30, -30, -30, -30, -30, -30, -50
  )

  private def pstIndex(sq: Square, color: Color): Int =
    val col = sq.file.index - 1
    val row = color match
      case Color.White => 8 - sq.rank.index
      case Color.Black => sq.rank.index - 1
    row * 8 + col

  def pstBonus(role: Role, sq: Square, color: Color): Int =
    val idx = pstIndex(sq, color)
    role match
      case Role.Pawn   => pawnTable(idx)
      case Role.Knight => knightTable(idx)
      case Role.Bishop => bishopTable(idx)
      case Role.Rook   => rookTable(idx)
      case Role.Queen  => queenTable(idx)
      case Role.King   => kingTable(idx)

  def phase(board: Board): GamePhase =
    val pieces = occupiedSquares(board)
    val totalNonPawnMaterial =
      pieces.foldLeft(0) { case (acc, (_, piece)) =>
        piece.role match
          case Role.King | Role.Pawn => acc
          case _                     => acc + materialValue(piece.role)
      }
    val queensOnBoard = pieces.count(_._2.role == Role.Queen)
    if totalNonPawnMaterial <= 2600 || (queensOnBoard == 0 && totalNonPawnMaterial <= 3600) then GamePhase.Endgame
    else GamePhase.Middlegame

  def kingBonus(sq: Square, color: Color, phase: GamePhase): Int =
    val idx = pstIndex(sq, color)
    phase match
      case GamePhase.Middlegame => kingTable(idx)
      case GamePhase.Endgame    => kingEndgameTable(idx)

  /** Net score from `color`'s perspective. */
  def evaluate(board: Board, color: Color): Int =
    val currentPhase = phase(board)
    val baseScore = Square.all.foldLeft(0) { (acc, sq) =>
      board.pieceAt(sq) match
        case Some(p) if p.color == color =>
          acc + materialValue(p.role) + positionalBonus(board, p.role, sq, color, currentPhase)
        case Some(p) =>
          acc - materialValue(p.role) - positionalBonus(board, p.role, sq, p.color, currentPhase)
        case None => acc
    }
    baseScore + conversionBonus(board, color, currentPhase) - conversionBonus(board, color.opposite, currentPhase)

  private def positionalBonus(
      board: Board,
      role: Role,
      sq: Square,
      color: Color,
      currentPhase: GamePhase
  ): Int =
    val tableBonus =
      role match
        case Role.King => kingBonus(sq, color, currentPhase)
        case _         => pstBonus(role, sq, color)
    val endgamePawnBonus =
      if currentPhase == GamePhase.Endgame && role == Role.Pawn then passedPawnBonus(board, sq, color) else 0
    tableBonus + endgamePawnBonus

  private def passedPawnBonus(board: Board, sq: Square, color: Color): Int =
    if !isPassedPawn(board, sq, color) then 0
    else
      val advancement =
        color match
          case Color.White => sq.rank.index - 1
          case Color.Black => 8 - sq.rank.index
      20 + (advancement * 15)

  private def conversionBonus(board: Board, color: Color, currentPhase: GamePhase): Int =
    if currentPhase != GamePhase.Endgame then 0
    else
      val pieces = occupiedSquares(board)
      val ownPieces = pieces.collect { case (square, piece) if piece.color == color => square -> piece }
      val opponentPieces = pieces.collect { case (square, piece) if piece.color != color => square -> piece }
      val materialLead = materialBalanceFor(board, color)
      kingCentralizationBonus(ownPieces) +
        passedPawnSupportBonus(board, ownPieces, color) +
        exchangeWhenAheadBonus(materialLead, ownPieces, opponentPieces)

  private def isPassedPawn(board: Board, sq: Square, color: Color): Boolean =
    val relevantFiles = List(sq.file.index - 1, sq.file.index, sq.file.index + 1).filter(index => index >= 1 && index <= 8)
    val enemyColor = color.opposite
    val blockingRanks =
      color match
        case Color.White => (sq.rank.index + 1) to 8
        case Color.Black => 1 until sq.rank.index

    !relevantFiles.exists { fileIndex =>
      blockingRanks.exists { rankIndex =>
        val candidate = Square.fromCoords(fileIndex, rankIndex).get
        board.pieceAt(candidate).exists(piece => piece.color == enemyColor && piece.role == Role.Pawn)
      }
    }

  private def occupiedSquares(board: Board): Vector[(Square, Piece)] =
    Square.all.flatMap(square => board.pieceAt(square).map(piece => square -> piece))

  private def materialBalanceFor(board: Board, color: Color): Int =
    occupiedSquares(board).foldLeft(0) { case (acc, (_, piece)) =>
      piece.role match
        case Role.King => acc
        case _ if piece.color == color => acc + materialValue(piece.role)
        case _                         => acc - materialValue(piece.role)
    }

  private def kingCentralizationBonus(ownPieces: Vector[(Square, Piece)]): Int =
    ownPieces.collectFirst { case (sq, Piece(Role.King, _)) =>
      val fileDistance = (sq.file.index - 4.5).abs
      val rankDistance = (sq.rank.index - 4.5).abs
      ((4.0 - (fileDistance + rankDistance) / 2.0) * 8.0).toInt.max(0)
    }.getOrElse(0)

  private def passedPawnSupportBonus(
      board: Board,
      ownPieces: Vector[(Square, Piece)],
      color: Color
  ): Int =
    val rookSquares = ownPieces.collect { case (sq, Piece(Role.Rook, _)) => sq }
    ownPieces.collect { case (pawnSquare, Piece(Role.Pawn, _)) if isPassedPawn(board, pawnSquare, color) =>
      val rookBehind = rookSquares.exists(rookSquare => isRookBehindPassedPawn(rookSquare, pawnSquare, color))
      val connected = ownPieces.exists {
        case (otherSquare, Piece(Role.Pawn, _)) =>
          otherSquare != pawnSquare && isPassedPawn(board, otherSquare, color) && areConnectedPawns(pawnSquare, otherSquare)
        case _ => false
      }
      (if rookBehind then 25 else 0) + (if connected then 20 else 0)
    }.sum

  private def exchangeWhenAheadBonus(
      materialLead: Int,
      ownPieces: Vector[(Square, Piece)],
      opponentPieces: Vector[(Square, Piece)]
  ): Int =
    if materialLead < 200 then 0
    else
      val ownNonPawnPieces = ownPieces.count { case (_, piece) => piece.role != Role.King && piece.role != Role.Pawn }
      val oppNonPawnPieces = opponentPieces.count { case (_, piece) => piece.role != Role.King && piece.role != Role.Pawn }
      (4 - oppNonPawnPieces).max(0) * 10 - ownNonPawnPieces.max(0) * 2

  private def isRookBehindPassedPawn(rookSquare: Square, pawnSquare: Square, color: Color): Boolean =
    rookSquare.file == pawnSquare.file &&
      (color match
        case Color.White => rookSquare.rank.index < pawnSquare.rank.index
        case Color.Black => rookSquare.rank.index > pawnSquare.rank.index)

  private def areConnectedPawns(left: Square, right: Square): Boolean =
    (left.file.index - right.file.index).abs == 1 && (left.rank.index - right.rank.index).abs <= 1
