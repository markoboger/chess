package chess.realtime

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.*
import chess.application.game.GameSessionEvent
import org.http4s.{Method, Request, Status}
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.implicits.*
import org.http4s.websocket.WebSocketFrame
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import scala.concurrent.duration.*

class GameEventRoutesSpec extends AnyWordSpec with Matchers {
  import GameEventJson.given

  private def makeApp() = {
    val hub = InMemoryGameEventHub.create.unsafeRunSync()
    (hub, GameEventRoutes.httpRoutes(hub).orNotFound)
  }

  "GameEventRoutes" should {

    "return health status" in {
      val (_, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/health")).unsafeRunSync()
      resp.status shouldBe Status.Ok
      resp.as[GameEventRoutes.RealtimeHealthResponse].unsafeRunSync() shouldBe
        GameEventRoutes.RealtimeHealthResponse("ok", "realtime-service")
    }

    "accept a posted event and return 202" in {
      val (_, app) = makeApp()
      val event = GameSessionEvent(
        gameId = "g1",
        eventType = "move_applied",
        fen = Some("fen"),
        pgn = Some("1. e4"),
        status = Some("Black to move"),
        move = Some("e4"),
        gameEvent = None,
        occurredAt = Instant.parse("2026-04-05T18:10:00Z")
      )
      val resp = app.run(Request[IO](Method.POST, uri"/events").withEntity(event)).unsafeRunSync()
      resp.status shouldBe Status.Accepted
    }

    "publish a posted event to subscribers" in {
      val hub = InMemoryGameEventHub.create.unsafeRunSync()
      val app = GameEventRoutes.httpRoutes(hub).orNotFound
      val event = GameSessionEvent(
        gameId = "g1",
        eventType = "move_applied",
        fen = Some("fen"),
        pgn = Some("1. e4"),
        status = Some("Black to move"),
        move = Some("e4"),
        gameEvent = None,
        occurredAt = Instant.parse("2026-04-05T18:10:00Z")
      )

      val delivered =
        hub.subscribeWhenReady("g1").use { stream =>
          val collect = stream.take(1).compile.lastOrError
          val post =
            app.run(Request[IO](Method.POST, uri"/events").withEntity(event)).flatMap { resp =>
              IO(resp.status shouldBe Status.Accepted)
            }
          (collect, post).parMapN((received, _) => received)
        }.unsafeRunSync()

      delivered shouldBe event
    }

    "reject unknown routes with 404" in {
      val (_, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/unknown")).unsafeRunSync()
      resp.status shouldBe Status.NotFound
    }

    "emit heartbeat frames for websocket subscribers" in {
      val hub = InMemoryGameEventHub.create.unsafeRunSync()
      val frame = GameEventRoutes
        .outboundFrames(hub, "g1", 10.millis)
        .take(1)
        .compile
        .lastOrError
        .unsafeRunSync()

      frame shouldBe WebSocketFrame.Text("""{"eventType":"heartbeat"}""")
    }
  }
}
