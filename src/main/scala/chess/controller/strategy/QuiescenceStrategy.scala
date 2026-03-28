package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, Role, PromotableRole, MoveResult}
import scala.util.Random

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
    val moves = board.legalMoves(color)
    if moves.isEmpty then return None

    var bestScore = -INF
    var bestMoves = List.empty[(Square, Square, Option[PromotableRole])]

    for (from, to) <- moves do
      val promo = MoveStrategy.promotionFor(board, from, to, color)
      board.move(from, to, promo) match
        case MoveResult.Moved(newBoard, _) =>
          val score = alphaBeta(newBoard, depth - 1, -INF, INF, maximizing = false, rootColor = color)
          if score > bestScore then
            bestScore = score
            bestMoves = List((from, to, promo))
          else if score == bestScore then bestMoves = (from, to, promo) :: bestMoves
        case _ => ()

    if bestMoves.isEmpty then None
    else Some(bestMoves(Random.nextInt(bestMoves.length)))

  private def alphaBeta(
      board: Board,
      depth: Int,
      alpha: Int,
      beta: Int,
      maximizing: Boolean,
      rootColor: Color
  ): Int =
    if depth == 0 then return quiescence(board, alpha, beta, maximizing, rootColor, qDepth)

    val currentColor = if maximizing then rootColor else rootColor.opposite
    val moves = board.legalMoves(currentColor)

    if moves.isEmpty then
      return if board.isInCheck(currentColor) then if maximizing then -INF + (depth * 100) else INF - (depth * 100)
      else 0

    if maximizing then
      var best = -INF
      var a = alpha
      var done = false
      val iter = moves.iterator
      while !done && iter.hasNext do
        val (from, to) = iter.next()
        val promo = MoveStrategy.promotionFor(board, from, to, currentColor)
        board.move(from, to, promo) match
          case MoveResult.Moved(newBoard, _) =>
            val score = alphaBeta(newBoard, depth - 1, a, beta, maximizing = false, rootColor)
            if score > best then best = score
            if best > a then a = best
            if a >= beta then done = true
          case _ => ()
      best
    else
      var best = INF
      var b = beta
      var done = false
      val iter = moves.iterator
      while !done && iter.hasNext do
        val (from, to) = iter.next()
        val promo = MoveStrategy.promotionFor(board, from, to, currentColor)
        board.move(from, to, promo) match
          case MoveResult.Moved(newBoard, _) =>
            val score = alphaBeta(newBoard, depth - 1, alpha, b, maximizing = true, rootColor)
            if score < best then best = score
            if best < b then b = best
            if b <= alpha then done = true
          case _ => ()
      best

  /** Search captures only until quiet, to avoid the horizon effect. */
  private def quiescence(
      board: Board,
      alpha: Int,
      beta: Int,
      maximizing: Boolean,
      rootColor: Color,
      remaining: Int
  ): Int =
    val standPat = Evaluator.evaluate(board, rootColor)

    if maximizing then
      if standPat >= beta then return beta
      if remaining == 0 then return standPat
      var a = alpha.max(standPat)
      val currentColor = rootColor
      val captures = board
        .legalMoves(currentColor)
        .filter((_, to) => board.pieceAt(to).isDefined)
      var done = false
      val iter = captures.iterator
      while !done && iter.hasNext do
        val (from, to) = iter.next()
        val promo = MoveStrategy.promotionFor(board, from, to, currentColor)
        board.move(from, to, promo) match
          case MoveResult.Moved(newBoard, _) =>
            val score = quiescence(newBoard, a, beta, maximizing = false, rootColor, remaining - 1)
            if score > a then a = score
            if a >= beta then done = true
          case _ => ()
      a
    else
      if standPat <= alpha then return alpha
      if remaining == 0 then return standPat
      var b = beta.min(standPat)
      val currentColor = rootColor.opposite
      val captures = board
        .legalMoves(currentColor)
        .filter((_, to) => board.pieceAt(to).isDefined)
      var done = false
      val iter = captures.iterator
      while !done && iter.hasNext do
        val (from, to) = iter.next()
        val promo = MoveStrategy.promotionFor(board, from, to, currentColor)
        board.move(from, to, promo) match
          case MoveResult.Moved(newBoard, _) =>
            val score = quiescence(newBoard, alpha, b, maximizing = true, rootColor, remaining - 1)
            if score < b then b = score
            if b <= alpha then done = true
          case _ => ()
      b
