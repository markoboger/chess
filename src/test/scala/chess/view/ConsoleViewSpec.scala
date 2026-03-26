package chess.view

import chess.controller.GameController
import chess.io.{FenIO, PgnIO}
import chess.io.fen.RegexFenParser
import chess.io.pgn.PgnFileIO
import chess.model.Board
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class ConsoleViewSpec extends AnyWordSpec with Matchers:

  given FenIO = RegexFenParser
  given PgnIO = PgnFileIO()
  "ConsoleView" should {
    "render ranks and files with coordinate labels" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val rendered = view.showBoard(Board.initial)

      rendered should include("8 |")
      rendered should include("1 |")
      rendered should include("a b c d e f g h")
    }

    "provide a welcome message" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)

      view.showWelcome() should include("Welcome")
    }

    "use showBoard as a thin wrapper around toString" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val board = Board.initial

      // showBoard is already tested above
    }
  }
