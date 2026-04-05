package chess.application.game

import cats.effect.{IO, Ref}
import chess.controller.GameController
import chess.controller.io.{FenIO, PgnIO}
import chess.model.{Board, MoveResult}

import java.time.Instant
import java.util.UUID

/** Application service for managing active chess game sessions.
  *
  * This sits above the pure chess core and below any transport layer such as HTTP or WebSocket
  * servers. It owns in-memory session orchestration while delegating move validation and notation
  * handling to [[GameController]].
  */
class GameSessionService(
    publisher: GameEventPublisher = GameEventPublisher.noop
)(using fenIO: FenIO, pgnIO: PgnIO):
  private val games: Ref[IO, Map[String, GameController]] = Ref.unsafe(Map.empty)

  def createGame(startFen: Option[String] = None): IO[Either[String, (String, String)]] =
    startFen match
      case Some(fen) =>
        fenIO.load(fen) match
          case scala.util.Success(board) =>
            val gameId = UUID.randomUUID().toString
            val controller = GameController(board)
            val fenStr = controller.getBoardAsFEN
            games
              .update(_.updated(gameId, controller))
              .flatMap(_ => publishSnapshot("game_created", gameId, controller))
              .as(Right((gameId, fenStr)))
          case scala.util.Failure(e) =>
            IO.pure(Left(s"Invalid FEN: ${e.getMessage}"))
      case None =>
        val gameId = UUID.randomUUID().toString
        val controller = GameController(Board.initial)
        val fen = controller.getBoardAsFEN
        games
          .update(_.updated(gameId, controller))
          .flatMap(_ => publishSnapshot("game_created", gameId, controller))
          .as(Right((gameId, fen)))

  def getGame(gameId: String): IO[Option[GameController]] =
    games.get.map(_.get(gameId))

  def deleteGame(gameId: String): IO[Boolean] =
    games.modify { gamesMap =>
      if gamesMap.contains(gameId) then (gamesMap - gameId, true)
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
    }

  def getGameState(gameId: String): IO[Option[(String, String, String)]] =
    getGame(gameId).map(_.map { controller =>
      val fen = controller.getBoardAsFEN
      val pgn = controller.pgnText
      val status = controller.gameStatus
      (fen, pgn, status)
    })

  def makeMove(gameId: String, move: String): IO[Either[String, (String, Option[String])]] =
    getGame(gameId).flatMap {
      case None => IO.pure(Left("Game not found"))
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

  def loadFen(gameId: String, fen: String): IO[Either[String, String]] =
    getGame(gameId).flatMap {
      case None => IO.pure(Left("Game not found"))
      case Some(controller) =>
        controller.loadFromFEN(fen) match
          case Right(_) =>
            publishSnapshot("fen_loaded", gameId, controller).as(Right(controller.getBoardAsFEN))
          case Left(error) => IO.pure(Left(error))
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

object GameSessionService:
  def apply(
      publisher: GameEventPublisher = GameEventPublisher.noop
  )(using fenIO: FenIO, pgnIO: PgnIO): GameSessionService =
    new GameSessionService(publisher)
