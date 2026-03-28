package chess.aview

import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Scene
import scalafx.scene.control._
import scalafx.scene.layout._
import scalafx.scene.paint.Color
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.{Font, FontWeight, Text, TextFlow}
import scalafx.stage.Stage
import scalafx.stage.FileChooser
import scalafx.stage.FileChooser.ExtensionFilter
import chess.controller.{GameController, ComputerPlayer, MoveStrategy}
import chess.controller.puzzle.PuzzleParser
import chess.model.Puzzle
import chess.controller.strategy.{
  RandomStrategy,
  GreedyStrategy,
  MaterialBalanceStrategy,
  PieceSquareStrategy,
  MinimaxStrategy,
  QuiescenceStrategy,
  IterativeDeepeningStrategy
}
import chess.model.{
  Board,
  Piece,
  PromotableRole,
  Role,
  Square,
  File,
  Rank,
  MoveResult,
  MoveError,
  GameEvent
}
import chess.model.{Color => ChessColor}
import chess.controller.io.FileIO
import chess.controller.io.json.circe.CirceJsonFileIO
import chess.controller.io.json.upickle.UPickleJsonFileIO
import chess.AppBindings.given
import chess.util.Observer
import chess.controller.clock.ClockActor
import org.apache.pekko.actor.typed.ActorSystem
import scala.compiletime.uninitialized
import javafx.application.{Application, Platform}
import javafx.application.Platform.{setImplicitExit}
import javafx.scene.input.{Clipboard, ClipboardContent, DragEvent, TransferMode}
import java.awt.Desktop
import java.net.URI
import java.io.{File => JFile, PrintWriter}
import scala.io.Source
import scala.util.{Try, Success, Failure}

class ChessGUI(val controller: GameController) extends Observer[MoveResult] {
  controller.add(this)

  private[aview] var selectedSquare: Option[Square] = None
  private var boardSquares: Array[Array[Rectangle]] =
    Array.ofDim[Rectangle](8, 8)
  private var boardLabels: Array[Array[Text]] = Array.ofDim[Text](8, 8)
  private var boardDots: Array[Array[scalafx.scene.shape.Circle]] =
    Array.ofDim[scalafx.scene.shape.Circle](8, 8)
  private[aview] var showLegalMoves: Boolean = true

  enum GameMode:
    case HumanVsHuman, HumanVsComputer, ComputerVsComputer

  private[aview] var gameMode: GameMode = GameMode.HumanVsHuman
  private[aview] val whiteComputer: ComputerPlayer = new ComputerPlayer(
    new IterativeDeepeningStrategy(2000L)
  )
  private[aview] val blackComputer: ComputerPlayer = new ComputerPlayer(
    new IterativeDeepeningStrategy(2000L)
  )
  // Whether a background computer-move thread is currently scheduled
  @volatile private var computerScheduled: Boolean = false
  @volatile private[aview] var paused: Boolean = false

  private lazy val puzzles: Vector[Puzzle] =
    PuzzleParser.fromResource("/puzzle/lichess_small_puzzle.csv")

  private var pauseButton: Button = uninitialized
  private var gameButtonBox: HBox = uninitialized
  private[aview] var primaryStage: Stage = uninitialized

  // UI components that need to be updated
  private var playerLabel: Label = uninitialized
  private var runButton: Button = uninitialized
  private var fenText: scalafx.scene.text.Text = uninitialized
  private var pgnDisplay: TextFlow = uninitialized
  private var pgnScrollPane: ScrollPane = uninitialized
  private var blackCapturesBox: HBox = uninitialized
  private var whiteCapturesBox: HBox = uninitialized
  private var materialLabel: Label = uninitialized
  private var whiteClockLabel: Label = uninitialized
  private var blackClockLabel: Label = uninitialized
  private var whiteStrategyLabel: Label = uninitialized
  private var puzzleInfoLabel: Label = uninitialized
  private var puzzleLink: scalafx.scene.control.Hyperlink = uninitialized
  private var blackStrategyLabel: Label = uninitialized

  // ── Chess clock ────────────────────────────────────────────────────────────
  enum ClockMode:
    case NoLimit
    case Timed(initialMs: Long, incrementMs: Long, label: String)

  private var clockMode: ClockMode = ClockMode.NoLimit
  private var whiteElapsedMs: Long = 0
  private var blackElapsedMs: Long = 0
  private var clockStarted: Boolean = false
  private var gameOverByTimeout: Boolean = false
  private var moveDelayEnabled: Boolean = true
  private var lastPgnLength: Int = 0
  private var clockSystem
      : ActorSystem[chess.controller.clock.ClockActor.Command] = uninitialized
  private[aview] var initialized: Boolean = false

  override def update(event: MoveResult): Unit = {
    if (!initialized) return
    Platform.runLater(() => {
      event match {
        case MoveResult.Moved(_, gameEvent) =>
          // Detect a real move (not navigation) by a growing PGN list
          val newPgnLength = controller.pgnMoves.length
          if (newPgnLength > lastPgnLength) {
            lastPgnLength = newPgnLength
            switchClock()
          }
          playerLabel.text = gameEvent match {
            case GameEvent.Checkmate           => "Checkmate!"
            case GameEvent.Stalemate           => "Stalemate! Draw."
            case GameEvent.ThreefoldRepetition => "Draw by threefold repetition."
            case GameEvent.Check =>
              s"${if (controller.isWhiteToMove) "White" else "Black"} is in check!"
            case GameEvent.Moved =>
              if (controller.isWhiteToMove) "White to move" else "Black to move"
          }
          if (fenText != null) fenText.text = controller.getBoardAsFEN
          updatePgnDisplay()
          updateBoard()
          updateCapturedPanel()
          triggerComputerMoveIfNeeded()
        case MoveResult.Failed(_, _) =>
          // Errors are handled inline by the originating action
          // (click vs text input require different UI feedback)
          ()
      }
    })
  }

  private def isGameOver: Boolean =
    controller.isCheckmate || controller.isStalemate ||
      controller.isThreefoldRepetition || gameOverByTimeout

  private def isComputerTurn: Boolean = gameMode match
    case GameMode.HumanVsHuman => false
    case GameMode.HumanVsComputer =>
      !controller.isWhiteToMove // computer plays Black
    case GameMode.ComputerVsComputer => true

  private[aview] def triggerComputerMoveIfNeeded(): Unit =
    if (!computerScheduled && !paused && isComputerTurn && !isGameOver) {
      computerScheduled = true
      val delayMs =
        if (!moveDelayEnabled) 0L
        else if (gameMode == GameMode.ComputerVsComputer) 500L
        else 300L
      val t = new Thread(
        () => {
          Thread.sleep(delayMs)
          // Snapshot immutable state on background thread (Board is immutable — safe)
          val color = controller.activeColor
          val board = controller.board
          // Compute AI move on background thread — does NOT block the FX thread,
          // so Pekko's clock callbacks can fire freely via Platform.runLater
          // Derive a per-move time budget from the remaining game clock.
          // Aim to use roughly 1/30 of the remaining time, clamped to [200ms, 5s].
          val remainingMs: Long = clockMode match
            case ClockMode.Timed(initialMs, _, _) =>
              val elapsed =
                if (color == chess.model.Color.White) whiteElapsedMs
                else blackElapsedMs
              (initialMs - elapsed).max(0L)
            case _ => 60_000L // no-limit game: treat as 60 s remaining
          val moveBudgetMs = (remainingMs / 30).max(200L).min(5000L)

          val player =
            if color == chess.model.Color.White then whiteComputer
            else blackComputer
          player.strategy match
            case ids: IterativeDeepeningStrategy =>
              ids.timeLimitMs = moveBudgetMs
            case _ => ()

          val moveOpt =
            if (isComputerTurn && !isGameOver)
              player.move(board, color, controller.wouldBeThirdRepetition)
            else None
          // Only mutate UI/controller state on the FX thread
          Platform.runLater(() => {
            computerScheduled = false
            moveOpt.foreach { case (from, to, promo) =>
              if (isComputerTurn && !isGameOver) {
                selectedSquare = None
                controller.applyMove(from, to, promo)
                // update() is notified by the controller and will call
                // triggerComputerMoveIfNeeded() again for C vs C
              }
            }
          })
        },
        "computer-move"
      )
      t.setDaemon(true)
      t.start()
    }

  private[aview] def createBoardPane(): GridPane = {
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

      val dot = new scalafx.scene.shape.Circle {
        radius = 14
        fill = Color.color(0, 0, 0, 0.2)
        visible = false
        mouseTransparent = true
      }

      val stackPane = new StackPane {
        children = Seq(squareRect, dot, text)
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
      boardDots(row)(col) = dot
    }

    updateBoard()
    boardPane
  }

  private[aview] def createControlPanel(): VBox = {
    playerLabel = new Label("White to move") {
      font = Font.font("Arial", FontWeight.Bold, 16)
      padding = Insets(10)
    }

    // PGN section — label + inline copy-icon button
    val pgnLabel = new Label("Game PGN") {
      font = Font.font("Arial", FontWeight.Normal, 14)
    }

    val copyPgnButton = new Button("\uD83D\uDCCB") {
      style = "-fx-font-size: 13px; -fx-padding: 2px 6px;"
      tooltip = new Tooltip("Copy PGN to clipboard")
      onAction = _ => {
        val moves = controller.pgnMoves
        val pgn = moves.zipWithIndex
          .map { case (m, i) =>
            if (i % 2 == 0) s"${i / 2 + 1}. $m" else m
          }
          .mkString(" ")
        val content = new ClipboardContent()
        content.putString(pgn)
        Clipboard.getSystemClipboard.setContent(content)
        text = "\u2713"
        new Thread(() => {
          Thread.sleep(1500)
          Platform.runLater(() => text = "\uD83D\uDCCB")
        }).start()
      }
    }

    val dropHint = new Label("\u2318V or drop a file to load") {
      style =
        "-fx-font-size: 10px; -fx-font-style: italic; -fx-text-fill: #888888;"
    }
    scalafx.scene.layout.HBox
      .setHgrow(dropHint, scalafx.scene.layout.Priority.Always)
    dropHint.maxWidth = Double.MaxValue
    dropHint.alignment = Pos.CenterRight

    val pgnHeader = new HBox(6) {
      alignment = Pos.CenterLeft
      padding = Insets(10, 0, 5, 0)
      children = Seq(pgnLabel, copyPgnButton, dropHint)
    }

    pgnDisplay = new TextFlow {
      padding = Insets(5)
      prefWidth = 250
    }

    pgnScrollPane = new ScrollPane {
      content = pgnDisplay
      prefHeight = 320
      fitToWidth = true
      style = "-fx-background-color: white;"
    }

    val backButton = new Button("\u25C0 Back") {
      prefWidth = 120
      style = "-fx-font-size: 13px; -fx-padding: 8px;"
      onAction = _ => {
        controller.backward()
        selectedSquare = None
      }
    }

    val forwardButton = new Button("Forward \u25B6") {
      prefWidth = 120
      style = "-fx-font-size: 13px; -fx-padding: 8px;"
      onAction = _ => {
        controller.forward()
        selectedSquare = None
      }
    }

    val navButtonBox = new HBox(10) {
      alignment = Pos.Center
      children = Seq(backButton, forwardButton)
    }

    // FEN display — styled to match the PGN area
    val fenLabel = new Label("FEN") {
      font = Font.font("Arial", FontWeight.Normal, 14)
    }

    val copyFenButton = new Button("\uD83D\uDCCB") {
      style = "-fx-font-size: 13px; -fx-padding: 2px 6px;"
      tooltip = new Tooltip("Copy FEN to clipboard")
      onAction = _ => {
        val content = new ClipboardContent()
        content.putString(controller.getBoardAsFEN)
        Clipboard.getSystemClipboard.setContent(content)
        text = "\u2713"
        new Thread(() => {
          Thread.sleep(1500)
          Platform.runLater(() => text = "\uD83D\uDCCB")
        }).start()
      }
    }

    val fenHeader = new HBox(6) {
      alignment = Pos.CenterLeft
      padding = Insets(10, 0, 5, 0)
      children = Seq(fenLabel, copyFenButton)
    }

    fenText = new scalafx.scene.text.Text {
      font = Font.font("Monospaced", FontWeight.Normal, 11)
      text = controller.getBoardAsFEN
    }

    val fenTextFlow = new TextFlow {
      padding = Insets(5)
      prefWidth = 250
      children = Seq(fenText)
    }

    val fenScrollPane = new ScrollPane {
      content = fenTextFlow
      prefHeight = 55
      fitToWidth = true
      style = "-fx-background-color: white;"
    }

    // New Game button (yellow)
    val resetButton = new Button("\u2605 New Game") {
      prefWidth = 120
      style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #f1c40f; -fx-text-fill: #333333; -fx-font-weight: bold;"
      onAction = _ => {
        paused = false
        gameMode = GameMode.HumanVsHuman
        gameOverByTimeout = false
        updatePauseButtonVisibility()
        updatePauseButton()
        resetClock()
        lastPgnLength = 0
        controller.announceInitial(Board.initial)
        selectedSquare = None
        pgnDisplay.children.clear()
        if (puzzleInfoLabel != null) {
          puzzleInfoLabel.visible = false
          puzzleInfoLabel.managed = false
        }
        if (puzzleLink != null) {
          puzzleLink.visible = false
          puzzleLink.managed = false
        }
      }
    }

    // Run button (green) — visible in non-CvC modes; switches to CvC and starts play
    runButton = new Button("\u25B6 Run") {
      prefWidth = 120
      style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;"
      onAction = _ => {
        gameMode = GameMode.ComputerVsComputer
        updatePauseButtonVisibility()
        triggerComputerMoveIfNeeded()
      }
    }

    // Pause / Continue button (orange when running, green when paused)
    pauseButton = new Button("\u23F8 Pause") {
      prefWidth = 120
      style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;"
      onAction = _ => {
        paused = !paused
        updatePauseButton()
        if (paused) {
          if (clockSystem != null) clockSystem ! ClockActor.Stop
        } else {
          if (!controller.isAtLatest) {
            controller.goToMove(controller.boardStates.length - 1)
            selectedSquare = None
          }
          if (clockStarted && clockSystem != null)
            clockSystem ! ClockActor.Start
          triggerComputerMoveIfNeeded()
        }
      }
    }

    gameButtonBox = new HBox(10) {
      alignment = Pos.Center
      children = Seq(resetButton, runButton, pauseButton)
    }
    pauseButton.visible = false
    pauseButton.managed = false

    puzzleInfoLabel = new Label("") {
      wrapText = true
      maxWidth = 260
      style = "-fx-font-size: 11px; -fx-text-fill: #555555;"
      visible = false
      managed = false
    }

    puzzleLink = new scalafx.scene.control.Hyperlink("") {
      wrapText = true
      maxWidth = 260
      style = "-fx-font-size: 11px;"
      visible = false
      managed = false
      onAction = _ => {
        val url = text.value
        if (url.nonEmpty && Desktop.isDesktopSupported)
          Try(Desktop.getDesktop.browse(new URI(url)))
      }
    }

    val panelNormalStyle =
      "-fx-border-color: #cccccc; -fx-border-width: 0 0 0 1;"
    val panelDropStyle =
      "-fx-border-color: #27ae60; -fx-border-width: 2; -fx-background-color: rgba(39,174,96,0.05);"

    val controlPanel = new VBox(10) {
      padding = Insets(20)
      prefWidth = 300
      style = panelNormalStyle
      children = Seq(
        playerLabel,
        new Separator(),
        pgnHeader,
        pgnScrollPane,
        navButtonBox,
        new Separator(),
        fenHeader,
        fenScrollPane,
        new Separator(),
        gameButtonBox,
        puzzleInfoLabel,
        puzzleLink
      )
    }

    // DnD at parent level: fires in the capture phase BEFORE any child skin filter
    // (including TextArea's own text-insertion handler), so we always win the race.
    controlPanel.delegate.addEventFilter(
      DragEvent.DRAG_OVER,
      (e: DragEvent) => {
        if (e.getDragboard.hasString || e.getDragboard.hasFiles)
          e.acceptTransferModes(TransferMode.ANY*)
        e.consume()
      }
    )
    controlPanel.delegate.addEventFilter(
      DragEvent.DRAG_ENTERED,
      (e: DragEvent) => {
        if (e.getDragboard.hasString || e.getDragboard.hasFiles)
          controlPanel.style = panelDropStyle
        // do not consume — lets children still see DRAG_ENTERED for cursor feedback
      }
    )
    controlPanel.delegate.addEventFilter(
      DragEvent.DRAG_EXITED,
      (e: DragEvent) => {
        controlPanel.style = panelNormalStyle
      }
    )
    controlPanel.delegate.addEventFilter(
      DragEvent.DRAG_DROPPED,
      (e: DragEvent) => {
        val db = e.getDragboard
        val content: Option[String] =
          if (db.hasString && db.getString.trim.nonEmpty)
            Some(db.getString.trim)
          else if (db.hasFiles) Try(readFromFile(db.getFiles.get(0))).toOption
          else None
        controlPanel.style = panelNormalStyle
        content.foreach { txt =>
          loadPgnOrFen(txt); selectedSquare = None
        }
        e.setDropCompleted(content.isDefined)
        e.consume()
      }
    )

    controlPanel
  }

  /** Update pause button label/colour to reflect current paused state. */
  private[aview] def updatePauseButton(): Unit =
    if (pauseButton == null) return
    if (paused) {
      pauseButton.text = "\u25B6 Continue"
      pauseButton.style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;"
    } else {
      pauseButton.text = "\u23F8 Pause"
      pauseButton.style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;"
    }

  /** Show/hide Pause and Run buttons depending on whether C vs C mode is
    * active.
    */
  private[aview] def updatePauseButtonVisibility(): Unit =
    if (pauseButton == null || runButton == null) return
    val cvc = gameMode == GameMode.ComputerVsComputer
    pauseButton.visible = cvc
    pauseButton.managed = cvc
    runButton.visible = !cvc
    runButton.managed = !cvc
    if (!cvc) {
      paused = false
      updatePauseButton()
    }

  private def updatePgnDisplay(): Unit = {
    pgnDisplay.children.clear()
    val moves = controller.pgnMoves
    val activeIdx =
      controller.currentIndex - 1 // index of the move that produced the current board
    var activeNode = Option.empty[Text]

    moves.zipWithIndex.foreach { case (move, i) =>
      if (i % 2 == 0) {
        pgnDisplay.children.add(new Text(s"${i / 2 + 1}. ") {
          font = Font.font("monospace", FontWeight.Normal, 13)
        })
      }

      val isActive = i == activeIdx
      val moveText = new Text(move + " ") {
        font = Font.font(
          "monospace",
          if (isActive) FontWeight.Bold else FontWeight.Normal,
          13
        )
        fill = if (isActive) Color.web("#1565C0") else Color.Black
        style = "-fx-cursor: hand;"

        onMouseEntered = _ => if (!isActive) fill = Color.web("#1565C0")
        onMouseExited = _ => if (!isActive) fill = Color.Black
        onMouseClicked = _ => {
          controller.goToMove(i + 1)
          selectedSquare = None
        }
      }
      pgnDisplay.children.add(moveText)
      if (isActive) activeNode = Some(moveText)
    }

    // Scroll so the active move is visible; fall back to bottom if none
    Platform.runLater(() => {
      activeNode match {
        case Some(node) =>
          val totalH = pgnDisplay.delegate.prefHeight(-1)
          val viewH = pgnScrollPane.height.value
          if (totalH > viewH) {
            val nodeY = node.delegate.getBoundsInParent.getMinY
            pgnScrollPane.vvalue = Math.min(1.0, nodeY / (totalH - viewH))
          }
        case None =>
          pgnScrollPane.vvalue = 1.0
      }
    })
  }

  private[aview] def createCapturedPanel(): HBox = {
    blackCapturesBox = new HBox(3) { padding = Insets(2, 0, 2, 0) }
    whiteCapturesBox = new HBox(3) { padding = Insets(2, 0, 2, 0) }
    materialLabel = new Label("=") {
      font = Font.font("Arial", FontWeight.Bold, 13)
      maxWidth = Double.MaxValue
      alignment = Pos.Center
    }

    val capturesVBox = new VBox(2) {
      padding = Insets(4, 20, 8, 20)
      children = Seq(blackCapturesBox, materialLabel, whiteCapturesBox)
    }
    scalafx.scene.layout.HBox
      .setHgrow(capturesVBox, scalafx.scene.layout.Priority.Always)

    blackClockLabel = new Label("--:--") {
      font = Font.font("Monospaced", FontWeight.Bold, 30)
      padding = Insets(2, 16, 0, 16)
      alignment = Pos.CenterRight
    }
    whiteClockLabel = new Label("--:--") {
      font = Font.font("Monospaced", FontWeight.Bold, 30)
      padding = Insets(0, 16, 2, 16)
      alignment = Pos.CenterRight
    }

    blackStrategyLabel = new Label(blackComputer.strategy.name) {
      font = Font.font("Arial", 9)
      padding = Insets(0, 16, 0, 16)
      alignment = Pos.CenterRight
    }
    whiteStrategyLabel = new Label(whiteComputer.strategy.name) {
      font = Font.font("Arial", 9)
      padding = Insets(0, 16, 0, 16)
      alignment = Pos.CenterRight
    }

    val clockVBox = new VBox(0) {
      alignment = Pos.CenterRight
      padding = Insets(4, 8, 4, 0)
      children = Seq(
        new Label("BLACK") {
          font = Font.font("Arial", FontWeight.Bold, 10);
          padding = Insets(0, 16, 0, 16)
        },
        blackStrategyLabel,
        blackClockLabel,
        new Label("WHITE") {
          font = Font.font("Arial", FontWeight.Bold, 10);
          padding = Insets(4, 16, 0, 16)
        },
        whiteStrategyLabel,
        whiteClockLabel
      )
    }

    updateCapturedPanel()
    updateClockDisplay()

    new HBox {
      style =
        "-fx-background-color: #ebebeb; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;"
      children = Seq(capturesVBox, clockVBox)
    }
  }

  private def updateCapturedPanel(): Unit = {
    val startingCounts = Map(
      Role.Pawn -> 8,
      Role.Knight -> 2,
      Role.Bishop -> 2,
      Role.Rook -> 2,
      Role.Queen -> 1
    )
    val pieceValues = Map(
      Role.Pawn -> 1,
      Role.Knight -> 3,
      Role.Bishop -> 3,
      Role.Rook -> 5,
      Role.Queen -> 9
    )
    val displayRoles =
      Seq(Role.Queen, Role.Rook, Role.Bishop, Role.Knight, Role.Pawn)

    val liveCounts = controller.board.squares.flatten.flatten
      .groupBy(p => (p.color, p.role))
      .map((k, v) => k -> v.size)

    def captured(color: ChessColor, role: Role): Int =
      (startingCounts(role) - liveCounts.getOrElse((color, role), 0)).max(0)

    def pieceSymbols(color: ChessColor): Seq[Text] =
      displayRoles.flatMap { role =>
        Seq.fill(captured(color, role))(new Text(Piece(role, color).toString) {
          font = Font.font("Arial", FontWeight.Normal, 28)
          fill = Color.Black
        })
      }

    // Pieces black captured = white pieces removed from the board
    val blackCaptures = pieceSymbols(ChessColor.White)
    // Pieces white captured = black pieces removed from the board
    val whiteCaptures = pieceSymbols(ChessColor.Black)

    blackCapturesBox.children.clear()
    blackCaptures.foreach(t => blackCapturesBox.children.add(t))
    whiteCapturesBox.children.clear()
    whiteCaptures.foreach(t => whiteCapturesBox.children.add(t))

    val whiteMaterial =
      displayRoles.map(r => captured(ChessColor.Black, r) * pieceValues(r)).sum
    val blackMaterial =
      displayRoles.map(r => captured(ChessColor.White, r) * pieceValues(r)).sum
    val adv = whiteMaterial - blackMaterial
    materialLabel.text =
      if adv > 0 then s"White +$adv"
      else if adv < 0 then s"Black +${-adv}"
      else "="
  }

  // ── Clock methods ──────────────────────────────────────────────────────────

  private def formatTime(ms: Long): String =
    val total = (ms / 1000).max(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    if h > 0 then f"$h%d:$m%02d:$s%02d" else f"$m%02d:$s%02d"

  private[aview] def updateStrategyLabels(): Unit =
    if (whiteStrategyLabel != null)
      whiteStrategyLabel.text = whiteComputer.strategy.name
    if (blackStrategyLabel != null)
      blackStrategyLabel.text = blackComputer.strategy.name

  private def updateClockDisplay(): Unit =
    if (whiteClockLabel == null || blackClockLabel == null) return
    clockMode match
      case ClockMode.NoLimit =>
        whiteClockLabel.text = formatTime(whiteElapsedMs)
        blackClockLabel.text = formatTime(blackElapsedMs)
      case ClockMode.Timed(initialMs, _, _) =>
        val wr = (initialMs - whiteElapsedMs).max(0)
        val br = (initialMs - blackElapsedMs).max(0)
        whiteClockLabel.text = formatTime(wr)
        blackClockLabel.text = formatTime(br)
    highlightActiveClockLabel()

  private def highlightActiveClockLabel(): Unit =
    if (whiteClockLabel == null || blackClockLabel == null) return
    def colorFor(elapsedMs: Long): String = clockMode match
      case ClockMode.Timed(initialMs, _, _)
          if (initialMs - elapsedMs) < 30000 =>
        "#e74c3c"
      case _ => "#333333"
    val activeBase = "-fx-background-color: #d5e8d4; -fx-background-radius: 4;"
    val inactiveBase = "-fx-background-color: transparent;"
    if controller.isWhiteToMove then
      whiteClockLabel.style =
        s"-fx-text-fill: ${colorFor(whiteElapsedMs)}; $activeBase"
      blackClockLabel.style = s"-fx-text-fill: #888888; $inactiveBase"
    else
      blackClockLabel.style =
        s"-fx-text-fill: ${colorFor(blackElapsedMs)}; $activeBase"
      whiteClockLabel.style = s"-fx-text-fill: #888888; $inactiveBase"

  /** Switch the active clock after a real move and start it if not yet running.
    */
  private def switchClock(): Unit =
    val incMs = clockMode match
      case ClockMode.Timed(_, inc, _) => inc
      case _                          => 0L
    if !clockStarted then
      clockStarted = true
      clockSystem ! ClockActor.Start
    clockSystem ! ClockActor.SwitchSide(incMs)
    if isGameOver then clockSystem ! ClockActor.Stop
    highlightActiveClockLabel()

  /** Reset both clocks; stops ticking and zeroes elapsed times. */
  private[aview] def resetClock(): Unit =
    whiteElapsedMs = 0
    blackElapsedMs = 0
    clockStarted = false
    if clockSystem != null then clockSystem ! ClockActor.Reset
    updateClockDisplay()

  /** Change clock mode (resets both clocks). */
  private[aview] def applyClockMode(mode: ClockMode): Unit =
    clockMode = mode
    val (initMs, incMs) = mode match
      case ClockMode.Timed(i, inc, _) => (Some(i), inc)
      case ClockMode.NoLimit          => (None, 0L)
    if clockSystem != null then clockSystem ! ClockActor.SetMode(initMs, incMs)
    whiteElapsedMs = 0
    blackElapsedMs = 0
    clockStarted = false
    updateClockDisplay()

  private def handleTimeout(color: chess.model.Color): Unit =
    val winner = if color == chess.model.Color.White then "Black" else "White"
    gameOverByTimeout = true
    gameMode = GameMode.HumanVsHuman
    updatePauseButtonVisibility()
    if clockSystem != null then clockSystem ! ClockActor.Stop
    if playerLabel != null then
      playerLabel.text = s"Time out! $winner wins."

  /** Spawn the ClockActor inside its own ActorSystem. Callbacks marshal to the
    * JavaFX thread via Platform.runLater.
    */
  private[aview] def initClockActor(): Unit =
    clockSystem = ActorSystem(
      ClockActor(
        onUpdate = (wMs, bMs) =>
          Platform.runLater { () =>
            whiteElapsedMs = wMs
            blackElapsedMs = bMs
            updateClockDisplay()
          },
        onTimeout = color =>
          Platform.runLater { () =>
            handleTimeout(color)
          }
      ),
      "chess-clock"
    )

  private[aview] def updateBoard(): Unit = {
    // Compute legal target squares once for the selected piece
    val legalTargets: Set[Square] = if (showLegalMoves) {
      selectedSquare
        .map { sel =>
          controller.board
            .legalMoves(controller.activeColor)
            .collect { case (from, to) if from == sel => to }
            .toSet
        }
        .getOrElse(Set.empty)
    } else Set.empty

    // Determine if the active king is in check or checkmate
    val activeColor = controller.activeColor
    val kingInDanger: Option[Square] =
      if (controller.isCheckmate || controller.isInCheck)
        controller.board.findKing(activeColor)
      else None
    val isCheckmate = controller.isCheckmate

    for {
      rank <- Rank.all
      file <- File.all
    } {
      val row = 8 - rank.index
      val col = file.index - 1
      val sq = Square(file, rank)

      val squareRect = boardSquares(row)(col)
      val text = boardLabels(row)(col)
      val dot = boardDots(row)(col)

      // Update square color
      val isLight = (file.index + rank.index) % 2 == 0
      val baseColor =
        if (isLight) Color.web("#f0d9b5") else Color.web("#b58863")

      val lastMoveSquares: Set[Square] = controller.board.lastMove match {
        case Some((from, to)) => Set(from, to)
        case None             => Set.empty
      }
      val lastMoveColor =
        if (isLight) Color.web("#cdd16e") else Color.web("#aaa23a")

      squareRect.fill = selectedSquare match {
        case Some(sel) if sel == sq => Color.web("#baca44")
        case _ if kingInDanger.contains(sq) =>
          if (isCheckmate) Color.web("#c0392b") else Color.web("#e74c3c")
        case _ if lastMoveSquares.contains(sq) => lastMoveColor
        case _                                 => baseColor
      }

      // Update move-hint dot
      if (legalTargets.contains(sq)) {
        val isCapture = controller.board.pieceAt(sq).isDefined
        if (isCapture) {
          // Large semi-transparent ring for capture squares
          dot.radius = 36
          dot.fill = Color.color(0, 0, 0, 0)
          dot.stroke = Color.color(0, 0, 0, 0.25)
          dot.strokeWidth = 6
        } else {
          // Small filled dot for empty target squares
          dot.radius = 14
          dot.fill = Color.color(0, 0, 0, 0.2)
          dot.stroke = Color.color(0, 0, 0, 0)
          dot.strokeWidth = 0
        }
        dot.visible = true
      } else {
        dot.visible = false
      }

      // Update piece
      text.text = controller.board.pieceAt(sq) match {
        case Some(piece) => piece.toString
        case None        => ""
      }
    }
  }

  private def handleSquareClick(square: Square): Unit = {
    if (isComputerTurn || isGameOver) return
    selectedSquare match {
      case None =>
        controller.board.pieceAt(square) match {
          case Some(piece)
              if piece.color == (if (controller.isWhiteToMove) ChessColor.White
                                 else ChessColor.Black) =>
            selectedSquare = Some(square)
          case _ => ()
        }
      case Some(fromSquare) =>
        if (square == fromSquare) {
          selectedSquare = None
        } else {
          // Detect promotion before applying: pawn moving to the back rank
          val needsPromotion =
            controller.board.pieceAt(fromSquare).exists { p =>
              p.role == Role.Pawn &&
              ((p.color == ChessColor.White && square.rank == Rank._8) ||
                (p.color == ChessColor.Black && square.rank == Rank._1))
            }
          val promotion: Option[PromotableRole] =
            if needsPromotion then showPromotionDialog() else None

          // If the user cancelled the promotion dialog, deselect and stop
          if needsPromotion && promotion.isEmpty then selectedSquare = None
          else
            controller.applyMove(fromSquare, square, promotion) match {
              case _: MoveResult.Moved =>
                selectedSquare = None
              // Board/label/FEN updated by observer update()
              case MoveResult.Failed(_, error) =>
                // Show error briefly in the player label
                playerLabel.text = error match {
                  case MoveError.LeavesKingInCheck =>
                    "Illegal: move leaves king in check!"
                  case MoveError.WrongColor  => "That's not your piece!"
                  case MoveError.NoPiece     => "No piece on that square!"
                  case MoveError.InvalidMove => "That piece can't move there!"
                  case MoveError.PromotionRequired => "Promotion required!"
                  case MoveError.ParseError(msg)   => s"Error: $msg"
                }
                // Try to re-select if clicking on own piece
                controller.board.pieceAt(square) match {
                  case Some(piece)
                      if piece.color == (if (controller.isWhiteToMove)
                                           ChessColor.White
                                         else ChessColor.Black) =>
                    selectedSquare = Some(square)
                  case _ =>
                    selectedSquare = None
                }
            }
        }
    }
  }

  private[aview] def loadPgnOrFen(input: String): Unit = {
    // Heuristic: FEN strings contain '/' for rank separators and typically
    // have 7 slashes (8 ranks). PGN move text does not.
    val isFen = input.count(_ == '/') >= 7 && !input.contains('\n')

    if (isFen) {
      controller.loadFromFEN(input) match {
        case Right(_) =>
          Platform.runLater(() => playerLabel.text = "FEN imported.")
        case Left(error) =>
          showAlert("Invalid FEN", error)
      }
    } else {
      controller.loadPgnMoves(input) match {
        case Right(_) =>
          Platform.runLater(() => playerLabel.text = "PGN imported.")
        case Left(error) =>
          showAlert("Invalid PGN", error)
      }
    }
  }

  private def writeToFile(file: java.io.File, content: String): Unit = {
    val pw = new PrintWriter(file)
    try pw.write(content)
    finally pw.close()
  }

  private def readFromFile(file: java.io.File): String = {
    val source = Source.fromFile(file)
    try source.mkString
    finally source.close()
  }

  private[aview] def createMenuBar(): MenuBar = {
    val circeFileIO: FileIO = new CirceJsonFileIO
    val upickleFileIO: FileIO = new UPickleJsonFileIO

    val jsonFilter = new ExtensionFilter("JSON Files", "*.json")
    val fenFilter = new ExtensionFilter("FEN Files", "*.fen")
    val pgnFilter = new ExtensionFilter("PGN Files", "*.pgn")

    def exportJson(fileIO: FileIO, label: String): Unit = {
      val chooser = new FileChooser {
        title = s"Export JSON ($label)"
        extensionFilters.add(jsonFilter)
        initialFileName = "board.json"
      }
      val file = chooser.showSaveDialog(primaryStage)
      if (file != null) {
        Try { writeToFile(file, fileIO.save(controller.board)) } match {
          case Success(_) =>
            showInfo(
              "Export Successful",
              s"Board exported to ${file.getName} using $label."
            )
          case Failure(e) =>
            showAlert("Export Failed", e.getMessage)
        }
      }
    }

    def importJson(fileIO: FileIO, label: String): Unit = {
      val chooser = new FileChooser {
        title = s"Import JSON ($label)"
        extensionFilters.add(jsonFilter)
      }
      val file = chooser.showOpenDialog(primaryStage)
      if (file != null) {
        Try(readFromFile(file)).flatMap(fileIO.load) match {
          case Success(board) =>
            controller.announceInitial(board)
            selectedSquare = None
            showInfo(
              "Import Successful",
              s"Board imported from ${file.getName} using $label."
            )
          case Failure(e) =>
            showAlert("Import Failed", e.getMessage)
        }
      }
    }

    def exportFEN(): Unit = {
      val chooser = new FileChooser {
        title = "Export FEN"
        extensionFilters.add(fenFilter)
        initialFileName = "position.fen"
      }
      val file = chooser.showSaveDialog(primaryStage)
      if (file != null) {
        Try { writeToFile(file, controller.getBoardAsFEN) } match {
          case Success(_) =>
            showInfo("Export Successful", s"FEN exported to ${file.getName}.")
          case Failure(e) =>
            showAlert("Export Failed", e.getMessage)
        }
      }
    }

    def importFEN(): Unit = {
      val chooser = new FileChooser {
        title = "Import FEN"
        extensionFilters.add(fenFilter)
      }
      val file = chooser.showOpenDialog(primaryStage)
      if (file != null) {
        Try(readFromFile(file).trim).toEither.left
          .map(_.getMessage)
          .flatMap(controller.loadFromFEN) match {
          case Right(_) =>
            selectedSquare = None
            showInfo("Import Successful", s"FEN imported from ${file.getName}.")
          case Left(e) =>
            showAlert("Import Failed", e)
        }
      }
    }

    def exportPGN(): Unit = {
      val chooser = new FileChooser {
        title = "Export PGN"
        extensionFilters.add(pgnFilter)
        initialFileName = "game.pgn"
      }
      val file = chooser.showSaveDialog(primaryStage)
      if (file != null) {
        Try { writeToFile(file, controller.pgnText) } match {
          case Success(_) =>
            showInfo("Export Successful", s"PGN exported to ${file.getName}.")
          case Failure(e) =>
            showAlert("Export Failed", e.getMessage)
        }
      }
    }

    def importPGN(): Unit = {
      val chooser = new FileChooser {
        title = "Import PGN"
        extensionFilters.add(pgnFilter)
      }
      val file = chooser.showOpenDialog(primaryStage)
      if (file != null) {
        Try(readFromFile(file)).toEither.left
          .map(_.getMessage)
          .flatMap(controller.loadPgnMoves) match {
          case Right(_) =>
            selectedSquare = None
            showInfo("Import Successful", s"PGN imported from ${file.getName}.")
          case Left(e) =>
            showAlert("Import Failed", e)
        }
      }
    }

    val importMenu = new Menu("Import") {
      items = Seq(
        new MenuItem("JSON (Circe)") {
          onAction = _ => importJson(circeFileIO, "Circe")
        },
        new MenuItem("JSON (uPickle)") {
          onAction = _ => importJson(upickleFileIO, "uPickle")
        },
        new SeparatorMenuItem(),
        new MenuItem("FEN") { onAction = _ => importFEN() },
        new MenuItem("PGN") { onAction = _ => importPGN() }
      )
    }
    val exportMenu = new Menu("Export") {
      items = Seq(
        new MenuItem("JSON (Circe)") {
          onAction = _ => exportJson(circeFileIO, "Circe")
        },
        new MenuItem("JSON (uPickle)") {
          onAction = _ => exportJson(upickleFileIO, "uPickle")
        },
        new SeparatorMenuItem(),
        new MenuItem("FEN") { onAction = _ => exportFEN() },
        new MenuItem("PGN") { onAction = _ => exportPGN() }
      )
    }

    val fileMenu = new Menu("File") {
      items = Seq(importMenu, exportMenu)
    }

    val showMovesItem = new CheckMenuItem("Show Legal Moves") {
      selected = showLegalMoves
      onAction = _ => {
        showLegalMoves = selected.value
        updateBoard()
      }
    }

    val viewMenu = new Menu("View") {
      items = Seq(showMovesItem)
    }

    // Game mode toggle group
    val modeGroup = new ToggleGroup()

    val hvhItem = new RadioMenuItem("Human vs Human") {
      toggleGroup = modeGroup
      selected = true
      onAction = _ =>
        if (selected.value) {
          gameMode = GameMode.HumanVsHuman
          updatePauseButtonVisibility()
        }
    }
    val hvcItem = new RadioMenuItem("Human vs Computer") {
      toggleGroup = modeGroup
      onAction = _ =>
        if (selected.value) {
          gameMode = GameMode.HumanVsComputer
          updatePauseButtonVisibility()
          triggerComputerMoveIfNeeded()
        }
    }
    val cvcItem = new RadioMenuItem("Computer vs Computer") {
      toggleGroup = modeGroup
      onAction = _ =>
        if (selected.value) {
          gameMode = GameMode.ComputerVsComputer
          updatePauseButtonVisibility()
          triggerComputerMoveIfNeeded()
        }
    }

    val delayItem = new CheckMenuItem("Move Delay (CvC: 500ms / HvC: 300ms)") {
      selected = moveDelayEnabled
      onAction = _ => moveDelayEnabled = selected.value
    }

    val gameMenu = new Menu("Game") {
      items = Seq(hvhItem, hvcItem, cvcItem, new SeparatorMenuItem(), delayItem)
    }

    // Per-player strategy selection
    val strategies: Seq[(String, () => MoveStrategy)] = Seq(
      "Random" -> (() => new RandomStrategy()),
      "Greedy" -> (() => new GreedyStrategy()),
      "Material Balance" -> (() => new MaterialBalanceStrategy()),
      "Piece-Square Tables" -> (() => new PieceSquareStrategy()),
      "Minimax (d=2)" -> (() => new MinimaxStrategy(2)),
      "Minimax (d=3)" -> (() => new MinimaxStrategy(3)),
      "Minimax (d=4)" -> (() => new MinimaxStrategy(4)),
      "Minimax+QSearch (d=3)" -> (() => new QuiescenceStrategy(3)),
      "Minimax+QSearch (d=4)" -> (() => new QuiescenceStrategy(4)),
      "Iterative Deepening" -> (() => new IterativeDeepeningStrategy())
    )

    def makeStrategySubmenu(label: String, player: ComputerPlayer): Menu = {
      val group = new ToggleGroup()
      val items = strategies.map { case (name, factory) =>
        new RadioMenuItem(name) {
          toggleGroup = group
          selected = player.strategy.name == name
          onAction = _ =>
            if (selected.value) {
              player.strategy = factory(); updateStrategyLabels()
            }
        }
      }
      val m = new Menu(label); m.items = items; m
    }

    val strategyMenu = new Menu("Strategy") {
      items = Seq(
        makeStrategySubmenu("White", whiteComputer),
        makeStrategySubmenu("Black", blackComputer)
      )
    }

    // Clock mode menu
    val clockModes: Seq[(String, ClockMode)] = Seq(
      "No Limit" -> ClockMode.NoLimit,
      "Bullet  1+0" -> ClockMode.Timed(1 * 60 * 1000L, 0, "Bullet 1+0"),
      "Bullet  2+1" -> ClockMode.Timed(2 * 60 * 1000L, 1 * 1000L, "Bullet 2+1"),
      "Blitz   3+0" -> ClockMode.Timed(3 * 60 * 1000L, 0, "Blitz 3+0"),
      "Blitz   3+2" -> ClockMode.Timed(3 * 60 * 1000L, 2 * 1000L, "Blitz 3+2"),
      "Blitz   5+0" -> ClockMode.Timed(5 * 60 * 1000L, 0, "Blitz 5+0"),
      "Blitz   5+3" -> ClockMode.Timed(5 * 60 * 1000L, 3 * 1000L, "Blitz 5+3"),
      "Rapid  10+0" -> ClockMode.Timed(10 * 60 * 1000L, 0, "Rapid 10+0"),
      "Rapid  15+10" -> ClockMode
        .Timed(15 * 60 * 1000L, 10 * 1000L, "Rapid 15+10"),
      "Classical 30+0" -> ClockMode.Timed(30 * 60 * 1000L, 0, "Classical 30+0")
    )
    val clockGroup = new ToggleGroup()
    val clockItems = clockModes.zipWithIndex.map { case ((label, mode), idx) =>
      new RadioMenuItem(label) {
        toggleGroup = clockGroup
        selected = idx == 0
        onAction = _ =>
          if (selected.value) {
            applyClockMode(mode)
            updateClockDisplay()
          }
      }
    }
    val clockMenu = new Menu("Clock") {
      items = clockItems
    }

    // ── Puzzles menu — grouped by theme, sorted alphabetically ──────────────
    val puzzlesByTheme: Seq[(String, Vector[Puzzle])] =
      puzzles
        .flatMap(p => p.themes.map(t => t -> p))
        .groupMap(_._1)(_._2)
        .toSeq
        .sortBy(_._1)

    val puzzleThemeMenus = puzzlesByTheme.map { case (theme, ps) =>
      val themeMenu = new Menu(s"$theme  (${ps.length})")
      themeMenu.items = ps.sortBy(_.rating).map { p =>
        new MenuItem(s"#${p.id}   \u2605${p.rating}  [${p.themes.mkString(", ")}]") {
          onAction = _ => {
            controller.loadFromFEN(p.fen)
            gameMode = GameMode.HumanVsHuman
            updatePauseButtonVisibility()
            selectedSquare = None
            if (puzzleInfoLabel != null) {
              puzzleInfoLabel.text = s"#${p.id}  \u2605${p.rating}  ${p.themes.mkString(", ")}"
              puzzleInfoLabel.visible = true
              puzzleInfoLabel.managed = true
            }
            if (puzzleLink != null) {
              puzzleLink.text = p.gameUrl
              puzzleLink.visited = false
              puzzleLink.visible = true
              puzzleLink.managed = true
            }
          }
        }
      }
      themeMenu
    }

    val puzzlesMenu = new Menu("Puzzles") {
      items = puzzleThemeMenus
    }

    new MenuBar {
      menus = Seq(fileMenu, gameMenu, strategyMenu, clockMenu, viewMenu, puzzlesMenu)
    }
  }

  private def showPromotionDialog(): Option[PromotableRole] = {
    val queenBtn = new ButtonType("♛ Queen")
    val rookBtn = new ButtonType("♜ Rook")
    val bishopBtn = new ButtonType("♝ Bishop")
    val knightBtn = new ButtonType("♞ Knight")
    new Alert(Alert.AlertType.Confirmation) {
      initOwner(primaryStage)
      title = "Pawn Promotion"
      headerText = "Choose a piece to promote to:"
      buttonTypes =
        Seq(queenBtn, rookBtn, bishopBtn, knightBtn, ButtonType.Cancel)
    }.showAndWait() match {
      case Some(btn) if btn == queenBtn  => Some(PromotableRole.Queen)
      case Some(btn) if btn == rookBtn   => Some(PromotableRole.Rook)
      case Some(btn) if btn == bishopBtn => Some(PromotableRole.Bishop)
      case Some(btn) if btn == knightBtn => Some(PromotableRole.Knight)
      case _                             => None
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

  private def showInfo(infoTitle: String, message: String): Unit = {
    new Alert(Alert.AlertType.Information) {
      initOwner(primaryStage)
      title = infoTitle
      headerText = None
      contentText = message
    }.showAndWait()
  }
}

object ChessGUI {
  private[aview] var instance: ChessGUI = uninitialized

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
    gui.primaryStage.scene = new Scene(1050, 900) {
      root = new BorderPane {
        top = gui.createMenuBar()
        center = new VBox {
          children = Seq(gui.createBoardPane(), gui.createCapturedPanel())
        }
        right = gui.createControlPanel()
        style = "-fx-background-color: #f5f5f5;"
      }
    }
    gui.primaryStage.scene.delegate.getValue.addEventFilter(
      javafx.scene.input.KeyEvent.KEY_PRESSED,
      (event: javafx.scene.input.KeyEvent) => {
        event.getCode match {
          case javafx.scene.input.KeyCode.LEFT =>
            gui.controller.backward(); gui.selectedSquare = None
          case javafx.scene.input.KeyCode.RIGHT =>
            gui.controller.forward(); gui.selectedSquare = None
          case javafx.scene.input.KeyCode.V if event.isShortcutDown =>
            // Read clipboard directly — works regardless of which node has focus
            val cb = Clipboard.getSystemClipboard
            if (cb.hasString) {
              val txt = cb.getString.trim
              if (txt.nonEmpty) {
                Platform.runLater(() => {
                  gui.loadPgnOrFen(txt); gui.selectedSquare = None
                })
              }
            }
            event.consume()
          case _ => ()
        }
      }
    )

    // Mark GUI as initialized — observer update() calls are now safe
    gui.initialized = true
    gui.initClockActor()

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
