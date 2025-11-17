package chess.controller.parser

import chess.model.{Board, Piece, PieceType, PieceColor}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.{Success, Failure}

final class FENParserSpec extends AnyWordSpec with Matchers:

  "FENParser" should {
    "parse the initial position" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Success[?]])
      val board = result.get
      
      // Check white pieces
      board.pieceAt(1, 1) should contain(Piece(PieceType.Rook, PieceColor.White))
      board.pieceAt(2, 1) should contain(Piece(PieceType.Knight, PieceColor.White))
      board.pieceAt(5, 1) should contain(Piece(PieceType.King, PieceColor.White))
      
      // Check black pieces
      board.pieceAt(1, 8) should contain(Piece(PieceType.Rook, PieceColor.Black))
      board.pieceAt(2, 8) should contain(Piece(PieceType.Knight, PieceColor.Black))
      board.pieceAt(5, 8) should contain(Piece(PieceType.King, PieceColor.Black))
      
      // Check pawns
      board.pieceAt(1, 2) should contain(Piece(PieceType.Pawn, PieceColor.White))
      board.pieceAt(1, 7) should contain(Piece(PieceType.Pawn, PieceColor.Black))
    }

    "parse a position with empty squares" in {
      val fen = "8/8/8/8/8/8/8/8"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Success[?]])
      val board = result.get
      
      // All squares should be empty
      for {
        rank <- 1 to 8
        file <- 1 to 8
      } {
        board.pieceAt(file, rank) shouldBe empty
      }
    }

    "parse a position after e4" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Success[?]])
      val board = result.get
      
      board.pieceAt(5, 4) should contain(Piece(PieceType.Pawn, PieceColor.White))
      board.pieceAt(5, 2) shouldBe empty
    }

    "parse a position after e4 e5" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Success[?]])
      val board = result.get
      
      board.pieceAt(5, 4) should contain(Piece(PieceType.Pawn, PieceColor.White))
      board.pieceAt(5, 5) should contain(Piece(PieceType.Pawn, PieceColor.Black))
    }

    "parse a complex middlegame position" in {
      val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Success[?]])
      val board = result.get
      
      // Check some key pieces
      board.pieceAt(3, 4) should contain(Piece(PieceType.Bishop, PieceColor.White))
      board.pieceAt(6, 6) should contain(Piece(PieceType.Knight, PieceColor.Black))
      board.pieceAt(3, 6) should contain(Piece(PieceType.Knight, PieceColor.Black))
    }

    "parse all piece types" in {
      val fen = "rnbqkbnr/8/8/8/8/8/8/RNBQKBNR"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Success[?]])
      val board = result.get
      
      // White pieces
      board.pieceAt(1, 1) should contain(Piece(PieceType.Rook, PieceColor.White))
      board.pieceAt(2, 1) should contain(Piece(PieceType.Knight, PieceColor.White))
      board.pieceAt(3, 1) should contain(Piece(PieceType.Bishop, PieceColor.White))
      board.pieceAt(4, 1) should contain(Piece(PieceType.Queen, PieceColor.White))
      board.pieceAt(5, 1) should contain(Piece(PieceType.King, PieceColor.White))
      
      // Black pieces
      board.pieceAt(1, 8) should contain(Piece(PieceType.Rook, PieceColor.Black))
      board.pieceAt(2, 8) should contain(Piece(PieceType.Knight, PieceColor.Black))
      board.pieceAt(3, 8) should contain(Piece(PieceType.Bishop, PieceColor.Black))
      board.pieceAt(4, 8) should contain(Piece(PieceType.Queen, PieceColor.Black))
      board.pieceAt(5, 8) should contain(Piece(PieceType.King, PieceColor.Black))
    }

    "fail on invalid FEN with wrong rank count" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Failure[?]])
    }

    "fail on invalid FEN with wrong square count in rank" in {
      val fen = "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Failure[?]])
    }

    "fail on invalid piece character" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNX"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Failure[?]])
    }

    "convert board to FEN notation" in {
      val board = Board.initial
      val fen = FENParser.boardToFEN(board)
      
      fen should be("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
    }

    "round-trip FEN: parse and convert back" in {
      val originalFEN = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FENParser.parseFEN(originalFEN).get
      val resultFEN = FENParser.boardToFEN(board)
      
      resultFEN should be(originalFEN)
    }

    "handle FEN with multiple consecutive empty squares" in {
      val fen = "8/8/8/3Q4/8/8/8/8"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Success[?]])
      val board = result.get
      
      board.pieceAt(4, 5) should contain(Piece(PieceType.Queen, PieceColor.White))
      board.pieceAt(1, 5) shouldBe empty
      board.pieceAt(8, 5) shouldBe empty
    }

    "parse FEN with extra whitespace and components" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val result = FENParser.parseFEN(fen)
      
      result should be(a[Success[?]])
    }
  }
