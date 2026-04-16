package chess.matchrunner.application

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.matchrunner.MatchRunnerConfig
import chess.matchrunner.data.MatchRunnerRepository
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchRun}
import chess.matchrunner.http.{ChessApiClient, CreateGameResponse, GameStateResponse}
import chess.model.GameSettings
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID
import scala.concurrent.duration.*

final class ExperimentRunnerSpec extends AnyWordSpec with Matchers with OptionValues:

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

    "classify a black-wins-by-checkmate result" in {
      val repo   = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("g1", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map("g1" -> List(Right(GameStateResponse("g1", "fen-f", "1. e4 e5", "Checkmate! Black wins!", GameSettings()))))
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))
      val experiment = runner.runExperiment(ExperimentRequest("bk", None, "random", "random", 1)).unsafeRunSync()
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.head.result.map(_.toString) shouldBe Some("BlackWin")
      runs.head.winner shouldBe Some("black")
    }

    "classify a white-wins-on-time (flag) result" in {
      val repo   = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("g2", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map("g2" -> List(Right(GameStateResponse("g2", "fen-f", "", "White wins on time!", GameSettings()))))
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))
      val experiment = runner.runExperiment(ExperimentRequest("wt", None, "random", "random", 1)).unsafeRunSync()
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.head.result.map(_.toString) shouldBe Some("WhiteWin")
      runs.head.winner shouldBe Some("white-flag")
    }

    "classify a black-wins-on-time (flag) result" in {
      val repo   = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("g3", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map("g3" -> List(Right(GameStateResponse("g3", "fen-f", "", "Black wins on time!", GameSettings()))))
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))
      val experiment = runner.runExperiment(ExperimentRequest("bt", None, "random", "random", 1)).unsafeRunSync()
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.head.result.map(_.toString) shouldBe Some("BlackWin")
      runs.head.winner shouldBe Some("black-flag")
    }

    "classify a draw-by-threefold-repetition result" in {
      val repo   = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("g4", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map("g4" -> List(Right(GameStateResponse("g4", "fen-f", "", "Draw by threefold repetition.", GameSettings()))))
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))
      val experiment = runner.runExperiment(ExperimentRequest("rep", None, "random", "random", 1)).unsafeRunSync()
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.head.result.map(_.toString) shouldBe Some("Draw")
    }

    "record a timeout as an error when the backend is too slow" in {
      val repo   = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("g5", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map("g5" -> List(Right(GameStateResponse("g5", "fen-f", "", "timeout", GameSettings()))))
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))
      val experiment = runner.runExperiment(ExperimentRequest("to", None, "random", "random", 1)).unsafeRunSync()
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.head.errorMessage shouldBe Some("Timed out while waiting for backend game completion")
    }

    "record an error: prefixed status as an error message" in {
      val repo   = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("g6", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map("g6" -> List(Right(GameStateResponse("g6", "fen-f", "", "error:engine crashed", GameSettings()))))
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))
      val experiment = runner.runExperiment(ExperimentRequest("err", None, "random", "random", 1)).unsafeRunSync()
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.head.errorMessage shouldBe Some("engine crashed")
    }

    "startAsync should return immediately and complete in the background" in {
      val repo = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("ga1", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map("ga1" -> List(Right(GameStateResponse("ga1", "fen-f", "1. e4", "Stalemate! The game is a draw.", GameSettings()))))
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))

      val experiment = runner.startAsync(ExperimentRequest("async", None, "random", "random", 1)).unsafeRunSync()
      experiment.status shouldBe ExperimentStatus.Running

      awaitStatus(repo, experiment.id, ExperimentStatus.Completed)
      repo.findExperiment(experiment.id).unsafeRunSync().value.status.shouldBe(ExperimentStatus.Completed)
    }

    "startAsync should run mirroredPairs as a second batch" in {
      val repo = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(
          Right(CreateGameResponse("gm1", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true))),
          Right(CreateGameResponse("gm2", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))
        ),
        gameStates = Map(
          "gm1" -> List(Right(GameStateResponse("gm1", "fen-f", "", "Draw by threefold repetition.", GameSettings()))),
          "gm2" -> List(Right(GameStateResponse("gm2", "fen-f", "", "Checkmate! White wins!", GameSettings())))
        )
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))

      val experiment = runner.startAsync(
        ExperimentRequest("mirrored", None, "minimax", "random", games = 1, mirroredPairs = true)
      ).unsafeRunSync()

      awaitStatus(repo, experiment.id, ExperimentStatus.Completed)
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.map(_.chessGameId).toSet shouldBe Set("gm1", "gm2")
    }

    "should time out via Temporal.timeoutTo when polling never reaches a terminal state" in {
      val repo = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("gt1", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map(
          "gt1" -> List(
            Right(GameStateResponse("gt1", "fen-x", "", "White to move", GameSettings())),
            Right(GameStateResponse("gt1", "fen-x", "", "Black to move", GameSettings()))
          )
        )
      )
      val runner = new ExperimentRunner(
        client,
        repo,
        config = MatchRunnerConfig(8084, "http://game-service:8081", pollIntervalMs = 50L, matchTimeoutMs = 1L, "postgres", 5432, "chess", "user", "pass")
      )

      val experiment = runner.runExperiment(ExperimentRequest("timeoutTo", None, "random", "random", 1)).unsafeRunSync()
      experiment.status shouldBe ExperimentStatus.Failed
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.head.errorMessage shouldBe Some("Timed out while waiting for backend game completion")
    }

    "should mark unknown checkmate message as an unexpected terminal status" in {
      val repo = new InMemoryMatchRunnerRepository
      val client = new StubChessApiClient(
        createResponses = List(Right(CreateGameResponse("gu1", "fen", GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)))),
        gameStates = Map("gu1" -> List(Right(GameStateResponse("gu1", "fen-f", "", "Checkmate!", GameSettings()))))
      )
      val runner = new ExperimentRunner(client, repo, config = MatchRunnerConfig(8084, "http://game-service:8081", 0L, 100L, "postgres", 5432, "chess", "user", "pass"))
      val experiment = runner.runExperiment(ExperimentRequest("unexpected", None, "random", "random", 1)).unsafeRunSync()
      val runs = repo.listRuns(experiment.id).unsafeRunSync()
      runs.head.errorMessage.value.should(include("Unexpected terminal status:"))
    }

  }

  private def awaitStatus(repo: MatchRunnerRepository[IO], id: UUID, status: ExperimentStatus): Unit =
    def loop(remaining: Int): IO[Unit] =
      if remaining <= 0 then IO.unit
      else
        repo.findExperiment(id).flatMap {
          case Some(e) if e.status == status => IO.unit
          case _ => IO.sleep(10.millis) *> loop(remaining - 1)
        }

    loop(100).unsafeRunSync()

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
