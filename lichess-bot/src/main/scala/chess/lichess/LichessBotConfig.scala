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
    maxThinkMs: Long = 5000L,
    /** Lowercase Lichess usernames to open a seek against via `POST /api/challenge/{username}`. */
    challengeUsernames: List[String] = Nil,
    /** Whether outgoing [[challengeUsernames]] games are rated. */
    challengeRated: Boolean = false,
    /** Clock initial time in seconds for outgoing challenges. */
    challengeClockLimitSec: Int = 300,
    /** Clock increment in seconds for outgoing challenges. */
    challengeClockIncrementSec: Int = 0,
    /** If set, re-send outgoing challenges to all [[challengeUsernames]] on this interval (minutes). */
    challengeEveryMinutes: Option[Int] = None
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
        val challengeUsernames =
          Option(System.getenv("LICHESS_CHALLENGE_USERNAMES"))
            .toList
            .flatMap(_.split(',').toList)
            .map(_.trim.toLowerCase)
            .filter(_.nonEmpty)
            .filter(_.forall(c => c.isLetterOrDigit || c == '_' || c == '-'))
        val challengeEveryMinutes =
          Option(System.getenv("LICHESS_CHALLENGE_EVERY_MINUTES")).flatMap(_.trim.toIntOption).filter(_ > 0)
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
            maxThinkMs = Option(System.getenv("LICHESS_MAX_THINK_MS")).flatMap(_.toLongOption).getOrElse(5000L),
            challengeUsernames = challengeUsernames,
            challengeRated = Option(System.getenv("LICHESS_CHALLENGE_RATED")).exists(_.trim == "1"),
            challengeClockLimitSec =
              Option(System.getenv("LICHESS_CHALLENGE_CLOCK_SEC")).flatMap(_.trim.toIntOption).getOrElse(300).max(1),
            challengeClockIncrementSec =
              Option(System.getenv("LICHESS_CHALLENGE_INC_SEC")).flatMap(_.trim.toIntOption).getOrElse(0).max(0),
            challengeEveryMinutes = challengeEveryMinutes
          )
        )

end LichessBotConfig
