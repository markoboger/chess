package chess.lichess

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.text
import io.circe.Json
import io.circe.parser.parse as parseJson
import org.http4s.*
import org.http4s.Credentials.Token
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Authorization
import org.typelevel.ci.CIStringSyntax
import scala.concurrent.duration.*

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

  /** Run request, read body as string (best-effort), retry on HTTP 429 with backoff. */
  private def runAuthedWithRetry(req: Request[IO], max429Retries: Int = 5): IO[(Status, String)] =
    def attempt(n: Int): IO[(Status, String)] =
      client.run(authed(req)).use { resp =>
        resp.as[String].attempt.flatMap { bodyEither =>
          val body = bodyEither.getOrElse("")
          if resp.status.code == 429 && n < max429Retries then
            IO.println(s"[lichess] HTTP 429, sleeping 60s (retry ${n + 1}/$max429Retries)") >>
              IO.sleep(60.seconds) >>
              attempt(n + 1)
          else IO.pure((resp.status, body))
        }
      }
    attempt(0)

  /** Bot account id (lowercase) from `GET /api/account`. */
  def fetchAccountId: IO[String] =
    val uri = apiRoot / "api" / "account"
    val req = Request[IO](Method.GET, uri)
    runAuthedWithRetry(req).flatMap { case (st, body) =>
      if st.isSuccess then
        IO.fromEither(
          parseJson(body)
            .flatMap(_.hcursor.get[String]("id"))
            .leftMap(e => new RuntimeException(e.getMessage))
        )
      else IO.raiseError(new RuntimeException(s"GET /api/account failed: HTTP $st body=$body"))
    }

  def acceptChallenge(challengeId: String): IO[Unit] =
    val uri = apiRoot / "api" / "challenge" / challengeId / "accept"
    val req = Request[IO](Method.POST, uri)
    runAuthedWithRetry(req).flatMap { case (st, body) =>
      if st.isSuccess then IO.unit
      else IO.raiseError(new RuntimeException(s"accept challenge failed: HTTP $st $body"))
    }

  def declineChallenge(challengeId: String): IO[Unit] =
    val uri = apiRoot / "api" / "challenge" / challengeId / "decline"
    val req = Request[IO](Method.POST, uri)
    runAuthedWithRetry(req).void

  def postBotMove(gameId: String, uci: String): IO[Unit] =
    val uri = apiRoot / "api" / "bot" / "game" / gameId / "move" / uci
    val req = Request[IO](Method.POST, uri)
    runAuthedWithRetry(req).flatMap { case (st, _) =>
      if st.isSuccess then IO.unit
      else if st.code == 400 then IO.println(s"[lichess] move POST rejected: HTTP $st for $uci")
      else IO.raiseError(new RuntimeException(s"move POST failed: HTTP $st"))
    }

  /** Open a standard game challenge to `username` (bot or human account). */
  def createUserChallenge(username: String): IO[Either[String, Unit]] =
    val clean = username.trim.toLowerCase
    if clean.isEmpty then IO.pure(Left("empty username"))
    else
      val uri = apiRoot / "api" / "challenge" / clean
      val body = Json.obj(
        "rated"   -> Json.fromBoolean(cfg.challengeRated),
        "variant" -> Json.fromString("standard"),
        "clock" -> Json.obj(
          "limit"     -> Json.fromInt(cfg.challengeClockLimitSec),
          "increment" -> Json.fromInt(cfg.challengeClockIncrementSec)
        )
      )
      val req = Request[IO](Method.POST, uri).withEntity(body)
      runAuthedWithRetry(req).map { case (st, responseBody) =>
        if st.isSuccess then Right(())
        else Left(s"HTTP $st: $responseBody")
      }

  /** Long-lived `GET /api/stream/event` as a stream of JSON lines. */
  def botEventStream: Stream[IO, Json] =
    val uri = apiRoot / "api" / "stream" / "event"
    val req = Request[IO](Method.GET, uri)
    ndjsonStream(req)

  /** Long-lived `GET /api/bot/game/stream/{gameId}`. */
  def botGameStream(gameId: String): Stream[IO, Json] =
    val uri = apiRoot / "api" / "bot" / "game" / "stream" / gameId
    val req = Request[IO](Method.GET, uri)
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
      else
        val msg =
          if resp.status.code == 429 then "stream open: HTTP 429 (rate limit)"
          else s"stream open failed: HTTP ${resp.status}"
        Stream.raiseError[IO](new RuntimeException(msg))
    }

end LichessClient

object LichessClient:

  def resource(cfg: LichessBotConfig): Resource[IO, LichessClient] =
    EmberClientBuilder.default[IO].build.map(c => new LichessClient(cfg, c))

end LichessClient
