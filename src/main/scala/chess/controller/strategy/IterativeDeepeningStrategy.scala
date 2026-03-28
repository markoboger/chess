package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole, MoveResult}
import scala.util.Random

/** Alpha-beta minimax with iterative deepening and a wall-clock time budget.
  *
  * Searches depth 1, 2, 3, … until `timeLimitMs` milliseconds have elapsed.
  * When the clock expires mid-search, the last *fully completed* depth's best
  * move is returned, so the answer is always sound.
  *
  * `timeLimitMs` is a `var` so the caller (e.g. the GUI) can adjust it
  * per-move based on remaining game-clock time before calling
  * [[chess.controller.ComputerPlayer.move]].
  */
class IterativeDeepeningStrategy(var timeLimitMs: Long = 2000L) extends MoveStrategy:

  val name = "Iterative Deepening"

  private val INF = Int.MaxValue / 2

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    val moves = board.legalMoves(color)
    if moves.isEmpty then return None

    val deadline = System.currentTimeMillis() + timeLimitMs

    // Depth-0 fallback: use the first legal move so we always have a result.
    var bestMove: Option[(Square, Square, Option[PromotableRole])] =
      moves.headOption.map { (from, to) =>
        (from, to, MoveStrategy.promotionFor(board, from, to, color))
      }

    var depth     = 1
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
    var bestScore = -INF
    var bestMoves = List.empty[(Square, Square, Option[PromotableRole])]
    var aborted   = false

    val iter = board.legalMoves(color).iterator
    while !aborted && iter.hasNext do
      val (from, to) = iter.next()
      val promo = MoveStrategy.promotionFor(board, from, to, color)
      board.move(from, to, promo) match
        case MoveResult.Moved(newBoard, _) =>
          val (score, ab) =
            alphaBeta(newBoard, depth - 1, -INF, INF, maximizing = false, color, deadline)
          if ab then aborted = true
          else
            if score > bestScore then
              bestScore = score
              bestMoves = List((from, to, promo))
            else if score == bestScore then
              bestMoves = (from, to, promo) :: bestMoves
        case _ => ()

    val result =
      if bestMoves.isEmpty then None
      else Some(bestMoves(Random.nextInt(bestMoves.length)))
    (result, aborted)

  private def alphaBeta(
      board: Board,
      depth: Int,
      alpha: Int,
      beta: Int,
      maximizing: Boolean,
      rootColor: Color,
      deadline: Long
  ): (Int, Boolean) =
    if System.currentTimeMillis() >= deadline then return (0, true)
    if depth == 0 then return (Evaluator.evaluate(board, rootColor), false)

    val currentColor = if maximizing then rootColor else rootColor.opposite
    val moves        = board.legalMoves(currentColor)

    if moves.isEmpty then
      val score =
        if board.isInCheck(currentColor) then
          if maximizing then -INF + (depth * 100) else INF - (depth * 100)
        else 0
      return (score, false)

    if maximizing then
      var best    = -INF
      var a       = alpha
      var done    = false
      var aborted = false
      val iter    = moves.iterator
      while !done && !aborted && iter.hasNext do
        val (from, to) = iter.next()
        val promo = MoveStrategy.promotionFor(board, from, to, currentColor)
        board.move(from, to, promo) match
          case MoveResult.Moved(newBoard, _) =>
            val (score, ab) =
              alphaBeta(newBoard, depth - 1, a, beta, maximizing = false, rootColor, deadline)
            if ab then aborted = true
            else
              if score > best then best = score
              if best > a    then a    = best
              if a >= beta   then done = true
          case _ => ()
      (best, aborted)
    else
      var best    = INF
      var b       = beta
      var done    = false
      var aborted = false
      val iter    = moves.iterator
      while !done && !aborted && iter.hasNext do
        val (from, to) = iter.next()
        val promo = MoveStrategy.promotionFor(board, from, to, currentColor)
        board.move(from, to, promo) match
          case MoveResult.Moved(newBoard, _) =>
            val (score, ab) =
              alphaBeta(newBoard, depth - 1, alpha, b, maximizing = true, rootColor, deadline)
            if ab then aborted = true
            else
              if score < best then best = score
              if best < b    then b    = best
              if b <= alpha  then done = true
          case _ => ()
      (best, aborted)
