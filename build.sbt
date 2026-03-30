ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.5.0"

// Determine the JavaFX platform classifier for this OS/arch
val javafxClassifier = {
  val os = sys.props("os.name").toLowerCase match {
    case n if n.contains("linux")   => "linux"
    case n if n.contains("mac")     => "mac"
    case n if n.contains("windows") => "win"
    case other => throw new RuntimeException(s"Unknown OS: $other")
  }
  val arch = sys.props("os.arch") match {
    case "aarch64" | "arm64" => "-aarch64"
    case _                   => ""
  }
  s"$os$arch"
}

lazy val Benchmarks = project
  .in(file("benchmark"))
  .enablePlugins(JmhPlugin)
  .dependsOn(Chess)
  .settings(
    name := "Chess-Benchmarks",
    scalaVersion := "3.5.0",
    publish / skip := true
  )

lazy val Chess = project
  .in(file("."))
  .settings(
    name := "Chess",
    Compile / mainClass := Some("chess.ChessApp"),
    // Try without fork - this might help window activation on macOS
    fork := false,
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scalafx" %% "scalafx" % "21.0.0-R32",
      "org.openjfx" % "javafx-base" % "21.0.2" classifier javafxClassifier,
      "org.openjfx" % "javafx-controls" % "21.0.2" classifier javafxClassifier,
      "org.openjfx" % "javafx-graphics" % "21.0.2" classifier javafxClassifier,
      "org.openjfx" % "javafx-fxml" % "21.0.2" classifier javafxClassifier,
      "io.circe" %% "circe-core" % "0.14.10",
      "io.circe" %% "circe-generic" % "0.14.10",
      "io.circe" %% "circe-parser" % "0.14.10",
      "com.lihaoyi" %% "upickle" % "4.0.2",
      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
      "com.lihaoyi" %% "fastparse" % "3.1.1",
      "org.apache.pekko" %% "pekko-actor-typed" % "1.1.2",
      "ch.qos.logback"    % "logback-classic"   % "1.5.6" % Runtime
    ),
    coverageExcludedFiles := ".*aview/ChessGUI.*;.*aview/FENExample.*;.*aview/PGNExample.*;.*ChessApp.*;.*AppBindings.*;.*ClockActor.*;.*FastParseFenParser.*;.*FastParsePgnParser.*",
    coverageMinimumStmtTotal := 40,
    coverageFailOnMinimum := false
  )
