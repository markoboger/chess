package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, Square, PromotableRole, MoveResult, GameEvent, CastlingRights, Piece}
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

  // Position key for repetition detection within the search path.
  // Includes castling rights and whose turn it is (maximizing).
  private type NodeKey = (Vector[Vector[Option[Piece]]], CastlingRights, Boolean)
  private def nodeKey(board: Board, maximizing: Boolean): NodeKey =
    (board.squares, board.castlingRights, maximizing)

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
    var bestScore  = -INF
    var bestMoves  = List.empty[(Square, Square, Option[PromotableRole])]
    var aborted    = false
    var mateFound  = false

    // Seed the path with the root position (maximizing = true at root).
    val rootPath: Set[NodeKey] = Set(nodeKey(board, maximizing = true))

    val iter = board.legalMoves(color).iterator
    while !aborted && iter.hasNext do
      val (from, to) = iter.next()
      val promo = MoveStrategy.promotionFor(board, from, to, color)
      board.move(from, to, promo) match
        case MoveResult.Moved(_, GameEvent.Checkmate) =>
          // Mate in 1 found — nothing can be better, accept immediately.
          bestScore = INF
          bestMoves = List((from, to, promo))
          mateFound = true
          aborted   = true   // early exit; mateFound prevents discarding result
        case MoveResult.Moved(_, GameEvent.Stalemate) =>
          if 0 > bestScore then
            bestScore = 0
            bestMoves = List((from, to, promo))
          else if 0 == bestScore then
            bestMoves = (from, to, promo) :: bestMoves
        case MoveResult.Moved(newBoard, _) =>
          val (score, ab) =
            alphaBeta(newBoard, depth - 1, -INF, INF, maximizing = false, color, deadline, rootPath)
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
    (result, aborted && !mateFound)

  private def alphaBeta(
      board: Board,
      depth: Int,
      alpha: Int,
      beta: Int,
      maximizing: Boolean,
      rootColor: Color,
      deadline: Long,
      seenInPath: Set[NodeKey]
  ): (Int, Boolean) =
    if System.currentTimeMillis() >= deadline then return (0, true)

    // Detect repetition within the search path: this line is a draw.
    val key = nodeKey(board, maximizing)
    if seenInPath.contains(key) then return (0, false)

    if depth == 0 then return (Evaluator.evaluate(board, rootColor), false)

    val currentColor = if maximizing then rootColor else rootColor.opposite
    val moves        = board.legalMoves(currentColor)

    if moves.isEmpty then
      val score =
        if board.isInCheck(currentColor) then
          if maximizing then -INF + (depth * 100) else INF - (depth * 100)
        else 0
      return (score, false)

    val nextSeen = seenInPath + key

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
          case MoveResult.Moved(_, GameEvent.Checkmate) =>
            val score = INF - (depth * 100)
            if score > best then best = score
            if best > a    then a    = best
            if a >= beta   then done = true
          case MoveResult.Moved(_, GameEvent.Stalemate) =>
            if 0 > best then best = 0
            if best > a  then a   = best
            if a >= beta then done = true
          case MoveResult.Moved(newBoard, _) =>
            val (score, ab) =
              alphaBeta(newBoard, depth - 1, a, beta, maximizing = false, rootColor, deadline, nextSeen)
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
          case MoveResult.Moved(_, GameEvent.Checkmate) =>
            val score = -INF + (depth * 100)
            if score < best then best = score
            if best < b    then b    = best
            if b <= alpha  then done = true
          case MoveResult.Moved(_, GameEvent.Stalemate) =>
            if 0 < best  then best = 0
            if best < b  then b    = best
            if b <= alpha then done = true
          case MoveResult.Moved(newBoard, _) =>
            val (score, ab) =
              alphaBeta(newBoard, depth - 1, alpha, b, maximizing = true, rootColor, deadline, nextSeen)
            if ab then aborted = true
            else
              if score < best then best = score
              if best < b    then b    = best
              if b <= alpha  then done = true
          case _ => ()
      (best, aborted)
