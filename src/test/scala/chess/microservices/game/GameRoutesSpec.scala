package chess.microservices.game

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.application.game.GameSessionService
import chess.AppBindings.given
import chess.microservices.shared.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GameRoutesSpec extends AnyWordSpec with Matchers {

  private val app = GameRoutes.routes(new GameSessionService()).orNotFound

  private def run(req: Request[IO]): Response[IO] = app.run(req).unsafeRunSync()

  "GET /health" should {
    "return 200 with service name" in {
      val resp = run(Request[IO](Method.GET, uri"/health"))
      resp.status shouldBe Status.Ok
      val body = resp.as[HealthResponse].unsafeRunSync()
      body.status  shouldBe "ok"
      body.service shouldBe "game-service"
    }
  }

  "POST /games" should {
    "create a new default game and return 200" in {
      val req = Request[IO](Method.POST, uri"/games")
        .withEntity(CreateGameRequest(None))
      val resp = run(req)
      resp.status shouldBe Status.Ok
      val body = resp.as[CreateGameResponse].unsafeRunSync()
      body.gameId should not be empty
      body.fen    should include("rnbqkbnr")
    }

    "create a game from a custom FEN" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val req = Request[IO](Method.POST, uri"/games")
        .withEntity(CreateGameRequest(Some(fen)))
      val resp = run(req)
      resp.status shouldBe Status.Ok
    }

    "return 400 for an invalid FEN" in {
      val req = Request[IO](Method.POST, uri"/games")
        .withEntity(CreateGameRequest(Some("not-a-fen")))
      run(req).status shouldBe Status.BadRequest
    }
  }

  "GET /games/:id" should {
    "return game state for a valid game" in {
      val createResp = run(
        Request[IO](Method.POST, uri"/games").withEntity(CreateGameRequest(None))
      ).as[CreateGameResponse].unsafeRunSync()

      val resp = run(Request[IO](Method.GET, Uri.unsafeFromString(s"/games/${createResp.gameId}")))
      resp.status shouldBe Status.Ok
      val state = resp.as[GameStateResponse].unsafeRunSync()
      state.gameId shouldBe createResp.gameId
    }

    "return 404 for unknown game" in {
      run(Request[IO](Method.GET, uri"/games/no-such-id")).status shouldBe Status.NotFound
    }
  }

  "DELETE /games/:id" should {
    "delete a game and return 204" in {
      val createResp = run(
        Request[IO](Method.POST, uri"/games").withEntity(CreateGameRequest(None))
      ).as[CreateGameResponse].unsafeRunSync()

      run(Request[IO](Method.DELETE, Uri.unsafeFromString(s"/games/${createResp.gameId}")))
        .status shouldBe Status.NoContent
    }

    "return 404 for unknown game" in {
      run(Request[IO](Method.DELETE, uri"/games/no-such-id")).status shouldBe Status.NotFound
    }
  }

  "POST /games/:id/moves" should {
    "apply a valid move and return 200" in {
      val createResp = run(
        Request[IO](Method.POST, uri"/games").withEntity(CreateGameRequest(None))
      ).as[CreateGameResponse].unsafeRunSync()

      val moveReq = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/${createResp.gameId}/moves"))
        .withEntity(MakeMoveRequest("e4"))
      val resp = run(moveReq)
      resp.status shouldBe Status.Ok
      val body = resp.as[MakeMoveResponse].unsafeRunSync()
      body.success shouldBe true
    }

    "return 400 for an illegal move" in {
      val createResp = run(
        Request[IO](Method.POST, uri"/games").withEntity(CreateGameRequest(None))
      ).as[CreateGameResponse].unsafeRunSync()

      val moveReq = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/${createResp.gameId}/moves"))
        .withEntity(MakeMoveRequest("e9"))
      run(moveReq).status shouldBe Status.BadRequest
    }

    "return 400 for unknown game" in {
      val moveReq = Request[IO](Method.POST, uri"/games/no-such-id/moves")
        .withEntity(MakeMoveRequest("e4"))
      run(moveReq).status shouldBe Status.BadRequest
    }
  }

  "GET /games/:id/moves" should {
    "return move history" in {
      val createResp = run(
        Request[IO](Method.POST, uri"/games").withEntity(CreateGameRequest(None))
      ).as[CreateGameResponse].unsafeRunSync()

      val resp = run(Request[IO](Method.GET, Uri.unsafeFromString(s"/games/${createResp.gameId}/moves")))
      resp.status shouldBe Status.Ok
    }

    "return 404 for unknown game" in {
      run(Request[IO](Method.GET, uri"/games/no-such-id/moves")).status shouldBe Status.NotFound
    }
  }

  "GET /games/:id/fen" should {
    "return FEN for existing game" in {
      val createResp = run(
        Request[IO](Method.POST, uri"/games").withEntity(CreateGameRequest(None))
      ).as[CreateGameResponse].unsafeRunSync()

      val resp = run(Request[IO](Method.GET, Uri.unsafeFromString(s"/games/${createResp.gameId}/fen")))
      resp.status shouldBe Status.Ok
      val body = resp.as[FenResponse].unsafeRunSync()
      body.fen should not be empty
    }

    "return 404 for unknown game" in {
      run(Request[IO](Method.GET, uri"/games/no-such-id/fen")).status shouldBe Status.NotFound
    }
  }

  "POST /games/:id/fen" should {
    "load a valid FEN and return 200" in {
      val createResp = run(
        Request[IO](Method.POST, uri"/games").withEntity(CreateGameRequest(None))
      ).as[CreateGameResponse].unsafeRunSync()

      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val req = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/${createResp.gameId}/fen"))
        .withEntity(LoadFenRequest(fen))
      val resp = run(req)
      resp.status shouldBe Status.Ok
      resp.as[LoadFenResponse].unsafeRunSync().success shouldBe true
    }

    "return 400 for invalid FEN" in {
      val createResp = run(
        Request[IO](Method.POST, uri"/games").withEntity(CreateGameRequest(None))
      ).as[CreateGameResponse].unsafeRunSync()

      val req = Request[IO](Method.POST, Uri.unsafeFromString(s"/games/${createResp.gameId}/fen"))
        .withEntity(LoadFenRequest("garbage"))
      run(req).status shouldBe Status.BadRequest
    }

    "return 400 for unknown game" in {
      val req = Request[IO](Method.POST, uri"/games/no-such-id/fen")
        .withEntity(LoadFenRequest("anything"))
      run(req).status shouldBe Status.BadRequest
    }
  }
}
