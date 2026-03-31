package chess.api.pekko.server

import chess.controller.GameController
import chess.controller.io.{FenIO, PgnIO}
import chess.model.{Board, MoveResult}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

/** Service for managing multiple chess games using Pekko
  */
class GameServicePekko(using fenIO: FenIO, pgnIO: PgnIO)(using ec: ExecutionContext):
  private val games: ConcurrentHashMap[String, GameController] = ConcurrentHashMap()

  /** Create a new game
    * @param startFen
    *   Optional starting FEN position
    * @return
    *   Future containing either game ID and starting FEN position or error message
    */
  def createGame(startFen: Option[String] = None): Future[Either[String, (String, String)]] =
    Future {
      startFen match
        case Some(fen) =>
          fenIO.load(fen) match
            case scala.util.Success(board) =>
              val gameId = UUID.randomUUID().toString
              val controller = GameController(board)
              val fenStr = controller.getBoardAsFEN
              games.put(gameId, controller)
              Right((gameId, fenStr))
            case scala.util.Failure(e) =>
              Left(s"Invalid FEN: ${e.getMessage}")
        case None =>
          val gameId = UUID.randomUUID().toString
          val controller = GameController(Board.initial)
          val fen = controller.getBoardAsFEN
          games.put(gameId, controller)
          Right((gameId, fen))
    }

  /** Get game controller by ID
    * @param gameId
    *   Game identifier
    * @return
    *   Future containing game controller if found
    */
  def getGame(gameId: String): Future[Option[GameController]] =
    Future.successful(Option(games.get(gameId)))

  /** Delete a game
    * @param gameId
    *   Game identifier
    * @return
    *   Future containing true if game was deleted, false if not found
    */
  def deleteGame(gameId: String): Future[Boolean] =
    Future.successful(games.remove(gameId) != null)

  /** Get game state
    * @param gameId
    *   Game identifier
    * @return
    *   Future containing current FEN, PGN history, and game status
    */
  def getGameState(gameId: String): Future[Option[(String, String, String)]] =
    getGame(gameId).map(_.map { controller =>
      val fen = controller.getBoardAsFEN
      val pgn = controller.pgnText
      val status = controller.gameStatus
      (fen, pgn, status)
    })

  /** Make a move in PGN notation
    * @param gameId
    *   Game identifier
    * @param move
    *   Move in PGN notation (e.g., "e4")
    * @return
    *   Future containing either move result (success with FEN and event) or error message
    */
  def makeMove(gameId: String, move: String): Future[Either[String, (String, Option[String])]] =
    getGame(gameId).map {
      case None => Left("Game not found")
      case Some(controller) =>
        controller.applyPgnMove(move) match
          case MoveResult.Moved(_, event) =>
            val fen = controller.getBoardAsFEN
            val eventStr = event match
              case chess.model.GameEvent.Check              => Some("check")
              case chess.model.GameEvent.Checkmate          => Some("checkmate")
              case chess.model.GameEvent.Stalemate          => Some("stalemate")
              case chess.model.GameEvent.ThreefoldRepetition => Some("threefold_repetition")
              case _                                        => None
            Right((fen, eventStr))
          case MoveResult.Failed(_, error) =>
            Left(error.message)
    }

  /** Get move history
    * @param gameId
    *   Game identifier
    * @return
    *   Future containing vector of PGN moves
    */
  def getMoveHistory(gameId: String): Future[Option[Vector[String]]] =
    getGame(gameId).map(_.map(_.pgnMoves))

  /** Get current FEN position
    * @param gameId
    *   Game identifier
    * @return
    *   Future containing FEN string
    */
  def getFen(gameId: String): Future[Option[String]] =
    getGame(gameId).map(_.map(_.getBoardAsFEN))

  /** Load position from FEN
    * @param gameId
    *   Game identifier
    * @param fen
    *   FEN position string
    * @return
    *   Future containing either the loaded FEN or error message
    */
  def loadFen(gameId: String, fen: String): Future[Either[String, String]] =
    getGame(gameId).map {
      case None => Left("Game not found")
      case Some(controller) =>
        controller.loadFromFEN(fen) match
          case Right(_)    => Right(controller.getBoardAsFEN)
          case Left(error) => Left(error)
    }
