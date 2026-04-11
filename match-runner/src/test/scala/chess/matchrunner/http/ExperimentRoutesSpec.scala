package chess.matchrunner.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.matchrunner.application.{ExperimentRequest, ExperimentRunner, ExperimentSummary}
import chess.matchrunner.data.MatchRunnerRepository
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchResult, MatchRun}
import chess.matchrunner.MatchRunnerConfig
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class ExperimentRoutesSpec extends AnyWordSpec with Matchers:

  private val config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 300_000L, "postgres", 5432, "chess", "user", "pass")

  private def makeApp(repo: InMemoryRepo): HttpApp[IO] =
    val client = new NoOpApiClient
    val runner = new ExperimentRunner(client, repo, config)
    ExperimentRoutes(runner, repo).routes.orNotFound

  private def run(app: HttpApp[IO], req: Request[IO]): Response[IO] =
    app.run(req).unsafeRunSync()

  "GET /experiments" should {
    "return an empty list when no experiments exist" in {
      val app = makeApp(new InMemoryRepo)
      val resp = run(app, Request[IO](Method.GET, uri"/experiments"))
      resp.status shouldBe Status.Ok
      resp.as[io.circe.Json].unsafeRunSync().isArray shouldBe true
    }

    "return persisted experiments" in {
      val repo = new InMemoryRepo
      val exp = Experiment.create("test exp", None, 2, ExperimentStatus.Completed)
      repo.saveExperiment(exp).unsafeRunSync()

      val app  = makeApp(repo)
      val resp = run(app, Request[IO](Method.GET, uri"/experiments"))
      resp.status shouldBe Status.Ok
      val body = resp.as[List[io.circe.Json]].unsafeRunSync()
      body should have size 1
    }
  }

  "GET /experiments/:id" should {
    "return the experiment for a known id" in {
      val repo = new InMemoryRepo
      val exp  = Experiment.create("my exp", Some("desc"), 5, ExperimentStatus.Running)
      repo.saveExperiment(exp).unsafeRunSync()

      val app  = makeApp(repo)
      val resp = run(app, Request[IO](Method.GET, Uri.unsafeFromString(s"/experiments/${exp.id}")))
      resp.status shouldBe Status.Ok
    }

    "return 404 for unknown id" in {
      val app  = makeApp(new InMemoryRepo)
      val resp = run(app, Request[IO](Method.GET, Uri.unsafeFromString(s"/experiments/${UUID.randomUUID()}")))
      resp.status shouldBe Status.NotFound
    }
  }

  "GET /experiments/:id/runs" should {
    "return empty list when experiment has no runs" in {
      val repo = new InMemoryRepo
      val exp  = Experiment.create("e", None, 1, ExperimentStatus.Completed)
      repo.saveExperiment(exp).unsafeRunSync()

      val app  = makeApp(repo)
      val resp = run(app, Request[IO](Method.GET, Uri.unsafeFromString(s"/experiments/${exp.id}/runs")))
      resp.status shouldBe Status.Ok
      resp.as[List[io.circe.Json]].unsafeRunSync() shouldBe empty
    }

    "return runs for a known experiment" in {
      val repo = new InMemoryRepo
      val exp  = Experiment.create("e", None, 1, ExperimentStatus.Completed)
      repo.saveExperiment(exp).unsafeRunSync()
      val run1 = MatchRun.create(exp.id, "game-1", "minimax", "random")
      repo.saveMatchRun(run1).unsafeRunSync()

      val app  = makeApp(repo)
      val resp = run(app, Request[IO](Method.GET, Uri.unsafeFromString(s"/experiments/${exp.id}/runs")))
      resp.status shouldBe Status.Ok
      resp.as[List[io.circe.Json]].unsafeRunSync() should have size 1
    }

    "return 404 for unknown experiment" in {
      val app  = makeApp(new InMemoryRepo)
      val resp = run(app, Request[IO](Method.GET, Uri.unsafeFromString(s"/experiments/${UUID.randomUUID()}/runs")))
      resp.status shouldBe Status.NotFound
    }
  }

  "GET /experiments/:id/summary" should {
    "return a summary with correct win counts" in {
      import java.time.Instant
      val repo = new InMemoryRepo
      val exp  = Experiment.create("e", None, 2, ExperimentStatus.Completed)
      repo.saveExperiment(exp).unsafeRunSync()
      val now = Instant.now()
      repo.saveMatchRun(MatchRun.create(exp.id, "g1", "minimax", "random").copy(
        finishedAt = Some(now), result = Some(MatchResult.WhiteWin), moveCount = Some(30), durationMs = Some(5000L)
      )).unsafeRunSync()
      repo.saveMatchRun(MatchRun.create(exp.id, "g2", "minimax", "random").copy(
        finishedAt = Some(now), result = Some(MatchResult.Draw), moveCount = Some(60), durationMs = Some(10000L)
      )).unsafeRunSync()

      val app  = makeApp(repo)
      val resp = run(app, Request[IO](Method.GET, Uri.unsafeFromString(s"/experiments/${exp.id}/summary")))
      resp.status shouldBe Status.Ok
      val json = resp.as[io.circe.Json].unsafeRunSync()
      json.hcursor.get[Int]("whiteWins").toOption shouldBe Some(1)
      json.hcursor.get[Int]("draws").toOption     shouldBe Some(1)
    }

    "return 404 for unknown experiment" in {
      val app  = makeApp(new InMemoryRepo)
      val resp = run(app, Request[IO](Method.GET, Uri.unsafeFromString(s"/experiments/${UUID.randomUUID()}/summary")))
      resp.status shouldBe Status.NotFound
    }
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private final class InMemoryRepo extends MatchRunnerRepository[IO]:
    private var experiments: Map[UUID, Experiment] = Map.empty
    private var runs: Vector[MatchRun]              = Vector.empty

    override def saveExperiment(e: Experiment): IO[Experiment] =
      IO { experiments = experiments.updated(e.id, e); e }

    override def findExperiment(id: UUID): IO[Option[Experiment]] =
      IO.pure(experiments.get(id))

    override def listExperiments(limit: Int, offset: Int): IO[List[Experiment]] =
      IO.pure(experiments.values.toList.sortBy(_.createdAt).reverse.slice(offset, offset + limit))

    override def updateExperimentStatus(id: UUID, status: ExperimentStatus): IO[Boolean] =
      IO { experiments.get(id).fold(false) { e => experiments = experiments.updated(id, e.copy(status = status)); true } }

    override def saveMatchRun(run: MatchRun): IO[MatchRun] =
      IO { runs.indexWhere(_.id == run.id) match
        case -1  => runs = runs :+ run
        case idx => runs = runs.updated(idx, run)
        run
      }

    override def listRuns(experimentId: UUID): IO[List[MatchRun]] =
      IO.pure(runs.filter(_.experimentId == experimentId).toList)

    override def countRuns(experimentId: UUID): IO[Long] =
      IO.pure(runs.count(_.experimentId == experimentId).toLong)

    override def deleteAll(): IO[Long] =
      IO { val n = (experiments.size + runs.size).toLong; experiments = Map.empty; runs = Vector.empty; n }

  private final class NoOpApiClient extends chess.matchrunner.http.ChessApiClient:
    import chess.model.GameSettings
    override def createGame(s: Option[String], gs: GameSettings) =
      IO.pure(Left(ChessApiClient.ChessApiError("stub")))
    override def createPassiveCvCGame(w: String, b: String, s: Option[String], ci: Option[Long], cr: Option[Long]) =
      IO.pure(Left(ChessApiClient.ChessApiError("stub")))
    override def getGameState(id: String) =
      IO.pure(Left(ChessApiClient.ChessApiError("stub")))
    override def loadPgn(id: String, pgn: String) =
      IO.pure(Left(ChessApiClient.ChessApiError("stub")))
