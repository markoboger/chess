package chess.lichess

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.text
import io.circe.Json
import io.circe.parser.parse as parseJson
import org.http4s.*
import org.http4s.Credentials.Token
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Authorization
import org.typelevel.ci.CIStringSyntax

/** HTTP + NDJSON helpers for the Lichess Bot API. */
final class LichessClient(cfg: LichessBotConfig, client: Client[IO]):

  private val apiRoot: Uri =
    val s = cfg.baseUri.trim
    Uri.unsafeFromString(if s.endsWith("/") then s.dropRight(1) else s)

  private def bearerHeaders: Headers =
    Headers(
      Authorization(Token(AuthScheme.Bearer, cfg.token)),
      Header.Raw(ci"User-Agent", cfg.userAgent)
    )

  private def authed(req: Request[IO]): Request[IO] =
    req.putHeaders(bearerHeaders)

  /** Bot account id (lowercase) from `GET /api/account`. */
  def fetchAccountId: IO[String] =
    val uri = apiRoot / "api" / "account"
    val req = authed(Request[IO](Method.GET, uri))
    client.expect[String](req).flatMap { body =>
      IO.fromEither(
        parseJson(body)
          .flatMap(_.hcursor.get[String]("id"))
          .leftMap(e => new RuntimeException(e.getMessage))
      )
    }

  def acceptChallenge(challengeId: String): IO[Unit] =
    val uri = apiRoot / "api" / "challenge" / challengeId / "accept"
    val req = authed(Request[IO](Method.POST, uri))
    client.status(req).flatMap { s =>
      if s.isSuccess then IO.unit
      else IO.raiseError(new RuntimeException(s"accept challenge failed: HTTP $s"))
    }

  def declineChallenge(challengeId: String): IO[Unit] =
    val uri = apiRoot / "api" / "challenge" / challengeId / "decline"
    val req = authed(Request[IO](Method.POST, uri))
    client.status(req).void

  def postBotMove(gameId: String, uci: String): IO[Unit] =
    val uri = apiRoot / "api" / "bot" / "game" / gameId / "move" / uci
    val req = authed(Request[IO](Method.POST, uri))
    client.status(req).flatMap { s =>
      if s.isSuccess then IO.unit
      else if s.code == 400 then IO.println(s"[lichess] move POST rejected: HTTP $s for $uci")
      else IO.raiseError(new RuntimeException(s"move POST failed: HTTP $s"))
    }

  /** Long-lived `GET /api/stream/event` as a stream of JSON lines. */
  def botEventStream: Stream[IO, Json] =
    val uri = apiRoot / "api" / "stream" / "event"
    val req = authed(Request[IO](Method.GET, uri))
    ndjsonStream(req)

  /** Long-lived `GET /api/bot/game/stream/{gameId}`. */
  def botGameStream(gameId: String): Stream[IO, Json] =
    val uri = apiRoot / "api" / "bot" / "game" / "stream" / gameId
    val req = authed(Request[IO](Method.GET, uri))
    ndjsonStream(req)

  private def ndjsonStream(req: Request[IO]): Stream[IO, Json] =
    client.stream(authed(req)).flatMap { resp =>
      if resp.status.isSuccess then
        resp.body
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(_.nonEmpty)
          .evalMap { line =>
            IO.fromEither(parseJson(line).leftMap(e => new RuntimeException(s"NDJSON parse: ${e.message}")))
          }
      else Stream.raiseError[IO](new RuntimeException(s"stream open failed: HTTP ${resp.status}"))
    }

end LichessClient

object LichessClient:

  def resource(cfg: LichessBotConfig): Resource[IO, LichessClient] =
    EmberClientBuilder.default[IO].build.map(c => new LichessClient(cfg, c))

end LichessClient
