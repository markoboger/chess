package chess.io.fen

import chess.io.{FenIO, FileIO}
import chess.io.fen.RegexFenParser
import chess.model.{Board, Piece, Role, Color, Square}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.util.{Success, Failure}

class FenFileIOSpec extends AnyWordSpec with Matchers {

  val fenIO: FenIO = RegexFenParser

  "RegexFenParser as FenIO" should {

    "round-trip the initial board" in {
      val board = Board.initial
      val fen = fenIO.save(board)
      val loaded = fenIO.load(fen)

      loaded shouldBe a[Success[?]]
      loaded.get shouldBe board
    }

    "save produces valid FEN" in {
      val fen = fenIO.save(Board.initial)
      fen shouldBe "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
    }

    "load parses valid FEN" in {
      val result = fenIO.load("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR")
      result shouldBe a[Success[?]]
      result.get.pieceAt(Square("e4")) should contain(
        Piece(Role.Pawn, Color.White)
      )
      result.get.pieceAt(Square("e2")) shouldBe empty
    }

    "load fails on invalid FEN" in {
      val result = fenIO.load("not a fen")
      result shouldBe a[Failure[?]]
    }

    "load fails on empty string" in {
      val result = fenIO.load("")
      result shouldBe a[Failure[?]]
    }

    "be usable through the FileIO interface" in {
      val fileIO: FileIO = fenIO
      val board = Board.initial
      val fen = fileIO.save(board)
      val loaded = fileIO.load(fen)
      loaded.get shouldBe board
    }

    "round-trip a position after moves" in {
      val board = Board.initial
        .move(Square("e2"), Square("e4"))
        .get
        .move(Square("e7"), Square("e5"))
        .get
      val fen = fenIO.save(board)
      val loaded = fenIO.load(fen)
      loaded shouldBe a[Success[?]]
      // FEN does not encode lastMove, so compare squares (piece positions)
      loaded.get.squares shouldBe board.squares
    }
  }
}
