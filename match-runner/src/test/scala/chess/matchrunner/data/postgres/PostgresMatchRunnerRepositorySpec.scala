package chess.matchrunner.data.postgres

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.matchrunner.TestcontainersSupport
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchResult, MatchRun}
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.PostgreSQLContainer

import scala.compiletime.uninitialized

final class PostgresMatchRunnerRepositorySpec extends AnyWordSpec with Matchers with BeforeAndAfterAll:

  private val container = new PostgreSQLContainer("postgres:16-alpine")

  private var repo: PostgresMatchRunnerRepository = uninitialized
  private var closePg: IO[Unit] = IO.unit

  override def beforeAll(): Unit =
    TestcontainersSupport.configureDockerDesktopIfNeeded()
    container.start()
    val resource = for
      ce <- ExecutionContexts.fixedThreadPool[IO](2)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        container.getJdbcUrl,
        container.getUsername,
        container.getPassword,
        ce
      )
      r <- cats.effect.Resource.eval(PostgresMatchRunnerRepository.create(xa))
    yield r

    val (repository, finalizer) = resource.allocated.unsafeRunSync()
    repo = repository
    closePg = finalizer

  override def afterAll(): Unit =
    closePg.unsafeRunSync()
    container.stop()

  private def cleanUp(): Unit =
    repo.deleteAll().unsafeRunSync()

  "PostgresMatchRunnerRepository" should {
    "create and find experiments" in {
      cleanUp()
      val experiment = Experiment.create("Strategy A vs B", Some("baseline comparison"), requestedGames = 20)
      repo.saveExperiment(experiment).unsafeRunSync()

      val found = repo.findExperiment(experiment.id).unsafeRunSync()
      found shouldBe Some(experiment)
    }

    "list experiments and update status" in {
      cleanUp()
      val experiment = Experiment.create("Batch run", None, requestedGames = 10)
      repo.saveExperiment(experiment).unsafeRunSync()

      repo.updateExperimentStatus(experiment.id, ExperimentStatus.Running).unsafeRunSync() shouldBe true

      val listed = repo.listExperiments().unsafeRunSync()
      listed should have size 1
      listed.head.status shouldBe ExperimentStatus.Running
    }

    "save and list match runs for an experiment" in {
      cleanUp()
      val experiment = Experiment.create("Matchups", None, requestedGames = 2)
      repo.saveExperiment(experiment).unsafeRunSync()

      val run1 = MatchRun.create(experiment.id, "game-1", "minimax", "random")
      val run2 = MatchRun.create(experiment.id, "game-2", "random", "minimax")

      repo.saveMatchRun(run1).unsafeRunSync()
      repo.saveMatchRun(run2).unsafeRunSync()

      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.map(_.chessGameId) shouldBe List("game-1", "game-2")
      repo.countRuns(experiment.id).unsafeRunSync() shouldBe 2L
    }

    "upsert completed match run details" in {
      cleanUp()
      val experiment = Experiment.create("Results", None, requestedGames = 1)
      repo.saveExperiment(experiment).unsafeRunSync()

      val initial = MatchRun.create(experiment.id, "game-1", "minimax", "random")
      repo.saveMatchRun(initial).unsafeRunSync()

      val completed = initial.copy(
        finishedAt = Some(initial.startedAt.plusSeconds(30)),
        result = Some(MatchResult.WhiteWin),
        winner = Some("white"),
        moveCount = Some(42),
        finalFen = Some("final-fen"),
        pgn = Some("1. e4 e5"),
        errorMessage = None
      )
      repo.saveMatchRun(completed).unsafeRunSync()

      val stored = repo.listRuns(experiment.id).unsafeRunSync().head
      stored.result shouldBe Some(MatchResult.WhiteWin)
      stored.moveCount shouldBe Some(42)
      stored.finalFen shouldBe Some("final-fen")
    }
  }
