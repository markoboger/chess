package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.controller.io.fen.RegexFenParser

class MoveResultSpec extends AnyWordSpec with Matchers {

  val board: Board = Board.initial

  "MoveResult.Moved" should {
    val moved = MoveResult.Moved(board, GameEvent.Check)

    "report isSuccess as true" in {
      moved.isSuccess shouldBe true
    }

    "report isFailed as false" in {
      moved.isFailed shouldBe false
    }

    "return the board via get" in {
      moved.get shouldBe board
    }

    "return the board via getOrElse, ignoring default" in {
      val other = RegexFenParser.parseFEN("8/8/8/8/8/8/8/4K3").get
      moved.getOrElse(other) shouldBe board
    }

    "convert to Some via toOption" in {
      moved.toOption shouldBe Some(board)
    }

    "return the event via event" in {
      moved.event shouldBe Some(GameEvent.Check)
    }

    "use default gameEvent of GameEvent.Moved" in {
      val m = MoveResult.Moved(board)
      m.gameEvent shouldBe GameEvent.Moved
    }

    "apply f in foreach" in {
      var called = false
      moved.foreach(_ => called = true)
      called shouldBe true
    }

    "transform the board via map" in {
      val result = moved.map(identity)
      result.isSuccess shouldBe true
      result.board shouldBe board
    }

    "chain via flatMap" in {
      val result = moved.flatMap(b => MoveResult.Moved(b, GameEvent.Stalemate))
      result.isSuccess shouldBe true
      result.event shouldBe Some(GameEvent.Stalemate)
    }
  }

  "MoveResult.Failed" should {
    val failed = MoveResult.Failed(board, MoveError.InvalidMove)

    "report isSuccess as false" in {
      failed.isSuccess shouldBe false
    }

    "report isFailed as true" in {
      failed.isFailed shouldBe true
    }

    "throw NoSuchElementException on get" in {
      a[NoSuchElementException] should be thrownBy failed.get
    }

    "return the default via getOrElse" in {
      val other = RegexFenParser.parseFEN("8/8/8/8/8/8/8/4K3").get
      failed.getOrElse(other) shouldBe other
    }

    "convert to None via toOption" in {
      failed.toOption shouldBe None
    }

    "return None via event" in {
      failed.event shouldBe None
    }

    "not apply f in foreach" in {
      var called = false
      failed.foreach(_ => called = true)
      called shouldBe false
    }

    "return itself on map (short-circuit)" in {
      val result = failed.map(identity)
      result shouldBe failed
    }

    "return itself on flatMap (short-circuit)" in {
      val result = failed.flatMap(b => MoveResult.Moved(b, GameEvent.Check))
      result shouldBe failed
    }
  }

  "MoveResult for-comprehension" should {
    "chain successful moves" in {
      val result = for
        b1 <- board.move(Square("e2"), Square("e4"))
        b2 <- b1.move(Square("d7"), Square("d5"))
      yield b2

      result.isSuccess shouldBe true
    }

    "short-circuit on first failure" in {
      val result = for
        b1 <- board.move(
          Square("e2"),
          Square("e5")
        ) // invalid 3-square pawn move
        b2 <- b1.move(Square("d7"), Square("d5"))
      yield b2

      result.isFailed shouldBe true
    }
  }

  "MoveResult game events" should {
    "detect check" in {
      // Scholar's mate setup: after Qh5, f6, Qxf7+ gives check
      val board = RegexFenParser
        .parseFEN("rnbqkbnr/pppp1ppp/5p2/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR")
        .get
      // Qxf7+ gives check
      val result = board.move(Square("h5"), Square("f7"))
      result.isSuccess shouldBe true
      result.event shouldBe Some(GameEvent.Check)
    }

    "detect checkmate" in {
      // Scholar's mate: Qxf7#
      val board = RegexFenParser
        .parseFEN("rnbqkbnr/pppp1ppp/8/4p3/2B1P3/8/PPPP1PPP/RNBQK1NR")
        .get
      // First Qh5
      val afterQh5 = board.move(Square("d1"), Square("h5")).get
      // Black plays Nc6
      val afterNc6 = afterQh5.move(Square("b8"), Square("c6")).get
      // Qxf7#
      val result = afterNc6.move(Square("h5"), Square("f7"))
      result.isSuccess shouldBe true
      result.event shouldBe Some(GameEvent.Checkmate)
    }

    "detect stalemate" in {
      // Setup: Black king on h8, White king on f6, White queen somewhere to deliver stalemate
      // After Qg7 would be checkmate, but Qg6 blocks all king moves without giving check
      // Use: Black king a1, White king a3, White queen on h8 -> Qa8 is check, but Qb2 is stalemate
      // Simpler: Black king h8, White king f7 is check...
      // Known stalemate pattern: Black king a8, White king a6, White pawn a7 = stalemate
      val board = RegexFenParser.parseFEN("k7/P7/K7/8/8/8/8/1Q6").get
      // This is already stalemate for black. Let's instead create a move that PRODUCES stalemate.
      // Setup: Black king on a8, White king on c7, White queen on b1
      // Qb6 would produce stalemate (a7 blocked by Kc7, b8 blocked by Kc7, b7 blocked by Qb6+Kc7)
      val setupBoard = RegexFenParser.parseFEN("k7/2K5/8/8/8/8/8/1Q6").get
      val result = setupBoard.move(Square("b1"), Square("b6"))
      result.isSuccess shouldBe true
      result.event shouldBe Some(GameEvent.Stalemate)
    }

    "report Moved for normal moves" in {
      val result = board.move(Square("e2"), Square("e4"))
      result.isSuccess shouldBe true
      result.event shouldBe Some(GameEvent.Moved)
    }
  }

  "MoveError" should {
    "provide NoPiece message" in {
      val result = board.move(Square("e4"), Square("e5"))
      result match {
        case MoveResult.Failed(_, MoveError.NoPiece) =>
          MoveError.NoPiece.message should include("No piece")
        case other => fail(s"Expected NoPiece, got: $other")
      }
    }

    "provide InvalidMove message" in {
      MoveError.InvalidMove.message should include("Invalid move")
    }

    "provide LeavesKingInCheck message" in {
      // Pinned rook: moving it exposes king
      val pinBoard = RegexFenParser.parseFEN("4r3/8/8/8/8/8/4R3/4K3").get
      val result = pinBoard.move(Square("e2"), Square("a2"))
      result match {
        case MoveResult.Failed(_, MoveError.LeavesKingInCheck) =>
          MoveError.LeavesKingInCheck.message should include("king in check")
        case other => fail(s"Expected LeavesKingInCheck, got: $other")
      }
    }

    "provide WrongColor message" in {
      MoveError.WrongColor.message should include("Not your piece")
    }

    "provide ParseError message" in {
      val err = MoveError.ParseError("bad input")
      err.message shouldBe "bad input"
    }
  }
}
