package chess.matchrunner

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class MatchRunnerConfigSpec extends AnyWordSpec with Matchers:

  "MatchRunnerConfig.load" should {
    "use defaults when env is empty" in {
      val cfg = MatchRunnerConfig.load(Map.empty)
      cfg.port shouldBe 8084
      cfg.chessApiUrl shouldBe "http://localhost:8081"
      cfg.pollIntervalMs shouldBe 100L
      cfg.matchTimeoutMs shouldBe 300_000L
      cfg.postgresHost shouldBe "localhost"
      cfg.postgresPort shouldBe 5432
      cfg.postgresDatabase shouldBe "chess"
      cfg.postgresUser shouldBe "chess"
      cfg.postgresPassword shouldBe ""
      cfg.postgresJdbcUrl shouldBe "jdbc:postgresql://localhost:5432/chess"
    }

    "parse numeric fields when valid and fall back when invalid" in {
      val env = Map(
        "PORT" -> "9999",
        "MATCH_RUNNER_POLL_INTERVAL_MS" -> "250",
        "MATCH_RUNNER_TIMEOUT_MS" -> "1234",
        "POSTGRES_PORT" -> "not-a-number",
        "POSTGRES_PASSWORD" -> "secret",
        "CHESS_API_URL" -> "http://example:8081",
        "POSTGRES_HOST" -> "pg",
        "POSTGRES_DATABASE" -> "db",
        "POSTGRES_USER" -> "u"
      )
      val cfg = MatchRunnerConfig.load(env)
      cfg.port shouldBe 9999
      cfg.pollIntervalMs shouldBe 250L
      cfg.matchTimeoutMs shouldBe 1234L
      cfg.postgresPort shouldBe 5432 // invalid env falls back
      cfg.postgresPassword shouldBe "secret"
      cfg.chessApiUrl shouldBe "http://example:8081"
      cfg.postgresJdbcUrl shouldBe "jdbc:postgresql://pg:5432/db"
    }
  }

