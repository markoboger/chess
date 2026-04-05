package chess.controller.strategy

import chess.controller.GameController
import chess.controller.io.fen.RegexFenParser
import chess.controller.io.pgn.PgnFileIO
import chess.model.{Board, Color, Opening, Square}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class OpeningContinuationStrategySpec extends AnyWordSpec with Matchers:

  given chess.controller.io.FenIO = RegexFenParser
  given chess.controller.io.PgnIO = PgnFileIO()

  private def opening(eco: String, name: String, moves: String, count: Int): Opening =
    Opening.unsafe(eco, name, moves, "fen", count)

  "OpeningContinuationStrategy" should {
    "play the deepest documented continuation from the current position" in {
      val strategy = new OpeningContinuationStrategy(
        openings = List(
          opening("C20", "Short", "1. e4 e5 2. Nf3", 3),
          opening("C50", "Italian", "1. e4 e5 2. Nf3 Nc6 3. Bc4", 5)
        ),
        fallback = new RandomStrategy()
      )

      val controller = new GameController(Board.initial)
      controller.applyPgnMove("e4")
      controller.applyPgnMove("e5")
      controller.applyPgnMove("Nf3")

      val result = strategy.selectMove(controller.board, Color.Black)

      result shouldBe Some((Square("b8"), Square("c6"), None))
    }

    "continue following documented opening moves for both colors as long as the line matches" in {
      val strategy = new OpeningContinuationStrategy(
        openings = List(opening("C50", "Italian", "1. e4 e5 2. Nf3 Nc6 3. Bc4", 5)),
        fallback = new RandomStrategy()
      )

      val controller = new GameController(Board.initial)

      for ply <- 1 to 5 do
        val color = if ply % 2 == 1 then Color.White else Color.Black
        strategy.selectMove(controller.board, color) match
          case Some((from, to, promo)) => controller.applyMove(from, to, promo)
          case None                    => fail(s"Expected a documented opening move at ply $ply")

      controller.pgnMoves shouldBe Vector("e4", "e5", "Nf3", "Nc6", "Bc4")
    }

    "use a documented black move from the opening database after white's first move" in {
      val strategy = new OpeningContinuationStrategy(
        openings = List(opening("B20", "Sicilian Defense", "1. e4 c5 2. Nf3 d6", 4)),
        fallback = new RandomStrategy()
      )

      val controller = new GameController(Board.initial)
      controller.applyPgnMove("e4")

      val result = strategy.selectMove(controller.board, Color.Black)

      result shouldBe Some((Square("c7"), Square("c5"), None))
    }

    "fall back once the position is no longer covered by a documented continuation" in {
      val fallback = new RandomStrategy()
      val strategy = new OpeningContinuationStrategy(
        openings = List(opening("C50", "Italian", "1. e4 e5 2. Nf3 Nc6", 4)),
        fallback = fallback
      )

      val controller = new GameController(Board.initial)
      controller.applyPgnMove("d4")

      strategy.selectMove(controller.board, Color.Black) shouldBe defined
    }

    "use another documented line after the opponent deviates from one opening into a different known opening" in {
      val strategy = new OpeningContinuationStrategy(
        openings = List(
          opening("C50", "Italian", "1. e4 e5 2. Nf3 Nc6 3. Bc4", 5),
          opening("C45", "Scotch Game", "1. e4 e5 2. Nf3 Nc6 3. d4 exd4 4. Nxd4", 7)
        ),
        fallback = new RandomStrategy()
      )

      val controller = new GameController(Board.initial)
      controller.applyPgnMove("e4")
      controller.applyPgnMove("e5")
      controller.applyPgnMove("Nf3")
      controller.applyPgnMove("Nc6")

      val result = strategy.selectMove(controller.board, Color.White)

      result shouldBe Some((Square("d2"), Square("d4"), None))
    }

    "default to iterative deepening once the opening path ends" in {
      new OpeningContinuationStrategy(openings = List(opening("C20", "King's Pawn Game", "1. e4 e5", 2))).fallback shouldBe
        a[IterativeDeepeningStrategy]
    }
  }
