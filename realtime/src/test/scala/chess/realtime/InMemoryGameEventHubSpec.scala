package chess.realtime

import cats.effect.unsafe.implicits.global
import chess.application.game.GameSessionEvent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class InMemoryGameEventHubSpec extends AnyWordSpec with Matchers {

  "InMemoryGameEventHub" should {
    "store and return events per game" in {
      val hub = InMemoryGameEventHub.create.unsafeRunSync()
      val event = GameSessionEvent(
        gameId = "g1",
        eventType = "move_applied",
        fen = Some("fen"),
        pgn = Some("1. e4"),
        status = Some("Black to move"),
        move = Some("e4"),
        gameEvent = None,
        occurredAt = Instant.parse("2026-04-05T18:00:00Z")
      )

      hub.publish(event).unsafeRunSync()

      hub.eventsFor("g1").unsafeRunSync() shouldBe Vector(event)
      hub.eventsFor("g2").unsafeRunSync() shouldBe Vector.empty
    }
  }
}

