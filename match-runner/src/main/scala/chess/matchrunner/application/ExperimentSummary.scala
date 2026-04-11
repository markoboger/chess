package chess.matchrunner.application

import chess.matchrunner.domain.{Experiment, MatchResult, MatchRun}

final case class DirectionStats(
    whiteStrategy: String,
    blackStrategy: String,
    games: Int,
    whiteWins: Int,
    blackWins: Int,
    draws: Int,
    errors: Int,
    averageMoves: Double,
    averageGameMs: Option[Double]
)

final case class ExperimentSummary(
    experiment: Experiment,
    totalRuns: Int,
    completedRuns: Int,
    whiteWins: Int,
    blackWins: Int,
    draws: Int,
    errors: Int,
    averageMoves: Double,
    averageGameMs: Option[Double],
    totalDurationMs: Option[Long],
    directions: List[DirectionStats]
)

object ExperimentSummary:
  def fromRuns(experiment: Experiment, runs: List[MatchRun]): ExperimentSummary =
    val completed = runs.count(_.finishedAt.nonEmpty)
    val whiteWins = runs.count(_.result.contains(MatchResult.WhiteWin))
    val blackWins = runs.count(_.result.contains(MatchResult.BlackWin))
    val draws = runs.count(_.result.contains(MatchResult.Draw))
    val errors = runs.count(_.errorMessage.nonEmpty)
    val moveCounts = runs.flatMap(_.moveCount)
    val averageMoves =
      if moveCounts.isEmpty then 0.0
      else moveCounts.sum.toDouble / moveCounts.size.toDouble
    val durations = runs.flatMap(_.durationMs)
    val averageGameMs =
      if durations.isEmpty then None
      else Some(durations.sum.toDouble / durations.size.toDouble)

    val directions =
      runs
        .groupBy(r => (r.whiteStrategy, r.blackStrategy))
        .toList
        .sortBy(_._1)
        .map { case ((white, black), dirRuns) =>
          val dMoveCounts = dirRuns.flatMap(_.moveCount)
          val dAvgMoves =
            if dMoveCounts.isEmpty then 0.0
            else dMoveCounts.sum.toDouble / dMoveCounts.size.toDouble
          val dDurations = dirRuns.flatMap(_.durationMs)
          val dAvgMs =
            if dDurations.isEmpty then None
            else Some(dDurations.sum.toDouble / dDurations.size.toDouble)
          DirectionStats(
            whiteStrategy = white,
            blackStrategy = black,
            games = dirRuns.size,
            whiteWins = dirRuns.count(_.result.contains(MatchResult.WhiteWin)),
            blackWins = dirRuns.count(_.result.contains(MatchResult.BlackWin)),
            draws = dirRuns.count(_.result.contains(MatchResult.Draw)),
            errors = dirRuns.count(_.errorMessage.nonEmpty),
            averageMoves = dAvgMoves,
            averageGameMs = dAvgMs
          )
        }

    ExperimentSummary(
      experiment = experiment,
      totalRuns = runs.size,
      completedRuns = completed,
      whiteWins = whiteWins,
      blackWins = blackWins,
      draws = draws,
      errors = errors,
      averageMoves = averageMoves,
      averageGameMs = averageGameMs,
      totalDurationMs = experiment.totalDurationMs,
      directions = directions
    )
