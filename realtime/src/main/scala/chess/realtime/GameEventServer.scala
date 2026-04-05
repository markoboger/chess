package chess.realtime

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder

object GameEventServer extends IOApp.Simple:

  private val port = sys.env.getOrElse("PORT", "8083")

  def run: IO[Unit] =
    InMemoryGameEventHub.create.flatMap { hub =>
      val routes = GameEventRoutes.routes(hub)

      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromString(port).getOrElse(port"8083"))
        .withHttpApp(routes.orNotFound)
        .build
        .use { server =>
          IO.println(s"Realtime Service started at ${server.address}") *>
            IO.println("Available endpoints:") *>
            IO.println("  GET /health") *>
            IO.println("  GET /events/:gameId") *>
            IO.never
        }
    }

