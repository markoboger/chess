package chess.matchrunner

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.semigroupk.*
import chess.matchrunner.application.ExperimentRunner
import chess.matchrunner.data.postgres.PostgresMatchRunnerRepository
import chess.matchrunner.http.{ChessApiClient, ExperimentRoutes, HealthRoutes}
import chess.matchrunner.tui.MatchRunnerShell
import com.comcast.ip4s.*
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

object MatchRunnerTuiMain:

  def main(args: Array[String]): Unit =
    run.unsafeRunSync()

  private def run: IO[Unit] =
    val config = MatchRunnerConfig.load()

    val resources = for
      client <- EmberClientBuilder.default[IO].build
      ec     <- ExecutionContexts.fixedThreadPool[IO](4)
      xa     <- HikariTransactor.newHikariTransactor[IO](
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
      val runner      = new ExperimentRunner(chessClient, repository, config)
      val shell       = new MatchRunnerShell(runner, repository, chessClient)

      // Start the REST API server alongside the TUI so the GUI can browse results
      val allRoutes = (HealthRoutes.routes(config) <+> ExperimentRoutes(runner, repository).routes).orNotFound
      val httpApp   = Logger.httpApp[IO](logHeaders = false, logBody = false)(allRoutes)
      val serverResource = EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(config.port).getOrElse(port"8084"))
        .withHttpApp(httpApp)
        .build

      serverResource.use { server =>
        IO.println(s"REST API listening on ${server.address} (GUI browse enabled)") *>
          shell.run
      }
    }
