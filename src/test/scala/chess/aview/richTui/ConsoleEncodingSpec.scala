package chess.aview.richTui

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConsoleEncodingSpec extends AnyWordSpec with Matchers:
  "ConsoleEncoding.isWindows" should {
    "detect Windows platform names" in {
      ConsoleEncoding.isWindows("Windows 11") shouldBe true
      ConsoleEncoding.isWindows("windows") shouldBe true
    }

    "reject non-Windows platform names" in {
      ConsoleEncoding.isWindows("Mac OS X") shouldBe false
      ConsoleEncoding.isWindows("Linux") shouldBe false
      ConsoleEncoding.isWindows(null) shouldBe false
    }
  }

  "ConsoleEncoding.preferUnicodeConsolePieces" should {
    "disable Unicode console pieces on Windows" in {
      ConsoleEncoding.preferUnicodeConsolePieces("Windows 11") shouldBe false
    }

    "keep Unicode console pieces on non-Windows platforms" in {
      ConsoleEncoding.preferUnicodeConsolePieces("Linux") shouldBe true
      ConsoleEncoding.preferUnicodeConsolePieces("Mac OS X") shouldBe true
    }
  }
