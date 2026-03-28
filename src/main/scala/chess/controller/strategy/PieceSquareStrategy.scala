package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole, MoveResult}
import scala.util.Random

/** Depth-1 evaluation using material balance and piece-square tables. Uses [[Evaluator]] for scoring; see that object
  * for table details.
  */
class PieceSquareStrategy extends MoveStrategy:
  val name = "Piece-Square Tables"

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = board.legalMoves(color)
    if moves.isEmpty then return None

    val scored = moves.flatMap { (from, to) =>
      val promo = MoveStrategy.promotionFor(board, from, to, color)
      board.move(from, to, promo) match
        case MoveResult.Moved(newBoard, _) =>
          Some((from, to, promo, Evaluator.evaluate(newBoard, color)))
        case _ => None
    }

    if scored.isEmpty then return None

    val bestScore = scored.maxBy(_._4)._4
    val candidates = scored.filter(_._4 == bestScore)
    val (from, to, promo, _) = candidates(Random.nextInt(candidates.length))
    Some((from, to, promo))
