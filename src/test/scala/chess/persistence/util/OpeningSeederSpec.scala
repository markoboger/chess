package chess.persistence.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import chess.persistence.model.Opening

class OpeningSeederSpec extends AnyWordSpec with Matchers {

  "OpeningSeeder.parseTsvLine" should {

    "parse a valid Lichess TSV line" in {
      val line = "A00\tPolish Opening\t1. b4"
      val result = OpeningSeeder.parseTsvLine(line)
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
      val result = OpeningSeeder.parseTsvLine(line)
      result shouldBe defined
      val opening = result.get
      opening.eco shouldBe "C50"
      opening.name shouldBe "Italian Game"
      opening.moveCount shouldBe 5
    }

    "skip header line" in {
      val line = "eco\tname\tpgn"
      OpeningSeeder.parseTsvLine(line) shouldBe None
    }

    "return None for malformed line" in {
      OpeningSeeder.parseTsvLine("A00\tOnly Two Fields") shouldBe None
    }

    "return None for empty fields" in {
      OpeningSeeder.parseTsvLine("A00\t\t1. e4") shouldBe None
      OpeningSeeder.parseTsvLine("\tSicilian\t1. e4 c5") shouldBe None
      OpeningSeeder.parseTsvLine("A00\tSicilian\t") shouldBe None
    }

    "handle unrecognized PGN gracefully" in {
      // applyPgnMove silently ignores invalid moves, so the board stays at initial position
      val result = OpeningSeeder.parseTsvLine("A00\tBogus\t1. Zz9")
      // Still parses (board stays initial), but moveCount is 1
      result shouldBe defined
    }
  }

  "OpeningSeeder.computeFenAndMoveCount" should {

    "compute FEN for initial pawn push" in {
      val result = OpeningSeeder.computeFenAndMoveCount("1. e4")
      result shouldBe defined
      val (fen, count) = result.get
      count shouldBe 1
      fen should include("PPPP1PPP") // e-pawn moved
    }

    "compute correct move count for multi-move PGN" in {
      val result = OpeningSeeder.computeFenAndMoveCount("1. e4 e5 2. Nf3 Nc6")
      result shouldBe defined
      result.get._2 shouldBe 4
    }

    "handle unrecognized PGN moves gracefully" in {
      // applyPgnMove silently ignores invalid moves; board stays at initial
      val result = OpeningSeeder.computeFenAndMoveCount("1. Zz9")
      result shouldBe defined
      result.get._2 shouldBe 1
    }
  }

  "OpeningSeeder.parseLichessOpenings" should {

    "parse all Lichess TSV files from resources" in {
      val openings = OpeningSeeder.parseLichessOpenings()
      openings should not be empty
      openings.length should be > 2000
    }

    "produce openings with valid ECO codes" in {
      val openings = OpeningSeeder.parseLichessOpenings()
      every(openings.map(_.eco)) should fullyMatch regex "[A-E][0-9]{2}"
    }

    "cover all five ECO categories" in {
      val openings = OpeningSeeder.parseLichessOpenings()
      val categories = openings.map(_.eco.charAt(0)).distinct.sorted
      categories should contain allOf ('A', 'B', 'C', 'D', 'E')
    }

    "produce unique (eco, name) pairs" in {
      val openings = OpeningSeeder.parseLichessOpenings()
      val pairs = openings.map(o => (o.eco, o.name))
      pairs.distinct.length shouldBe pairs.length
    }
  }

  "OpeningSeeder.validateOpenings" should {

    "return no errors for the parsed Lichess dataset" in {
      val openings = OpeningSeeder.parseLichessOpenings()
      val errors = OpeningSeeder.validateOpenings(openings)
      errors shouldBe empty
    }

    "report duplicate (eco, name) pairs" in {
      val o = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 1)
      val errors = OpeningSeeder.validateOpenings(List(o, o))
      errors.exists(_.contains("Duplicate")) shouldBe true
    }

    "report invalid ECO format" in {
      val o = Opening.unsafe("Z99", "Bad ECO", "1. e4", "fen", 1)
      val errors = OpeningSeeder.validateOpenings(List(o))
      errors.exists(_.contains("Invalid ECO")) shouldBe true
    }

    "report empty name" in {
      val o = Opening.unsafe("A00", "", "1. b4", "fen", 1)
      val errors = OpeningSeeder.validateOpenings(List(o))
      errors.exists(_.contains("Empty name")) shouldBe true
    }

    "report unreasonable move count (0)" in {
      val o = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 0)
      val errors = OpeningSeeder.validateOpenings(List(o))
      errors.exists(_.contains("Unreasonable move count")) shouldBe true
    }

    "report unreasonable move count (> 50)" in {
      val o = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 51)
      val errors = OpeningSeeder.validateOpenings(List(o))
      errors.exists(_.contains("Unreasonable move count")) shouldBe true
    }
  }

  "OpeningSeeder.printStatistics" should {

    "handle empty openings list" in {
      noException should be thrownBy OpeningSeeder.printStatistics(Nil)
    }

    "print stats for a non-empty list" in {
      val openings = List(
        Opening.unsafe("A00", "Polish Opening", "1. b4", "fen1", 1),
        Opening.unsafe("C50", "Italian Game", "1. e4 e5 2. Nf3", "fen2", 3)
      )
      noException should be thrownBy OpeningSeeder.printStatistics(openings)
    }
  }

  "OpeningSeeder.seedLichessOpenings" should {

    "seed all openings into a repository" in {
      import cats.effect.unsafe.implicits.global
      import chess.persistence.memory.InMemoryOpeningRepository
      val repo = new InMemoryOpeningRepository()
      val count = OpeningSeeder.seedLichessOpenings(repo).unsafeRunSync()
      count should be > 2000
    }
  }

  "OpeningSeeder.seedFromTsvResource" should {

    "seed openings from a single TSV resource" in {
      import cats.effect.unsafe.implicits.global
      import chess.persistence.memory.InMemoryOpeningRepository
      val repo = new InMemoryOpeningRepository()
      val count = OpeningSeeder.seedFromTsvResource(repo, "/openings/a.tsv").unsafeRunSync()
      count should be > 0
    }

    "return 0 for a resource with no valid lines" in {
      import cats.effect.unsafe.implicits.global
      import chess.persistence.memory.InMemoryOpeningRepository
      val repo = new InMemoryOpeningRepository()
      // The header-only resource would produce 0 valid openings.
      // Use the eco-openings CSV as a non-TSV resource — seeder finds no valid TSV lines.
      val count = OpeningSeeder.seedFromTsvResource(repo, "/openings/eco-openings.csv").unsafeRunSync()
      count shouldBe 0
    }

    "fail with IO error for a missing resource" in {
      import cats.effect.unsafe.implicits.global
      import chess.persistence.memory.InMemoryOpeningRepository
      val repo = new InMemoryOpeningRepository()
      an[Exception] should be thrownBy
        OpeningSeeder.seedFromTsvResource(repo, "/nonexistent.tsv").unsafeRunSync()
    }
  }

  "OpeningSeeder.seedFromCsvResource" should {

    "seed openings from the legacy CSV resource" in {
      import cats.effect.unsafe.implicits.global
      import chess.persistence.memory.InMemoryOpeningRepository
      val repo = new InMemoryOpeningRepository()
      val count = OpeningSeeder.seedFromCsvResource(repo, "/openings/eco-openings.csv").unsafeRunSync()
      count should be >= 0
    }

    "return 0 when resource has no valid CSV lines (covers isEmpty branch)" in {
      import cats.effect.unsafe.implicits.global
      import chess.persistence.memory.InMemoryOpeningRepository
      val repo = new InMemoryOpeningRepository()
      // Header-only CSV: parseCsvOpenings drops the header and finds no data rows → IO.pure(0)
      val count = OpeningSeeder.seedFromCsvResource(repo, "/empty-openings.csv").unsafeRunSync()
      count shouldBe 0
    }
  }

  "OpeningSeeder.deduplicate" should {

    "keep the entry with fewer moves when duplicates exist" in {
      val short = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen1", 1)
      val long = Opening.unsafe("A00", "Polish Opening", "1. b4 e5", "fen2", 2)
      val result = OpeningSeeder.deduplicate(List(long, short))
      result.length shouldBe 1
      result.head.moveCount shouldBe 1
    }

    "sort results by (eco, name)" in {
      val c = Opening.unsafe("C50", "Italian Game", "1. e4", "fen", 1)
      val a = Opening.unsafe("A00", "Polish Opening", "1. b4", "fen", 1)
      val result = OpeningSeeder.deduplicate(List(c, a))
      result.map(_.eco) shouldBe List("A00", "C50")
    }
  }
}
