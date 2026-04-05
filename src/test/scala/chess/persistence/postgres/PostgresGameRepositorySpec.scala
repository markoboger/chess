package chess.persistence.postgres

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.model.PersistedGame
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.PostgreSQLContainer
import scala.compiletime.uninitialized

class PostgresGameRepositorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  private val container = new PostgreSQLContainer("postgres:16-alpine")

  private var repo: PostgresGameRepository = uninitialized
  private var closePg: IO[Unit]            = IO.unit

  override def beforeAll(): Unit = {
    container.start()
    val resource = for {
      ce <- ExecutionContexts.fixedThreadPool[IO](2)
      xa <- HikariTransactor.newHikariTransactor[IO](
              "org.postgresql.Driver",
              container.getJdbcUrl,
              container.getUsername,
              container.getPassword,
              ce
            )
      r  <- cats.effect.Resource.eval(PostgresGameRepository.create(xa))
    } yield r
    val (r, finalizer) = resource.allocated.unsafeRunSync()
    repo    = r
    closePg = finalizer
  }

  override def afterAll(): Unit = {
    closePg.unsafeRunSync()
    container.stop()
  }

  private def cleanUp(): Unit = repo.deleteAll().unsafeRunSync()

  "createTable / createIndexes" should {
    "be idempotent" in {
      noException should be thrownBy repo.createTable().unsafeRunSync()
      noException should be thrownBy repo.createIndexes().unsafeRunSync()
    }
  }

  "save" should {
    "insert a new game and return it with updated timestamp" in {
      cleanUp()
      val game    = PersistedGame.create()
      val saved   = repo.save(game).unsafeRunSync()
      saved.id     shouldBe game.id
      saved.status shouldBe "InProgress"
      repo.count().unsafeRunSync() shouldBe 1L
    }

    "upsert on duplicate id" in {
      cleanUp()
      val game    = PersistedGame.create()
      repo.save(game).unsafeRunSync()
      val updated = game.copy(status = "Checkmate")
      repo.save(updated).unsafeRunSync()
      repo.count().unsafeRunSync() shouldBe 1L
      val found = repo.findById(game.id).unsafeRunSync().get
      found.status shouldBe "Checkmate"
    }

    "persist a game with optional opening info" in {
      cleanUp()
      val game = PersistedGame.create().copy(
        openingEco  = Some("C50"),
        openingName = Some("Italian Game"),
        result      = Some("1-0"),
        status      = "Checkmate"
      )
      repo.save(game).unsafeRunSync()
      val found = repo.findById(game.id).unsafeRunSync().get
      found.openingEco  shouldBe Some("C50")
      found.openingName shouldBe Some("Italian Game")
      found.result      shouldBe Some("1-0")
    }
  }

  "findById" should {
    "return None for unknown id" in {
      import java.util.UUID
      repo.findById(UUID.randomUUID()).unsafeRunSync() shouldBe None
    }

    "return Some for existing id" in {
      cleanUp()
      val game = PersistedGame.create()
      repo.save(game).unsafeRunSync()
      repo.findById(game.id).unsafeRunSync() shouldBe defined
    }
  }

  "findAll" should {
    "return games with limit and offset" in {
      cleanUp()
      val g1 = PersistedGame.create()
      val g2 = PersistedGame.create()
      val g3 = PersistedGame.create()
      List(g1, g2, g3).foreach(repo.save(_).unsafeRunSync())
      repo.findAll(limit = 2, offset = 0).unsafeRunSync().length shouldBe 2
      repo.findAll(limit = 10, offset = 2).unsafeRunSync().length shouldBe 1
    }
  }

  "findByStatus" should {
    "return only games with matching status" in {
      cleanUp()
      repo.save(PersistedGame.create(status = "InProgress")).unsafeRunSync()
      repo.save(PersistedGame.create(status = "Checkmate")).unsafeRunSync()
      val result = repo.findByStatus("InProgress").unsafeRunSync()
      result.length shouldBe 1
      result.head.status shouldBe "InProgress"
    }
  }

  "findByOpening" should {
    "return games by ECO code" in {
      cleanUp()
      val game = PersistedGame.create().copy(openingEco = Some("C50"))
      repo.save(game).unsafeRunSync()
      val result = repo.findByOpening("C50").unsafeRunSync()
      result.length shouldBe 1
    }

    "return empty for unknown ECO" in {
      repo.findByOpening("Z99").unsafeRunSync() shouldBe empty
    }
  }

  "delete" should {
    "remove the game and return true" in {
      cleanUp()
      val game = PersistedGame.create()
      repo.save(game).unsafeRunSync()
      repo.delete(game.id).unsafeRunSync() shouldBe true
      repo.count().unsafeRunSync() shouldBe 0L
    }

    "return false for unknown id" in {
      import java.util.UUID
      repo.delete(UUID.randomUUID()).unsafeRunSync() shouldBe false
    }
  }

  "count" should {
    "return correct number of games" in {
      cleanUp()
      repo.save(PersistedGame.create()).unsafeRunSync()
      repo.save(PersistedGame.create()).unsafeRunSync()
      repo.count().unsafeRunSync() shouldBe 2L
    }
  }

  "deleteAll" should {
    "remove all games and return count" in {
      cleanUp()
      repo.save(PersistedGame.create()).unsafeRunSync()
      repo.save(PersistedGame.create()).unsafeRunSync()
      repo.deleteAll().unsafeRunSync() shouldBe 2L
      repo.count().unsafeRunSync() shouldBe 0L
    }
  }
}
