package chess.controller

import chess.model.{Board, PieceColor, Square, File, Rank}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class GameControllerSpec extends AnyWordSpec with Matchers:

  "GameController" should {
    "announce the initial board to listeners" in {
      val controller = new GameController(Board.initial)
      var lastBoard: Option[Board] = None

      controller.boardProperty.onChange { (_, _, newBoard) =>
        lastBoard = Some(newBoard)
      }

      val emptyBoard = Board(Vector.fill(8, 8)(None))
      controller.announceInitial(emptyBoard)

      lastBoard shouldBe defined
      lastBoard.get shouldBe emptyBoard
    }

    "update its internal board and notify listeners when applying a move" in {
      val controller = new GameController(Board.initial)
      var lastBoard: Option[Board] = None

      controller.boardProperty.onChange { (_, _, newBoard) =>
        lastBoard = Some(newBoard)
      }

      val before = controller.board
      val after =
        controller.applyMove(before, from = Square("e2"), to = Square("e4"))

      controller.board shouldBe after
      lastBoard should contain(after)
      after should not be theSameInstanceAs(before)
    }

    "track whose turn it is" in {
      val controller = new GameController(Board.initial)

      controller.isWhiteToMove shouldBe true

      controller.applyMove(Square("e2"), Square("e4"))
      controller.isWhiteToMove shouldBe false

      controller.applyMove(Square("e7"), Square("e5"))
      controller.isWhiteToMove shouldBe true
    }

    "apply PGN moves successfully" in {
      val controller = new GameController(Board.initial)

      controller.isWhiteToMove shouldBe true
      val result = controller.applyPgnMove("e4")
      result.isRight shouldBe true
      controller.isWhiteToMove shouldBe false
    }

    "return error for invalid PGN moves" in {
      val controller = new GameController(Board.initial)

      val result = controller.applyPgnMove("Ke4")
      result.isLeft shouldBe true
    }

    "apply coordinate moves successfully" in {
      val controller = new GameController(Board.initial)

      val result = controller.applyMove(Square("e2"), Square("e4"))
      result.isDefined shouldBe true
      controller.isWhiteToMove shouldBe false
    }

    "return None for invalid coordinate moves" in {
      val controller = new GameController(Board.initial)

      val result = controller.applyMove(Square("e4"), Square("e5"))
      result shouldBe None
      controller.isWhiteToMove shouldBe true
    }

    "prevent moving opponent's pieces" in {
      val controller = new GameController(Board.initial)

      val result = controller.applyMove(Square("e7"), Square("e5"))
      result shouldBe None
      controller.isWhiteToMove shouldBe true
    }

    "execute a sequence of PGN moves" in {
      val controller = new GameController(Board.initial)

      val moves = List("e4", "e5", "Nf3", "Nc6", "Bc4")
      moves.foreach { move =>
        val result = controller.applyPgnMove(move)
        result.isRight shouldBe true
      }

      controller.isWhiteToMove shouldBe false
    }

    "load a position from FEN notation" in {
      val controller = new GameController(Board.initial)
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"

      val result = controller.loadFromFEN(fen)
      result.isRight shouldBe true
      controller.isWhiteToMove shouldBe true
    }

    "return error for invalid FEN" in {
      val controller = new GameController(Board.initial)
      val invalidFEN = "invalid/fen/string"

      val result = controller.loadFromFEN(invalidFEN)
      result.isLeft shouldBe true
    }

    "get current board position as FEN" in {
      val controller = new GameController(Board.initial)
      val fen = controller.getBoardAsFEN

      fen should be("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
    }

    "get FEN after making moves" in {
      val controller = new GameController(Board.initial)

      controller.applyMove(Square("e2"), Square("e4")) // e4
      val fen = controller.getBoardAsFEN

      fen should be("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR")
    }

    "return None when piece exists but move is invalid" in {
      val controller = new GameController(Board.initial)
      // White pawn on e2 trying to move to e5 (3 squares) - piece exists, correct color, but invalid move
      val result = controller.applyMove(Square("e2"), Square("e5"))
      result shouldBe None
      controller.isWhiteToMove shouldBe true
    }

    "return Left when PGN parses but Board rejects the move" in {
      // Use a FEN position where PGNParser finds a piece (its simplified validation passes)
      // but Board.move's stricter validation rejects it
      // King on e1, pawn on e2 blocks Ke2 but PGNParser's King validation (dx<=1,dy<=1) passes
      val controller = new GameController(Board.initial)
      val result = controller.applyPgnMove("Ke2")
      result.isLeft shouldBe true
    }
  }
