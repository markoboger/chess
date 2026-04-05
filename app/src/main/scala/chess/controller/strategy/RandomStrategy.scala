package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole}
import scala.util.Random

/** Picks uniformly at random from all legal moves. */
class RandomStrategy extends MoveStrategy:
  val name = "Random"

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = board.legalMoves(color)
    if moves.isEmpty then None
    else
      val (from, to) = moves(Random.nextInt(moves.length))
      Some((from, to, MoveStrategy.promotionFor(board, from, to, color)))
