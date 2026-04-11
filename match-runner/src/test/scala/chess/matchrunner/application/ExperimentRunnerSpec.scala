package chess.matchrunner.application

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.matchrunner.MatchRunnerConfig
import chess.matchrunner.data.MatchRunnerRepository
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchRun}
import chess.matchrunner.http.{ChessApiClient, CreateGameResponse, GameStateResponse}
import chess.model.GameSettings
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class ExperimentRunnerSpec extends AnyWordSpec with Matchers:

  "ExperimentRunner" should {
    "run a complete passive batch and persist finished runs" in {
      val repo = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(
          Right(CreateGameResponse("game-1", "fen-1", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true))),
          Right(CreateGameResponse("game-2", "fen-2", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))
        ),
        gameStates = Map(
          "game-1" -> List(
            Right(GameStateResponse("game-1", "fen-x", "1. e4", "White to move", GameSettings())),
            Right(GameStateResponse("game-1", "fen-final-1", "1. e4 e5 2. Nf3 Nc6", "Checkmate! White wins!", GameSettings()))
          ),
          "game-2" -> List(
            Right(GameStateResponse("game-2", "fen-final-2", "1. d4 d5", "Stalemate! The game is a draw.", GameSettings()))
          )
        )
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))

      val experiment = runner.runExperiment(
        ExperimentRequest(
          name = "minimax vs random",
          description = Some("baseline"),
          whiteStrategy = "minimax",
          blackStrategy = "random",
          games = 2
        )
      ).unsafeRunSync()

      experiment.status shouldBe ExperimentStatus.Completed

      val storedExperiment = repo.findExperiment(experiment.id).unsafeRunSync().get
      storedExperiment.status.shouldBe(ExperimentStatus.Completed)

      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs should have size 2
      runs.map(_.chessGameId) shouldBe List("game-1", "game-2")
      runs.forall(_.finishedAt.nonEmpty) shouldBe true
      runs.head.result.map(_.toString) shouldBe Some("WhiteWin")
      runs.head.winner shouldBe Some("white")
      runs.head.moveCount shouldBe Some(4)
      runs(1).result.map(_.toString) shouldBe Some("Draw")
    }

    "mark the experiment as failed when game creation fails" in {
      val repo = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Left(ChessApiClient.ChessApiError("backend unavailable"))),
        gameStates = Map.empty
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))

      val experiment = runner.runExperiment(
        ExperimentRequest(
          name = "broken batch",
          description = None,
          whiteStrategy = "minimax",
          blackStrategy = "random",
          games = 1
        )
      ).unsafeRunSync()

      experiment.status shouldBe ExperimentStatus.Failed
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs should have size 1
      runs.head.errorMessage shouldBe Some("backend unavailable")
    }
  }

  private final class InMemoryMatchRunnerRepository extends MatchRunnerRepository[IO]:
    private var experiments: Map[UUID, Experiment] = Map.empty
    private var runs: Vector[MatchRun] = Vector.empty

    override def saveExperiment(experiment: Experiment): IO[Experiment] =
      IO {
        experiments = experiments.updated(experiment.id, experiment)
        experiment
      }

    override def findExperiment(id: UUID): IO[Option[Experiment]] =
      IO.pure(experiments.get(id))

    override def listExperiments(limit: Int, offset: Int): IO[List[Experiment]] =
      IO.pure(experiments.values.toList.sortBy(_.createdAt).reverse.slice(offset, offset + limit))

    override def updateExperimentStatus(id: UUID, status: ExperimentStatus): IO[Boolean] =
      IO {
        experiments.get(id) match
          case Some(experiment) =>
            experiments = experiments.updated(id, experiment.copy(status = status))
            true
          case None => false
      }

    override def saveMatchRun(run: MatchRun): IO[MatchRun] =
      IO {
        runs.indexWhere(_.id == run.id) match
          case -1 => runs = runs :+ run
          case idx => runs = runs.updated(idx, run)
        run
      }

    override def listRuns(experimentId: UUID): IO[List[MatchRun]] =
      IO.pure(runs.filter(_.experimentId == experimentId).toList)

    override def countRuns(experimentId: UUID): IO[Long] =
      IO.pure(runs.count(_.experimentId == experimentId).toLong)

    override def deleteAll(): IO[Long] =
      IO {
        val count = experiments.size + runs.size
        experiments = Map.empty
        runs = Vector.empty
        count.toLong
      }

  private final class StubChessApiClient(
      createResponses: List[Either[ChessApiClient.ChessApiError, CreateGameResponse]],
      gameStates: Map[String, List[Either[ChessApiClient.ChessApiError, GameStateResponse]]]
  ) extends ChessApiClient:
    private var createIndex = 0
    private var stateIndices: Map[String, Int] = Map.empty

    override def createGame(startFen: Option[String], settings: GameSettings) =
      createPassiveCvCGame(
        settings.whiteStrategy,
        settings.blackStrategy,
        startFen,
        settings.clockInitialMs,
        settings.clockIncrementMs
      )

    override def createPassiveCvCGame(
        whiteStrategy: String,
        blackStrategy: String,
        startFen: Option[String],
        clockInitialMs: Option[Long],
        clockIncrementMs: Option[Long]
    ) =
      IO {
        val response = createResponses.lift(createIndex).getOrElse(Left(ChessApiClient.ChessApiError("missing stub create response")))
        createIndex += 1
        response
      }

    override def getGameState(gameId: String) =
      IO {
        val states = gameStates.getOrElse(gameId, Nil)
        val currentIndex = stateIndices.getOrElse(gameId, 0)
        val response = states.lift(currentIndex).orElse(states.lastOption).getOrElse(Left(ChessApiClient.ChessApiError("missing stub game state")))
        stateIndices = stateIndices.updated(gameId, currentIndex + 1)
        response
      }

    override def loadPgn(gameId: String, pgn: String) =
      IO.pure(Right(chess.matchrunner.http.LoadPgnResponse(success = true, fen = "", moves = 0)))
