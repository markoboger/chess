package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole, GameEvent, CastlingRights, Piece}
import SearchSupport.SearchMode

/** Iterative deepening with quiescence search at the leaves and the full phase-aware endgame
  * evaluator.
  *
  * Combines the strengths of three existing strategies:
  * - [[IterativeDeepeningStrategy]]: searches deeper within a wall-clock budget, always returning
  *   the best fully-completed depth result.
  * - [[QuiescenceStrategy]]: extends leaf nodes with capture/check/promotion sequences to avoid the
  *   horizon effect.
  * - [[EndgameMinimaxStrategy]]: phase-aware evaluation with endgame king activity, passed-pawn
  *   bonuses, conversion heuristics, and draw avoidance when ahead.
  *
  * `timeLimitMs` is a `var` so callers can adjust it per-move based on remaining game-clock time.
  */
class IterativeDeepeningEndgameStrategy(var timeLimitMs: Long = 2000L, val qDepth: Int = 6) extends MoveStrategy:

  val name = "ID+Endgame"

  private val INF = Int.MaxValue / 2

  private type NodeKey = (Vector[Vector[Option[Piece]]], CastlingRights, Boolean)
  private def nodeKey(board: Board, maximizing: Boolean): NodeKey =
    (board.squares, board.castlingRights, maximizing)

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = SearchSupport.legalSearchMoves(board, color)
    if moves.isEmpty then None
    else
      val deadline = System.currentTimeMillis() + timeLimitMs
      var bestMove: Option[(Square, Square, Option[PromotableRole])] = moves.headOption.map(_.asTuple)

      var depth = 1
      var keepGoing = true

      while keepGoing do
        val (result, aborted) = searchAtDepth(board, color, depth, deadline)
        if !aborted then bestMove = result
        if aborted || System.currentTimeMillis() >= deadline then keepGoing = false
        else depth += 1

      bestMove

  private def searchAtDepth(
      board: Board,
      color: Color,
      depth: Int,
      deadline: Long
  ): (Option[(Square, Square, Option[PromotableRole])], Boolean) =
    var aborted = false
    var mateFound = false
    val rootPath: Set[NodeKey] = Set(nodeKey(board, maximizing = true))

    var bestScore = -INF
    var bestMoves = List.empty[(Square, Square, Option[PromotableRole])]
    val iter = SearchSupport.legalSearchMoves(board, color).iterator
    while !aborted && iter.hasNext do
      val move = iter.next()
      val (score, wasAborted, foundMate) = rootMoveScore(move, depth, color, deadline, rootPath)
      if foundMate then
        bestScore = INF
        bestMoves = List(move.asTuple)
        mateFound = true
        aborted = true
      else if wasAborted then aborted = true
      else
        val updated = SearchSupport.updateBestMoves(bestScore, bestMoves, move, score)
        bestScore = updated._1
        bestMoves = updated._2

    val result = SearchSupport.chooseRandom(bestMoves)
    (result, aborted && !mateFound)

  private def rootMoveScore(
      move: SearchSupport.SearchMove,
      depth: Int,
      color: Color,
      deadline: Long,
      rootPath: Set[NodeKey]
  ): (Int, Boolean, Boolean) =
    move.event match
      case GameEvent.Checkmate => (INF, false, true)
      case GameEvent.Stalemate => (DrawPolicy.drawScore(move.board, color), false, false)
      case _ =>
        val (score, aborted) =
          alphaBeta(move.board, depth - 1, -INF, INF, SearchMode.Minimize, color, deadline, rootPath)
        (score, aborted, false)

  private def alphaBeta(
      board: Board,
      depth: Int,
      alpha: Int,
      beta: Int,
      mode: SearchMode,
      rootColor: Color,
      deadline: Long,
      seenInPath: Set[NodeKey]
  ): (Int, Boolean) =
    if System.currentTimeMillis() >= deadline then (0, true)
    else
      val key = nodeKey(board, mode == SearchMode.Maximize)
      if seenInPath.contains(key) then (DrawPolicy.repetitionScore(board, rootColor), false)
      else if depth == 0 then (quiescence(board, alpha, beta, mode, rootColor, qDepth), false)
      else
        val currentColor = mode.currentColor(rootColor)
        SearchSupport
          .terminalScore(board, currentColor, mode, depth, INF, rootColor)
          .map((_, false))
          .getOrElse {
            val nextSeen = seenInPath + key
            val moves = SearchSupport.legalSearchMoves(board, currentColor)
            SearchSupport.searchChildrenUntilDeadline(moves, mode, alpha, beta, INF) { (move, currentAlpha, currentBeta) =>
              childScore(move, depth, mode, currentAlpha, currentBeta, rootColor, deadline, nextSeen)
            }
          }

  private def childScore(
      move: SearchSupport.SearchMove,
      depth: Int,
      mode: SearchMode,
      alpha: Int,
      beta: Int,
      rootColor: Color,
      deadline: Long,
      nextSeen: Set[NodeKey]
  ): (Int, Boolean) =
    move.event match
      case GameEvent.Checkmate => (mode.childCheckmateScore(INF, depth), false)
      case GameEvent.Stalemate => (DrawPolicy.drawScore(move.board, rootColor), false)
      case _                   => alphaBeta(move.board, depth - 1, alpha, beta, mode.next, rootColor, deadline, nextSeen)

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
      if standPat >= beta then beta
      else if remaining == 0 then standPat
      else
        val tacticalMoves =
          SearchSupport
            .legalSearchMoves(board, rootColor)
            .filter(m => QuiescenceStrategy.isTacticalMove(board, m))
        if tacticalMoves.isEmpty then alpha.max(standPat)
        else
          SearchSupport.searchChildren(tacticalMoves, SearchMode.Maximize, alpha.max(standPat), beta, INF) { (move, a, b) =>
            quiescence(move.board, a, b, SearchMode.Minimize, rootColor, remaining - 1)
          }
    else
      if standPat <= alpha then alpha
      else if remaining == 0 then standPat
      else
        val tacticalMoves =
          SearchSupport
            .legalSearchMoves(board, rootColor.opposite)
            .filter(m => QuiescenceStrategy.isTacticalMove(board, m))
        if tacticalMoves.isEmpty then beta.min(standPat)
        else
          SearchSupport.searchChildren(tacticalMoves, SearchMode.Minimize, alpha, beta.min(standPat), INF) { (move, a, b) =>
            quiescence(move.board, a, b, SearchMode.Maximize, rootColor, remaining - 1)
          }
