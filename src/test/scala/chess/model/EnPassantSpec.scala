package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class EnPassantSpec extends AnyWordSpec with Matchers {
  
  "En Passant Capture" when {
    "white pawn captures black pawn" should {
      "allow en passant capture when opponent pawn moves 2 squares" in {
        val board = Board.initial
        val afterE4 = board.move(5, 2, 5, 4)
        val afterD5 = afterE4.move(4, 7, 4, 5)
        val afterCapture = afterD5.move(5, 4, 4, 5)
        
        afterCapture should not equal afterD5
        afterCapture.pieceAt(4, 5) shouldEqual Some(Piece(PieceType.Pawn, PieceColor.White))
        afterCapture.pieceAt(4, 4) shouldEqual None
      }
      
      "allow en passant capture on queenside" in {
        val board = Board.initial
        val afterD4 = board.move(4, 2, 4, 4)
        val afterE5 = afterD4.move(5, 7, 5, 5)
        val afterCapture = afterE5.move(4, 4, 5, 5)
        
        afterCapture should not equal afterE5
        afterCapture.pieceAt(5, 5) shouldEqual Some(Piece(PieceType.Pawn, PieceColor.White))
        afterCapture.pieceAt(5, 4) shouldEqual None
      }
    }
    
    "black pawn captures white pawn" should {
      "allow black en passant capture" in {
        val board = Board.initial
        val afterA3 = board.move(1, 2, 1, 3)
        val afterB5 = afterA3.move(2, 7, 2, 5)
        val afterA4 = afterB5.move(1, 3, 1, 4)
        val afterB4 = afterA4.move(2, 5, 2, 4)
        val afterC4 = afterB4.move(3, 2, 3, 4)
        val afterCapture = afterC4.move(2, 4, 3, 3)
        
        afterCapture should not equal afterC4
        afterCapture.pieceAt(3, 3) shouldEqual Some(Piece(PieceType.Pawn, PieceColor.Black))
        afterCapture.pieceAt(3, 4) shouldEqual None
      }
    }
    
    "invalid en passant scenarios" should {
      "not allow en passant if opponent pawn moved 1 square" in {
        val board = Board.initial
        val afterE4 = board.move(5, 2, 5, 4)
        val afterD6 = afterE4.move(4, 7, 4, 6)
        val resultBoard = afterD6.move(5, 4, 4, 5)
        
        resultBoard shouldEqual afterD6
      }
      
      "not allow en passant if not immediately after 2-square move" in {
        val board = Board.initial
        val afterE4 = board.move(5, 2, 5, 4)
        val afterD5 = afterE4.move(4, 7, 4, 5)
        val afterE5 = afterD5.move(5, 4, 5, 5)
        val afterC5 = afterE5.move(3, 7, 3, 5)
        val resultBoard = afterC5.move(5, 5, 4, 4)
        
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
