package chess.lichess

/** Configuration from environment variables (see `lichess-bot/README.md`). */
final case class LichessBotConfig(
    token: String,
    baseUri: String = "https://lichess.org",
    strategyId: String = "iterative-deepening",
    /** POST /api/challenge/{id}/accept for incoming challenges when true. */
    autoAcceptChallenges: Boolean = true,
    /** If true, only auto-accept when the challenger account is marked as a Lichess bot. */
    onlyBotChallengers: Boolean = false,
    userAgent: String = "ChessBot/0.1 (https://github.com/markoboger/chess; lichess-bot)",
    /** Floor for [[BotStrategy]] search budget (ms). */
    minThinkMs: Long = 100L,
    /** Cap for search budget (ms). */
    maxThinkMs: Long = 5000L
)

object LichessBotConfig:

  def load: Either[String, LichessBotConfig] =
    Option(System.getenv("LICHESS_TOKEN")).map(_.trim).filter(_.nonEmpty) match
      case None => Left("Missing LICHESS_TOKEN (personal API token with bot play scope).")
      case Some(token) =>
        val autoAccept =
          Option(System.getenv("LICHESS_AUTO_ACCEPT")) match
            case None        => true
            case Some(value) => !"0".equalsIgnoreCase(value.trim)
        Right(
          LichessBotConfig(
            token = token,
            baseUri =
              Option(System.getenv("LICHESS_API_BASE")).map(_.trim).filter(_.nonEmpty).getOrElse("https://lichess.org"),
            strategyId =
              Option(System.getenv("LICHESS_STRATEGY")).map(_.trim).filter(_.nonEmpty).getOrElse("iterative-deepening"),
            autoAcceptChallenges = autoAccept,
            onlyBotChallengers = Option(System.getenv("LICHESS_ONLY_BOT_CHALLENGERS")).exists(_.trim == "1"),
            userAgent =
              Option(System.getenv("LICHESS_USER_AGENT")).map(_.trim).filter(_.nonEmpty).getOrElse(
                "ChessBot/0.1 (https://github.com/markoboger/chess; lichess-bot)"
              ),
            minThinkMs = Option(System.getenv("LICHESS_MIN_THINK_MS")).flatMap(_.toLongOption).getOrElse(100L),
            maxThinkMs = Option(System.getenv("LICHESS_MAX_THINK_MS")).flatMap(_.toLongOption).getOrElse(5000L)
          )
        )

end LichessBotConfig
