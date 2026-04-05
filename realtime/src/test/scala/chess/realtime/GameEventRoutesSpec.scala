package chess.realtime

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.application.game.GameSessionEvent
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class GameEventRoutesSpec extends AnyWordSpec with Matchers {
  import GameEventJson.given

  "GameEventRoutes" should {
    "return health status" in {
      val hub = InMemoryGameEventHub.create.unsafeRunSync()
      val app = GameEventRoutes.routes(hub).orNotFound

      val resp = app.run(Request[IO](Method.GET, uri"/health")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "return stored events for a game" in {
      val hub = InMemoryGameEventHub.create.unsafeRunSync()
      val app = GameEventRoutes.routes(hub).orNotFound
      val event = GameSessionEvent(
        gameId = "g1",
        eventType = "game_created",
        fen = Some("fen"),
        pgn = Some(""),
        status = Some("White to move"),
        move = None,
        gameEvent = None,
        occurredAt = Instant.parse("2026-04-05T18:00:00Z")
      )
      hub.publish(event).unsafeRunSync()

      val resp = app.run(Request[IO](Method.GET, uri"/events/g1")).unsafeRunSync()
      resp.status shouldBe Status.Ok
      resp.as[Vector[GameSessionEvent]].unsafeRunSync() shouldBe Vector(event)
    }

    "accept posted events" in {
      val hub = InMemoryGameEventHub.create.unsafeRunSync()
      val app = GameEventRoutes.routes(hub).orNotFound
      val event = GameSessionEvent(
        gameId = "g2",
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
      hub.eventsFor("g2").unsafeRunSync() shouldBe Vector(event)
    }
  }
}
