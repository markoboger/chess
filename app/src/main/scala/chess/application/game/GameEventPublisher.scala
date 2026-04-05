package chess.application.game

import cats.effect.IO

/** Port for publishing game session events to a realtime transport or downstream adapter. */
trait GameEventPublisher:
  def publish(event: GameSessionEvent): IO[Unit]

object GameEventPublisher:
  val noop: GameEventPublisher = _ => IO.unit

