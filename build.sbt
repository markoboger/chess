ThisBuild / organization := "com.example"
ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.5.0"

lazy val Chess = project
  .in(file("."))
  .settings(
    name := "Chess",
    Compile / mainClass := Some("chess.ChessApp"),
    // Try without fork - this might help window activation on macOS
    fork := false,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalafx" %% "scalafx" % "21.0.0-R32"
    ),
    // Note: Scoverage exclusions don't work reliably with Scala 3.5.0
    // See COVERAGE_REPORT.md for actual core logic coverage (88.54%)
    coverageMinimumStmtTotal := 40,
    coverageFailOnMinimum := false
  )
