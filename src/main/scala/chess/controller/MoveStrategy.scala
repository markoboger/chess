package chess.controller

import chess.model.{Board, Color, Square, Rank, Role, PromotableRole}

/** Selects a move for the computer player.
  *
  * Implementations receive the current board and the color to move, and return the chosen move as `(from, to,
  * promotionPiece)`, or `None` when there are no legal moves (game over).
  */
trait MoveStrategy:
  def name: String
  def selectMove(board: Board, color: Color): Option[(Square, Square, Option[PromotableRole])]

object MoveStrategy:
  /** Promotes to Queen whenever a pawn reaches the back rank. */
  def promotionFor(board: Board, from: Square, to: Square, color: Color): Option[PromotableRole] =
    board.pieceAt(from).flatMap { piece =>
      val backRank = if color == Color.White then Rank._8 else Rank._1
      if piece.role == Role.Pawn && to.rank == backRank then Some(PromotableRole.Queen)
      else None
    }
