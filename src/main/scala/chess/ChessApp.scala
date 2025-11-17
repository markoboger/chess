package chess

import chess.controller.GameController
import chess.model.Board
import chess.view.{ConsoleView, ChessGUI}

object ChessApp:
  def main(args: Array[String]): Unit = {
    println(welcomeBanner())
    val controller = new GameController(Board.initial)
    ChessGUI.startInBackground(controller)
    new ConsoleView(controller).run()
  }
  
  private def welcomeBanner(): String = {
    val separator = "=" * 60
    s"""$separator
       |Chess Game - Dual UI Mode
       |Starting both ScalaFX GUI and Console...
       |$separator
       |""".stripMargin
  }
  
  