package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole, MoveResult}
import SearchSupport.SearchMode

/** Minimax with alpha-beta pruning extended by a quiescence search at the leaves.
  *
  * At depth 0, instead of returning the static evaluation immediately, the engine keeps searching *captures only* until
  * the position is "quiet" (no captures remain or the max quiescence depth is reached). This eliminates the horizon
  * effect where a fixed-depth search misses that a piece is recaptured on the very next move.
  *
  * @param depth
  *   Main search depth in plies (half-moves).
  * @param qDepth
  *   Maximum additional plies of capture-only search (default 6).
  */
class QuiescenceStrategy(val depth: Int = 3, val qDepth: Int = 6) extends MoveStrategy:
  val name = s"Minimax+QSearch (d=$depth)"

  private val INF = Int.MaxValue / 2

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = SearchSupport.legalSearchMoves(board, color)
    if moves.isEmpty then return None

    val (_, bestMoves) =
      moves.foldLeft((-INF, List.empty[(Square, Square, Option[PromotableRole])])) { case ((bestScore, bestMoves), move) =>
        val score = alphaBeta(move.board, depth - 1, -INF, INF, SearchMode.Minimize, color)
        SearchSupport.updateBestMoves(bestScore, bestMoves, move, score)
      }
    SearchSupport.chooseRandom(bestMoves)

  private def alphaBeta(
      board: Board,
      depth: Int,
      alpha: Int,
      beta: Int,
      mode: SearchMode,
      rootColor: Color
  ): Int =
    if depth == 0 then return quiescence(board, alpha, beta, mode, rootColor, qDepth)

    val currentColor = mode.currentColor(rootColor)
    SearchSupport
      .terminalScore(board, currentColor, mode, depth, INF)
      .getOrElse {
        val moves = SearchSupport.legalSearchMoves(board, currentColor)
        SearchSupport.searchChildren(moves, mode, alpha, beta, INF) { (move, currentAlpha, currentBeta) =>
          alphaBeta(move.board, depth - 1, currentAlpha, currentBeta, mode.next, rootColor)
        }
      }

  /** Search captures only until quiet, to avoid the horizon effect. */
  private def quiescence(
      board: Board,
      alpha: Int,
      beta: Int,
      mode: SearchMode,
      rootColor: Color,
      remaining: Int
  ): Int =
    val standPat = Evaluator.evaluate(board, rootColor)

    if mode == SearchMode.Maximize then
      if standPat >= beta then return beta
      if remaining == 0 then return standPat
      val captures = captureMoves(board, rootColor)
      if captures.isEmpty then return alpha.max(standPat)
      SearchSupport.searchChildren(captures, SearchMode.Maximize, alpha.max(standPat), beta, INF) { (move, currentAlpha, currentBeta) =>
        quiescence(move.board, currentAlpha, currentBeta, SearchMode.Minimize, rootColor, remaining - 1)
      }
    else
      if standPat <= alpha then return alpha
      if remaining == 0 then return standPat
      val captures = captureMoves(board, rootColor.opposite)
      if captures.isEmpty then return beta.min(standPat)
      SearchSupport.searchChildren(captures, SearchMode.Minimize, alpha, beta.min(standPat), INF) { (move, currentAlpha, currentBeta) =>
        quiescence(move.board, currentAlpha, currentBeta, SearchMode.Maximize, rootColor, remaining - 1)
      }

  private def captureMoves(board: Board, color: Color): Vector[SearchSupport.SearchMove] =
    SearchSupport.legalSearchMoves(board, color).filter(move => board.pieceAt(move.to).isDefined)
