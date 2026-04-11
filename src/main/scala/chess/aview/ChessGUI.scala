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
import chess.application.puzzle.PuzzleParser
import chess.model.Puzzle
import chess.model.Opening
import chess.persistence.OpeningRepository
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.controller.strategy.{
  RandomStrategy,
  GreedyStrategy,
  MaterialBalanceStrategy,
  PieceSquareStrategy,
  MinimaxStrategy,
  EndgameMinimaxStrategy,
  QuiescenceStrategy,
  IterativeDeepeningStrategy,
  IterativeDeepeningEndgameStrategy,
  OpeningContinuationStrategy,
  OpeningBookStrategy
}
import chess.model.{Board, Piece, PromotableRole, Role, Square, File, Rank, MoveResult, MoveError, GameEvent}
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
  private[aview] var boardFlipped: Boolean = false
  private var boardSquares: Array[Array[Rectangle]] =
    Array.ofDim[Rectangle](8, 8)
  private var boardLabels: Array[Array[Text]] = Array.ofDim[Text](8, 8)
  private var boardDots: Array[Array[scalafx.scene.shape.Circle]] =
    Array.ofDim[scalafx.scene.shape.Circle](8, 8)
  private var fileLabels: Array[Label] = Array.ofDim[Label](8)
  private var rankLabels: Array[Label] = Array.ofDim[Label](8)
  private[aview] var showLegalMoves: Boolean = true

  enum GameMode:
    case HumanVsHuman, HumanVsComputer, ComputerVsComputer

  private[aview] var gameMode: GameMode = GameMode.HumanVsHuman
  private[aview] val whiteComputer: ComputerPlayer = new ComputerPlayer(
    new OpeningContinuationStrategy(openings)
  )
  private[aview] val blackComputer: ComputerPlayer = new ComputerPlayer(
    new OpeningContinuationStrategy(openings)
  )
  // Whether a background computer-move thread is currently scheduled
  @volatile private var computerScheduled: Boolean = false
  @volatile private[aview] var paused: Boolean = false

  private lazy val puzzles: Vector[Puzzle] =
    PuzzleParser.fromResource("/puzzle/lichess_small_puzzle.csv")

  private lazy val openings: List[Opening] =
    summon[OpeningRepository[IO]].findAll(limit = 10000).unsafeRunSync()

  private lazy val openingsByFen: Map[String, Opening] =
    openings.map(o => o.fen -> o).toMap

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
  private var openingInfoLabel: Label = uninitialized
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
  private var clockSystem: ActorSystem[chess.controller.clock.ClockActor.Command] = uninitialized
  private[aview] var initialized: Boolean = false

  // ── Session / backend integration ───────────────────────────────────────────
  private val backendUrl     = sys.env.getOrElse("CHESS_API_URL",      "http://localhost:8081")
  private val matchRunnerUrl = sys.env.getOrElse("MATCH_RUNNER_URL",   "http://localhost:8084")
  private val wsBaseUrl  = sys.env.getOrElse("CHESS_WS_URL",  "ws://localhost:8083")
  private val httpClient: java.net.http.HttpClient = java.net.http.HttpClient.newHttpClient()
  @volatile private var currentSessionId: Option[String] = None
  @volatile private var wsGeneration: Int = 0
  @volatile private var wsConn: Option[java.net.http.WebSocket] = None
  private val sessionMovePublicationGate = new SessionMovePublicationGate
  // 'w' = plays white, 'b' = plays black, 'x' = no restriction (local)
  private var mySessionColor: Char = 'x'
  private var sessionIdText: scalafx.scene.text.Text = uninitialized
  private var sessionBar: HBox = uninitialized

  private case class SessionInfo(gameId: String, status: String, whiteIsHuman: Boolean, blackIsHuman: Boolean)

  // ── Lucide icon SVG paths (24×24 viewBox) ──────────────────────────
  private val IconChevronsLeft = Seq("m11 17-5-5 5-5", "m18 17-5-5 5-5")
  private val IconChevronLeft = Seq("m15 18-6-6 6-6")
  private val IconChevronRight = Seq("m9 18 6-6-6-6")
  private val IconChevronsRight = Seq("m6 17 5-5-5-5", "m13 17 5-5-5-5")
  private val IconUndo = Seq("M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11")
  private val IconRedo = Seq("m15 14 5-5-5-5", "M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13")
  private val IconArrowUpDown = Seq("m21 16-4 4-4-4", "M17 20V4", "m3 8 4-4 4 4", "M7 4v16")
  private val IconPlay = Seq("M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z")
  private val IconPause = Seq(
    "M15 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
    "M6 3h3a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z"
  )
  private val IconSquarePlus = Seq(
    "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
    "M8 12h8",
    "M12 8v8"
  )
  private val IconCopy = Seq(
    "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z",
    "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"
  )
  private val IconCheck = Seq("M20 6 9 17l-5-5")

  private def lucideIcon(
      paths: Seq[String],
      size: Double = 16,
      strokeColor: javafx.scene.paint.Color = javafx.scene.paint.Color.web("#555")
  ): javafx.scene.Group = {
    val group = new javafx.scene.Group()
    paths.foreach { d =>
      val svgPath = new javafx.scene.shape.SVGPath()
      svgPath.setContent(d)
      svgPath.setFill(javafx.scene.paint.Color.TRANSPARENT)
      svgPath.setStroke(strokeColor)
      svgPath.setStrokeWidth(2.0)
      svgPath.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      svgPath.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND)
      group.getChildren.add(svgPath)
    }
    val scale = size / 24.0
    group.setScaleX(scale)
    group.setScaleY(scale)
    group
  }

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
            // Publish move to backend when playing in a session
            if (sessionMovePublicationGate.shouldPublishObservedMove()) {
              for {
                sid <- currentSessionId
                san <- controller.pgnMoves.lastOption
              } postMoveToBackend(sid, san)
            }
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
          updateOpeningLabel()
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

  // ── HTTP helpers ─────────────────────────────────────────────────────────────

  private def httpPost(url: String, body: String): Option[String] =
    Try {
      val req = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(url))
        .header("Content-Type", "application/json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
        .timeout(java.time.Duration.ofSeconds(3))
        .build()
      val r = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
      if (r.statusCode() / 100 == 2) Some(r.body()) else None
    }.getOrElse(None)

  private def httpGet(url: String): Option[String] =
    Try {
      val req = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(url))
        .GET()
        .timeout(java.time.Duration.ofSeconds(3))
        .build()
      val r = httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
      if (r.statusCode() == 200) Some(r.body()) else None
    }.getOrElse(None)

  private def httpDelete(url: String): Unit =
    Try {
      val req = java.net.http.HttpRequest.newBuilder()
        .uri(java.net.URI.create(url))
        .DELETE()
        .timeout(java.time.Duration.ofSeconds(3))
        .build()
      httpClient.send(req, java.net.http.HttpResponse.BodyHandlers.discarding())
    }

  private def jsonStr(json: String, key: String): Option[String] =
    s""""$key"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(json).map(_.group(1))

  private def jsonBool(json: String, key: String): Option[Boolean] =
    s""""$key"\\s*:\\s*(true|false)""".r.findFirstMatchIn(json).map(_.group(1) == "true")

  // ── Session backend calls ────────────────────────────────────────────────────

  private def registerSession(
    whiteIsHuman: Boolean, blackIsHuman: Boolean,
    whiteStrat: String, blackStrat: String,
    clockMs: Option[Long], incMs: Option[Long],
    startFen: Option[String]
  ): Option[String] = {
    val incField    = incMs.filter(_ > 0).map(i => s""","clockIncrementMs":$i""").getOrElse("")
    val clockFields = clockMs.map(ms => s""","clockInitialMs":$ms$incField""").getOrElse("")
    val fenField = startFen.filter(_.nonEmpty).map(f => s""","startFen":"$f"""").getOrElse("")
    val body = s"""{"settings":{"whiteIsHuman":$whiteIsHuman,"blackIsHuman":$blackIsHuman,"whiteStrategy":"$whiteStrat","blackStrategy":"$blackStrat"$clockFields}$fenField}"""
    httpPost(s"$backendUrl/games", body).flatMap(jsonStr(_, "gameId"))
  }

  private def loadSessions(): List[SessionInfo] =
    httpGet(s"$backendUrl/games").map { json =>
      val ids      = """"gameId"\s*:\s*"([^"]+)"""".r.findAllMatchIn(json).map(_.group(1)).toList
      val statuses = """"status"\s*:\s*"([^"]+)"""".r.findAllMatchIn(json).map(_.group(1)).toList
      val whites   = """"whiteIsHuman"\s*:\s*(true|false)""".r.findAllMatchIn(json).map(_.group(1) == "true").toList
      val blacks   = """"blackIsHuman"\s*:\s*(true|false)""".r.findAllMatchIn(json).map(_.group(1) == "true").toList
      ids.zipWithIndex.map { case (id, i) =>
        SessionInfo(id, statuses.applyOrElse(i, _ => ""), whites.applyOrElse(i, _ => true), blacks.applyOrElse(i, _ => true))
      }
    }.getOrElse(List.empty)

  private def postMoveToBackend(gameId: String, san: String): Unit = {
    val t = new Thread(() => { httpPost(s"$backendUrl/games/$gameId/moves", s"""{"move":"$san"}"""); () }, "backend-move")
    t.setDaemon(true); t.start()
  }

  // ── WebSocket ────────────────────────────────────────────────────────────────

  private def connectWebSocket(gameId: String): Unit = {
    disconnectWebSocket()
    val gen = wsGeneration
    val t = new Thread(() => { Try {
      val uri = java.net.URI.create(s"$wsBaseUrl/ws/$gameId")
      val sb  = new StringBuilder()
      val listener = new java.net.http.WebSocket.Listener {
        override def onText(ws: java.net.http.WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage[?] = {
          sb.append(data)
          if (last) {
            val msg = sb.toString(); sb.clear()
            if (gen == wsGeneration) Platform.runLater(() => handleWsMessage(msg))
          }
          ws.request(1)
          null
        }
        override def onError(ws: java.net.http.WebSocket, error: Throwable): Unit = ()
      }
      val ws = httpClient.newWebSocketBuilder()
        .buildAsync(uri, listener)
        .get(5, java.util.concurrent.TimeUnit.SECONDS)
      wsConn = Some(ws)
      ws.request(1)
    }; () }, "ws-connect")
    t.setDaemon(true); t.start()
  }

  private def disconnectWebSocket(): Unit = {
    wsGeneration += 1
    wsConn.foreach(ws => Try(ws.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "").get(1, java.util.concurrent.TimeUnit.SECONDS)))
    wsConn = None
  }

  private def parseSanMoves(pgn: String): Vector[String] =
    pgn.replaceAll("""\d+\.""", " ").replaceAll("""\s+""", " ").trim
      .split(" ").filter(_.nonEmpty).toVector

  private def handleWsMessage(json: String): Unit =
    if (jsonStr(json, "eventType").contains("move_applied")) {
      jsonStr(json, "pgn").foreach { pgn =>
        val incoming = parseSanMoves(pgn)
        val current  = controller.pgnMoves.length
        if (incoming.length > current) {
          val san = incoming(current)
          sessionMovePublicationGate.markRemoteMoveApplied()
          controller.applyPgnMove(san) // fires update() via observer
        }
      }
    }

  private def updateSessionBar(): Unit =
    if (sessionIdText != null) {
      currentSessionId match {
        case Some(id) =>
          sessionIdText.text = id
          if (sessionBar != null) { sessionBar.visible = true; sessionBar.managed = true }
        case None =>
          sessionIdText.text = ""
          if (sessionBar != null) { sessionBar.visible = false; sessionBar.managed = false }
      }
    }

  // ── Session bar ───────────────────────────────────────────────────────────────

  private[aview] def createSessionBar(): HBox = {
    val lbl = new Label("SESSION") {
      font = Font.font("Arial", FontWeight.Bold, 10)
      style = "-fx-text-fill: #2d5a1b;"
      padding = Insets(0, 4, 0, 0)
    }
    sessionIdText = new scalafx.scene.text.Text {
      font = Font.font("Monospaced", FontWeight.Normal, 11)
      fill = Color.web("#2d5a1b")
    }
    val copyBtn = new Button() {
      style = "-fx-padding: 2px 5px;"
      tooltip = new Tooltip("Copy session ID")
      delegate.setGraphic(lucideIcon(IconCopy, 13))
      onAction = _ => currentSessionId.foreach { id =>
        val c = new ClipboardContent(); c.putString(id)
        Clipboard.getSystemClipboard.setContent(c)
        delegate.setGraphic(lucideIcon(IconCheck, 13, javafx.scene.paint.Color.web("#27ae60")))
        val t = new Thread(() => { Thread.sleep(1500); Platform.runLater(() => delegate.setGraphic(lucideIcon(IconCopy, 13))) })
        t.setDaemon(true); t.start()
      }
    }
    sessionBar = new HBox(6) {
      alignment = Pos.CenterLeft
      padding = Insets(4, 12, 4, 12)
      style = "-fx-background-color: #f0f8e6; -fx-border-color: #d5e8b8; -fx-border-width: 1 0 0 0;"
      children = Seq(lbl, sessionIdText, copyBtn)
      visible = false
      managed = false
    }
    sessionBar
  }

  // ── Strategy helper ───────────────────────────────────────────────────────────

  private def strategyForId(id: String): MoveStrategy = id match {
    case "random"              => new RandomStrategy()
    case "greedy"              => new GreedyStrategy()
    case "material-balance"    => new MaterialBalanceStrategy()
    case "piece-square"        => new PieceSquareStrategy()
    case "minimax"             => new MinimaxStrategy(3)
    case "endgame-minimax"     => new EndgameMinimaxStrategy(3)
    case "quiescence"          => new QuiescenceStrategy(3)
    case "iterative-deepening"              => new IterativeDeepeningStrategy()
    case "iterative-deepening-endgame"      => new IterativeDeepeningEndgameStrategy()
    case "opening-continuation"             => new OpeningContinuationStrategy(openings)
    case "opening-continuation-endgame"     => new OpeningContinuationStrategy(openings, new IterativeDeepeningEndgameStrategy())
    case "opening-intelligence"             => new OpeningBookStrategy(openings)
    case "opening-intelligence-endgame"     => new OpeningBookStrategy(openings, new IterativeDeepeningEndgameStrategy())
    case _                                  => new OpeningContinuationStrategy(openings)
  }

  // ── New Game dialog ───────────────────────────────────────────────────────────

  private[aview] def showNewGameDialog(): Unit = {
    var whiteHuman = true; var blackHuman = true
    var whiteStrat = "opening-continuation"; var blackStrat = "opening-continuation"
    var clockMs: Option[Long] = None; var incMs: Option[Long] = None
    var playAsWhite = true

    val stratIds     = Array("opening-continuation","opening-continuation-endgame","opening-intelligence","opening-intelligence-endgame","random","greedy","material-balance","piece-square","minimax","endgame-minimax","quiescence","iterative-deepening","iterative-deepening-endgame")
    val stratLabels  = Array("Opening Continuation","Opening Continuation+EG","Opening Intelligence","Opening Intelligence+EG","Random","Greedy","Material Balance","Piece-Square","Minimax (d=3)","Endgame Minimax (d=3)","Quiescence (d=3)","Iterative Deepening","ID+Endgame")
    val clockPresets = Array(
      ("No Limit", None, None), ("Bullet 1+0", Some(60_000L), Some(0L)),
      ("Blitz 3+0", Some(180_000L), Some(0L)), ("Blitz 5+0", Some(300_000L), Some(0L)),
      ("Rapid 10+0", Some(600_000L), Some(0L)), ("Blitz 3+2", Some(180_000L), Some(2_000L)),
      ("Blitz 5+3", Some(300_000L), Some(3_000L)), ("Rapid 15+10", Some(900_000L), Some(10_000L)),
      ("Classical 30+0", Some(1_800_000L), Some(0L))
    )

    val dlg = new javafx.stage.Stage()
    dlg.initOwner(primaryStage.delegate)
    dlg.initModality(javafx.stage.Modality.WINDOW_MODAL)
    dlg.setTitle("New Game"); dlg.setResizable(false)

    def mkPlayerRow(icon: String, initHuman: Boolean,
                    onHuman: Boolean => Unit, onStrat: String => Unit
                   ): (javafx.scene.layout.HBox, () => Unit) = {
      val lbl = new javafx.scene.control.Label(icon)
      lbl.setStyle("-fx-font-size:16px; -fx-min-width:30px;")
      val humanBtn = new javafx.scene.control.ToggleButton("Human")
      val compBtn  = new javafx.scene.control.ToggleButton("Computer")
      val tg = new javafx.scene.control.ToggleGroup()
      humanBtn.setToggleGroup(tg); compBtn.setToggleGroup(tg)
      if (initHuman) humanBtn.setSelected(true) else compBtn.setSelected(true)
      val stratBox = new javafx.scene.control.ComboBox[String]()
      stratLabels.foreach(stratBox.getItems.add)
      stratBox.getSelectionModel.selectFirst()
      stratBox.setVisible(!initHuman); stratBox.setManaged(!initHuman)
      tg.selectedToggleProperty().addListener { (_, _, t) =>
        val isHuman = t == humanBtn
        onHuman(isHuman)
        stratBox.setVisible(!isHuman); stratBox.setManaged(!isHuman)
        dlg.sizeToScene()
      }
      stratBox.setOnAction(_ => onStrat(stratIds(stratBox.getSelectionModel.getSelectedIndex)))
      val row = new javafx.scene.layout.HBox(8, lbl, humanBtn, compBtn, stratBox)
      row.setAlignment(javafx.geometry.Pos.CENTER_LEFT)
      (row, () => { tg.getToggles })
    }

    val (whiteRow, _) = mkPlayerRow("♔  White", true, b => { whiteHuman = b }, s => { whiteStrat = s })
    val (blackRow, _) = mkPlayerRow("♚  Black", true, b => { blackHuman = b }, s => { blackStrat = s })

    val clockTG   = new javafx.scene.control.ToggleGroup()
    val clockFlow = new javafx.scene.layout.FlowPane(6, 6)
    clockPresets.zipWithIndex.foreach { case ((lbl, ms, inc), i) =>
      val btn = new javafx.scene.control.ToggleButton(lbl)
      btn.setToggleGroup(clockTG)
      if (i == 0) btn.setSelected(true)
      btn.setOnAction(_ => { clockMs = ms; incMs = inc })
      clockFlow.getChildren.add(btn)
    }

    val playWhiteBtn = new javafx.scene.control.ToggleButton("♔ White")
    val playBlackBtn = new javafx.scene.control.ToggleButton("♚ Black")
    val playTG = new javafx.scene.control.ToggleGroup()
    playWhiteBtn.setToggleGroup(playTG); playBlackBtn.setToggleGroup(playTG); playWhiteBtn.setSelected(true)
    playTG.selectedToggleProperty().addListener((_, _, t) => playAsWhite = (t == playWhiteBtn))
    val playAsLbl = new javafx.scene.control.Label("I play as")
    playAsLbl.setStyle("-fx-font-weight:bold; -fx-font-size:12px;")
    val playAsRow = new javafx.scene.layout.HBox(8, playAsLbl, playWhiteBtn, playBlackBtn)
    playAsRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT)

    val fenLbl = new javafx.scene.control.Label("Starting FEN (optional)")
    fenLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#888;")
    val fenField = new javafx.scene.control.TextField()
    fenField.setPromptText("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    fenField.setStyle("-fx-font-family:monospace; -fx-font-size:11px;"); fenField.setPrefWidth(440)

    val cancelBtn = new javafx.scene.control.Button("Cancel")
    cancelBtn.setOnAction(_ => dlg.close())
    val startBtn = new javafx.scene.control.Button("Start Game")
    startBtn.setDefaultButton(true)
    startBtn.setStyle("-fx-background-color:#629924; -fx-text-fill:white; -fx-font-weight:bold;")
    startBtn.setOnAction(_ => {
      dlg.close()
      val startFen = Option(fenField.getText.trim).filter(_.nonEmpty)
      paused = false; gameOverByTimeout = false; lastPgnLength = 0; selectedSquare = None
      gameMode = if (whiteHuman && blackHuman) GameMode.HumanVsHuman
                 else if (!whiteHuman && !blackHuman) GameMode.ComputerVsComputer
                 else GameMode.HumanVsComputer
      if (!whiteHuman) { whiteComputer.strategy = strategyForId(whiteStrat); updateStrategyLabels() }
      if (!blackHuman) { blackComputer.strategy = strategyForId(blackStrat); updateStrategyLabels() }
      val newClock = clockMs match {
        case Some(ms) => ClockMode.Timed(ms, incMs.getOrElse(0L), "")
        case None     => ClockMode.NoLimit
      }
      applyClockMode(newClock)
      startFen match {
        case Some(fen) => controller.loadFromFEN(fen)
        case None      => controller.announceInitial(Board.initial)
      }
      mySessionColor = if (gameMode == GameMode.HumanVsHuman) (if (playAsWhite) 'w' else 'b') else 'x'
      boardFlipped = mySessionColor == 'b'
      updateBoard(); updatePauseButtonVisibility()
      if (openingInfoLabel != null) { openingInfoLabel.visible = false; openingInfoLabel.managed = false }
      if (puzzleInfoLabel != null)  { puzzleInfoLabel.visible  = false; puzzleInfoLabel.managed  = false }
      if (puzzleLink != null)       { puzzleLink.visible        = false; puzzleLink.managed        = false }
      pgnDisplay.children.clear()
      disconnectWebSocket()
      val t = new Thread(() => {
        val sid = registerSession(whiteHuman, blackHuman, whiteStrat, blackStrat, clockMs, incMs.filter(_ > 0), startFen)
        Platform.runLater(() => {
          currentSessionId = sid; updateSessionBar()
          sid.foreach(id => if (gameMode == GameMode.HumanVsHuman) connectWebSocket(id))
        })
      }, "session-create")
      t.setDaemon(true); t.start()
      triggerComputerMoveIfNeeded()
    })

    val btnRow = new javafx.scene.layout.HBox(8, cancelBtn, startBtn)
    btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT)
    val clockLbl = new javafx.scene.control.Label("Time Control")
    clockLbl.setStyle("-fx-font-weight:bold; -fx-font-size:12px;")
    val root = new javafx.scene.layout.VBox(12)
    root.setPadding(new javafx.geometry.Insets(20)); root.setPrefWidth(480)
    root.getChildren.addAll(
      whiteRow, blackRow, new javafx.scene.control.Separator(),
      clockLbl, clockFlow, new javafx.scene.control.Separator(),
      playAsRow, fenLbl, fenField, new javafx.scene.control.Separator(),
      btnRow
    )
    dlg.setScene(new javafx.scene.Scene(root))
    dlg.showAndWait()
  }

  // ── Join Session dialog ───────────────────────────────────────────────────────

  private[aview] def showJoinSessionDialog(): Unit = {
    val dlg = new javafx.stage.Stage()
    dlg.initOwner(primaryStage.delegate)
    dlg.initModality(javafx.stage.Modality.WINDOW_MODAL)
    dlg.setTitle("Join Session"); dlg.setResizable(false)

    val idField = new javafx.scene.control.TextField()
    idField.setPromptText("Paste session ID here")
    idField.setStyle("-fx-font-family:monospace; -fx-font-size:11px;")

    val listView = new javafx.scene.control.ListView[String]()
    listView.setPrefHeight(160)
    var infos: List[SessionInfo] = List.empty

    def refresh(): Unit = {
      val t = new Thread(() => {
        val sessions = loadSessions()
        Platform.runLater(() => {
          infos = sessions
          listView.getItems.clear()
          sessions.foreach { s =>
            val mode = if (s.whiteIsHuman && s.blackIsHuman) "HvH"
                       else if (!s.whiteIsHuman && !s.blackIsHuman) "CvC"
                       else if (s.whiteIsHuman) "HvC" else "CvH"
            listView.getItems.add(s"$mode  ${s.status.take(20)}  ${s.gameId.take(8)}…")
          }
        })
      }, "session-refresh")
      t.setDaemon(true); t.start()
    }
    refresh()

    listView.getSelectionModel.selectedIndexProperty().addListener((_, _, idx) => {
      val i = idx.intValue()
      if (i >= 0 && i < infos.length) idField.setText(infos(i).gameId)
    })

    val refreshBtn = new javafx.scene.control.Button("↻")
    refreshBtn.setOnAction(_ => refresh())
    val delAllBtn = new javafx.scene.control.Button("Delete All")
    delAllBtn.setStyle("-fx-text-fill:#e74c3c; -fx-border-color:#e74c3c;")
    delAllBtn.setOnAction(_ => {
      val t = new Thread(() => {
        httpDelete(s"$backendUrl/games")
        Platform.runLater(() => { listView.getItems.clear(); infos = List.empty })
      }, "del-all")
      t.setDaemon(true); t.start()
    })
    val hdr = new javafx.scene.layout.HBox(6)
    val hdrLbl = new javafx.scene.control.Label("Active Sessions")
    hdrLbl.setStyle("-fx-font-weight:bold; -fx-font-size:12px;")
    hdr.getChildren.addAll(hdrLbl, refreshBtn, delAllBtn)
    hdr.setAlignment(javafx.geometry.Pos.CENTER_LEFT)

    val cancelBtn = new javafx.scene.control.Button("Cancel")
    cancelBtn.setOnAction(_ => dlg.close())
    val joinBtn = new javafx.scene.control.Button("Join Game")
    joinBtn.setDefaultButton(true)
    joinBtn.setStyle("-fx-background-color:#629924; -fx-text-fill:white; -fx-font-weight:bold;")
    joinBtn.setOnAction(_ => {
      val id = idField.getText.trim
      if (id.nonEmpty) {
        dlg.close()
        val t = new Thread(() => {
          httpGet(s"$backendUrl/games/$id") match {
            case None =>
              Platform.runLater(() => showAlert("Join Failed", "Session not found or server unreachable."))
            case Some(json) =>
              val fen = jsonStr(json, "fen").getOrElse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
              val pgn = jsonStr(json, "pgn").getOrElse("")
              val wH  = jsonBool(json, "whiteIsHuman").getOrElse(true)
              val bH  = jsonBool(json, "blackIsHuman").getOrElse(true)
              Platform.runLater(() => {
                paused = false; gameOverByTimeout = false; lastPgnLength = 0; selectedSquare = None
                gameMode = if (wH && bH) GameMode.HumanVsHuman else GameMode.HumanVsComputer
                mySessionColor = 'b'; boardFlipped = true
                controller.loadFromFEN(fen)
                if (pgn.trim.nonEmpty) controller.loadPgnMoves(pgn)
                currentSessionId = Some(id); updateSessionBar(); updateBoard(); updatePauseButtonVisibility()
                if (wH && bH) connectWebSocket(id)
              })
          }
        }, "session-join")
        t.setDaemon(true); t.start()
      }
    })

    val btnRow = new javafx.scene.layout.HBox(8, cancelBtn, joinBtn)
    btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT)
    val idLbl = new javafx.scene.control.Label("Or paste Session ID")
    idLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#888;")
    val root = new javafx.scene.layout.VBox(10)
    root.setPadding(new javafx.geometry.Insets(20)); root.setPrefWidth(440)
    root.getChildren.addAll(hdr, listView, new javafx.scene.control.Separator(), idLbl, idField, new javafx.scene.control.Separator(), btnRow)
    dlg.setScene(new javafx.scene.Scene(root))
    dlg.showAndWait()
  }

  // ── Browse Games dialog ───────────────────────────────────────────────────
  // Lists experiments from the match-runner, lets the user pick a run, then
  // replays the stored PGN into a fresh game session.

  private[aview] def showBrowseGamesDialog(): Unit = {
    val dlg = new javafx.stage.Stage()
    dlg.initOwner(primaryStage.delegate)
    dlg.initModality(javafx.stage.Modality.WINDOW_MODAL)
    dlg.setTitle("Browse Experiment Games"); dlg.setResizable(true)

    // ── left panel: experiment list ──────────────────────────────────────
    case class ExpEntry(id: String, name: String, status: String, games: Int)
    case class RunEntry(id: String, chessGameId: String, result: String, winner: String, moves: String, pgn: String)

    val expListView = new javafx.scene.control.ListView[String]()
    expListView.setPrefWidth(260); expListView.setPrefHeight(340)

    val runListView = new javafx.scene.control.ListView[String]()
    runListView.setPrefWidth(340); runListView.setPrefHeight(340)

    var experiments: Vector[ExpEntry] = Vector.empty
    var runs: Vector[RunEntry]        = Vector.empty

    val statusLbl = new javafx.scene.control.Label("Loading experiments…")
    statusLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#888;")

    def parseExperiments(json: String): Vector[ExpEntry] = {
      val idRx     = """"id"\s*:\s*"([^"]+)"""".r
      val nameRx   = """"name"\s*:\s*"([^"]+)"""".r
      val statusRx = """"status"\s*:\s*"([^"]+)"""".r
      val gamesRx  = """"games"\s*:\s*(\d+)""".r
      val ids      = idRx.findAllMatchIn(json).map(_.group(1)).toVector
      val names    = nameRx.findAllMatchIn(json).map(_.group(1)).toVector
      val statuses = statusRx.findAllMatchIn(json).map(_.group(1)).toVector
      val gameNums = gamesRx.findAllMatchIn(json).map(_.group(1).toInt).toVector
      ids.zipWithIndex.map { case (id, i) =>
        ExpEntry(id, names.applyOrElse(i, _ => id.take(8)), statuses.applyOrElse(i, _ => ""), gameNums.applyOrElse(i, _ => 0))
      }
    }

    def parseRuns(json: String): Vector[RunEntry] = {
      // Runs come as an array; parse field by field using index alignment
      val idRx      = """"id"\s*:\s*"([^"]+)"""".r
      val cgIdRx    = """"chessGameId"\s*:\s*"([^"]+)"""".r
      val resultRx  = """"result"\s*:\s*"([^"]+)"""".r
      val winnerRx  = """"winner"\s*:\s*"([^"]+)"""".r
      val movesRx   = """"moveCount"\s*:\s*(\d+)""".r
      val pgnRx     = """"pgn"\s*:\s*"((?:[^"\\]|\\.)*)"""".r
      val ids     = idRx.findAllMatchIn(json).map(_.group(1)).toVector
      val cgIds   = cgIdRx.findAllMatchIn(json).map(_.group(1)).toVector
      val results = resultRx.findAllMatchIn(json).map(_.group(1)).toVector
      val winners = winnerRx.findAllMatchIn(json).map(_.group(1)).toVector
      val moveCts = movesRx.findAllMatchIn(json).map(_.group(1)).toVector
      val pgns    = pgnRx.findAllMatchIn(json).map(_.group(1)).toVector
      ids.zipWithIndex.map { case (id, i) =>
        RunEntry(
          id       = id,
          chessGameId = cgIds.applyOrElse(i, _ => ""),
          result   = results.applyOrElse(i, _ => "?"),
          winner   = winners.applyOrElse(i, _ => "-"),
          moves    = moveCts.applyOrElse(i, _ => "?"),
          pgn      = pgns.applyOrElse(i, _ => "").replace("\\n", "\n").replace("\\\"", "\"")
        )
      }
    }

    def loadExperiments(): Unit = {
      val t = new Thread(() => {
        val result = httpGet(s"$matchRunnerUrl/experiments")
        Platform.runLater(() => {
          result match {
            case None =>
              statusLbl.setText(s"Could not reach match runner at $matchRunnerUrl")
            case Some(json) =>
              experiments = parseExperiments(json)
              expListView.getItems.clear()
              experiments.foreach { e =>
                expListView.getItems.add(s"${e.name}  [${e.status}]  (${e.games} games)")
              }
              if (experiments.isEmpty) statusLbl.setText("No experiments found.")
              else statusLbl.setText(s"${experiments.size} experiment(s)")
          }
        })
      }, "browse-experiments")
      t.setDaemon(true); t.start()
    }

    def loadRuns(expId: String): Unit = {
      runs = Vector.empty
      runListView.getItems.clear()
      val t = new Thread(() => {
        val result = httpGet(s"$matchRunnerUrl/experiments/$expId/runs")
        Platform.runLater(() => {
          result match {
            case None => statusLbl.setText("Could not load runs.")
            case Some(json) =>
              runs = parseRuns(json)
              runListView.getItems.clear()
              runs.foreach { r =>
                val res = if (r.result.isEmpty || r.result == "?") "?" else r.result
                runListView.getItems.add(s"${r.moves} moves  $res  [${r.winner}]  ${r.chessGameId.take(8)}…")
              }
              if (runs.isEmpty) statusLbl.setText("No runs for this experiment.")
              else statusLbl.setText(s"${runs.size} game(s) — select one to replay")
          }
        })
      }, "browse-runs")
      t.setDaemon(true); t.start()
    }

    expListView.getSelectionModel.selectedIndexProperty().addListener((_, _, idx) => {
      val i = idx.intValue()
      if (i >= 0 && i < experiments.size) loadRuns(experiments(i).id)
    })

    val refreshBtn = new javafx.scene.control.Button("↻ Refresh")
    refreshBtn.setOnAction(_ => loadExperiments())

    val expLbl = new javafx.scene.control.Label("Experiments")
    expLbl.setStyle("-fx-font-weight:bold; -fx-font-size:12px;")
    val runLbl = new javafx.scene.control.Label("Games")
    runLbl.setStyle("-fx-font-weight:bold; -fx-font-size:12px;")

    val expHdr = new javafx.scene.layout.HBox(6, expLbl, refreshBtn)
    expHdr.setAlignment(javafx.geometry.Pos.CENTER_LEFT)
    val expPanel = new javafx.scene.layout.VBox(6, expHdr, expListView)
    val runPanel = new javafx.scene.layout.VBox(6, runLbl, runListView)
    val listsRow = new javafx.scene.layout.HBox(12, expPanel, runPanel)

    val cancelBtn = new javafx.scene.control.Button("Cancel")
    cancelBtn.setOnAction(_ => dlg.close())

    val replayBtn = new javafx.scene.control.Button("Replay Game")
    replayBtn.setDefaultButton(true)
    replayBtn.setStyle("-fx-background-color:#629924; -fx-text-fill:white; -fx-font-weight:bold;")
    replayBtn.setDisable(true)
    runListView.getSelectionModel.selectedIndexProperty().addListener((_, _, idx) => {
      replayBtn.setDisable(idx.intValue() < 0 || idx.intValue() >= runs.size)
    })

    replayBtn.setOnAction(_ => {
      val idx = runListView.getSelectionModel.getSelectedIndex
      if (idx >= 0 && idx < runs.size) {
        val run = runs(idx)
        dlg.close()
        // Load PGN directly into the local controller — no game service required
        Platform.runLater(() => {
          if (run.pgn.trim.isEmpty) {
            showAlert("Browse Error", "No PGN stored for this game.")
          } else {
            paused = false; gameOverByTimeout = false; lastPgnLength = 0; selectedSquare = None
            gameMode = GameMode.ComputerVsComputer
            mySessionColor = 'x'; boardFlipped = false
            disconnectWebSocket()
            currentSessionId = None; updateSessionBar()
            controller.announceInitial(chess.model.Board.initial)
            controller.loadPgnMoves(run.pgn) match {
              case Left(err) => showAlert("Browse Error", s"PGN replay failed: $err")
              case Right(_)  =>
            }
            updateBoard(); updatePauseButtonVisibility()
            if (pgnDisplay != null) pgnDisplay.children.clear()
          }
        })
      }
    })

    val btnRow = new javafx.scene.layout.HBox(8, cancelBtn, replayBtn)
    btnRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT)

    val root = new javafx.scene.layout.VBox(10)
    root.setPadding(new javafx.geometry.Insets(20)); root.setPrefWidth(640)
    root.getChildren.addAll(listsRow, statusLbl, new javafx.scene.control.Separator(), btnRow)

    dlg.setScene(new javafx.scene.Scene(root))
    loadExperiments()
    dlg.showAndWait()
  }

  private[aview] def createBoardPane(): GridPane = {
    val boardPane = new GridPane {
      padding = Insets(20)
      hgap = 0
      vgap = 0
      alignment = Pos.Center
    }

    // Add file labels (a-h) at the bottom
    for (i <- 0 until 8) {
      val file = File.all(i)
      val fl = new Label(file.letter.toString) {
        font = Font.font("Arial", FontWeight.Bold, 16)
        prefWidth = 80
        prefHeight = 25
        alignment = Pos.Center
      }
      fileLabels(i) = fl
      boardPane.add(fl, i + 1, 9)
    }

    // Add rank labels (8-1) on the left
    for (gridRow <- 0 until 8) {
      val rl = new Label((8 - gridRow).toString) {
        font = Font.font("Arial", FontWeight.Bold, 16)
        prefWidth = 25
        prefHeight = 80
        alignment = Pos.Center
      }
      rankLabels(gridRow) = rl
      boardPane.add(rl, 0, gridRow + 1)
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
      style = "-fx-font-size: 10px; -fx-font-style: italic; -fx-text-fill: #888888;"
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
      prefHeight = 450
      fitToWidth = true
      style = "-fx-background-color: white;"
    }

    val navBtnStyle = "-fx-padding: 4px 10px;"
    val startButton = new Button() {
      style = navBtnStyle
      tooltip = new Tooltip("Start")
      delegate.setGraphic(lucideIcon(IconChevronsLeft))
      onAction = _ => { controller.goToMove(0); selectedSquare = None }
    }
    val backButton = new Button() {
      style = navBtnStyle
      tooltip = new Tooltip("Back")
      delegate.setGraphic(lucideIcon(IconChevronLeft))
      onAction = _ => { controller.backward(); selectedSquare = None }
    }
    val forwardButton = new Button() {
      style = navBtnStyle
      tooltip = new Tooltip("Forward")
      delegate.setGraphic(lucideIcon(IconChevronRight))
      onAction = _ => { controller.forward(); selectedSquare = None }
    }
    val endButton = new Button() {
      style = navBtnStyle
      tooltip = new Tooltip("End")
      delegate.setGraphic(lucideIcon(IconChevronsRight))
      onAction = _ => { controller.goToMove(controller.boardStates.length - 1); selectedSquare = None }
    }

    val navButtonBox = new HBox(6) {
      alignment = Pos.Center
      children = Seq(startButton, backButton, forwardButton, endButton)
    }

    // New Game button (yellow) — opens the New Game dialog
    val resetButton = new Button("New Game") {
      prefWidth = 130
      style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #f1c40f; -fx-text-fill: #333333; -fx-font-weight: bold;"
      delegate.setGraphic(lucideIcon(IconSquarePlus, 14, javafx.scene.paint.Color.web("#333")))
      contentDisplay = scalafx.scene.control.ContentDisplay.Left
      onAction = _ => showNewGameDialog()
    }

    // Join Session button (blue)
    val joinButton = new Button("Join") {
      style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold;"
      onAction = _ => showJoinSessionDialog()
    }

    // Run button (green) — visible in non-CvC modes; switches to CvC and starts play
    runButton = new Button("Run") {
      prefWidth = 120
      style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;"
      delegate.setGraphic(lucideIcon(IconPlay, 14, javafx.scene.paint.Color.WHITE))
      contentDisplay = scalafx.scene.control.ContentDisplay.Left
      onAction = _ => {
        gameMode = GameMode.ComputerVsComputer
        updatePauseButtonVisibility()
        triggerComputerMoveIfNeeded()
      }
    }

    // Pause / Continue button (orange when running, green when paused)
    pauseButton = new Button("Pause") {
      prefWidth = 130
      style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;"
      delegate.setGraphic(lucideIcon(IconPause, 14, javafx.scene.paint.Color.WHITE))
      contentDisplay = scalafx.scene.control.ContentDisplay.Left
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
      children = Seq(resetButton, joinButton, runButton, pauseButton)
    }
    pauseButton.visible = false
    pauseButton.managed = false

    // Undo / Redo / Flip tool buttons
    val toolBtnStyle = "-fx-padding: 4px 10px;"
    val undoButton = new Button() {
      style = toolBtnStyle
      tooltip = new Tooltip("Undo")
      delegate.setGraphic(lucideIcon(IconUndo))
      onAction = _ => { controller.undo(); selectedSquare = None }
    }
    val redoButton = new Button() {
      style = toolBtnStyle
      tooltip = new Tooltip("Redo")
      delegate.setGraphic(lucideIcon(IconRedo))
      onAction = _ => { controller.redo(); selectedSquare = None }
    }
    val flipButton = new Button() {
      style = toolBtnStyle
      tooltip = new Tooltip("Flip Board")
      delegate.setGraphic(lucideIcon(IconArrowUpDown))
      onAction = _ => { boardFlipped = !boardFlipped; updateBoard() }
    }
    val toolButtonBox = new HBox(6) {
      alignment = Pos.Center
      children = Seq(undoButton, redoButton, flipButton)
    }

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

    openingInfoLabel = new Label("") {
      wrapText = true
      maxWidth = 260
      style = "-fx-font-size: 11px; -fx-text-fill: #555555;"
      visible = false
      managed = false
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
        gameButtonBox,
        toolButtonBox,
        openingInfoLabel,
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
      pauseButton.text = "Continue"
      pauseButton.delegate.setGraphic(lucideIcon(IconPlay, 14, javafx.scene.paint.Color.WHITE))
      pauseButton.style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;"
    } else {
      pauseButton.text = "Pause"
      pauseButton.delegate.setGraphic(lucideIcon(IconPause, 14, javafx.scene.paint.Color.WHITE))
      pauseButton.style =
        "-fx-font-size: 13px; -fx-padding: 10px; -fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;"
    }

  /** Show/hide Pause and Run buttons depending on whether C vs C mode is active.
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

  private def updateOpeningLabel(): Unit = {
    if (openingInfoLabel == null) return
    val fen = controller.getBoardAsFEN
    openingsByFen.get(fen) match {
      case Some(o) =>
        openingInfoLabel.text = s"${o.eco}  ${o.name}\n${o.moves}"
        openingInfoLabel.visible = true
        openingInfoLabel.managed = true
        if (puzzleInfoLabel != null) {
          puzzleInfoLabel.visible = false
          puzzleInfoLabel.managed = false
        }
      case None =>
        openingInfoLabel.visible = false
        openingInfoLabel.managed = false
    }
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

      // Line break after every full move (i.e. after Black's move)
      if (i % 2 == 1) pgnDisplay.children.add(new Text("\n"))
    }

    // Always scroll to the bottom to show the latest moves
    Platform.runLater(() => {
      pgnScrollPane.vvalue = 1.0
    })
  }

  private[aview] def createFenBar(): HBox = {
    val fenLabel = new Label("FEN") {
      font = Font.font("Arial", FontWeight.Bold, 12)
      style = "-fx-text-fill: #666666;"
      padding = Insets(0, 4, 0, 0)
    }

    fenText = new scalafx.scene.text.Text {
      font = Font.font("Monospaced", FontWeight.Normal, 12)
      fill = Color.web("#333333")
      text = controller.getBoardAsFEN
    }

    val copyFenButton = new Button() {
      style = "-fx-padding: 2px 5px;"
      tooltip = new Tooltip("Copy FEN to clipboard")
      delegate.setGraphic(lucideIcon(IconCopy, 13))
      onAction = _ => {
        val content = new ClipboardContent()
        content.putString(controller.getBoardAsFEN)
        Clipboard.getSystemClipboard.setContent(content)
        delegate.setGraphic(lucideIcon(IconCheck, 13, javafx.scene.paint.Color.web("#27ae60")))
        new Thread(() => {
          Thread.sleep(1500)
          Platform.runLater(() => delegate.setGraphic(lucideIcon(IconCopy, 13)))
        }).start()
      }
    }

    new HBox(6) {
      alignment = Pos.CenterLeft
      padding = Insets(4, 12, 4, 12)
      style = "-fx-background-color: #f7f6f4;"
      children = Seq(fenLabel, fenText, copyFenButton)
    }
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
      style = "-fx-background-color: #ebebeb; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;"
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
      case ClockMode.Timed(initialMs, _, _) if (initialMs - elapsedMs) < 30000 =>
        "#e74c3c"
      case _ => "#333333"
    val activeBase = "-fx-background-color: #d5e8d4; -fx-background-radius: 4;"
    val inactiveBase = "-fx-background-color: transparent;"
    if controller.isWhiteToMove then
      whiteClockLabel.style = s"-fx-text-fill: ${colorFor(whiteElapsedMs)}; $activeBase"
      blackClockLabel.style = s"-fx-text-fill: #888888; $inactiveBase"
    else
      blackClockLabel.style = s"-fx-text-fill: ${colorFor(blackElapsedMs)}; $activeBase"
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
    if playerLabel != null then playerLabel.text = s"Time out! $winner wins."

  /** Spawn the ClockActor inside its own ActorSystem. Callbacks marshal to the JavaFX thread via Platform.runLater.
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
    // Update file/rank labels for current orientation
    for (i <- 0 until 8) {
      if (boardFlipped) {
        fileLabels(i).text = ('h' - i).toChar.toString
        rankLabels(i).text = (i + 1).toString
      } else {
        fileLabels(i).text = ('a' + i).toChar.toString
        rankLabels(i).text = (8 - i).toString
      }
    }

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
      // Map chess square to grid position based on flip state
      val row = if (boardFlipped) rank.index - 1 else 8 - rank.index
      val col = if (boardFlipped) 8 - file.index else file.index - 1
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
    if (mySessionColor != 'x') {
      val activeColor = if (controller.isWhiteToMove) 'w' else 'b'
      if (activeColor != mySessionColor) return
    }
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
      "Iterative Deepening" -> (() => new IterativeDeepeningStrategy()),
      "Opening Continuation" -> (() => new OpeningContinuationStrategy(openings))
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

    // ── Openings menu — grouped by ECO family → ECO code → variation ────────
    val openingsByFamily: Seq[(Char, List[Opening])] =
      openings
        .groupBy(_.eco.charAt(0))
        .toSeq
        .sortBy(_._1)

    val openingFamilyMenus = openingsByFamily.map { case (family, familyOpenings) =>
      val familyDesc = family match {
        case 'A' => "A — Flank Openings"
        case 'B' => "B — Semi-Open Games"
        case 'C' => "C — Open Games"
        case 'D' => "D — Closed & Semi-Closed"
        case 'E' => "E — Indian Defenses"
        case c   => s"$c"
      }
      val byEco = familyOpenings.groupBy(_.eco).toSeq.sortBy(_._1)
      val familyMenu = new Menu(s"$familyDesc  (${familyOpenings.length})")
      familyMenu.items = byEco.map { case (eco, ecoOpenings) =>
        val ecoMenu = new Menu(s"$eco  (${ecoOpenings.length})")
        ecoMenu.items = ecoOpenings.sortBy(_.name).map { opening =>
          new MenuItem(s"${opening.name}  [${opening.moveCount} plies]") {
            onAction = _ => {
              gameMode = GameMode.HumanVsHuman
              updatePauseButtonVisibility()
              selectedSquare = None
              controller.loadPgnMoves(opening.moves)
              if (openingInfoLabel != null) {
                openingInfoLabel.text = s"${opening.eco}  ${opening.name}\n${opening.moves}"
                openingInfoLabel.visible = true
                openingInfoLabel.managed = true
              }
              // Hide puzzle info when showing an opening
              if (puzzleInfoLabel != null) {
                puzzleInfoLabel.visible = false; puzzleInfoLabel.managed = false
              }
              if (puzzleLink != null) {
                puzzleLink.visible = false; puzzleLink.managed = false
              }
            }
          }
        }
        ecoMenu
      }
      familyMenu
    }

    val openingsMenu = new Menu("Openings") {
      items = openingFamilyMenus
    }

    val experimentsMenu = new Menu("Experiments") {
      items = Seq(new MenuItem("Browse Games…") {
        onAction = _ => showBrowseGamesDialog()
      })
    }

    new MenuBar {
      menus = Seq(fileMenu, gameMenu, strategyMenu, clockMenu, viewMenu, openingsMenu, puzzlesMenu, experimentsMenu)
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

    val boardPane = gui.createBoardPane()
    val capturedPanel = gui.createCapturedPanel()
    val fenBar = gui.createFenBar()
    val sessionBar = gui.createSessionBar()

    // Wrap board in a scalable Group + Pane so it resizes with the window
    val boardGroup = new scalafx.scene.Group(boardPane)
    val scaleTransform = new javafx.scene.transform.Scale(1, 1, 0, 0)
    boardGroup.delegate.getTransforms.add(scaleTransform)

    val boardContainer = new Pane {
      children = Seq(boardGroup)
      style = "-fx-background-color: transparent;"
    }
    VBox.setVgrow(boardContainer, Priority.Always)

    def rescaleBoard(): Unit = {
      val availW = boardContainer.width.value
      val availH = boardContainer.height.value
      if (availW > 10 && availH > 10) {
        val nativeW = boardPane.boundsInLocal.value.getWidth
        val nativeH = boardPane.boundsInLocal.value.getHeight
        if (nativeW > 0 && nativeH > 0) {
          val s = Math.min(availW / nativeW, availH / nativeH)
          scaleTransform.setX(s)
          scaleTransform.setY(s)
          boardGroup.layoutX = (availW - nativeW * s) / 2
          boardGroup.layoutY = (availH - nativeH * s) / 2
        }
      }
    }

    boardContainer.width.onChange((_, _, _) => rescaleBoard())
    boardContainer.height.onChange((_, _, _) => rescaleBoard())

    gui.primaryStage.scene = new Scene(1050, 900) {
      root = new BorderPane {
        top = gui.createMenuBar()
        center = new VBox {
          children = Seq(boardContainer, capturedPanel, fenBar, sessionBar)
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

    // Initial board rescale after layout is complete
    Platform.runLater(() => rescaleBoard())

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
