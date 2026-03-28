package chess.controller

import chess.controller.strategy.{RandomStrategy, Evaluator}
import chess.model.{Board, Color, Square, PromotableRole, MoveResult}

/** Delegates move selection to the current [[MoveStrategy]]. The strategy can be swapped at any time (e.g., from the
  * GUI menu).
  */
class ComputerPlayer(var strategy: MoveStrategy = new RandomStrategy):

  /** Centipawn lead required before the player actively avoids a draw by repetition. */
  private val RepetitionAvoidanceThreshold = 150

  def move(
      board: Board,
      color: Color,
      wouldRepeat: Board => Boolean = _ => false
  ): Option[(Square, Square, Option[PromotableRole])] =
    val candidate = strategy.selectMove(board, color)

    // Only try to avoid repetition when we have a meaningful material lead.
    val ahead = Evaluator.evaluate(board, color) >= RepetitionAvoidanceThreshold
    if !ahead then return candidate

    // Check whether the candidate move leads to a repeated position.
    val candidateRepeats = candidate.exists { case (from, to, promo) =>
      board.move(from, to, promo).toOption.exists(wouldRepeat)
    }
    if !candidateRepeats then return candidate

    // Find the best-scoring non-repeating legal move as a fallback.
    val alternatives = board.legalMoves(color).flatMap { case (from, to) =>
      val promo = MoveStrategy.promotionFor(board, from, to, color)
      board
        .move(from, to, promo)
        .toOption
        .filterNot(wouldRepeat)
        .map(newBoard => (from, to, promo, Evaluator.evaluate(newBoard, color)))
    }

    if alternatives.isEmpty then candidate // all moves repeat — accept the draw
    else
      val best = alternatives.maxBy(_._4)
      Some((best._1, best._2, best._3))
