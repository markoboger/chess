package chess.microservices.game

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.AppBindings.given
import chess.application.game.GameSessionService
import chess.microservices.shared.{CreateGameRequest, CreateGameResponse, GameStateResponse}
import chess.model.GameSettings
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.implicits.uri
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PassiveCvcFeasibilitySpec extends AnyWordSpec with Matchers:

  private val app = GameRoutes.routes(new GameSessionService()).orNotFound

  private def run(req: Request[IO]) =
    app.run(req).unsafeRunSync()

  "The HTTP game service" should {
    "autonomously advance a CvC game when backendAutoplay is enabled" in {
      val createReq = Request[IO](Method.POST, uri"/games")
        .withEntity(
          CreateGameRequest(
            settings = GameSettings(
              whiteIsHuman = false,
              blackIsHuman = false,
              whiteStrategy = "random",
              blackStrategy = "random",
              backendAutoplay = true
            )
          )
        )

      val createResp = run(createReq)
      createResp.status shouldBe Status.Ok
      val created = createResp.as[CreateGameResponse].unsafeRunSync()

      val initialState = run(Request[IO](Method.GET, uri"/games" / created.gameId))
        .as[GameStateResponse]
        .unsafeRunSync()

      Thread.sleep(300L)

      val laterState = run(Request[IO](Method.GET, uri"/games" / created.gameId))
        .as[GameStateResponse]
        .unsafeRunSync()

      laterState.pgn should not be empty
      laterState.pgn should not be initialState.pgn
      laterState.fen should not be initialState.fen
    }
  }
