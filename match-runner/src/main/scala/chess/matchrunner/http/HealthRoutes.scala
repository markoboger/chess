package chess.matchrunner.http

import cats.effect.IO
import chess.matchrunner.MatchRunnerConfig
import io.circe.generic.auto.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*

object HealthRoutes:

  final case class HealthStatus(
      status: String,
      service: String,
      chessApiUrl: String,
      postgresJdbcUrl: String
  )

  def routes(config: MatchRunnerConfig): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "health" =>
        Ok(
          HealthStatus(
            status = "ok",
            service = "match-runner-service",
            chessApiUrl = config.chessApiUrl,
            postgresJdbcUrl = config.postgresJdbcUrl
          )
        )
    }
