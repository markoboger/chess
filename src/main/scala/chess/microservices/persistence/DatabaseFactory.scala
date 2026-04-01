package chess.microservices.persistence

import cats.effect.{IO, Resource}
import chess.microservices.persistence.mongodb.{MongoGameRepository, MongoOpeningRepository}
import chess.microservices.persistence.postgres.{PostgresGameRepository, PostgresOpeningRepository}
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import mongo4cats.client.MongoClient

/** Factory for creating database connections and repositories
  *
  * Creates appropriate repositories based on database configuration.
  */
object DatabaseFactory:

  /** Create game and opening repositories based on configuration
    *
    * @param config
    *   Database configuration
    * @return
    *   Resource containing both repositories
    */
  def createRepositories(
      config: DatabaseConfig
  ): Resource[IO, (GameRepository[IO], OpeningRepository[IO])] =
    config.active.toLowerCase match
      case "mongodb" => createMongoRepositories(config.mongodb)
      case "postgres" | "postgresql" => createPostgresRepositories(config.postgres)
      case other => throw new IllegalArgumentException(s"Unsupported database: $other")

  /** Create MongoDB repositories
    */
  private def createMongoRepositories(
      config: MongoConfig
  ): Resource[IO, (GameRepository[IO], OpeningRepository[IO])] =
    for
      client <- MongoClient.fromConnectionString[IO](config.uri)
      database <- Resource.eval(client.getDatabase(config.database))
      gameRepo <- Resource.eval(MongoGameRepository.create(database, "games"))
      openingRepo <- Resource.eval(MongoOpeningRepository.create(database, "openings"))
    yield (gameRepo, openingRepo)

  /** Create PostgreSQL repositories
    */
  private def createPostgresRepositories(
      config: PostgresConfig
  ): Resource[IO, (GameRepository[IO], OpeningRepository[IO])] =
    for
      transactor <- createPostgresTransactor(config)
      gameRepo <- Resource.eval(PostgresGameRepository.create(transactor))
      openingRepo <- Resource.eval(PostgresOpeningRepository.create(transactor))
    yield (gameRepo, openingRepo)

  /** Create a Hikari connection pool transactor for PostgreSQL
    */
  private def createPostgresTransactor(config: PostgresConfig): Resource[IO, HikariTransactor[IO]] =
    for
      ec <- ExecutionContexts.fixedThreadPool[IO](config.poolSize)
      transactor <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = "org.postgresql.Driver",
        url = s"jdbc:postgresql://${config.host}:${config.port}/${config.database}",
        user = config.user,
        pass = config.password,
        connectEC = ec
      )
    yield transactor

  /** Create repositories and seed openings if empty
    *
    * @param config
    *   Database configuration
    * @param seedIfEmpty
    *   Whether to seed openings if the repository is empty
    * @return
    *   Resource containing both repositories
    */
  def createAndSeedRepositories(
      config: DatabaseConfig,
      seedIfEmpty: Boolean = true
  ): Resource[IO, (GameRepository[IO], OpeningRepository[IO])] =
    for
      repos <- createRepositories(config)
      (gameRepo, openingRepo) = repos
      _ <- Resource.eval {
        if seedIfEmpty then
          for
            count <- openingRepo.count()
            _ <-
              if count == 0 then
                IO.println("Opening repository is empty, seeding...") *>
                  OpeningSeeder.seedAndVerify(openingRepo)
              else IO.println(s"Opening repository contains $count openings")
          yield ()
        else IO.unit
      }
    yield repos
