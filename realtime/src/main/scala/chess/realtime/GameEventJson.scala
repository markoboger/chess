package chess.realtime

import chess.application.game.GameSessionEvent
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

import java.time.Instant
import scala.util.Try

object GameEventJson:
  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] = Decoder.decodeString.emap { value =>
    Try(Instant.parse(value)).toEither.left.map(_.getMessage)
  }
  given Encoder[GameSessionEvent] = deriveEncoder
  given Decoder[GameSessionEvent] = deriveDecoder
