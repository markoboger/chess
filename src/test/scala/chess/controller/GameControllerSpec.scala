package chess.controller

import chess.model.{
  Board,
  Color,
  Piece,
  Role,
  Square,
  File,
  Rank,
  MoveResult,
  MoveError,
  GameEvent
}
import chess.io.{FenIO, PgnIO}
import chess.io.fen.RegexFenParser
import chess.io.pgn.PgnFileIO
import chess.util.Observer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A simple test observer that records the last event it received. */
class TestObserver extends Observer[MoveResult]:
  var lastEvent: Option[MoveResult] = None
  override def update(event: MoveResult): Unit = lastEvent = Some(event)

final class GameControllerSpec extends AnyWordSpec with Matchers:

  given FenIO = RegexFenParser
  given PgnIO = PgnFileIO()

  "GameController" should {
    "announce the initial board to observers" in {
      val controller = new GameController(Board.initial)
      val observer = new TestObserver
      controller.add(observer)

      val emptyBoard = Board(Vector.fill(8, 8)(None))
      controller.announceInitial(emptyBoard)

      observer.lastEvent shouldBe defined
      observer.lastEvent.get.isSuccess shouldBe true
      observer.lastEvent.get.board shouldBe emptyBoard
    }

    "update its internal board and notify observers when applying a move" in {
      val controller = new GameController(Board.initial)
      val observer = new TestObserver
      controller.add(observer)

      val before = controller.board
      val result = controller.applyMove(from = Square("e2"), to = Square("e4"))
      result.isSuccess shouldBe true

      val after = result.board
      controller.board shouldBe after
      observer.lastEvent.get.board shouldBe after
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
      result.isSuccess shouldBe true
      controller.isWhiteToMove shouldBe false
    }

    "return error for invalid PGN moves" in {
      val controller = new GameController(Board.initial)

      val result = controller.applyPgnMove("Ke4")
      result.isFailed shouldBe true
    }

    "apply coordinate moves successfully" in {
      val controller = new GameController(Board.initial)

      val result = controller.applyMove(Square("e2"), Square("e4"))
      result.isSuccess shouldBe true
      controller.isWhiteToMove shouldBe false
    }

    "return Failed for invalid coordinate moves" in {
      val controller = new GameController(Board.initial)

      val result = controller.applyMove(Square("e4"), Square("e5"))
      result.isFailed shouldBe true
      controller.isWhiteToMove shouldBe true
    }

    "prevent moving opponent's pieces" in {
      val controller = new GameController(Board.initial)

      val result = controller.applyMove(Square("e7"), Square("e5"))
      result.isFailed shouldBe true
      result match {
        case MoveResult.Failed(_, MoveError.WrongColor) => // expected
        case other => fail(s"Expected WrongColor, got: $other")
      }
      controller.isWhiteToMove shouldBe true
    }

    "execute a sequence of PGN moves" in {
      val controller = new GameController(Board.initial)

      val moves = List("e4", "e5", "Nf3", "Nc6", "Bc4")
      moves.foreach { move =>
        val result = controller.applyPgnMove(move)
        result.isSuccess shouldBe true
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

    "return Failed when piece exists but move is invalid" in {
      val controller = new GameController(Board.initial)
      // White pawn on e2 trying to move to e5 (3 squares) - piece exists, correct color, but invalid move
      val result = controller.applyMove(Square("e2"), Square("e5"))
      result.isFailed shouldBe true
      controller.isWhiteToMove shouldBe true
    }

    "return Failed when PGN parses but Board rejects the move" in {
      // Use a FEN position where PGNParser finds a piece (its simplified validation passes)
      // but Board.move's stricter validation rejects it
      // King on e1, pawn on e2 blocks Ke2 but PGNParser's King validation (dx<=1,dy<=1) passes
      val controller = new GameController(Board.initial)
      val result = controller.applyPgnMove("Ke2")
      result.isFailed shouldBe true
    }

    "report activeColor as White initially" in {
      val controller = new GameController(Board.initial)
      controller.activeColor shouldBe Color.White
    }

    "report activeColor as Black after one move" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))
      controller.activeColor shouldBe Color.Black
    }

    "report not in check for initial position" in {
      val controller = new GameController(Board.initial)
      controller.isInCheck shouldBe false
    }

    "report not checkmate for initial position" in {
      val controller = new GameController(Board.initial)
      controller.isCheckmate shouldBe false
    }

    "report not stalemate for initial position" in {
      val controller = new GameController(Board.initial)
      controller.isStalemate shouldBe false
    }

    "report correct gameStatus for initial position" in {
      val controller = new GameController(Board.initial)
      controller.gameStatus shouldBe "White to move"
    }

    "report check via gameStatus" in {
      val board = RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/4RK2").get
      val controller = new GameController(board)
      // It's white to move, but let's set up black to move scenario
      // Actually, with white to move and black king in check from rook,
      // we need to check from black's perspective
      // Let's use a position where white is in check
      val boardWhiteInCheck =
        RegexFenParser.parseFEN("4k3/8/8/8/8/8/8/3rK3").get
      val ctrl2 = new GameController(boardWhiteInCheck)
      ctrl2.isInCheck shouldBe true
      ctrl2.gameStatus should include("check")
    }

    "report checkmate via gameStatus" in {
      // White king on a1, black queen on b2 (protected by black king on c3)
      val matedBoard = RegexFenParser.parseFEN("8/8/8/8/8/2k5/1q6/K7").get
      val ctrl = new GameController(matedBoard)
      ctrl.isCheckmate shouldBe true
      ctrl.gameStatus should include("Checkmate")
    }

    "report stalemate via gameStatus" in {
      val board = RegexFenParser.parseFEN("k7/8/1Q6/8/8/8/8/2K5").get
      // Black to move, but controller starts with white to move
      // Need white stalemated: king on a1, black queen on b3
      val stalemateBoard = RegexFenParser.parseFEN("7k/8/8/8/8/1q6/8/K7").get
      val ctrl = new GameController(stalemateBoard)
      // White king on a1, black queen on b3: king can go to a2 (attacked by q), b1 (attacked by q), b2 (attacked by q)
      // Actually a1 king, b3 queen: a2 is attacked diag by queen. b1 attacked by queen on rank. b2 attacked diag.
      // So stalemate!
      ctrl.isStalemate shouldBe true
      ctrl.gameStatus should include("Stalemate")
    }

    "track board states in history" in {
      val controller = new GameController(Board.initial)
      controller.boardStates.length shouldBe 1
      controller.currentIndex shouldBe 0
      controller.isAtLatest shouldBe true

      controller.applyMove(Square("e2"), Square("e4"))
      controller.boardStates.length shouldBe 2
      controller.currentIndex shouldBe 1
      controller.isAtLatest shouldBe true

      controller.applyMove(Square("e7"), Square("e5"))
      controller.boardStates.length shouldBe 3
      controller.currentIndex shouldBe 2
    }

    "generate PGN text from move history" in {
      val controller = new GameController(Board.initial)
      controller.pgnText shouldBe ""

      controller.applyPgnMove("e4")
      controller.pgnText shouldBe "1. e4"

      controller.applyPgnMove("e5")
      controller.pgnText shouldBe "1. e4 e5"

      controller.applyPgnMove("Nf3")
      controller.pgnText shouldBe "1. e4 e5 2. Nf3"
    }

    "record PGN for coordinate moves" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))
      controller.pgnMoves.head shouldBe "e4"
    }

    "navigate backward through history" in {
      val controller = new GameController(Board.initial)
      val observer = new TestObserver
      controller.add(observer)

      controller.applyMove(Square("e2"), Square("e4"))
      controller.applyMove(Square("e7"), Square("e5"))
      controller.currentIndex shouldBe 2

      controller.backward()
      controller.currentIndex shouldBe 1
      controller.board shouldBe controller.boardStates(1)
      controller.isWhiteToMove shouldBe false
      observer.lastEvent.get.isSuccess shouldBe true

      controller.backward()
      controller.currentIndex shouldBe 0
      controller.board shouldBe controller.boardStates(0)
      controller.isWhiteToMove shouldBe true
    }

    "not navigate backward past the initial position" in {
      val controller = new GameController(Board.initial)
      controller.backward()
      controller.currentIndex shouldBe 0
      controller.board shouldBe Board.initial
    }

    "navigate forward through history" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))
      controller.applyMove(Square("e7"), Square("e5"))

      controller.backward()
      controller.backward()
      controller.currentIndex shouldBe 0

      controller.forward()
      controller.currentIndex shouldBe 1
      controller.isWhiteToMove shouldBe false

      controller.forward()
      controller.currentIndex shouldBe 2
      controller.isAtLatest shouldBe true
    }

    "not navigate forward past the latest position" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))

      controller.forward()
      controller.currentIndex shouldBe 1
      controller.isAtLatest shouldBe true
    }

    "jump directly to any position with goToMove" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))
      controller.applyMove(Square("e7"), Square("e5"))
      controller.applyMove(Square("g1"), Square("f3"))

      controller.goToMove(1)
      controller.currentIndex shouldBe 1
      controller.board.pieceAt(Square("e4")) shouldEqual Some(Piece(Role.Pawn, Color.White))
      controller.board.pieceAt(Square("e5")) shouldBe None

      controller.goToMove(0)
      controller.currentIndex shouldBe 0
      controller.board shouldEqual Board.initial

      controller.goToMove(3)
      controller.currentIndex shouldBe 3
      controller.isAtLatest shouldBe true
    }

    "ignore goToMove with out-of-range index" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))

      controller.goToMove(-1)
      controller.currentIndex shouldBe 1

      controller.goToMove(99)
      controller.currentIndex shouldBe 1
    }

    "notify observer when goToMove is called" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))
      controller.applyMove(Square("e7"), Square("e5"))

      var notified = false
      controller.add(new chess.util.Observer[MoveResult] {
        def update(e: MoveResult): Unit = notified = true
      })

      controller.goToMove(1)
      notified shouldBe true
    }

    "reject moves when not at the latest position" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))
      controller.applyMove(Square("e7"), Square("e5"))

      controller.backward()
      controller.isAtLatest shouldBe false

      val result = controller.applyMove(Square("d2"), Square("d4"))
      result.isFailed shouldBe true
    }

    "reset history on announceInitial" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))
      controller.applyMove(Square("e7"), Square("e5"))
      controller.pgnMoves.length shouldBe 2

      controller.announceInitial(Board.initial)
      controller.boardStates.length shouldBe 1
      controller.pgnMoves shouldBe empty
      controller.currentIndex shouldBe 0
      controller.pgnText shouldBe ""
    }

    "reset history on loadFromFEN" in {
      val controller = new GameController(Board.initial)
      controller.applyMove(Square("e2"), Square("e4"))
      controller.pgnMoves.length shouldBe 1

      controller.loadFromFEN("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR")
      controller.boardStates.length shouldBe 1
      controller.pgnMoves shouldBe empty
      controller.currentIndex shouldBe 0
    }
  }

  "GameController.loadPgnMoves" should {
    "load a sequence of PGN moves with move numbers" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("1. e4 e5 2. Nf3 Nc6")
      result.isRight shouldBe true
      controller.pgnMoves.length shouldBe 4
      controller.boardStates.length shouldBe 5
      controller.currentIndex shouldBe 4
      controller.isAtLatest shouldBe true
    }

    "load moves without move numbers" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("e4 e5 Nf3 Nc6")
      result.isRight shouldBe true
      controller.pgnMoves.length shouldBe 4
    }

    "strip comments and results" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("1. e4 {best move} e5 1-0")
      result.isRight shouldBe true
      controller.pgnMoves.length shouldBe 2
    }

    "strip variations in parentheses" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("1. e4 e5 (1... c5) 2. Nf3")
      result.isRight shouldBe true
      controller.pgnMoves.length shouldBe 3
    }

    "return Left on empty input" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("")
      result.isLeft shouldBe true
      result.left.getOrElse("") should include("No moves found")
    }

    "return Left on input with only move numbers and results" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("1. 2. 1/2-1/2")
      result.isLeft shouldBe true
    }

    "return Left on invalid move" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("1. e4 e5 2. Qh8")
      result.isLeft shouldBe true
      result.left.getOrElse("") should include("Failed at move 3")
    }

    "reset board to initial before applying moves" in {
      val controller = new GameController(Board.initial)
      // Make some moves first
      controller.applyMove(Square("d2"), Square("d4"))
      controller.pgnMoves.length shouldBe 1

      // Load PGN should reset and start fresh
      controller.loadPgnMoves("1. e4 e5")
      controller.pgnMoves.length shouldBe 2
      controller.boardStates.head shouldBe Board.initial
    }

    "allow navigation after loading" in {
      val controller = new GameController(Board.initial)
      controller.loadPgnMoves("1. e4 e5 2. Nf3")
      controller.currentIndex shouldBe 3

      controller.backward()
      controller.currentIndex shouldBe 2
      controller.isAtLatest shouldBe false

      controller.forward()
      controller.currentIndex shouldBe 3
      controller.isAtLatest shouldBe true
    }

    "handle result markers *" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("1. e4 e5 *")
      result.isRight shouldBe true
      controller.pgnMoves.length shouldBe 2
    }

    "propagate first error when multiple moves follow a bad one" in {
      val controller = new GameController(Board.initial)
      val result = controller.loadPgnMoves("1. e4 Qh8 Nf3 Nc6")
      result.isLeft shouldBe true
      result.left.getOrElse("") should include("Failed at move 2")
    }
  }
