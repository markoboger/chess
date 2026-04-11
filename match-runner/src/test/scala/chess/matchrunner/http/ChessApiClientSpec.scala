package chess.matchrunner.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.matchrunner.http.ChessApiClient.ChessApiError
import chess.model.GameSettings
import io.circe.syntax.*
import org.http4s.Method.POST
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ChessApiClientSpec extends AnyWordSpec with Matchers:

  private val baseUri = Uri.unsafeFromString("http://game-service:8081")

  "ChessApiClient" should {
    "create a passive CvC game with backend autoplay enabled" in {
      val backend = org.http4s.HttpRoutes.of[IO] {
        case req @ POST -> Root / "games" =>
          req.as[CreateGameRequest].flatMap { request =>
            request.settings.whiteIsHuman.shouldBe(false)
            request.settings.blackIsHuman.shouldBe(false)
            request.settings.whiteStrategy.shouldBe("minimax")
            request.settings.blackStrategy.shouldBe("random")
            request.settings.backendAutoplay.shouldBe(true)

            Ok(
              CreateGameResponse(
                gameId = "game-123",
                fen = "start-fen",
                settings = request.settings
              ).asJson
            )
          }
      }.orNotFound

      val client = ChessApiClient.http(baseUri, Client.fromHttpApp(backend))

      val result = client.createPassiveCvCGame("minimax", "random").unsafeRunSync()

      result.shouldBe(
        Right(
        CreateGameResponse(
          gameId = "game-123",
          fen = "start-fen",
          settings = GameSettings(
            whiteIsHuman = false,
            blackIsHuman = false,
            whiteStrategy = "minimax",
            blackStrategy = "random",
            backendAutoplay = true
          )
        )
      ))
    }

    "decode the game state for an existing game" in {
      val settings = GameSettings(whiteIsHuman = false, blackIsHuman = false, backendAutoplay = true)

      val backend = org.http4s.HttpRoutes.of[IO] {
        case GET -> Root / "games" / "game-123" =>
          Ok(
            GameStateResponse(
              gameId = "game-123",
              fen = "fen-123",
              pgn = "1. e4 e5",
              status = "in_progress",
              settings = settings
            ).asJson
          )
      }.orNotFound

      val client = ChessApiClient.http(baseUri, Client.fromHttpApp(backend))

      val result = client.getGameState("game-123").unsafeRunSync()

      result.shouldBe(
        Right(
        GameStateResponse(
          gameId = "game-123",
          fen = "fen-123",
          pgn = "1. e4 e5",
          status = "in_progress",
          settings = settings
        )
      ))
    }

    "decode backend error responses" in {
      val backend = org.http4s.HttpRoutes.of[IO] {
        case GET -> Root / "games" / _ =>
          NotFound(ErrorResponse("Game not found", Some("unknown game id")).asJson)
      }.orNotFound

      val client = ChessApiClient.http(baseUri, Client.fromHttpApp(backend))

      val result = client.getGameState("missing").unsafeRunSync()

      result.shouldBe(
        Left(
        ChessApiError(
          message = "Game not found",
          statusCode = Some(Status.NotFound.code),
          details = Some("unknown game id")
        )
      ))
    }
  }
