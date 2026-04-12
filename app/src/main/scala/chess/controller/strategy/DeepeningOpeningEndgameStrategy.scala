package chess.controller.strategy

import chess.application.opening.OpeningParser
import chess.controller.MoveStrategy
import chess.model.{Board, Color, Opening, PromotableRole, Square}

/** Unified "best of everything" strategy.
  *
  * Combines three complementary components into a single strategy:
  *
  *   - '''Opening''': uses [[OpeningContinuationStrategy]] (position-based book lookup) to play
  *     theoretically sound moves in the opening phase. Because the lookup is position-driven the
  *     strategy is stateless and works correctly even when the same instance is reused across
  *     multiple games.
  *
  *   - '''Iterative Deepening''': once the board position is no longer in the book, the engine
  *     searches using iterative deepening within the per-move clock budget (`timeLimitMs`), always
  *     returning the best fully-completed depth before the deadline.
  *
  *   - '''Endgame Evaluation''': at leaf nodes the search calls quiescence search backed by the
  *     full phase-aware evaluator (king activity, passed-pawn bonuses, conversion heuristics,
  *     draw-avoidance when ahead). See [[IterativeDeepeningEndgameStrategy]].
  *
  * `timeLimitMs` is a `var` so callers (e.g. [[chess.application.game.GameSessionService]]) can
  * adjust the per-move time budget based on remaining game-clock time without creating a new
  * instance and re-building the opening book index.
  */
class DeepeningOpeningEndgameStrategy(
    openings: List[Opening] = OpeningParser.parseLichessOpenings(),
    var timeLimitMs: Long = 2000L,
    val qDepth: Int = 6
) extends MoveStrategy:

  val name = "deepening-opening-endgame"

  // Shared ID+Endgame engine — timeLimitMs is updated each call before delegation
  private val idEndgame = new IterativeDeepeningEndgameStrategy(timeLimitMs, qDepth)

  // Position-based opening book with the shared engine as fallback
  private val book = new OpeningContinuationStrategy(openings, fallback = idEndgame)

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    idEndgame.timeLimitMs = timeLimitMs   // propagate current clock budget before each call
    book.selectMove(board, color)
