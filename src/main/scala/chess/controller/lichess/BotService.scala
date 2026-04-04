package chess.controller.lichess

import cats.effect.*
import cats.effect.std.Queue
import cats.effect.syntax.monadCancel.*
import cats.implicits.*
import chess.controller.{GameController, MoveStrategy}
import chess.controller.lichess.LichessModels.*
import chess.model.{Board, Color}
import fs2.Stream
import io.circe.parser.*

import scala.concurrent.duration.*

/** Service that manages bot gameplay on Lichess */
trait BotService[F[_]]:
  /** Start the bot - streams events and plays games */
  def start: F[Unit]

  /** Stop the bot gracefully */
  def stop: F[Unit]

object BotService:

  def apply[F[_]: Async](
      client: LichessClient[F],
      config: BotConfig,
      strategy: MoveStrategy
  ): F[BotService[F]] =
    for
      activeGames <- Ref.of[F, Set[String]](Set.empty)
      isRunning <- Ref.of[F, Boolean](false)
    yield new BotServiceImpl[F](client, config, strategy, activeGames, isRunning)

  private class BotServiceImpl[F[_]: Async](
      client: LichessClient[F],
      config: BotConfig,
      strategy: MoveStrategy,
      activeGames: Ref[F, Set[String]],
      isRunning: Ref[F, Boolean]
  ) extends BotService[F]:

    override def start: F[Unit] =
      for
        _ <- isRunning.set(true)
        _ <- Async[F].start(eventLoop)
        _ <- log("Bot started and listening for events")
      yield ()

    override def stop: F[Unit] =
      for
        _ <- isRunning.set(false)
        _ <- log("Bot stopped")
      yield ()

    private def eventLoop: F[Unit] =
      client.streamEvents
        .evalMap(processEvent)
        .compile
        .drain
        .handleErrorWith { error =>
          log(s"Event loop error: ${error.getMessage}") *>
            Async[F].sleep(5.seconds) *>
            isRunning.get.flatMap { running =>
              if running then eventLoop else Async[F].unit
            }
        }

    private def processEvent(eventJson: String): F[Unit] =
      if eventJson.trim.isEmpty then Async[F].unit
      else
        parse(eventJson) match
          case Right(json) =>
            json.hcursor.get[String]("type") match
              case Right("gameStart") =>
                json.as[GameStart] match
                  case Right(gameStart) => handleGameStart(gameStart)
                  case Left(error)      => log(s"Failed to parse gameStart: ${error.getMessage}")

              case Right("challenge") =>
                json.hcursor.get[Challenge]("challenge") match
                  case Right(challenge) => handleChallenge(challenge)
                  case Left(error)      => log(s"Failed to parse challenge: ${error.getMessage}")

              case Right(eventType) =>
                log(s"Ignoring event type: $eventType")

              case Left(_) =>
                Async[F].unit

          case Left(error) =>
            log(s"Failed to parse event JSON: ${error.getMessage}")

    private def handleGameStart(gameStart: GameStart): F[Unit] =
      for
        games <- activeGames.get
        _ <-
          if games.size < config.maxConcurrentGames then
            activeGames.update(_ + gameStart.gameId) *>
              log(s"Game started: ${gameStart.gameId}") *>
              Async[F].start(playGame(gameStart.gameId, gameStart.color)).void
          else log(s"Max concurrent games reached, ignoring game: ${gameStart.gameId}")
      yield ()

    private def handleChallenge(challenge: Challenge): F[Unit] =
      val decision = shouldAcceptChallenge(challenge)
      decision match
        case ChallengeDecision.Accept =>
          client.acceptChallenge(challenge.id) *>
            log(s"Accepted challenge: ${challenge.id} from ${challenge.challenger.name}")

        case ChallengeDecision.Decline =>
          client.declineChallenge(challenge.id) *>
            log(s"Declined challenge: ${challenge.id} from ${challenge.challenger.name}")

    private def shouldAcceptChallenge(challenge: Challenge): ChallengeDecision =
      val variantOk = config.acceptVariants.contains(challenge.variant.key)
      val ratedOk = if challenge.rated then config.acceptRated else config.acceptCasual
      val timeOk = challenge.timeControl.limit match
        case Some(limit) =>
          limit >= config.minTimeControl && limit <= config.maxTimeControl
        case None => true // Correspondence or unlimited

      if variantOk && ratedOk && timeOk then ChallengeDecision.Accept
      else ChallengeDecision.Decline

    private def playGame(gameId: String, colorStr: String): F[Unit] =
      val color = if colorStr == "white" then Color.White else Color.Black

      client.streamGame(gameId)
        .evalMap { stateJson =>
          if stateJson.trim.isEmpty then Async[F].unit
          else processGameState(gameId, color, stateJson)
        }
        .compile
        .drain
        .handleErrorWith { error =>
          log(s"Game $gameId error: ${error.getMessage}")
        }
        .guarantee(
          activeGames.update(_ - gameId) *>
            log(s"Game finished: $gameId")
        )

    private def processGameState(gameId: String, myColor: Color, stateJson: String): F[Unit] =
      parse(stateJson) match
        case Right(json) =>
          json.hcursor.get[String]("type") match
            case Right("gameFull") =>
              json.as[GameFull] match
                case Right(gameFull) =>
                  handleGameState(gameId, myColor, gameFull.state)
                case Left(error) =>
                  log(s"Failed to parse gameFull: ${error.getMessage}")

            case Right("gameState") =>
              json.as[GameState] match
                case Right(gameState) =>
                  handleGameState(gameId, myColor, gameState)
                case Left(error) =>
                  log(s"Failed to parse gameState: ${error.getMessage}")

            case Right(stateType) =>
              log(s"Ignoring game state type: $stateType")

            case Left(_) =>
              Async[F].unit

        case Left(error) =>
          log(s"Failed to parse game state JSON: ${error.getMessage}")

    private def handleGameState(gameId: String, myColor: Color, state: GameState): F[Unit] =
      if state.status != "started" then
        log(s"Game $gameId finished with status: ${state.status}")
      else
        // We need FenIO and PgnIO for GameController
        import chess.AppBindings.given

        val controller = new GameController(Board.initial)
        val moves = if state.moves.isEmpty then List.empty else state.moves.split(" ").toList

        // Apply all moves to reconstruct current position
        moves.foreach { uciMove =>
          UciHelper.parseUciMove(uciMove) match
            case scala.util.Success((from, to, promotion)) =>
              controller.applyMove(from, to, promotion)
            case scala.util.Failure(error) =>
              println(s"Failed to parse UCI move $uciMove: ${error.getMessage}")
        }

        // Determine if it's our turn
        val moveCount = moves.length
        val isWhiteTurn = moveCount % 2 == 0
        val isMyTurn = (myColor == Color.White && isWhiteTurn) || (myColor == Color.Black && !isWhiteTurn)

        if isMyTurn then
          // Calculate best move using the strategy
          val currentBoard = controller.board
          strategy.selectMove(currentBoard, myColor) match
            case Some((from, to, promotion)) =>
              val uciMove = UciHelper.moveToUci(from, to, promotion)
              client.makeMove(gameId, uciMove) *>
                log(s"Made move in game $gameId: $uciMove")

            case None =>
              // No legal moves available - game should end
              log(s"No legal moves available in game $gameId")
        else
          Async[F].unit

    private def log(message: String): F[Unit] =
      Async[F].delay(println(s"[BotService] $message"))
