package chess.matchrunner.csv

import cats.effect.IO
import chess.matchrunner.application.ExperimentSummary
import chess.matchrunner.domain.MatchRun

import java.io.{BufferedWriter, FileOutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter:

  private val timestampFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

  // ── Runs export ────────────────────────────────────────────────────────────
  // One row per game. Good for pivot tables in Excel.

  def writeRuns(
      experimentName: String,
      runs: List[MatchRun],
      path: String
  ): IO[Unit] = IO.blocking {
    withWriter(path) { w =>
      // UTF-8 BOM so Excel auto-detects encoding
      w.write("\uFEFF")
      w.write("experiment,game_id,white_strategy,black_strategy,result,winner,move_count,duration_s,error,started_at,finished_at\n")
      runs.foreach { r =>
        val row = Seq(
          q(experimentName),
          q(r.chessGameId),
          q(r.whiteStrategy),
          q(r.blackStrategy),
          q(r.result.map(_.toString).getOrElse("")),
          q(r.winner.getOrElse("")),
          r.moveCount.map(_.toString).getOrElse(""),
          r.durationMs.map(ms => f"${ms / 1000.0}%.3f").getOrElse(""),
          q(r.errorMessage.getOrElse("")),
          r.startedAt.pipe(timestampFmt.format),
          r.finishedAt.map(timestampFmt.format).getOrElse("")
        )
        w.write(row.mkString(",") + "\n")
      }
    }
  }

  // ── Per-direction summary export ──────────────────────────────────────────
  // One row per (white, black) strategy pair.

  def writeSummary(
      summary: ExperimentSummary,
      path: String
  ): IO[Unit] = IO.blocking {
    withWriter(path) { w =>
      w.write("\uFEFF")
      w.write("experiment,white_strategy,black_strategy,games,wins,draws,losses,win_rate_pct,avg_ply,avg_game_s,total_duration_s\n")
      summary.directions.foreach { d =>
        val total   = d.games
        val winRate = if total > 0 then f"${d.whiteWins * 100.0 / total}%.1f" else ""
        val avgPly  = f"${d.averageMoves}%.1f"
        val avgGame = d.averageGameMs.map(ms => f"${ms / 1000.0}%.3f").getOrElse("")
        val totalDur = summary.totalDurationMs.map(ms => f"${ms / 1000.0}%.3f").getOrElse("")
        val row = Seq(
          q(summary.experiment.name),
          q(d.whiteStrategy),
          q(d.blackStrategy),
          total.toString,
          d.whiteWins.toString,
          d.draws.toString,
          d.blackWins.toString,
          winRate,
          avgPly,
          avgGame,
          totalDur
        )
        w.write(row.mkString(",") + "\n")
      }
    }
  }

  // ── Leaderboard export (vs-all) ───────────────────────────────────────────

  def writeLeaderboard(
      focus: String,
      results: List[(String, ExperimentSummary)],
      path: String
  ): IO[Unit] = IO.blocking {
    withWriter(path) { w =>
      w.write("\uFEFF")
      w.write("rank,opponent,focus_wins,draws,opponent_wins,points,win_rate_pct,avg_ply,avg_game_s\n")

      val rows = results.map { case (opponent, summary) =>
        val focusWins = summary.directions.map { d =>
          if d.whiteStrategy == focus then d.whiteWins else d.blackWins
        }.sum
        val oppWins = summary.directions.map { d =>
          if d.blackStrategy == focus then d.whiteWins else d.blackWins
        }.sum
        val draws = summary.draws
        val total = summary.totalRuns
        val pts   = focusWins * 2 + draws
        (opponent, focusWins, draws, oppWins, pts, total, summary.averageMoves, summary.averageGameMs)
      }.sortBy(-_._5).zipWithIndex

      rows.foreach { case ((opp, wins, draws, losses, pts, total, avgPly, avgGame), idx) =>
        val winRate    = if total > 0 then f"${wins * 100.0 / total}%.1f" else ""
        val avgPlyFmt  = f"$avgPly%.1f"
        val avgGameFmt = avgGame.map(ms => f"${ms / 1000.0}%.3f").getOrElse("")
        val row = Seq(
          (idx + 1).toString,
          q(opp),
          wins.toString,
          draws.toString,
          losses.toString,
          pts.toString,
          winRate,
          avgPlyFmt,
          avgGameFmt
        )
        w.write(row.mkString(",") + "\n")
      }
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /** Suggest a safe filename from an experiment name + suffix. */
  def suggestPath(base: String, suffix: String): String =
    val safe = base.trim.replaceAll("[^a-zA-Z0-9_-]", "-").replaceAll("-{2,}", "-").take(60)
    s"$safe-$suffix.csv"

  /** Wrap a value in double-quotes, escaping any quotes inside. */
  private def q(value: String): String =
    "\"" + value.replace("\"", "\"\"") + "\""

  private def withWriter(path: String)(f: BufferedWriter => Unit): Unit =
    val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(path), StandardCharsets.UTF_8))
    try f(writer)
    finally writer.close()

  extension [A](a: A)
    private def pipe[B](f: A => B): B = f(a)
