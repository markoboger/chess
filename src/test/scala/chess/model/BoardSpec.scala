package chess.model

import chess.controller.parser.FENParser
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class BoardSpec extends AnyWordSpec with Matchers:
  "An initial Board" should {
    "place kings in the center files" in {
      val board = Board.initial

      board.pieceAt(Square("e1")) should contain(
        Piece(Role.King, Color.White)
      )
      board.pieceAt(Square("e8")) should contain(
        Piece(Role.King, Color.Black)
      )
    }

    "position pawns on the second ranks" in {
      val board = Board.initial

      board.pieceAt(Square("a2")) should contain(
        Piece(Role.Pawn, Color.White)
      )
      board.pieceAt(Square("h7")) should contain(
        Piece(Role.Pawn, Color.Black)
      )
    }

    "leave the middle of the board empty" in {
      val board = Board.initial

      board.pieceAt(Square("d4")) shouldBe empty
      board.pieceAt(Square("e5")) shouldBe empty
    }

    "reject invalid square notation" in {
      Square.fromCoords(0, 1) shouldBe None
      Square.fromCoords(9, 1) shouldBe None
      Square.fromCoords(1, 9) shouldBe None
    }

    "allow moving a piece to an empty square" in {
      val board = Board.initial

      // Move white pawn from e2 to e4
      val moved = board.move(from = Square("e2"), to = Square("e4"))

      moved.pieceAt(Square("e2")) shouldBe empty
      moved.pieceAt(Square("e4")) should contain(
        Piece(Role.Pawn, Color.White)
      )
    }

    "allow capturing an opposing piece" in {
      val board = Board.initial

      // First move white pawn from e2 to e4
      val afterWhite = board.move(Square("e2"), Square("e4"))
      // Then move black pawn from d7 to d5
      val afterBlack = afterWhite.move(Square("d7"), Square("d5"))
      // Now white pawn can capture black pawn diagonally: e4 to d5
      val afterCapture = afterBlack.move(Square("e4"), Square("d5"))

      afterCapture.pieceAt(Square("e4")) shouldBe empty
      afterCapture.pieceAt(Square("d5")) should contain(
        Piece(Role.Pawn, Color.White)
      )
    }
  }

  "Board piece moves" should {
    "allow valid rook moves on open files" in {
      // FEN with rook on a1 and no obstructions on file a
      val board = FENParser.parseFEN("8/8/8/8/8/8/8/R3K3").get
      val moved = board.move(Square("a1"), Square("a8"))
      moved.pieceAt(Square("a8")) should contain(
        Piece(Role.Rook, Color.White)
      )
      moved.pieceAt(Square("a1")) shouldBe empty
    }

    "allow valid rook moves along ranks" in {
      val board = FENParser.parseFEN("8/8/8/8/8/8/8/R3K3").get
      val moved = board.move(Square("a1"), Square("d1"))
      moved.pieceAt(Square("d1")) should contain(
        Piece(Role.Rook, Color.White)
      )
    }

    "reject rook move to same square" in {
      val board = FENParser.parseFEN("8/8/8/8/8/8/8/R3K3").get
      val moved = board.move(Square("a1"), Square("a1"))
      moved shouldEqual board
    }

    "reject rook move when path is blocked" in {
      // Rook on a1 blocked by pawn on a2
      val board = FENParser.parseFEN("8/8/8/8/8/8/P7/R3K3").get
      val moved = board.move(Square("a1"), Square("a4"))
      moved shouldEqual board
    }

    "allow valid queen diagonal moves" in {
      val board = FENParser.parseFEN("8/8/8/8/8/8/8/3QK3").get
      val moved = board.move(Square("d1"), Square("h5"))
      moved.pieceAt(Square("h5")) should contain(
        Piece(Role.Queen, Color.White)
      )
    }

    "allow valid queen straight moves" in {
      val board = FENParser.parseFEN("8/8/8/8/8/8/8/3QK3").get
      val moved = board.move(Square("d1"), Square("d8"))
      moved.pieceAt(Square("d8")) should contain(
        Piece(Role.Queen, Color.White)
      )
    }

    "reject queen L-shape move" in {
      val board = FENParser.parseFEN("8/8/8/8/8/8/8/3QK3").get
      val moved = board.move(Square("d1"), Square("e3"))
      moved shouldEqual board
    }

    "reject queen move to same square" in {
      val board = FENParser.parseFEN("8/8/8/8/8/8/8/3QK3").get
      val moved = board.move(Square("d1"), Square("d1"))
      moved shouldEqual board
    }

    "reject queen move when path is blocked" in {
      val board = FENParser.parseFEN("8/8/8/8/8/8/3P4/3QK3").get
      val moved = board.move(Square("d1"), Square("d4"))
      moved shouldEqual board
    }
  }

  "Board en passant edge cases" should {
    "not remove captured piece on regular diagonal capture from rank 5" in {
      // White pawn on e5 captures black pawn on d6 (regular capture, not en passant)
      // This triggers isEnPassantCapture from move execution (line 48) where pieceAt(to) is defined
      val board = FENParser.parseFEN("8/8/3p4/4P3/8/8/8/4K3").get
      val moved = board.move(Square("e5"), Square("d6"))
      moved.pieceAt(Square("d6")) should contain(
        Piece(Role.Pawn, Color.White)
      )
      moved.pieceAt(Square("e5")) shouldBe empty
    }

    "not trigger en passant on forward pawn move from rank 5" in {
      // Forward move from rank 5 - triggers isEnPassantCapture from line 48, hits fileDiff!=1
      val board = FENParser.parseFEN("8/8/8/4P3/8/8/8/4K3").get
      val moved = board.move(Square("e5"), Square("e6"))
      moved.pieceAt(Square("e6")) should contain(
        Piece(Role.Pawn, Color.White)
      )
    }

    "reject en passant when last move distance is wrong from starting rank" in {
      // lastMove from rank 7 but only 1 square (not 2) - hits line 128
      val board = FENParser.parseFEN("8/8/8/4P3/8/8/8/4K3").get
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("d7"), Square("d6"))))
      val moved = boardWithLastMove.move(Square("e5"), Square("d6"))
      moved shouldEqual boardWithLastMove
    }

    "reject en passant when pawn is not on correct rank" in {
      // White pawn on e4 (not rank 5), trying diagonal move to empty d5
      val board = FENParser.parseFEN("8/8/8/8/4P3/8/8/4K3").get
      val moved = board.move(Square("e4"), Square("d5"))
      // Diagonal pawn move to empty square with no en passant possible - rejected
      moved shouldEqual board
    }

    "reject en passant when last move was not a 2-square pawn advance" in {
      val board = FENParser.parseFEN("8/8/8/3pP3/8/8/8/4K3").get
      // Last move was d6->d5 (1-square move, not 2-square)
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("d6"), Square("d5"))))
      val moved = boardWithLastMove.move(Square("e5"), Square("d6"))
      moved shouldEqual boardWithLastMove
    }

    "reject en passant when last move target is on wrong rank" in {
      val board = FENParser.parseFEN("8/8/8/4P3/3p4/8/8/4K3").get
      // Last move was d6->d4, but d4 is not on same rank as e5
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("d6"), Square("d4"))))
      val moved = boardWithLastMove.move(Square("e5"), Square("d6"))
      moved shouldEqual boardWithLastMove
    }

    "reject en passant when last move file doesn't match target" in {
      val board = FENParser.parseFEN("8/8/8/2ppP3/8/8/8/4K3").get
      // Last move was c7->c5, but we try to capture on d6
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("c7"), Square("c5"))))
      val moved = boardWithLastMove.move(Square("e5"), Square("d6"))
      moved shouldEqual boardWithLastMove
    }

    "reject en passant when there is no last move" in {
      val board = FENParser.parseFEN("8/8/8/3pP3/8/8/8/4K3").get
      // No lastMove set
      val moved = board.move(Square("e5"), Square("d6"))
      moved shouldEqual board
    }

    "reject en passant when captured piece is not a pawn" in {
      val board = FENParser.parseFEN("8/8/8/3nP3/8/8/8/4K3").get
      // Fake lastMove suggesting a 2-square move (but it's a knight, not a pawn)
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("d7"), Square("d5"))))
      val moved = boardWithLastMove.move(Square("e5"), Square("d6"))
      moved shouldEqual boardWithLastMove
    }
  }

  "Board.toString" should {
    "display the initial board in a readable format" in {
      val board = Board.initial
      val boardStr = board.toString

      boardStr should include("♜") // Black rook
      boardStr should include("♟") // Black pawn
      boardStr should include("♙") // White pawn
      boardStr should include("♖") // White rook
      boardStr should include("·") // Empty square
      boardStr should include("a b c d e f g h")
      boardStr should include("8")
      boardStr should include("1")
    }

    "display moved pieces correctly" in {
      val board = Board.initial.move(Square("e2"), Square("e4"))
      val boardStr = board.toString

      boardStr should include("♙")
      boardStr should not include ("e2") // Pawn no longer on e2
    }

    "have multiple lines for each rank" in {
      val board = Board.initial
      val boardStr = board.toString
      val lines = boardStr.split("\n")

      lines.length shouldBe 10 // 8 ranks + 2 file labels
    }
  }
