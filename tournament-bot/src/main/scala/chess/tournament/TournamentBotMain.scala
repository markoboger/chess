package chess.tournament

import cats.effect.kernel.Ref
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import chess.controller.ComputerPlayer
import chess.lichess.{BotStrategy, Uci}
import chess.model.Color
import io.circe.Json
import scala.concurrent.duration.*

/** Long-running tournament bot: stream events, play assigned games via UCI + [[ComputerPlayer]]. */
object TournamentBotMain extends IOApp.Simple:

  private val TerminalStatuses =
    Set("checkmate", "stalemate", "draw", "resigned", "timeout")

  def run: IO[Unit] =
    TournamentBotConfig.load match
      case Left(msg) =>
        IO.println(s"Config error: $msg") >> IO.raiseError(new IllegalArgumentException(msg))
      case Right(rawCfg) =>
        TournamentClient.resource(rawCfg).use { api =>
          for
            cfg <- resolveAuth(rawCfg, api)
            _   <- IO.println(s"[tournament] Bot id=${cfg.botId} strategy=${cfg.strategyId} api=${cfg.baseUri}")
            _   <- joinConfiguredTournament(cfg, api)
            tid <- resolveTournamentId(cfg, api)
            _   <- IO.println(s"[tournament] Streaming tournament $tid …")
            _   <- tournamentLoop(cfg, api, tid)
          yield ()
        }

  /** Register when only a bot name was supplied; otherwise use the provided token. */
  private def resolveAuth(cfg: TournamentBotConfig, api: TournamentClient): IO[TournamentBotConfig] =
    if cfg.token.nonEmpty then IO.pure(cfg)
    else
      val name =
        Option(System.getenv("TOURNAMENT_BOT_NAME")).map(_.trim).filter(_.nonEmpty).getOrElse("maichess-bot")
      api.registerBot(name).flatMap { case (id, token) =>
        IO.println(s"[tournament] Registered as $name ($id)") >>
          IO.pure(cfg.copy(token = token, botId = id))
      }

  private def joinConfiguredTournament(cfg: TournamentBotConfig, api: TournamentClient): IO[Unit] =
    cfg.tournamentId match
      case None => IO.unit
      case Some(id) =>
        IO.println(s"[tournament] Joining $id …") >> api.joinTournament(id)

  private def resolveTournamentId(cfg: TournamentBotConfig, api: TournamentClient): IO[String] =
    cfg.tournamentId match
      case Some(id) => IO.pure(id)
      case None if cfg.autoJoinCreated =>
        api.listTournaments.flatMap { json =>
          val created = json.hcursor.downField("created").as[List[Json]].getOrElse(Nil)
          created.headOption.flatMap(_.hcursor.get[String]("id").toOption) match
            case Some(id) =>
              IO.println(s"[tournament] Auto-joining created tournament $id …") >>
                api.joinTournament(id).as(id)
            case None =>
              IO.raiseError(new RuntimeException("No created tournaments to auto-join (set TOURNAMENT_ID)."))
        }
      case None =>
        IO.raiseError(new RuntimeException("Set TOURNAMENT_ID or TOURNAMENT_AUTO_JOIN=1."))

  private def tournamentLoop(cfg: TournamentBotConfig, api: TournamentClient, tournamentId: String): IO[Unit] =
    def step: IO[Unit] =
      api
        .tournamentStream(tournamentId)
        .evalMap(handleTournamentEvent(cfg, api, tournamentId, _))
        .compile
        .drain
        .handleErrorWith { e =>
          IO.println(s"[tournament] stream error: $e") >> IO.sleep(5.seconds) >> step
        }
    step

  private def handleTournamentEvent(
      cfg: TournamentBotConfig,
      api: TournamentClient,
      tournamentId: String,
      json: Json
  ): IO[Unit] =
    val c   = json.hcursor
    val typ = c.get[String]("type").toOption.getOrElse("")
    typ match
      case "heartbeat" => IO.unit
      case "tournamentStarted" =>
        IO.println(s"[tournament] $tournamentId started")
      case "roundStarted" =>
        IO.println(s"[tournament] round ${c.get[Int]("round").getOrElse(-1)} started")
      case "roundFinished" =>
        IO.println(s"[tournament] round ${c.get[Int]("round").getOrElse(-1)} finished")
      case "tournamentFinished" =>
        val w = c.downField("winner").get[String]("name").toOption.getOrElse("?")
        IO.println(s"[tournament] finished — winner: $w")
      case "gameStart" =>
        val gameId = c.get[String]("gameId").toOption.getOrElse("")
        val eventBotId = c.get[String]("botId").toOption.getOrElse("")
        val colorS = c.get[String]("color").toOption.getOrElse("white").toLowerCase
        val color  = if colorS == "black" then Color.Black else Color.White
        val mine   = cfg.botId.isEmpty || eventBotId == cfg.botId
        if gameId.isEmpty || !mine then IO.unit
        else
          IO.println(s"[tournament] gameStart $gameId as $colorS (bot=${cfg.botId})") >>
            playGame(cfg, api, tournamentId, gameId, color).start.void
      case other =>
        IO.whenA(other.nonEmpty)(IO.println(s"[tournament] event: $other"))

  private def playGame(
      cfg: TournamentBotConfig,
      api: TournamentClient,
      tournamentId: String,
      gameId: String,
      myColor: Color
  ): IO[Unit] =
    val computer = new ComputerPlayer(BotStrategy(cfg.strategyId, cfg.maxThinkMs))
    Ref.of[IO, Option[Color]](Some(myColor)).flatMap { colorRef =>
      Ref.of[IO, String]("").flatMap { movesRef =>
      def round: IO[Unit] =
        api
          .gameStream(tournamentId, gameId)
          .evalMap(json => handleGameEvent(cfg, api, tournamentId, gameId, colorRef, movesRef, computer, json))
          .compile
          .drain
          .attempt
          .flatMap {
            case Right(_) =>
              IO.println(s"[tournament] game $gameId stream ended")
            case Left(e) =>
              IO.println(s"[tournament] game $gameId: $e") >> IO.sleep(3.seconds) >> round
          }
      round
      }
    }

  private def handleGameEvent(
      cfg: TournamentBotConfig,
      api: TournamentClient,
      tournamentId: String,
      gameId: String,
      colorRef: Ref[IO, Option[Color]],
      movesRef: Ref[IO, String],
      computer: ComputerPlayer,
      json: Json
  ): IO[Unit] =
    val c   = json.hcursor
    val typ = c.get[String]("type").toOption.getOrElse("")
    if typ == "heartbeat" then IO.unit
    else if typ == "gameEnd" then
      IO.println(s"[tournament] game $gameId ended: ${c.get[String]("status").getOrElse("?")}")
    else if typ == "gameState" then
      val moves = c.get[String]("moves").toOption.getOrElse("")
      movesRef.set(moves) >> handleGamePayload(cfg, api, tournamentId, gameId, colorRef, movesRef, computer, c)
    else if typ == "move" then
      val uci = c.get[String]("uci").toOption.getOrElse("")
      movesRef.update(m => if m.isEmpty then uci else s"$m $uci") >>
        handleGamePayload(cfg, api, tournamentId, gameId, colorRef, movesRef, computer, c)
    else IO.unit

  private def handleGamePayload(
      cfg: TournamentBotConfig,
      api: TournamentClient,
      tournamentId: String,
      gameId: String,
      colorRef: Ref[IO, Option[Color]],
      movesRef: Ref[IO, String],
      computer: ComputerPlayer,
      c: io.circe.HCursor
  ): IO[Unit] =
    val status = c.get[String]("status").toOption.getOrElse("ongoing").toLowerCase
    if TerminalStatuses.contains(status) then IO.unit
    else
      for
        moves <- movesRef.get
        boardEither = if moves.trim.isEmpty then Right(chess.model.Board.initial) else Uci.applyMovesFromStart(moves)
        plies       = moves.trim.split("\\s+").count(_.nonEmpty)
        side        = Uci.sideToMoveAfterPlies(plies)
        wSec        = c.downField("clock").get[Double]("whiteTime").toOption.getOrElse(300.0)
        bSec        = c.downField("clock").get[Double]("blackTime").toOption.getOrElse(300.0)
        myColorOpt <- colorRef.get
        _ <- (myColorOpt, boardEither) match
          case (_, Left(err)) =>
            IO.println(s"[tournament] game $gameId UCI replay failed: $err")
          case (Some(myColor), Right(board)) if myColor == side =>
            val budget = thinkBudget(cfg, myColor, (wSec * 1000).toLong, (bSec * 1000).toLong)
            tuneStrategy(computer.strategy, budget)
            computer.move(board, myColor, _ => false) match
              case None =>
                IO.println(s"[tournament] game $gameId: no move returned")
              case Some((from, to, promo)) =>
                val uci = Uci.encode(from, to, promo)
                IO.println(s"[tournament] game $gameId play $uci (budget=${budget}ms)") >>
                  api.postMove(tournamentId, gameId, uci)
          case _ =>
            IO.unit
      yield ()

  private def thinkBudget(cfg: TournamentBotConfig, myColor: Color, wMs: Long, bMs: Long): Long =
    val rem = if myColor == Color.White then wMs else bMs
    (rem / 25).max(cfg.minThinkMs).min(cfg.maxThinkMs)

  private def tuneStrategy(s: chess.controller.MoveStrategy, ms: Long): Unit =
    s match
      case i: chess.controller.strategy.IterativeDeepeningStrategy        => i.timeLimitMs = ms
      case i: chess.controller.strategy.IterativeDeepeningEndgameStrategy => i.timeLimitMs = ms
      case d: chess.controller.strategy.DeepeningOpeningEndgameStrategy   => d.timeLimitMs = ms
      case _                                                              => ()

end TournamentBotMain
