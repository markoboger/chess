package chess.tournament

import cats.effect.{IO, IOApp, ExitCode}
import chess.tournament.events.TournamentEvents.TournamentType
import chess.tournament.kafka.EventProducer
import chess.tournament.service.TournamentService

/** Demo application for Kafka Tournament Platform */
object TournamentApp extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    EventProducer[IO]().use { producer =>
      val service = TournamentService[IO](producer)

      for
        _ <- IO.println("=== Kafka Tournament Platform Demo ===")
        _ <- IO.println("Creating tournament...")

        // Create a tournament
        tournament <- service.createTournament(
          name = "Spring Championship 2026",
          tournamentType = TournamentType.RoundRobin,
          maxPlayers = 4,
          timeControlSeconds = 300,
          createdBy = "admin"
        )

        _ <- IO.println(s"Tournament created: ${tournament.name} (${tournament.id})")
        _ <- IO.println(s"Type: ${tournament.tournamentType}, Max players: ${tournament.maxPlayers}")
        _ <- IO.println("")

        // Register players
        _ <- IO.println("Registering players...")
        _ <- service.registerPlayer(tournament.id, "bot1", "AlphaBot", Some(1800))
        _ <- service.registerPlayer(tournament.id, "bot2", "BetaEngine", Some(1750))
        _ <- service.registerPlayer(tournament.id, "bot3", "GammaAI", Some(1820))
        _ <- service.registerPlayer(tournament.id, "bot4", "DeltaMaster", Some(1790))

        updatedTournament <- service.getTournament(tournament.id)
        _ <- IO.println(s"Registered ${updatedTournament.map(_.players.length).getOrElse(0)} players")
        _ <- IO.println("")

        // Start tournament
        _ <- IO.println("Starting tournament...")
        _ <- service.startTournament(tournament.id)

        finalTournament <- service.getTournament(tournament.id)
        _ <- finalTournament match
          case Some(t) =>
            IO.println(s"Tournament started: ${t.status}") *>
            IO.println(s"Current round: ${t.currentRound}") *>
            IO.println(s"Total players: ${t.players.length}")
          case None =>
            IO.println("Tournament not found")

        _ <- IO.println("")
        _ <- IO.println("✅ Events published to Kafka topics:")
        _ <- IO.println("   - tournament-events: TournamentCreated, PlayerRegistered (x4), TournamentStarted")
        _ <- IO.println("   - game-events: GameScheduled (x6 for round-robin)")
        _ <- IO.println("")
        _ <- IO.println("Check Kafka UI at http://localhost:8080 to see the events!")
        _ <- IO.println("")
        _ <- IO.println("To start Kafka: docker-compose -f docker-compose.kafka.yml up")

      yield ExitCode.Success
    }
