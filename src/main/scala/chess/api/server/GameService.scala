package chess.api.server

import cats.effect.{IO, Ref}
import chess.controller.GameController
import chess.controller.io.{FenIO, PgnIO}
import chess.model.{Board, MoveResult}
import java.util.UUID

/** Service for managing multiple chess games
  */
class GameService(using fenIO: FenIO, pgnIO: PgnIO):
  private val games: Ref[IO, Map[String, GameController]] = Ref.unsafe(Map.empty)

  /** Create a new game
    * @param startFen
    *   Optional starting FEN position
    * @return
    *   Game ID and starting FEN position
    */
  def createGame(startFen: Option[String] = None): IO[Either[String, (String, String)]] =
    startFen match
      case Some(fen) =>
        fenIO.load(fen) match
          case scala.util.Success(board) =>
            val gameId = UUID.randomUUID().toString
            val controller = GameController(board)
            val fenStr = controller.getBoardAsFEN
            games.update(_.updated(gameId, controller)).map(_ => Right((gameId, fenStr)))
          case scala.util.Failure(e) =>
            IO.pure(Left(s"Invalid FEN: ${e.getMessage}"))
      case None =>
        val gameId = UUID.randomUUID().toString
        val controller = GameController(Board.initial)
        val fen = controller.getBoardAsFEN
        games.update(_.updated(gameId, controller)).map(_ => Right((gameId, fen)))

  /** Get game controller by ID
    * @param gameId
    *   Game identifier
    * @return
    *   Game controller if found
    */
  def getGame(gameId: String): IO[Option[GameController]] =
    games.get.map(_.get(gameId))

  /** Delete a game
    * @param gameId
    *   Game identifier
    * @return
    *   true if game was deleted, false if not found
    */
  def deleteGame(gameId: String): IO[Boolean] =
    games.modify { gamesMap =>
      if gamesMap.contains(gameId) then (gamesMap - gameId, true)
      else (gamesMap, false)
    }

  /** Get game state
    * @param gameId
    *   Game identifier
    * @return
    *   Current FEN, PGN history, and game status
    */
  def getGameState(gameId: String): IO[Option[(String, String, String)]] =
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
    *   Either move result (success with FEN and event) or error message
    */
  def makeMove(gameId: String, move: String): IO[Either[String, (String, Option[String])]] =
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
    *   Vector of PGN moves
    */
  def getMoveHistory(gameId: String): IO[Option[Vector[String]]] =
    getGame(gameId).map(_.map(_.pgnMoves))

  /** Get current FEN position
    * @param gameId
    *   Game identifier
    * @return
    *   FEN string
    */
  def getFen(gameId: String): IO[Option[String]] =
    getGame(gameId).map(_.map(_.getBoardAsFEN))

  /** Load position from FEN
    * @param gameId
    *   Game identifier
    * @param fen
    *   FEN position string
    * @return
    *   Either the loaded FEN or error message
    */
  def loadFen(gameId: String, fen: String): IO[Either[String, String]] =
    getGame(gameId).map {
      case None => Left("Game not found")
      case Some(controller) =>
        controller.loadFromFEN(fen) match
          case Right(_)    => Right(controller.getBoardAsFEN)
          case Left(error) => Left(error)
    }
