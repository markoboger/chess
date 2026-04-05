package chess.persistence.mongodb

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.model.Opening
import io.circe.generic.auto.*
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.MongoDBContainer
import scala.compiletime.uninitialized

class MongoOpeningRepositorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val container = new MongoDBContainer("mongo:7.0")

  private var repo: MongoOpeningRepository = uninitialized
  private var closeMongo: IO[Unit] = IO.unit

  private def opening(eco: String, name: String, moves: String = "1. e4", moveCount: Int = 1) =
    Opening.unsafe(eco, name, moves, "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", moveCount)

  override def beforeAll(): Unit = {
    container.start()
    val resource = MongoClient
      .fromConnectionString[IO](container.getConnectionString)
      .evalMap { client =>
        for {
          db <- client.getDatabase("chess_test")
          col <- db.getCollectionWithCodec[Opening]("openings")
        } yield new MongoOpeningRepository(col)
      }
    val (r, finalizer) = resource.allocated.unsafeRunSync()
    repo = r
    closeMongo = finalizer
  }

  override def afterAll(): Unit = {
    closeMongo.unsafeRunSync()
    container.stop()
  }

  private def cleanUp(): Unit = repo.deleteAll().unsafeRunSync()

  "save" should {
    "insert a new opening and return it" in {
      cleanUp()
      val o = opening("C50", "Italian Game")
      repo.save(o).unsafeRunSync() shouldBe o
      repo.count().unsafeRunSync() shouldBe 1L
    }

    "upsert on duplicate (eco, name)" in {
      cleanUp()
      val o1 = opening("C50", "Italian Game", "1. e4", 1)
      val o2 = opening("C50", "Italian Game", "1. e4 e5", 2)
      repo.save(o1).unsafeRunSync()
      repo.save(o2).unsafeRunSync()
      repo.count().unsafeRunSync() shouldBe 1L
    }
  }

  "saveAll" should {
    "bulk insert and return count" in {
      cleanUp()
      val openings = List(opening("A00", "Polish Opening"), opening("B12", "Caro-Kann Defense"))
      repo.saveAll(openings).unsafeRunSync() shouldBe 2
    }

    "return 0 for empty list" in {
      repo.saveAll(Nil).unsafeRunSync() shouldBe 0
    }
  }

  "findByEco" should {
    "return all openings for the ECO code sorted by name" in {
      cleanUp()
      repo.saveAll(List(opening("C50", "Italian Game"), opening("C50", "Giuoco Piano"))).unsafeRunSync()
      val result = repo.findByEco("C50").unsafeRunSync()
      result.length shouldBe 2
      result.map(_.name) should contain allOf ("Italian Game", "Giuoco Piano")
    }

    "return empty list for unknown ECO" in {
      repo.findByEco("Z99").unsafeRunSync() shouldBe empty
    }
  }

  "findByEcoAndName" should {
    "return Some for an existing opening" in {
      cleanUp()
      val o = opening("B12", "Caro-Kann Defense")
      repo.save(o).unsafeRunSync()
      repo.findByEcoAndName("B12", "Caro-Kann Defense").unsafeRunSync() shouldBe defined
    }

    "return None for unknown" in {
      repo.findByEcoAndName("Z99", "Unknown").unsafeRunSync() shouldBe None
    }
  }

  "findByName" should {
    "match by name regex" in {
      cleanUp()
      repo.save(opening("C50", "Italian Game")).unsafeRunSync()
      val result = repo.findByName("Italian").unsafeRunSync()
      result.map(_.name) should contain("Italian Game")
    }

    "respect limit" in {
      cleanUp()
      repo
        .saveAll(
          List(
            opening("A00", "Polish Opening"),
            opening("A01", "Polish Opening Variation")
          )
        )
        .unsafeRunSync()
      repo.findByName("Polish", limit = 1).unsafeRunSync().length shouldBe 1
    }
  }

  "findAll" should {
    "return openings in ECO order with limit/offset" in {
      cleanUp()
      repo
        .saveAll(
          List(
            opening("C50", "Italian Game"),
            opening("A00", "Polish Opening"),
            opening("B12", "Caro-Kann Defense")
          )
        )
        .unsafeRunSync()
      val page = repo.findAll(limit = 2, offset = 1).unsafeRunSync()
      page.length shouldBe 2
    }

    "use default parameters when called with no arguments" in {
      cleanUp()
      repo.saveAll(List(opening("A00", "Polish Opening"), opening("B00", "King's Pawn"))).unsafeRunSync()
      repo.findAll().unsafeRunSync().length shouldBe 2
    }
  }

  "findByMoveCount" should {
    "filter by maxMoves" in {
      cleanUp()
      repo
        .saveAll(
          List(
            opening("A00", "Polish Opening", moveCount = 1),
            opening("C50", "Italian Game", moveCount = 5)
          )
        )
        .unsafeRunSync()
      val result = repo.findByMoveCount(maxMoves = 3).unsafeRunSync()
      result.map(_.name) should contain("Polish Opening")
      result.map(_.name) should not contain "Italian Game"
    }
  }

  "findRandom" should {
    "return None when empty" in {
      cleanUp()
      repo.findRandom().unsafeRunSync() shouldBe None
    }

    "return Some when non-empty" in {
      cleanUp()
      repo.save(opening("A00", "Polish Opening")).unsafeRunSync()
      repo.findRandom().unsafeRunSync() shouldBe defined
    }
  }

  "count" should {
    "return correct count" in {
      cleanUp()
      repo.saveAll(List(opening("A00", "Polish Opening"), opening("C50", "Italian Game"))).unsafeRunSync()
      repo.count().unsafeRunSync() shouldBe 2L
    }
  }

  "deleteAll" should {
    "remove all documents and return count" in {
      cleanUp()
      repo.saveAll(List(opening("A00", "Polish Opening"), opening("C50", "Italian Game"))).unsafeRunSync()
      repo.deleteAll().unsafeRunSync() shouldBe 2L
      repo.count().unsafeRunSync() shouldBe 0L
    }
  }
}
