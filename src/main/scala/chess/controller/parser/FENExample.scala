package chess.controller.parser

import chess.model.{Board, Square, File, Rank}
import chess.controller.GameController

object FENExample extends App {
  println("=== FEN Notation Example ===\n")

  // Example 1: Initial position
  println("1. Initial Position:")
  val controller1 = new GameController(Board.initial)
  println(controller1.board)
  println(s"FEN: ${controller1.getBoardAsFEN}\n")

  // Example 2: Load a position from FEN (after 1.e4 e5)
  println("2. After 1.e4 e5:")
  val controller2 = new GameController(Board.initial)
  val fen1 = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
  controller2.loadFromFEN(fen1) match {
    case Right(board) =>
      println(board)
      println(s"FEN: ${controller2.getBoardAsFEN}\n")
    case Left(error) =>
      println(s"Error: $error\n")
  }

  // Example 3: Load a complex middlegame position
  println("3. Complex Middlegame Position:")
  val controller3 = new GameController(Board.initial)
  val fen2 = "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R"
  controller3.loadFromFEN(fen2) match {
    case Right(board) =>
      println(board)
      println(s"FEN: ${controller3.getBoardAsFEN}\n")
    case Left(error) =>
      println(s"Error: $error\n")
  }

  // Example 4: Make moves and get FEN
  println("4. Make Moves and Get FEN:")
  val controller4 = new GameController(Board.initial)
  println("Initial position:")
  println(controller4.board)
  println(s"FEN: ${controller4.getBoardAsFEN}\n")

  println("After 1.e4:")
  controller4.applyMove(Square("e2"), Square("e4"))
  println(controller4.board)
  println(s"FEN: ${controller4.getBoardAsFEN}\n")

  println("After 1...e5:")
  controller4.applyMove(Square("e7"), Square("e5"))
  println(controller4.board)
  println(s"FEN: ${controller4.getBoardAsFEN}\n")

  println("After 2.Nf3:")
  controller4.applyMove(Square("g1"), Square("f3"))
  println(controller4.board)
  println(s"FEN: ${controller4.getBoardAsFEN}\n")

  // Example 5: Invalid FEN
  println("5. Invalid FEN Example:")
  val controller5 = new GameController(Board.initial)
  val invalidFEN = "invalid/fen/notation"
  controller5.loadFromFEN(invalidFEN) match {
    case Right(_) =>
      println("Position loaded successfully")
    case Left(error) =>
      println(s"Error (as expected): $error\n")
  }
}
