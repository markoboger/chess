ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.5.0"

// Determine the JavaFX platform classifier for this OS/arch
val javafxClassifier = {
  val os = sys.props("os.name").toLowerCase match {
    case n if n.contains("linux")   => "linux"
    case n if n.contains("mac")     => "mac"
    case n if n.contains("windows") => "win"
    case other                      => throw new RuntimeException(s"Unknown OS: $other")
  }
  val arch = sys.props("os.arch") match {
    case "aarch64" | "arm64" => "-aarch64"
    case _                   => ""
  }
  s"$os$arch"
}

lazy val Seeder = project
  .in(file("seeder"))
  .dependsOn(Chess)
  .settings(
    name := "Chess-Seeder",
    scalaVersion := "3.5.0",
    Compile / mainClass := Some("chess.seeder.SeedOpeningsApp"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    publish / skip := true
  )

lazy val Benchmarks = project
  .in(file("benchmark"))
  .enablePlugins(JmhPlugin)
  .dependsOn(Chess)
  .settings(
    name := "Chess-Benchmarks",
    scalaVersion := "3.5.0",
    publish / skip := true
  )

lazy val Core = project
  .in(file("core"))
  .settings(
    name := "Chess-Core",
    scalaVersion := "3.5.0",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.4.0",
      "com.lihaoyi" %% "fastparse" % "3.1.1"
    ),
    publish / skip := true
  )

lazy val App = project
  .in(file("app"))
  .dependsOn(Core)
  .settings(
    name := "Chess-App",
    scalaVersion := "3.5.0",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "org.apache.pekko" %% "pekko-actor-typed" % "1.1.2"
    ),
    Compile / unmanagedResourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "resources",
    Test / unmanagedResourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "resources",
    publish / skip := true
  )

lazy val Realtime = project
  .in(file("realtime"))
  .dependsOn(App)
  .settings(
    name := "Chess-Realtime",
    scalaVersion := "3.5.0",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "io.circe" %% "circe-core" % "0.14.10",
      "io.circe" %% "circe-generic" % "0.14.10",
      "org.http4s" %% "http4s-dsl" % "0.23.30",
      "org.http4s" %% "http4s-ember-client" % "0.23.30",
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.http4s" %% "http4s-circe" % "0.23.30"
    ),
    publish / skip := true
  )

lazy val Data = project
  .in(file("data"))
  .dependsOn(Core, App)
  .settings(
    name := "Chess-Data",
    scalaVersion := "3.5.0",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "io.circe" %% "circe-core" % "0.14.10",
      "io.circe" %% "circe-generic" % "0.14.10",
      "io.circe" %% "circe-parser" % "0.14.10",
      "io.github.kirill5k" %% "mongo4cats-core" % "0.7.8",
      "io.github.kirill5k" %% "mongo4cats-circe" % "0.7.8",
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC5",
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC5",
      "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC5",
      "org.postgresql" % "postgresql" % "42.7.3",
      "com.zaxxer" % "HikariCP" % "5.1.0",
      "org.testcontainers" % "testcontainers" % "1.19.8" % Test,
      "org.testcontainers" % "postgresql" % "1.19.8" % Test,
      "org.testcontainers" % "mongodb" % "1.19.8" % Test,
      "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "4.13.1" % Test
    ),
    Test / unmanagedResourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "resources",
    publish / skip := true
  )

lazy val Chess = project
  .in(file("."))
  .dependsOn(Core, App, Realtime, Data)
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
      "org.apache.pekko" %% "pekko-actor-typed" % "1.1.2",
      "ch.qos.logback" % "logback-classic" % "1.5.6" % Runtime,
      // http4s (for microservices)
      "org.http4s" %% "http4s-dsl" % "0.23.30",
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.http4s" %% "http4s-ember-client" % "0.23.30",
      "org.http4s" %% "http4s-circe" % "0.23.30",
      "org.typelevel" %% "cats-effect" % "3.5.4"
    ),
    coverageExcludedFiles := ".*aview/ChessGUI.*;.*aview/FENExample.*;.*aview/PGNExample.*;.*ChessApp.*;.*AppBindings.*;.*ClockActor.*;.*FastParseFenParser.*;.*FastParsePgnParser.*;.*GameServer.*;.*GatewayServer.*;.*UIServer.*",
    coverageMinimumStmtTotal := 40,
    coverageFailOnMinimum := false
  )
