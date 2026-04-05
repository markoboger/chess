package chess.persistence.model

/** Represents a chess opening stored in the database.
  *
  * Multiple openings can share the same ECO code (e.g., A00 covers Polish Opening, Grob Opening, etc.). The combination
  * of (eco, name) is unique.
  *
  * @param eco
  *   ECO (Encyclopedia of Chess Openings) code (e.g., "A00", "B12", "C45")
  * @param name
  *   Full name of the opening (e.g., "Sicilian Defense: Najdorf Variation, English Attack")
  * @param moves
  *   PGN moves representing the opening line
  * @param fen
  *   FEN position after the opening moves are played
  * @param moveCount
  *   Number of half-moves (plies) in the opening line
  */
final case class Opening(
    eco: String,
    name: String,
    moves: String,
    fen: String,
    moveCount: Int
)

object Opening:
  /** Creates an Opening from basic components. Validates ECO code format.
    */
  def apply(eco: String, name: String, moves: String, fen: String, moveCount: Int): Opening =
    require(eco.matches("[A-E][0-9]{2}"), s"Invalid ECO code format: $eco (expected A00-E99)")
    require(name.nonEmpty, "Opening name cannot be empty")
    require(moves.nonEmpty, "Opening moves cannot be empty")
    require(fen.nonEmpty, "Opening FEN cannot be empty")
    require(moveCount > 0, s"Move count must be positive: $moveCount")
    new Opening(eco, name, moves, fen, moveCount)

  /** Creates an Opening without validation (used for deserialization).
    */
  def unsafe(eco: String, name: String, moves: String, fen: String, moveCount: Int): Opening =
    new Opening(eco, name, moves, fen, moveCount)
