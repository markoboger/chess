package chess.realtime

import cats.effect.IO
import cats.syntax.semigroupk.*
import chess.application.game.GameSessionEvent
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import scala.concurrent.duration.*

object GameEventRoutes:
  import GameEventJson.given
  private val heartbeatPayload = Map("eventType" -> "heartbeat").asJson.noSpaces

  final case class RealtimeHealthResponse(status: String, service: String)
  object RealtimeHealthResponse:
    given Encoder[RealtimeHealthResponse] = deriveEncoder
    given Decoder[RealtimeHealthResponse] = deriveDecoder

  /** HTTP-only routes (health + event ingestion). Usable without a WebSocket builder. */
  def httpRoutes(hub: InMemoryGameEventHub): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "health" =>
        Ok(RealtimeHealthResponse("ok", "realtime-service"))

      case req @ POST -> Root / "events" =>
        req.as[GameSessionEvent].flatMap { event =>
          hub.publish(event) *> Accepted()
        }
    }

  private[realtime] def outboundFrames(
      hub: InMemoryGameEventHub,
      gameId: String,
      heartbeatEvery: FiniteDuration = 15.seconds
  ): Stream[IO, WebSocketFrame] =
    val events =
      hub.subscribe(gameId).map(event => WebSocketFrame.Text(event.asJson.noSpaces))
    val heartbeats =
      Stream.awakeEvery[IO](heartbeatEvery).map(_ => WebSocketFrame.Text(heartbeatPayload))
    events.mergeHaltL(heartbeats)

  /** WebSocket route: clients subscribe here to receive live game events. */
  def wsRoutes(
      hub: InMemoryGameEventHub,
      wsBuilder: WebSocketBuilder2[IO],
      heartbeatEvery: FiniteDuration = 15.seconds
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "ws" / gameId =>
        val send = outboundFrames(hub, gameId, heartbeatEvery)
        wsBuilder.build(send, _.drain)
    }

  /** Combined routes used by the server. */
  def routes(
      hub: InMemoryGameEventHub,
      wsBuilder: WebSocketBuilder2[IO],
      heartbeatEvery: FiniteDuration = 15.seconds
  ): HttpRoutes[IO] =
    httpRoutes(hub) <+> wsRoutes(hub, wsBuilder, heartbeatEvery)
