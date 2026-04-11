package chess.matchrunner

import cats.effect.{IO, IOApp, Resource}
import cats.syntax.semigroupk.*
import chess.matchrunner.application.ExperimentRunner
import chess.matchrunner.data.postgres.PostgresMatchRunnerRepository
import chess.matchrunner.http.{ExperimentRoutes, HealthRoutes}
import com.comcast.ip4s.*
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

object MatchRunnerServer extends IOApp.Simple:

  def run: IO[Unit] =
    val config = MatchRunnerConfig.load()
    buildResources(config).use { case (runner, repository) =>
      val experimentRoutes = ExperimentRoutes(runner, repository).routes
      val healthRoutes = HealthRoutes.routes(config)
      val allRoutes = (healthRoutes <+> experimentRoutes).orNotFound
      val httpApp = Logger.httpApp[IO](logHeaders = false, logBody = false)(allRoutes)

      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(config.port).getOrElse(port"8084"))
        .withHttpApp(httpApp)
        .build
        .use { server =>
          IO.println(s"Match Runner Service started at ${server.address}") *>
            IO.println(s"Chess API: ${config.chessApiUrl}") *>
            IO.println(s"PostgreSQL: ${config.postgresJdbcUrl}") *>
            IO.println("") *>
            IO.println("Endpoints:") *>
            IO.println("  GET    /health") *>
            IO.println("  POST   /experiments") *>
            IO.println("  GET    /experiments") *>
            IO.println("  GET    /experiments/:id") *>
            IO.println("  GET    /experiments/:id/runs") *>
            IO.println("  GET    /experiments/:id/summary") *>
            IO.println("") *>
            IO.println("Press Ctrl+C to stop.") *>
            IO.never
        }
    }

  def buildResources(config: MatchRunnerConfig): Resource[IO, (ExperimentRunner, PostgresMatchRunnerRepository)] =
    for
      httpClient <- EmberClientBuilder.default[IO].build
      ec         <- ExecutionContexts.fixedThreadPool[IO](4)
      xa         <- HikariTransactor.newHikariTransactor[IO](
                      "org.postgresql.Driver",
                      config.postgresJdbcUrl,
                      config.postgresUser,
                      config.postgresPassword,
                      ec
                    )
      repository <- Resource.eval(PostgresMatchRunnerRepository.create(xa))
      chessClient = chess.matchrunner.http.ChessApiClient.http(Uri.unsafeFromString(config.chessApiUrl), httpClient)
      runner      = ExperimentRunner(chessClient, repository, config)
    yield (runner, repository)
