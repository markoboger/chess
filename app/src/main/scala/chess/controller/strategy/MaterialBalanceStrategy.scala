package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, Role, PromotableRole, MoveResult}
import scala.util.Random

/** Depth-1 minimax with a pure material evaluation function.
  *
  * For every legal move, the resulting board is evaluated as: score = Σ(own piece values) − Σ(opponent piece values)
  *
  * The move with the highest score is chosen; ties are broken randomly.
  *
  * Piece values: Q=9, R=5, B=3, N=3, P=1.
  *
  * Improvement over [[GreedyStrategy]]: Greedy only considers what is captured on this move. MaterialBalance scores the
  * whole board after the move, so it also prefers moves that keep own pieces safe and naturally values promotions at
  * their full worth.
  */
class MaterialBalanceStrategy extends MoveStrategy:
  val name = "Material Balance"

  private val pieceValue: Role => Int = {
    case Role.Queen  => 9
    case Role.Rook   => 5
    case Role.Bishop => 3
    case Role.Knight => 3
    case Role.Pawn   => 1
    case Role.King   => 0
  }

  /** Net material score from `color`'s perspective. */
  private def evaluate(board: Board, color: Color): Int =
    Square.all.foldLeft(0) { (acc, sq) =>
      board.pieceAt(sq) match
        case Some(p) if p.color == color => acc + pieceValue(p.role)
        case Some(p) if p.color != color => acc - pieceValue(p.role)
        case _                           => acc
    }

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = board.legalMoves(color)
    if moves.isEmpty then return None

    val scored = moves.flatMap { (from, to) =>
      val promo = MoveStrategy.promotionFor(board, from, to, color)
      board.move(from, to, promo) match
        case MoveResult.Moved(newBoard, _) =>
          Some((from, to, promo, evaluate(newBoard, color)))
        case _ => None
    }

    if scored.isEmpty then return None

    val bestScore = scored.maxBy(_._4)._4
    val candidates = scored.filter(_._4 == bestScore)
    val (from, to, promo, _) = candidates(Random.nextInt(candidates.length))
    Some((from, to, promo))
