package chess.seeder

import cats.effect.{IO, IOApp, ExitCode}
import chess.persistence.model.Opening
import chess.persistence.mongodb.MongoOpeningRepository
import chess.persistence.postgres.PostgresOpeningRepository
import chess.controller.io.opening.OpeningParser
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import mongo4cats.client.MongoClient
import mongo4cats.circe._
import io.circe.generic.auto._

/** CLI application that seeds the Lichess openings into both databases and prints a timing comparison.
  *
  * Usage: sbt "seeder/runMain chess.seeder.SeedOpeningsApp"
  */
object SeedOpeningsApp extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    for
      _ <- IO.println("=" * 60)
      _ <- IO.println("Chess Openings Database Seeder")
      _ <- IO.println("=" * 60)

      _ <- IO.println("\n[1/5] Parsing Lichess TSV files...")
      parseStart <- IO.monotonic
      openings <- IO(OpeningParser.parseLichessOpenings())
      parseEnd <- IO.monotonic
      _ <- IO.println(s"  Parsed ${openings.length} openings in ${(parseEnd - parseStart).toMillis}ms")
      _ <- IO(OpeningParser.printStatistics(openings))

      _ <- IO.println("\n[2/5] Seeding PostgreSQL...")
      pgResult <- seedPostgres(openings)
      _ <- IO.println(s"  Write: ${pgResult._1} openings in ${pgResult._2}ms")

      _ <- IO.println("\n[3/5] Seeding MongoDB...")
      mongoResult <- seedMongo(openings)
      _ <- IO.println(s"  Write: ${mongoResult._1} openings in ${mongoResult._2}ms")

      _ <- IO.println("\n[4/5] Read benchmarks...")
      pgReadMs <- benchmarkPostgresReads()
      mongoReadMs <- benchmarkMongoReads()

      _ <- IO.println("\n[5/5] Summary")
      _ <- IO.println("=" * 60)
      _ <- IO.println(f"  ${"Operation"}%-30s ${"PostgreSQL"}%12s ${"MongoDB"}%12s")
      _ <- IO.println(f"  ${"-" * 30}%-30s ${"-" * 12}%12s ${"-" * 12}%12s")
      _ <- IO.println(f"  ${"Write all openings"}%-30s ${pgResult._2 + "ms"}%12s ${mongoResult._2 + "ms"}%12s")
      _ <- IO.println(f"  ${"findByEco (A00)"}%-30s ${pgReadMs._1 + "ms"}%12s ${mongoReadMs._1 + "ms"}%12s")
      _ <- IO.println(f"  ${"findByName (Sicilian)"}%-30s ${pgReadMs._2 + "ms"}%12s ${mongoReadMs._2 + "ms"}%12s")
      _ <- IO.println(f"  ${"findAll (100)"}%-30s ${pgReadMs._3 + "ms"}%12s ${mongoReadMs._3 + "ms"}%12s")
      _ <- IO.println(f"  ${"count"}%-30s ${pgReadMs._4 + "ms"}%12s ${mongoReadMs._4 + "ms"}%12s")
      _ <- IO.println("=" * 60)
      _ <- IO.println("Done!")
    yield ExitCode.Success

  private def seedPostgres(openings: List[Opening]): IO[(Int, Long)] =
    val url = pgUrl
    ExecutionContexts.fixedThreadPool[IO](4).use { ce =>
      HikariTransactor.newHikariTransactor[IO]("org.postgresql.Driver", url, pgUser, pgPass, ce).use { xa =>
        for
          repo <- PostgresOpeningRepository.create(xa)
          _ <- repo.deleteAll()
          start <- IO.monotonic
          count <- repo.saveAll(openings)
          end <- IO.monotonic
        yield (count, (end - start).toMillis)
      }
    }

  private def seedMongo(openings: List[Opening]): IO[(Int, Long)] =
    MongoClient.fromConnectionString[IO](mongoUri).use { client =>
      for
        db <- client.getDatabase(mongoDb)
        col <- db.getCollectionWithCodec[Opening]("openings")
        repo = new MongoOpeningRepository(col)
        _ <- repo.deleteAll()
        start <- IO.monotonic
        count <- repo.saveAll(openings)
        end <- IO.monotonic
      yield (count, (end - start).toMillis)
    }

  private def benchmarkPostgresReads(): IO[(Long, Long, Long, Long)] =
    ExecutionContexts.fixedThreadPool[IO](4).use { ce =>
      HikariTransactor.newHikariTransactor[IO]("org.postgresql.Driver", pgUrl, pgUser, pgPass, ce).use { xa =>
        for
          repo <- PostgresOpeningRepository.create(xa)
          t1 <- timed(repo.findByEco("A00"))
          t2 <- timed(repo.findByName("Sicilian"))
          t3 <- timed(repo.findAll(100))
          t4 <- timed(repo.count())
        yield (t1, t2, t3, t4)
      }
    }

  private def benchmarkMongoReads(): IO[(Long, Long, Long, Long)] =
    MongoClient.fromConnectionString[IO](mongoUri).use { client =>
      for
        db <- client.getDatabase(mongoDb)
        col <- db.getCollectionWithCodec[Opening]("openings")
        repo = new MongoOpeningRepository(col)
        t1 <- timed(repo.findByEco("A00"))
        t2 <- timed(repo.findByName("Sicilian"))
        t3 <- timed(repo.findAll(100))
        t4 <- timed(repo.count())
      yield (t1, t2, t3, t4)
    }

  private def timed[A](action: IO[A]): IO[Long] =
    for
      start <- IO.monotonic
      _ <- action
      end <- IO.monotonic
    yield (end - start).toMillis

  private def pgUrl =
    s"jdbc:postgresql://${sys.env.getOrElse("POSTGRES_HOST", "localhost")}:${sys.env.getOrElse("POSTGRES_PORT", "5432")}/${sys.env
        .getOrElse("POSTGRES_DATABASE", "chess")}"
  private def pgUser = sys.env.getOrElse("POSTGRES_USER", "chess")
  private def pgPass = sys.env.getOrElse("POSTGRES_PASSWORD", "chess123")
  private def mongoUri = sys.env.getOrElse("MONGO_URI", "mongodb://chess:chess123@localhost:27017")
  private def mongoDb = sys.env.getOrElse("MONGO_DATABASE", "chess")
