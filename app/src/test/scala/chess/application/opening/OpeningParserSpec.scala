package chess.application.opening

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import chess.model.Opening

class OpeningParserSpec extends AnyWordSpec with Matchers {

  private def withOutput(block: => Unit): String =
    val buffer = new java.io.ByteArrayOutputStream()
    Console.withOut(new java.io.PrintStream(buffer))(block)
    buffer.toString

  "OpeningParser.parseTsvLine" should {

    "parse a valid Lichess TSV line" in {
      val line = "A00\tPolish Opening\t1. b4"
      val result = OpeningParser.parseTsvLine(line)
      result shouldBe defined
      val opening = result.get
      opening.eco shouldBe "A00"
      opening.name shouldBe "Polish Opening"
      opening.moves shouldBe "1. b4"
      opening.moveCount shouldBe 1
      opening.fen should not be empty
    }

    "parse a multi-move opening" in {
      val line = "C50\tItalian Game\t1. e4 e5 2. Nf3 Nc6 3. Bc4"
      val result = OpeningParser.parseTsvLine(line)
      result shouldBe defined
      val opening = result.get
      opening.eco shouldBe "C50"
      opening.name shouldBe "Italian Game"
      opening.moveCount shouldBe 5
    }

    "skip header line" in {
      val line = "eco\tname\tpgn"
      OpeningParser.parseTsvLine(line) shouldBe None
    }

    "return None for malformed line" in {
      OpeningParser.parseTsvLine("A00\tOnly Two Fields") shouldBe None
    }

    "return None for empty fields" in {
      OpeningParser.parseTsvLine("A00\t\t1. e4") shouldBe None
      OpeningParser.parseTsvLine("\tSicilian\t1. e4 c5") shouldBe None
      OpeningParser.parseTsvLine("A00\tSicilian\t") shouldBe None
    }

    "handle unrecognized PGN gracefully" in {
      // applyPgnMove silently ignores invalid moves, so the board stays at initial position
      val result = OpeningParser.parseTsvLine("A00\tBogus\t1. Zz9")
      // Still parses (board stays initial), but moveCount is 1
      result shouldBe defined
    }
  }

  "OpeningParser.computeFenAndMoveCount" should {

    "compute FEN for initial pawn push" in {
      val result = OpeningParser.computeFenAndMoveCount("1. e4")
      result shouldBe defined
      val (fen, count) = result.get
      count shouldBe 1
      fen should include("PPPP1PPP") // e-pawn moved
    }

    "compute correct move count for multi-move PGN" in {
      val result = OpeningParser.computeFenAndMoveCount("1. e4 e5 2. Nf3 Nc6")
      result shouldBe defined
      result.get._2 shouldBe 4
    }

    "handle unrecognized PGN moves gracefully" in {
      // applyPgnMove silently ignores invalid moves; board stays at initial
      val result = OpeningParser.computeFenAndMoveCount("1. Zz9")
      result shouldBe defined
      result.get._2 shouldBe 1
    }
  }

  "OpeningParser.parseLichessOpenings" should {

    "parse all Lichess TSV files from resources" in {
      val openings = OpeningParser.parseLichessOpenings()
      openings should not be empty
      openings.length should be > 2000
    }

    "produce openings with valid ECO codes" in {
      val openings = OpeningParser.parseLichessOpenings()
      every(openings.map(_.eco)) should fullyMatch regex "[A-E][0-9]{2}"
    }

    "cover all five ECO categories" in {
      val openings = OpeningParser.parseLichessOpenings()
      val categories = openings.map(_.eco.charAt(0)).distinct.sorted
      categories should contain allOf ('A', 'B', 'C', 'D', 'E')
    }

    "produce unique (eco, name) pairs" in {
      val openings = OpeningParser.parseLichessOpenings()
      val pairs = openings.map(o => (o.eco, o.name))
      pairs.distinct.length shouldBe pairs.length
    }
  }

  "OpeningParser.parseTsvResource" should {
    "parse a bundled TSV file" in {
      val result = OpeningParser.parseTsvResource("/openings/a.tsv")

      result.isSuccess shouldBe true
      result.get should not be empty
    }

    "fail when the resource does not exist" in {
      val result = OpeningParser.parseTsvResource("/openings/does-not-exist.tsv")

      result.isFailure shouldBe true
      result.failed.get.getMessage should include("Resource not found")
    }
  }

  "OpeningParser.parseCsvResource" should {
    "parse the legacy CSV resource" in {
      val result = OpeningParser.parseCsvResource("/openings/eco-openings.csv")

      result.isSuccess shouldBe true
      result.get should not be empty
    }

    "parse an empty CSV resource into an empty list" in {
      val result = OpeningParser.parseCsvResource("/empty-openings.csv")

      result shouldBe scala.util.Success(Nil)
    }
  }

  "OpeningParser.parseCsvOpenings" should {
    "skip the header and parse valid rows" in {
      val rows = List(
        "eco,name,moves",
        "A00,Polish Opening,1. b4",
        "C50,Italian Game,1. e4 e5 2. Nf3 Nc6 3. Bc4"
      )

      val result = OpeningParser.parseCsvOpenings(rows)

      result.map(_.eco) shouldBe List("A00", "C50")
      result.map(_.moveCount) shouldBe List(1, 5)
    }

    "discard malformed and invalid rows" in {
      val rows = List(
        "eco,name,moves",
        "A00,Missing moves",
        "B00,Blank moves,",
        "C00,Bad pgn,1. Zz9"
      )

      val result = OpeningParser.parseCsvOpenings(rows)

      result.map(_.eco) shouldBe List("C00")
    }
  }

  "OpeningParser.validateOpenings" should {

    "return no errors for the parsed Lichess dataset" in {
      val openings = OpeningParser.parseLichessOpenings()
      val errors = OpeningParser.validateOpenings(openings)
      errors shouldBe empty
    }

    "report duplicate (eco, name) pairs" in {
      val o = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 1)
      val errors = OpeningParser.validateOpenings(List(o, o))
      errors.exists(_.contains("Duplicate")) shouldBe true
    }

    "report invalid ECO format" in {
      val o = Opening.unsafe("Z99", "Bad ECO", "1. e4", "fen", 1)
      val errors = OpeningParser.validateOpenings(List(o))
      errors.exists(_.contains("Invalid ECO")) shouldBe true
    }

    "report empty name" in {
      val o = Opening.unsafe("A00", "", "1. b4", "fen", 1)
      val errors = OpeningParser.validateOpenings(List(o))
      errors.exists(_.contains("Empty name")) shouldBe true
    }

    "report unreasonable move count (0)" in {
      val o = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 0)
      val errors = OpeningParser.validateOpenings(List(o))
      errors.exists(_.contains("Unreasonable move count")) shouldBe true
    }

    "report unreasonable move count (> 50)" in {
      val o = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 51)
      val errors = OpeningParser.validateOpenings(List(o))
      errors.exists(_.contains("Unreasonable move count")) shouldBe true
    }
  }

  "OpeningParser.printStatistics" should {

    "handle empty openings list" in {
      val out = withOutput {
        OpeningParser.printStatistics(Nil)
      }

      out should include("No openings to report.")
    }

    "print stats for a non-empty list" in {
      val openings = List(
        Opening.unsafe("A00", "Polish Opening", "1. b4", "fen1", 1),
        Opening.unsafe("C50", "Italian Game", "1. e4 e5 2. Nf3", "fen2", 3)
      )
      val out = withOutput {
        OpeningParser.printStatistics(openings)
      }

      out should include("Total openings: 2")
      out should include("Unique ECO codes: 2")
      out should include("By ECO category:")
      out should include("A: 1 openings")
      out should include("C: 1 openings")
    }
  }

  "OpeningParser.deduplicate" should {

    "keep the entry with fewer moves when duplicates exist" in {
      val short = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen1", 1)
      val long = Opening.unsafe("A00", "Polish Opening", "1. b4 e5", "fen2", 2)
      val result = OpeningParser.deduplicate(List(long, short))
      result.length shouldBe 1
      result.head.moveCount shouldBe 1
    }

    "sort results by (eco, name)" in {
      val c = Opening.unsafe("C50", "Italian Game", "1. e4", "fen", 1)
      val a = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 1)
      val result = OpeningParser.deduplicate(List(c, a))
      result.map(_.eco) shouldBe List("A00", "C50")
    }
  }
}
