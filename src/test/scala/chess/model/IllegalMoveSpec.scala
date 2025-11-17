package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class IllegalMoveSpec extends AnyWordSpec with Matchers {
  
  "Illegal Move Detection" when {
    "testing pawn moves" should {
      "reject pawn moving 2 squares diagonally (e2d4)" in {
        val board = Board.initial
        val resultBoard = board.move(5, 2, 4, 4)
        resultBoard shouldEqual board
      }
      
      "reject pawn moving backwards" in {
        val board = Board.initial
        val resultBoard = board.move(5, 4, 5, 3)
        resultBoard shouldEqual board
      }
      
      "reject pawn moving 3 squares" in {
        val board = Board.initial
        val resultBoard = board.move(5, 2, 5, 5)
        resultBoard shouldEqual board
      }
      
      "allow pawn moving 2 squares from starting position" in {
        val board = Board.initial
        val resultBoard = board.move(5, 2, 5, 4)
        resultBoard should not equal board
        resultBoard.pieceAt(5, 4) shouldEqual Some(Piece(PieceType.Pawn, PieceColor.White))
        resultBoard.pieceAt(5, 2) shouldEqual None
      }
      
      "reject pawn moving 2 squares from non-starting position" in {
        val board = Board.initial
        val afterE4 = board.move(5, 2, 5, 4)
        val afterD5 = afterE4.move(4, 7, 4, 5)
        val resultBoard = afterD5.move(5, 4, 5, 6)
        resultBoard shouldEqual afterD5
      }
      
      "reject pawn moving to occupied square" in {
        val board = Board.initial
        val afterE3 = board.move(5, 2, 5, 3)
        afterE3 should not equal board
        val resultBoard = afterE3.move(5, 4, 5, 3)
        resultBoard shouldEqual afterE3
      }
      
      "allow pawn capturing diagonally" in {
        val board = Board.initial
        val afterE4 = board.move(5, 2, 5, 4)
        val afterD5 = afterE4.move(4, 7, 4, 5)
        val resultBoard = afterD5.move(5, 4, 4, 5)
        resultBoard should not equal afterD5
        resultBoard.pieceAt(4, 5) shouldEqual Some(Piece(PieceType.Pawn, PieceColor.White))
      }
      
      "reject pawn capturing non-diagonally" in {
        val board = Board.initial
        val afterE4 = board.move(5, 2, 5, 4)
        val afterD5 = afterE4.move(4, 7, 4, 5)
        val afterE5 = afterD5.move(5, 4, 5, 5)
        afterE5 should not equal afterD5
        val resultBoard = afterE5.move(5, 5, 4, 6)
        resultBoard shouldEqual afterE5
      }
    }
    
    "testing knight moves" should {
      "reject knight moving in straight line" in {
        val board = Board.initial
        val resultBoard = board.move(2, 1, 2, 3)
        resultBoard shouldEqual board
      }
    }
    
    "testing bishop moves" should {
      "reject bishop moving in straight line" in {
        val board = Board.initial
        val resultBoard = board.move(3, 1, 3, 3)
        resultBoard shouldEqual board
      }
    }
    
    "testing rook moves" should {
      "reject rook moving diagonally" in {
        val board = Board.initial
        val resultBoard = board.move(1, 1, 3, 3)
        resultBoard shouldEqual board
      }
    }
    
    "testing queen moves" should {
      "reject queen moving in L-shape" in {
        val board = Board.initial
        val resultBoard = board.move(4, 1, 6, 2)
        resultBoard shouldEqual board
      }
    }
    
    "testing king moves" should {
      "reject king moving 2 squares" in {
        val board = Board.initial
        val resultBoard = board.move(5, 1, 5, 3)
        resultBoard shouldEqual board
      }
    }
    
    "testing board boundaries" should {
      "reject moving piece off the board" in {
        val board = Board.initial
        val resultBoard = board.move(5, 2, 5, 9)
        resultBoard shouldEqual board
      }
      
      "reject moving to negative coordinates" in {
        val board = Board.initial
        val resultBoard = board.move(5, 2, 0, 4)
        resultBoard shouldEqual board
      }
    }
    
    "testing capture rules" should {
      "reject capturing own piece" in {
        val board = Board.initial
        val resultBoard = board.move(5, 2, 4, 1)
        resultBoard shouldEqual board
      }
    }
  }
}
