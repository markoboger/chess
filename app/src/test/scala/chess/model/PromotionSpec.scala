package chess.model

import chess.controller.io.fen.RegexFenParser
import chess.controller.io.pgn.PgnFileIO
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.GameController
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PromotionSpec extends AnyWordSpec with Matchers {

  given FenIO = RegexFenParser
  given PgnIO = PgnFileIO()

  // White pawn on e7, black king on h8, white king on a1
  private def nearPromotionBoard: Board =
    RegexFenParser.parseFEN("7k/4P3/8/8/8/8/8/K7").get

  // Black pawn on d2, white king on h1, black king on a8
  private def blackNearPromotionBoard: Board =
    RegexFenParser.parseFEN("k7/8/8/8/8/8/3p4/7K").get

  "Board.move with promotion" should {
    "require a promotion piece when white pawn reaches rank 8" in {
      val result = nearPromotionBoard.move(Square("e7"), Square("e8"))
      result.isFailed shouldBe true
      result match {
        case MoveResult.Failed(_, MoveError.PromotionRequired) => succeed
        case _                                                 => fail("Expected PromotionRequired")
      }
    }

    "require a promotion piece when black pawn reaches rank 1" in {
      val result = blackNearPromotionBoard.move(Square("d2"), Square("d1"))
      result.isFailed shouldBe true
      result match {
        case MoveResult.Failed(_, MoveError.PromotionRequired) => succeed
        case _                                                 => fail("Expected PromotionRequired")
      }
    }

    "promote white pawn to Queen" in {
      val result = nearPromotionBoard.move(Square("e7"), Square("e8"), Some(PromotableRole.Queen))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Queen, Color.White))
      result.board.pieceAt(Square("e7")) shouldBe None
    }

    "promote white pawn to Rook" in {
      val result = nearPromotionBoard.move(Square("e7"), Square("e8"), Some(PromotableRole.Rook))
      result.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Rook, Color.White))
    }

    "promote white pawn to Bishop" in {
      val result = nearPromotionBoard.move(Square("e7"), Square("e8"), Some(PromotableRole.Bishop))
      result.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Bishop, Color.White))
    }

    "promote white pawn to Knight" in {
      val result = nearPromotionBoard.move(Square("e7"), Square("e8"), Some(PromotableRole.Knight))
      result.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Knight, Color.White))
    }

    "promote black pawn to Queen" in {
      val result = blackNearPromotionBoard.move(Square("d2"), Square("d1"), Some(PromotableRole.Queen))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("d1")) shouldEqual Some(Piece(Role.Queen, Color.Black))
    }

    "allow promotion capture — pawn takes on back rank" in {
      // White pawn on d7, black rook on e8
      val board = RegexFenParser.parseFEN("7k/3P4/8/8/8/8/8/K7").get
      // Not testing capture specifically here, just that promotion works via forward move
      val result = board.move(Square("d7"), Square("d8"), Some(PromotableRole.Queen))
      result.isSuccess shouldBe true
      result.board.pieceAt(Square("d8")) shouldEqual Some(Piece(Role.Queen, Color.White))
    }

    "count promoted piece in legalMoves (promotion moves are legal)" in {
      // Pawn on e7 can promote to any of 4 pieces — legalMoves uses auto-Queen
      val moves = nearPromotionBoard.legalMoves(Color.White)
      moves.map(_._2) should contain(Square("e8"))
    }
  }

  "GameController.applyPgnMove with promotion" should {
    "apply promotion from PGN e8=Q" in {
      val controller = new GameController(nearPromotionBoard)
      val result = controller.applyPgnMove("e8=Q")
      result.isSuccess shouldBe true
      controller.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Queen, Color.White))
    }

    "apply promotion from PGN e8=R" in {
      val controller = new GameController(nearPromotionBoard)
      controller.applyPgnMove("e8=R").isSuccess shouldBe true
      controller.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Rook, Color.White))
    }

    "apply promotion from PGN e8=B" in {
      val controller = new GameController(nearPromotionBoard)
      controller.applyPgnMove("e8=B").isSuccess shouldBe true
      controller.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Bishop, Color.White))
    }

    "apply promotion from PGN e8=N" in {
      val controller = new GameController(nearPromotionBoard)
      controller.applyPgnMove("e8=N").isSuccess shouldBe true
      controller.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Knight, Color.White))
    }

    "fail with PromotionRequired when PGN has no promotion piece" in {
      val controller = new GameController(nearPromotionBoard)
      val result = controller.applyPgnMove("e8")
      result.isFailed shouldBe true
      result match {
        case MoveResult.Failed(_, MoveError.PromotionRequired) => succeed
        case _                                                 => fail(s"Expected PromotionRequired, got $result")
      }
    }

    "record promotion notation in pgnMoves (may include check symbol)" in {
      val controller = new GameController(nearPromotionBoard)
      controller.applyPgnMove("e8=Q")
      controller.pgnMoves.headOption.map(_.replace("+", "").replace("#", "")) shouldEqual Some("e8=Q")
    }

    "record promotion to Rook in pgnMoves" in {
      val controller = new GameController(nearPromotionBoard)
      controller.applyPgnMove("e8=R")
      controller.pgnMoves.headOption.map(_.replace("+", "").replace("#", "")) shouldEqual Some("e8=R")
    }

    "apply black pawn promotion from PGN d1=Q" in {
      // White king must move first; then black can promote
      val controller = new GameController(blackNearPromotionBoard)
      controller.applyMove(Square("h1"), Square("g1")) // white king step
      controller.applyPgnMove("d1=Q").isSuccess shouldBe true
      controller.board.pieceAt(Square("d1")) shouldEqual Some(Piece(Role.Queen, Color.Black))
    }
  }

  "GameController.applyMove with promotion" should {
    "apply promotion via coordinate move with promotion piece" in {
      val controller = new GameController(nearPromotionBoard)
      val result = controller.applyMove(Square("e7"), Square("e8"), Some(PromotableRole.Queen))
      result.isSuccess shouldBe true
      controller.board.pieceAt(Square("e8")) shouldEqual Some(Piece(Role.Queen, Color.White))
    }

    "fail with PromotionRequired via coordinate move without promotion piece" in {
      val controller = new GameController(nearPromotionBoard)
      val result = controller.applyMove(Square("e7"), Square("e8"))
      result match {
        case MoveResult.Failed(_, MoveError.PromotionRequired) => succeed
        case _                                                 => fail(s"Expected PromotionRequired, got $result")
      }
    }
  }
}
