package chess.lichess

import cats.effect.kernel.Ref
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import chess.controller.{ComputerPlayer, MoveStrategy}
import chess.controller.strategy.{
  DeepeningOpeningEndgameStrategy,
  IterativeDeepeningEndgameStrategy,
  IterativeDeepeningStrategy
}
import chess.model.Color
import io.circe.Json
import scala.concurrent.duration.*

/** Long-running process: Lichess bot event stream, auto-accept challenges, play games via [[Uci]] + [[ComputerPlayer]]. */
object LichessBotMain extends IOApp.Simple:

  /** Lowercase Lichess `status` field values that mean the game is over. */
  private val TerminalStatuses = Set(
    "mate",
    "resign",
    "stalemate",
    "draw",
    "outoftime",
    "timeout",
    "cheat",
    "aborted",
    "nostart",
    "unknownfinish"
  )

  def run: IO[Unit] =
    LichessBotConfig.load match
      case Left(msg) =>
        IO.println(s"Config error: $msg") >> IO.raiseError(new IllegalArgumentException(msg))
      case Right(cfg) =>
        LichessClient.resource(cfg).use { api =>
          for
            myId <- api.fetchAccountId
            _ <- IO.println(
              s"[lichess] Connected as account id=$myId (autoAccept=${cfg.autoAcceptChallenges}, onlyBots=${cfg.onlyBotChallengers})"
            )
            _ <- eventLoop(cfg, api, myId)
          yield ()
        }

  /** Reconnecting event stream loop (Lichess may close idle streams). */
  private def eventLoop(cfg: LichessBotConfig, api: LichessClient, myId: String): IO[Unit] =
    def step: IO[Unit] =
      api.botEventStream
        .evalMap(handleBotEvent(cfg, api, myId, _))
        .compile
        .drain
        .handleErrorWith(e => IO.println(s"[lichess] event stream error: $e") >> IO.sleep(5.seconds)) >> step
    step

  private def handleBotEvent(cfg: LichessBotConfig, api: LichessClient, myId: String, json: Json): IO[Unit] =
    val c = json.hcursor
    c.get[String]("type").toOption match
      case Some("challenge") =>
        val ch  = c.downField("challenge")
        val idO = ch.get[String]("id").toOption
        val challengerBot =
          ch.downField("challenger").get[Boolean]("bot").toOption.getOrElse(false)
        idO match
          case None => IO.unit
          case Some(challengeId) =>
            val wantAccept =
              cfg.autoAcceptChallenges && (!cfg.onlyBotChallengers || challengerBot)
            if wantAccept then
              IO.println(s"[lichess] Accepting challenge $challengeId") >> api.acceptChallenge(challengeId)
            else if cfg.onlyBotChallengers && !challengerBot then
              IO.println(s"[lichess] Declining non-bot challenge $challengeId") >> api.declineChallenge(challengeId)
            else IO.unit
      case Some("gameStart") =>
        val g    = c.downField("game")
        val idO  = g.get[String]("id").toOption
        val hint = g.get[String]("color").toOption.flatMap:
          case "white" => Some(Color.White)
          case "black" => Some(Color.Black)
          case _       => None
        idO match
          case None => IO.unit
          case Some(gameId) =>
            IO.println(s"[lichess] Game start $gameId colorHint=$hint") >>
              runGameSession(cfg, api, myId, gameId, hint).start.void
      case Some("gameFinish") =>
        IO.println("[lichess] gameFinish")
      case _ =>
        IO.unit

  private def runGameSession(
      cfg: LichessBotConfig,
      api: LichessClient,
      myUserId: String,
      gameId: String,
      colorHint: Option[Color]
  ): IO[Unit] =
    val strategy = BotStrategy(cfg.strategyId, cfg.maxThinkMs)
    val computer = new ComputerPlayer(strategy)
    Ref.of[IO, Option[Color]](colorHint).flatMap { colorRef =>
      api
        .botGameStream(gameId)
        .evalMap(json => handleGameJson(cfg, api, myUserId, gameId, colorRef, computer, json))
        .compile
        .drain
        .handleErrorWith(e => IO.println(s"[lichess] game $gameId: $e"))
    }

  private def handleGameJson(
      cfg: LichessBotConfig,
      api: LichessClient,
      myUserId: String,
      gameId: String,
      colorRef: Ref[IO, Option[Color]],
      computer: ComputerPlayer,
      json: Json
  ): IO[Unit] =
    val c = json.hcursor
    val typ = c.get[String]("type").toOption.getOrElse("")
    if typ != "gameFull" && typ != "gameState" then IO.unit
    else
      handleGamePayload(cfg, api, myUserId, gameId, colorRef, computer, c, typ)

  private def handleGamePayload(
      cfg: LichessBotConfig,
      api: LichessClient,
      myUserId: String,
      gameId: String,
      colorRef: Ref[IO, Option[Color]],
      computer: ComputerPlayer,
      c: io.circe.HCursor,
      typ: String
  ): IO[Unit] =
    val (movesUci, statusRaw) = movesAndStatus(c)
    val status                = statusRaw.trim.toLowerCase
    val isTerminal            = TerminalStatuses.contains(status)

    if isTerminal then
      IO.whenA(status.nonEmpty)(IO.println(s"[lichess] game $gameId status=$status"))
    else
      for
        _ <- colorRef.update(prev => prev.orElse(colorFromGameFull(c, myUserId)))
        myColorOpt <- colorRef.get
        boardEither = Uci.applyMovesFromStart(movesUci)
        plies = movesUci.trim.split("\\s+").count(_.nonEmpty)
        side = Uci.sideToMoveAfterPlies(plies)
        (wMs, bMs) = clockFields(c)
        _ <- (myColorOpt, boardEither) match
          case (_, Left(err)) =>
            IO.println(s"[lichess] game $gameId UCI replay failed: $err")
          case (Some(myColor), Right(board)) if myColor == side =>
            val budget = thinkBudget(cfg, myColor, wMs, bMs)
            tuneStrategy(computer.strategy, budget)
            computer.move(board, myColor, _ => false) match
              case None =>
                IO.println(s"[lichess] game $gameId: no move returned (game over locally?)")
              case Some((from, to, promo)) =>
                val uci = Uci.encode(from, to, promo)
                IO.println(s"[lichess] game $gameId play $uci (budget=${budget}ms)") >>
                  api.postBotMove(gameId, uci)
          case (None, _) =>
            IO.whenA(typ == "gameFull")(
              IO.println(
                s"[lichess] game $gameId: could not resolve bot color from payload (waiting for more events)"
              )
            )
          case _ =>
            IO.unit
      yield ()

  private def movesAndStatus(c: io.circe.HCursor): (String, String) =
    val st = c.downField("state")
    val moves =
      st.get[String]("moves").toOption.orElse(c.get[String]("moves").toOption).getOrElse("")
    val status =
      st.get[String]("status").toOption.orElse(c.get[String]("status").toOption).getOrElse("")
    (moves, status)

  private def clockFields(c: io.circe.HCursor): (Long, Long) =
    val st = c.downField("state")
    val w  = st.get[Long]("wtime").toOption.orElse(c.get[Long]("wtime").toOption).getOrElse(300_000L)
    val b  = st.get[Long]("btime").toOption.orElse(c.get[Long]("btime").toOption).getOrElse(300_000L)
    (w, b)

  /** Read Lichess `white` / `black` player id from a side object (supports `id`, `user.id`, `userId`). */
  private def userIdFromSideNode(sideNode: io.circe.ACursor): Option[String] =
    sideNode.get[String]("id").toOption
      .orElse(sideNode.downField("user").get[String]("id").toOption)
      .orElse(sideNode.get[String]("userId").toOption)

  private def colorFromGameFull(c: io.circe.HCursor, myId: String): Option[Color] =
    val players = c.downField("players")
    val w =
      userIdFromSideNode(c.downField("white")).orElse(
        if players.succeeded then userIdFromSideNode(players.downField("white")) else None
      )
    val b =
      userIdFromSideNode(c.downField("black")).orElse(
        if players.succeeded then userIdFromSideNode(players.downField("black")) else None
      )
    if w.exists(_.equalsIgnoreCase(myId)) then Some(Color.White)
    else if b.exists(_.equalsIgnoreCase(myId)) then Some(Color.Black)
    else None

  private def thinkBudget(cfg: LichessBotConfig, myColor: Color, wMs: Long, bMs: Long): Long =
    val rem = if myColor == Color.White then wMs else bMs
    (rem / 25).max(cfg.minThinkMs).min(cfg.maxThinkMs)

  private def tuneStrategy(s: MoveStrategy, ms: Long): Unit =
    s match
      case i: IterativeDeepeningStrategy        => i.timeLimitMs = ms
      case i: IterativeDeepeningEndgameStrategy => i.timeLimitMs = ms
      case d: DeepeningOpeningEndgameStrategy   => d.timeLimitMs = ms
      case _                                    => ()

end LichessBotMain
