package chess.persistence.mongodb

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.persistence.model.PersistedGame
import chess.persistence.repository.GameRepository
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

import java.time.Instant
import java.util.UUID

/** Integration tests for MongoGameRepository using Testcontainers.
  *
  * These tests verify the MongoDB persistence layer works correctly with a real MongoDB instance running in a Docker
  * container. Each test suite starts/stops its own container to ensure test isolation.
  */
class MongoGameRepositoryIntegrationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  private var container: MongoDBContainer = _
  private var client: MongoClient[IO] = _
  private var database: MongoDatabase[IO] = _
  private var repository: GameRepository[IO] = _

  override def beforeAll(): Unit =
    super.beforeAll()
    // Start MongoDB container
    container = new MongoDBContainer(DockerImageName.parse("mongo:7.0"))
    container.start()

    // Initialize MongoDB client and repository
    val connectionString = container.getReplicaSetUrl
    val setup = for
      mongoClient <- MongoClient.fromConnectionString[IO](connectionString)
      db <- mongoClient.getDatabase("chess_test")
      collection <- db.getCollectionWithCodec[PersistedGame]("games")
    yield (mongoClient, db, new MongoGameRepository(collection))

    val (mongoClient, db, repo) = setup.unsafeRunSync()
    client = mongoClient
    database = db
    repository = repo

  override def afterAll(): Unit =
    // Clean up resources
    if client != null then client.close().unsafeRunSync()
    if container != null then container.stop()
    super.afterAll()

  "MongoGameRepository" should {

    "save and retrieve a game by id" in {
      val game = PersistedGame.create(
        fenHistory = List("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
        pgnMoves = List("e4"),
        currentTurn = "Black",
        status = "InProgress"
      )

      val result = for
        saved <- repository.save(game)
        retrieved <- repository.findById(game.id)
      yield (saved, retrieved)

      val (saved, retrieved) = result.unsafeRunSync()
      saved.id shouldBe game.id
      retrieved shouldBe defined
      retrieved.get.id shouldBe game.id
      retrieved.get.pgnMoves shouldBe List("e4")
      retrieved.get.currentTurn shouldBe "Black"
      retrieved.get.status shouldBe "InProgress"
    }

    "update an existing game with upsert" in {
      val game = PersistedGame.create(
        pgnMoves = List("e4"),
        status = "InProgress"
      )

      val result = for
        _ <- repository.save(game)
        updated = game.copy(pgnMoves = List("e4", "e5"), status = "Checkmate")
        _ <- repository.save(updated)
        retrieved <- repository.findById(game.id)
      yield retrieved

      val retrieved = result.unsafeRunSync()
      retrieved shouldBe defined
      retrieved.get.pgnMoves shouldBe List("e4", "e5")
      retrieved.get.status shouldBe "Checkmate"
    }

    "return None when game not found" in {
      val randomId = UUID.randomUUID()
      val result = repository.findById(randomId).unsafeRunSync()
      result shouldBe None
    }

    "retrieve all games with pagination" in {
      val games = (1 to 5).map { i =>
        PersistedGame.create(
          pgnMoves = List(s"move$i"),
          status = "InProgress"
        )
      }.toList

      val result = for
        _ <- repository.deleteAll() // Clean slate
        _ <- games.traverse(repository.save)
        all <- repository.findAll(limit = 10)
        page1 <- repository.findAll(limit = 2, offset = 0)
        page2 <- repository.findAll(limit = 2, offset = 2)
      yield (all, page1, page2)

      val (all, page1, page2) = result.unsafeRunSync()
      all.size shouldBe 5
      page1.size shouldBe 2
      page2.size shouldBe 2
      // Verify pagination returns different games
      page1.map(_.id).intersect(page2.map(_.id)) shouldBe empty
    }

    "find games by status" in {
      val result = for
        _ <- repository.deleteAll()
        game1 = PersistedGame.create(status = "InProgress")
        game2 = PersistedGame.create(status = "Checkmate")
        game3 = PersistedGame.create(status = "InProgress")
        _ <- repository.save(game1)
        _ <- repository.save(game2)
        _ <- repository.save(game3)
        inProgress <- repository.findByStatus("InProgress")
        checkmate <- repository.findByStatus("Checkmate")
        stalemate <- repository.findByStatus("Stalemate")
      yield (inProgress, checkmate, stalemate)

      val (inProgress, checkmate, stalemate) = result.unsafeRunSync()
      inProgress.size shouldBe 2
      checkmate.size shouldBe 1
      stalemate shouldBe empty
    }

    "find games by opening ECO code" in {
      val result = for
        _ <- repository.deleteAll()
        game1 = PersistedGame.create().copy(openingEco = Some("B12"), openingName = Some("Caro-Kann"))
        game2 = PersistedGame.create().copy(openingEco = Some("C45"), openingName = Some("Scotch Game"))
        game3 = PersistedGame.create().copy(openingEco = Some("B12"), openingName = Some("Caro-Kann"))
        _ <- repository.save(game1)
        _ <- repository.save(game2)
        _ <- repository.save(game3)
        b12Games <- repository.findByOpening("B12")
        c45Games <- repository.findByOpening("C45")
        e20Games <- repository.findByOpening("E20")
      yield (b12Games, c45Games, e20Games)

      val (b12Games, c45Games, e20Games) = result.unsafeRunSync()
      b12Games.size shouldBe 2
      c45Games.size shouldBe 1
      e20Games shouldBe empty
    }

    "delete a game by id" in {
      val game = PersistedGame.create(status = "InProgress")

      val result = for
        _ <- repository.save(game)
        deleted <- repository.delete(game.id)
        retrieved <- repository.findById(game.id)
      yield (deleted, retrieved)

      val (deleted, retrieved) = result.unsafeRunSync()
      deleted shouldBe true
      retrieved shouldBe None
    }

    "return false when deleting non-existent game" in {
      val randomId = UUID.randomUUID()
      val deleted = repository.delete(randomId).unsafeRunSync()
      deleted shouldBe false
    }

    "count total games" in {
      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(PersistedGame.create())
        _ <- repository.save(PersistedGame.create())
        _ <- repository.save(PersistedGame.create())
        count <- repository.count()
      yield count

      result.unsafeRunSync() shouldBe 3
    }

    "delete all games" in {
      val result = for
        _ <- repository.save(PersistedGame.create())
        _ <- repository.save(PersistedGame.create())
        beforeCount <- repository.count()
        deletedCount <- repository.deleteAll()
        afterCount <- repository.count()
      yield (beforeCount, deletedCount, afterCount)

      val (beforeCount, deletedCount, afterCount) = result.unsafeRunSync()
      beforeCount should be > 0L
      deletedCount shouldBe beforeCount
      afterCount shouldBe 0L
    }

    "handle games with complete data including openings" in {
      val now = Instant.now()
      val game = PersistedGame(
        id = UUID.randomUUID(),
        fenHistory = List(
          "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
          "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
        ),
        pgnMoves = List("e4", "e5"),
        currentTurn = "White",
        status = "InProgress",
        result = None,
        openingEco = Some("C44"),
        openingName = Some("King's Pawn Opening"),
        createdAt = now,
        updatedAt = now
      )

      val result = for
        _ <- repository.save(game)
        retrieved <- repository.findById(game.id)
      yield retrieved

      val retrieved = result.unsafeRunSync()
      retrieved shouldBe defined
      retrieved.get.fenHistory shouldBe game.fenHistory
      retrieved.get.pgnMoves shouldBe game.pgnMoves
      retrieved.get.openingEco shouldBe Some("C44")
      retrieved.get.openingName shouldBe Some("King's Pawn Opening")
    }

    "retrieve games ordered by creation date (newest first)" in {
      val result = for
        _ <- repository.deleteAll()
        game1 = PersistedGame.create(pgnMoves = List("first"))
        _ <- IO.sleep(scala.concurrent.duration.Duration.fromNanos(100000)) // Small delay
        game2 = PersistedGame.create(pgnMoves = List("second"))
        _ <- IO.sleep(scala.concurrent.duration.Duration.fromNanos(100000))
        game3 = PersistedGame.create(pgnMoves = List("third"))
        _ <- repository.save(game1)
        _ <- repository.save(game2)
        _ <- repository.save(game3)
        all <- repository.findAll()
      yield all

      val all = result.unsafeRunSync()
      all.size shouldBe 3
      // Should be ordered by creation date descending (newest first)
      all.head.pgnMoves shouldBe List("third")
      all.last.pgnMoves shouldBe List("first")
    }

    "handle limit parameter correctly" in {
      val result = for
        _ <- repository.deleteAll()
        games = (1 to 10).map(_ => PersistedGame.create()).toList
        _ <- games.traverse(repository.save)
        limited <- repository.findAll(limit = 3)
      yield limited

      val limited = result.unsafeRunSync()
      limited.size shouldBe 3
    }

    "return empty list when no games match status filter" in {
      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(PersistedGame.create(status = "InProgress"))
        games <- repository.findByStatus("NonExistentStatus")
      yield games

      result.unsafeRunSync() shouldBe empty
    }
  }
