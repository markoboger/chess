package chess.microservices.ui

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.microservices.shared.HealthResponse
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UIServerSpec extends AnyWordSpec with Matchers {

  private val app = UIServer.routes.orNotFound

  "GET /health" should {
    "return 200 with ui-service name" in {
      val resp = app.run(Request[IO](Method.GET, uri"/health")).unsafeRunSync()
      resp.status shouldBe Status.Ok
      val body = resp.as[HealthResponse].unsafeRunSync()
      body.status shouldBe "ok"
      body.service shouldBe "ui-service"
    }
  }

  "GET /" should {
    "respond (200 if file exists, 404 otherwise)" in {
      val resp = app.run(Request[IO](Method.GET, uri"/")).unsafeRunSync()
      List(Status.Ok, Status.NotFound) should contain(resp.status)
    }
  }

  "GET /nonexistent-file-xyz.html" should {
    "return 404" in {
      val resp = app.run(Request[IO](Method.GET, uri"/nonexistent-file-xyz.html")).unsafeRunSync()
      resp.status shouldBe Status.NotFound
    }
  }

  "GET /some/file.js" should {
    "return 404 for a non-existent JS file" in {
      val resp = app.run(Request[IO](Method.GET, uri"/some/file.js")).unsafeRunSync()
      resp.status shouldBe Status.NotFound
    }
  }
}
