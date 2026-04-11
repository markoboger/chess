package chess.matchrunner

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.matchrunner.application.ExperimentRunner
import chess.matchrunner.data.postgres.PostgresMatchRunnerRepository
import chess.matchrunner.http.ChessApiClient
import chess.matchrunner.tui.MatchRunnerShell
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder

object MatchRunnerTuiMain:

  def main(args: Array[String]): Unit =
    run.unsafeRunSync()

  private def run: IO[Unit] =
    val config = MatchRunnerConfig.load()

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
      val shell = new MatchRunnerShell(runner, repository, chessClient)
      shell.run
    }
