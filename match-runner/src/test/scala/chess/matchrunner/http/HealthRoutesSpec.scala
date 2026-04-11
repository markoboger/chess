package chess.matchrunner.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.matchrunner.MatchRunnerConfig
import org.http4s.Method.GET
import org.http4s.Request
import org.http4s.Status
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HealthRoutesSpec extends AnyWordSpec with Matchers:

  "HealthRoutes" should {
    "return health details for the match-runner skeleton" in {
      val config = MatchRunnerConfig(
        port = 8084,
        chessApiUrl = "http://game-service:8081",
        pollIntervalMs = 100,
        matchTimeoutMs = 15000,
        postgresHost = "postgres",
        postgresPort = 5432,
        postgresDatabase = "chess",
        postgresUser = "runner",
        postgresPassword = "secret"
      )

      val response =
        HealthRoutes.routes(config).orNotFound.run(Request[IO](GET, uri"/health")).unsafeRunSync()

      response.status.shouldBe(Status.Ok)

      val body = response.as[String].unsafeRunSync()
      body.should(include("match-runner-service"))
      body.should(include("http://game-service:8081"))
      body.should(include("jdbc:postgresql://postgres:5432/chess"))
    }
  }
