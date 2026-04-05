package chess.model

import java.time.Instant
import java.util.UUID
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PersistedGameSpec extends AnyWordSpec with Matchers {

  "PersistedGame.create" should {
    "create a game with default values" in {
      val game = PersistedGame.create()
      game.fenHistory    shouldBe List.empty
      game.pgnMoves      shouldBe List.empty
      game.currentTurn   shouldBe "White"
      game.status        shouldBe "InProgress"
      game.result        shouldBe None
      game.openingEco    shouldBe None
      game.openingName   shouldBe None
      game.id            should not be null
      game.createdAt     should not be null
      game.updatedAt     should not be null
    }

    "generate unique IDs for separate calls" in {
      val g1 = PersistedGame.create()
      val g2 = PersistedGame.create()
      g1.id should not be g2.id
    }

    "create a game with custom FEN history and moves" in {
      val fens  = List("startpos", "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
      val moves = List("e4")
      val game  = PersistedGame.create(fenHistory = fens, pgnMoves = moves, currentTurn = "Black")
      game.fenHistory  shouldBe fens
      game.pgnMoves    shouldBe moves
      game.currentTurn shouldBe "Black"
    }

    "create a game with custom status" in {
      val game = PersistedGame.create(status = "Checkmate")
      game.status shouldBe "Checkmate"
    }
  }

  "PersistedGame case class" should {
    "support copy with result" in {
      val game = PersistedGame.create()
      val updated = game.copy(
        result      = Some("1-0"),
        openingEco  = Some("C50"),
        openingName = Some("Italian Game"),
        status      = "Checkmate"
      )
      updated.result      shouldBe Some("1-0")
      updated.openingEco  shouldBe Some("C50")
      updated.openingName shouldBe Some("Italian Game")
      updated.status      shouldBe "Checkmate"
      updated.id          shouldBe game.id
    }

    "support equality by value" in {
      val now = Instant.now()
      val id  = UUID.randomUUID()
      val g1 = PersistedGame(id, Nil, Nil, "White", "InProgress", None, None, None, now, now)
      val g2 = PersistedGame(id, Nil, Nil, "White", "InProgress", None, None, None, now, now)
      g1 shouldBe g2
    }
  }
}
