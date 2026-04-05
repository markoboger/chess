package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.application.opening.OpeningParser
import chess.controller.io.pgn.PGNParser
import chess.model.{Board, Color, Opening, PromotableRole, Square}

import scala.util.{Failure, Random, Success}

/** A strategy that follows an opening book for the first several moves, then falls back to a
  * search-based engine.
  *
  * On White's first move an opening is chosen at random from the supplied pool. Both colors then
  * follow the opening line move-by-move. The book is abandoned (and the fallback used for all
  * subsequent moves) as soon as either:
  *   - the next book move cannot be legally played on the current board (opponent deviated), or
  *   - all book moves have been exhausted.
  *
  * Indices for White and Black are tracked independently, so the strategy works correctly in
  * human-vs-computer games where only one color calls `selectMove`.
  *
  * Call [[reset]] between games so that a fresh opening is selected for the next game.
  *
  * @param openings
  *   Pool of openings to choose from. Defaults to the full Lichess library.
  * @param fallback
  *   Engine used once the opening phase ends.
  */
class OpeningBookStrategy(
    openings: List[Opening] = OpeningParser.parseLichessOpenings(),
    val fallback: MoveStrategy = new IterativeDeepeningStrategy()
) extends MoveStrategy:

  private val rng = new Random()

  private var chosen: Option[Opening]  = None
  private var bookMoves: Vector[String] = Vector.empty
  private var whiteIndex: Int           = 0
  private var blackIndex: Int           = 0
  private var bookDone: Boolean         = false

  def name: String =
    chosen.fold(s"Opening Book → ${fallback.name}")(o =>
      s"${o.eco} ${o.name} → ${fallback.name}"
    )

  /** The opening that was selected for the current game. */
  def selectedOpening: Option[Opening] = chosen

  /** Reset opening state so a new random opening is selected on the next White move. */
  def reset(): Unit =
    chosen     = None
    bookMoves  = Vector.empty
    whiteIndex = 0
    blackIndex = 0
    bookDone   = false

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    if bookDone || openings.isEmpty then
      return fallback.selectMove(board, color)

    if chosen.isEmpty && color == Color.White then
      pickOpening()

    if chosen.isEmpty then
      return fallback.selectMove(board, color)

    val moveIdx  = if color == Color.White then 2 * whiteIndex else 2 * blackIndex + 1
    val isWhite  = color == Color.White

    if moveIdx >= bookMoves.length then
      fallback.selectMove(board, color)
    else
      PGNParser.parseMove(bookMoves(moveIdx), board, isWhite) match
        case Success((from, to)) =>
          if isWhite then whiteIndex += 1 else blackIndex += 1
          Some((from, to, MoveStrategy.promotionFor(board, from, to, color)))
        case Failure(_) =>
          bookDone = true
          fallback.selectMove(board, color)

  private def pickOpening(): Unit =
    val o = openings(rng.nextInt(openings.length))
    chosen    = Some(o)
    bookMoves = parseSan(o.moves)

  private def parseSan(pgn: String): Vector[String] =
    pgn.split("\\s+").toVector
      .filterNot(token => token.isEmpty || token.matches("\\d+\\.+"))
