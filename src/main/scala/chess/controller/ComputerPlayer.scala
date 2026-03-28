package chess.controller

import chess.controller.strategy.RandomStrategy
import chess.model.{Board, Color, Square, PromotableRole}

/** Delegates move selection to the current [[MoveStrategy]].
  * The strategy can be swapped at any time (e.g., from the GUI menu).
  */
class ComputerPlayer(var strategy: MoveStrategy = new RandomStrategy):
  def move(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    strategy.selectMove(board, color)
