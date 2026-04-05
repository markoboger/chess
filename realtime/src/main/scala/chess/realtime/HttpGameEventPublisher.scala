package chess.realtime

import cats.effect.IO
import chess.application.game.{GameEventPublisher, GameSessionEvent}
import org.http4s.Method.POST
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.Request

/** HTTP adapter that forwards published game session events to the realtime service. */
final class HttpGameEventPublisher(client: Client[IO], baseUri: Uri) extends GameEventPublisher:
  import GameEventJson.given

  override def publish(event: GameSessionEvent): IO[Unit] =
    val uri = baseUri / "events"
    val request = Request[IO](method = POST, uri = uri).withEntity(event)
    client.successful(request).void
