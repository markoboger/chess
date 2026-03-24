package chess.controller.parser

import chess.model.{Board, Color}
import chess.controller.GameController

object PGNExample extends App {
  // Initialize the game with the starting position
  val board = Board.initial
  val controller = new GameController(board)

  // Example PGN moves for the Italian Game
  val pgnMoves = List(
    "e4",
    "e5",
    "Nf3",
    "Nc6",
    "Bc4",
    "Bc5",
    "b4",
    "Bxb4",
    "c3",
    "Ba5",
    "d4",
    "exd4",
    "O-O",
    "dxc3",
    "Qb3",
    "Qe7",
    "Nxc3"
  )

  println("Initial position:")
  println(board)

  // Execute each move
  pgnMoves.foreach { move =>
    println(
      s"\n${if (controller.isWhiteToMove) "White" else "Black"} plays: $move"
    )
    controller.applyPgnMove(move) match {
      case Right(newBoard) =>
        println(newBoard)
      case Left(error) =>
        println(s"Error: $error")
    }
  }
}
