package chess.model

/** Represents the game state resulting from a successful move. */
enum GameEvent:
  /** A normal move with no special game state change. */
  case Moved

  /** The move puts the opponent's king in check. */
  case Check

  /** The move results in checkmate — the game is over. */
  case Checkmate

  /** The move results in stalemate — the game is a draw. */
  case Stalemate

  /** The same position has occurred three times — the game is a draw. */
  case ThreefoldRepetition
