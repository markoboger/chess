package chess.aview

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class SessionMovePublicationGateSpec extends AnyWordSpec with Matchers:

  "SessionMovePublicationGate" should {
    "publish local moves by default" in {
      val gate = new SessionMovePublicationGate

      gate.shouldPublishObservedMove() shouldBe true
    }

    "suppress the next observed move after a remote move is marked" in {
      val gate = new SessionMovePublicationGate

      gate.markRemoteMoveApplied()

      gate.shouldPublishObservedMove() shouldBe false
      gate.pendingRemoteMoveCount shouldBe 0
    }

    "resume publishing after consuming a remote move" in {
      val gate = new SessionMovePublicationGate

      gate.markRemoteMoveApplied()

      gate.shouldPublishObservedMove() shouldBe false
      gate.shouldPublishObservedMove() shouldBe true
    }

    "queue multiple remote moves independently" in {
      val gate = new SessionMovePublicationGate

      gate.markRemoteMoveApplied()
      gate.markRemoteMoveApplied()

      gate.shouldPublishObservedMove() shouldBe false
      gate.shouldPublishObservedMove() shouldBe false
      gate.shouldPublishObservedMove() shouldBe true
    }
  }
