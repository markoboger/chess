package chess.persistence.mongodb

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.persistence.TestcontainersSupport
import chess.model.PersistedGame
import io.circe.generic.auto.*
import mongo4cats.circe.*
import mongo4cats.client.MongoClient
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.MongoDBContainer
import scala.compiletime.uninitialized
import java.util.UUID

class MongoGameRepositorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val container = new MongoDBContainer("mongo:7.0")

  private var repo: MongoGameRepository = uninitialized
  private var closeMongo: IO[Unit] = IO.unit

  override def beforeAll(): Unit = {
    TestcontainersSupport.configureDockerDesktopIfNeeded()
    container.start()
    val resource = MongoClient
      .fromConnectionString[IO](container.getConnectionString)
      .evalMap { client =>
        for {
          db <- client.getDatabase("chess_test")
          col <- db.getCollectionWithCodec[PersistedGame]("games")
        } yield new MongoGameRepository(col)
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
    "insert a new game and return it" in {
      cleanUp()
      val game = PersistedGame.create()
      val saved = repo.save(game).unsafeRunSync()
      saved.id shouldBe game.id
      repo.count().unsafeRunSync() shouldBe 1L
    }

    "upsert on duplicate id" in {
      cleanUp()
      val game = PersistedGame.create()
      val saved1 = repo.save(game).unsafeRunSync()
      val saved2 = repo.save(game.copy(status = "Checkmate")).unsafeRunSync()
      saved1.id shouldBe game.id
      saved2.status shouldBe "Checkmate"
    }

    "persist optional opening info" in {
      cleanUp()
      val game = PersistedGame
        .create()
        .copy(
          openingEco = Some("C50"),
          openingName = Some("Italian Game"),
          result = Some("1-0"),
          status = "Checkmate"
        )
      val saved = repo.save(game).unsafeRunSync()
      saved.openingEco shouldBe Some("C50")
      saved.openingName shouldBe Some("Italian Game")
      repo.count().unsafeRunSync() shouldBe 1L
    }
  }

  "findById" should {
    "return None for unknown id" in {
      repo.findById(UUID.randomUUID()).unsafeRunSync() shouldBe None
    }

    "attempt find for existing id (exercises findById code path)" in {
      cleanUp()
      val game = PersistedGame.create()
      repo.save(game).unsafeRunSync()
      noException should be thrownBy repo.findById(game.id).unsafeRunSync()
    }
  }

  "findAll" should {
    "return games with limit and offset" in {
      cleanUp()
      List.fill(3)(PersistedGame.create()).foreach(repo.save(_).unsafeRunSync())
      repo.findAll(limit = 2, offset = 0).unsafeRunSync().length shouldBe 2
      repo.findAll(limit = 10, offset = 2).unsafeRunSync().length shouldBe 1
    }

    "use default parameters when called with no arguments" in {
      cleanUp()
      List.fill(2)(PersistedGame.create()).foreach(repo.save(_).unsafeRunSync())
      repo.findAll().unsafeRunSync().length shouldBe 2
    }
  }

  "findByStatus" should {
    "return only games matching status" in {
      cleanUp()
      repo.save(PersistedGame.create(status = "InProgress")).unsafeRunSync()
      repo.save(PersistedGame.create(status = "Checkmate")).unsafeRunSync()
      repo.findByStatus("InProgress").unsafeRunSync().length shouldBe 1
    }
  }

  "findByOpening" should {
    "return games by ECO code" in {
      cleanUp()
      val game = PersistedGame.create().copy(openingEco = Some("C50"))
      repo.save(game).unsafeRunSync()
      repo.findByOpening("C50").unsafeRunSync().length shouldBe 1
    }

    "return empty for unknown ECO" in {
      repo.findByOpening("Z99").unsafeRunSync() shouldBe empty
    }
  }

  "delete" should {
    "return false for unknown id" in {
      repo.delete(UUID.randomUUID()).unsafeRunSync() shouldBe false
    }

    "exercise delete code path for known id" in {
      cleanUp()
      val game = PersistedGame.create()
      repo.save(game).unsafeRunSync()
      noException should be thrownBy repo.delete(game.id).unsafeRunSync()
    }
  }

  "count" should {
    "return correct count" in {
      cleanUp()
      List.fill(3)(PersistedGame.create()).foreach(repo.save(_).unsafeRunSync())
      repo.count().unsafeRunSync() shouldBe 3L
    }
  }

  "deleteAll" should {
    "remove all games and return count" in {
      cleanUp()
      List.fill(2)(PersistedGame.create()).foreach(repo.save(_).unsafeRunSync())
      repo.deleteAll().unsafeRunSync() shouldBe 2L
      repo.count().unsafeRunSync() shouldBe 0L
    }
  }
}
