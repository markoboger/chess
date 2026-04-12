package chess.application.game

import cats.effect.{IO, Ref}
import cats.effect.kernel.Temporal
import chess.controller.{GameController, MoveStrategy}
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.strategy.*
import chess.model.{Board, Color, GameSettings, MoveResult}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.*
import chess.application.game.GameSessionService.GameNotFound

/** Application service for managing active chess game sessions.
  *
  * This sits above the pure chess core and below any transport layer such as HTTP or WebSocket
  * servers. It owns in-memory session orchestration while delegating move validation and notation
  * handling to [[GameController]].
  */
class GameSessionService(
    publisher: GameEventPublisher = GameEventPublisher.noop
)(using fenIO: FenIO, pgnIO: PgnIO):
  private val games: Ref[IO, Map[String, (GameController, GameSettings)]] = Ref.unsafe(Map.empty)
  private val autoplayRunning: Ref[IO, Set[String]] = Ref.unsafe(Set.empty)

  // Per-game clock state (only present when clockInitialMs was set at game creation)
  private val clocks: Ref[IO, Map[String, ClockState]] = Ref.unsafe(Map.empty)
  // Games ended by flag fall: maps gameId -> Color of the player who flagged
  private val clockLoss: Ref[IO, Map[String, Color]] = Ref.unsafe(Map.empty)

  // Per-game strategy cache: gameId -> (strategyId -> strategy instance).
  // Strategies with expensive constructors (opening book index) are cached here so the index is
  // only built once per game rather than once per move.
  private val strategyCache: Ref[IO, Map[String, Map[String, MoveStrategy]]] = Ref.unsafe(Map.empty)

  def createGame(
      startFen: Option[String] = None,
      settings: GameSettings = GameSettings()
  ): IO[Either[String, (String, String)]] =
    startFen match
      case Some(fen) =>
        fenIO.load(fen) match
          case scala.util.Success(board) =>
            val gameId = UUID.randomUUID().toString
            val controller = GameController(board)
            val fenStr = controller.getBoardAsFEN
            games
              .update(_.updated(gameId, (controller, settings)))
              .flatTap(_ => initClock(gameId, settings))
              .flatMap(_ => publishSnapshot("game_created", gameId, controller))
              .flatTap(_ => ensureAutoplay(gameId))
              .as(Right((gameId, fenStr)))
          case scala.util.Failure(e) =>
            IO.pure(Left(s"Invalid FEN: ${e.getMessage}"))
      case None =>
        val gameId = UUID.randomUUID().toString
        val controller = GameController(Board.initial)
        val fen = controller.getBoardAsFEN
        games
          .update(_.updated(gameId, (controller, settings)))
          .flatTap(_ => initClock(gameId, settings))
          .flatMap(_ => publishSnapshot("game_created", gameId, controller))
          .flatTap(_ => ensureAutoplay(gameId))
          .as(Right((gameId, fen)))

  def listGames: IO[List[(String, String, chess.model.GameSettings)]] =
    games.get.map(_.toList.map { case (id, (ctrl, settings)) => (id, ctrl.gameStatus, settings) })

  def deleteAllGames: IO[Unit] =
    games.set(Map.empty) >>
      autoplayRunning.set(Set.empty) >>
      clocks.set(Map.empty) >>
      clockLoss.set(Map.empty) >>
      strategyCache.set(Map.empty)

  def getGame(gameId: String): IO[Option[GameController]] =
    games.get.map(_.get(gameId).map(_._1))

  def getSettings(gameId: String): IO[Option[GameSettings]] =
    games.get.map(_.get(gameId).map(_._2))

  def deleteGame(gameId: String): IO[Boolean] =
    games.modify { gamesMap =>
      if gamesMap.contains(gameId) then (gamesMap.removed(gameId), true)
      else (gamesMap, false)
    }.flatTap { deleted =>
      if deleted then publisher.publish(
        GameSessionEvent(
          gameId = gameId,
          eventType = "game_deleted",
          fen = None,
          pgn = None,
          status = None,
          move = None,
          gameEvent = None,
          occurredAt = Instant.now()
        )
      )
      else IO.unit
    }.flatMap { deleted =>
      autoplayRunning.update(_ - gameId)
        .flatMap(_ => clocks.update(_ - gameId))
        .flatMap(_ => clockLoss.update(_ - gameId))
        .flatMap(_ => strategyCache.update(_ - gameId))
        .as(deleted)
    }

  def getGameState(gameId: String): IO[Option[(String, String, String, GameSettings)]] =
    for
      gamesMap     <- games.get
      clockLossMap <- clockLoss.get
    yield gamesMap.get(gameId).map { case (controller, settings) =>
      val fen = controller.getBoardAsFEN
      val pgn = controller.pgnText
      val status = clockLossMap.get(gameId) match
        case Some(Color.White) => "Timeout: Black wins on time"
        case Some(Color.Black) => "Timeout: White wins on time"
        case None              => controller.gameStatus
      (fen, pgn, status, settings)
    }

  def makeMove(gameId: String, move: String): IO[Either[String, (String, Option[String])]] =
    getGame(gameId).flatMap {
      case None => IO.pure(Left(GameNotFound))
      case Some(controller) =>
        controller.applyPgnMove(move) match
          case MoveResult.Moved(_, event) =>
            val fen = controller.getBoardAsFEN
            val eventStr = event match
              case chess.model.GameEvent.Check               => Some("check")
              case chess.model.GameEvent.Checkmate           => Some("checkmate")
              case chess.model.GameEvent.Stalemate           => Some("stalemate")
              case chess.model.GameEvent.ThreefoldRepetition => Some("threefold_repetition")
              case _                                         => None
            publishSnapshot("move_applied", gameId, controller, move = Some(move), gameEvent = eventStr)
              .flatTap(_ => ensureAutoplay(gameId))
              .as(Right((fen, eventStr)))
          case MoveResult.Failed(_, error) =>
            publisher.publish(
              GameSessionEvent(
                gameId = gameId,
                eventType = "move_rejected",
                fen = Some(controller.getBoardAsFEN),
                pgn = Some(controller.pgnText),
                status = Some(controller.gameStatus),
                move = Some(move),
                gameEvent = Some(error.message),
                occurredAt = Instant.now()
              )
            ).as(Left(error.message))
    }

  def getMoveHistory(gameId: String): IO[Option[Vector[String]]] =
    getGame(gameId).map(_.map(_.pgnMoves))

  def getFen(gameId: String): IO[Option[String]] =
    getGame(gameId).map(_.map(_.getBoardAsFEN))

  def loadPgn(gameId: String, pgn: String): IO[Either[String, (String, Int)]] =
    getGame(gameId).flatMap {
      case None => IO.pure(Left(GameNotFound))
      case Some(controller) =>
        controller.loadPgnMoves(pgn) match
          case Right(_) =>
            publishSnapshot("pgn_loaded", gameId, controller)
              .as(Right((controller.getBoardAsFEN, controller.pgnMoves.size)))
          case Left(error) => IO.pure(Left(error))
    }

  def loadFen(gameId: String, fen: String): IO[Either[String, String]] =
    getGame(gameId).flatMap {
      case None => IO.pure(Left(GameNotFound))
      case Some(controller) =>
        controller.loadFromFEN(fen) match
          case Right(_) =>
            publishSnapshot("fen_loaded", gameId, controller)
              .flatTap(_ => ensureAutoplay(gameId))
              .as(Right(controller.getBoardAsFEN))
          case Left(error) => IO.pure(Left(error))
    }

  def computeAiMove(gameId: String, strategyId: String): IO[Either[String, Option[String]]] =
    getGame(gameId).flatMap {
      case None => IO.pure(Left(GameNotFound))
      case Some(controller) =>
        val color = if controller.isWhiteToMove then Color.White else Color.Black
        getOrCreateStrategy(gameId, strategyId).map { strategy =>
          val moveOpt = strategy.selectMove(controller.board, color)
          val san = moveOpt.flatMap { case (from, to, promo) =>
            controller.board.move(from, to, promo).toOption.map { boardAfter =>
              chess.controller.io.pgn.PGNParser.toAlgebraic(
                from, to, controller.board, boardAfter, color == Color.White
              )
            }
          }
          Right(san)
        }
    }

  private def publishSnapshot(
      eventType: String,
      gameId: String,
      controller: GameController,
      move: Option[String] = None,
      gameEvent: Option[String] = None
  ): IO[Unit] =
    publisher.publish(
      GameSessionEvent(
        gameId = gameId,
        eventType = eventType,
        fen = Some(controller.getBoardAsFEN),
        pgn = Some(controller.pgnText),
        status = Some(controller.gameStatus),
        move = move,
        gameEvent = gameEvent,
        occurredAt = Instant.now()
      )
    )

  private def getSession(gameId: String): IO[Option[(GameController, GameSettings)]] =
    games.get.map(_.get(gameId))

  private def initClock(gameId: String, settings: GameSettings): IO[Unit] =
    settings.clockInitialMs match
      case Some(initial) =>
        val increment = settings.clockIncrementMs.getOrElse(0L)
        clocks.update(_.updated(gameId, ClockState(initial, initial, increment)))
      case None => IO.unit

  private def ensureAutoplay(gameId: String): IO[Unit] =
    for
      sessionOpt   <- getSession(gameId)
      clockLossMap <- clockLoss.get
      _ <- sessionOpt match
        case Some((controller, settings))
            if settings.backendAutoplay
            && isAiTurn(settings, controller)
            && !isTerminalController(controller)
            && !clockLossMap.contains(gameId) =>
          autoplayRunning.modify { running =>
            if running.contains(gameId) then (running, false)
            else (running + gameId, true)
          }.flatMap { shouldStart =>
            if shouldStart then
              runAutoplay(gameId).guarantee(autoplayRunning.update(_ - gameId)).start.void
            else IO.unit
          }
        case _ => IO.unit
    yield ()

  private def runAutoplay(gameId: String): IO[Unit] =
    for
      sessionOpt   <- getSession(gameId)
      clockLossMap <- clockLoss.get
      _ <- sessionOpt match
        case Some((controller, settings))
            if settings.backendAutoplay
            && isAiTurn(settings, controller)
            && !isTerminalController(controller)
            && !clockLossMap.contains(gameId) =>
          val isWhite    = controller.isWhiteToMove
          val color      = if isWhite then Color.White else Color.Black
          val strategyId = activeStrategy(settings, controller)
          val board      = controller.board  // immutable snapshot for blocking compute

          clocks.get.map(_.get(gameId)).flatMap {
            case Some(cs) =>
              val remaining = if isWhite then cs.whiteRemainingMs else cs.blackRemainingMs
              val startMs = System.currentTimeMillis()
              IO.race(
                computeAiMoveBlocking(board, color, strategyId, Some(remaining), gameId),
                Temporal[IO].sleep(remaining.millis)
              ).flatMap {
                case Left(sanOpt) =>
                  val elapsed    = System.currentTimeMillis() - startMs
                  val newRemain  = (remaining - elapsed + cs.incrementMs).max(0L)
                  val updatedCs  = if isWhite then cs.copy(whiteRemainingMs = newRemain)
                                   else cs.copy(blackRemainingMs = newRemain)
                  clocks.update(_.updated(gameId, updatedCs)).flatMap { _ =>
                    sanOpt match
                      case Some(move) => makeMove(gameId, move).flatMap(_ => runAutoplay(gameId))
                      case None       => IO.unit  // strategy found no move (game over)
                  }
                case Right(_) =>
                  // Clock flag: the player who was to move ran out of time
                  clockLoss.update(_.updated(gameId, color)) *>
                    publishClockLoss(gameId, controller, color)
              }

            case None =>
              // No clock: compute without timeout (original behaviour)
              computeAiMoveBlocking(board, color, strategyId, gameId = gameId).flatMap {
                case Some(move) => makeMove(gameId, move).flatMap(_ => runAutoplay(gameId))
                case None       => IO.unit
              }
          }

        case _ => IO.unit
    yield ()

  private def computeAiMoveBlocking(board: Board, color: Color, strategyId: String, clockMs: Option[Long] = None, gameId: String = ""): IO[Option[String]] =
    getOrCreateStrategy(gameId, strategyId, clockMs).flatMap { strategy =>
      IO.blocking {
        strategy.selectMove(board, color).flatMap { case (from, to, promo) =>
          board.move(from, to, promo).toOption.map { boardAfter =>
            chess.controller.io.pgn.PGNParser.toAlgebraic(from, to, board, boardAfter, color == Color.White)
          }
        }
      }
    }

  private def publishClockLoss(gameId: String, controller: GameController, loser: Color): IO[Unit] =
    val winner = if loser == Color.White then "Black" else "White"
    publisher.publish(
      GameSessionEvent(
        gameId   = gameId,
        eventType = "game_over",
        fen      = Some(controller.getBoardAsFEN),
        pgn      = Some(controller.pgnText),
        status   = Some(s"Timeout: $winner wins on time"),
        move     = None,
        gameEvent = Some("clock"),
        occurredAt = Instant.now()
      )
    )

  // Strategies whose constructors are expensive (they build an opening-book index on creation).
  // These are cached per game so the index is only built once per game session.
  private val cachedStrategyIds = Set(
    "opening-continuation",
    "opening-continuation-endgame",
    "opening-intelligence",
    "opening-intelligence-endgame",
    "deepening-opening-endgame"
  )

  /** Returns an existing cached strategy instance for the game (updating its time budget if it
    * is a time-managed strategy), or creates and caches a new one.  Cheap stateless strategies
    * are always created fresh.
    */
  private def getOrCreateStrategy(gameId: String, strategyId: String, clockMs: Option[Long] = None): IO[MoveStrategy] =
    val idBudget: Long = clockMs match
      case Some(remaining) => (remaining / 5).max(50L).min(2000L)
      case None            => 2000L

    if !cachedStrategyIds.contains(strategyId) then
      IO.pure(freshStrategy(strategyId, idBudget))
    else
      strategyCache.modify { cache =>
        val forGame = cache.getOrElse(gameId, Map.empty)
        forGame.get(strategyId) match
          case Some(s) =>
            // Update the time budget in-place for time-managed strategies
            s match
              case d: DeepeningOpeningEndgameStrategy       => d.timeLimitMs = idBudget
              case i: IterativeDeepeningEndgameStrategy     => i.timeLimitMs = idBudget
              case i: IterativeDeepeningStrategy            => i.timeLimitMs = idBudget
              case _                                        => ()
            (cache, s)
          case None =>
            val s = cachedStrategy(strategyId, idBudget)
            (cache.updated(gameId, forGame.updated(strategyId, s)), s)
      }

  private def freshStrategy(strategyId: String, idBudget: Long): MoveStrategy =
    strategyId match
      case "random"              => new RandomStrategy
      case "material-balance"    => new MaterialBalanceStrategy
      case "piece-square"        => new PieceSquareStrategy
      case "minimax"             => new MinimaxStrategy
      case "endgame-minimax"     => new EndgameMinimaxStrategy
      case "quiescence"          => new QuiescenceStrategy
      case "iterative-deepening"         => new IterativeDeepeningStrategy(idBudget)
      case "iterative-deepening-endgame" => new IterativeDeepeningEndgameStrategy(idBudget)
      case _                             => new GreedyStrategy

  private def cachedStrategy(strategyId: String, idBudget: Long): MoveStrategy =
    strategyId match
      case "opening-continuation"         => new OpeningContinuationStrategy(fallback = new IterativeDeepeningStrategy(idBudget))
      case "opening-continuation-endgame" => new OpeningContinuationStrategy(fallback = new IterativeDeepeningEndgameStrategy(idBudget))
      case "opening-intelligence"         => new OpeningBookStrategy(fallback = new IterativeDeepeningStrategy(idBudget))
      case "opening-intelligence-endgame" => new OpeningBookStrategy(fallback = new IterativeDeepeningEndgameStrategy(idBudget))
      case "deepening-opening-endgame"    => new DeepeningOpeningEndgameStrategy(timeLimitMs = idBudget)
      case _                              => new GreedyStrategy

  private def activeStrategy(settings: GameSettings, controller: GameController): String =
    if controller.isWhiteToMove then settings.whiteStrategy else settings.blackStrategy

  private def isAiTurn(settings: GameSettings, controller: GameController): Boolean =
    if controller.isWhiteToMove then !settings.whiteIsHuman else !settings.blackIsHuman

  private def isTerminalController(controller: GameController): Boolean =
    controller.isCheckmate || controller.isStalemate || controller.isThreefoldRepetition

private final case class ClockState(
    whiteRemainingMs: Long,
    blackRemainingMs: Long,
    incrementMs: Long
)

object GameSessionService:
  val GameNotFound = "Game not found"

  def apply(
      publisher: GameEventPublisher = GameEventPublisher.noop
  )(using fenIO: FenIO, pgnIO: PgnIO): GameSessionService =
    new GameSessionService(publisher)
