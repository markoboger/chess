package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, Role, PromotableRole}
import scala.util.Random

/** Greedy capture strategy: prefers captures ordered by the value of the captured piece (highest first). Falls back to
  * a random move when no capture is available.
  *
  * Piece values: Q=9, R=5, B=3, N=3, P=1.
  */
class GreedyStrategy extends MoveStrategy:
  val name = "Greedy"

  private val pieceValue: Role => Int = {
    case Role.Queen  => 9
    case Role.Rook   => 5
    case Role.Bishop => 3
    case Role.Knight => 3
    case Role.Pawn   => 1
    case Role.King   => 0
  }

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = board.legalMoves(color)
    if moves.isEmpty then None
    else
      // Partition into captures and quiet moves
      val captures = moves.flatMap { (from, to) =>
        board.pieceAt(to).map(captured => (from, to, pieceValue(captured.role)))
      }

      val (from, to) =
        if captures.nonEmpty then
          // Pick the highest-value capture (break ties randomly)
          val best = captures.maxBy(_._3)._3
          val bestCaptures = captures.filter(_._3 == best)
          val pick = bestCaptures(Random.nextInt(bestCaptures.length))
          (pick._1, pick._2)
        else moves(Random.nextInt(moves.length))

      Some((from, to, MoveStrategy.promotionFor(board, from, to, color)))
