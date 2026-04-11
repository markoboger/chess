package chess.matchrunner

final case class MatchRunnerConfig(
    port: Int,
    chessApiUrl: String,
    pollIntervalMs: Long,
    matchTimeoutMs: Long,
    postgresHost: String,
    postgresPort: Int,
    postgresDatabase: String,
    postgresUser: String,
    postgresPassword: String
):
  def postgresJdbcUrl: String =
    s"jdbc:postgresql://$postgresHost:$postgresPort/$postgresDatabase"

object MatchRunnerConfig:
  def load(env: Map[String, String] = sys.env): MatchRunnerConfig =
    MatchRunnerConfig(
      port = env.get("PORT").flatMap(_.toIntOption).getOrElse(8084),
      chessApiUrl = env.getOrElse("CHESS_API_URL", "http://localhost:8081"),
      pollIntervalMs = env.get("MATCH_RUNNER_POLL_INTERVAL_MS").flatMap(_.toLongOption).getOrElse(100L),
      matchTimeoutMs = env.get("MATCH_RUNNER_TIMEOUT_MS").flatMap(_.toLongOption).getOrElse(15000L),
      postgresHost = env.getOrElse("POSTGRES_HOST", "localhost"),
      postgresPort = env.get("POSTGRES_PORT").flatMap(_.toIntOption).getOrElse(5432),
      postgresDatabase = env.getOrElse("POSTGRES_DATABASE", "chess"),
      postgresUser = env.getOrElse("POSTGRES_USER", "chess"),
      postgresPassword = env.getOrElse("POSTGRES_PASSWORD", "")
    )
