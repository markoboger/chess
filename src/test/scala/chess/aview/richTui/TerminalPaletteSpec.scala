package chess.aview.richTui

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TerminalPaletteSpec extends AnyWordSpec with Matchers:

  "TerminalPalette.colorize" should {
    "wrap text with the given color and reset sequence" in {
      TerminalPalette.colorize("hello", TerminalPalette.Accent) shouldBe
        s"${TerminalPalette.Accent}hello${TerminalPalette.Reset}"
    }
  }

  "TerminalPalette constants" should {
    "define ANSI escape sequences for the public palette entries" in {
      TerminalPalette.Reset should startWith("\u001b[")
      TerminalPalette.Bold should startWith("\u001b[")
      TerminalPalette.LightSquare should startWith("\u001b[")
      TerminalPalette.DarkSquare should startWith("\u001b[")
      TerminalPalette.Accent should startWith("\u001b[")
      TerminalPalette.Warning should startWith("\u001b[")
      TerminalPalette.Error should startWith("\u001b[")
      TerminalPalette.Success should startWith("\u001b[")
      TerminalPalette.Muted should startWith("\u001b[")
    }
  }
