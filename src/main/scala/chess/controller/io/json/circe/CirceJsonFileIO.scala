package chess.controller.io.json.circe

import chess.controller.io.FileIO
import chess.model.Board
import _root_.io.circe.syntax.*
import _root_.io.circe.parser.decode
import scala.util.{Try, Success, Failure}

/** A [[FileIO]] implementation that uses the Circe library for JSON
  * serialization.
  *
  * All Circe-specific codec definitions live in [[BoardCodecs]]; this class
  * simply wires them into the format-agnostic [[FileIO]] interface.
  */
class CirceJsonFileIO extends FileIO:
  import BoardCodecs.given

  override def save(board: Board): String =
    board.asJson.spaces2

  override def load(input: String): Try[Board] =
    decode[Board](input) match
      case Right(board) => Success(board)
      case Left(error) =>
        Failure(new IllegalArgumentException(error.getMessage))
