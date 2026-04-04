package chess.controller.lichess

import chess.controller.lichess.LichessModels.BotConfig

import scala.io.Source
import scala.util.{Try, Using}

/** Loads bot configuration from file or environment */
object BotConfigLoader:

  /** Load configuration from a file or environment variables
    * @param configFile
    *   Optional path to configuration file
    * @return
    *   BotConfig instance
    */
  def load(configFile: Option[String] = None): Try[BotConfig] =
    Try {
      val token = configFile match
        case Some(path) =>
          loadFromFile(path)
        case None =>
          loadFromEnv()

      BotConfig(
        apiToken = token,
        baseUrl = sys.env.getOrElse("LICHESS_BASE_URL", "https://lichess.org"),
        acceptRated = sys.env.get("LICHESS_ACCEPT_RATED").exists(_.toBoolean),
        acceptCasual = sys.env.getOrElse("LICHESS_ACCEPT_CASUAL", "true").toBoolean,
        acceptVariants = sys.env
          .getOrElse("LICHESS_ACCEPT_VARIANTS", "standard")
          .split(",")
          .map(_.trim)
          .toSet,
        minTimeControl = sys.env.getOrElse("LICHESS_MIN_TIME", "60").toInt,
        maxTimeControl = sys.env.getOrElse("LICHESS_MAX_TIME", "3600").toInt,
        maxConcurrentGames = sys.env.getOrElse("LICHESS_MAX_GAMES", "1").toInt
      )
    }

  private def loadFromFile(path: String): String =
    Using(Source.fromFile(path)) { source =>
      source.getLines().find(_.trim.nonEmpty).getOrElse {
        throw new IllegalArgumentException(s"Config file $path is empty")
      }
    }.get

  private def loadFromEnv(): String =
    sys.env.getOrElse(
      "LICHESS_API_TOKEN",
      throw new IllegalArgumentException(
        "LICHESS_API_TOKEN environment variable not set and no config file provided"
      )
    )

  /** Create a sample configuration file
    * @param path
    *   Path where to create the config file
    */
  def createSampleConfig(path: String): Try[Unit] =
    Try {
      val content = """# Lichess Bot API Token
                      |# Get your token from https://lichess.org/account/oauth/token
                      |your_api_token_here
                      |""".stripMargin

      val writer = new java.io.PrintWriter(path)
      try writer.write(content)
      finally writer.close()
    }
