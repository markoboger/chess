package chess.controller

import chess.model.{Board, Color, Square, File, Rank}
import chess.controller.parser.{PGNParser, FENParser}
import scalafx.beans.property.ObjectProperty
import scala.util.{Try, Success, Failure}

final class GameController(initialBoard: Board):
  val boardProperty: ObjectProperty[Board] = ObjectProperty(initialBoard)
  private var _isWhiteToMove: Boolean = true

  def board: Board = boardProperty.value
  def board_=(newBoard: Board): Unit = boardProperty.value = newBoard

  def isWhiteToMove: Boolean = _isWhiteToMove

  /** Notify listeners about the initial board that was created elsewhere. */
  def announceInitial(board: Board): Unit = {
    boardProperty.value = board
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
        this.board = newBoard
        _isWhiteToMove = true
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

  /** Apply a move to the given board using PGN notation.
    * @param pgnMove
    *   The move in PGN notation (e.g., "e4", "Nf3", "O-O")
    * @return
    *   The new board state after the move, or an error message
    */
  def applyPgnMove(pgnMove: String): Either[String, Board] = {
    PGNParser.parseMove(pgnMove, board, _isWhiteToMove) match {
      case Success((from, to)) =>
        applyMove(from, to) match {
          case Some(newBoard) =>
            Right(newBoard)
          case None =>
            Left(s"Invalid move: $pgnMove is not a legal move in this position")
        }
      case Failure(e) =>
        Left(s"Failed to parse move '$pgnMove': ${e.getMessage}")
    }
  }

  /** Apply a move to the given board using file/rank coordinates.
    * @return
    *   The new board if the move was valid, None otherwise
    */
  def applyMove(from: Square, to: Square): Option[Board] = {
    board.pieceAt(from) match {
      case Some(piece)
          if piece.color == (if (_isWhiteToMove) Color.White
                             else Color.Black) =>
        val nextBoard = board.move(from, to)
        if (nextBoard != board) {
          this.board = nextBoard
          _isWhiteToMove = !_isWhiteToMove
          Some(nextBoard)
        } else {
          None
        }
      case _ => None
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

  /** Apply a move to the given board and return the new board.
    * @deprecated
    *   Use applyMove(from, to) instead
    */
  def applyMove(board: Board, from: Square, to: Square): Board = {
    val nextBoard = board.move(from, to)
    this.board = nextBoard
    nextBoard
  }
