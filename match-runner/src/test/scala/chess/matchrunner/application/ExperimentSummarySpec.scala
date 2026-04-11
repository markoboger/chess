package chess.matchrunner.application

import chess.matchrunner.domain.{Experiment, ExperimentStatus, MatchResult, MatchRun}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class ExperimentSummarySpec extends AnyWordSpec with Matchers:

  private val now = Instant.now()

  private def exp = Experiment.create("test", None, 4, ExperimentStatus.Completed)

  "ExperimentSummary.fromRuns" should {
    "produce zeros for an empty run list" in {
      val e = exp
      val s = ExperimentSummary.fromRuns(e, Nil)
      s.totalRuns     shouldBe 0
      s.completedRuns shouldBe 0
      s.whiteWins     shouldBe 0
      s.blackWins     shouldBe 0
      s.draws         shouldBe 0
      s.errors        shouldBe 0
      s.flagWins      shouldBe 0
      s.averageMoves  shouldBe 0.0
      s.averageGameMs shouldBe None
      s.directions    shouldBe empty
    }

    "count flag wins correctly" in {
      val e = exp
      val runs = List(
        MatchRun.create(e.id, "g1", "a", "b").copy(finishedAt = Some(now), result = Some(MatchResult.WhiteWin), winner = Some("white-flag"), moveCount = Some(20), durationMs = Some(5000L)),
        MatchRun.create(e.id, "g2", "a", "b").copy(finishedAt = Some(now), result = Some(MatchResult.BlackWin), winner = Some("black-flag"), moveCount = Some(30), durationMs = Some(8000L)),
        MatchRun.create(e.id, "g3", "a", "b").copy(finishedAt = Some(now), result = Some(MatchResult.Draw),     winner = None,               moveCount = Some(60), durationMs = Some(12000L))
      )
      val s = ExperimentSummary.fromRuns(e, runs)
      s.flagWins      shouldBe 2
      s.whiteWins     shouldBe 1
      s.blackWins     shouldBe 1
      s.draws         shouldBe 1
      s.averageMoves  shouldBe ((20 + 30 + 60).toDouble / 3)
      s.averageGameMs shouldBe Some((5000 + 8000 + 12000).toDouble / 3)
    }

    "count errors and handle missing move counts" in {
      val e = exp
      val runs = List(
        MatchRun.create(e.id, "g1", "x", "y").copy(errorMessage = Some("crash")),
        MatchRun.create(e.id, "g2", "x", "y").copy(finishedAt = Some(now), result = Some(MatchResult.WhiteWin), moveCount = Some(10), durationMs = Some(3000L))
      )
      val s = ExperimentSummary.fromRuns(e, runs)
      s.errors        shouldBe 1
      s.completedRuns shouldBe 1
      s.averageMoves  shouldBe 10.0
      s.averageGameMs shouldBe Some(3000.0)
    }

    "build per-direction stats" in {
      val e = exp
      val runs = List(
        MatchRun.create(e.id, "g1", "mini", "rand").copy(finishedAt = Some(now), result = Some(MatchResult.WhiteWin), moveCount = Some(40), durationMs = Some(9000L)),
        MatchRun.create(e.id, "g2", "rand", "mini").copy(finishedAt = Some(now), result = Some(MatchResult.BlackWin), moveCount = Some(50))
      )
      val s = ExperimentSummary.fromRuns(e, runs)
      s.directions should have size 2
      val miniVsRand = s.directions.find(d => d.whiteStrategy == "mini" && d.blackStrategy == "rand").get
      miniVsRand.games      shouldBe 1
      miniVsRand.whiteWins  shouldBe 1
      miniVsRand.averageMoves shouldBe 40.0
      miniVsRand.averageGameMs shouldBe Some(9000.0)
      val randVsMini = s.directions.find(d => d.whiteStrategy == "rand" && d.blackStrategy == "mini").get
      randVsMini.averageGameMs shouldBe None
    }
  }
