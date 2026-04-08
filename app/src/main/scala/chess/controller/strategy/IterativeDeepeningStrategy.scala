package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole, MoveResult, GameEvent, CastlingRights, Piece}
import SearchSupport.SearchMode

/** Alpha-beta minimax with iterative deepening and a wall-clock time budget.
  *
  * Searches depth 1, 2, 3, … until `timeLimitMs` milliseconds have elapsed. When the clock expires mid-search, the last
  * *fully completed* depth's best move is returned, so the answer is always sound.
  *
  * `timeLimitMs` is a `var` so the caller (e.g. the GUI) can adjust it per-move based on remaining game-clock time
  * before calling [[chess.controller.ComputerPlayer.move]].
  */
class IterativeDeepeningStrategy(var timeLimitMs: Long = 2000L) extends MoveStrategy:

  val name = "Iterative Deepening"

  private val INF = Int.MaxValue / 2

  // Position key for repetition detection within the search path.
  // Includes castling rights and whose turn it is (maximizing).
  private type NodeKey = (Vector[Vector[Option[Piece]]], CastlingRights, Boolean)
  private def nodeKey(board: Board, maximizing: Boolean): NodeKey =
    (board.squares, board.castlingRights, maximizing)

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = SearchSupport.legalSearchMoves(board, color)
    if moves.isEmpty then return None

    val deadline = System.currentTimeMillis() + timeLimitMs

    // Depth-0 fallback: use the first legal move so we always have a result.
    var bestMove: Option[(Square, Square, Option[PromotableRole])] = moves.headOption.map(_.asTuple)

    var depth = 1
    var keepGoing = true

    while keepGoing do
      val (result, aborted) = searchAtDepth(board, color, depth, deadline)
      if !aborted then bestMove = result
      if aborted || System.currentTimeMillis() >= deadline then keepGoing = false
      else depth += 1

    bestMove

  /** One full alpha-beta pass at `depth`. Returns (best-move, was-aborted). */
  private def searchAtDepth(
      board: Board,
      color: Color,
      depth: Int,
      deadline: Long
  ): (Option[(Square, Square, Option[PromotableRole])], Boolean) =
    var aborted = false
    var mateFound = false

    // Seed the path with the root position (maximizing = true at root).
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
      case GameEvent.Stalemate => (0, false, false)
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
    if System.currentTimeMillis() >= deadline then return (0, true)

    // Detect repetition within the search path: this line is a draw.
    val key = nodeKey(board, mode == SearchMode.Maximize)
    if seenInPath.contains(key) then return (0, false)

    if depth == 0 then return (Evaluator.evaluate(board, rootColor), false)

    val currentColor = mode.currentColor(rootColor)
    SearchSupport
      .terminalScore(board, currentColor, mode, depth, INF)
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
      case GameEvent.Stalemate => (0, false)
      case _                   => alphaBeta(move.board, depth - 1, alpha, beta, mode.next, rootColor, deadline, nextSeen)
