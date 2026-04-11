package chess.matchrunner

import cats.effect.{IO, IOApp}
import chess.matchrunner.application.{ExperimentRequest, ExperimentRunner, ExperimentSummary}
import chess.matchrunner.data.postgres.PostgresMatchRunnerRepository
import chess.matchrunner.http.ChessApiClient
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder

object MatchRunnerDemoApp extends IOApp.Simple:

  private val defaultCombos = Vector(
    ("random", "greedy"),
    ("greedy", "random"),
    ("minimax", "random")
  )

  def run: IO[Unit] =
    val config = MatchRunnerConfig.load()
    val gamesPerCombo = sys.env.get("MATCH_RUNNER_DEMO_GAMES").flatMap(_.toIntOption).getOrElse(1)

    val resources = for
      client <- EmberClientBuilder.default[IO].build
      ec <- ExecutionContexts.fixedThreadPool[IO](4)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        config.postgresJdbcUrl,
        config.postgresUser,
        config.postgresPassword,
        ec
      )
      repository <- cats.effect.Resource.eval(PostgresMatchRunnerRepository.create(xa))
    yield (client, repository)

    resources.use { case (httpClient, repository) =>
      val chessClient = ChessApiClient.http(Uri.unsafeFromString(config.chessApiUrl), httpClient)
      val runner = new ExperimentRunner(chessClient, repository, config)

      IO.println(s"Running demo CvC batches against ${config.chessApiUrl} with $gamesPerCombo game(s) per combo...") *>
        defaultCombos.foldLeft(IO.unit) { case (acc, (whiteStrategy, blackStrategy)) =>
          acc *> runCombo(runner, repository, whiteStrategy, blackStrategy, gamesPerCombo)
        }
    }

  private def runCombo(
      runner: ExperimentRunner,
      repository: PostgresMatchRunnerRepository,
      whiteStrategy: String,
      blackStrategy: String,
      games: Int
  ): IO[Unit] =
    val name = s"demo-$whiteStrategy-vs-$blackStrategy"
    val request = ExperimentRequest(
      name = name,
      description = Some("non-interactive demo batch"),
      whiteStrategy = whiteStrategy,
      blackStrategy = blackStrategy,
      games = games
    )

    for
      _ <- IO.println("")
      _ <- IO.println(s"Starting $name")
      experiment <- runner.runExperiment(request, run => IO.println(s"  finished ${run.chessGameId}: result=${run.result} error=${run.errorMessage}"))
      runs <- repository.listRuns(experiment.id)
      summary = ExperimentSummary.fromRuns(experiment, runs)
      _ <- IO.println(formatSummary(summary))
    yield ()

  private def formatSummary(summary: ExperimentSummary): String =
    f"""Summary for ${summary.experiment.name}
       |  status: ${summary.experiment.status}
       |  runs: ${summary.completedRuns}/${summary.totalRuns}
       |  white wins: ${summary.whiteWins}
       |  black wins: ${summary.blackWins}
       |  draws: ${summary.draws}
       |  errors: ${summary.errors}
       |  average moves: ${summary.averageMoves}%.2f
       |""".stripMargin
