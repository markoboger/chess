package chess.realtime

import cats.effect.IO
import chess.application.game.GameSessionEvent
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.io.*

object GameEventRoutes:
  import GameEventJson.given
  final case class RealtimeHealthResponse(status: String, service: String)
  object RealtimeHealthResponse:
    given Encoder[RealtimeHealthResponse] = deriveEncoder
    given Decoder[RealtimeHealthResponse] = deriveDecoder

  def routes(hub: InMemoryGameEventHub): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "health" =>
        Ok(RealtimeHealthResponse("ok", "realtime-service"))

      case req @ POST -> Root / "events" =>
        req.as[GameSessionEvent].flatMap { event =>
          hub.publish(event) *> Accepted()
        }

      case GET -> Root / "events" / gameId =>
        hub.eventsFor(gameId).flatMap(Ok(_))
    }
