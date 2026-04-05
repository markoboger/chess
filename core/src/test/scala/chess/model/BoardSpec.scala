package chess.model

import chess.controller.io.fen.RegexFenParser
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
      val result = board.move(from = Square("e2"), to = Square("e4"))
      result.isSuccess shouldBe true

      result.board.pieceAt(Square("e2")) shouldBe empty
      result.board.pieceAt(Square("e4")) should contain(
        Piece(Role.Pawn, Color.White)
      )
    }

    "allow capturing an opposing piece" in {
      val board = Board.initial

      // Chain moves using for-comprehension
      val result = for
        b1 <- board.move(Square("e2"), Square("e4"))
        b2 <- b1.move(Square("d7"), Square("d5"))
        b3 <- b2.move(Square("e4"), Square("d5"))
      yield b3

      result.isSuccess shouldBe true
      result.board.pieceAt(Square("e4")) shouldBe empty
      result.board.pieceAt(Square("d5")) should contain(
        Piece(Role.Pawn, Color.White)
      )
    }
  }

  "Board piece moves" should {
    "allow valid rook moves on open files" in {
      // FEN with rook on a1 and no obstructions on file a
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/R3K3").get
      val result = board.move(Square("a1"), Square("a8"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("a8")) should contain(
        Piece(Role.Rook, Color.White)
      )
      result.board.pieceAt(Square("a1")) shouldBe empty
    }

    "allow valid rook moves along ranks" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/R3K3").get
      val result = board.move(Square("a1"), Square("d1"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("d1")) should contain(
        Piece(Role.Rook, Color.White)
      )
    }

    "reject rook move to same square" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/R3K3").get
      val result = board.move(Square("a1"), Square("a1"))
      result.isFailed shouldBe true
    }

    "reject rook move when path is blocked" in {
      // Rook on a1 blocked by pawn on a2
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/P7/R3K3").get
      val result = board.move(Square("a1"), Square("a4"))
      result.isFailed shouldBe true
    }

    "allow valid queen diagonal moves" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/3QK3").get
      val result = board.move(Square("d1"), Square("h5"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("h5")) should contain(
        Piece(Role.Queen, Color.White)
      )
    }

    "allow valid queen straight moves" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/3QK3").get
      val result = board.move(Square("d1"), Square("d8"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("d8")) should contain(
        Piece(Role.Queen, Color.White)
      )
    }

    "reject queen L-shape move" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/3QK3").get
      val result = board.move(Square("d1"), Square("e3"))
      result.isFailed shouldBe true
    }

    "reject queen move to same square" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/3QK3").get
      val result = board.move(Square("d1"), Square("d1"))
      result.isFailed shouldBe true
    }

    "reject queen move when path is blocked" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/3P4/3QK3").get
      val result = board.move(Square("d1"), Square("d4"))
      result.isFailed shouldBe true
    }
  }

  "Board en passant edge cases" should {
    "not remove captured piece on regular diagonal capture from rank 5" in {
      // White pawn on e5 captures black pawn on d6 (regular capture, not en passant)
      // This triggers isEnPassantCapture from move execution (line 48) where pieceAt(to) is defined
      val board = RegexFenParser.parseFEN("8/8/3p4/4P3/8/8/8/4K3").get
      val result = board.move(Square("e5"), Square("d6"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("d6")) should contain(
        Piece(Role.Pawn, Color.White)
      )
      result.board.pieceAt(Square("e5")) shouldBe empty
    }

    "not trigger en passant on forward pawn move from rank 5" in {
      // Forward move from rank 5 - triggers isEnPassantCapture from line 48, hits fileDiff!=1
      val board = RegexFenParser.parseFEN("8/8/8/4P3/8/8/8/4K3").get
      val result = board.move(Square("e5"), Square("e6"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("e6")) should contain(
        Piece(Role.Pawn, Color.White)
      )
    }

    "reject en passant when last move distance is wrong from starting rank" in {
      // lastMove from rank 7 but only 1 square (not 2) - hits line 128
      val board = RegexFenParser.parseFEN("8/8/8/4P3/8/8/8/4K3").get
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("d7"), Square("d6"))))
      val result = boardWithLastMove.move(Square("e5"), Square("d6"))
      result.isFailed shouldBe true
    }

    "reject en passant when pawn is not on correct rank" in {
      // White pawn on e4 (not rank 5), trying diagonal move to empty d5
      val board = RegexFenParser.parseFEN("8/8/8/8/4P3/8/8/4K3").get
      val result = board.move(Square("e4"), Square("d5"))
      // Diagonal pawn move to empty square with no en passant possible - rejected
      result.isFailed shouldBe true
    }

    "reject en passant when last move was not a 2-square pawn advance" in {
      val board = RegexFenParser.parseFEN("8/8/8/3pP3/8/8/8/4K3").get
      // Last move was d6->d5 (1-square move, not 2-square)
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("d6"), Square("d5"))))
      val result = boardWithLastMove.move(Square("e5"), Square("d6"))
      result.isFailed shouldBe true
    }

    "reject en passant when last move target is on wrong rank" in {
      val board = RegexFenParser.parseFEN("8/8/8/4P3/3p4/8/8/4K3").get
      // Last move was d6->d4, but d4 is not on same rank as e5
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("d6"), Square("d4"))))
      val result = boardWithLastMove.move(Square("e5"), Square("d6"))
      result.isFailed shouldBe true
    }

    "reject en passant when last move file doesn't match target" in {
      val board = RegexFenParser.parseFEN("8/8/8/2ppP3/8/8/8/4K3").get
      // Last move was c7->c5, but we try to capture on d6
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("c7"), Square("c5"))))
      val result = boardWithLastMove.move(Square("e5"), Square("d6"))
      result.isFailed shouldBe true
    }

    "reject en passant when there is no last move" in {
      val board = RegexFenParser.parseFEN("8/8/8/3pP3/8/8/8/4K3").get
      // No lastMove set
      val result = board.move(Square("e5"), Square("d6"))
      result.isFailed shouldBe true
    }

    "reject en passant when captured piece is not a pawn" in {
      val board = RegexFenParser.parseFEN("8/8/8/3nP3/8/8/8/4K3").get
      // Fake lastMove suggesting a 2-square move (but it's a knight, not a pawn)
      val boardWithLastMove =
        board.copy(lastMove = Some((Square("d7"), Square("d5"))))
      val result = boardWithLastMove.move(Square("e5"), Square("d6"))
      result.isFailed shouldBe true
    }
  }

  "Board.applyMoveUnchecked" should {
    "return same board when source square is empty" in {
      val board = RegexFenParser.parseFEN("8/8/8/8/8/8/8/4K3").get
      val result = board.applyMoveUnchecked(Square("a1"), Square("a2"))
      result should be theSameInstanceAs board
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
      val board = Board.initial.move(Square("e2"), Square("e4")).get
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

  "Castling" should {
    // Minimal boards: king on e1/e8, rook on h1 or a1
    def kingsideBoard: Board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/4K2R").get
    def queensideBoard: Board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/R3K3").get

    "execute white kingside castling — king to g1, rook to f1" in {
      val result = kingsideBoard.move(Square("e1"), Square("g1"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("g1")) shouldEqual Some(Piece(Role.King, Color.White))
      result.board.pieceAt(Square("f1")) shouldEqual Some(Piece(Role.Rook, Color.White))
      result.board.pieceAt(Square("e1")) shouldBe None
      result.board.pieceAt(Square("h1")) shouldBe None
    }

    "execute white queenside castling — king to c1, rook to d1" in {
      val result = queensideBoard.move(Square("e1"), Square("c1"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("c1")) shouldEqual Some(Piece(Role.King, Color.White))
      result.board.pieceAt(Square("d1")) shouldEqual Some(Piece(Role.Rook, Color.White))
      result.board.pieceAt(Square("e1")) shouldBe None
      result.board.pieceAt(Square("a1")) shouldBe None
    }

    "execute black kingside castling — king to g8, rook to f8" in {
      val board = RegexFenParser.parseFEN("4k2r/8/8/8/8/8/8/4K3").get
      val result = board.move(Square("e8"), Square("g8"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("g8")) shouldEqual Some(Piece(Role.King, Color.Black))
      result.board.pieceAt(Square("f8")) shouldEqual Some(Piece(Role.Rook, Color.Black))
    }

    "execute black queenside castling — king to c8, rook to d8" in {
      val board = RegexFenParser.parseFEN("r3k3/8/8/8/8/8/8/4K3").get
      val result = board.move(Square("e8"), Square("c8"))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("c8")) shouldEqual Some(Piece(Role.King, Color.Black))
      result.board.pieceAt(Square("d8")) shouldEqual Some(Piece(Role.Rook, Color.Black))
    }

    "revoke both castling rights after king moves" in {
      val b1 = kingsideBoard.move(Square("e1"), Square("f1")).get
      b1.castlingRights.whiteKingside shouldBe false
      b1.castlingRights.whiteQueenside shouldBe false
    }

    "revoke only kingside right after h-rook moves" in {
      val b1 = kingsideBoard.move(Square("h1"), Square("h2")).get
      b1.castlingRights.whiteKingside shouldBe false
      b1.castlingRights.whiteQueenside shouldBe true
    }

    "revoke only queenside right after a-rook moves" in {
      val b1 = queensideBoard.move(Square("a1"), Square("a2")).get
      b1.castlingRights.whiteKingside shouldBe true
      b1.castlingRights.whiteQueenside shouldBe false
    }

    "reject castling after king has moved" in {
      val b1 = kingsideBoard.move(Square("e1"), Square("f1")).get
      val b2 = b1.move(Square("f1"), Square("e1")).get
      b2.move(Square("e1"), Square("g1")).isFailed shouldBe true
    }

    "reject castling when rook is absent" in {
      val board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/4K3").get
      board.move(Square("e1"), Square("g1")).isFailed shouldBe true
    }

    "reject castling when path is blocked" in {
      val board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/4KB1R").get
      board.move(Square("e1"), Square("g1")).isFailed shouldBe true
    }

    "reject castling when king is in check" in {
      val board = RegexFenParser.parseFEN("4r3/8/8/8/8/8/8/4K2R").get
      board.move(Square("e1"), Square("g1")).isFailed shouldBe true
    }

    "reject castling when king passes through check" in {
      val board = RegexFenParser.parseFEN("5r2/8/8/8/8/8/8/4K2R").get
      board.move(Square("e1"), Square("g1")).isFailed shouldBe true
    }
  }
