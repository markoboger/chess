package chess.tournament

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

/** HTTP + NDJSON client for the NowChess tournament server. */
final class TournamentClient(cfg: TournamentBotConfig, client: Client[IO]):

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

  private def runAuthed(req: Request[IO]): IO[(Status, String)] =
    client.run(authed(req)).use { resp =>
      resp.as[String].attempt.map(body => (resp.status, body.getOrElse("")))
    }

  /** `POST /api/auth/register` — returns `(botId, token)`. */
  def registerBot(name: String): IO[(String, String)] =
    val uri = apiRoot / "api" / "auth" / "register"
    val body = Json.obj("name" -> Json.fromString(name), "isBot" -> Json.fromBoolean(true))
    val req  = Request[IO](Method.POST, uri).withEntity(body)
    client.run(req).use { resp =>
      if !resp.status.isSuccess then
        resp.as[String].flatMap(b => IO.raiseError(new RuntimeException(s"register failed: HTTP ${resp.status} $b")))
      else
        resp.as[Json].flatMap { json =>
          val c = json.hcursor
          IO.fromEither(
            for
              id    <- c.get[String]("id")
              token <- c.get[String]("token")
            yield (id, token)
          ).adaptError(e => new RuntimeException(s"register JSON: ${e.getMessage}"))
        }
    }

  def joinTournament(tournamentId: String): IO[Unit] =
    val uri = apiRoot / "api" / "tournament" / tournamentId / "join"
    val req = Request[IO](Method.POST, uri)
    runAuthed(req).flatMap { case (st, body) =>
      if st.isSuccess then IO.unit
      else if st.code == 409 then IO.println(s"[tournament] already joined or not joinable: $tournamentId")
      else IO.raiseError(new RuntimeException(s"join failed: HTTP $st $body"))
    }

  def listTournaments: IO[Json] =
    val uri = apiRoot / "api" / "tournament"
    val req = Request[IO](Method.GET, uri)
    client.run(req).use { resp =>
      if !resp.status.isSuccess then
        resp.as[String].flatMap(b => IO.raiseError(new RuntimeException(s"list tournaments: HTTP ${resp.status} $b")))
      else resp.as[Json]
    }

  def postMove(tournamentId: String, gameId: String, uci: String): IO[Unit] =
    val uri = apiRoot / "api" / "tournament" / tournamentId / "game" / gameId / "move" / uci
    val req = Request[IO](Method.POST, uri)
    runAuthed(req).flatMap { case (st, _) =>
      if st.isSuccess then IO.unit
      else if st.code == 400 || st.code == 403 || st.code == 409 then
        IO.println(s"[tournament] move rejected: HTTP $st for $uci")
      else IO.raiseError(new RuntimeException(s"move POST failed: HTTP $st"))
    }

  /** `GET /api/tournament/{id}/stream` — tournament lifecycle NDJSON. */
  def tournamentStream(tournamentId: String): Stream[IO, Json] =
    val uri = apiRoot / "api" / "tournament" / tournamentId / "stream"
    ndjsonStream(authed(Request[IO](Method.GET, uri)))

  /** `GET /api/tournament/{id}/game/{gameId}/stream` — in-game NDJSON. */
  def gameStream(tournamentId: String, gameId: String): Stream[IO, Json] =
    val uri = apiRoot / "api" / "tournament" / tournamentId / "game" / gameId / "stream"
    ndjsonStream(authed(Request[IO](Method.GET, uri)))

  private def ndjsonStream(req: Request[IO]): Stream[IO, Json] =
    val r = req
    client.stream(r).flatMap { resp =>
      if resp.status.isSuccess then
        resp.body
          .through(text.utf8.decode)
          .through(text.lines)
          .filter(_.nonEmpty)
          .evalMap(line => IO.fromEither(parseJson(line).leftMap(e => new RuntimeException(s"NDJSON: ${e.message}"))))
      else
        Stream.raiseError[IO](new RuntimeException(s"stream open failed: HTTP ${resp.status}"))
    }

end TournamentClient

object TournamentClient:

  def resource(cfg: TournamentBotConfig): Resource[IO, TournamentClient] =
    EmberClientBuilder.default[IO].build.map(c => new TournamentClient(cfg, c))

end TournamentClient
