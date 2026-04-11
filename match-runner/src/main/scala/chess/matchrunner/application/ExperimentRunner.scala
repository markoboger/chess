package chess.matchrunner.application

import cats.effect.IO
import cats.effect.kernel.Temporal
import chess.matchrunner.MatchRunnerConfig
import chess.matchrunner.data.MatchRunnerRepository
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchResult, MatchRun}
import chess.matchrunner.http.{ChessApiClient, GameStateResponse}

import java.time.Instant
import scala.concurrent.duration.*

final class ExperimentRunner(
    client: ChessApiClient,
    repository: MatchRunnerRepository[IO],
    config: MatchRunnerConfig
):

  // Blocking: runs all games sequentially and returns the final experiment.
  // Used by the TUI.
  def runExperiment(
      request: ExperimentRequest,
      onRunFinished: MatchRun => IO[Unit] = _ => IO.unit
  ): IO[Experiment] =
    val experiment = Experiment.create(
      name = request.name,
      description = request.description,
      requestedGames = request.games,
      status = ExperimentStatus.Running
    )
    for
      _ <- repository.saveExperiment(experiment)
      result <- runBatches(experiment, request, onRunFinished)
    yield result

  // Non-blocking: persists the experiment record and starts batch execution in the background.
  // Returns the initial experiment record immediately.
  // Used by the HTTP API.
  def startAsync(request: ExperimentRequest): IO[Experiment] =
    val experiment = Experiment.create(
      name = request.name,
      description = request.description,
      requestedGames = request.games,
      status = ExperimentStatus.Running
    )
    for
      _ <- repository.saveExperiment(experiment)
      _ <- runBatches(experiment, request).start
    yield experiment

  private def runBatches(
      experiment: Experiment,
      request: ExperimentRequest,
      onRunFinished: MatchRun => IO[Unit] = _ => IO.unit
  ): IO[Experiment] =
    for
      hadErrors1 <- runBatch(experiment, request.whiteStrategy, request.blackStrategy, request, count = request.games, onRunFinished = onRunFinished)
      hadErrors2 <- if request.mirroredPairs then
        runBatch(experiment, request.blackStrategy, request.whiteStrategy, request, count = request.games, onRunFinished = onRunFinished)
      else IO.pure(false)
      finishedAt = Instant.now()
      totalDurationMs = finishedAt.toEpochMilli - experiment.createdAt.toEpochMilli
      hadErrors = hadErrors1 || hadErrors2
      finalStatus = if hadErrors then ExperimentStatus.Failed else ExperimentStatus.Completed
      finalExperiment = experiment.copy(status = finalStatus, finishedAt = Some(finishedAt), totalDurationMs = Some(totalDurationMs))
      _ <- repository.saveExperiment(finalExperiment)
    yield finalExperiment

  private def runBatch(
      experiment: Experiment,
      whiteStrategy: String,
      blackStrategy: String,
      request: ExperimentRequest,
      count: Int,
      hadErrors: Boolean = false,
      onRunFinished: MatchRun => IO[Unit]
  ): IO[Boolean] =
    if count <= 0 then IO.pure(hadErrors)
    else
      runSingleGame(experiment, whiteStrategy, blackStrategy, request, onRunFinished).flatMap { runHadError =>
        runBatch(experiment, whiteStrategy, blackStrategy, request, count - 1, hadErrors || runHadError, onRunFinished)
      }

  private def runSingleGame(
      experiment: Experiment,
      whiteStrategy: String,
      blackStrategy: String,
      request: ExperimentRequest,
      onRunFinished: MatchRun => IO[Unit]
  ): IO[Boolean] =
    client
      .createPassiveCvCGame(
        whiteStrategy = whiteStrategy,
        blackStrategy = blackStrategy,
        startFen = request.startFen,
        clockInitialMs = request.clockInitialMs,
        clockIncrementMs = request.clockIncrementMs
      )
      .flatMap {
        case Right(created) =>
          val initialRun = MatchRun.create(
            experimentId = experiment.id,
            chessGameId = created.gameId,
            whiteStrategy = whiteStrategy,
            blackStrategy = blackStrategy
          )

          val gameTimeoutMs = request.clockInitialMs
            .map(perSide => perSide * 2 + 3_000L)
            .getOrElse(config.matchTimeoutMs)

          for
            _ <- repository.saveMatchRun(initialRun)
            completed <- pollUntilFinished(created.gameId, gameTimeoutMs)
            finishedAt = Instant.now()
            completedRun = enrichRun(initialRun, completed, finishedAt)
            _ <- repository.saveMatchRun(completedRun)
            _ <- onRunFinished(completedRun)
          yield completedRun.errorMessage.nonEmpty

        case Left(error) =>
          val failedAt = Instant.now()
          val failedRun = MatchRun.create(
            experimentId = experiment.id,
            chessGameId = s"create-failed-${failedAt.toEpochMilli}",
            whiteStrategy = whiteStrategy,
            blackStrategy = blackStrategy
          ).copy(
            finishedAt = Some(failedAt),
            errorMessage = Some(error.message),
            durationMs = Some(0L)
          )

          repository.saveMatchRun(failedRun) *> onRunFinished(failedRun).as(true)
      }

  private def pollUntilFinished(gameId: String, timeoutMs: Long = config.matchTimeoutMs): IO[GameStateResponse] =
    Temporal[IO].timeoutTo(
      pollLoop(gameId),
      timeoutMs.millis,
      IO.pure(
        GameStateResponse(
          gameId = gameId,
          fen = "",
          pgn = "",
          status = "timeout",
          settings = chess.model.GameSettings()
        )
      )
    )

  private def pollLoop(gameId: String): IO[GameStateResponse] =
    client.getGameState(gameId).flatMap {
      case Right(state) if isTerminal(state.status) =>
        IO.pure(state)
      case Right(_) =>
        Temporal[IO].sleep(config.pollIntervalMs.millis) *> pollLoop(gameId)
      case Left(error) =>
        IO.pure(
          GameStateResponse(
            gameId = gameId,
            fen = "",
            pgn = "",
            status = s"error:${error.message}",
            settings = chess.model.GameSettings()
          )
        )
    }

  private def enrichRun(run: MatchRun, state: GameStateResponse, finishedAt: Instant): MatchRun =
    val terminal = classifyTerminalState(state)
    val durationMs = finishedAt.toEpochMilli - run.startedAt.toEpochMilli
    run.copy(
      finishedAt = Some(finishedAt),
      result = terminal.result,
      winner = terminal.winner,
      moveCount = Some(countPly(state.pgn)),
      finalFen = Option(state.fen).filter(_.nonEmpty),
      pgn = Option(state.pgn).filter(_.nonEmpty),
      errorMessage = terminal.error,
      durationMs = Some(durationMs)
    )

  private def isTerminal(status: String): Boolean =
    val normalized = status.toLowerCase
    normalized.contains("checkmate") ||
    normalized.contains("stalemate") ||
    normalized.contains("draw by threefold repetition") ||
    normalized.contains("wins on time") ||
    normalized == "timeout" ||
    normalized.startsWith("error:")

  private def classifyTerminalState(state: GameStateResponse): TerminalState =
    val normalized = state.status.toLowerCase
    if normalized.contains("checkmate") && normalized.contains("white wins") then
      TerminalState(Some(MatchResult.WhiteWin), Some("white"), None)
    else if normalized.contains("checkmate") && normalized.contains("black wins") then
      TerminalState(Some(MatchResult.BlackWin), Some("black"), None)
    else if normalized.contains("white wins on time") then
      TerminalState(Some(MatchResult.WhiteWin), Some("white-flag"), None)
    else if normalized.contains("black wins on time") then
      TerminalState(Some(MatchResult.BlackWin), Some("black-flag"), None)
    else if normalized.contains("stalemate") || normalized.contains("draw by threefold repetition") then
      TerminalState(Some(MatchResult.Draw), None, None)
    else if normalized == "timeout" then
      TerminalState(None, None, Some("Timed out while waiting for backend game completion"))
    else if normalized.startsWith("error:") then
      TerminalState(None, None, Some(state.status.drop("error:".length)))
    else
      TerminalState(None, None, Some(s"Unexpected terminal status: ${state.status}"))

  private def countPly(pgn: String): Int =
    pgn
      .split("\\s+")
      .toList
      .map(_.trim)
      .filter(token => token.nonEmpty && !token.matches("\\d+\\.") && token != "1-0" && token != "0-1" && token != "1/2-1/2")
      .length

  private final case class TerminalState(
      result: Option[MatchResult],
      winner: Option[String],
      error: Option[String]
  )
