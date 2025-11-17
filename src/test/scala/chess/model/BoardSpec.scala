package chess.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class BoardSpec extends AnyWordSpec with Matchers:
  "An initial Board" should {
    "place kings in the center files" in {
      val board = Board.initial

      board.pieceAt(5, 1) should contain(Piece(PieceType.King, PieceColor.White))
      board.pieceAt(5, 8) should contain(Piece(PieceType.King, PieceColor.Black))
    }

    "position pawns on the second ranks" in {
      val board = Board.initial

      board.pieceAt(1, 2) should contain(Piece(PieceType.Pawn, PieceColor.White))
      board.pieceAt(8, 7) should contain(Piece(PieceType.Pawn, PieceColor.Black))
    }

    "leave the middle of the board empty" in {
      val board = Board.initial

      board.pieceAt(4, 4) shouldBe empty
      board.pieceAt(5, 5) shouldBe empty
    }

    "return None for coordinates off the board" in {
      val board = Board.initial

      board.pieceAt(0, 1) shouldBe empty
      board.pieceAt(9, 1) shouldBe empty
      board.pieceAt(1, 9) shouldBe empty
    }

    "allow moving a piece to an empty square" in {
      val board = Board.initial

      // Move white pawn from e2 to e4
      val moved = board.move(fromFile = 5, fromRank = 2, toFile = 5, toRank = 4)

      moved.pieceAt(5, 2) shouldBe empty
      moved.pieceAt(5, 4) should contain(Piece(PieceType.Pawn, PieceColor.White))
    }

    "allow capturing an opposing piece" in {
      val board = Board.initial

      // First move white pawn from e2 to e4
      val afterWhite = board.move(fromFile = 5, fromRank = 2, toFile = 5, toRank = 4)
      // Then move black pawn from d7 to d5
      val afterBlack = afterWhite.move(fromFile = 4, fromRank = 7, toFile = 4, toRank = 5)
      // Now white pawn can capture black pawn diagonally: e4 to d5
      val afterCapture = afterBlack.move(fromFile = 5, fromRank = 4, toFile = 4, toRank = 5)

      afterCapture.pieceAt(5, 4) shouldBe empty
      afterCapture.pieceAt(4, 5) should contain(Piece(PieceType.Pawn, PieceColor.White))
    }
  }

  "Board.toString" should {
    "display the initial board in a readable format" in {
      val board = Board.initial
      val boardStr = board.toString

      boardStr should include("♜")  // Black rook
      boardStr should include("♟")  // Black pawn
      boardStr should include("♙")  // White pawn
      boardStr should include("♖")  // White rook
      boardStr should include("·")  // Empty square
      boardStr should include("a b c d e f g h")
      boardStr should include("8")
      boardStr should include("1")
    }

    "display moved pieces correctly" in {
      val board = Board.initial.move(5, 2, 5, 4)
      val boardStr = board.toString

      boardStr should include("♙")
      boardStr should not include ("e2")  // Pawn no longer on e2
    }

    "have multiple lines for each rank" in {
      val board = Board.initial
      val boardStr = board.toString
      val lines = boardStr.split("\n")

      lines.length shouldBe 10  // 8 ranks + 2 file labels
    }
  }
