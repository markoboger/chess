package chess.controller.io.pgn

import chess.controller.io.PgnIO
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.{Success, Failure}

final class FastParsePgnParserSpec extends AnyWordSpec with Matchers:

  "FastParsePgnParser.save" should {

    "format moves with move numbers" in {
      val moves = Vector("e4", "e5", "Nf3", "Nc6")
      FastParsePgnParser.save(moves) shouldBe "1. e4 e5\n2. Nf3 Nc6"
    }

    "handle a single move" in {
      FastParsePgnParser.save(Vector("e4")) shouldBe "1. e4"
    }

    "handle an odd number of moves" in {
      FastParsePgnParser.save(
        Vector("e4", "e5", "Nf3")
      ) shouldBe "1. e4 e5\n2. Nf3"
    }

    "return empty string for no moves" in {
      FastParsePgnParser.save(Vector.empty) shouldBe ""
    }
  }

  "FastParsePgnParser.load" should {

    "parse moves with move numbers" in {
      val result = FastParsePgnParser.load("1. e4 e5 2. Nf3 Nc6")
      result shouldBe Success(Vector("e4", "e5", "Nf3", "Nc6"))
    }

    "parse moves without move numbers" in {
      val result = FastParsePgnParser.load("e4 e5 Nf3 Nc6")
      result shouldBe Success(Vector("e4", "e5", "Nf3", "Nc6"))
    }

    "strip comments in braces" in {
      val result = FastParsePgnParser.load("1. e4 {best move} e5 {solid reply}")
      result shouldBe Success(Vector("e4", "e5"))
    }

    "strip variations in parentheses" in {
      val result = FastParsePgnParser.load("1. e4 e5 (1... c5) 2. Nf3")
      result shouldBe Success(Vector("e4", "e5", "Nf3"))
    }

    "strip result markers" in {
      val result = FastParsePgnParser.load("1. e4 e5 1-0")
      result shouldBe Success(Vector("e4", "e5"))
    }

    "strip result marker 0-1" in {
      val result = FastParsePgnParser.load("1. e4 e5 0-1")
      result shouldBe Success(Vector("e4", "e5"))
    }

    "strip result marker 1/2-1/2" in {
      val result = FastParsePgnParser.load("1. e4 e5 1/2-1/2")
      result shouldBe Success(Vector("e4", "e5"))
    }

    "strip result marker *" in {
      val result = FastParsePgnParser.load("1. e4 e5 *")
      result shouldBe Success(Vector("e4", "e5"))
    }

    "fail on empty input" in {
      val result = FastParsePgnParser.load("")
      result shouldBe a[Failure[?]]
      result.failed.get.getMessage should include("No moves found")
    }

    "fail on input with only move numbers and results" in {
      val result = FastParsePgnParser.load("1. 2. 1/2-1/2")
      result shouldBe a[Failure[?]]
    }

    "handle multi-dot move numbers like 1..." in {
      val result = FastParsePgnParser.load("1. e4 1... e5")
      result shouldBe Success(Vector("e4", "e5"))
    }

    "produce the same result as PgnFileIO" in {
      val input = "1. e4 e5 2. Nf3 Nc6 3. Bb5 {Ruy Lopez}"
      val regex = PgnFileIO().load(input).get
      val fast = FastParsePgnParser.load(input).get
      fast shouldBe regex
    }
  }

  "FastParsePgnParser round-trip" should {

    "save then load preserves moves" in {
      val moves = Vector("e4", "e5", "Nf3", "Nc6", "Bb5")
      val text = FastParsePgnParser.save(moves)
      val loaded = FastParsePgnParser.load(text)
      loaded shouldBe Success(moves)
    }

    "save then load preserves single move" in {
      val moves = Vector("d4")
      val text = FastParsePgnParser.save(moves)
      val loaded = FastParsePgnParser.load(text)
      loaded shouldBe Success(moves)
    }
  }

  "FastParsePgnParser as PgnIO" should {

    "be usable polymorphically" in {
      val io: PgnIO = FastParsePgnParser
      val moves = Vector("e4", "e5", "Nf3")
      val text = io.save(moves)
      val loaded = io.load(text)
      loaded shouldBe Success(moves)
    }
  }
