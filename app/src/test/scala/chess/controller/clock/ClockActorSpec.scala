package chess.controller.clock

import chess.model.Color
import org.apache.pekko.actor.typed.ActorSystem
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

class ClockActorSpec extends AnyWordSpec with Matchers with Eventually {
  override given patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(3, Seconds), interval = Span(25, Millis))

  private def withClockActor(test: (ActorSystem[ClockActor.Command], AtomicLong, AtomicLong, AtomicReference[Option[Color]]) => Any): Unit = {
    val whiteElapsed = new AtomicLong(0L)
    val blackElapsed = new AtomicLong(0L)
    val timeoutColor = new AtomicReference[Option[Color]](None)

    val system = ActorSystem(
      ClockActor(
        onUpdate = (white, black) => {
          whiteElapsed.set(white)
          blackElapsed.set(black)
        },
        onTimeout = color => timeoutColor.set(Some(color))
      ),
      "clock-actor-spec"
    )

    try test(system, whiteElapsed, blackElapsed, timeoutColor)
    finally system.terminate()
  }

  "ClockActor" should {
    "tick the white clock after Start" in withClockActor { (system, whiteElapsed, blackElapsed, _) =>
      system ! ClockActor.Start

      eventually {
        whiteElapsed.get() should be >= 100L
        blackElapsed.get() shouldBe 0L
      }
    }

    "switch sides and credit increment to the side that just moved" in withClockActor {
      (system, whiteElapsed, blackElapsed, _) =>
        system ! ClockActor.Start

        eventually {
          whiteElapsed.get() should be >= 200L
        }

        val beforeSwitch = whiteElapsed.get()
        system ! ClockActor.Stop
        system ! ClockActor.SwitchSide(100L)
        system ! ClockActor.Start

        eventually {
          blackElapsed.get() should be >= 100L
          whiteElapsed.get() shouldBe (beforeSwitch - 100L).max(0L)
        }
    }

    "time out the active side in timed mode" in withClockActor { (system, _, _, timeoutColor) =>
      system ! ClockActor.SetMode(Some(200L), incrementMs = 0L)
      system ! ClockActor.Start

      eventually {
        timeoutColor.get() shouldBe Some(Color.White)
      }
    }

    "reset elapsed time while preserving the configured mode" in withClockActor {
      (system, whiteElapsed, blackElapsed, timeoutColor) =>
        system ! ClockActor.SetMode(Some(300L), incrementMs = 0L)
        system ! ClockActor.Start

        eventually {
          whiteElapsed.get() should be >= 100L
        }

        system ! ClockActor.Reset
        system ! ClockActor.Start

        eventually {
          blackElapsed.get() shouldBe 0L
          timeoutColor.get() shouldBe Some(Color.White)
        }
    }
  }
}
