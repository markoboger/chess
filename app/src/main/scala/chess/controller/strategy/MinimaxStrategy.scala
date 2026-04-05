package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole, MoveResult, GameEvent, CastlingRights, Piece}
import scala.util.Random

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
    val moves = board.legalMoves(color)
    if moves.isEmpty then return None

    var bestScore = -INF
    var bestMoves = List.empty[(Square, Square, Option[PromotableRole])]
    val rootPath: Set[NodeKey] = Set(nodeKey(board, maximizing = true))

    for (from, to) <- moves do
      val promo = MoveStrategy.promotionFor(board, from, to, color)
      board.move(from, to, promo).movedOption.foreach { (newBoard, event) =>
        val score = event match
          case GameEvent.Checkmate => INF
          case GameEvent.Stalemate => 0
          case _ => alphaBeta(newBoard, depth - 1, -INF, INF, maximizing = false, rootColor = color, rootPath)
        if score > bestScore then
          bestScore = score
          bestMoves = List((from, to, promo))
        else if score == bestScore then bestMoves = (from, to, promo) :: bestMoves
      }

    if bestMoves.isEmpty then None
    else Some(bestMoves(Random.nextInt(bestMoves.length)))

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
      maximizing: Boolean,
      rootColor: Color,
      seenInPath: Set[NodeKey]
  ): Int =
    // Detect repetition within the search path: this line is a draw.
    val key = nodeKey(board, maximizing)
    if seenInPath.contains(key) then return 0

    if depth == 0 then return Evaluator.evaluate(board, rootColor)

    val currentColor = if maximizing then rootColor else rootColor.opposite
    val moves = board.legalMoves(currentColor)

    // Terminal node: checkmate or stalemate
    if moves.isEmpty then
      return if board.isInCheck(currentColor) then
        if maximizing then -INF + (depth * 100) // prefer faster mates
        else INF - (depth * 100)
      else 0 // stalemate

    val nextSeen = seenInPath + key

    if maximizing then
      var best = -INF
      var a = alpha
      var done = false
      val iter = moves.iterator
      while !done && iter.hasNext do
        val (from, to) = iter.next()
        val promo = MoveStrategy.promotionFor(board, from, to, currentColor)
        board.move(from, to, promo).movedOption.foreach { (newBoard, event) =>
          val score = event match
            case GameEvent.Checkmate => INF - (depth * 100)
            case GameEvent.Stalemate => 0
            case _                   => alphaBeta(newBoard, depth - 1, a, beta, maximizing = false, rootColor, nextSeen)
          if score > best then best = score
          if best > a then a = best
          if a >= beta then done = true
        }
      best
    else
      var best = INF
      var b = beta
      var done = false
      val iter = moves.iterator
      while !done && iter.hasNext do
        val (from, to) = iter.next()
        val promo = MoveStrategy.promotionFor(board, from, to, currentColor)
        board.move(from, to, promo).movedOption.foreach { (newBoard, event) =>
          val score = event match
            case GameEvent.Checkmate => -INF + (depth * 100)
            case GameEvent.Stalemate => 0
            case _                   => alphaBeta(newBoard, depth - 1, alpha, b, maximizing = true, rootColor, nextSeen)
          if score < best then best = score
          if best < b then b = best
          if b <= alpha then done = true
        }
      best
