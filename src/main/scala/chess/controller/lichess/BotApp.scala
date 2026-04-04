package chess.controller.lichess

import cats.effect.{IO, IOApp, ExitCode}
import org.http4s.ember.client.EmberClientBuilder
import chess.controller.strategy.MinimaxStrategy

import scala.util.{Success, Failure}

/** Main entry point for the Lichess bot application */
object BotApp extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    val configFile = args.headOption

    // Load configuration
    BotConfigLoader.load(configFile) match
      case Success(config) =>
        println(s"Starting Lichess bot...")
        println(s"Base URL: ${config.baseUrl}")
        println(s"Accept rated: ${config.acceptRated}")
        println(s"Accept casual: ${config.acceptCasual}")
        println(s"Accept variants: ${config.acceptVariants.mkString(", ")}")
        println(s"Max concurrent games: ${config.maxConcurrentGames}")
        println()

        // Create HTTP client and bot service
        EmberClientBuilder
          .default[IO]
          .build
          .use { httpClient =>
            val lichessClient = LichessClient[IO](httpClient, config)

            // Verify bot account
            for
              profile <- lichessClient.getAccount
              _ <- IO.println(s"Logged in as: ${profile.username} (${profile.id})")
              _ <-
                if profile.title.contains("BOT") then IO.unit
                else
                  IO.println("WARNING: Account is not upgraded to BOT account!") *>
                    IO.println("Visit https://lichess.org/api#tag/Bot/operation/botAccountUpgrade")

              // Create bot service with default strategy
              strategy = new MinimaxStrategy(depth = 3)
              botService <- BotService[IO](lichessClient, config, strategy)

              // Start the bot
              _ <- IO.println("Bot is running. Press Ctrl+C to stop.")
              _ <- botService.start

              // Keep running until interrupted
              _ <- IO.never[Unit]
            yield ExitCode.Success
          }
          .handleErrorWith { error =>
            IO.println(s"Error: ${error.getMessage}") *>
              IO.println(s"Stack trace: ${error.getStackTrace.mkString("\n")}") *>
              IO.pure(ExitCode.Error)
          }

      case Failure(error) =>
        IO.println(s"Failed to load configuration: ${error.getMessage}") *>
          IO.println("") *>
          IO.println("Usage: sbt \"runMain chess.controller.lichess.BotApp [config-file]\"") *>
          IO.println("") *>
          IO.println("Configuration options:") *>
          IO.println("  1. Provide config file path as argument") *>
          IO.println("  2. Set LICHESS_API_TOKEN environment variable") *>
          IO.println("") *>
          IO.println("Additional environment variables:") *>
          IO.println("  LICHESS_BASE_URL          - Default: https://lichess.org") *>
          IO.println("  LICHESS_ACCEPT_RATED      - Default: false") *>
          IO.println("  LICHESS_ACCEPT_CASUAL     - Default: true") *>
          IO.println("  LICHESS_ACCEPT_VARIANTS   - Default: standard") *>
          IO.println("  LICHESS_MIN_TIME          - Default: 60") *>
          IO.println("  LICHESS_MAX_TIME          - Default: 3600") *>
          IO.println("  LICHESS_MAX_GAMES         - Default: 1") *>
          IO.pure(ExitCode.Error)
