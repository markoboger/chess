package chess.view

import chess.controller.GameController
import chess.model.{
  Board,
  Piece,
  Color,
  Square,
  File,
  Rank,
  MoveResult,
  MoveError,
  GameEvent
}
import scala.io.StdIn

/** A console-based view for the chess game. Uses ScalaFX's Reactor pattern to
  * receive board updates.
  */
class ConsoleView(val controller: GameController) {
  controller.boardProperty.onChange { (_, _, newBoard) =>
    println(showBoard(newBoard))
    if (!isInputAvailable) {
      val player = if (controller.isWhiteToMove) "White" else "Black"
      println(s"$player to move (make moves in GUI)\n")
    }
  }

  def showWelcome(): String =
    """Welcome to Console Chess!
      |Type moves in algebraic notation (e.g., e4, Nf3, O-O) or coordinate notation (e2e4)
      |Type 'quit' or 'exit' to end the game
      |""".stripMargin

  def showBoard(board: Board): String = {
    val ranks = Rank.all.reverse
      .map { rank =>
        val rankLine = File.all
          .map { file =>
            board.pieceAt(Square(file, rank)) match {
              case Some(piece) => piece.toString
              case None        => "."
            }
          }
          .mkString(" ")
        s"${rank.index} | $rankLine"
      }
      .mkString("\n")

    "    a b c d e f g h\n" + ranks
  }

  def promptForMove(): String = {
    val player = if (controller.isWhiteToMove) "White" else "Black"
    print(s"$player's move: ")
    val input = StdIn.readLine()
    if (input == null) {
      throw new NullPointerException("Input stream closed")
    }
    input.trim
  }

  /** Check if stdin is available for input
    */
  private def isInputAvailable: Boolean = {
    try {
      System.console() != null || System.in.available() >= 0
    } catch {
      case _: Exception => false
    }
  }

  private def handleGameEvent(event: GameEvent): Unit = event match {
    case GameEvent.Check     => println("Check!")
    case GameEvent.Checkmate => println("Checkmate!")
    case GameEvent.Stalemate => println("Stalemate! The game is a draw.")
    case GameEvent.Moved     => // normal move, board shown via observer
  }

  /** Main game loop for the console interface.
    */
  def run(): Unit = {
    println(showWelcome())
    println(showBoard(controller.board))

    // Check if we can read input
    if (!isInputAvailable) {
      println("\n[Console Mode: Read-Only Observer]")
      println("Input not available - displaying board updates only")
      println("Use the GUI to make moves\n")

      // Just keep the thread alive to observe updates
      // (updates happen via the Observer interface's update() method)
      while (true) {
        Thread.sleep(1000)
      }
    }

    var running = true
    while (running) {
      val input = promptForMove()

      if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
        println("Thanks for playing!")
        running = false
      } else {
        // Try PGN notation first
        controller.applyPgnMove(input) match {
          case MoveResult.Moved(_, event) =>
            handleGameEvent(event)
          case MoveResult.Failed(_, MoveError.ParseError(_)) =>
            // PGN parsing failed, try coordinate notation
            parseMove(input) match {
              case Some((from, to)) =>
                controller.applyMove(from, to) match {
                  case MoveResult.Moved(_, event) =>
                    handleGameEvent(event)
                  case MoveResult.Failed(_, error) =>
                    println(s"Invalid move: ${error.message}")
                }
              case None =>
                println(
                  "Invalid move format. Use PGN (e4, Nf3, O-O) or coordinates (e2e4)."
                )
            }
          case MoveResult.Failed(_, error) =>
            println(s"Illegal move: ${error.message}")
        }
      }
    }
  }

  /** Parse a move in coordinate notation (e.g., "e2e4")
    * @return
    *   Tuple of (from, to) Squares if valid, None otherwise
    */
  private def parseMove(move: String): Option[(Square, Square)] = {
    if (move.length == 4) {
      for
        from <- Square.fromString(move.substring(0, 2))
        to <- Square.fromString(move.substring(2, 4))
      yield (from, to)
    } else {
      None
    }
  }
}
