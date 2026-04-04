package chess.tournament.model

import chess.tournament.events.TournamentEvents.{TournamentType, GameResult}

import java.time.Instant

/** Domain models for tournament system */
object TournamentModels:

  /** Player in a tournament */
  case class Player(
      id: String,
      name: String,
      rating: Option[Int] = None
  )

  /** Tournament configuration and state */
  case class Tournament(
      id: String,
      name: String,
      tournamentType: TournamentType,
      maxPlayers: Int,
      timeControlSeconds: Int,
      status: TournamentStatus,
      players: List[Player] = List.empty,
      currentRound: Int = 0,
      createdAt: Instant,
      startedAt: Option[Instant] = None,
      completedAt: Option[Instant] = None
  ):
    def canRegisterMore: Boolean =
      status == TournamentStatus.Registration && players.length < maxPlayers

    def canStart: Boolean =
      status == TournamentStatus.Registration && players.length >= 2

    def isComplete: Boolean =
      status == TournamentStatus.Completed

  enum TournamentStatus:
    case Registration
    case InProgress
    case Completed
    case Cancelled

  /** Game in a tournament */
  case class Game(
      id: String,
      tournamentId: String,
      round: Int,
      whitePlayer: Player,
      blackPlayer: Player,
      status: GameStatus,
      result: Option[GameResult] = None,
      moves: List[String] = List.empty, // UCI notation
      startedAt: Option[Instant] = None,
      completedAt: Option[Instant] = None
  ):
    def isComplete: Boolean = status == GameStatus.Completed

    def currentFen: String =
      // TODO: Apply moves to get current position
      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  enum GameStatus:
    case Scheduled
    case InProgress
    case Completed
    case Abandoned

  /** Player standings in a tournament */
  case class Standing(
      player: Player,
      points: Double,
      wins: Int,
      draws: Int,
      losses: Int,
      gamesPlayed: Int
  ):
    def addResult(result: GameResult, isWhite: Boolean): Standing =
      result match
        case GameResult.Draw =>
          copy(
            points = points + 0.5,
            draws = draws + 1,
            gamesPlayed = gamesPlayed + 1
          )
        case GameResult.WhiteWins if isWhite =>
          copy(
            points = points + 1.0,
            wins = wins + 1,
            gamesPlayed = gamesPlayed + 1
          )
        case GameResult.BlackWins if !isWhite =>
          copy(
            points = points + 1.0,
            wins = wins + 1,
            gamesPlayed = gamesPlayed + 1
          )
        case _ =>
          copy(
            losses = losses + 1,
            gamesPlayed = gamesPlayed + 1
          )

  object Standing:
    def empty(player: Player): Standing =
      Standing(player, 0.0, 0, 0, 0, 0)

  /** Round-robin pairing algorithm */
  object RoundRobinPairing:
    /** Generate all pairings for a round-robin tournament */
    def generateAllPairings(players: List[Player]): List[List[(Player, Player)]] =
      val n = players.length
      if n % 2 != 0 then
        // Add a "bye" player for odd number of players
        generateAllPairings(players :+ Player("bye", "BYE"))
      else
        val rounds = n - 1
        (0 until rounds).toList.map { round =>
          generateRound(players, round)
        }.filter(_.nonEmpty)

    private def generateRound(players: List[Player], round: Int): List[(Player, Player)] =
      val n = players.length
      val pairings = for
        i <- 0 until n / 2
        home = players((round + i) % n)
        away = players((n - 1 - i + round) % n)
        if home.id != "bye" && away.id != "bye"
      yield
        if round % 2 == i % 2 then (home, away)
        else (away, home)

      pairings.toList

  /** Swiss pairing algorithm (simplified) */
  object SwissPairing:
    /** Generate pairings for next Swiss round based on current standings */
    def generateNextRound(standings: List[Standing]): List[(Player, Player)] =
      val sortedPlayers = standings.sortBy(s => (-s.points, -s.wins)).map(_.player)

      // Simple top-half vs bottom-half pairing
      // In a real Swiss system, you'd avoid pairing players who already played
      sortedPlayers.grouped(2).collect {
        case List(p1, p2) => (p1, p2)
      }.toList
