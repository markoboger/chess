package chess.aview

import chess.controller.GameController
import chess.model.{
  Board,
  Piece,
  Color,
  PromotableRole,
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
      // $COVERAGE-OFF$ IOException from System.in is not triggerable in unit tests
      case _: Exception => false
      // $COVERAGE-ON$
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
    // $COVERAGE-OFF$ stdin check and infinite loop only run in non-interactive mode
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
    // $COVERAGE-ON$

    var running = true
    while (running) {
      val input = promptForMove()

      if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
        println("Thanks for playing!")
        running = false
      } else {
        // Try PGN notation first (e.g. e4, Nf3, e8=Q); display handled by update()
        controller.applyPgnMove(input) match {
          case _: MoveResult.Moved => // displayed by update()
          case MoveResult.Failed(_, MoveError.PromotionRequired) =>
            println("Promotion required. Include the piece: e8=Q, e8=R, e8=B, or e8=N.")
          case MoveResult.Failed(_, MoveError.ParseError(_)) =>
            // PGN parsing failed — try coordinate notation (e.g. e2e4, e7e8=Q)
            parseMove(input) match {
              case Some((from, to, promo)) =>
                val result = controller.applyMove(from, to, promo)
                result match {
                  // $COVERAGE-OFF$ coordinate promotion: PGN always catches pawn moves first
                  case MoveResult.Failed(_, MoveError.PromotionRequired) =>
                    promptForPromotion().foreach { pr =>
                      controller.applyMove(from, to, Some(pr))
                    }
                  // $COVERAGE-ON$
                  case _ => // displayed by update()
                }
              case None =>
                println(
                  "Invalid move format. Use PGN (e4, Nf3, O-O, e8=Q) or coordinates (e2e4, e7e8=Q)."
                )
            }
          case _: MoveResult.Failed => // error displayed by update()
        }
      }
    }
  }

  /** Parse a move in coordinate notation (e.g., "e2e4", "e7e8=Q").
    * @return
    *   Tuple of (from, to, promotion) if valid, None otherwise
    */
  private[aview] def parseMove(move: String): Option[(Square, Square, Option[PromotableRole])] = {
    val (coords, promoStr) = move.indexOf('=') match {
      case -1  => (move, "")
      case idx => (move.take(idx), move.drop(idx + 1))
    }
    if (coords.length == 4) {
      for
        from <- Square.fromString(coords.substring(0, 2))
        to   <- Square.fromString(coords.substring(2, 4))
      yield
        val promo = promoStr.toUpperCase match {
          case "Q" => Some(PromotableRole.Queen)
          case "R" => Some(PromotableRole.Rook)
          case "B" => Some(PromotableRole.Bishop)
          case "N" => Some(PromotableRole.Knight)
          case _   => None
        }
        (from, to, promo)
    } else {
      None
    }
  }

  private[aview] def promptForPromotion(): Option[PromotableRole] = {
    print("Promote to (Q=Queen, R=Rook, B=Bishop, N=Knight): ")
    val input = StdIn.readLine()
    if (input == null) None
    else input.trim.toUpperCase match {
      case "Q" | "QUEEN"  => Some(PromotableRole.Queen)
      case "R" | "ROOK"   => Some(PromotableRole.Rook)
      case "B" | "BISHOP" => Some(PromotableRole.Bishop)
      case "N" | "KNIGHT" => Some(PromotableRole.Knight)
      case _ =>
        println("Invalid choice. Defaulting to Queen.")
        Some(PromotableRole.Queen)
    }
  }
}
