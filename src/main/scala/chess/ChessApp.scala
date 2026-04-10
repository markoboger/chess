package chess

import chess.AppBindings.given
import chess.aview.ChessGUI
import chess.aview.ConsoleView
import chess.aview.richTui.ConsoleEncoding
import chess.aview.richTui.TuiShell
import chess.controller.GameController
import chess.model.Board

object ChessApp:
  def main(args: Array[String]): Unit =
    ConsoleEncoding.enableUtf8IfNeeded()
    val controller = new GameController(Board.initial)
    ChessGUI.startInBackground(controller)
    new ConsoleView(controller).run()

object ChessGuiApp:
  def main(args: Array[String]): Unit =
    ChessGUI.main(args)

object ChessTuiApp:
  def main(args: Array[String]): Unit =
    TuiShell.run()

object ChessConsoleApp:
  def main(args: Array[String]): Unit =
    ConsoleEncoding.enableUtf8IfNeeded()
    val controller = new GameController(Board.initial)
    new ConsoleView(controller).run()
