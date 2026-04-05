package chess.application.game

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.effect.Ref
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.io.fen.RegexFenParser
import chess.controller.io.pgn.PgnFileIO
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GameSessionEventPublishingSpec extends AnyWordSpec with Matchers {
  given FenIO = RegexFenParser
  given PgnIO = PgnFileIO()

  "GameSessionService" should {
    "publish lifecycle events for create and move" in {
      val recordedRef = Ref.unsafe[IO, Vector[GameSessionEvent]](Vector.empty)
      val publisher = new GameEventPublisher:
        override def publish(event: GameSessionEvent): IO[Unit] =
          recordedRef.update(_ :+ event)

      val service = new GameSessionService(publisher)
      val gameId = service.createGame().unsafeRunSync().toOption.get._1
      service.makeMove(gameId, "e4").unsafeRunSync() shouldBe a[Right[?, ?]]

      val events = recordedRef.get.unsafeRunSync()
      events.map(_.eventType) shouldBe Vector("game_created", "move_applied")
      events.last.move shouldBe Some("e4")
    }
  }
}

