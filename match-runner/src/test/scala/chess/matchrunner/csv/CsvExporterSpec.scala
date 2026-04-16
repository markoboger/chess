package chess.matchrunner.csv

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.matchrunner.application.ExperimentSummary
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchResult, MatchRun}
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import scala.io.Source

final class CsvExporterSpec extends AnyWordSpec with Matchers with OptionValues:

  "CsvExporter.suggestPath" should {
    "sanitize and truncate filenames" in {
      val base = "  My experiment: Q\"uote, / weird ✨ name  "
      val path = CsvExporter.suggestPath(base, "runs")
      path.endsWith("-runs.csv") shouldBe true
      path.contains(" ") shouldBe false
      path.contains("/") shouldBe false
      path.length should be <= ("runs".length + 1 + 60 + "-.csv".length + 10) // loose upper bound
    }
  }

  "CsvExporter.writeRuns" should {
    "write a UTF-8 BOM, header, and escaped values" in {
      val expId = UUID.randomUUID()
      val started = Instant.parse("2026-04-16T10:00:00Z")
      val finished = Instant.parse("2026-04-16T10:00:01Z")
      val run = MatchRun(
        id = UUID.randomUUID(),
        experimentId = expId,
        chessGameId = "game-\"1\"",
        whiteStrategy = "minimax,3",
        blackStrategy = "random",
        startedAt = started,
        finishedAt = Some(finished),
        result = Some(MatchResult.WhiteWin),
        winner = Some("white"),
        moveCount = Some(7),
        finalFen = None,
        pgn = None,
        errorMessage = Some("oops, \"bad\""),
        durationMs = Some(1234L)
      )

      val tmp = Files.createTempFile("chess-runs-", ".csv")
      try
        CsvExporter.writeRuns("exp, \"name\"", List(run), tmp.toString).unsafeRunSync()

        val txt = Source.fromFile(tmp.toFile, "UTF-8").mkString
        txt.startsWith("\uFEFF") shouldBe true
        txt should include("experiment,game_id,white_strategy,black_strategy,result,winner,move_count")
        // Quotes should be doubled inside quoted CSV values
        txt should include("\"game-\"\"1\"\"\"")
        txt should include("\"exp, \"\"name\"\"\"")
        txt should include("\"minimax,3\"")
        txt should include("\"oops, \"\"bad\"\"\"")
      finally
        Files.deleteIfExists(tmp)
    }
  }

  "CsvExporter.writeSummary" should {
    "write one row per direction" in {
      val experiment = Experiment.create("exp", None, requestedGames = 2, status = ExperimentStatus.Completed)
      val now = Instant.parse("2026-04-16T10:00:00Z")
      val runs = List(
        MatchRun.create(experiment.id, "g1", "a", "b").copy(
          finishedAt = Some(now),
          result = Some(MatchResult.WhiteWin),
          winner = Some("white"),
          moveCount = Some(10),
          durationMs = Some(1000L)
        ),
        MatchRun.create(experiment.id, "g2", "b", "a").copy(
          finishedAt = Some(now),
          result = Some(MatchResult.Draw),
          moveCount = Some(20),
          durationMs = Some(2000L)
        )
      )
      val summary = ExperimentSummary.fromRuns(experiment, runs)

      val tmp = Files.createTempFile("chess-summary-", ".csv")
      try
        CsvExporter.writeSummary(summary, tmp.toString).unsafeRunSync()
        val lines = Source.fromFile(tmp.toFile, "UTF-8").getLines().toList
        // First line contains BOM + header; then 1 row per direction
        lines.length shouldBe 1 + 2
        lines.head.startsWith("\uFEFF") shouldBe true
        lines.head should include("experiment,white_strategy,black_strategy,games")
      finally
        Files.deleteIfExists(tmp)
    }
  }

  "CsvExporter.writeLeaderboard" should {
    "write rows sorted by points (wins*2 + draws)" in {
      val exp = Experiment.create("exp", None, requestedGames = 2, status = ExperimentStatus.Completed)
      val now = Instant.parse("2026-04-16T10:00:00Z")

      def summaryFor(focus: String, opp: String, focusWins: Int, draws: Int, oppWins: Int): ExperimentSummary = {
        val runs =
          List.fill(focusWins)(MatchRun.create(exp.id, UUID.randomUUID().toString, focus, opp).copy(
            finishedAt = Some(now),
            result = Some(MatchResult.WhiteWin),
            winner = Some("white"),
            moveCount = Some(10),
            durationMs = Some(1000L)
          )) ++
            List.fill(draws)(MatchRun.create(exp.id, UUID.randomUUID().toString, focus, opp).copy(
              finishedAt = Some(now),
              result = Some(MatchResult.Draw),
              moveCount = Some(10),
              durationMs = Some(1000L)
            )) ++
            List.fill(oppWins)(MatchRun.create(exp.id, UUID.randomUUID().toString, focus, opp).copy(
              finishedAt = Some(now),
              result = Some(MatchResult.BlackWin),
              winner = Some("black"),
              moveCount = Some(10),
              durationMs = Some(1000L)
            ))

        ExperimentSummary.fromRuns(exp, runs)
      }

      val focus = "minimax"
      val results = List(
        // points: 1 win (2 pts) + 0 draws = 2
        ("random", summaryFor(focus, "random", focusWins = 1, draws = 0, oppWins = 0)),
        // points: 0 wins + 2 draws = 2 (tie)
        ("greedy", summaryFor(focus, "greedy", focusWins = 0, draws = 2, oppWins = 0)),
        // points: 0
        ("material", summaryFor(focus, "material", focusWins = 0, draws = 0, oppWins = 1))
      )

      val tmp = Files.createTempFile("chess-leaderboard-", ".csv")
      try
        CsvExporter.writeLeaderboard(focus, results, tmp.toString).unsafeRunSync()
        val lines = Source.fromFile(tmp.toFile, "UTF-8").getLines().toList
        lines.head.startsWith("\uFEFF") shouldBe true
        lines.head should include("rank,opponent,focus_wins,draws,opponent_wins,points")

        // First data row is rank 1; should be one of the two tied-at-2 opponents.
        val row1 = lines.lift(1).value
        (row1.contains("\"random\"") || row1.contains("\"greedy\"")).shouldBe(true)
        // Last row should be the 0-point opponent
        val rowLast = lines.last
        rowLast should include("\"material\"")
      finally
        Files.deleteIfExists(tmp)
    }
  }

