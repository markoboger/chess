package chess.clock

import org.apache.pekko.actor.typed.{ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import chess.model.Color
import scala.concurrent.duration.*

/** Pekko Typed actor that owns all chess-clock state.
  *
  * The actor ticks every 100 ms and delegates UI updates via two callbacks
  * (supplied by the caller) so it remains decoupled from JavaFX.  The caller
  * is responsible for marshalling those callbacks onto the UI thread
  * (e.g. with `Platform.runLater`).
  *
  * == Message protocol ==
  * {{{
  *   Start              — begin ticking the active side's clock
  *   Stop               — pause ticking (state is preserved)
  *   SwitchSide(incMs)  — flip active side; credit increment to side that just moved
  *   SetMode(init, inc) — change clock mode and reset all elapsed time; also stops ticking
  *   Reset              — zero elapsed time, stop ticking, keep current mode
  * }}}
  *
  * == Future server note ==
  * When migrating to a server architecture, replace the function callbacks with
  * `ActorRef[Response]` messages and add a sealed `Response` ADT.
  */
object ClockActor:

  // ── Commands ───────────────────────────────────────────────────────────────
  sealed trait Command
  case object Start                                         extends Command
  case object Stop                                          extends Command
  case class  SwitchSide(incrementMs: Long)                 extends Command
  /** `initialMs = None` means no-limit (elapsed-time only). */
  case class  SetMode(initialMs: Option[Long], incrementMs: Long) extends Command
  case object Reset                                         extends Command
  private case object Tick                                  extends Command

  // ── Internal state (immutable) ─────────────────────────────────────────────
  private case class State(
    whiteElapsedMs: Long         = 0L,
    blackElapsedMs: Long         = 0L,
    isWhiteActive:  Boolean      = true,
    initialMs:      Option[Long] = None,
    incrementMs:    Long         = 0L
  )

  private val TickKey      = "clock-tick"
  private val TickInterval = 100.millis
  private val TickDeltaMs  = 100L

  /** Create a `ClockActor` behaviour.
    *
    * @param onUpdate  Called every tick with `(whiteElapsedMs, blackElapsedMs)`.
    * @param onTimeout Called once when a timed clock runs out, with the colour
    *                  that ran out of time.
    */
  def apply(
    onUpdate:  (Long, Long) => Unit,
    onTimeout: Color => Unit
  ): Behavior[Command] =
    Behaviors.withTimers { timers =>
      running(timers, State(), onUpdate, onTimeout)
    }

  private def running(
    timers:    TimerScheduler[Command],
    state:     State,
    onUpdate:  (Long, Long) => Unit,
    onTimeout: Color => Unit
  ): Behavior[Command] =
    Behaviors.receiveMessage:

      case Start =>
        timers.startTimerAtFixedRate(TickKey, Tick, TickInterval)
        running(timers, state, onUpdate, onTimeout)

      case Stop =>
        timers.cancel(TickKey)
        running(timers, state, onUpdate, onTimeout)

      case SwitchSide(incMs) =>
        // Credit increment to the side that just moved (subtract from elapsed)
        val next =
          if state.isWhiteActive then
            state.copy(isWhiteActive = false,
                       whiteElapsedMs = (state.whiteElapsedMs - incMs).max(0L))
          else
            state.copy(isWhiteActive = true,
                       blackElapsedMs = (state.blackElapsedMs - incMs).max(0L))
        running(timers, next, onUpdate, onTimeout)

      case SetMode(initialMs, incrementMs) =>
        timers.cancel(TickKey)
        running(timers, State(initialMs = initialMs, incrementMs = incrementMs),
                onUpdate, onTimeout)

      case Reset =>
        timers.cancel(TickKey)
        running(timers,
                State(initialMs = state.initialMs, incrementMs = state.incrementMs),
                onUpdate, onTimeout)

      case Tick =>
        val next =
          if state.isWhiteActive then
            state.copy(whiteElapsedMs = state.whiteElapsedMs + TickDeltaMs)
          else
            state.copy(blackElapsedMs = state.blackElapsedMs + TickDeltaMs)

        // Check for timeout in timed mode
        val timedOut = state.initialMs.exists { limit =>
          if state.isWhiteActive then next.whiteElapsedMs >= limit
          else next.blackElapsedMs >= limit
        }

        if timedOut then
          timers.cancel(TickKey)
          val loser = if state.isWhiteActive then Color.White else Color.Black
          onTimeout(loser)
          running(timers, next, onUpdate, onTimeout)
        else
          onUpdate(next.whiteElapsedMs, next.blackElapsedMs)
          running(timers, next, onUpdate, onTimeout)
