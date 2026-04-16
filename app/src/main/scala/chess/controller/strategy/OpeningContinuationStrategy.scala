package chess.controller.strategy

import chess.controller.MoveStrategy
import chess.application.opening.OpeningParser
import chess.controller.io.fen.FullFen
import chess.controller.io.pgn.PGNParser
import chess.model.{Board, Color, Opening, PromotableRole, Square}

/** Position-driven opening strategy.
  *
  * Instead of selecting one opening only on move one, this strategy looks at the current board
  * and follows any documented opening line that still matches the position. When several book
  * continuations are available, it prefers the move that keeps the game inside the opening book
  * for the greatest remaining depth. Once no documented continuation exists, it falls back to the
  * configured engine.
  */
class OpeningContinuationStrategy(
    openings: List[Opening] = OpeningParser.parseLichessOpenings(),
    val fallback: MoveStrategy = new IterativeDeepeningStrategy()
) extends MoveStrategy:

  val name = "Opening Continuation"

  private val book = OpeningContinuationStrategy.Index.build(openings)

  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])] =
    book.bestContinuation(board, color).flatMap { entry =>
      PGNParser.parseMove(entry.nextSan, board, color == Color.White).toOption.map { case (from, to) =>
        (from, to, MoveStrategy.promotionFor(board, from, to, color))
      }
    }.orElse(fallback.selectMove(board, color))

object OpeningContinuationStrategy:

  final case class Continuation(nextSan: String, remainingPlies: Int, eco: String, name: String)

  private object Index:
    private type PositionKey = String

    final class Book(private val continuations: Map[PositionKey, Vector[Continuation]]):
      def bestContinuation(board: Board, color: Color): Option[Continuation] =
        continuations.get(positionKey(board, color)).flatMap(_.sortBy(c => (-c.remainingPlies, c.nextSan)).headOption)

    def build(openings: List[Opening]): Book =
      val entries = scala.collection.mutable.Map.empty[PositionKey, Vector[Continuation]]

      openings.foreach { opening =>
        val sanMoves = parseSan(opening.moves)

        @annotation.tailrec
        def indexFrom(idx: Int, board: Board, color: Color): Unit =
          if idx >= sanMoves.length then ()
          else
            val san = sanMoves(idx)
            val key = positionKey(board, color)
            val continuation = Continuation(san, sanMoves.length - idx, opening.eco, opening.name)
            entries.update(key, entries.getOrElse(key, Vector.empty) :+ continuation)

            val nextBoardOpt =
              PGNParser
                .parseMove(san, board, color == Color.White)
                .toOption
                .flatMap { case (from, to) =>
                  val promo = MoveStrategy.promotionFor(board, from, to, color)
                  board.move(from, to, promo).toOption
                }

            nextBoardOpt match
              case Some(nextBoard) => indexFrom(idx + 1, nextBoard, color.opposite)
              case None            => ()

        indexFrom(0, Board.initial, Color.White)
      }

      val deduped =
        entries.iterator.map { case (key, conts) =>
          key -> conts.distinctBy(_.nextSan)
        }.toMap

      Book(deduped)

    private def parseSan(pgn: String): Vector[String] =
      pgn.split("\\s+").toVector.filterNot(token => token.isEmpty || token.matches("\\d+\\.+"))

    private def positionKey(board: Board, color: Color): PositionKey =
      FullFen.openingKey(board, color == Color.White)
