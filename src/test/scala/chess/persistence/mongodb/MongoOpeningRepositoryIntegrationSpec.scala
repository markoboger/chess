package chess.persistence.mongodb

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.persistence.model.Opening
import chess.persistence.repository.OpeningRepository
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

/** Integration tests for MongoOpeningRepository using Testcontainers.
  *
  * These tests verify the MongoDB opening repository works correctly with a real MongoDB instance. Tests cover all CRUD
  * operations, search functionality, and batch operations.
  */
class MongoOpeningRepositoryIntegrationSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  private var container: MongoDBContainer = _
  private var client: MongoClient[IO] = _
  private var database: MongoDatabase[IO] = _
  private var repository: OpeningRepository[IO] = _

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
      collection <- db.getCollectionWithCodec[Opening]("openings")
    yield (mongoClient, db, new MongoOpeningRepository(collection))

    val (mongoClient, db, repo) = setup.unsafeRunSync()
    client = mongoClient
    database = db
    repository = repo

  override def afterAll(): Unit =
    // Clean up resources
    if client != null then client.close().unsafeRunSync()
    if container != null then container.stop()
    super.afterAll()

  "MongoOpeningRepository" should {

    "save and retrieve an opening by ECO code" in {
      val opening = Opening(
        eco = "B12",
        name = "Caro-Kann Defense",
        moves = "1. e4 c6",
        fen = "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        moveCount = 2
      )

      val result = for
        _ <- repository.deleteAll()
        saved <- repository.save(opening)
        retrieved <- repository.findByEco("B12")
      yield (saved, retrieved)

      val (saved, retrieved) = result.unsafeRunSync()
      saved.eco shouldBe "B12"
      retrieved shouldBe defined
      retrieved.get.name shouldBe "Caro-Kann Defense"
      retrieved.get.moves shouldBe "1. e4 c6"
      retrieved.get.moveCount shouldBe 2
    }

    "upsert an existing opening" in {
      val opening = Opening(
        eco = "C45",
        name = "Scotch Game",
        moves = "1. e4 e5 2. Nf3 Nc6 3. d4",
        fen = "r1bqkbnr/pppp1ppp/2n5/4p3/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq - 0 3",
        moveCount = 5
      )

      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(opening)
        updated = opening.copy(name = "Scotch Game: Main Line")
        _ <- repository.save(updated)
        retrieved <- repository.findByEco("C45")
      yield retrieved

      val retrieved = result.unsafeRunSync()
      retrieved shouldBe defined
      retrieved.get.name shouldBe "Scotch Game: Main Line"
    }

    "return None when opening not found" in {
      val result = repository.findByEco("Z99").unsafeRunSync()
      result shouldBe None
    }

    "save multiple openings in batch" in {
      val openings = List(
        Opening("A00", "Van't Kruijs Opening", "1. e3", "rnbqkbnr/pppppppp/8/8/8/4P3/PPPP1PPP/RNBQKBNR b KQkq - 0 1", 1),
        Opening("A01", "Nimzowitsch-Larsen Attack", "1. b3", "rnbqkbnr/pppppppp/8/8/8/1P6/P1PPPPPP/RNBQKBNR b KQkq - 0 1", 1),
        Opening("A02", "Bird's Opening", "1. f4", "rnbqkbnr/pppppppp/8/8/5P2/8/PPPPP1PP/RNBQKBNR b KQkq - 0 1", 1)
      )

      val result = for
        _ <- repository.deleteAll()
        count <- repository.saveAll(openings)
        all <- repository.findAll()
      yield (count, all)

      val (count, all) = result.unsafeRunSync()
      count shouldBe 3
      all.size shouldBe 3
    }

    "handle empty list in saveAll" in {
      val count = repository.saveAll(List.empty).unsafeRunSync()
      count shouldBe 0
    }

    "search openings by name (case-insensitive)" in {
      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(Opening("B12", "Caro-Kann Defense", "1. e4 c6", "fen1", 2))
        _ <- repository.save(Opening("B13", "Caro-Kann Defense: Exchange Variation", "1. e4 c6 2. d4 d5 3. exd5", "fen2", 5))
        _ <- repository.save(Opening("C45", "Scotch Game", "1. e4 e5 2. Nf3 Nc6 3. d4", "fen3", 5))
        _ <- repository.save(Opening("E20", "Nimzo-Indian Defense", "1. d4 Nf6 2. c4 e6 3. Nc3 Bb4", "fen4", 6))
        caroKann <- repository.findByName("Caro-Kann")
        scotch <- repository.findByName("scotch") // Test case-insensitivity
        indian <- repository.findByName("Indian")
        notFound <- repository.findByName("Ruy Lopez")
      yield (caroKann, scotch, indian, notFound)

      val (caroKann, scotch, indian, notFound) = result.unsafeRunSync()
      caroKann.size shouldBe 2
      scotch.size shouldBe 1
      scotch.head.eco shouldBe "C45"
      indian.size shouldBe 1
      notFound shouldBe empty
    }

    "retrieve all openings with pagination" in {
      val openings = (0 to 9).map { i =>
        Opening(
          eco = f"A$i%02d",
          name = s"Opening $i",
          moves = s"1. move$i",
          fen = s"fen$i",
          moveCount = i + 1
        )
      }.toList

      val result = for
        _ <- repository.deleteAll()
        _ <- openings.traverse(repository.save)
        all <- repository.findAll(limit = 20)
        page1 <- repository.findAll(limit = 3, offset = 0)
        page2 <- repository.findAll(limit = 3, offset = 3)
      yield (all, page1, page2)

      val (all, page1, page2) = result.unsafeRunSync()
      all.size shouldBe 10
      page1.size shouldBe 3
      page2.size shouldBe 3
      // Verify they're ordered by ECO code
      all.map(_.eco) shouldBe all.map(_.eco).sorted
    }

    "find openings by move count" in {
      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(Opening("A00", "Opening 1", "1. e3", "fen1", 1))
        _ <- repository.save(Opening("A01", "Opening 2", "1. e4 e5 2. Nf3", "fen2", 3))
        _ <- repository.save(Opening("A02", "Opening 3", "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4", "fen3", 7))
        _ <- repository.save(Opening("A03", "Opening 4", "1. d4 d5 2. c4", "fen4", 3))
        shortOpenings <- repository.findByMoveCount(maxMoves = 2)
        mediumOpenings <- repository.findByMoveCount(maxMoves = 4)
        allOpenings <- repository.findByMoveCount(maxMoves = 10)
      yield (shortOpenings, mediumOpenings, allOpenings)

      val (shortOpenings, mediumOpenings, allOpenings) = result.unsafeRunSync()
      shortOpenings.size shouldBe 1
      shortOpenings.head.moveCount shouldBe 1
      mediumOpenings.size shouldBe 3
      allOpenings.size shouldBe 4
    }

    "find a random opening" in {
      val openings = (0 to 4).map { i =>
        Opening(f"B$i%02d", s"Opening $i", s"moves$i", s"fen$i", i + 1)
      }.toList

      val result = for
        _ <- repository.deleteAll()
        _ <- openings.traverse(repository.save)
        random1 <- repository.findRandom()
        random2 <- repository.findRandom()
        random3 <- repository.findRandom()
      yield (random1, random2, random3)

      val (random1, random2, random3) = result.unsafeRunSync()
      random1 shouldBe defined
      random2 shouldBe defined
      random3 shouldBe defined
      // All randoms should be valid openings from our list
      openings.map(_.eco) should contain(random1.get.eco)
    }

    "return None for random opening when database is empty" in {
      val result = for
        _ <- repository.deleteAll()
        random <- repository.findRandom()
      yield random

      result.unsafeRunSync() shouldBe None
    }

    "count total openings" in {
      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(Opening("A00", "Opening 1", "moves1", "fen1", 1))
        _ <- repository.save(Opening("A01", "Opening 2", "moves2", "fen2", 2))
        _ <- repository.save(Opening("A02", "Opening 3", "moves3", "fen3", 3))
        count <- repository.count()
      yield count

      result.unsafeRunSync() shouldBe 3
    }

    "delete all openings" in {
      val result = for
        _ <- repository.save(Opening("A00", "Opening 1", "moves1", "fen1", 1))
        _ <- repository.save(Opening("A01", "Opening 2", "moves2", "fen2", 2))
        beforeCount <- repository.count()
        deletedCount <- repository.deleteAll()
        afterCount <- repository.count()
      yield (beforeCount, deletedCount, afterCount)

      val (beforeCount, deletedCount, afterCount) = result.unsafeRunSync()
      beforeCount should be > 0L
      deletedCount shouldBe beforeCount
      afterCount shouldBe 0L
    }

    "respect limit parameter in search operations" in {
      val openings = (0 to 19).map { i =>
        Opening(f"C$i%02d", s"Opening $i", s"moves$i", s"fen$i", i + 1)
      }.toList

      val result = for
        _ <- repository.deleteAll()
        _ <- openings.traverse(repository.save)
        limited <- repository.findByName("Opening", limit = 5)
      yield limited

      val limited = result.unsafeRunSync()
      limited.size shouldBe 5
    }

    "handle special characters in name search" in {
      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(Opening("C44", "King's Pawn Opening", "1. e4", "fen1", 1))
        _ <- repository.save(Opening("C45", "Scotch Game", "1. e4 e5", "fen2", 2))
        searchResult <- repository.findByName("King's")
      yield searchResult

      val searchResult = result.unsafeRunSync()
      searchResult.size shouldBe 1
      searchResult.head.name shouldBe "King's Pawn Opening"
    }

    "return openings ordered by ECO code" in {
      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(Opening("E20", "Opening E", "moves", "fen", 1))
        _ <- repository.save(Opening("A10", "Opening A", "moves", "fen", 1))
        _ <- repository.save(Opening("C50", "Opening C", "moves", "fen", 1))
        _ <- repository.save(Opening("B12", "Opening B", "moves", "fen", 1))
        all <- repository.findAll()
      yield all

      val all = result.unsafeRunSync()
      all.size shouldBe 4
      all.map(_.eco) shouldBe List("A10", "B12", "C50", "E20")
    }

    "handle maximum move count edge case" in {
      val result = for
        _ <- repository.deleteAll()
        _ <- repository.save(Opening("A00", "Short", "moves", "fen", 1))
        _ <- repository.save(Opening("A01", "Long", "moves", "fen", 20))
        found <- repository.findByMoveCount(maxMoves = 1)
      yield found

      val found = result.unsafeRunSync()
      found.size shouldBe 1
      found.head.eco shouldBe "A00"
    }
  }
