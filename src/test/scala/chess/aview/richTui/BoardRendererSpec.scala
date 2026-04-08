package chess.aview.richTui

import chess.model.Board
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BoardRendererSpec extends AnyWordSpec with Matchers:
  "BoardRenderer" should {
    "render board coordinates" in {
      val rendered = BoardRenderer.render(Board.initial)
      rendered should include("a")
      rendered should include("h")
      rendered should include("8")
      rendered should include("1")
    }

    "support flipped orientation" in {
      val rendered = BoardRenderer.render(Board.initial, flipped = true)
      rendered.linesIterator.next().trim should startWith("h")
    }
  }
