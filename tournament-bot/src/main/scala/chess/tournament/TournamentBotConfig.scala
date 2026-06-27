package chess.tournament

/** Configuration from environment variables (see `tournament-bot/README.md`). */
final case class TournamentBotConfig(
    token: String,
    botId: String,
    baseUri: String = "http://localhost:8086",
    strategyId: String = "deepening-opening-endgame",
    /** Join this tournament on startup when set. */
    tournamentId: Option[String] = None,
    /** When true, poll `GET /api/tournament` for `created` tournaments and join the first one. */
    autoJoinCreated: Boolean = false,
    userAgent: String = "ChessBot/0.1 (https://github.com/markoboger/chess; tournament-bot)",
    minThinkMs: Long = 100L,
    maxThinkMs: Long = 5000L
)

object TournamentBotConfig:

  def load: Either[String, TournamentBotConfig] =
    val tokenOpt = Option(System.getenv("TOURNAMENT_TOKEN")).map(_.trim).filter(_.nonEmpty)
    val nameOpt    = Option(System.getenv("TOURNAMENT_BOT_NAME")).map(_.trim).filter(_.nonEmpty)

    (tokenOpt, nameOpt) match
      case (None, None) =>
        Left("Set TOURNAMENT_TOKEN or TOURNAMENT_BOT_NAME (register on startup).")
      case _ =>
        val token = tokenOpt.getOrElse("")
        val envBotId =
          Option(System.getenv("TOURNAMENT_BOT_ID")).map(_.trim).filter(_.nonEmpty)
        val botId = envBotId.orElse(if token.nonEmpty then JwtSub.subject(token) else None).getOrElse("")
        Right(
          TournamentBotConfig(
            token = token,
            botId = botId,
            baseUri =
              Option(System.getenv("TOURNAMENT_API_URL"))
                .map(_.trim)
                .filter(_.nonEmpty)
                .getOrElse("http://localhost:8086"),
            strategyId =
              Option(System.getenv("TOURNAMENT_STRATEGY"))
                .map(_.trim)
                .filter(_.nonEmpty)
                .getOrElse("deepening-opening-endgame"),
            tournamentId =
              Option(System.getenv("TOURNAMENT_ID")).map(_.trim).filter(_.nonEmpty),
            autoJoinCreated = Option(System.getenv("TOURNAMENT_AUTO_JOIN")).exists(v =>
              "1".equalsIgnoreCase(v.trim) || "true".equalsIgnoreCase(v.trim)
            ),
            userAgent =
              Option(System.getenv("TOURNAMENT_USER_AGENT"))
                .map(_.trim)
                .filter(_.nonEmpty)
                .getOrElse("ChessBot/0.1 (https://github.com/markoboger/chess; tournament-bot)"),
            minThinkMs = Option(System.getenv("TOURNAMENT_MIN_THINK_MS")).flatMap(_.toLongOption).getOrElse(100L),
            maxThinkMs = Option(System.getenv("TOURNAMENT_MAX_THINK_MS")).flatMap(_.toLongOption).getOrElse(5000L)
          )
        )

end TournamentBotConfig
