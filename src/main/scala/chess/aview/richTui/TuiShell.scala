package chess.aview.richTui

import cats.effect.unsafe.implicits.global
import chess.AppBindings.given
import chess.application.game.GameSessionService
import chess.controller.GameController
import chess.controller.io.FileIO
import chess.controller.io.json.circe.CirceJsonFileIO
import chess.controller.io.json.upickle.UPickleJsonFileIO
import chess.model.{Board, Color, GameSettings, PromotableRole, Square}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.io.StdIn
import scala.util.Try

final case class TuiShellSnapshot(
    activeGameId: Option[String],
    flipped: Boolean,
    running: Boolean,
    statusMessage: String,
    jsonFlavor: String,
    activeMenuTitle: Option[String],
    pendingPromptTitle: Option[String],
    infoOverlayTitle: Option[String]
)

final class TuiShell:
  private val AnsiPattern = raw"\u001B\[[;\d]*m".r
  private val SidebarValueLimit = 44
  private val StrategyCatalog = Vector(
    "opening-continuation" -> "Opening Continuation",
    "random" -> "Random",
    "greedy" -> "Greedy",
    "material-balance" -> "Material Balance",
    "piece-square" -> "Piece-Square",
    "minimax" -> "Minimax",
    "quiescence" -> "Quiescence",
    "iterative-deepening" -> "Iterative Deepening"
  )
  private val sessionService = GameSessionService()
  private val circeJsonFileIO: FileIO = new CirceJsonFileIO
  private val upickleJsonFileIO: FileIO = new UPickleJsonFileIO

  private var activeGameId: Option[String] = None
  private var flipped = false
  private var running = true
  private var statusMessage = "Welcome to the Chess TUI."
  private var jsonFlavor = "circe"
  private var activeMenu: Option[TuiMenu] = None
  private var pendingPrompt: Option[PendingPrompt] = None
  private var infoOverlay: Option[InfoOverlay] = None

  private[richTui] def snapshot: TuiShellSnapshot =
    TuiShellSnapshot(
      activeGameId = activeGameId,
      flipped = flipped,
      running = running,
      statusMessage = statusMessage,
      jsonFlavor = jsonFlavor,
      activeMenuTitle = activeMenu.map(_.title),
      pendingPromptTitle = pendingPrompt.map(_.title),
      infoOverlayTitle = infoOverlay.map(_.title)
    )

  private[richTui] def ensureInitialSessionForTest(): Unit =
    ensureInitialSession()

  private[richTui] def processInputForTest(input: String): Unit =
    processMainInput(input)

  def run(): Unit =
    ensureInitialSession()
    while running do
      render()
      processMainInput(prompt("Command / move"))

  private def ensureInitialSession(): Unit =
    if activeGameId.isEmpty then
      createGame()

  private def render(): Unit =
    clearScreen()
    println(header("Chess TUI"))
    activeController match
      case Some(controller) =>
        println(renderBoardAndSidebar(controller))
        println()
        println(renderPgn(controller))
      case None =>
        println(TerminalPalette.colorize("No active session selected.", TerminalPalette.Warning))
    println()
    println(TerminalPalette.colorize(s"Status: $statusMessage", TerminalPalette.Muted))
    println(
      TerminalPalette.colorize(
        "Hotkeys: n new  a ai  u undo  r redo  b/f history  l flip  s sessions  q quit",
        TerminalPalette.Accent
      )
    )
    println(
      TerminalPalette.colorize(
        "Menus: 1 Game  2 Move  3 History  4 Import/Export  5 Sessions  6 Help  0 Quit",
        TerminalPalette.Muted
      )
    )
    println(
      TerminalPalette.colorize(
        "Moves: e4, Nf3, O-O, e2e4. Commands: :help  :fen  :pgn  :json  :join <id>  :cancel",
        TerminalPalette.Muted
      )
    )
    activeMenu.foreach { menu =>
      println()
      println(renderMenuOverlay(menu))
    }
    pendingPrompt.foreach { prompt =>
      println()
      println(renderPendingPrompt(prompt))
    }
    infoOverlay.foreach { overlay =>
      println()
      println(renderInfoOverlay(overlay))
    }

  private def header(title: String): String =
    TerminalPalette.colorize(s"${TerminalPalette.Bold}$title${TerminalPalette.Reset}", TerminalPalette.Accent)

  private def sectionLabel(title: String): String =
    TerminalPalette.colorize(title, TerminalPalette.Accent)

  private def renderInfo(controller: GameController): Vector[String] =
    val sessionLabel = activeGameId.getOrElse("<none>")
    val moveCount = controller.pgnMoves.length
    val activeColor = if controller.isWhiteToMove then "White" else "Black"
    val jsonLabel = if jsonFlavor == "circe" then "Circe" else "uPickle"
    val settings = activeGameId.flatMap(gameId => sessionService.getSettings(gameId).unsafeRunSync())
    val primary =
      Vector(
        header("Session"),
        s"id: $sessionLabel",
        s"mode: ${settings.map(gameModeLabel).getOrElse("HvH")}",
        s"status: ${controller.gameStatus}",
        s"turn: $activeColor",
        s"view: ${historyViewLabel(controller)}",
        s"moves: $moveCount",
        s"history index: ${controller.currentIndex}/${controller.boardStates.length - 1}",
        s"can undo/redo: ${controller.canUndo}/${controller.canRedo}",
        s"auto AI: ${settings.exists(isAiTurn(_, controller))}"
      )

    val playerSection =
      settings match
        case Some(current) =>
          Vector(
            "",
            sectionLabel("Players"),
            s"white: ${playerSummary(current.whiteIsHuman, current.whiteStrategy)}",
            s"black: ${playerSummary(current.blackIsHuman, current.blackStrategy)}"
          )
        case None => Vector.empty

    val secondary =
      Vector(
        sectionLabel("Board"),
        s"board flipped: $flipped",
        s"json backend: $jsonLabel",
        s"fen: ${truncateInline(controller.getBoardAsFEN, SidebarValueLimit)}"
      )

    primary ++ playerSection ++ Vector("") ++ secondary

  private def renderBoardAndSidebar(controller: GameController): String =
    val boardLines = BoardRenderer.render(controller.board, flipped = flipped).linesIterator.toVector
    val infoLines = renderInfo(controller)
    val sidebarStart = BoardRenderer.VisibleWidth + 3
    val sidebarTop = 1
    val totalRows = boardLines.length.max(sidebarTop + infoLines.length)

    Vector.tabulate(totalRows) { idx =>
      val boardLine = boardLines.lift(idx).getOrElse("")
      val sidebarLine =
        if idx >= sidebarTop then infoLines.lift(idx - sidebarTop).getOrElse("")
        else ""
      if sidebarLine.isEmpty then boardLine
      else padVisible(boardLine, sidebarStart) + sidebarLine
    }.mkString("\n")

  private def renderPgn(controller: GameController): String =
    val pgn = renderAnnotatedPgn(controller)
    s"${header("PGN")}\n  ${if pgn.isBlank then "<empty>" else pgn}"

  private def renderMenuOverlay(menu: TuiMenu): String =
    val title = header(s"${menu.title} Menu")
    val lines = menu.options.map { case (key, label) => s"  [$key] $label" }
    (title +: lines :+ "  [0] Back").mkString("\n")

  private def renderPendingPrompt(prompt: PendingPrompt): String =
    val lines = Vector(
      header(prompt.title),
      s"  ${prompt.hint}",
      "  Submit your value in the main prompt. Use :cancel to abort."
    )
    lines.mkString("\n")

  private def renderInfoOverlay(overlay: InfoOverlay): String =
    (header(overlay.title) +: overlay.lines.map("  " + _)).mkString("\n")

  private def processMainInput(input: String): Unit =
    input.trim match
      case "" => ()
      case value if pendingPrompt.exists(prompt => handlePendingInput(prompt, value)) => ()
      case value if infoOverlay.exists(_ => handleInfoOverlayInput(value)) => ()
      case value if activeMenu.exists(menu => handleMenuInput(menu, value)) => ()
      case value if value.startsWith(":") => runColonCommand(value.drop(1).trim)
      case value if runHotkey(value) => ()
      case "1" => openMenu(TuiMenu.Game)
      case "2" => openMenu(TuiMenu.Move)
      case "3" => openMenu(TuiMenu.History)
      case "4" => openMenu(TuiMenu.ImportExport)
      case "5" => openMenu(TuiMenu.Session)
      case "6" => openMenu(TuiMenu.Help)
      case "0" => running = false
      case value if tryApplyMove(value) => ()
      case other => statusMessage = s"Unknown command or move: $other"

  private def openMenu(menu: TuiMenu): Unit =
    activeMenu = Some(menu)
    pendingPrompt = None
    infoOverlay = None
    statusMessage = s"Opened ${menu.title.toLowerCase} menu."

  private def handleMenuInput(menu: TuiMenu, input: String): Boolean =
    input.trim match
      case "0" =>
        activeMenu = None
        statusMessage = s"Closed ${menu.title.toLowerCase} menu."
        true
      case choice =>
        val handled = menu.handle(this, choice)
        if handled then activeMenu = None
        handled

  private def handlePendingInput(prompt: PendingPrompt, input: String): Boolean =
    input.trim match
      case "" => true
      case value if value == ":cancel" || value == "0" =>
        pendingPrompt = None
        statusMessage = s"Cancelled ${prompt.title.toLowerCase}."
        true
      case value =>
        pendingPrompt = None
        prompt.onSubmit(value)
        true

  private def handleInfoOverlayInput(input: String): Boolean =
    input.trim match
      case "" | "0" | ":cancel" =>
        infoOverlay = None
        statusMessage = "Closed overlay."
        true
      case _ =>
        false

  private def runHotkey(input: String): Boolean =
    input.trim.toLowerCase match
      case "q" =>
        running = false
        true
      case "n" =>
        promptGameMode()
        true
      case "u" =>
        withController(_.undo(), "Undid last move.")
        true
      case "r" =>
        withController(_.redo(), "Redid move.")
        true
      case "b" =>
        withController(_.backward(), "Moved one step backward in history.")
        true
      case "f" =>
        withController(_.forward(), "Moved one step forward in history.")
        true
      case "l" =>
        flipped = !flipped
        statusMessage = s"Board flipped: $flipped"
        true
      case "a" =>
        computeAiMove()
        true
      case "s" =>
        listSessions()
        true
      case _ =>
        false

  private def runColonCommand(raw: String): Unit =
    val parts = raw.split("\\s+", 2).toList.filter(_.nonEmpty)
    parts match
      case Nil => ()
      case cmd :: rest =>
        val arg = rest.headOption.getOrElse("")
        cmd.toLowerCase match
          case "q" | "quit" | "exit" => running = false
          case "u" | "undo"          => withController(_.undo(), "Undid last move.")
          case "redo" | "r"          => withController(_.redo(), "Redid move.")
          case "back" | "b"          => withController(_.backward(), "Moved one step backward in history.")
          case "forward" | "f"       => withController(_.forward(), "Moved one step forward in history.")
          case "start" | "begin"     => withController(_.goToMove(0), "Jumped to the beginning.")
          case "end"                 => withController(ctrl => ctrl.goToMove(ctrl.boardStates.length - 1), "Jumped to the latest position.")
          case "flip"                => flipped = !flipped; statusMessage = s"Board flipped: $flipped"
          case "fen"                 => if arg.nonEmpty then importFenFrom(arg) else statusFromController(ctrl => s"Current FEN: ${ctrl.getBoardAsFEN}")
          case "pgn"                 => if arg.nonEmpty then importPgnFrom(arg) else statusFromController(ctrl => s"Current PGN: ${if ctrl.pgnText.isBlank then "<empty>" else ctrl.pgnText}")
          case "json"                => if arg.nonEmpty then importJsonFrom(arg) else exportJson()
          case "writefen"            => exportText("FEN", ctrl => ctrl.getBoardAsFEN, ".fen")
          case "writepgn"            => exportText("PGN", ctrl => ctrl.pgnText, ".pgn")
          case "writejson"           => exportJson()
          case "new"                 => createGame()
          case "join"                => joinSessionById(arg)
          case "sessions" | "ls"     => listSessions()
          case "del" | "delete"      => deleteCurrentSession()
          case "ai"                  => runAiCommand(arg)
          case "menu" | "m"          => statusMessage = "Use menu digits 1-6 from the main prompt."
          case "cancel"              => pendingPrompt = None; infoOverlay = None; statusMessage = "Cancelled pending input."
          case "help"                => helpMenu()
          case _                     => statusMessage = s"Unknown :command '$cmd'. Try :help."

  private[richTui] def gameMenu(choice: String): Boolean =
    choice.trim match
      case "1" => promptGameMode()
      case "2" => createGameWithFen()
      case "3" => statusFromController(ctrl => s"Current FEN: ${ctrl.getBoardAsFEN}")
      case "4" => statusFromController(ctrl => s"Current PGN: ${if ctrl.pgnText.isBlank then "<empty>" else ctrl.pgnText}")
      case "5" => promptAiStrategy()
      case "6" =>
        flipped = !flipped
        statusMessage = s"Board flipped: $flipped"
      case _   => return false
    true

  private[richTui] def moveMenu(choice: String): Boolean =
    choice.trim match
      case "1" => applyPgnMove()
      case "2" => applyCoordinateMove()
      case "3" => withController(_.undo(), "Undid last move.")
      case "4" => withController(_.redo(), "Redid move.")
      case _   => return false
    true

  private[richTui] def historyMenu(choice: String): Boolean =
    choice.trim match
      case "1" => withController(_.backward(), "Moved one step backward in history.")
      case "2" => withController(_.forward(), "Moved one step forward in history.")
      case "3" => withController(_.goToMove(0), "Jumped to the beginning.")
      case "4" => withController(ctrl => ctrl.goToMove(ctrl.boardStates.length - 1), "Jumped to the latest position.")
      case "5" =>
        promptHistoryIndex()
      case "6" =>
        showHistoryOverlay()
      case _ => return false
    true

  private[richTui] def importExportMenu(choice: String): Boolean =
    choice.trim match
      case "1" => importFen()
      case "2" => importPgn()
      case "3" => importJson()
      case "4" => exportText("FEN", ctrl => ctrl.getBoardAsFEN, ".fen")
      case "5" => exportText("PGN", ctrl => ctrl.pgnText, ".pgn")
      case "6" => exportJson()
      case "7" =>
        jsonFlavor = if jsonFlavor == "circe" then "upickle" else "circe"
        statusMessage = s"JSON backend switched to $jsonFlavorLabel."
      case _ => return false
    true

  private[richTui] def sessionMenu(choice: String): Boolean =
    choice.trim match
      case "1" => listSessions()
      case "2" => promptJoinSession()
      case "3" => deleteCurrentSession()
      case "4" =>
        sessionService.deleteAllGames.unsafeRunSync()
        activeGameId = None
        ensureInitialSession()
        statusMessage = "Deleted all sessions and created a fresh game."
      case _ => return false
    true

  private[richTui] def helpMenu(choice: String = "1"): Boolean =
    choice.trim match
      case "1" | "" =>
        statusMessage =
          "Moves are direct: e4, Nf3, e2e4. Hotkeys: n/u/r/b/f/l/a/s/q. Useful commands: :undo, :redo, :back, :forward, :fen, :pgn, :json, :new, :join <id>, :ai minimax, :flip, :quit."
        true
      case _ =>
        false

  private def createGame(settings: GameSettings = GameSettings()): Unit =
    sessionService.createGame(settings = settings).unsafeRunSync() match
      case Right((gameId, fen)) =>
        activeGameId = Some(gameId)
        statusMessage = s"Created game $gameId with position $fen"
        triggerConfiguredAiIfNeeded()
      case Left(error) =>
        statusMessage = error

  private def createGameWithFen(): Unit =
    val fen = promptMultiline("Paste FEN or enter a file path")
    val resolved = resolveInput(fen)
    sessionService.createGame(startFen = Some(resolved)).unsafeRunSync() match
      case Right((gameId, _)) =>
        activeGameId = Some(gameId)
        statusMessage = s"Created game $gameId from FEN."
      case Left(error) =>
        statusMessage = error

  private def applyPgnMove(): Unit =
    withController { controller =>
      val move = prompt("PGN move")
      applyMoveText(controller, move)
    }

  private def applyCoordinateMove(): Unit =
    withController { controller =>
      parseCoordinateMove(prompt("Coordinate move (e2e4 or e7e8=Q)")) match
        case Some((from, to, promotion)) =>
          handleMoveResult(controller.applyMove(from, to, promotion), s"${from}${to}")
        case None =>
          statusMessage = "Invalid coordinate move."
    }

  private def computeAiMove(): Unit =
    promptAiStrategy()

  private def importFen(): Unit =
    pendingPrompt = Some(
      PendingPrompt(
        "Import FEN",
        "Paste a FEN string or a file path.",
        importFenFrom
      )
    )
    statusMessage = "FEN import prompt opened."

  private def importPgn(): Unit =
    pendingPrompt = Some(
      PendingPrompt(
        "Import PGN",
        "Paste a PGN line or a file path.",
        importPgnFrom
      )
    )
    statusMessage = "PGN import prompt opened."

  private def importJson(): Unit =
    pendingPrompt = Some(
      PendingPrompt(
        s"Import ${jsonFlavorLabel} JSON",
        s"Paste ${jsonFlavorLabel} JSON on one line or a file path.",
        importJsonFrom
      )
    )
    statusMessage = s"${jsonFlavorLabel} JSON import prompt opened."

  private def exportJson(): Unit =
    exportText(s"${jsonFlavorLabel} JSON", ctrl => currentJsonFileIO.save(ctrl.board), ".json")

  private def exportText(label: String, producer: GameController => String, defaultSuffix: String): Unit =
    withController { controller =>
      val payload = producer(controller)
      val lines = payload.linesIterator.toVector
      infoOverlay = Some(
        InfoOverlay(
          label,
          lines ++ Vector("", s"Use :write${defaultSuffix.drop(1)} to save this output to a file.")
        )
      )
      statusMessage = s"Showing $label."
    }

  private def listSessions(): Unit =
    val sessions = sessionService.listGames.unsafeRunSync()
    val lines =
      if sessions.isEmpty then Vector("No sessions.")
      else sessions.map { case (id, status, settings) =>
        s"$id | $status | white=${playerLabel(settings.whiteIsHuman, settings.whiteStrategy)} | black=${playerLabel(settings.blackIsHuman, settings.blackStrategy)}"
      }.toVector
    infoOverlay = Some(InfoOverlay("Sessions", lines :+ "" :+ "Press Enter, 0, or :cancel to close."))
    statusMessage = s"Showing ${sessions.size} session(s)."

  private def joinSession(): Unit =
    promptJoinSession()

  private def deleteCurrentSession(): Unit =
    activeGameId match
      case None =>
        statusMessage = "No active session."
      case Some(gameId) =>
        if sessionService.deleteGame(gameId).unsafeRunSync() then
          activeGameId = None
          ensureInitialSession()
          statusMessage = s"Deleted session $gameId."
        else statusMessage = s"Could not delete session $gameId."

  private def playerLabel(isHuman: Boolean, strategy: String): String =
    if isHuman then "human" else s"ai:$strategy"

  private def historyViewLabel(controller: GameController): String =
    if controller.currentIndex == controller.boardStates.length - 1 then "live"
    else s"history (${controller.currentIndex}/${controller.boardStates.length - 1})"

  private def renderAnnotatedPgn(controller: GameController): String =
    if controller.pgnMoves.isEmpty then ""
    else
      controller.pgnMoves.grouped(2).zipWithIndex.map { case (pair, idx) =>
        val basePly = idx * 2 + 1
        val annotatedPair = pair.zipWithIndex.map { case (move, offset) =>
          val ply = basePly + offset
          if ply == controller.currentIndex then s"[$move]"
          else move
        }
        s"${idx + 1}. ${annotatedPair.mkString(" ")}"
      }.mkString(" ")

  private def showHistoryOverlay(): Unit =
    activeController match
      case None =>
        statusMessage = "No active session."
      case Some(controller) =>
        val lines =
          if controller.pgnMoves.isEmpty then Vector("<no moves yet>")
          else
            controller.pgnMoves.grouped(2).zipWithIndex.map { case (pair, idx) =>
              val basePly = idx * 2 + 1
              val annotatedPair = pair.zipWithIndex.map { case (move, offset) =>
                val ply = basePly + offset
                val marker =
                  if controller.currentIndex == ply then ">"
                  else " "
                s"$marker$move"
              }
              s"${idx + 1}. ${annotatedPair.mkString(" ")}"
            }.toVector
        infoOverlay = Some(
          InfoOverlay(
            "History",
            lines ++ Vector("", s"Current view: ${historyViewLabel(controller)}", "Press Enter, 0, or :cancel to close.")
          )
        )
        statusMessage = "Showing move history."

  private def playerSummary(isHuman: Boolean, strategy: String): String =
    if isHuman then "human"
    else s"ai (${strategyDisplayName(strategy)})"

  private def strategyDisplayName(strategyId: String): String =
    StrategyCatalog.find(_._1 == strategyId).map(_._2).getOrElse(strategyId)

  private def gameModeLabel(settings: GameSettings): String =
    if settings.whiteIsHuman && settings.blackIsHuman then "HvH"
    else if settings.whiteIsHuman && !settings.blackIsHuman then "HvC"
    else if !settings.whiteIsHuman && settings.blackIsHuman then "CvH"
    else "CvC"

  private def isAiTurn(settings: GameSettings, controller: GameController): Boolean =
    if controller.isWhiteToMove then !settings.whiteIsHuman else !settings.blackIsHuman

  private def parseCoordinateMove(move: String): Option[(Square, Square, Option[PromotableRole])] =
    val (coords, promoStr) = move.indexOf('=') match
      case -1  => (move, "")
      case idx => (move.take(idx), move.drop(idx + 1))

    if coords.length != 4 then None
    else
      for
        from <- Square.fromString(coords.substring(0, 2))
        to <- Square.fromString(coords.substring(2, 4))
      yield
        val promotion = promoStr.trim.toUpperCase match
          case "Q" => Some(PromotableRole.Queen)
          case "R" => Some(PromotableRole.Rook)
          case "B" => Some(PromotableRole.Bishop)
          case "N" => Some(PromotableRole.Knight)
          case _   => None
        (from, to, promotion)

  private def tryApplyMove(move: String): Boolean =
    activeController match
      case Some(controller) =>
        val normalized = move.trim
        val looksLikeCoordinate = parseCoordinateMove(normalized).isDefined
        val looksLikePgn =
          normalized.matches("(?i)(o-o(-o)?|[kqrbn]?[a-h]?[1-8]?x?[a-h][1-8](=[qrbn])?[+#]?|[a-h]x?[a-h][1-8](=[qrbn])?[+#]?)")
        if looksLikeCoordinate || looksLikePgn then
          applyMoveText(controller, normalized)
          true
        else false
      case None => false

  private def applyMoveText(controller: GameController, move: String): Unit =
    parseCoordinateMove(move) match
      case Some((from, to, promotion)) =>
        handleMoveResult(controller.applyMove(from, to, promotion), move)
      case None =>
        handleMoveResult(controller.applyPgnMove(move), move)

  private def handleMoveResult(result: chess.model.MoveResult, moveLabel: String): Unit =
    result match
      case chess.model.MoveResult.Moved(_, event) =>
        statusMessage = s"Applied move $moveLabel (${event.toString})."
      case chess.model.MoveResult.Failed(_, error) =>
        statusMessage = s"Move failed: ${error.message}"

  private def runAiCommand(arg: String): Unit =
    val strategyId =
      if arg.nonEmpty then normalizeStrategyChoice(arg.trim).getOrElse(arg.trim)
      else ""
    if strategyId.nonEmpty then computeAiMoveWith(strategyId) else promptAiStrategy()

  private def computeAiMoveWith(strategyId: String): Unit =
    activeGameId match
      case None => statusMessage = "No active session."
      case Some(gameId) =>
        sessionService.computeAiMove(gameId, strategyId).unsafeRunSync() match
          case Right(Some(move)) =>
            sessionService.makeMove(gameId, move).unsafeRunSync() match
              case Right((_, event)) =>
                statusMessage = s"AI played $move${event.fold("")(e => s" ($e)")}"
                triggerConfiguredAiIfNeeded()
              case Left(error) =>
                statusMessage = s"AI move failed: $error"
          case Right(None) =>
            statusMessage = "AI found no legal move."
          case Left(error) =>
            statusMessage = error

  private def importFenFrom(input: String): Unit =
    withController { controller =>
      controller.loadFromFEN(resolveInput(input)) match
        case Right(_)     => statusMessage = "Imported FEN successfully."
        case Left(error)  => statusMessage = error
    }

  private def importPgnFrom(input: String): Unit =
    withController { controller =>
      controller.loadPgnMoves(resolveInput(input)) match
        case Right(_)    => statusMessage = "Imported PGN successfully."
        case Left(error) => statusMessage = error
    }

  private def importJsonFrom(input: String): Unit =
    withController { controller =>
      currentJsonFileIO.load(resolveInput(input)) match
        case scala.util.Success(board) =>
          controller.announceInitial(board)
          statusMessage = s"Imported ${jsonFlavorLabel} JSON successfully."
        case scala.util.Failure(error) =>
          statusMessage = s"JSON import failed: ${error.getMessage}"
    }

  private def joinSessionById(gameId: String): Unit =
    if gameId.isBlank then statusMessage = "Session id is required."
    else
      sessionService.getGame(gameId).unsafeRunSync() match
        case Some(_) =>
          activeGameId = Some(gameId)
          statusMessage = s"Joined session $gameId"
        case None =>
          statusMessage = s"Unknown session: $gameId"

  private def withController(action: GameController => Unit, successMessage: String = ""): Unit =
    activeController match
      case Some(controller) =>
        action(controller)
        if successMessage.nonEmpty then statusMessage = successMessage
      case None =>
        statusMessage = "No active session."

  private def statusFromController(render: GameController => String): Unit =
    activeController match
      case Some(controller) => statusMessage = render(controller)
      case None             => statusMessage = "No active session."

  private def activeController: Option[GameController] =
    activeGameId.flatMap(gameId => sessionService.getGame(gameId).unsafeRunSync())

  private def currentJsonFileIO: FileIO =
    if jsonFlavor == "circe" then circeJsonFileIO else upickleJsonFileIO

  private def jsonFlavorLabel: String =
    if jsonFlavor == "circe" then "Circe" else "uPickle"

  private def visibleLength(text: String): Int =
    AnsiPattern.replaceAllIn(text, "").length

  private def padVisible(text: String, targetWidth: Int): String =
    text + (" " * (targetWidth - visibleLength(text)).max(0))

  private def truncateInline(text: String, limit: Int): String =
    if text.length <= limit then text else text.take((limit - 3).max(0)) + "..."

  private def clearScreen(): Unit =
    print("\u001b[2J\u001b[H")

  private def prompt(label: String): String =
    print(TerminalPalette.colorize(s"$label> ", TerminalPalette.Bold))
    val line = StdIn.readLine()
    if line == null then
      running = false
      ""
    else line

  private def promptMultiline(label: String): String =
    val first = prompt(label).trim
    if first == "<<EOF" then
      Iterator
        .continually(StdIn.readLine())
        .takeWhile(line => line != null && line != "EOF")
        .mkString("\n")
    else first

  private def readInt(label: String): Option[Int] =
    Try(prompt(label).trim.toInt).toOption

  private def resolveInput(input: String): String =
    val trimmed = input.trim
    val path = Path.of(trimmed)
    if trimmed.nonEmpty && Files.exists(path) then Files.readString(path, StandardCharsets.UTF_8)
    else input

  private def writeFile(path: String, content: String): Unit =
    Files.writeString(Path.of(path), content, StandardCharsets.UTF_8)

  private def promptAiStrategy(): Unit =
    infoOverlay = Some(
      InfoOverlay(
        "AI Strategies",
        StrategyCatalog.zipWithIndex.map { case ((_, label), idx) => s"${idx + 1}. $label" } :+
          "" :+ "Enter the number or strategy id in the main prompt. Use :cancel to abort."
      )
    )
    pendingPrompt = Some(
      PendingPrompt(
        "AI Strategy",
        "Enter a strategy number or id.",
        strategy =>
          normalizeStrategyChoice(strategy.trim) match
            case Some(strategyId) => computeAiMoveWith(strategyId)
            case None             => statusMessage = s"Unknown AI strategy: $strategy"
      )
    )
    statusMessage = "AI strategy prompt opened."

  private def promptGameMode(): Unit =
    infoOverlay = Some(
      InfoOverlay(
        "New Game Mode",
        Vector(
          "1. Human vs Human",
          "2. Human vs Computer",
          "3. Computer vs Human",
          "4. Computer vs Computer",
          "",
          "Enter the mode number in the main prompt. Use :cancel to abort."
        )
      )
    )
    pendingPrompt = Some(
      PendingPrompt(
        "Game Mode",
        "Choose 1-4.",
        mode =>
          mode.trim match
            case "1" => createGame(GameSettings())
            case "2" => createGame(GameSettings(whiteIsHuman = true, blackIsHuman = false))
            case "3" => createGame(GameSettings(whiteIsHuman = false, blackIsHuman = true))
            case "4" => createGame(GameSettings(whiteIsHuman = false, blackIsHuman = false))
            case _   => statusMessage = s"Unknown game mode: $mode"
      )
    )
    statusMessage = "Game mode prompt opened."

  private def promptJoinSession(): Unit =
    pendingPrompt = Some(
      PendingPrompt(
        "Join Session",
        "Paste the session id to join.",
        gameId => joinSessionById(gameId.trim)
      )
    )
    statusMessage = "Enter a session id."

  private def promptHistoryIndex(): Unit =
    pendingPrompt = Some(
      PendingPrompt(
        "History Index",
        "Enter the move index to jump to.",
        value =>
          Try(value.trim.toInt).toOption match
            case Some(idx) =>
              withController(_.goToMove(idx), s"Jumped to move index $idx.")
            case None =>
              statusMessage = s"Invalid move index: $value"
      )
    )
    statusMessage = "Enter a move index."

  private def normalizeStrategyChoice(choice: String): Option[String] =
    choice match
      case "" => None
      case raw if raw.forall(_.isDigit) =>
        raw.toIntOption.flatMap(idx => StrategyCatalog.lift(idx - 1).map(_._1))
      case raw =>
        StrategyCatalog.find(_._1 == raw).map(_._1)

  private def triggerConfiguredAiIfNeeded(): Unit =
    activeGameId.foreach(runConfiguredAiIfNeeded)

  private def runConfiguredAiIfNeeded(gameId: String): Unit =
    sessionService.getSettings(gameId).unsafeRunSync() match
      case None => ()
      case Some(settings) =>
        @annotation.tailrec
        def loop(limit: Int): Unit =
          if limit > 0 then
            sessionService.getGame(gameId).unsafeRunSync() match
              case Some(controller) =>
                val whiteToMove = controller.isWhiteToMove
                val currentIsHuman = if whiteToMove then settings.whiteIsHuman else settings.blackIsHuman
                val strategyId = if whiteToMove then settings.whiteStrategy else settings.blackStrategy
                if !currentIsHuman then
                  sessionService.computeAiMove(gameId, strategyId).unsafeRunSync() match
                    case Right(Some(move)) =>
                      sessionService.makeMove(gameId, move).unsafeRunSync() match
                        case Right(_) => loop(limit - 1)
                        case Left(error) => statusMessage = s"Configured AI move failed: $error"
                    case Right(None) =>
                      statusMessage = "Configured AI found no legal move."
                    case Left(error) =>
                      statusMessage = error
              case None => ()
        loop(200)

object TuiShell:
  def run(): Unit =
    ConsoleEncoding.enableUtf8IfNeeded()
    new TuiShell().run()

private final case class PendingPrompt(
    title: String,
    hint: String,
    onSubmit: String => Unit
)

private final case class InfoOverlay(
    title: String,
    lines: Vector[String]
)

private enum TuiMenu(val title: String, val options: Vector[(String, String)]):
  def handle(shell: TuiShell, choice: String): Boolean = this match
    case TuiMenu.Game         => shell.gameMenu(choice)
    case TuiMenu.Move         => shell.moveMenu(choice)
    case TuiMenu.History      => shell.historyMenu(choice)
    case TuiMenu.ImportExport => shell.importExportMenu(choice)
    case TuiMenu.Session      => shell.sessionMenu(choice)
    case TuiMenu.Help         => shell.helpMenu(choice)

  case Game extends TuiMenu(
    "Game",
    Vector(
      "1" -> "New game",
      "2" -> "New game from FEN",
      "3" -> "Show FEN",
      "4" -> "Show PGN",
      "5" -> "Compute AI move",
      "6" -> "Flip board"
    )
  )

  case Move extends TuiMenu(
    "Move",
    Vector(
      "1" -> "PGN move",
      "2" -> "Coordinate move",
      "3" -> "Undo",
      "4" -> "Redo"
    )
  )

  case History extends TuiMenu(
    "History",
    Vector(
      "1" -> "Backward",
      "2" -> "Forward",
      "3" -> "Beginning",
      "4" -> "End",
      "5" -> "Jump to move index",
      "6" -> "Show move history"
    )
  )

  case ImportExport extends TuiMenu(
    "Import/Export",
    Vector(
      "1" -> "Import FEN",
      "2" -> "Import PGN",
      "3" -> "Import JSON",
      "4" -> "Export FEN",
      "5" -> "Export PGN",
      "6" -> "Export JSON",
      "7" -> "Switch JSON backend"
    )
  )

  case Session extends TuiMenu(
    "Sessions",
    Vector(
      "1" -> "List sessions",
      "2" -> "Join session",
      "3" -> "Delete current session",
      "4" -> "Delete all sessions"
    )
  )

  case Help extends TuiMenu(
    "Help",
    Vector(
      "1" -> "Show controls and command help"
    )
  )
