package chess.model

/** Represents the reason a move was rejected. */
enum MoveError(val message: String):
  /** No piece exists at the source square. */
  case NoPiece extends MoveError("No piece at source square")
  /** The piece cannot make this move (wrong direction, blocked path, etc.). */
  case InvalidMove extends MoveError("Invalid move for this piece")
  /** The move would leave the player's own king in check. */
  case LeavesKingInCheck extends MoveError("Move would leave king in check")
  /** The piece belongs to the opponent — it is not the player's turn. */
  case WrongColor extends MoveError("Not your piece to move")
  /** The move input could not be parsed (e.g., invalid PGN notation). */
  case ParseError(msg: String) extends MoveError(msg)
  /** A pawn reached the back rank but no promotion piece was specified. */
  case PromotionRequired extends MoveError("Promotion required — use e8=Q, e8=R, e8=B, or e8=N")
