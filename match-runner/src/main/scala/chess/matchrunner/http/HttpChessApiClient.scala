package chess.matchrunner.http

import cats.effect.IO
import chess.matchrunner.http.ChessApiClient.ChessApiError
import chess.model.GameSettings
import io.circe.Decoder
import io.circe.parser.decode
import org.http4s.{Method, Request, Uri}
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client

private final class HttpChessApiClient(baseUri: Uri, client: Client[IO]) extends ChessApiClient:

  override def createGame(
      startFen: Option[String],
      settings: GameSettings
  ): IO[Either[ChessApiError, CreateGameResponse]] =
    val request = CreateGameRequest(startFen = startFen, settings = settings)
    val uri = baseUri / "games"
    val httpRequest = Request[IO](method = Method.POST, uri = uri).withEntity(request)
    runRequest[CreateGameResponse](httpRequest)

  override def createPassiveCvCGame(
      whiteStrategy: String,
      blackStrategy: String,
      startFen: Option[String] = None,
      clockInitialMs: Option[Long] = None,
      clockIncrementMs: Option[Long] = None
  ): IO[Either[ChessApiError, CreateGameResponse]] =
    createGame(
      startFen = startFen,
      settings = GameSettings(
        whiteIsHuman = false,
        blackIsHuman = false,
        whiteStrategy = whiteStrategy,
        blackStrategy = blackStrategy,
        clockInitialMs = clockInitialMs,
        clockIncrementMs = clockIncrementMs,
        backendAutoplay = true
      )
    )

  override def getGameState(gameId: String): IO[Either[ChessApiError, GameStateResponse]] =
    val uri = baseUri / "games" / gameId
    runRequest[GameStateResponse](Request[IO](method = Method.GET, uri = uri))

  private def runRequest[A: Decoder](request: org.http4s.Request[IO]): IO[Either[ChessApiError, A]] =
    client.run(request).use { response =>
      response.bodyText.compile.string.map { body =>
        if response.status.isSuccess then decodeSuccess[A](body)
        else Left(decodeError(body, response.status.code))
      }
    }

  private def decodeSuccess[A: Decoder](body: String): Either[ChessApiError, A] =
    decode[A](body).left.map { failure =>
      ChessApiError(s"Failed to decode success response: ${failure.getMessage}")
    }

  private def decodeError(body: String, statusCode: Int): ChessApiError =
    decode[ErrorResponse](body) match
      case Right(error) =>
        ChessApiError(
          message = error.error,
          statusCode = Some(statusCode),
          details = error.details
        )
      case Left(_) =>
        ChessApiError(
          message = s"HTTP request failed with status $statusCode",
          statusCode = Some(statusCode),
          details = Option(body).filter(_.nonEmpty)
        )
