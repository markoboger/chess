package chess.tournament.service

import cats.effect.*
import cats.effect.std.UUIDGen
import cats.implicits.*
import chess.tournament.events.TournamentEvents.*
import chess.tournament.kafka.EventProducer
import chess.tournament.model.TournamentModels.*

import java.time.Instant
import java.util.UUID

/** Service for managing tournaments with event sourcing */
trait TournamentService[F[_]]:
  /** Create a new tournament */
  def createTournament(
      name: String,
      tournamentType: TournamentType,
      maxPlayers: Int,
      timeControlSeconds: Int,
      createdBy: String
  ): F[Tournament]

  /** Register a player for a tournament */
  def registerPlayer(
      tournamentId: String,
      playerId: String,
      playerName: String,
      rating: Option[Int] = None
  ): F[Unit]

  /** Start a tournament (must have at least 2 players) */
  def startTournament(tournamentId: String): F[Unit]

  /** Get current tournament state (query side - would read from event store) */
  def getTournament(tournamentId: String): F[Option[Tournament]]

object TournamentService:

  def apply[F[_]: Async](producer: EventProducer[F]): TournamentService[F] =
    new TournamentServiceImpl[F](producer)

  private class TournamentServiceImpl[F[_]: Async](
      producer: EventProducer[F]
  ) extends TournamentService[F]:

    // In-memory state (in production, this would be an event-sourced read model)
    private val tournaments = new java.util.concurrent.ConcurrentHashMap[String, Tournament]()

    override def createTournament(
        name: String,
        tournamentType: TournamentType,
        maxPlayers: Int,
        timeControlSeconds: Int,
        createdBy: String
    ): F[Tournament] =
      for
        tournamentId <- UUIDGen[F].randomUUID.map(_.toString)
        now <- Async[F].delay(Instant.now())

        event = TournamentCreated(
          eventId = newEventId(),
          timestamp = now,
          tournamentId = tournamentId,
          name = name,
          tournamentType = tournamentType,
          maxPlayers = maxPlayers,
          timeControlSeconds = timeControlSeconds,
          createdBy = createdBy
        )

        tournament = Tournament(
          id = tournamentId,
          name = name,
          tournamentType = tournamentType,
          maxPlayers = maxPlayers,
          timeControlSeconds = timeControlSeconds,
          status = TournamentStatus.Registration,
          createdAt = now
        )

        _ <- producer.publish(event)
        _ <- Async[F].delay(tournaments.put(tournamentId, tournament))
      yield tournament

    override def registerPlayer(
        tournamentId: String,
        playerId: String,
        playerName: String,
        rating: Option[Int]
    ): F[Unit] =
      for
        tournament <- getTournamentOrFail(tournamentId)

        _ <-
          if !tournament.canRegisterMore then
            Async[F].raiseError(new RuntimeException("Tournament is full or not accepting registrations"))
          else
            Async[F].unit

        player = Player(playerId, playerName, rating)
        now <- Async[F].delay(Instant.now())

        event = PlayerRegistered(
          eventId = newEventId(),
          timestamp = now,
          tournamentId = tournamentId,
          playerId = playerId,
          playerName = playerName,
          rating = rating
        )

        updatedTournament = tournament.copy(players = tournament.players :+ player)

        _ <- producer.publish(event)
        _ <- Async[F].delay(tournaments.put(tournamentId, updatedTournament))
      yield ()

    override def startTournament(tournamentId: String): F[Unit] =
      for
        tournament <- getTournamentOrFail(tournamentId)

        _ <-
          if !tournament.canStart then
            Async[F].raiseError(new RuntimeException("Tournament cannot start (needs at least 2 players)"))
          else
            Async[F].unit

        now <- Async[F].delay(Instant.now())
        totalRounds = calculateTotalRounds(tournament)

        event = TournamentStarted(
          eventId = newEventId(),
          timestamp = now,
          tournamentId = tournamentId,
          totalPlayers = tournament.players.length,
          totalRounds = totalRounds
        )

        // Generate first round pairings
        pairings = generatePairings(tournament)
        gameEvents <- scheduleGames(tournamentId, 1, pairings)

        updatedTournament = tournament.copy(
          status = TournamentStatus.InProgress,
          startedAt = Some(now),
          currentRound = 1
        )

        _ <- producer.publish(event)
        _ <- producer.publishAll(gameEvents)
        _ <- Async[F].delay(tournaments.put(tournamentId, updatedTournament))
      yield ()

    override def getTournament(tournamentId: String): F[Option[Tournament]] =
      Async[F].delay(Option(tournaments.get(tournamentId)))

    private def getTournamentOrFail(tournamentId: String): F[Tournament] =
      getTournament(tournamentId).flatMap {
        case Some(t) => Async[F].pure(t)
        case None => Async[F].raiseError(new RuntimeException(s"Tournament not found: $tournamentId"))
      }

    private def calculateTotalRounds(tournament: Tournament): Int =
      tournament.tournamentType match
        case TournamentType.RoundRobin =>
          val n = tournament.players.length
          if n % 2 == 0 then n - 1 else n
        case TournamentType.Swiss =>
          Math.ceil(Math.log(tournament.players.length) / Math.log(2)).toInt
        case TournamentType.SingleElimination =>
          Math.ceil(Math.log(tournament.players.length) / Math.log(2)).toInt

    private def generatePairings(tournament: Tournament): List[(Player, Player)] =
      tournament.tournamentType match
        case TournamentType.RoundRobin =>
          RoundRobinPairing.generateAllPairings(tournament.players).headOption.getOrElse(Nil)
        case _ =>
          // For Swiss and SingleElimination, use simple pairing for first round
          tournament.players.grouped(2).collect {
            case List(p1, p2) => (p1, p2)
          }.toList

    private def scheduleGames(
        tournamentId: String,
        round: Int,
        pairings: List[(Player, Player)]
    ): F[List[GameScheduled]] =
      pairings.traverse { case (white, black) =>
        for
          gameId <- UUIDGen[F].randomUUID.map(_.toString)
          now <- Async[F].delay(Instant.now())
        yield GameScheduled(
          eventId = newEventId(),
          timestamp = now,
          tournamentId = tournamentId,
          gameId = gameId,
          round = round,
          whitePlayerId = white.id,
          blackPlayerId = black.id
        )
      }
