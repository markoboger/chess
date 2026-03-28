package chess.controller

import chess.model.{Board, Color, PromotableRole, Square, File, Rank, MoveResult, MoveError, GameEvent}
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.io.pgn.PGNParser
import chess.util.Observable
import scala.util.{Try, Success, Failure}

final class GameController(initialBoard: Board)(using
    val fenIO: FenIO,
    val pgnIO: PgnIO
) extends Observable[MoveResult]:
  private var currentBoard: Board = initialBoard
  private var _isWhiteToMove: Boolean = true

  // Move history for forward/backward navigation
  private var _boardStates: Vector[Board] = Vector(initialBoard)
  private var _pgnMoves: Vector[String] = Vector.empty
  private var _currentIndex: Int = 0

  def board: Board = currentBoard

  def isWhiteToMove: Boolean = _isWhiteToMove

  /** All board states from initial to latest. */
  def boardStates: Vector[Board] = _boardStates

  /** PGN move strings in order. `pgnMoves(i)` transitions from `boardStates(i)`
    * to `boardStates(i+1)`.
    */
  def pgnMoves: Vector[String] = _pgnMoves

  /** Index of the currently displayed board state (0 = initial). */
  def currentIndex: Int = _currentIndex

  /** Whether we are viewing the latest position (moves are allowed). */
  def isAtLatest: Boolean = _currentIndex == _boardStates.length - 1

  /** Format the move history as a PGN move text. */
  def pgnText: String = pgnIO.save(_pgnMoves)

  /** Navigate one move backward. */
  def backward(): Unit = {
    if (_currentIndex > 0) {
      _currentIndex -= 1
      currentBoard = _boardStates(_currentIndex)
      _isWhiteToMove = _currentIndex % 2 == 0
      notifyObservers(MoveResult.Moved(currentBoard, gameEventAt(_currentIndex)))
    }
  }

  /** Navigate one move forward. */
  def forward(): Unit = {
    if (_currentIndex < _boardStates.length - 1) {
      _currentIndex += 1
      currentBoard = _boardStates(_currentIndex)
      _isWhiteToMove = _currentIndex % 2 == 0
      notifyObservers(MoveResult.Moved(currentBoard, gameEventAt(_currentIndex)))
    }
  }

  /** Jump directly to any position in the game history.
    * @param index 0 = initial position, N = position after move N
    */
  def goToMove(index: Int): Unit =
    if index >= 0 && index < _boardStates.length then
      _currentIndex = index
      currentBoard = _boardStates(_currentIndex)
      _isWhiteToMove = _currentIndex % 2 == 0
      notifyObservers(MoveResult.Moved(currentBoard, gameEventAt(_currentIndex)))

  /** Compute the GameEvent for a board state at the given history index.
    * The active color at that index is the player whose turn it is to move.
    */
  private def gameEventAt(index: Int): GameEvent =
    val activeColorAtIndex = if index % 2 == 0 then Color.White else Color.Black
    _boardStates(index).detectGameEvent(activeColorAtIndex)

  private def resetHistory(board: Board): Unit = {
    _boardStates = Vector(board)
    _pgnMoves = Vector.empty
    _currentIndex = 0
    _isWhiteToMove = true
  }

  /** Notify observers about a board reset (new game, FEN load, etc.). */
  def announceInitial(board: Board): Unit = {
    currentBoard = board
    resetHistory(board)
    notifyObservers(MoveResult.Moved(board))
  }

  /** Load a position from FEN notation.
    * @param fen
    *   The FEN string (e.g., "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
    * @return
    *   Either the new board or an error message
    */
  def loadFromFEN(fen: String): Either[String, Board] = {
    fenIO.load(fen) match {
      case Success(newBoard) =>
        currentBoard = newBoard
        resetHistory(newBoard)
        notifyObservers(MoveResult.Moved(newBoard))
        Right(newBoard)
      case Failure(e) =>
        Left(s"Failed to parse FEN: ${e.getMessage}")
    }
  }

  /** Get the current board position as FEN notation.
    * @return
    *   The FEN string representing the current board
    */
  def getBoardAsFEN: String = {
    fenIO.save(board)
  }

  /** Apply a move using PGN notation (e.g., "e4", "Nf3", "O-O", "e8=Q").
    * @return
    *   [[MoveResult]] — [[MoveResult.Moved]] with board and game event on
    *   success, or [[MoveResult.Failed]] with error reason on failure
    */
  def applyPgnMove(pgnMove: String): MoveResult =
    PGNParser.parseMove(pgnMove, board, _isWhiteToMove) match
      case Success((from, to)) => applyMove(from, to, parsePromotion(pgnMove))
      case Failure(e) =>
        MoveResult.Failed(board, MoveError.ParseError(e.getMessage))

  private def parsePromotion(pgnMove: String): Option[PromotableRole] =
    val promoRegex = """.*=([QRBN]).*""".r
    pgnMove match
      case promoRegex("Q") => Some(PromotableRole.Queen)
      case promoRegex("R") => Some(PromotableRole.Rook)
      case promoRegex("B") => Some(PromotableRole.Bishop)
      case promoRegex("N") => Some(PromotableRole.Knight)
      case _               => None

  /** Apply a move using file/rank coordinates.
    * @return
    *   [[MoveResult]] — [[MoveResult.Moved]] with board and game event on
    *   success, or [[MoveResult.Failed]] with error reason on failure
    */
  def applyMove(from: Square, to: Square, promotion: Option[PromotableRole] = None): MoveResult =
    if !isAtLatest then return MoveResult.Failed(board, MoveError.InvalidMove)
    val boardBefore = board
    val wasWhite = _isWhiteToMove
    val result = board.pieceAt(from) match
      case Some(piece) if piece.color == activeColor =>
        board.move(from, to, promotion) match
          case moved: MoveResult.Moved =>
            val pgn = PGNParser.toAlgebraic(
              from,
              to,
              boardBefore,
              moved.board,
              wasWhite
            )
            currentBoard = moved.board
            _isWhiteToMove = !_isWhiteToMove
            _boardStates = _boardStates :+ moved.board
            _pgnMoves = _pgnMoves :+ pgn
            _currentIndex = _boardStates.length - 1
            moved
          case failed: MoveResult.Failed =>
            failed
      case Some(_) => MoveResult.Failed(board, MoveError.WrongColor)
      case None    => MoveResult.Failed(board, MoveError.NoPiece)
    notifyObservers(result)
    result

  /** Load a game from PGN move text (e.g. "1. e4 e5 2. Nf3 Nc6"). Resets to the
    * initial board and applies all moves in sequence.
    * @return
    *   Right(board) on success, Left(error) if any move fails
    */
  def loadPgnMoves(pgnText: String): Either[String, Board] = {
    pgnIO.load(pgnText) match {
      case Failure(e) => Left(e.getMessage)
      case Success(tokens) =>
        announceInitial(Board.initial)
        tokens.zipWithIndex.foldLeft[Either[String, Board]](Right(board)) {
          case (Left(err), _) => Left(err)
          case (Right(_), (token, i)) =>
            applyPgnMove(token) match {
              case _: MoveResult.Moved => Right(board)
              case MoveResult.Failed(_, error) =>
                Left(s"Failed at move ${i + 1} ('$token'): ${error.message}")
            }
        }
    }
  }

  def activeColor: Color =
    if _isWhiteToMove then Color.White else Color.Black

  def isInCheck: Boolean = board.isInCheck(activeColor)

  def isCheckmate: Boolean = board.isCheckmate(activeColor)

  def isStalemate: Boolean = board.isStalemate(activeColor)

  def gameStatus: String =
    if isCheckmate then s"Checkmate! ${activeColor.opposite} wins!"
    else if isStalemate then "Stalemate! The game is a draw."
    else if isInCheck then s"${activeColor} is in check!"
    else s"${activeColor} to move"
