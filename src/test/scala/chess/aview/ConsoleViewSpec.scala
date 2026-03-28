package chess.aview

import chess.controller.GameController
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.io.fen.RegexFenParser
import chess.controller.io.pgn.PgnFileIO
import chess.model.{Board, Color, GameEvent, MoveError, MoveResult, Piece, PromotableRole, Role, Square}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream}

final class ConsoleViewSpec extends AnyWordSpec with Matchers:

  given FenIO = RegexFenParser
  given PgnIO = PgnFileIO()

  private def withOutput(block: => Unit): String = {
    val bos = new ByteArrayOutputStream
    val ps = new PrintStream(bos)
    Console.withOut(ps)(block)
    ps.flush()
    bos.toString
  }

  "ConsoleView.showBoard" should {
    "render ranks and files with coordinate labels" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val rendered = view.showBoard(Board.initial)
      rendered should include("8 |")
      rendered should include("1 |")
      rendered should include("a b c d e f g h")
    }
  }

  "ConsoleView.showWelcome" should {
    "provide a welcome message" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      view.showWelcome() should include("Welcome")
    }
  }

  "ConsoleView.update" should {
    "print the board and a status line on a Moved event" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val out = withOutput {
        view.update(MoveResult.Moved(Board.initial, GameEvent.Moved))
      }
      out should include("a b c d e f g h")
      out should include("to move")
    }

    "print an error message on a Failed event" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val out = withOutput {
        view.update(MoveResult.Failed(Board.initial, MoveError.InvalidMove))
      }
      out should include("Error")
    }
  }

  "ConsoleView handles game events" should {
    "announce check" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val out = withOutput {
        view.update(MoveResult.Moved(Board.initial, GameEvent.Check))
      }
      out should include("Check")
    }

    "announce checkmate (Black wins when White to move)" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val out = withOutput {
        view.update(MoveResult.Moved(Board.initial, GameEvent.Checkmate))
      }
      out should include("Black wins")
    }

    "announce checkmate (White wins when Black to move)" in {
      val controller = new GameController(Board.initial)
      // Apply one move so it's Black's turn
      controller.applyMove(Square("e2"), Square("e4"), None)
      val view = new ConsoleView(controller)
      val out = withOutput {
        view.update(MoveResult.Moved(Board.initial, GameEvent.Checkmate))
      }
      out should include("White wins")
    }

    "announce stalemate" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val out = withOutput {
        view.update(MoveResult.Moved(Board.initial, GameEvent.Stalemate))
      }
      out should include("Stalemate")
    }

    "announce threefold repetition" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val out = withOutput {
        view.update(MoveResult.Moved(Board.initial, GameEvent.ThreefoldRepetition))
      }
      out should include("threefold repetition")
    }
  }

  "ConsoleView.parseMove" should {
    "parse valid coordinate notation" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val result = view.parseMove("e2e4")
      result shouldBe defined
      result.get._1 shouldBe Square("e2")
      result.get._2 shouldBe Square("e4")
      result.get._3 shouldBe None
    }

    "parse coordinate notation with promotion" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      val result = view.parseMove("e7e8=Q")
      result shouldBe defined
      result.get._3 shouldBe Some(PromotableRole.Queen)
    }

    "parse promotion to Rook" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      view.parseMove("e7e8=R").get._3 shouldBe Some(PromotableRole.Rook)
    }

    "parse promotion to Bishop" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      view.parseMove("e7e8=B").get._3 shouldBe Some(PromotableRole.Bishop)
    }

    "parse promotion to Knight" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      view.parseMove("e7e8=N").get._3 shouldBe Some(PromotableRole.Knight)
    }

    "parse promotion with unknown piece as None" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      view.parseMove("e7e8=X").get._3 shouldBe None
    }

    "return None for a move string that is too short" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      view.parseMove("e2e") shouldBe None
    }

    "return None for an invalid square" in {
      val controller = new GameController(Board.initial)
      val view = new ConsoleView(controller)
      view.parseMove("z9z9") shouldBe None
    }
  }

  "ConsoleView.promptForMove" should {
    "return the trimmed input" in {
      val in = new ByteArrayInputStream("  e4  \n".getBytes)
      Console.withIn(in) {
        val controller = new GameController(Board.initial)
        val view = new ConsoleView(controller)
        view.promptForMove() shouldBe "e4"
      }
    }

    "throw NullPointerException when stdin is closed" in {
      val in = new ByteArrayInputStream(Array.emptyByteArray)
      Console.withIn(in) {
        val controller = new GameController(Board.initial)
        val view = new ConsoleView(controller)
        an[NullPointerException] should be thrownBy view.promptForMove()
      }
    }
  }

  "ConsoleView.run" should {
    "exit cleanly on 'quit' input" in {
      val in = new ByteArrayInputStream("quit\n".getBytes)
      val out = withOutput {
        Console.withIn(in) {
          val controller = new GameController(Board.initial)
          val view = new ConsoleView(controller)
          view.run()
        }
      }
      out should include("Thanks for playing")
    }

    "exit cleanly on 'exit' input" in {
      val in = new ByteArrayInputStream("exit\n".getBytes)
      val out = withOutput {
        Console.withIn(in) {
          val controller = new GameController(Board.initial)
          val view = new ConsoleView(controller)
          view.run()
        }
      }
      out should include("Thanks for playing")
    }

    "apply a valid PGN move and continue" in {
      val in = new ByteArrayInputStream("e4\nquit\n".getBytes)
      val out = withOutput {
        Console.withIn(in) {
          val controller = new GameController(Board.initial)
          val view = new ConsoleView(controller)
          view.run()
        }
      }
      out should include("Thanks for playing")
    }

    "apply a valid coordinate move" in {
      val in = new ByteArrayInputStream("e2e4\nquit\n".getBytes)
      val out = withOutput {
        Console.withIn(in) {
          val controller = new GameController(Board.initial)
          val view = new ConsoleView(controller)
          view.run()
        }
      }
      out should include("Thanks for playing")
    }

    "handle an invalid move format" in {
      val in = new ByteArrayInputStream("zzz\nquit\n".getBytes)
      val out = withOutput {
        Console.withIn(in) {
          val controller = new GameController(Board.initial)
          val view = new ConsoleView(controller)
          view.run()
        }
      }
      out should include("Thanks for playing")
    }

    "handle PromotionRequired error via PGN path" in {
      // Set up a board where a pawn is about to promote
      val controller = new GameController(Board.initial)(using
        RegexFenParser,
        PgnFileIO()
      )
      controller.loadFromFEN("8/4P3/8/8/8/8/8/k6K")
      // e7e8 without promotion piece → PromotionRequired
      val in = new ByteArrayInputStream("e7e8\nQ\nquit\n".getBytes)
      val out = withOutput {
        Console.withIn(in) {
          val view = new ConsoleView(controller)
          view.run()
        }
      }
      out should include("Thanks for playing")
    }

    "exercise coordinate path when PGN fails (rook move looks like pawn move)" in {
      // Board with rook on e2: PGN parses 'e2e4' as 'pawn from e2 to e4' but
      // there is no pawn at e2 → PGN ParseError → falls through to coordinate path
      val controller = new GameController(Board.initial)(using
        RegexFenParser,
        PgnFileIO()
      )
      controller.loadFromFEN("8/8/8/8/8/8/4R3/4K3")
      val in = new ByteArrayInputStream("e2e4\nquit\n".getBytes)
      val out = withOutput {
        Console.withIn(in) {
          val view = new ConsoleView(controller)
          view.run()
        }
      }
      out should include("Thanks for playing")
    }

    "exercise PGN-success-but-model-rejects path (pinned piece)" in {
      // White king e1, White rook e4, Black queen e8: rook is pinned on e-file.
      // 'Rd4' — PGN finds the rook but the model rejects the move (exposes king).
      val controller = new GameController(Board.initial)(using
        RegexFenParser,
        PgnFileIO()
      )
      controller.loadFromFEN("4q3/8/8/8/4R3/8/8/4K3")
      val in = new ByteArrayInputStream("Rd4\nquit\n".getBytes)
      val out = withOutput {
        Console.withIn(in) {
          val view = new ConsoleView(controller)
          view.run()
        }
      }
      out should include("Thanks for playing")
    }
  }

  "ConsoleView.promptForPromotion" should {
    "return Queen for Q input" in {
      val in = new ByteArrayInputStream("Q\n".getBytes)
      Console.withIn(in) {
        val controller = new GameController(Board.initial)
        val view = new ConsoleView(controller)
        view.promptForPromotion() shouldBe Some(PromotableRole.Queen)
      }
    }

    "return Rook for ROOK input" in {
      val in = new ByteArrayInputStream("ROOK\n".getBytes)
      Console.withIn(in) {
        val controller = new GameController(Board.initial)
        val view = new ConsoleView(controller)
        view.promptForPromotion() shouldBe Some(PromotableRole.Rook)
      }
    }

    "return Bishop for B input" in {
      val in = new ByteArrayInputStream("B\n".getBytes)
      Console.withIn(in) {
        val controller = new GameController(Board.initial)
        val view = new ConsoleView(controller)
        view.promptForPromotion() shouldBe Some(PromotableRole.Bishop)
      }
    }

    "return Knight for KNIGHT input" in {
      val in = new ByteArrayInputStream("KNIGHT\n".getBytes)
      Console.withIn(in) {
        val controller = new GameController(Board.initial)
        val view = new ConsoleView(controller)
        view.promptForPromotion() shouldBe Some(PromotableRole.Knight)
      }
    }

    "default to Queen for unknown input" in {
      val in = new ByteArrayInputStream("X\n".getBytes)
      Console.withIn(in) {
        val controller = new GameController(Board.initial)
        val view = new ConsoleView(controller)
        view.promptForPromotion() shouldBe Some(PromotableRole.Queen)
      }
    }

    "return None when input stream is closed" in {
      val in = new ByteArrayInputStream(Array.emptyByteArray)
      Console.withIn(in) {
        val controller = new GameController(Board.initial)
        val view = new ConsoleView(controller)
        // readLine returns null on EOF
        view.promptForPromotion() shouldBe None
      }
    }
  }
