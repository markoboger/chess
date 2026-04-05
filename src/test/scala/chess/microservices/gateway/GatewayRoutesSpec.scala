package chess.microservices.gateway

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.microservices.shared.HealthResponse
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GatewayRoutesSpec extends AnyWordSpec with Matchers {

  private val mockClient: Client[IO] =
    Client.fromHttpApp(HttpApp.liftF(IO.pure(Response[IO](Status.Ok))))

  private val app = GatewayRoutes.routes(mockClient).orNotFound

  "GET /health" should {
    "return 200 with gateway service name" in {
      val resp = app.run(Request[IO](Method.GET, uri"/health")).unsafeRunSync()
      resp.status shouldBe Status.Ok
      val body = resp.as[HealthResponse].unsafeRunSync()
      body.status  shouldBe "ok"
      body.service shouldBe "api-gateway"
    }
  }

  "proxy routes" should {
    "forward POST /api/games to game service" in {
      val resp = app.run(Request[IO](Method.POST, uri"/api/games")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward GET /api/games/some-id to game service" in {
      val resp = app.run(Request[IO](Method.GET, uri"/api/games/some-id")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward DELETE /api/games/some-id to game service" in {
      val resp = app.run(Request[IO](Method.DELETE, uri"/api/games/some-id")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward GET / to UI service" in {
      val resp = app.run(Request[IO](Method.GET, uri"/")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }

    "forward GET /some/path to UI service" in {
      val resp = app.run(Request[IO](Method.GET, uri"/some/path")).unsafeRunSync()
      resp.status shouldBe Status.Ok
    }
  }
}
