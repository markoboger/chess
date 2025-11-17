package chess.controller

import chess.model.{Board, PieceColor}
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

  /**
   * Load a position from FEN notation.
   * @param fen The FEN string (e.g., "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
   * @return Either the new board or an error message
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

  /**
   * Get the current board position as FEN notation.
   * @return The FEN string representing the current board
   */
  def getBoardAsFEN: String = {
    FENParser.boardToFEN(board)
  }

  /** 
   * Apply a move to the given board using PGN notation.
   * @param pgnMove The move in PGN notation (e.g., "e4", "Nf3", "O-O")
   * @return The new board state after the move, or an error message
   */
  def applyPgnMove(pgnMove: String): Either[String, Board] = {
    PGNParser.parseMove(pgnMove, board, _isWhiteToMove) match {
      case Success((fromFile, fromRank, toFile, toRank)) =>
        applyMove(fromFile, fromRank, toFile, toRank) match {
          case Some(newBoard) => 
            Right(newBoard)
          case None => 
            Left(s"Invalid move: $pgnMove is not a legal move in this position")
        }
      case Failure(e) => 
        Left(s"Failed to parse move '$pgnMove': ${e.getMessage}")
    }
  }

  /** 
   * Apply a move to the given board using file/rank coordinates.
   * @return The new board if the move was valid, None otherwise
   */
  def applyMove(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Option[Board] = {
    board.pieceAt(fromFile, fromRank) match {
      case Some(piece) if piece.color == (if (_isWhiteToMove) PieceColor.White else PieceColor.Black) =>
        val nextBoard = board.move(fromFile, fromRank, toFile, toRank)
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

  /** 
   * Apply a move to the given board and return the new board.
   * @deprecated Use applyMove(fromFile, fromRank, toFile, toRank) instead
   */
  def applyMove(board: Board, fromFile: Int, fromRank: Int, toFile: Int, toRank: Int): Board = {
    val nextBoard = board.move(fromFile, fromRank, toFile, toRank)
    this.board = nextBoard
    nextBoard
  }
