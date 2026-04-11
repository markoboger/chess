package chess.matchrunner.tui

import chess.matchrunner.application.ExperimentSummary
import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchResult, MatchRun}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

final class MatchRunnerShellSpec extends AnyWordSpec with Matchers:

  "MatchRunnerShell" should {
    "resolve strategies by number and id" in {
      MatchRunnerShell.resolveStrategy("1") shouldBe Some("greedy")
      MatchRunnerShell.resolveStrategy("2") shouldBe Some("endgame-minimax")
      MatchRunnerShell.resolveStrategy("iterative-deepening-endgame") shouldBe Some("iterative-deepening-endgame")
      MatchRunnerShell.resolveStrategy("does-not-exist") shouldBe None
    }

    "format a readable experiment summary" in {
      val finishedAt = Instant.now()
      val experiment = Experiment
        .create("minimax vs random", Some("baseline"), 4, ExperimentStatus.Completed)
        .copy(totalDurationMs = Some(90_000L))
      val runs = List(
        MatchRun.create(experiment.id, "g1", "minimax", "random").copy(
          finishedAt = Some(finishedAt),
          result = Some(MatchResult.WhiteWin),
          moveCount = Some(40),
          durationMs = Some(20_000L)
        ),
        MatchRun.create(experiment.id, "g2", "minimax", "random").copy(
          finishedAt = Some(finishedAt),
          result = Some(MatchResult.BlackWin),
          moveCount = Some(60),
          durationMs = Some(30_000L)
        ),
        MatchRun.create(experiment.id, "g3", "minimax", "random").copy(
          finishedAt = Some(finishedAt),
          result = Some(MatchResult.Draw),
          moveCount = Some(20),
          durationMs = Some(40_000L)
        ),
        MatchRun.create(experiment.id, "g4", "minimax", "random").copy(
          errorMessage = Some("timeout")
        )
      )

      val summary = ExperimentSummary.fromRuns(experiment, runs)
      val text = MatchRunnerShell.formatSummary(summary)

      text should include("Experiment: minimax vs random")
      text should include("Status:     Completed")
      text should include("Total runs: 3/4  errors=1")
      text should include("Avg game:   30.0s  total: 1m30s")
      text should include("minimax (W) vs random (B): wins=1 draws=1 losses=1")
      text should include("avg ply=40.0")
    }
  }
