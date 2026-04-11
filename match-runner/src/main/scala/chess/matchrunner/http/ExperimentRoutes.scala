package chess.matchrunner.http

import cats.effect.IO
import chess.matchrunner.application.{ExperimentRequest, ExperimentRunner, ExperimentSummary}
import chess.matchrunner.data.MatchRunnerRepository
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.io.*

import java.util.UUID

final class ExperimentRoutes(
    runner: ExperimentRunner,
    repository: MatchRunnerRepository[IO]
):
  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // POST /experiments — persist record, start batch in background, return 202 + experiment
    case req @ POST -> Root / "experiments" =>
      req.as[ExperimentRequest].flatMap { request =>
        runner.startAsync(request).flatMap(exp => Accepted(exp.asJson))
      }

    // GET /experiments — list all experiments
    case GET -> Root / "experiments" =>
      repository.listExperiments().flatMap(exps => Ok(exps.asJson))

    // GET /experiments/:id
    case GET -> Root / "experiments" / UUIDVar(id) =>
      repository.findExperiment(id).flatMap {
        case Some(exp) => Ok(exp.asJson)
        case None      => NotFound(ApiError(s"Experiment $id not found").asJson)
      }

    // GET /experiments/:id/runs
    case GET -> Root / "experiments" / UUIDVar(id) / "runs" =>
      repository.findExperiment(id).flatMap {
        case None => NotFound(ApiError(s"Experiment $id not found").asJson)
        case Some(_) =>
          repository.listRuns(id).flatMap(runs => Ok(runs.asJson))
      }

    // GET /experiments/:id/summary
    case GET -> Root / "experiments" / UUIDVar(id) / "summary" =>
      repository.findExperiment(id).flatMap {
        case None => NotFound(ApiError(s"Experiment $id not found").asJson)
        case Some(exp) =>
          repository.listRuns(id).flatMap { runs =>
            Ok(ExperimentSummary.fromRuns(exp, runs).asJson)
          }
      }
  }

private final case class ApiError(error: String)
private object ApiError:
  import io.circe.generic.semiauto.*
  given io.circe.Encoder[ApiError] = deriveEncoder
