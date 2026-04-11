package chess.matchrunner.tui

import cats.effect.IO
import chess.matchrunner.application.{DirectionStats, ExperimentRequest, ExperimentRunner, ExperimentSummary}
import chess.matchrunner.data.MatchRunnerRepository
import chess.matchrunner.domain.{Experiment, MatchRun}
import chess.matchrunner.csv.CsvExporter

final class MatchRunnerShell(
    runner: ExperimentRunner,
    repository: MatchRunnerRepository[IO],
    readLine: String => IO[String] = MatchRunnerShell.defaultReadLine,
    writeLine: String => IO[Unit] = line => IO.println(line)
):

  def run: IO[Unit] =
    writeHeader *> loop

  private def loop: IO[Unit] =
    readLine("\nmatch-runner> ").flatMap {
      case null | ""        => loop
      case "1" | "run"      => launchExperiment *> loop
      case "2" | "vs-all"   => launchVsAll *> loop
      case "3" | "list"     => listExperiments *> loop
      case "0" | "quit" | "exit" => writeLine("Bye.")
      case "help" | ":help" => printHelp *> loop
      case other            => writeLine(s"Unknown command: $other") *> printHelp *> loop
    }

  private def writeHeader: IO[Unit] =
    writeLine("Match Runner Service TUI") *>
      writeLine("========================") *>
      printHelp

  private def printHelp: IO[Unit] =
    writeLine("[1] run     Launch a single experiment (one strategy pair)") *>
      writeLine("[2] vs-all  Run a strategy against all others") *>
      writeLine("[3] list    List persisted experiments") *>
      writeLine("[0] quit    Exit the TUI")

  // ── Single experiment ──────────────────────────────────────────────────────

  private def launchExperiment: IO[Unit] =
    for
      _ <- writeLine("")
      _ <- writeLine("Available strategies:")
      _ <- writeLine(MatchRunnerShell.strategyMenu)
      name          <- readNonBlank("Experiment name: ")
      description   <- readOptional("Description (optional): ")
      whiteStrategy <- readStrategy("White strategy (number or id): ")
      blackStrategy <- readStrategy("Black strategy (number or id): ")
      games         <- readPositiveInt("Number of games: ")
      mirrored      <- readYesNo("Run mirrored pairs (swap colors for equal comparison)? [y/N]: ")
      clock         <- readClockMode
      request = ExperimentRequest(
        name = name,
        description = description,
        whiteStrategy = whiteStrategy,
        blackStrategy = blackStrategy,
        games = games,
        mirroredPairs = mirrored,
        clockInitialMs = clock.initialMs,
        clockIncrementMs = clock.incrementMs
      )
      totalGames = if mirrored then games * 2 else games
      _ <- writeLine(s"Starting experiment '$name' with $totalGames game(s)${if mirrored then " (mirrored)" else ""}${clock.label}...")
      experiment <- runner.runExperiment(request, onRunFinished = reportRunFinished)
      runs       <- repository.listRuns(experiment.id)
      summary    = ExperimentSummary.fromRuns(experiment, runs)
      _          <- writeLine("")
      _          <- writeLine(MatchRunnerShell.formatSummary(summary))
      _          <- offerRunsExport(summary.experiment.name, runs)
    yield ()

  // ── Vs-all tournament ─────────────────────────────────────────────────────

  private def launchVsAll: IO[Unit] =
    for
      _ <- writeLine("")
      _ <- writeLine("Available strategies:")
      _ <- writeLine(MatchRunnerShell.strategyMenu)
      focus       <- readStrategy("Focus strategy (number or id): ")
      games       <- readPositiveInt("Games per matchup: ")
      mirrored    <- readYesNo("Run mirrored pairs? [y/N]: ")
      clock       <- readClockMode
      opponents    = MatchRunnerShell.availableStrategies.filterNot(_ == focus)
      totalMatchups = opponents.size
      _ <- writeLine(s"Running $focus against $totalMatchups opponents, $games game(s) each${if mirrored then " (mirrored)" else ""}${clock.label}...")
      _ <- writeLine("")
      results <- runVsAllLoop(focus, opponents.toList, games, mirrored, clock, accumulator = Nil)
      _ <- writeLine("")
      _ <- writeLine(MatchRunnerShell.formatVsAllLeaderboard(focus, results))
      _ <- offerLeaderboardExport(focus, results)
      _ <- offerVsAllRunsExport(focus, results)
    yield ()

  private def runVsAllLoop(
      focus: String,
      opponents: List[String],
      games: Int,
      mirrored: Boolean,
      clock: MatchRunnerShell.ClockMode,
      accumulator: List[(String, ExperimentSummary)]
  ): IO[List[(String, ExperimentSummary)]] =
    opponents match
      case Nil => IO.pure(accumulator.reverse)
      case opponent :: rest =>
        val request = ExperimentRequest(
          name = s"$focus vs $opponent",
          description = Some(s"Auto-generated: $focus vs $opponent"),
          whiteStrategy = focus,
          blackStrategy = opponent,
          games = games,
          mirroredPairs = mirrored,
          clockInitialMs = clock.initialMs,
          clockIncrementMs = clock.incrementMs
        )
        val total = if mirrored then games * 2 else games
        for
          _ <- writeLine(s"  [$focus vs $opponent] $total game(s)...")
          experiment <- runner.runExperiment(request, onRunFinished = reportRunFinished)
          runs <- repository.listRuns(experiment.id)
          summary = ExperimentSummary.fromRuns(experiment, runs)
          _ <- writeLine(s"  Done: ${MatchRunnerShell.formatOneLiner(focus, opponent, summary)}")
          result <- runVsAllLoop(focus, rest, games, mirrored, clock, (opponent, summary) :: accumulator)
        yield result

  // ── List ──────────────────────────────────────────────────────────────────

  private def listExperiments: IO[Unit] =
    repository.listExperiments().flatMap { experiments =>
      if experiments.isEmpty then writeLine("No experiments stored yet.")
      else
        writeLine("Stored experiments:") *>
          writeLine(
            experiments.map { e =>
              val dur = e.totalDurationMs.map(ms => s"  ${MatchRunnerShell.formatDuration(ms)}").getOrElse("")
              s"  ${e.id} | ${e.status} | ${e.name} | games=${e.requestedGames}$dur"
            }.mkString("\n")
          )
    }

  // ── Per-run callback ──────────────────────────────────────────────────────

  private def reportRunFinished(run: MatchRun): IO[Unit] =
    val timing = run.durationMs.map(ms => s" duration=${MatchRunnerShell.formatDuration(ms)}").getOrElse("")
    val outcome = run.errorMessage match
      case Some(error) => s"error=$error"
      case None =>
        val result = run.result.map(_.toString).getOrElse("unknown")
        val flag   = if run.winner.exists(_.endsWith("-flag")) then " [flag]" else ""
        val moves  = run.moveCount.map(_.toString).getOrElse("?")
        s"result=$result$flag moves=$moves"
    writeLine(s"    game ${run.chessGameId}: $outcome$timing")

  // ── CSV export helpers ────────────────────────────────────────────────────

  private def offerRunsExport(experimentName: String, runs: List[MatchRun]): IO[Unit] =
    val default = CsvExporter.suggestPath(experimentName, "runs")
    readLine(s"Export game runs to CSV? [y/N] (default: $default): ").flatMap { input =>
      val trimmed = Option(input).map(_.trim).getOrElse("")
      if trimmed.equalsIgnoreCase("y") || trimmed.equalsIgnoreCase("yes") then
        val path = if trimmed.equalsIgnoreCase("y") || trimmed.equalsIgnoreCase("yes") then default else trimmed
        CsvExporter.writeRuns(experimentName, runs, path) *>
          writeLine(s"Runs exported to $path")
      else IO.unit
    }

  private def offerLeaderboardExport(focus: String, results: List[(String, ExperimentSummary)]): IO[Unit] =
    val default = CsvExporter.suggestPath(s"$focus-vs-all", "leaderboard")
    readLine(s"Export leaderboard to CSV? [y/N] (default: $default): ").flatMap { input =>
      val trimmed = Option(input).map(_.trim).getOrElse("")
      if trimmed.equalsIgnoreCase("y") || trimmed.equalsIgnoreCase("yes") then
        CsvExporter.writeLeaderboard(focus, results, default) *>
          writeLine(s"Leaderboard exported to $default")
      else IO.unit
    }

  private def offerVsAllRunsExport(focus: String, results: List[(String, ExperimentSummary)]): IO[Unit] =
    val default = CsvExporter.suggestPath(s"$focus-vs-all", "runs")
    readLine(s"Export all individual game runs to CSV? [y/N] (default: $default): ").flatMap { input =>
      val trimmed = Option(input).map(_.trim).getOrElse("")
      if trimmed.equalsIgnoreCase("y") || trimmed.equalsIgnoreCase("yes") then
        // Collect all runs from all matchup summaries stored in the DB
        // We re-use the summary's experiment reference to load runs
        val allRunsIO = results.foldLeft(IO.pure(List.empty[MatchRun])) {
          case (acc, (_, summary)) =>
            for
              soFar <- acc
              runs  <- repository.listRuns(summary.experiment.id)
            yield soFar ++ runs
        }
        allRunsIO.flatMap { runs =>
          CsvExporter.writeRuns(s"$focus vs all", runs, default) *>
            writeLine(s"All runs (${runs.size} games) exported to $default")
        }
      else IO.unit
    }

  // ── Input helpers ─────────────────────────────────────────────────────────

  private def readNonBlank(prompt: String): IO[String] =
    readLine(prompt).flatMap { input =>
      val trimmed = Option(input).getOrElse("").trim
      if trimmed.nonEmpty then IO.pure(trimmed)
      else writeLine("Value must not be empty.") *> readNonBlank(prompt)
    }

  private def readOptional(prompt: String): IO[Option[String]] =
    readLine(prompt).map(input => Option(input).map(_.trim).filter(_.nonEmpty))

  private def readPositiveInt(prompt: String): IO[Int] =
    readLine(prompt).flatMap { input =>
      Option(input).map(_.trim).flatMap(_.toIntOption).filter(_ > 0) match
        case Some(v) => IO.pure(v)
        case None    => writeLine("Please enter a positive integer.") *> readPositiveInt(prompt)
    }

  private def readNonNegativeLong(prompt: String): IO[Long] =
    readLine(prompt).flatMap { input =>
      Option(input).map(_.trim).flatMap(_.toLongOption).filter(_ >= 0) match
        case Some(v) => IO.pure(v)
        case None    => writeLine("Please enter a non-negative number.") *> readNonNegativeLong(prompt)
    }

  private def readYesNo(prompt: String): IO[Boolean] =
    readLine(prompt).map(input => Option(input).map(_.trim.toLowerCase).exists(v => v == "y" || v == "yes"))

  private def readStrategy(prompt: String): IO[String] =
    readLine(prompt).flatMap { input =>
      MatchRunnerShell.resolveStrategy(input) match
        case Some(s) => IO.pure(s)
        case None    => writeLine("Unknown strategy. Use a number from the list or a valid strategy id.") *> readStrategy(prompt)
    }

  private def readClockMode: IO[MatchRunnerShell.ClockMode] =
    writeLine("Clock mode:") *>
      writeLine("  [1] none (no clock)") *>
      writeLine("  [2] 1 second per side") *>
      writeLine("  [3] 2 seconds per side") *>
      writeLine("  [4] 5 seconds per side") *>
      writeLine("  [5] 10 seconds per side") *>
      writeLine("  [6] 20 seconds per side") *>
      writeLine("  [7] 1 minute per side") *>
      writeLine("  [8] 5 minutes per side") *>
      writeLine("  [9] custom") *>
      readLine("Clock mode [1]: ").flatMap { input =>
        Option(input).map(_.trim).getOrElse("1") match
          case "2" => IO.pure(MatchRunnerShell.ClockMode(Some(1_000L),   Some(0L), " [1s per side]"))
          case "3" => IO.pure(MatchRunnerShell.ClockMode(Some(2_000L),   Some(0L), " [2s per side]"))
          case "4" => IO.pure(MatchRunnerShell.ClockMode(Some(5_000L),   Some(0L), " [5s per side]"))
          case "5" => IO.pure(MatchRunnerShell.ClockMode(Some(10_000L),  Some(0L), " [10s per side]"))
          case "6" => IO.pure(MatchRunnerShell.ClockMode(Some(20_000L),  Some(0L), " [20s per side]"))
          case "7" => IO.pure(MatchRunnerShell.ClockMode(Some(60_000L),  Some(0L), " [1 min per side]"))
          case "8" => IO.pure(MatchRunnerShell.ClockMode(Some(300_000L), Some(0L), " [5 min per side]"))
          case "9" =>
            for
              mins    <- readNonNegativeLong("  Initial time per player (minutes): ")
              secs    <- readNonNegativeLong("  Increment per move (seconds): ")
              initMs   = mins * 60_000L
              incrMs   = secs * 1_000L
              label    = s" [custom ${mins}m+${secs}s]"
            yield MatchRunnerShell.ClockMode(Some(initMs), Some(incrMs), label)
          case _ => IO.pure(MatchRunnerShell.ClockMode(None, None, ""))
      }

object MatchRunnerShell:
  import java.util.Locale

  final case class ClockMode(initialMs: Option[Long], incrementMs: Option[Long], label: String)

  val availableStrategies: Vector[String] = Vector(
    "greedy",
    "endgame-minimax",
    "iterative-deepening-endgame",
    "opening-continuation-endgame",
    "opening-intelligence-endgame"
  )

  val strategyMenu: String =
    availableStrategies.zipWithIndex.map { case (s, i) => s"  [${i + 1}] $s" }.mkString("\n")

  def resolveStrategy(input: String): Option[String] =
    val trimmed = Option(input).getOrElse("").trim
    trimmed.toIntOption match
      case Some(index) if index >= 1 && index <= availableStrategies.length =>
        Some(availableStrategies(index - 1))
      case _ =>
        availableStrategies.find(_.equalsIgnoreCase(trimmed))

  // ── Formatting ────────────────────────────────────────────────────────────

  def formatSummary(summary: ExperimentSummary): String =
    val avgGame = summary.averageGameMs.map(ms => formatDuration(ms.toLong)).getOrElse("n/a")
    val total   = summary.totalDurationMs.map(formatDuration).getOrElse("n/a")
    val sb = new StringBuilder
    sb.append(s"Experiment: ${summary.experiment.name}\n")
    sb.append(s"Status:     ${summary.experiment.status}\n")
    val flagNote = if summary.flagWins > 0 then s"  flags=${summary.flagWins}" else ""
    sb.append(s"Total runs: ${summary.completedRuns}/${summary.totalRuns}  errors=${summary.errors}$flagNote\n")
    sb.append(s"Avg game:   $avgGame  total: $total\n")

    if summary.directions.size > 1 then
      sb.append("\nPer-direction breakdown:\n")
      sb.append(formatDirectionTable(summary.directions))
      sb.append("\nCombined strategy comparison:\n")
      sb.append(formatCombinedMatrix(summary))
    else if summary.directions.size == 1 then
      sb.append(formatDirectionRow(summary.directions.head))

    sb.toString

  def formatOneLiner(focus: String, opponent: String, summary: ExperimentSummary): String =
    // Tally wins from focus's perspective across all directions
    val focusWins = summary.directions.map { d =>
      if d.whiteStrategy == focus then d.whiteWins else d.blackWins
    }.sum
    val oppWins = summary.directions.map { d =>
      if d.blackStrategy == focus then d.whiteWins else d.blackWins
    }.sum
    val draws = summary.draws
    val total = summary.totalRuns
    val avg   = summary.averageMoves
    f"$focus vs $opponent ($total games): W=$focusWins D=$draws L=$oppWins  avg ply=${avg}%.1f"

  def formatVsAllLeaderboard(focus: String, results: List[(String, ExperimentSummary)]): String =
    val sb = new StringBuilder
    sb.append(s"=== $focus vs All — Final Standings ===\n\n")
    val header = f"  ${"Opponent"}%-28s ${"W"}%4s ${"D"}%4s ${"L"}%4s ${"pts"}%6s ${"avg ply"}%8s\n"
    val sep    = "  " + "-" * 60 + "\n"
    sb.append(header)
    sb.append(sep)

    // Score: win=2, draw=1, loss=0
    val rows = results.map { case (opponent, summary) =>
      val focusWins = summary.directions.map { d =>
        if d.whiteStrategy == focus then d.whiteWins else d.blackWins
      }.sum
      val oppWins = summary.directions.map { d =>
        if d.blackStrategy == focus then d.whiteWins else d.blackWins
      }.sum
      val draws = summary.draws
      val pts   = focusWins * 2 + draws
      (opponent, focusWins, draws, oppWins, pts, summary.averageMoves)
    }.sortBy(-_._5)

    rows.foreach { case (opp, w, d, l, pts, avg) =>
      sb.append(f"  ${opp}%-28s ${w}%4d ${d}%4d ${l}%4d ${pts}%6d ${avg}%8.1f\n")
    }

    val totalW = rows.map(_._2).sum
    val totalD = rows.map(_._3).sum
    val totalL = rows.map(_._4).sum
    sb.append(sep)
    sb.append(f"  ${"Total"}%-28s ${totalW}%4d ${totalD}%4d ${totalL}%4d\n")
    sb.toString

  private def formatDirectionTable(directions: List[DirectionStats]): String =
    val anyFlags = directions.exists(_.flagWins > 0)
    val flagHdr  = if anyFlags then f" ${"flag"}%5s" else ""
    val header   = f"  ${"White"}%-28s ${"Black"}%-28s ${"W"}%4s ${"D"}%4s ${"L"}%4s$flagHdr ${"avg ply"}%8s ${"avg time"}%10s\n"
    val sep      = "  " + "-" * (94 + (if anyFlags then 6 else 0)) + "\n"
    val rows     = directions.map { d =>
      val avgMoves = String.format(Locale.US, "%.1f", Double.box(d.averageMoves))
      val avgTime  = d.averageGameMs.map(ms => formatDuration(ms.toLong)).getOrElse("n/a")
      val flagCol  = if anyFlags then f" ${d.flagWins}%5d" else ""
      f"  ${d.whiteStrategy}%-28s ${d.blackStrategy}%-28s ${d.whiteWins}%4d ${d.draws}%4d ${d.blackWins}%4d$flagCol ${avgMoves}%8s ${avgTime}%10s\n"
    }
    header + sep + rows.mkString

  private def formatDirectionRow(d: DirectionStats): String =
    val avgMoves = String.format(Locale.US, "%.1f", Double.box(d.averageMoves))
    val avgTime  = d.averageGameMs.map(ms => formatDuration(ms.toLong)).getOrElse("n/a")
    s"  ${d.whiteStrategy} (W) vs ${d.blackStrategy} (B): wins=${d.whiteWins} draws=${d.draws} losses=${d.blackWins}  avg ply=$avgMoves  avg time=$avgTime\n"

  private def formatCombinedMatrix(summary: ExperimentSummary): String =
    val strategyPairs =
      summary.directions
        .groupBy(d => if d.whiteStrategy <= d.blackStrategy then (d.whiteStrategy, d.blackStrategy) else (d.blackStrategy, d.whiteStrategy))
        .toList
        .sortBy(_._1)
    strategyPairs.map { case ((a, b), dirs) =>
      val aWins    = dirs.map { d => if d.whiteStrategy == a then d.whiteWins else d.blackWins }.sum
      val bWins    = dirs.map { d => if d.blackStrategy == b then d.blackWins else d.whiteWins }.sum
      val allDraws = dirs.map(_.draws).sum
      val total    = dirs.map(_.games).sum
      val aRate    = if total > 0 then f"${aWins * 100.0 / total}%.0f%%" else "n/a"
      val bRate    = if total > 0 then f"${bWins * 100.0 / total}%.0f%%" else "n/a"
      s"  $a vs $b ($total games): $a wins=$aWins ($aRate)  $b wins=$bWins ($bRate)  draws=$allDraws\n"
    }.mkString

  def formatDuration(ms: Long): String =
    if ms < 1000 then s"${ms}ms"
    else if ms < 60000 then String.format(Locale.US, "%.1fs", Double.box(ms / 1000.0))
    else
      val minutes = ms / 60000
      val seconds = (ms % 60000) / 1000
      s"${minutes}m${seconds}s"

  private def defaultReadLine(prompt: String): IO[String] =
    IO.blocking {
      print(prompt)
      scala.io.StdIn.readLine()
    }
