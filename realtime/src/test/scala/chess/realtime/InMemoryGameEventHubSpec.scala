package chess.realtime

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.*
import chess.application.game.GameSessionEvent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import scala.concurrent.duration.*

class InMemoryGameEventHubSpec extends AnyWordSpec with Matchers {

  private val event = GameSessionEvent(
    gameId = "g1",
    eventType = "move_applied",
    fen = Some("fen"),
    pgn = Some("1. e4"),
    status = Some("Black to move"),
    move = Some("e4"),
    gameEvent = None,
    occurredAt = Instant.parse("2026-04-05T18:00:00Z")
  )

  "InMemoryGameEventHub" should {

    // subscribeWhenReady acquires the subscription during Resource.use entry,
    // so any publish inside the use block is guaranteed to reach the subscriber.

    "deliver a published event to a subscriber" in {
      val prog = InMemoryGameEventHub.create.flatMap { hub =>
        hub.subscribeWhenReady("g1").use { stream =>
          val collect = stream.take(1).compile.toVector
          val publish = hub.publish(event)
          (collect, publish).parMapN((res, _) => res)
        }
      }
      prog.timeout(5.seconds).unsafeRunSync() shouldBe Vector(event)
    }

    "isolate events by game ID" in {
      val event2 = event.copy(gameId = "g2")
      val prog = InMemoryGameEventHub.create.flatMap { hub =>
        hub.subscribeWhenReady("g1").use { s1 =>
          hub.subscribeWhenReady("g2").use { s2 =>
            val collect1 = s1.take(1).compile.toVector
            val collect2 = s2.take(1).compile.toVector
            val publish  = hub.publish(event) *> hub.publish(event2)
            (collect1, collect2, publish).parMapN((r1, r2, _) => (r1, r2))
          }
        }
      }
      val (r1, r2) = prog.timeout(5.seconds).unsafeRunSync()
      r1 shouldBe Vector(event)
      r2 shouldBe Vector(event2)
    }

    "fan-out a single event to multiple subscribers for the same game" in {
      val prog = InMemoryGameEventHub.create.flatMap { hub =>
        hub.subscribeWhenReady("g1").use { s1 =>
          hub.subscribeWhenReady("g1").use { s2 =>
            val collect1 = s1.take(1).compile.toVector
            val collect2 = s2.take(1).compile.toVector
            val publish  = hub.publish(event)
            (collect1, collect2, publish).parMapN((r1, r2, _) => (r1, r2))
          }
        }
      }
      val (r1, r2) = prog.timeout(5.seconds).unsafeRunSync()
      r1 shouldBe Vector(event)
      r2 shouldBe Vector(event)
    }

    "allow a topic to be cleared and recreated for the same game id" in {
      val refreshed = event.copy(
        pgn = Some("1. e4 e5"),
        status = Some("White to move"),
        move = Some("e5"),
        occurredAt = Instant.parse("2026-04-05T18:01:00Z")
      )

      val prog = InMemoryGameEventHub.create.flatMap { hub =>
        hub.publish(event) *>
          hub.clear("g1") *>
          hub.subscribeWhenReady("g1").use { stream =>
            val collect = stream.take(1).compile.lastOrError
            val publish = hub.publish(refreshed)
            (collect, publish).parMapN((received, _) => received)
          }
      }

      prog.timeout(5.seconds).unsafeRunSync() shouldBe refreshed
    }
  }
}
