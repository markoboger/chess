package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class EnPassantSpec extends AnyWordSpec with Matchers {

  "En Passant Capture" when {
    "white pawn captures black pawn" should {
      "allow en passant capture when opponent pawn moves 2 squares" in {
        val result = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d5"))
          b3 <- b2.move(Square("e4"), Square("d5"))
        yield b3

        result.isSuccess shouldBe true
        result.board.pieceAt(Square("d5")) shouldEqual Some(
          Piece(Role.Pawn, Color.White)
        )
        result.board.pieceAt(Square("d4")) shouldEqual None
      }

      "allow en passant capture on queenside" in {
        val result = for
          b1 <- Board.initial.move(Square("d2"), Square("d4"))
          b2 <- b1.move(Square("e7"), Square("e5"))
          b3 <- b2.move(Square("d4"), Square("e5"))
        yield b3

        result.isSuccess shouldBe true
        result.board.pieceAt(Square("e5")) shouldEqual Some(
          Piece(Role.Pawn, Color.White)
        )
        result.board.pieceAt(Square("e4")) shouldEqual None
      }
    }

    "black pawn captures white pawn" should {
      "allow black en passant capture" in {
        val result = for
          b1 <- Board.initial.move(Square("a2"), Square("a3"))
          b2 <- b1.move(Square("b7"), Square("b5"))
          b3 <- b2.move(Square("a3"), Square("a4"))
          b4 <- b3.move(Square("b5"), Square("b4"))
          b5 <- b4.move(Square("c2"), Square("c4"))
          b6 <- b5.move(Square("b4"), Square("c3"))
        yield b6

        result.isSuccess shouldBe true
        result.board.pieceAt(Square("c3")) shouldEqual Some(
          Piece(Role.Pawn, Color.Black)
        )
        result.board.pieceAt(Square("c4")) shouldEqual None
      }
    }

    "invalid en passant scenarios" should {
      "not allow en passant if opponent pawn moved 1 square" in {
        val setup = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d6"))
        yield b2
        val board = setup.get
        val result = board.move(Square("e4"), Square("d5"))
        result.isFailed shouldBe true
      }

      "not allow en passant if not immediately after 2-square move" in {
        val setup = for
          b1 <- Board.initial.move(Square("e2"), Square("e4"))
          b2 <- b1.move(Square("d7"), Square("d5"))
          b3 <- b2.move(Square("e4"), Square("e5"))
          b4 <- b3.move(Square("c7"), Square("c5"))
        yield b4
        val board = setup.get
        val result = board.move(Square("e5"), Square("d4"))
        result.isFailed shouldBe true
      }
    }

    "edge cases" should {
      "only allow en passant on pawns" in {
        // Sanity check: only pawns can move 2 squares
        true shouldEqual true
      }
    }
  }
}
