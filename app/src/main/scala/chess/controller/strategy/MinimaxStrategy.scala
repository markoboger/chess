package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole, MoveResult, GameEvent, CastlingRights, Piece}
import SearchSupport.SearchMode

/** Minimax search with alpha-beta pruning, evaluated with material + PST.
  *
  * @param depth
  *   Search depth in plies (half-moves). Depth 2 is fast; depth 3 plays noticeable tactics; depth 4 is stronger but
  *   slower (seconds per move without move ordering).
  *
  * Alpha-beta pruning skips branches that cannot affect the result, roughly halving the effective branching factor vs
  * plain minimax.
  */
class MinimaxStrategy(val depth: Int = 3) extends MoveStrategy:
  val name = s"Minimax (d=$depth)"

  private val INF = Int.MaxValue / 2

  private type NodeKey = (Vector[Vector[Option[Piece]]], CastlingRights, Boolean)
  private def nodeKey(board: Board, maximizing: Boolean): NodeKey =
    (board.squares, board.castlingRights, maximizing)

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = SearchSupport.legalSearchMoves(board, color)
    if moves.isEmpty then None
    else
      val rootPath: Set[NodeKey] = Set(nodeKey(board, maximizing = true))
      val (_, bestMoves) =
        moves.foldLeft((-INF, List.empty[(Square, Square, Option[PromotableRole])])) { case ((bestScore, bestMoves), move) =>
          val score = rootMoveScore(move, color, rootPath)
          SearchSupport.updateBestMoves(bestScore, bestMoves, move, score)
        }
      SearchSupport.chooseRandom(bestMoves)

  private def rootMoveScore(
      move: SearchSupport.SearchMove,
      color: Color,
      rootPath: Set[NodeKey]
  ): Int =
    move.event match
      case GameEvent.Checkmate => INF
      case GameEvent.Stalemate => DrawPolicy.drawScore(move.board, color)
      case _ =>
        alphaBeta(move.board, depth - 1, -INF, INF, SearchMode.Minimize, color, rootPath)

  /** Alpha-beta minimax.
    *
    * @param board
    *   Position to evaluate
    * @param depth
    *   Remaining depth (0 = leaf, evaluate statically)
    * @param alpha
    *   Best score the maximiser has found so far
    * @param beta
    *   Best score the minimiser has found so far
    * @param maximizing
    *   True when it is `rootColor`'s turn
    * @param rootColor
    *   The side that called `selectMove`
    */
  private def alphaBeta(
      board: Board,
      depth: Int,
      alpha: Int,
      beta: Int,
      mode: SearchMode,
      rootColor: Color,
      seenInPath: Set[NodeKey]
  ): Int =
    // Detect repetition within the search path: this line is a draw.
    val key = nodeKey(board, mode == SearchMode.Maximize)
    if seenInPath.contains(key) then DrawPolicy.repetitionScore(board, rootColor)
    else if depth == 0 then Evaluator.evaluate(board, rootColor)
    else
      val currentColor = mode.currentColor(rootColor)
      SearchSupport
        .terminalScore(board, currentColor, mode, depth, INF, rootColor)
        .getOrElse {
          val nextSeen = seenInPath + key
          val moves = SearchSupport.legalSearchMoves(board, currentColor)
          SearchSupport.searchChildren(moves, mode, alpha, beta, INF) { (move, currentAlpha, currentBeta) =>
            childScore(move, depth, mode, currentAlpha, currentBeta, rootColor, nextSeen)
          }
        }

  private def childScore(
      move: SearchSupport.SearchMove,
      depth: Int,
      mode: SearchMode,
      alpha: Int,
      beta: Int,
      rootColor: Color,
      nextSeen: Set[NodeKey]
  ): Int =
    move.event match
      case GameEvent.Checkmate => mode.childCheckmateScore(INF, depth)
      case GameEvent.Stalemate => DrawPolicy.drawScore(move.board, rootColor)
      case _                   => alphaBeta(move.board, depth - 1, alpha, beta, mode.next, rootColor, nextSeen)
