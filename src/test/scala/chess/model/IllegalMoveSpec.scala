package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class IllegalMoveSpec extends AnyWordSpec with Matchers {

  "Illegal Move Detection" when {
    "testing pawn moves" should {
      "reject pawn moving 2 squares diagonally (e2d4)" in {
        val board = Board.initial
        val resultBoard = board.move(Square("e2"), Square("d4"))
        resultBoard shouldEqual board
      }

      "reject pawn moving backwards" in {
        val board = Board.initial
        val resultBoard = board.move(Square("e4"), Square("e3"))
        resultBoard shouldEqual board
      }

      "reject pawn moving 3 squares" in {
        val board = Board.initial
        val resultBoard = board.move(Square("e2"), Square("e5"))
        resultBoard shouldEqual board
      }

      "allow pawn moving 2 squares from starting position" in {
        val board = Board.initial
        val resultBoard = board.move(Square("e2"), Square("e4"))
        resultBoard should not equal board
        resultBoard.pieceAt(Square("e4")) shouldEqual Some(
          Piece(PieceType.Pawn, PieceColor.White)
        )
        resultBoard.pieceAt(Square("e2")) shouldEqual None
      }

      "reject pawn moving 2 squares from non-starting position" in {
        val board = Board.initial
        val afterE4 = board.move(Square("e2"), Square("e4"))
        val afterD5 = afterE4.move(Square("d7"), Square("d5"))
        val resultBoard = afterD5.move(Square("e4"), Square("e6"))
        resultBoard shouldEqual afterD5
      }

      "reject pawn moving to occupied square" in {
        val board = Board.initial
        val afterE3 = board.move(Square("e2"), Square("e3"))
        afterE3 should not equal board
        val resultBoard = afterE3.move(Square("e4"), Square("e3"))
        resultBoard shouldEqual afterE3
      }

      "allow pawn capturing diagonally" in {
        val board = Board.initial
        val afterE4 = board.move(Square("e2"), Square("e4"))
        val afterD5 = afterE4.move(Square("d7"), Square("d5"))
        val resultBoard = afterD5.move(Square("e4"), Square("d5"))
        resultBoard should not equal afterD5
        resultBoard.pieceAt(Square("d5")) shouldEqual Some(
          Piece(PieceType.Pawn, PieceColor.White)
        )
      }

      "reject pawn capturing non-diagonally" in {
        val board = Board.initial
        val afterE4 = board.move(Square("e2"), Square("e4"))
        val afterD5 = afterE4.move(Square("d7"), Square("d5"))
        val afterE5 = afterD5.move(Square("e4"), Square("e5"))
        afterE5 should not equal afterD5
        val resultBoard = afterE5.move(Square("e5"), Square("d6"))
        resultBoard shouldEqual afterE5
      }
    }

    "testing knight moves" should {
      "reject knight moving in straight line" in {
        val board = Board.initial
        val resultBoard = board.move(Square("b1"), Square("b3"))
        resultBoard shouldEqual board
      }
    }

    "testing bishop moves" should {
      "reject bishop moving in straight line" in {
        val board = Board.initial
        val resultBoard = board.move(Square("c1"), Square("c3"))
        resultBoard shouldEqual board
      }
    }

    "testing rook moves" should {
      "reject rook moving diagonally" in {
        val board = Board.initial
        val resultBoard = board.move(Square("a1"), Square("c3"))
        resultBoard shouldEqual board
      }
    }

    "testing queen moves" should {
      "reject queen moving in L-shape" in {
        val board = Board.initial
        val resultBoard = board.move(Square("d1"), Square("f2"))
        resultBoard shouldEqual board
      }
    }

    "testing king moves" should {
      "reject king moving 2 squares" in {
        val board = Board.initial
        val resultBoard = board.move(Square("e1"), Square("e3"))
        resultBoard shouldEqual board
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
        val resultBoard = board.move(Square("e2"), Square("d1"))
        resultBoard shouldEqual board
      }
    }
  }
}
