package chess.controller

import chess.model.{Board, Color, Square, File, Rank, MoveResult, MoveError}
import chess.controller.parser.{PGNParser, FENParser}
import chess.util.Observable
import scala.util.{Try, Success, Failure}

final class GameController(initialBoard: Board) extends Observable[MoveResult]:
  private var currentBoard: Board = initialBoard
  private var _isWhiteToMove: Boolean = true

  def board: Board = currentBoard

  def isWhiteToMove: Boolean = _isWhiteToMove

  /** Notify observers about a board reset (new game, FEN load, etc.). */
  def announceInitial(board: Board): Unit = {
    currentBoard = board
    notifyObservers(MoveResult.Moved(board))
  }

  /** Load a position from FEN notation.
    * @param fen
    *   The FEN string (e.g., "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
    * @return
    *   Either the new board or an error message
    */
  def loadFromFEN(fen: String): Either[String, Board] = {
    FENParser.parseFEN(fen) match {
      case Success(newBoard) =>
        currentBoard = newBoard
        _isWhiteToMove = true
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
    FENParser.boardToFEN(board)
  }

  /** Apply a move using PGN notation (e.g., "e4", "Nf3", "O-O").
    * @return
    *   [[MoveResult]] — [[MoveResult.Moved]] with board and game event on
    *   success, or [[MoveResult.Failed]] with error reason on failure
    */
  def applyPgnMove(pgnMove: String): MoveResult =
    PGNParser.parseMove(pgnMove, board, _isWhiteToMove) match
      case Success((from, to)) => applyMove(from, to)
      case Failure(e) =>
        MoveResult.Failed(board, MoveError.ParseError(e.getMessage))

  /** Apply a move using file/rank coordinates.
    * @return
    *   [[MoveResult]] — [[MoveResult.Moved]] with board and game event on
    *   success, or [[MoveResult.Failed]] with error reason on failure
    */
  def applyMove(from: Square, to: Square): MoveResult =
    val result = board.pieceAt(from) match
      case Some(piece) if piece.color == activeColor =>
        board.move(from, to) match
          case moved: MoveResult.Moved =>
            currentBoard = moved.board
            _isWhiteToMove = !_isWhiteToMove
            moved
          case failed: MoveResult.Failed =>
            failed
      case Some(_) => MoveResult.Failed(board, MoveError.WrongColor)
      case None    => MoveResult.Failed(board, MoveError.NoPiece)
    notifyObservers(result)
    result

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
