package chess.controller.strategy

import chess.model.{Board, Color}

/** Shared policy for scoring drawish outcomes from the root side's perspective.
  *
  * A draw is not always neutral in practice:
  * - when we are clearly better, a draw is a disappointment and should be slightly penalized
  * - when we are clearly worse, a draw is a good escape and should be slightly rewarded
  */
object DrawPolicy:
  private val MeaningfulLeadThreshold = 150
  private val DrawBias = 120

  def drawScore(board: Board, rootColor: Color): Int =
    val staticEval = Evaluator.evaluate(board, rootColor)
    if staticEval >= MeaningfulLeadThreshold then -DrawBias
    else if staticEval <= -MeaningfulLeadThreshold then DrawBias
    else 0

  def repetitionScore(board: Board, rootColor: Color): Int =
    drawScore(board, rootColor)
