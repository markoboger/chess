package chess.io.json.upickle

import chess.io.FileIO
import chess.model.Board
import _root_.upickle.default.*
import scala.util.{Try, Success, Failure}

/** A [[FileIO]] implementation that uses the uPickle library for JSON
  * serialization.
  *
  * All uPickle-specific codec definitions live in [[BoardCodecs]]; this
  * class simply wires them into the format-agnostic [[FileIO]] interface.
  */
class UPickleJsonFileIO extends FileIO:
  import BoardCodecs.given

  override def save(board: Board): String =
    write(board, indent = 2)

  override def load(input: String): Try[Board] =
    Try(read[Board](input))
