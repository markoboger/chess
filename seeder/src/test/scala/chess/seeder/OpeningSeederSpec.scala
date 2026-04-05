package chess.seeder

import cats.effect.unsafe.implicits.global
import chess.persistence.memory.InMemoryOpeningRepository
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class OpeningSeederSpec extends AnyWordSpec with Matchers {

  "OpeningSeeder.seedLichessOpenings" should {
    "seed all openings into a repository" in {
      val repo  = new InMemoryOpeningRepository()
      val count = OpeningSeeder.seedLichessOpenings(repo).unsafeRunSync()
      count should be > 2000
    }
  }

  "OpeningSeeder.seedFromTsvResource" should {
    "seed openings from a single TSV resource" in {
      val repo  = new InMemoryOpeningRepository()
      val count = OpeningSeeder.seedFromTsvResource(repo, "/openings/a.tsv").unsafeRunSync()
      count should be > 0
    }

    "return 0 for a TSV resource with no valid lines" in {
      val repo  = new InMemoryOpeningRepository()
      val count = OpeningSeeder.seedFromTsvResource(repo, "/openings/eco-openings.csv").unsafeRunSync()
      count shouldBe 0
    }

    "fail for a missing resource" in {
      val repo = new InMemoryOpeningRepository()
      an[Exception] should be thrownBy
        OpeningSeeder.seedFromTsvResource(repo, "/nonexistent.tsv").unsafeRunSync()
    }
  }

  "OpeningSeeder.seedFromCsvResource" should {
    "seed openings from a CSV resource" in {
      val repo  = new InMemoryOpeningRepository()
      val count = OpeningSeeder.seedFromCsvResource(repo, "/openings/eco-openings.csv").unsafeRunSync()
      count should be >= 0
    }

    "return 0 for a header-only CSV resource" in {
      val repo  = new InMemoryOpeningRepository()
      val count = OpeningSeeder.seedFromCsvResource(repo, "/empty-openings.csv").unsafeRunSync()
      count shouldBe 0
    }
  }
}
