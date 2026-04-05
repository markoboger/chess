package chess.realtime

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.application.game.GameSessionEvent
import org.http4s.Method
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.circe.CirceEntityCodec.given
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class HttpGameEventPublisherSpec extends AnyWordSpec with Matchers {
  import GameEventJson.given

  "HttpGameEventPublisher" should {
    "post events to the realtime service" in {
      var captured: Option[GameSessionEvent] = None
      val client = Client.fromHttpApp[IO](org.http4s.HttpApp[IO] { req =>
        req.method shouldBe Method.POST
        req.uri shouldBe Uri.unsafeFromString("http://realtime.test/events")
        req.as[GameSessionEvent].map { event =>
          captured = Some(event)
          Response[IO](Status.Accepted)
        }
      })

      val publisher = new HttpGameEventPublisher(client, Uri.unsafeFromString("http://realtime.test"))
      val event = GameSessionEvent(
        gameId = "g1",
        eventType = "game_created",
        fen = Some("fen"),
        pgn = Some(""),
        status = Some("White to move"),
        move = None,
        gameEvent = None,
        occurredAt = Instant.parse("2026-04-05T18:15:00Z")
      )

      publisher.publish(event).unsafeRunSync()

      captured shouldBe Some(event)
    }
  }
}

