package chess.view

import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.{Font, FontWeight, Text}
import scalafx.stage.Stage
import chess.controller.GameController
import chess.model.{Board, PieceColor, Square, File, Rank}
import scala.compiletime.uninitialized
import javafx.application.{Application, Platform}
import javafx.application.Platform.{setImplicitExit}
import java.awt.Desktop

class ChessGUI(val controller: GameController) {
  private var selectedSquare: Option[Square] = None
  private var boardSquares: Array[Array[Rectangle]] =
    Array.ofDim[Rectangle](8, 8)
  private var boardLabels: Array[Array[Text]] = Array.ofDim[Text](8, 8)
  private[view] var primaryStage: Stage = uninitialized

  // UI components that need to be updated
  private var playerLabel: Label = uninitialized
  private var moveInput: TextField = uninitialized
  private var fenDisplay: TextArea = uninitialized

  private[view] def createBoardPane(): GridPane = {
    val boardPane = new GridPane {
      padding = Insets(20)
      hgap = 0
      vgap = 0
      alignment = Pos.Center
    }

    // Add file labels (a-h) at the bottom
    for (file <- File.all) {
      val fileLabel = new Label(file.letter.toString) {
        font = Font.font("Arial", FontWeight.Bold, 16)
        prefWidth = 80
        prefHeight = 25
        alignment = Pos.Center
      }
      boardPane.add(
        fileLabel,
        file.index,
        9
      ) // Row 9 is below the board (rows 1-8)
    }

    // Add rank labels (8-1) on the left
    for (rank <- Rank.all.reverse) {
      val row = 8 - rank.index
      val rankLabel = new Label(rank.index.toString) {
        font = Font.font("Arial", FontWeight.Bold, 16)
        prefWidth = 25
        prefHeight = 80
        alignment = Pos.Center
      }
      boardPane.add(
        rankLabel,
        0,
        row + 1
      ) // Column 0 is left of the board (cols 1-8)
    }

    // Create the chess squares
    for {
      rank <- Rank.all.reverse
      file <- File.all
    } {
      val row = 8 - rank.index
      val col = file.index - 1
      val sq = Square(file, rank)

      val squareRect = new Rectangle {
        width = 80
        height = 80
        val isLight = (file.index + rank.index) % 2 == 0
        fill = if (isLight) Color.web("#f0d9b5") else Color.web("#b58863")
        stroke = Color.Black
        strokeWidth = 1
      }

      val text = new Text {
        font = Font.font("Arial", FontWeight.Normal, 50)
        fill = Color.Black
      }

      val stackPane = new StackPane {
        children = Seq(squareRect, text)
        prefWidth = 80
        prefHeight = 80

        onMouseClicked = _ => {
          handleSquareClick(sq)
          updateBoard()
        }
      }

      // Offset by 1 column and 1 row to make room for labels
      boardPane.add(stackPane, col + 1, row + 1)
      boardSquares(row)(col) = squareRect
      boardLabels(row)(col) = text
    }

    updateBoard()
    boardPane
  }

  private[view] def createControlPanel(): VBox = {
    playerLabel = new Label("White to move") {
      font = Font.font("Arial", FontWeight.Bold, 16)
      padding = Insets(10)
    }

    // Move input section
    val moveLabel = new Label("Enter Move (PGN):") {
      font = Font.font("Arial", FontWeight.Normal, 14)
    }

    moveInput = new TextField {
      promptText = "e.g., e4, Nf3, O-O"
      prefWidth = 250
    }

    val moveButton = new Button("Make Move") {
      prefWidth = 250
      style = "-fx-font-size: 14px; -fx-padding: 10px;"
      onAction = _ => handleMoveInput()
    }

    // Allow Enter key to submit move
    moveInput.onAction = _ => handleMoveInput()

    // FEN section
    val fenLabel = new Label("FEN Position:") {
      font = Font.font("Arial", FontWeight.Normal, 14)
      padding = Insets(10, 0, 5, 0)
    }

    fenDisplay = new TextArea {
      text = controller.getBoardAsFEN
      prefRowCount = 4
      wrapText = true
    }

    val fenLoadButton = new Button("Load FEN") {
      prefWidth = 250
      style = "-fx-font-size: 14px; -fx-padding: 10px;"
      onAction = _ => {
        val fen = fenDisplay.text.value.trim
        if (fen.nonEmpty) {
          controller.loadFromFEN(fen) match {
            case Right(_) =>
              selectedSquare = None
              playerLabel.text = "White to move"
              updateBoard()
            case Left(error) =>
              showAlert("Invalid FEN", error)
          }
        }
      }
    }
    // New Game button
    val resetButton = new Button("New Game") {
      prefWidth = 250
      style =
        "-fx-font-size: 14px; -fx-padding: 10px; -fx-background-color: #ff9800; -fx-text-fill: white;"
      onAction = _ => {
        // Reset the board state
        controller.board = Board.initial
        controller.announceInitial(controller.board)
        selectedSquare = None
        moveInput.text = ""
        playerLabel.text = "White to move"
        fenDisplay.text = controller.getBoardAsFEN
        updateBoard()
      }
    }

    new VBox(10) {
      padding = Insets(20)
      prefWidth = 300
      style = "-fx-border-color: #cccccc; -fx-border-width: 0 0 0 1;"
      children = Seq(
        playerLabel,
        new Separator(),
        moveLabel,
        moveInput,
        moveButton,
        new Separator(),
        fenLabel,
        fenDisplay,
        fenLoadButton,
        new Separator(),
        resetButton
      )
    }
  }

  private def handleMoveInput(): Unit = {
    val move = moveInput.text.value.trim
    if (move.nonEmpty) {
      controller.applyPgnMove(move) match {
        case Right(_) =>
          moveInput.text = ""
          playerLabel.text =
            if (controller.isWhiteToMove) "White to move" else "Black to move"
          fenDisplay.text = controller.getBoardAsFEN
          updateBoard()
        case Left(error) =>
          val detailedError = s"""
            |Move: '$move'
            |
            |Reason: $error
            |
            |Valid moves in PGN notation:
            |  • Pawn: e4, e5
            |  • Knight: Nf3, Nc3
            |  • Bishop: Bc4, Bf4
            |  • Rook: Ra1, Rh1
            |  • Queen: Qh5, Qd1
            |  • King: Ke2, Kf1
            |  • Castling: O-O, O-O-O
            |  • Captures: exd5, Nxe5
          """.stripMargin
          showAlert("Illegal Move - Chess Rules Violation", detailedError)
      }
    }
  }

  private[view] def updateBoard(): Unit = {
    for {
      rank <- Rank.all
      file <- File.all
    } {
      val row = 8 - rank.index
      val col = file.index - 1
      val sq = Square(file, rank)

      val squareRect = boardSquares(row)(col)
      val text = boardLabels(row)(col)

      // Update square color
      val isLight = (file.index + rank.index) % 2 == 0
      val baseColor =
        if (isLight) Color.web("#f0d9b5") else Color.web("#b58863")

      squareRect.fill = selectedSquare match {
        case Some(sel) if sel == sq => Color.web("#baca44")
        case _                      => baseColor
      }

      // Update piece
      text.text = controller.board.pieceAt(sq) match {
        case Some(piece) => piece.toString
        case None        => ""
      }
    }
  }

  private def handleSquareClick(square: Square): Unit = {
    selectedSquare match {
      case None =>
        controller.board.pieceAt(square) match {
          case Some(piece)
              if piece.color == (if (controller.isWhiteToMove) PieceColor.White
                                 else PieceColor.Black) =>
            selectedSquare = Some(square)
          case _ => ()
        }
      case Some(fromSquare) =>
        if (square == fromSquare) {
          selectedSquare = None
        } else {
          controller.applyMove(fromSquare, square) match {
            case Some(_) =>
              selectedSquare = None
              playerLabel.text =
                if (controller.isWhiteToMove) "White to move"
                else "Black to move"
              fenDisplay.text = controller.getBoardAsFEN
            case None =>
              controller.board.pieceAt(square) match {
                case Some(piece)
                    if piece.color == (if (controller.isWhiteToMove)
                                         PieceColor.White
                                       else PieceColor.Black) =>
                  selectedSquare = Some(square)
                case _ =>
                  selectedSquare = None
              }
          }
        }
    }
  }

  private def showAlert(alertTitle: String, message: String): Unit = {
    new Alert(Alert.AlertType.Error) {
      initOwner(primaryStage)
      title = alertTitle
      headerText = None
      contentText = message
    }.showAndWait()
  }
}

object ChessGUI {
  private[view] var instance: ChessGUI = uninitialized

  def main(args: Array[String]): Unit = {
    if (instance == null) {
      instance = new ChessGUI(new GameController(Board.initial))
    }
    Application.launch(classOf[ChessGUILauncher], args*)
  }

  def startInBackground(controller: GameController): Unit = {
    instance = new ChessGUI(controller)
    new Thread(() => Application.launch(classOf[ChessGUILauncher])).start()
    Thread.sleep(2000)
  }
}

// Launcher class required for proper JavaFX initialization
class ChessGUILauncher extends javafx.application.Application {
  override def init(): Unit = {
    // Prevent JavaFX from exiting when last window closes (for dual UI mode)
    Platform.setImplicitExit(false)
  }

  override def start(jfxStage: javafx.stage.Stage): Unit = {
    println("\n[JavaFX] Application.start() called")
    println(s"[JavaFX] Primary stage: $jfxStage")

    // Get the ChessGUI instance
    val gui = ChessGUI.instance

    // CRITICAL: Set the stage to always be on top initially to force macOS to show it
    jfxStage.setAlwaysOnTop(true)
    jfxStage.setIconified(false)

    println("[JavaFX] Creating scene and board...")

    // Wrap the JavaFX Stage with ScalaFX
    gui.primaryStage = new Stage(jfxStage)

    // Set up the scene
    gui.primaryStage.title = "Chess Game - ScalaFX"
    gui.primaryStage.scene = new Scene(1000, 800) {
      root = new BorderPane {
        center = gui.createBoardPane()
        right = gui.createControlPanel()
        style = "-fx-background-color: #f5f5f5;"
      }
    }

    // Listen to board property changes using ScalaFX Reactor pattern
    gui.controller.boardProperty.onChange { (_, _, _) =>
      gui.updateBoard()
    }

    // Configure stage properties for visibility
    gui.primaryStage.resizable = true
    gui.primaryStage.centerOnScreen()

    // Show the stage and request focus
    gui.primaryStage.show()
    gui.primaryStage.toFront()
    gui.primaryStage.requestFocus()

    println("[JavaFX] Window created, attempting to show...")

    // Aggressively bring window to front with multiple attempts
    Platform.runLater(() => {
      Thread.sleep(100)
      jfxStage.toFront()
      jfxStage.requestFocus()
    })

    Platform.runLater(() => {
      Thread.sleep(500)
      jfxStage.setAlwaysOnTop(false)
      jfxStage.toFront()
      jfxStage.requestFocus()
      println("[JavaFX] Window brought to front")
    })

    Platform.runLater(() => {
      Thread.sleep(1000)
      jfxStage.toFront()
      jfxStage.requestFocus()
      println("[JavaFX] Final activation attempt")
    })

    println("\n" + "=" * 60)
    println("✓ ScalaFX Chess GUI Window Opened!")
    println(s"✓ Stage visible: ${jfxStage.isShowing}")
    println(s"✓ Dimensions: ${jfxStage.getWidth} x ${jfxStage.getHeight}")
    println("✓ You can now play by clicking pieces or entering PGN moves")
    println("=" * 60)
    println(
      "\n*** If you don't see the window, check your Dock or press Cmd+Tab ***\n"
    )
  }
}
