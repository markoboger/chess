package chess.io.json.upickle

import chess.io.FileIO
import chess.io.json.circe.CirceJsonFileIO
import chess.model.{Board, Piece, Color, Role, Square, File, Rank}
import chess.io.fen.RegexFenParser
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class UPickleJsonFileIOSpec extends AnyWordSpec with Matchers {

  val fileIO: FileIO = new UPickleJsonFileIO

  "UPickleJsonFileIO" should {

    "round-trip the initial board" in {
      val board = Board.initial
      val json = fileIO.save(board)
      val loaded = fileIO.load(json)

      loaded.isSuccess shouldBe true
      loaded.get shouldBe board
    }

    "round-trip an empty board" in {
      val board = Board(Vector.fill(8, 8)(None))
      val json = fileIO.save(board)
      val loaded = fileIO.load(json)

      loaded.isSuccess shouldBe true
      loaded.get shouldBe board
    }

    "round-trip a board with lastMove set" in {
      val board = Board.initial
      val moved = board.move(Square("e2"), Square("e4")).get
      val json = fileIO.save(moved)
      val loaded = fileIO.load(json)

      loaded.isSuccess shouldBe true
      loaded.get shouldBe moved
      loaded.get.lastMove shouldBe Some((Square("e2"), Square("e4")))
    }

    "round-trip a board without lastMove" in {
      val board = Board.initial
      board.lastMove shouldBe None

      val json = fileIO.save(board)
      val loaded = fileIO.load(json)

      loaded.get.lastMove shouldBe None
    }

    "round-trip a FEN-loaded mid-game position" in {
      val board =
        RegexFenParser
          .parseFEN("r1bqkbnr/pppppppp/2n5/8/4P3/8/PPPP1PPP/RNBQKBNR")
          .get
      val json = fileIO.save(board)
      val loaded = fileIO.load(json)

      loaded.isSuccess shouldBe true
      loaded.get shouldBe board
    }

    "preserve all piece types and colors" in {
      val board = Board.initial
      val json = fileIO.save(board)
      val loaded = fileIO.load(json).get

      // Check white pieces
      loaded.pieceAt(Square("a1")) shouldBe Some(Piece(Role.Rook, Color.White))
      loaded.pieceAt(Square("b1")) shouldBe Some(
        Piece(Role.Knight, Color.White)
      )
      loaded.pieceAt(Square("c1")) shouldBe Some(
        Piece(Role.Bishop, Color.White)
      )
      loaded.pieceAt(Square("d1")) shouldBe Some(Piece(Role.Queen, Color.White))
      loaded.pieceAt(Square("e1")) shouldBe Some(Piece(Role.King, Color.White))
      loaded.pieceAt(Square("e2")) shouldBe Some(Piece(Role.Pawn, Color.White))

      // Check black pieces
      loaded.pieceAt(Square("a8")) shouldBe Some(Piece(Role.Rook, Color.Black))
      loaded.pieceAt(Square("b8")) shouldBe Some(
        Piece(Role.Knight, Color.Black)
      )
      loaded.pieceAt(Square("c8")) shouldBe Some(
        Piece(Role.Bishop, Color.Black)
      )
      loaded.pieceAt(Square("d8")) shouldBe Some(Piece(Role.Queen, Color.Black))
      loaded.pieceAt(Square("e8")) shouldBe Some(Piece(Role.King, Color.Black))
      loaded.pieceAt(Square("e7")) shouldBe Some(Piece(Role.Pawn, Color.Black))

      // Check empty squares
      loaded.pieceAt(Square("e4")) shouldBe None
    }

    "produce valid JSON containing expected keys" in {
      val json = fileIO.save(Board.initial)
      json should include("\"squares\"")
      json should include("\"lastMove\"")
      json should include("\"role\"")
      json should include("\"color\"")
      json should include("\"King\"")
      json should include("\"Pawn\"")
      json should include("\"White\"")
      json should include("\"Black\"")
    }

    "fail on invalid JSON input" in {
      val result = fileIO.load("not json at all")
      result.isFailure shouldBe true
    }

    "fail on empty input" in {
      val result = fileIO.load("")
      result.isFailure shouldBe true
    }

    "fail on valid JSON but wrong structure" in {
      val result = fileIO.load("""{"foo": "bar"}""")
      result.isFailure shouldBe true
    }

    "fail on JSON with invalid color" in {
      val json = fileIO.save(Board.initial).replace("\"White\"", "\"Purple\"")
      val result = fileIO.load(json)
      result.isFailure shouldBe true
    }

    "fail on JSON with invalid role" in {
      val json = fileIO.save(Board.initial).replace("\"King\"", "\"Dragon\"")
      val result = fileIO.load(json)
      result.isFailure shouldBe true
    }

    "fail on JSON with invalid square in lastMove" in {
      val moved = Board.initial.move(Square("e2"), Square("e4")).get
      val json = fileIO.save(moved).replace("\"e2\"", "\"z9\"")
      val result = fileIO.load(json)
      result.isFailure shouldBe true
    }

    "round-trip a board after several moves" in {
      val result = for
        b1 <- Board.initial.move(Square("e2"), Square("e4"))
        b2 <- b1.move(Square("e7"), Square("e5"))
        b3 <- b2.move(Square("g1"), Square("f3"))
      yield b3

      result.isSuccess shouldBe true
      val board = result.get
      val json = fileIO.save(board)
      val loaded = fileIO.load(json)

      loaded.isSuccess shouldBe true
      loaded.get shouldBe board
    }

    "be usable through the FileIO interface" in {
      def roundTrip(io: FileIO, board: Board): Board =
        io.load(io.save(board)).get

      val board = Board.initial
      roundTrip(fileIO, board) shouldBe board
    }

    "produce JSON compatible with Circe implementation" in {
      val circeIO: FileIO = new CirceJsonFileIO
      val board = Board.initial.move(Square("e2"), Square("e4")).get

      // uPickle JSON → Circe load
      val upickleJson = fileIO.save(board)
      circeIO.load(upickleJson).get shouldBe board

      // Circe JSON → uPickle load
      val circeJson = circeIO.save(board)
      fileIO.load(circeJson).get shouldBe board
    }
  }
}
