package chess.microservices.gateway

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ServiceProxySpec extends AnyWordSpec with Matchers {

  private val mockClient: Client[IO] =
    Client.fromHttpApp(HttpApp.liftF(IO.pure(Response[IO](Status.Ok))))

  private val proxy = ServiceProxy(mockClient)

  "buildTargetUri" should {
    "build a URI from base and path" in {
      val base = uri"http://localhost:8081"
      val result = proxy.buildTargetUri(base, "/games")
      result.renderString should include("localhost:8081")
      result.renderString should include("/games")
    }

    "append a query string when provided" in {
      val base = uri"http://localhost:8081"
      val result = proxy.buildTargetUri(base, "/games", Some("limit=10"))
      result.renderString should include("limit=10")
    }

    "omit query string when None" in {
      val base = uri"http://localhost:8081"
      val result = proxy.buildTargetUri(base, "/games", None)
      result.renderString should not include "?"
    }

    "work with nested paths" in {
      val base = uri"http://game-service:8081"
      val result = proxy.buildTargetUri(base, "/games/abc-123/moves")
      result.renderString should include("/games/abc-123/moves")
    }
  }

  "proxy" should {
    "forward a request and return the backend response" in {
      val request = Request[IO](Method.GET, uri"http://localhost:8081/health")
      val targetUri = uri"http://localhost:8081/health"
      val response = proxy.proxy(request, targetUri).unsafeRunSync()
      response.status shouldBe Status.Ok
    }
  }
}
