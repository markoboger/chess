package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class IllegalMoveSpec extends AnyWordSpec with Matchers {

  "Illegal Move Detection" when {
    "testing pawn moves" should {
      "reject pawn moving 2 squares diagonally (e2d4)" in {
        val board = Board.initial
        val result = board.move(Square("e2"), Square("d4"))
        result.isFailed shouldBe true
      }

      "reject pawn moving backwards" in {
        val board = Board.initial
        val result = board.move(Square("e4"), Square("e3"))
        result.isFailed shouldBe true
      }

      "reject pawn moving 3 squares" in {
        val board = Board.initial
        val result = board.move(Square("e2"), Square("e5"))
        result.isFailed shouldBe true
      }

      "allow pawn moving 2 squares from starting position" in {
        val board = Board.initial
        val result = board.move(Square("e2"), Square("e4"))
        result.isSuccess shouldBe true
        result.board.pieceAt(Square("e4")) shouldEqual Some(
          Piece(Role.Pawn, Color.White)
        )
        result.board.pieceAt(Square("e2")) shouldEqual None
      }

      "reject pawn moving 2 squares from non-starting position" in {
        val setup = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d5"))
        yield b2
        val board = setup.get
        val result = board.move(Square("e4"), Square("e6"))
        result.isFailed shouldBe true
      }

      "reject pawn moving to occupied square" in {
        val board = Board.initial
        val afterE3 = board.move(Square("e2"), Square("e3"))
        afterE3.isSuccess shouldBe true
        val result = afterE3.board.move(Square("e4"), Square("e3"))
        result.isFailed shouldBe true
      }

      "allow pawn capturing diagonally" in {
        val result = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d5"))
          b3 <- b2.move(Square("e4"), Square("d5"))
        yield b3
        result.isSuccess shouldBe true
        result.board.pieceAt(Square("d5")) shouldEqual Some(
          Piece(Role.Pawn, Color.White)
        )
      }

      "reject pawn capturing non-diagonally" in {
        val setup = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d5"))
          b3 <- b2.move(Square("e4"), Square("e5"))
        yield b3
        setup.isSuccess shouldBe true
        val result = setup.board.move(Square("e5"), Square("d6"))
        result.isFailed shouldBe true
      }
    }

    "testing knight moves" should {
      "reject knight moving in straight line" in {
        val board = Board.initial
        val result = board.move(Square("b1"), Square("b3"))
        result.isFailed shouldBe true
      }
    }

    "testing bishop moves" should {
      "reject bishop moving in straight line" in {
        val board = Board.initial
        val result = board.move(Square("c1"), Square("c3"))
        result.isFailed shouldBe true
      }
    }

    "testing rook moves" should {
      "reject rook moving diagonally" in {
        val board = Board.initial
        val result = board.move(Square("a1"), Square("c3"))
        result.isFailed shouldBe true
      }
    }

    "testing queen moves" should {
      "reject queen moving in L-shape" in {
        val board = Board.initial
        val result = board.move(Square("d1"), Square("f2"))
        result.isFailed shouldBe true
      }
    }

    "testing king moves" should {
      "reject king moving 2 squares" in {
        val board = Board.initial
        val result = board.move(Square("e1"), Square("e3"))
        result.isFailed shouldBe true
      }
    }

    "testing board boundaries" should {
      "reject invalid square coordinates" in {
        // With Square types, invalid coordinates are caught at construction time
        Square.fromCoords(5, 9) shouldEqual None
        Square.fromCoords(0, 4) shouldEqual None
        Square.fromCoords(9, 1) shouldEqual None
      }
    }

    "testing capture rules" should {
      "reject capturing own piece" in {
        val board = Board.initial
        val result = board.move(Square("e2"), Square("d1"))
        result.isFailed shouldBe true
      }
    }
  }
}
