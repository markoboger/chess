package chess.tournament

import io.circe.parser.parse as parseJson
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Minimal JWT payload decode — reads `sub` without signature verification (local config only). */
object JwtSub:

  def subject(token: String): Option[String] =
    val parts = token.trim.split("\\.").toList
    parts match
      case _ :: payload :: _ =>
        val padded =
          payload + ("=" * ((4 - payload.length % 4) % 4))
        val jsonStr =
          try
            new String(Base64.getUrlDecoder.decode(padded), StandardCharsets.UTF_8)
          catch
            case _: IllegalArgumentException => return None
        parseJson(jsonStr).toOption.flatMap(_.hcursor.get[String]("sub").toOption)
      case _ => None

end JwtSub
