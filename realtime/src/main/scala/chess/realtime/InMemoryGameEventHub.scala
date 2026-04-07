package chess.realtime

import cats.effect.{IO, Ref, Resource}
import cats.effect.std.Supervisor
import chess.application.game.{GameEventPublisher, GameSessionEvent}
import fs2.Stream
import fs2.concurrent.Topic

/** In-memory event hub backed by one fs2 Topic per game.
  *
  * Each game gets a Topic on first access. Publishing broadcasts to all current WebSocket
  * subscribers for that game. Events are not replayed — subscribers receive only events
  * published after they subscribe (i.e. future moves).
  */
final class InMemoryGameEventHub private (
    private val topics: Ref[IO, Map[String, Topic[IO, GameSessionEvent]]]
) extends GameEventPublisher:

  /** Get the existing Topic for a game or atomically create a new one. */
  private def getOrCreate(gameId: String): IO[Topic[IO, GameSessionEvent]] =
    // Eagerly allocate a candidate topic; modify will discard it if one already exists.
    Topic[IO, GameSessionEvent].flatMap { candidate =>
      topics.modify { map =>
        map.get(gameId) match
          case Some(existing) => (map, existing)
          case None           => (map.updated(gameId, candidate), candidate)
      }
    }

  override def publish(event: GameSessionEvent): IO[Unit] =
    getOrCreate(event.gameId).flatMap(_.publish1(event).void)

  /** Stream of events for a game. Only events published *after* subscribing are delivered. */
  def subscribe(gameId: String): Stream[IO, GameSessionEvent] =
    Stream.eval(getOrCreate(gameId)).flatMap(_.subscribe(256))

  /** Resource-based subscribe: the subscription is fully registered when the Resource is
    * acquired, so any events published inside `Resource.use` are guaranteed to be delivered.
    * Prefer this over [[subscribe]] in tests or anywhere precise ordering matters.
    */
  def subscribeWhenReady(gameId: String): Resource[IO, Stream[IO, GameSessionEvent]] =
    Resource.eval(getOrCreate(gameId)).flatMap(_.subscribeAwait(256))

  def clear(gameId: String): IO[Unit] =
    topics.update(_ - gameId)

object InMemoryGameEventHub:
  def create: IO[InMemoryGameEventHub] =
    Ref.of[IO, Map[String, Topic[IO, GameSessionEvent]]](Map.empty)
      .map(new InMemoryGameEventHub(_))
