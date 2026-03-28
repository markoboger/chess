package chess.controller.strategy

import chess.model.{Board, Color, Square, Role}

/** Shared position evaluator used by depth-based strategies.
  *
  * Returns a score in centipawns from `color`'s perspective:
  *   positive = `color` is ahead, negative = opponent is ahead.
  *
  * Score = Σ own(material + PST bonus) − Σ opponent(material + PST bonus)
  */
object Evaluator:

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
     0,   0,   0,   0,   0,   0,   0,   0,
    50,  50,  50,  50,  50,  50,  50,  50,
    10,  10,  20,  30,  30,  20,  10,  10,
     5,   5,  10,  25,  25,  10,   5,   5,
     0,   0,   0,  20,  20,   0,   0,   0,
     5,  -5, -10,   0,   0, -10,  -5,   5,
     5,  10,  10, -20, -20,  10,  10,   5,
     0,   0,   0,   0,   0,   0,   0,   0
  )
  private val knightTable = Array(
    -50, -40, -30, -30, -30, -30, -40, -50,
    -40, -20,   0,   0,   0,   0, -20, -40,
    -30,   0,  10,  15,  15,  10,   0, -30,
    -30,   5,  15,  20,  20,  15,   5, -30,
    -30,   0,  15,  20,  20,  15,   0, -30,
    -30,   5,  10,  15,  15,  10,   5, -30,
    -40, -20,   0,   5,   5,   0, -20, -40,
    -50, -40, -30, -30, -30, -30, -40, -50
  )
  private val bishopTable = Array(
    -20, -10, -10, -10, -10, -10, -10, -20,
    -10,   0,   0,   0,   0,   0,   0, -10,
    -10,   0,   5,  10,  10,   5,   0, -10,
    -10,   5,   5,  10,  10,   5,   5, -10,
    -10,   0,  10,  10,  10,  10,   0, -10,
    -10,  10,  10,  10,  10,  10,  10, -10,
    -10,   5,   0,   0,   0,   0,   5, -10,
    -20, -10, -10, -10, -10, -10, -10, -20
  )
  private val rookTable = Array(
     0,   0,   0,   0,   0,   0,   0,   0,
     5,  10,  10,  10,  10,  10,  10,   5,
    -5,   0,   0,   0,   0,   0,   0,  -5,
    -5,   0,   0,   0,   0,   0,   0,  -5,
    -5,   0,   0,   0,   0,   0,   0,  -5,
    -5,   0,   0,   0,   0,   0,   0,  -5,
    -5,   0,   0,   0,   0,   0,   0,  -5,
     0,   0,   0,   5,   5,   0,   0,   0
  )
  private val queenTable = Array(
    -20, -10, -10,  -5,  -5, -10, -10, -20,
    -10,   0,   0,   0,   0,   0,   0, -10,
    -10,   0,   5,   5,   5,   5,   0, -10,
     -5,   0,   5,   5,   5,   5,   0,  -5,
      0,   0,   5,   5,   5,   5,   0,  -5,
    -10,   5,   5,   5,   5,   5,   0, -10,
    -10,   0,   5,   0,   0,   0,   0, -10,
    -20, -10, -10,  -5,  -5, -10, -10, -20
  )
  private val kingTable = Array(
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -30, -40, -40, -50, -50, -40, -40, -30,
    -20, -30, -30, -40, -40, -30, -30, -20,
    -10, -20, -20, -20, -20, -20, -20, -10,
     20,  20,   0,   0,   0,   0,  20,  20,
     20,  30,  10,   0,   0,  10,  30,  20
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

  /** Net score from `color`'s perspective. */
  def evaluate(board: Board, color: Color): Int =
    Square.all.foldLeft(0) { (acc, sq) =>
      board.pieceAt(sq) match
        case Some(p) if p.color == color =>
          acc + materialValue(p.role) + pstBonus(p.role, sq, color)
        case Some(p) =>
          acc - materialValue(p.role) - pstBonus(p.role, sq, p.color)
        case None => acc
    }
