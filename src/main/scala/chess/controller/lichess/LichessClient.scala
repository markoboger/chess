package chess.controller.lichess

import cats.effect.*
import cats.implicits.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.circe.*
import org.http4s.headers.Authorization
import io.circe.parser.*
import fs2.Stream
import chess.controller.lichess.LichessModels.*

import scala.concurrent.duration.*

/** HTTP client for Lichess Bot API */
trait LichessClient[F[_]]:
  /** Get bot account profile */
  def getAccount: F[BotProfile]

  /** Stream incoming events (challenges, game starts) */
  def streamEvents: Stream[F, String]

  /** Stream game state updates for a specific game */
  def streamGame(gameId: String): Stream[F, String]

  /** Make a move in a game */
  def makeMove(gameId: String, move: String): F[Boolean]

  /** Accept a challenge */
  def acceptChallenge(challengeId: String): F[Boolean]

  /** Decline a challenge */
  def declineChallenge(challengeId: String): F[Boolean]

  /** Resign a game */
  def resignGame(gameId: String): F[Boolean]

object LichessClient:

  /** Create a new Lichess client */
  def apply[F[_]: Async](httpClient: Client[F], config: BotConfig): LichessClient[F] =
    new LichessClientImpl[F](httpClient, config)

  private class LichessClientImpl[F[_]: Async](
      httpClient: Client[F],
      config: BotConfig
  ) extends LichessClient[F]:

    private val authHeader = Authorization(Credentials.Token(AuthScheme.Bearer, config.apiToken))

    private def baseRequest(path: String): Request[F] =
      Request[F](
        method = Method.GET,
        uri = Uri.unsafeFromString(s"${config.baseUrl}$path"),
        headers = Headers(authHeader)
      )

    override def getAccount: F[BotProfile] =
      val request = baseRequest("/api/account")
      httpClient.expect[BotProfile](request)(jsonOf[F, BotProfile])

    override def streamEvents: Stream[F, String] =
      val request = baseRequest("/api/stream/event")
      httpClient.stream(request).flatMap(_.body.through(fs2.text.utf8.decode).through(fs2.text.lines))

    override def streamGame(gameId: String): Stream[F, String] =
      val request = baseRequest(s"/api/bot/game/stream/$gameId")
      httpClient.stream(request).flatMap(_.body.through(fs2.text.utf8.decode).through(fs2.text.lines))

    override def makeMove(gameId: String, move: String): F[Boolean] =
      val request = Request[F](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"${config.baseUrl}/api/bot/game/$gameId/move/$move"),
        headers = Headers(authHeader)
      )
      httpClient
        .run(request)
        .use { response =>
          response.status match
            case Status.Ok => Async[F].pure(true)
            case _         => Async[F].pure(false)
        }

    override def acceptChallenge(challengeId: String): F[Boolean] =
      val request = Request[F](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"${config.baseUrl}/api/challenge/$challengeId/accept"),
        headers = Headers(authHeader)
      )
      httpClient
        .run(request)
        .use { response =>
          response.status match
            case Status.Ok => Async[F].pure(true)
            case _         => Async[F].pure(false)
        }

    override def declineChallenge(challengeId: String): F[Boolean] =
      val request = Request[F](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"${config.baseUrl}/api/challenge/$challengeId/decline"),
        headers = Headers(authHeader)
      )
      httpClient
        .run(request)
        .use { response =>
          response.status match
            case Status.Ok => Async[F].pure(true)
            case _         => Async[F].pure(false)
        }

    override def resignGame(gameId: String): F[Boolean] =
      val request = Request[F](
        method = Method.POST,
        uri = Uri.unsafeFromString(s"${config.baseUrl}/api/bot/game/$gameId/resign"),
        headers = Headers(authHeader)
      )
      httpClient
        .run(request)
        .use { response =>
          response.status match
            case Status.Ok => Async[F].pure(true)
            case _         => Async[F].pure(false)
        }
