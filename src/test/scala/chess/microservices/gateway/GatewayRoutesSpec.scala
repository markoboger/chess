package chess.microservices.gateway

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import chess.microservices.shared.HealthResponse
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GatewayRoutesSpec extends AnyWordSpec with Matchers {

  private def makeApp() = {
    val recordedUri = Ref.unsafe[IO, Option[Uri]](None)
    val httpApp: HttpApp[IO] = HttpApp { (req: Request[IO]) =>
      recordedUri.set(Some(req.uri)).as(Response[IO](Status.Ok))
    }
    val client =
      Client.fromHttpApp(httpApp)

    (recordedUri, GatewayRoutes.routes(client).orNotFound)
  }

  "GET /health" should {
    "return 200 with gateway service name" in {
      val (_, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/health")).unsafeRunSync()
      resp.status shouldBe Status.Ok
      val body = resp.as[HealthResponse].unsafeRunSync()
      body.status  shouldBe "ok"
      body.service shouldBe "api-gateway"
    }
  }

  "proxy routes" should {
    "forward POST /api/games to game service" in {
      val (_, app) = makeApp()
      val resp = app.run(Request[IO](Method.POST, uri"/api/games")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward GET /api/games/some-id to game service" in {
      val (_, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/api/games/some-id")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward DELETE /api/games/some-id to game service" in {
      val (_, app) = makeApp()
      val resp = app.run(Request[IO](Method.DELETE, uri"/api/games/some-id")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward GET / to UI service" in {
      val (_, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward GET /some/path to UI service" in {
      val (_, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/some/path")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward GET /api/games with query parameters to the game service" in {
      val (recordedUri, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/api/games?limit=20&offset=5")).unsafeRunSync()

      resp.status shouldBe Status.Ok
      recordedUri.get.unsafeRunSync().map(_.renderString) shouldBe
        Some(s"${GatewayConfig.gameServiceUrl.renderString}/games?limit=20&offset=5")
    }

    "forward PATCH /api/games/:id to the game service" in {
      val (recordedUri, app) = makeApp()
      val resp = app.run(Request[IO](Method.PATCH, uri"/api/games/game-1")).unsafeRunSync()

      resp.status shouldBe Status.Ok
      recordedUri.get.unsafeRunSync().map(_.renderString) shouldBe
        Some(s"${GatewayConfig.gameServiceUrl.renderString}/games/game-1")
    }

    "forward GET /api/openings/lookup with query parameters to the game service" in {
      val (recordedUri, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/api/openings/lookup?fen=abc")).unsafeRunSync()

      resp.status shouldBe Status.Ok
      recordedUri.get.unsafeRunSync().map(_.renderString) shouldBe
        Some(s"${GatewayConfig.gameServiceUrl.renderString}/openings/lookup?fen=abc")
    }

    "forward GET /some/path with query parameters to the UI service" in {
      val (recordedUri, app) = makeApp()
      val resp = app.run(Request[IO](Method.GET, uri"/some/path?view=compact")).unsafeRunSync()

      resp.status shouldBe Status.Ok
      recordedUri.get.unsafeRunSync().map(_.renderString) shouldBe
        Some(s"${GatewayConfig.uiServiceUrl.renderString}/some/path?view=compact")
    }
  }
}
