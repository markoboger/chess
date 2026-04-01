package chess.benchmark

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import chess.persistence.model.PersistedGame
import chess.persistence.mongodb.MongoGameRepository
import chess.persistence.postgres.PostgresGameRepository
import chess.persistence.repository.GameRepository
import com.mongodb.client.model.Indexes
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import mongo4cats.client.MongoClient
import mongo4cats.database.MongoDatabase
import org.openjdk.jmh.annotations.*

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.Random

/** JMH Benchmark comparing MongoDB and PostgreSQL persistence performance.
  *
  * Prerequisites: Start databases with docker-compose up -d before running benchmarks.
  *
  * This benchmark measures the performance characteristics of different database operations using both MongoDB and
  * PostgreSQL implementations of the GameRepository.
  *
  * Benchmark Modes:
  *   - Throughput: Operations per second
  *   - AverageTime: Average time per operation
  *
  * Test Scenarios:
  *   - Single game save operations
  *   - Bulk save operations (10 games)
  *   - FindById lookups
  *   - FindAll with pagination
  *   - Query by status
  *   - Query by opening ECO code
  */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = Array("-Xms2G", "-Xmx2G"))
class DatabasePersistenceBenchmark:

  // Test data
  var sampleGames: List[PersistedGame] = scala.compiletime.uninitialized
  var savedGameIds: List[UUID] = scala.compiletime.uninitialized
  var mongoRepo: GameRepository[IO] = scala.compiletime.uninitialized
  var postgresRepo: GameRepository[IO] = scala.compiletime.uninitialized

  // Resources that need cleanup
  var mongoClientResource: Resource[IO, MongoClient[IO]] = scala.compiletime.uninitialized
  var postgresTransactorResource: Resource[IO, HikariTransactor[IO]] = scala.compiletime.uninitialized
  var mongoClient: MongoClient[IO] = scala.compiletime.uninitialized
  var postgresTransactor: HikariTransactor[IO] = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    println("Setting up database connections...")
    println("NOTE: Ensure MongoDB and PostgreSQL are running via docker-compose!")

    // MongoDB connection string
    val mongoUrl = "mongodb://chess:chess123@localhost:27017/chess?authSource=admin"

    // Setup MongoDB
    import io.circe.generic.auto.*
    import mongo4cats.circe.*

    mongoClientResource = MongoClient.fromConnectionString[IO](mongoUrl)
    mongoClient = mongoClientResource.allocated.unsafeRunSync()._1

    val setupMongo = for
      database <- mongoClient.getDatabase("chess")
      collection <- database.getCollectionWithCodec[PersistedGame]("games")
      _ <- IO.blocking:
        try
          collection.createIndex(Indexes.ascending("id"))
          collection.createIndex(Indexes.ascending("status"))
          collection.createIndex(Indexes.ascending("openingEco"))
          collection.createIndex(Indexes.descending("createdAt"))
        catch case _: Exception => ()
    yield collection

    val mongoCollection = setupMongo.unsafeRunSync()
    mongoRepo = new MongoGameRepository(mongoCollection)

    // Setup PostgreSQL
    val dbUrl = "jdbc:postgresql://localhost:5432/chess"
    val dbUser = "chess"
    val dbPassword = "chess123"

    postgresTransactorResource = HikariTransactor.newHikariTransactor[IO](
      driverClassName = "org.postgresql.Driver",
      url = dbUrl,
      user = dbUser,
      pass = dbPassword,
      connectEC = scala.concurrent.ExecutionContext.global
    )

    postgresTransactor = postgresTransactorResource.allocated.unsafeRunSync()._1

    val postgresRepoWithSetup = new PostgresGameRepository(postgresTransactor)
    postgresRepoWithSetup.createTable().unsafeRunSync()
    postgresRepoWithSetup.createIndexes().unsafeRunSync()
    postgresRepo = postgresRepoWithSetup

    // Clean existing data
    println("Cleaning existing data...")
    mongoRepo.deleteAll().unsafeRunSync()
    postgresRepo.deleteAll().unsafeRunSync()

    // Generate test data
    println("Generating test data...")
    sampleGames = TestDataGenerator.generateBatch(1000, TestDataGenerator.DataProfile.Mixed)

    // Pre-populate databases with some data for read benchmarks
    println("Pre-populating databases with 500 games...")
    val prePopulateGames = TestDataGenerator.generateBatch(500, TestDataGenerator.DataProfile.Mixed)
    savedGameIds = prePopulateGames.map(_.id)

    prePopulateGames.foreach: game =>
      mongoRepo.save(game).unsafeRunSync()
      postgresRepo.save(game).unsafeRunSync()

    println(s"Setup complete! Pre-populated ${savedGameIds.length} games in each database.")

  @TearDown(Level.Trial)
  def tearDown(): Unit =
    println("Tearing down...")
    // Release resources
    mongoClientResource.allocated.unsafeRunSync()._2.unsafeRunSync()
    postgresTransactorResource.allocated.unsafeRunSync()._2.unsafeRunSync()
    println("Teardown complete!")

  // ===== MongoDB Benchmarks =====

  @Benchmark
  def mongoSaveSingleGame(): PersistedGame =
    val game = TestDataGenerator.generateGame()
    mongoRepo.save(game).unsafeRunSync()

  @Benchmark
  def mongoSaveBulk10(): List[PersistedGame] =
    val games = TestDataGenerator.generateBatch(10, TestDataGenerator.DataProfile.Mixed)
    games.map(g => mongoRepo.save(g).unsafeRunSync())

  @Benchmark
  def mongoFindById(): Option[PersistedGame] =
    val randomId = savedGameIds(Random.nextInt(savedGameIds.length))
    mongoRepo.findById(randomId).unsafeRunSync()

  @Benchmark
  def mongoFindAll100(): List[PersistedGame] =
    mongoRepo.findAll(limit = 100, offset = 0).unsafeRunSync()

  @Benchmark
  def mongoFindByStatus(): List[PersistedGame] =
    mongoRepo.findByStatus("InProgress", limit = 50).unsafeRunSync()

  @Benchmark
  def mongoFindByOpening(): List[PersistedGame] =
    mongoRepo.findByOpening("B20", limit = 50).unsafeRunSync()

  // ===== PostgreSQL Benchmarks =====

  @Benchmark
  def postgresSaveSingleGame(): PersistedGame =
    val game = TestDataGenerator.generateGame()
    postgresRepo.save(game).unsafeRunSync()

  @Benchmark
  def postgresSaveBulk10(): List[PersistedGame] =
    val games = TestDataGenerator.generateBatch(10, TestDataGenerator.DataProfile.Mixed)
    games.map(g => postgresRepo.save(g).unsafeRunSync())

  @Benchmark
  def postgresFindById(): Option[PersistedGame] =
    val randomId = savedGameIds(Random.nextInt(savedGameIds.length))
    postgresRepo.findById(randomId).unsafeRunSync()

  @Benchmark
  def postgresFindAll100(): List[PersistedGame] =
    postgresRepo.findAll(limit = 100, offset = 0).unsafeRunSync()

  @Benchmark
  def postgresFindByStatus(): List[PersistedGame] =
    postgresRepo.findByStatus("InProgress", limit = 50).unsafeRunSync()

  @Benchmark
  def postgresFindByOpening(): List[PersistedGame] =
    postgresRepo.findByOpening("B20", limit = 50).unsafeRunSync()
