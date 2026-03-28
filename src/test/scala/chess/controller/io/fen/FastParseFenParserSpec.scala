package chess.controller.io.fen

import chess.controller.io.FenIO
import chess.model.{Board, Piece, Role, Color, Square, File, Rank}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.{Success, Failure}

final class FastParseFenParserSpec extends AnyWordSpec with Matchers:

  "FastParseFenParser" should {
    "parse the initial position" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Success[?]])
      val board = result.get

      board.pieceAt(Square("a1")) should contain(
        Piece(Role.Rook, Color.White)
      )
      board.pieceAt(Square("b1")) should contain(
        Piece(Role.Knight, Color.White)
      )
      board.pieceAt(Square("e1")) should contain(
        Piece(Role.King, Color.White)
      )

      board.pieceAt(Square("a8")) should contain(
        Piece(Role.Rook, Color.Black)
      )
      board.pieceAt(Square("b8")) should contain(
        Piece(Role.Knight, Color.Black)
      )
      board.pieceAt(Square("e8")) should contain(
        Piece(Role.King, Color.Black)
      )

      board.pieceAt(Square("a2")) should contain(
        Piece(Role.Pawn, Color.White)
      )
      board.pieceAt(Square("a7")) should contain(
        Piece(Role.Pawn, Color.Black)
      )
    }

    "parse a position with empty squares" in {
      val fen = "8/8/8/8/8/8/8/8"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Success[?]])
      val board = result.get

      for {
        square <- Square.all
      } {
        board.pieceAt(square) shouldBe empty
      }
    }

    "parse a position after e4" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Success[?]])
      val board = result.get

      board.pieceAt(Square("e4")) should contain(
        Piece(Role.Pawn, Color.White)
      )
      board.pieceAt(Square("e2")) shouldBe empty
    }

    "parse a position after e4 e5" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Success[?]])
      val board = result.get

      board.pieceAt(Square("e4")) should contain(
        Piece(Role.Pawn, Color.White)
      )
      board.pieceAt(Square("e5")) should contain(
        Piece(Role.Pawn, Color.Black)
      )
    }

    "parse a complex middlegame position" in {
      val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK02R"
      val result = FastParseFenParser.parseFEN(
        "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
      )

      result should be(a[Success[?]])
      val board = result.get

      board.pieceAt(Square("c4")) should contain(
        Piece(Role.Bishop, Color.White)
      )
      board.pieceAt(Square("f6")) should contain(
        Piece(Role.Knight, Color.Black)
      )
      board.pieceAt(Square("c6")) should contain(
        Piece(Role.Knight, Color.Black)
      )
    }

    "parse all piece types" in {
      val fen = "rnbqkbnr/8/8/8/8/8/8/RNBQKBNR"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Success[?]])
      val board = result.get

      board.pieceAt(Square("a1")) should contain(
        Piece(Role.Rook, Color.White)
      )
      board.pieceAt(Square("b1")) should contain(
        Piece(Role.Knight, Color.White)
      )
      board.pieceAt(Square("c1")) should contain(
        Piece(Role.Bishop, Color.White)
      )
      board.pieceAt(Square("d1")) should contain(
        Piece(Role.Queen, Color.White)
      )
      board.pieceAt(Square("e1")) should contain(
        Piece(Role.King, Color.White)
      )

      board.pieceAt(Square("a8")) should contain(
        Piece(Role.Rook, Color.Black)
      )
      board.pieceAt(Square("b8")) should contain(
        Piece(Role.Knight, Color.Black)
      )
      board.pieceAt(Square("c8")) should contain(
        Piece(Role.Bishop, Color.Black)
      )
      board.pieceAt(Square("d8")) should contain(
        Piece(Role.Queen, Color.Black)
      )
      board.pieceAt(Square("e8")) should contain(
        Piece(Role.King, Color.Black)
      )
    }

    "fail on invalid FEN with wrong rank count" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Failure[?]])
    }

    "fail on invalid FEN with wrong square count in rank" in {
      val fen = "rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Failure[?]])
    }

    "fail on invalid piece character" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNX"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Failure[?]])
    }

    "convert board to FEN notation" in {
      val board = Board.initial
      val fen = FastParseFenParser.boardToFEN(board)

      fen should be("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
    }

    "round-trip FEN: parse and convert back" in {
      val originalFEN = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
      val board = FastParseFenParser.parseFEN(originalFEN).get
      val resultFEN = FastParseFenParser.boardToFEN(board)

      resultFEN should be(originalFEN)
    }

    "handle FEN with multiple consecutive empty squares" in {
      val fen = "8/8/8/3Q4/8/8/8/8"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Success[?]])
      val board = result.get

      board.pieceAt(Square("d5")) should contain(
        Piece(Role.Queen, Color.White)
      )
      board.pieceAt(Square("a5")) shouldBe empty
      board.pieceAt(Square("h5")) shouldBe empty
    }

    "parse FEN with extra whitespace and components" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Success[?]])
    }

    "fail on invalid piece character in FEN" in {
      val fen = "xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
      val result = FastParseFenParser.parseFEN(fen)

      result should be(a[Failure[?]])
    }

    "fail on empty FEN string" in {
      val result = FastParseFenParser.parseFEN("")

      result should be(a[Failure[?]])
    }

    "produce the same Board as RegexFenParser for initial position" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
      val regex = RegexFenParser.parseFEN(fen).get
      val fast = FastParseFenParser.parseFEN(fen).get

      fast shouldBe regex
    }

    "produce the same Board as RegexFenParser for complex position" in {
      val fen = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK02R"
      val regex = RegexFenParser
        .parseFEN(
          "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
        )
        .get
      val fast = FastParseFenParser
        .parseFEN(
          "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
        )
        .get

      fast shouldBe regex
    }
  }

  "FastParseFenParser as FenIO" should {
    val fileIO: FenIO = FastParseFenParser

    "save a board as FEN" in {
      val fen = fileIO.save(Board.initial)
      fen should be("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
    }

    "load a board from FEN" in {
      val result = fileIO.load("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR")
      result should be(a[Success[?]])
      result.get.pieceAt(Square("e4")) should contain(
        Piece(Role.Pawn, Color.White)
      )
    }

    "round-trip through save and load" in {
      val original = Board.initial
      val serialized = fileIO.save(original)
      val deserialized = fileIO.load(serialized).get

      deserialized shouldBe original
    }

    "fail to load invalid FEN" in {
      val result = fileIO.load("invalid")
      result should be(a[Failure[?]])
    }

    "be usable polymorphically as FenIO" in {
      val io: FenIO = FastParseFenParser
      val fen = io.save(Board.initial)
      val board = io.load(fen).get

      board shouldBe Board.initial
    }

    "be usable polymorphically as FileIO" in {
      val io: chess.controller.io.FileIO = FastParseFenParser
      val fen = io.save(Board.initial)
      val board = io.load(fen).get

      board shouldBe Board.initial
    }
  }
