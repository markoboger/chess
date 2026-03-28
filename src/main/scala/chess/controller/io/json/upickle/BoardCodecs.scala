package chess.controller.io.json.upickle

import chess.model.{Board, Piece, Color, Role, Square, File, Rank}
import _root_.upickle.default.*

/** uPickle [[ReadWriter]] instances for the chess model types.
  *
  * All codecs are defined here — the model classes carry no uPickle annotations, keeping the library dependency
  * contained in this package.
  *
  * The JSON structure is identical to the Circe implementation so that files produced by one library can be read by the
  * other.
  */
object BoardCodecs:

  // --- Color ---
  given ReadWriter[Color] = readwriter[String].bimap(
    _.toString,
    {
      case "White" => Color.White
      case "Black" => Color.Black
      case other   => throw new IllegalArgumentException(s"Invalid color: $other")
    }
  )

  // --- Role ---
  given ReadWriter[Role] = readwriter[String].bimap(
    _.toString,
    s =>
      Role.values
        .find(_.toString == s)
        .getOrElse(
          throw new IllegalArgumentException(s"Invalid role: $s")
        )
  )

  // --- Square ---
  given ReadWriter[Square] = readwriter[String].bimap(
    _.toString,
    s =>
      Square
        .fromString(s)
        .getOrElse(
          throw new IllegalArgumentException(s"Invalid square: $s")
        )
  )

  // --- Piece ---
  given ReadWriter[Piece] = readwriter[ujson.Value].bimap(
    p =>
      ujson.Obj(
        "role" -> writeJs(p.role),
        "color" -> writeJs(p.color)
      ),
    json => {
      val obj = json.obj
      Piece(read[Role](obj("role")), read[Color](obj("color")))
    }
  )

  // --- Board ---
  // 8x8 array where each cell is null or a Piece object.
  // lastMove serialized as an optional [from, to] pair.
  given ReadWriter[Board] = readwriter[ujson.Value].bimap(
    board => {
      val squaresJson = ujson.Arr.from(
        board.squares.map { rank =>
          ujson.Arr.from(
            rank.map {
              case Some(piece) => writeJs(piece)
              case None        => ujson.Null
            }
          )
        }
      )

      val lastMoveJson = board.lastMove match
        case Some((from, to)) =>
          ujson.Arr(writeJs(from), writeJs(to))
        case None => ujson.Null

      ujson.Obj(
        "squares" -> squaresJson,
        "lastMove" -> lastMoveJson
      )
    },
    json => {
      val obj = json.obj
      val squares = obj("squares").arr.map { rank =>
        rank.arr.map { cell =>
          if cell.isNull then None
          else Some(read[Piece](cell))
        }.toVector
      }.toVector

      val lastMove = obj("lastMove") match
        case v if v.isNull => None
        case v =>
          val arr = v.arr
          Some((read[Square](arr(0)), read[Square](arr(1))))

      Board(squares, lastMove)
    }
  )
