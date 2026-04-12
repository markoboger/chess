package chess.controller

import chess.model.{Board, Color, Piece, Role, PromotableRole, Square, File, Rank, MoveResult, MoveError, GameEvent}
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.io.fen.FullFen
import chess.controller.io.pgn.PGNParser
import chess.util.Observable
import scala.util.{Try, Success, Failure}

final class GameController(initialBoard: Board)(using
    val fenIO: FenIO,
    val pgnIO: PgnIO
) extends Observable[MoveResult]:
  private case class HistoryState(
      board: Board,
      whiteToMove: Boolean,
      halfmoveClock: Int,
      fullmoveNumber: Int
  )

  private var currentBoard: Board = initialBoard
  private var _isWhiteToMove: Boolean = true
  private var _halfmoveClock: Int = 0
  private var _fullmoveNumber: Int = 1

  // Move history for forward/backward navigation
  private var _history: Vector[HistoryState] = Vector(
    HistoryState(initialBoard, whiteToMove = true, halfmoveClock = 0, fullmoveNumber = 1)
  )
  private var _pgnMoves: Vector[String] = Vector.empty
  private var _currentIndex: Int = 0

  // Redo buffer for undo/redo support
  private var _redoHistory: Vector[HistoryState] = Vector.empty
  private var _redoMoves: Vector[String] = Vector.empty

  // Position repetition tracking: key → count
  private case class PositionKey(
      squares: Vector[Vector[Option[Piece]]],
      castlingRights: chess.model.CastlingRights,
      whiteToMove: Boolean,
      enPassantFile: Option[File]
  )

  private def positionKey(board: Board, whiteToMove: Boolean): PositionKey =
    val epFile = board.lastMove.flatMap { case (from, to) =>
      val rankDiff = (to.rank.index - from.rank.index).abs
      if rankDiff == 2 then
        board.pieceAt(to) match
          case Some(Piece(Role.Pawn, _)) => Some(to.file)
          case _                         => None
      else None
    }
    PositionKey(board.squares, board.castlingRights, whiteToMove, epFile)

  private var positionCounts: Map[PositionKey, Int] =
    Map(positionKey(initialBoard, true) -> 1)

  def board: Board = currentBoard

  def isWhiteToMove: Boolean = _isWhiteToMove

  /** All board states from initial to latest. */
  def boardStates: Vector[Board] = _history.map(_.board)

  /** FEN string for every historical board state (index i = FEN after i half-moves). */
  def boardFens: Vector[String] =
    _history.map(s => FullFen.render(s.board, s.whiteToMove, s.halfmoveClock, s.fullmoveNumber))

  /** PGN move strings in order. `pgnMoves(i)` transitions from `boardStates(i)` to `boardStates(i+1)`.
    */
  def pgnMoves: Vector[String] = _pgnMoves

  /** Index of the currently displayed board state (0 = initial). */
  def currentIndex: Int = _currentIndex

  /** Whether we are viewing the latest position (moves are allowed). */
  def isAtLatest: Boolean = _currentIndex == _history.length - 1

  /** Whether undo is possible (at least one move in history). */
  def canUndo: Boolean = _history.length > 1

  /** Whether redo is possible (redo buffer is non-empty). */
  def canRedo: Boolean = _redoHistory.nonEmpty

  /** Format the move history as a PGN move text. */
  def pgnText: String = pgnIO.save(_pgnMoves)

  /** Navigate one move backward. */
  def backward(): Unit = {
    if (_currentIndex > 0) {
      _currentIndex -= 1
      val state = _history(_currentIndex)
      currentBoard = state.board
      _isWhiteToMove = state.whiteToMove
      _halfmoveClock = state.halfmoveClock
      _fullmoveNumber = state.fullmoveNumber
      notifyObservers(MoveResult.Moved(currentBoard, gameEventAt(_currentIndex)))
    }
  }

  /** Navigate one move forward. */
  def forward(): Unit = {
    if (_currentIndex < _history.length - 1) {
      _currentIndex += 1
      val state = _history(_currentIndex)
      currentBoard = state.board
      _isWhiteToMove = state.whiteToMove
      _halfmoveClock = state.halfmoveClock
      _fullmoveNumber = state.fullmoveNumber
      notifyObservers(MoveResult.Moved(currentBoard, gameEventAt(_currentIndex)))
    }
  }

  /** Undo the last move: truncate history and push removed state onto the redo buffer. First jumps to the latest
    * position if navigating in history.
    */
  def undo(): Unit = {
    // If not at latest, jump to latest first
    if (!isAtLatest) goToMove(_history.length - 1)
    if (_history.length > 1) {
      // Push removed state/move onto redo buffer
      _redoHistory = _history.last +: _redoHistory
      _redoMoves = _pgnMoves.last +: _redoMoves
      // Truncate history
      _history = _history.dropRight(1)
      _pgnMoves = _pgnMoves.dropRight(1)
      _currentIndex = _history.length - 1
      val state = _history(_currentIndex)
      currentBoard = state.board
      _isWhiteToMove = state.whiteToMove
      _halfmoveClock = state.halfmoveClock
      _fullmoveNumber = state.fullmoveNumber
      notifyObservers(MoveResult.Moved(currentBoard, gameEventAt(_currentIndex)))
    }
  }

  /** Redo a previously undone move: pop from the redo buffer and append to history. */
  def redo(): Unit = {
    if (_redoHistory.nonEmpty) {
      // If not at latest, jump to latest first
      if (!isAtLatest) goToMove(_history.length - 1)
      val state = _redoHistory.head
      val move = _redoMoves.head
      _redoHistory = _redoHistory.tail
      _redoMoves = _redoMoves.tail
      _history = _history :+ state
      _pgnMoves = _pgnMoves :+ move
      _currentIndex = _history.length - 1
      currentBoard = state.board
      _isWhiteToMove = state.whiteToMove
      _halfmoveClock = state.halfmoveClock
      _fullmoveNumber = state.fullmoveNumber
      notifyObservers(MoveResult.Moved(currentBoard, gameEventAt(_currentIndex)))
    }
  }

  /** Jump directly to any position in the game history.
    * @param index
    *   0 = initial position, N = position after move N
    */
  def goToMove(index: Int): Unit =
    if index >= 0 && index < _history.length then
      _currentIndex = index
      val state = _history(_currentIndex)
      currentBoard = state.board
      _isWhiteToMove = state.whiteToMove
      _halfmoveClock = state.halfmoveClock
      _fullmoveNumber = state.fullmoveNumber
      notifyObservers(MoveResult.Moved(currentBoard, gameEventAt(_currentIndex)))

  /** Compute the GameEvent for a board state at the given history index. The active color at that index is the player
    * whose turn it is to move.
    */
  private def gameEventAt(index: Int): GameEvent =
    val activeColorAtIndex = if _history(index).whiteToMove then Color.White else Color.Black
    _history(index).board.detectGameEvent(activeColorAtIndex)

  private def resetHistory(
      board: Board,
      whiteToMove: Boolean = true,
      halfmoveClock: Int = 0,
      fullmoveNumber: Int = 1
  ): Unit = {
    _history = Vector(HistoryState(board, whiteToMove, halfmoveClock, fullmoveNumber))
    _pgnMoves = Vector.empty
    _currentIndex = 0
    _isWhiteToMove = whiteToMove
    _halfmoveClock = halfmoveClock
    _fullmoveNumber = fullmoveNumber
    _redoHistory = Vector.empty
    _redoMoves = Vector.empty
    positionCounts = Map(positionKey(board, whiteToMove) -> 1)
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
    FullFen.parse(fen) match {
      case Success(state) =>
        currentBoard = state.board
        resetHistory(
          board = state.board,
          whiteToMove = state.whiteToMove,
          halfmoveClock = state.halfmoveClock,
          fullmoveNumber = state.fullmoveNumber
        )
        notifyObservers(MoveResult.Moved(state.board))
        Right(state.board)
      case Failure(e) =>
        Left(s"Failed to parse FEN: ${e.getMessage}")
    }
  }

  /** Get the current board position as FEN notation.
    * @return
    *   The FEN string representing the current board
    */
  def getBoardAsFEN: String = {
    FullFen.render(board, _isWhiteToMove, _halfmoveClock, _fullmoveNumber)
  }

  /** Apply a move using PGN notation (e.g., "e4", "Nf3", "O-O", "e8=Q").
    * @return
    *   [[MoveResult]] — [[MoveResult.Moved]] with board and game event on success, or [[MoveResult.Failed]] with error
    *   reason on failure
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
    *   [[MoveResult]] — [[MoveResult.Moved]] with board and game event on success, or [[MoveResult.Failed]] with error
    *   reason on failure
    */
  def applyMove(from: Square, to: Square, promotion: Option[PromotableRole] = None): MoveResult =
    if !isAtLatest then return MoveResult.Failed(board, MoveError.InvalidMove)
    val boardBefore = board
    val wasWhite = _isWhiteToMove
    val moveFacts = MoveFacts.from(boardBefore, from, to)
    val result = validateActivePiece(from) match
      case Left(error) => MoveResult.Failed(board, error)
      case Right(_) =>
        board.move(from, to, promotion) match
          case moved: MoveResult.Moved =>
            finalizeSuccessfulMove(from, to, boardBefore, wasWhite, moveFacts, moved)
          case failed: MoveResult.Failed =>
            failed
    notifyObservers(result)
    result

  private def validateActivePiece(from: Square): Either[MoveError, Piece] =
    board.pieceAt(from) match
      case Some(piece) if piece.color == activeColor => Right(piece)
      case Some(_)                                   => Left(MoveError.WrongColor)
      case None                                      => Left(MoveError.NoPiece)

  private def finalizeSuccessfulMove(
      from: Square,
      to: Square,
      boardBefore: Board,
      wasWhite: Boolean,
      moveFacts: MoveFacts,
      moved: MoveResult.Moved
  ): MoveResult =
    val pgn = PGNParser.toAlgebraic(from, to, boardBefore, moved.board, wasWhite)
    currentBoard = moved.board
    _isWhiteToMove = !_isWhiteToMove
    _halfmoveClock = nextHalfmoveClock(moveFacts)
    _fullmoveNumber = nextFullmoveNumber(wasWhite)
    appendHistory(moved.board, pgn)
    val repetitionCount = updatePositionCount(moved.board, _isWhiteToMove)
    if repetitionCount >= 3 then MoveResult.Moved(moved.board, GameEvent.ThreefoldRepetition)
    else moved

  private def nextHalfmoveClock(moveFacts: MoveFacts): Int =
    if moveFacts.isPawnMove || moveFacts.isCapture then 0 else _halfmoveClock + 1

  private def nextFullmoveNumber(wasWhite: Boolean): Int =
    if wasWhite then _fullmoveNumber else _fullmoveNumber + 1

  private def appendHistory(nextBoard: Board, pgn: String): Unit =
    _history = _history :+ HistoryState(
      board = nextBoard,
      whiteToMove = _isWhiteToMove,
      halfmoveClock = _halfmoveClock,
      fullmoveNumber = _fullmoveNumber
    )
    _pgnMoves = _pgnMoves :+ pgn
    _currentIndex = _history.length - 1
    _redoHistory = Vector.empty
    _redoMoves = Vector.empty

  private def updatePositionCount(board: Board, whiteToMove: Boolean): Int =
    val key = positionKey(board, whiteToMove)
    val count = positionCounts.getOrElse(key, 0) + 1
    positionCounts = positionCounts.updated(key, count)
    count

  private case class MoveFacts(isPawnMove: Boolean, isCapture: Boolean)

  private object MoveFacts:
    def from(board: Board, from: Square, to: Square): MoveFacts =
      val movingPiece = board.pieceAt(from)
      val isPawnMove = movingPiece.exists(_.role == Role.Pawn)
      val isEnPassantCapture =
        isPawnMove && from.file != to.file && board.pieceAt(to).isEmpty
      val isCapture = board.pieceAt(to).isDefined || isEnPassantCapture
      MoveFacts(isPawnMove, isCapture)

  /** Load a game from PGN move text (e.g. "1. e4 e5 2. Nf3 Nc6"). Resets to the initial board and applies all moves in
    * sequence.
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

  def isThreefoldRepetition: Boolean =
    positionCounts.getOrElse(positionKey(board, _isWhiteToMove), 0) >= 3

  /** True if playing `newBoard` next would create the third occurrence. */
  def wouldBeThirdRepetition(newBoard: Board): Boolean =
    positionCounts.getOrElse(positionKey(newBoard, !_isWhiteToMove), 0) >= 2

  def gameStatus: String =
    if isCheckmate then s"Checkmate! ${activeColor.opposite} wins!"
    else if isStalemate then "Stalemate! The game is a draw."
    else if isThreefoldRepetition then "Draw by threefold repetition."
    else if isInCheck then s"${activeColor} is in check!"
    else s"${activeColor} to move"
