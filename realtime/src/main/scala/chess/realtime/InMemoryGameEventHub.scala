package chess.realtime

import cats.effect.{IO, Ref}
import chess.application.game.{GameEventPublisher, GameSessionEvent}

/** In-memory adapter for capturing published game events.
  *
  * This is intentionally simple: it gives the app layer a realtime boundary now, while keeping the
  * transport implementation replaceable later by WebSocket, Redis, or a message broker.
  */
final class InMemoryGameEventHub private (
    private val events: Ref[IO, Map[String, Vector[GameSessionEvent]]]
) extends GameEventPublisher:

  override def publish(event: GameSessionEvent): IO[Unit] =
    events.update { state =>
      val existing = state.getOrElse(event.gameId, Vector.empty)
      state.updated(event.gameId, existing :+ event)
    }

  def eventsFor(gameId: String): IO[Vector[GameSessionEvent]] =
    events.get.map(_.getOrElse(gameId, Vector.empty))

  def clear(gameId: String): IO[Unit] =
    events.update(_ - gameId)

object InMemoryGameEventHub:
  def create: IO[InMemoryGameEventHub] =
    Ref.of[IO, Map[String, Vector[GameSessionEvent]]](Map.empty).map(new InMemoryGameEventHub(_))

