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
import chess.util.Observer
import scala.io.StdIn

/** A console-based view for the chess game.
  *
  * Implements [[Observer]] to react to move results published by the
  * [[GameController]]. Both successful moves and errors are handled in
  * [[update]], making the TUI fully event-driven.
  */
class ConsoleView(val controller: GameController) extends Observer[MoveResult] {
  controller.add(this)

  override def update(event: MoveResult): Unit = event match {
    case MoveResult.Moved(board, gameEvent) =>
      println(showBoard(board))
      handleGameEvent(gameEvent)
    case MoveResult.Failed(_, error) =>
      println(s"Error: ${error.message}")
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

  private def handleGameEvent(event: GameEvent): Unit = {
    val player = if (controller.isWhiteToMove) "White" else "Black"
    event match {
      case GameEvent.Check => println(s"Check! $player to move.")
      case GameEvent.Checkmate =>
        println(
          s"Checkmate! ${if (controller.isWhiteToMove) "Black" else "White"} wins!"
        )
      case GameEvent.Stalemate => println("Stalemate! The game is a draw.")
      case GameEvent.Moved     => println(s"$player to move.")
    }
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
        // Try PGN notation first; display is handled by update() observer
        controller.applyPgnMove(input) match {
          case _: MoveResult.Moved => // displayed by update()
          case MoveResult.Failed(_, MoveError.ParseError(_)) =>
            // PGN parsing failed, try coordinate notation
            parseMove(input) match {
              case Some((from, to)) =>
                controller.applyMove(from, to) // result displayed by update()
              case None =>
                println(
                  "Invalid move format. Use PGN (e4, Nf3, O-O) or coordinates (e2e4)."
                )
            }
          case _: MoveResult.Failed => // error displayed by update()
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
