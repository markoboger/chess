package chess.controller.io

import scala.util.Try

/** PGN-specific game serialization interface.
  *
  * Unlike [[chess.controller.io.FileIO]] which serializes a single board
  * position, PGN represents a game as a sequence of moves. The save/load
  * contract therefore operates on move lists rather than boards.
  */
trait PgnIO:

  /** Serialize a list of PGN move strings to formatted PGN text.
    *
    * @param moves
    *   the move strings in order (e.g. Vector("e4", "e5", "Nf3", "Nc6"))
    * @return
    *   formatted PGN text with move numbers (e.g. "1. e4 e5 2. Nf3 Nc6")
    */
  def save(moves: Vector[String]): String

  /** Deserialize PGN text into a list of move tokens.
    *
    * Strips move numbers, comments, variations, and result markers.
    *
    * @param input
    *   the PGN text
    * @return
    *   `Success(moves)` with the ordered move tokens, or `Failure` with a
    *   descriptive error
    */
  def load(input: String): Try[Vector[String]]
