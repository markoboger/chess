package chess.controller.strategy

/** Exposes the endgame-tuned minimax variant as a distinct selectable strategy.
  *
  * The search itself is the same alpha-beta minimax skeleton, but on this branch it benefits from:
  * - phase-aware evaluation
  * - endgame king activity bonuses
  * - passed-pawn and conversion heuristics
  * - draw-avoidance when ahead
  */
class EndgameMinimaxStrategy(depth: Int = 3) extends MinimaxStrategy(depth):
  override val name: String = s"Endgame Minimax (d=$depth)"
