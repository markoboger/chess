package chess.controller.puzzle

import chess.model.Puzzle
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class PuzzleParserSpec extends AnyWordSpec with Matchers:

  "PuzzleParser.parseLine" should {

    // 9-field base line (no openingTags column)
    val baseValidLine =
      "00sHx,r1bqkb1r/pp3ppp/2nppn2/8/2BPP3/2N2N2/PP3PPP/R1BQK2R," +
        "f6d5 e4d5 c6d4 f3d4,1500,76,94,123,fork middlegame,https://lichess.org/xyz"

    "parse a valid CSV line into a Puzzle" in {
      val result = PuzzleParser.parseLine(baseValidLine)
      result shouldBe defined
      result.get.id shouldBe "00sHx"
      result.get.rating shouldBe 1500
      result.get.moves should contain("f6d5")
      result.get.themes should contain("fork")
      result.get.gameUrl should include("lichess.org")
    }

    "return None for a line with too few fields" in {
      PuzzleParser.parseLine("a,b,c") shouldBe None
    }

    "return None for a line with a non-integer rating field" in {
      val badLine = "id,fen,moves,NOT_A_NUMBER,76,94,123,themes,url"
      PuzzleParser.parseLine(badLine) shouldBe None
    }

    "handle an empty themes field" in {
      val line = "id,fen,e2e4,1400,50,80,200,,https://lichess.org/abc"
      val result = PuzzleParser.parseLine(line)
      result shouldBe defined
      result.get.themes shouldBe Nil
    }

    "populate openingTags when a 10th field is present" in {
      val line = baseValidLine + ",SicilianDefense_Najdorf"
      val result = PuzzleParser.parseLine(line)
      result shouldBe defined
      result.get.openingTags should contain("SicilianDefense_Najdorf")
    }

    "return Nil for openingTags when the 10th field is absent" in {
      val result = PuzzleParser.parseLine(baseValidLine)
      result shouldBe defined
      result.get.openingTags shouldBe Nil
    }
  }

  "PuzzleParser.fromResource" should {

    "return an empty vector for a non-existent resource" in {
      PuzzleParser.fromResource("/nonexistent/path.csv") shouldBe Vector.empty
    }

    "load puzzles from the bundled resource" in {
      // The test resource at /puzzle/lichess_small_puzzle.csv must exist on the classpath.
      val puzzles = PuzzleParser.fromResource("/puzzle/lichess_small_puzzle.csv")
      puzzles should not be empty
      puzzles.head.id should not be empty
    }
  }
