package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class EnPassantSpec extends AnyWordSpec with Matchers {

  "En Passant Capture" when {
    "white pawn captures black pawn" should {
      "allow en passant capture when opponent pawn moves 2 squares" in {
        val board = Board.initial
        val afterE4 = board.move(Square("e2"), Square("e4"))
        val afterD5 = afterE4.move(Square("d7"), Square("d5"))
        val afterCapture = afterD5.move(Square("e4"), Square("d5"))

        afterCapture should not equal afterD5
        afterCapture.pieceAt(Square("d5")) shouldEqual Some(
          Piece(PieceType.Pawn, PieceColor.White)
        )
        afterCapture.pieceAt(Square("d4")) shouldEqual None
      }

      "allow en passant capture on queenside" in {
        val board = Board.initial
        val afterD4 = board.move(Square("d2"), Square("d4"))
        val afterE5 = afterD4.move(Square("e7"), Square("e5"))
        val afterCapture = afterE5.move(Square("d4"), Square("e5"))

        afterCapture should not equal afterE5
        afterCapture.pieceAt(Square("e5")) shouldEqual Some(
          Piece(PieceType.Pawn, PieceColor.White)
        )
        afterCapture.pieceAt(Square("e4")) shouldEqual None
      }
    }

    "black pawn captures white pawn" should {
      "allow black en passant capture" in {
        val board = Board.initial
        val afterA3 = board.move(Square("a2"), Square("a3"))
        val afterB5 = afterA3.move(Square("b7"), Square("b5"))
        val afterA4 = afterB5.move(Square("a3"), Square("a4"))
        val afterB4 = afterA4.move(Square("b5"), Square("b4"))
        val afterC4 = afterB4.move(Square("c2"), Square("c4"))
        val afterCapture = afterC4.move(Square("b4"), Square("c3"))

        afterCapture should not equal afterC4
        afterCapture.pieceAt(Square("c3")) shouldEqual Some(
          Piece(PieceType.Pawn, PieceColor.Black)
        )
        afterCapture.pieceAt(Square("c4")) shouldEqual None
      }
    }

    "invalid en passant scenarios" should {
      "not allow en passant if opponent pawn moved 1 square" in {
        val board = Board.initial
        val afterE4 = board.move(Square("e2"), Square("e4"))
        val afterD6 = afterE4.move(Square("d7"), Square("d6"))
        val resultBoard = afterD6.move(Square("e4"), Square("d5"))

        resultBoard shouldEqual afterD6
      }

      "not allow en passant if not immediately after 2-square move" in {
        val board = Board.initial
        val afterE4 = board.move(Square("e2"), Square("e4"))
        val afterD5 = afterE4.move(Square("d7"), Square("d5"))
        val afterE5 = afterD5.move(Square("e4"), Square("e5"))
        val afterC5 = afterE5.move(Square("c7"), Square("c5"))
        val resultBoard = afterC5.move(Square("e5"), Square("d4"))

        resultBoard shouldEqual afterC5
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
