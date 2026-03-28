package chess.view

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
import chess.controller.strategy.{RandomStrategy, GreedyStrategy, MaterialBalanceStrategy}
import chess.model.{Board, Piece, PromotableRole, Role, Square, File, Rank, MoveResult, MoveError, GameEvent}
import chess.model.{Color => ChessColor}
import chess.io.FileIO
import chess.io.json.circe.CirceJsonFileIO
import chess.io.json.upickle.UPickleJsonFileIO
import chess.AppBindings.given
import chess.util.Observer
import scala.compiletime.uninitialized
import javafx.application.{Application, Platform}
import javafx.application.Platform.{setImplicitExit}
import java.awt.Desktop
import java.io.{File => JFile, PrintWriter}
import scala.io.Source
import scala.util.{Try, Success, Failure}

class ChessGUI(val controller: GameController) extends Observer[MoveResult] {
  controller.add(this)

  private[view] var selectedSquare: Option[Square] = None
  private var boardSquares: Array[Array[Rectangle]] =
    Array.ofDim[Rectangle](8, 8)
  private var boardLabels: Array[Array[Text]] = Array.ofDim[Text](8, 8)
  private var boardDots: Array[Array[scalafx.scene.shape.Circle]] =
    Array.ofDim[scalafx.scene.shape.Circle](8, 8)
  private[view] var showLegalMoves: Boolean = true

  enum GameMode:
    case HumanVsHuman, HumanVsComputer, ComputerVsComputer

  private[view] var gameMode: GameMode = GameMode.HumanVsHuman
  private[view] val computerPlayer: ComputerPlayer = new ComputerPlayer()
  // Whether a background computer-move thread is currently scheduled
  @volatile private var computerScheduled: Boolean = false
  private[view] var primaryStage: Stage = uninitialized

  // UI components that need to be updated
  private var playerLabel: Label = uninitialized
  private var moveInput: TextField = uninitialized
  private var fenDisplay: TextArea = uninitialized
  private var pgnDisplay: TextFlow = uninitialized
  private var pgnScrollPane: ScrollPane = uninitialized
  private var blackCapturesBox: HBox = uninitialized
  private var whiteCapturesBox: HBox = uninitialized
  private var materialLabel: Label = uninitialized
  private[view] var initialized: Boolean = false

  override def update(event: MoveResult): Unit = {
    if (!initialized) return
    Platform.runLater(() => {
      event match {
        case MoveResult.Moved(_, gameEvent) =>
          playerLabel.text = gameEvent match {
            case GameEvent.Checkmate => "Checkmate!"
            case GameEvent.Stalemate => "Stalemate! Draw."
            case GameEvent.Check =>
              s"${if (controller.isWhiteToMove) "White" else "Black"} is in check!"
            case GameEvent.Moved =>
              if (controller.isWhiteToMove) "White to move" else "Black to move"
          }
          fenDisplay.text = controller.getBoardAsFEN
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

  private def isGameOver: Boolean = controller.isCheckmate || controller.isStalemate

  private def isComputerTurn: Boolean = gameMode match
    case GameMode.HumanVsHuman      => false
    case GameMode.HumanVsComputer   => !controller.isWhiteToMove  // computer plays Black
    case GameMode.ComputerVsComputer => true

  private[view] def triggerComputerMoveIfNeeded(): Unit =
    if (!computerScheduled && isComputerTurn && !isGameOver) {
      computerScheduled = true
      val delayMs = if (gameMode == GameMode.ComputerVsComputer) 500L else 300L
      val t = new Thread(() => {
        Thread.sleep(delayMs)
        Platform.runLater(() => {
          computerScheduled = false
          if (isComputerTurn && !isGameOver) {
            val color = controller.activeColor
            computerPlayer.move(controller.board, color).foreach {
              case (from, to, promo) =>
                selectedSquare = None
                controller.applyMove(from, to, promo)
                // update() is notified by the controller and will call
                // triggerComputerMoveIfNeeded() again for C vs C
            }
          }
        })
      }, "computer-move")
      t.setDaemon(true)
      t.start()
    }

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

    // PGN section
    val pgnLabel = new Label("Game PGN (click move to navigate):") {
      font = Font.font("Arial", FontWeight.Normal, 14)
      padding = Insets(10, 0, 5, 0)
    }

    pgnDisplay = new TextFlow {
      padding = Insets(5)
      prefWidth = 250
    }

    pgnScrollPane = new ScrollPane {
      content = pgnDisplay
      prefHeight = 120
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

    // Paste input section — accepts both FEN and PGN
    val pasteLabel = new Label("Paste PGN or FEN:") {
      font = Font.font("Arial", FontWeight.Normal, 14)
      padding = Insets(10, 0, 5, 0)
    }

    val pasteInput = new TextArea {
      promptText =
        "Paste PGN moves (e.g. 1. e4 e5 2. Nf3 ...)\nor a FEN string here"
      prefRowCount = 4
      wrapText = true
      style = "-fx-font-family: monospace; -fx-font-size: 12px;"
    }

    val loadButton = new Button("Load") {
      prefWidth = 120
      style =
        "-fx-font-size: 13px; -fx-padding: 8px; -fx-background-color: #4CAF50; -fx-text-fill: white;"
      onAction = _ => {
        val input = pasteInput.text.value.trim
        if (input.nonEmpty) {
          loadPgnOrFen(input)
          selectedSquare = None
        }
      }
    }

    val clearPasteButton = new Button("Clear") {
      prefWidth = 120
      style = "-fx-font-size: 13px; -fx-padding: 8px;"
      onAction = _ => pasteInput.text = ""
    }

    val loadButtonBox = new HBox(10) {
      alignment = Pos.Center
      children = Seq(loadButton, clearPasteButton)
    }

    // FEN display (read-only)
    val fenLabel = new Label("FEN:") {
      font = Font.font("Arial", FontWeight.Normal, 14)
      padding = Insets(10, 0, 5, 0)
    }

    fenDisplay = new TextArea {
      text = controller.getBoardAsFEN
      prefRowCount = 2
      wrapText = true
      editable = false
      style =
        "-fx-font-family: monospace; -fx-font-size: 11px; -fx-opacity: 0.9;"
    }

    // New Game button
    val resetButton = new Button("New Game") {
      prefWidth = 250
      style =
        "-fx-font-size: 14px; -fx-padding: 10px; -fx-background-color: #ff9800; -fx-text-fill: white;"
      onAction = _ => {
        // Reset the board state — observer update() handles display refresh
        controller.announceInitial(Board.initial)
        selectedSquare = None
        moveInput.text = ""
        pgnDisplay.children.clear()
        pasteInput.text = ""
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
        pgnLabel,
        pgnScrollPane,
        navButtonBox,
        new Separator(),
        pasteLabel,
        pasteInput,
        loadButtonBox,
        new Separator(),
        fenLabel,
        fenDisplay,
        new Separator(),
        resetButton
      )
    }
  }

  private def handleMoveInput(): Unit = {
    val move = moveInput.text.value.trim
    if (move.nonEmpty) {
      controller.applyPgnMove(move) match {
        case _: MoveResult.Moved =>
          moveInput.text = ""
        // Board/label/FEN updated by observer update()
        case MoveResult.Failed(_, error) =>
          val detailedError = s"""
            |Move: '$move'
            |
            |Reason: ${error.message}
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

  private def updatePgnDisplay(): Unit = {
    pgnDisplay.children.clear()
    val moves      = controller.pgnMoves
    val activeIdx  = controller.currentIndex - 1 // index of the move that produced the current board
    var activeNode = Option.empty[Text]

    moves.zipWithIndex.foreach { case (move, i) =>
      if (i % 2 == 0) {
        pgnDisplay.children.add(new Text(s"${i / 2 + 1}. ") {
          font = Font.font("monospace", FontWeight.Normal, 13)
        })
      }

      val isActive = i == activeIdx
      val moveText = new Text(move + " ") {
        font  = Font.font("monospace", if (isActive) FontWeight.Bold else FontWeight.Normal, 13)
        fill  = if (isActive) Color.web("#1565C0") else Color.Black
        style = "-fx-cursor: hand;"

        onMouseEntered = _ => if (!isActive) fill = Color.web("#1565C0")
        onMouseExited  = _ => if (!isActive) fill = Color.Black
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
          val viewH  = pgnScrollPane.height.value
          if (totalH > viewH) {
            val nodeY = node.delegate.getBoundsInParent.getMinY
            pgnScrollPane.vvalue = Math.min(1.0, nodeY / (totalH - viewH))
          }
        case None =>
          pgnScrollPane.vvalue = 1.0
      }
    })
  }

  private[view] def createCapturedPanel(): VBox = {
    blackCapturesBox = new HBox(3) { padding = Insets(2, 0, 2, 0) }
    whiteCapturesBox = new HBox(3) { padding = Insets(2, 0, 2, 0) }
    materialLabel = new Label("=") {
      font = Font.font("Arial", FontWeight.Bold, 13)
      maxWidth = Double.MaxValue
      alignment = Pos.Center
    }
    updateCapturedPanel()
    new VBox(2) {
      padding = Insets(4, 20, 8, 20)
      style = "-fx-background-color: #ebebeb; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;"
      children = Seq(blackCapturesBox, materialLabel, whiteCapturesBox)
    }
  }

  private def updateCapturedPanel(): Unit = {
    val startingCounts = Map(
      Role.Pawn -> 8, Role.Knight -> 2, Role.Bishop -> 2,
      Role.Rook -> 2, Role.Queen  -> 1
    )
    val pieceValues = Map(
      Role.Pawn -> 1, Role.Knight -> 3, Role.Bishop -> 3,
      Role.Rook -> 5, Role.Queen  -> 9
    )
    val displayRoles = Seq(Role.Queen, Role.Rook, Role.Bishop, Role.Knight, Role.Pawn)

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

    val whiteMaterial = displayRoles.map(r => captured(ChessColor.Black, r) * pieceValues(r)).sum
    val blackMaterial = displayRoles.map(r => captured(ChessColor.White, r) * pieceValues(r)).sum
    val adv = whiteMaterial - blackMaterial
    materialLabel.text =
      if adv > 0 then s"White +$adv"
      else if adv < 0 then s"Black +${-adv}"
      else "="
  }

  private[view] def updateBoard(): Unit = {
    // Compute legal target squares once for the selected piece
    val legalTargets: Set[Square] = if (showLegalMoves) {
      selectedSquare.map { sel =>
        controller.board
          .legalMoves(controller.activeColor)
          .collect { case (from, to) if from == sel => to }
          .toSet
      }.getOrElse(Set.empty)
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

      squareRect.fill = selectedSquare match {
        case Some(sel) if sel == sq => Color.web("#baca44")
        case _ if kingInDanger.contains(sq) =>
          if (isCheckmate) Color.web("#c0392b") else Color.web("#e74c3c")
        case _ => baseColor
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
          val needsPromotion = controller.board.pieceAt(fromSquare).exists { p =>
            p.role == Role.Pawn &&
              ((p.color == ChessColor.White && square.rank == Rank._8) ||
                (p.color == ChessColor.Black && square.rank == Rank._1))
          }
          val promotion: Option[PromotableRole] =
            if needsPromotion then showPromotionDialog() else None

          // If the user cancelled the promotion dialog, deselect and stop
          if needsPromotion && promotion.isEmpty then
            selectedSquare = None
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
                  case MoveError.WrongColor        => "That's not your piece!"
                  case MoveError.NoPiece           => "No piece on that square!"
                  case MoveError.InvalidMove       => "That piece can't move there!"
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

  private def loadPgnOrFen(input: String): Unit = {
    // Heuristic: FEN strings contain '/' for rank separators and typically
    // have 7 slashes (8 ranks). PGN move text does not.
    val isFen = input.count(_ == '/') >= 7 && !input.contains('\n')

    if (isFen) {
      controller.loadFromFEN(input) match {
        case Right(_) =>
          showInfo("FEN Loaded", "Position loaded from FEN string.")
        case Left(error) =>
          showAlert("Invalid FEN", error)
      }
    } else {
      controller.loadPgnMoves(input) match {
        case Right(_) =>
          showInfo(
            "PGN Loaded",
            s"Loaded ${controller.pgnMoves.length} moves from PGN."
          )
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

  private[view] def createMenuBar(): MenuBar = {
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
      onAction = _ => if (selected.value) {
        gameMode = GameMode.HumanVsHuman
      }
    }
    val hvcItem = new RadioMenuItem("Human vs Computer") {
      toggleGroup = modeGroup
      onAction = _ => if (selected.value) {
        gameMode = GameMode.HumanVsComputer
        triggerComputerMoveIfNeeded()
      }
    }
    val cvcItem = new RadioMenuItem("Computer vs Computer") {
      toggleGroup = modeGroup
      onAction = _ => if (selected.value) {
        gameMode = GameMode.ComputerVsComputer
        triggerComputerMoveIfNeeded()
      }
    }

    val gameMenu = new Menu("Game") {
      items = Seq(hvhItem, hvcItem, cvcItem)
    }

    // Strategy selection (applies to any computer-controlled side)
    val strategyGroup = new ToggleGroup()
    val strategies: Seq[(String, () => MoveStrategy)] = Seq(
      "Random"           -> (() => new RandomStrategy()),
      "Greedy"           -> (() => new GreedyStrategy()),
      "Material Balance" -> (() => new MaterialBalanceStrategy())
    )
    val strategyItems = strategies.zipWithIndex.map { case ((label, factory), idx) =>
      new RadioMenuItem(label) {
        toggleGroup = strategyGroup
        selected = idx == 0
        onAction = _ => if (selected.value) computerPlayer.strategy = factory()
      }
    }
    val strategyMenu = new Menu("Strategy") {
      items = strategyItems
    }

    new MenuBar {
      menus = Seq(fileMenu, gameMenu, strategyMenu, viewMenu)
    }
  }

  private def showPromotionDialog(): Option[PromotableRole] = {
    val queenBtn  = new ButtonType("♛ Queen")
    val rookBtn   = new ButtonType("♜ Rook")
    val bishopBtn = new ButtonType("♝ Bishop")
    val knightBtn = new ButtonType("♞ Knight")
    new Alert(Alert.AlertType.Confirmation) {
      initOwner(primaryStage)
      title = "Pawn Promotion"
      headerText = "Choose a piece to promote to:"
      buttonTypes = Seq(queenBtn, rookBtn, bishopBtn, knightBtn, ButtonType.Cancel)
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
          case javafx.scene.input.KeyCode.LEFT  =>
            gui.controller.backward(); gui.selectedSquare = None
          case javafx.scene.input.KeyCode.RIGHT =>
            gui.controller.forward();  gui.selectedSquare = None
          case _ => ()
        }
      }
    )

    // Mark GUI as initialized — observer update() calls are now safe
    gui.initialized = true

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
