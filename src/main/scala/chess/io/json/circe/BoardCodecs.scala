package chess.io.json.circe

import chess.model.{Board, Piece, Color, Role, Square, File, Rank}
import _root_.io.circe.{Encoder, Decoder, Json, HCursor, DecodingFailure}

/** Circe [[Encoder]]/[[Decoder]] instances for the chess model types.
  *
  * All codecs are defined here — the model classes carry no Circe annotations,
  * keeping the library dependency contained in this package.
  */
object BoardCodecs:

  // --- Color ---
  given Encoder[Color] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Color] = Decoder.decodeString.emap {
    case "White" => Right(Color.White)
    case "Black" => Right(Color.Black)
    case other   => Left(s"Invalid color: $other")
  }

  // --- Role ---
  given Encoder[Role] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Role] = Decoder.decodeString.emap { s =>
    Role.values.find(_.toString == s).toRight(s"Invalid role: $s")
  }

  // --- Square ---
  given Encoder[Square] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Square] = Decoder.decodeString.emap { s =>
    Square.fromString(s).toRight(s"Invalid square: $s")
  }

  // --- Piece ---
  given Encoder[Piece] = Encoder.instance { p =>
    Json.obj(
      "role" -> Encoder[Role].apply(p.role),
      "color" -> Encoder[Color].apply(p.color)
    )
  }
  given Decoder[Piece] = Decoder.instance { c =>
    for
      role <- c.get[Role]("role")
      color <- c.get[Color]("color")
    yield Piece(role, color)
  }

  // --- Board ---
  // Compact representation: 8x8 array where each cell is either null or a Piece object.
  // lastMove is serialized as an optional [from, to] pair.
  given Encoder[Board] = Encoder.instance { board =>
    val squaresJson = Json.arr(
      board.squares.map { rank =>
        Json.arr(
          rank.map {
            case Some(piece) => Encoder[Piece].apply(piece)
            case None        => Json.Null
          }*
        )
      }*
    )

    val lastMoveJson = board.lastMove match
      case Some((from, to)) =>
        Json.arr(Encoder[Square].apply(from), Encoder[Square].apply(to))
      case None => Json.Null

    Json.obj(
      "squares" -> squaresJson,
      "lastMove" -> lastMoveJson
    )
  }

  given Decoder[Board] = Decoder.instance { c =>
    for
      squares <- c.get[Vector[Vector[Option[Piece]]]]("squares")
      lastMove <- c
        .get[Option[Vector[Square]]]("lastMove")
        .map(_.collect { case Vector(from, to) => (from, to) })
    yield Board(squares, lastMove)
  }
