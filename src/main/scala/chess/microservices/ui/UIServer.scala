package chess.microservices.ui

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.`Content-Type`
import fs2.io.file.{Files, Path}
import chess.microservices.shared.HealthResponse
import org.http4s.circe.CirceEntityCodec.given

/** UI Service microservice
  *
  * Serves static frontend assets (HTML, CSS, JavaScript) for the chess application.
  *
  * Runs on port 8082 by default.
  */
object UIServer extends IOApp.Simple:

  private val port = sys.env.getOrElse("PORT", "8082").toInt
  private val staticRoot = Path("frontend/dist")
  private def fileNotFound(fileName: String) = s"File not found: $fileName"

  def routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // Health check
    case GET -> Root / "health" =>
      Ok(HealthResponse("ok", "ui-service"))

    // Serve index.html at root
    case GET -> Root =>
      serveFile("index.html")

    // Serve static files
    case GET -> path =>
      val filePath = path.toString.stripPrefix("/")
      serveFile(filePath)
  }

  private def serveFile(fileName: String): IO[Response[IO]] =
    val file = staticRoot / fileName
    Files[IO].exists(file).flatMap {
      case true =>
        val mediaType = fileName match
          case f if f.endsWith(".html") => MediaType.text.html
          case f if f.endsWith(".css")  => MediaType.text.css
          case f if f.endsWith(".js")   => MediaType.text.javascript
          case f if f.endsWith(".json") => MediaType.application.json
          case _                        => MediaType.application.`octet-stream`

        val body = Files[IO].readAll(file)
        Ok(body).map(_.withContentType(`Content-Type`(mediaType)))

      case false =>
        NotFound(fileNotFound(fileName))
    }

  def run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(port).getOrElse(port"8082"))
      .withHttpApp(routes.orNotFound)
      .build
      .use { server =>
        IO.println(s"UI Service started at ${server.address}") *>
          IO.println("") *>
          IO.println("Serving static files from: frontend/dist/") *>
          IO.println("") *>
          IO.println("Press Ctrl+C to stop the server") *>
          IO.never
      }
