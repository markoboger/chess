package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.model.{Board, Color, GameEvent, PromotableRole, Square}
import scala.annotation.tailrec
import scala.util.Random

private[strategy] object SearchSupport:
  final case class SearchMove(
      from: Square,
      to: Square,
      promotion: Option[PromotableRole],
      board: Board,
      event: GameEvent
  ):
    def asTuple: (Square, Square, Option[PromotableRole]) = (from, to, promotion)

  enum SearchMode:
    case Maximize, Minimize

    def currentColor(rootColor: Color): Color =
      if this == Maximize then rootColor else rootColor.opposite

    def next: SearchMode =
      if this == Maximize then Minimize else Maximize

    def initialBest(inf: Int): Int =
      if this == Maximize then -inf else inf

    def better(candidate: Int, current: Int): Boolean =
      if this == Maximize then candidate > current else candidate < current

    def updateBounds(alpha: Int, beta: Int, best: Int): (Int, Int) =
      if this == Maximize then (alpha.max(best), beta) else (alpha, beta.min(best))

    def shouldCutoff(alpha: Int, beta: Int): Boolean =
      if this == Maximize then alpha >= beta else beta <= alpha

    def terminalMateScore(inf: Int, depth: Int): Int =
      this match
        case Maximize => -inf + (depth * 100)
        case Minimize => inf - (depth * 100)

    def childCheckmateScore(inf: Int, depth: Int): Int =
      this match
        case Maximize => inf - (depth * 100)
        case Minimize => -inf + (depth * 100)

  def legalSearchMoves(board: Board, color: Color): Vector[SearchMove] =
    board.legalMoves(color).flatMap { (from, to) =>
      val promotion = MoveStrategy.promotionFor(board, from, to, color)
      board
        .move(from, to, promotion)
        .movedOption
        .map { (newBoard, event) =>
          SearchMove(from, to, promotion, newBoard, event)
        }
    }

  def terminalScore(
      board: Board,
      currentColor: Color,
      mode: SearchMode,
      depth: Int,
      inf: Int,
      rootColor: Color
  ): Option[Int] =
    Option.when(board.legalMoves(currentColor).isEmpty) {
      if board.isInCheck(currentColor) then mode.terminalMateScore(inf, depth)
      else DrawPolicy.drawScore(board, rootColor)
    }

  def updateBestMoves(
      bestScore: Int,
      bestMoves: List[(Square, Square, Option[PromotableRole])],
      move: SearchMove,
      score: Int
  ): (Int, List[(Square, Square, Option[PromotableRole])]) =
    if score > bestScore then (score, List(move.asTuple))
    else if score == bestScore then (bestScore, move.asTuple :: bestMoves)
    else (bestScore, bestMoves)

  def chooseRandom(
      bestMoves: List[(Square, Square, Option[PromotableRole])]
  ): Option[(Square, Square, Option[PromotableRole])] =
    Option.when(bestMoves.nonEmpty)(bestMoves(Random.nextInt(bestMoves.length)))

  def searchChildren(
      moves: Vector[SearchMove],
      mode: SearchMode,
      alpha: Int,
      beta: Int,
      inf: Int
  )(
      scoreMove: (SearchMove, Int, Int) => Int
  ): Int =
    @tailrec
    def loop(
        remaining: List[SearchMove],
        best: Int,
        currentAlpha: Int,
        currentBeta: Int
    ): Int =
      remaining match
        case Nil => best
        case move :: tail =>
          val score = scoreMove(move, currentAlpha, currentBeta)
          val nextBest = if mode.better(score, best) then score else best
          val (nextAlpha, nextBeta) = mode.updateBounds(currentAlpha, currentBeta, nextBest)
          if mode.shouldCutoff(nextAlpha, nextBeta) then nextBest
          else loop(tail, nextBest, nextAlpha, nextBeta)

    loop(moves.toList, mode.initialBest(inf), alpha, beta)

  def searchChildrenUntilDeadline(
      moves: Vector[SearchMove],
      mode: SearchMode,
      alpha: Int,
      beta: Int,
      inf: Int
  )(
      scoreMove: (SearchMove, Int, Int) => (Int, Boolean)
  ): (Int, Boolean) =
    @tailrec
    def loop(
        remaining: List[SearchMove],
        best: Int,
        currentAlpha: Int,
        currentBeta: Int
    ): (Int, Boolean) =
      remaining match
        case Nil => (best, false)
        case move :: tail =>
          val (score, aborted) = scoreMove(move, currentAlpha, currentBeta)
          if aborted then (best, true)
          else
            val nextBest = if mode.better(score, best) then score else best
            val (nextAlpha, nextBeta) = mode.updateBounds(currentAlpha, currentBeta, nextBest)
            if mode.shouldCutoff(nextAlpha, nextBeta) then (nextBest, false)
            else loop(tail, nextBest, nextAlpha, nextBeta)

    loop(moves.toList, mode.initialBest(inf), alpha, beta)
