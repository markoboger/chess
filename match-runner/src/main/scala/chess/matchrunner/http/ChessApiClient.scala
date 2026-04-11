package chess.matchrunner.http

import cats.effect.IO
import chess.model.GameSettings
import org.http4s.Uri

trait ChessApiClient:
  def createGame(startFen: Option[String], settings: GameSettings): IO[Either[ChessApiClient.ChessApiError, CreateGameResponse]]
  def createPassiveCvCGame(
      whiteStrategy: String,
      blackStrategy: String,
      startFen: Option[String] = None,
      clockInitialMs: Option[Long] = None,
      clockIncrementMs: Option[Long] = None
  ): IO[Either[ChessApiClient.ChessApiError, CreateGameResponse]]
  def getGameState(gameId: String): IO[Either[ChessApiClient.ChessApiError, GameStateResponse]]

object ChessApiClient:

  final case class ChessApiError(
      message: String,
      statusCode: Option[Int] = None,
      details: Option[String] = None
  )

  def http(baseUri: Uri, client: org.http4s.client.Client[IO]): ChessApiClient =
    new HttpChessApiClient(baseUri, client)
