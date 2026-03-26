package chess.io

import chess.model.Board
import scala.util.Try

/** A format-agnostic interface for serializing and deserializing a [[Board]].
  *
  * Implementations provide concrete strategies for specific formats
  * (e.g. JSON, FEN, PGN). The controller and views depend only on this
  * trait, making it easy to swap or add formats without changing consumers.
  *
  * @see [[chess.io.json.circe.CirceJsonFileIO]]
  */
trait FileIO:

  /** Serialize a board to a string representation.
    *
    * @param board
    *   the board state to serialize
    * @return
    *   the serialized string (JSON, FEN, PGN, …)
    */
  def save(board: Board): String

  /** Deserialize a board from a string representation.
    *
    * @param input
    *   the serialized string
    * @return
    *   `Success(board)` if parsing succeeds, `Failure` with a descriptive
    *   error otherwise
    */
  def load(input: String): Try[Board]
