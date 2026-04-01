package chess.benchmark

import chess.persistence.model.PersistedGame

import java.time.Instant
import java.util.UUID
import scala.util.Random

/** Utility for generating realistic chess game test data for benchmarking.
  *
  * Provides methods to generate individual games or batches of games with varying characteristics to simulate real-world
  * database workloads.
  */
object TestDataGenerator:

  /** Common chess opening ECO codes */
  val ecoCodes: List[String] = List(
    "A00", // Polish Opening
    "A40", // Queen's Pawn
    "B20", // Sicilian Defense
    "C50", // Italian Game
    "C60", // Ruy Lopez
    "D20", // Queen's Gambit Accepted
    "E60", // King's Indian Defense
    "E70" // King's Indian, Normal
  )

  /** Opening names corresponding to ECO codes */
  val openingNames: Map[String, String] = Map(
    "A00" -> "Polish Opening",
    "A40" -> "Queen's Pawn Game",
    "B20" -> "Sicilian Defense",
    "C50" -> "Italian Game",
    "C60" -> "Ruy Lopez",
    "D20" -> "Queen's Gambit Accepted",
    "E60" -> "King's Indian Defense",
    "E70" -> "King's Indian, Normal Variation"
  )

  /** Possible game statuses */
  val gameStatuses: List[String] = List("InProgress", "Checkmate", "Stalemate", "Draw")

  /** Possible game results */
  val gameResults: Map[String, Option[String]] = Map(
    "InProgress" -> None,
    "Checkmate" -> Some("1-0"),
    "Stalemate" -> Some("1/2-1/2"),
    "Draw" -> Some("1/2-1/2")
  )

  /** Generate a single random chess game with realistic data.
    *
    * @param moveCount
    *   Optional specific number of moves (random if not provided)
    * @param status
    *   Optional specific status (random if not provided)
    * @param withOpening
    *   Whether to include opening information (default: true)
    * @return
    *   A PersistedGame with realistic data
    */
  def generateGame(
      moveCount: Option[Int] = None,
      status: Option[String] = None,
      withOpening: Boolean = true
  ): PersistedGame =
    val moves = moveCount.getOrElse(Random.between(10, 80))
    val gameStatus = status.getOrElse(gameStatuses(Random.nextInt(gameStatuses.length)))

    // Generate FEN history (simplified - in reality would vary more)
    val fenHistory = (0 to moves).map: i =>
      generateFenPosition(i + 1)
    .toList

    // Generate PGN moves (simplified algebraic notation)
    val pgnMoves = generatePgnMoves(moves)

    // Select random opening
    val (selectedEco, selectedOpening) =
      if withOpening && Random.nextBoolean() then
        val eco = ecoCodes(Random.nextInt(ecoCodes.length))
        (Some(eco), Some(openingNames(eco)))
      else (None, None)

    val now = Instant.now()
    val ageInSeconds = Random.nextInt(86400 * 90) // 0-90 days old

    PersistedGame(
      id = UUID.randomUUID(),
      fenHistory = fenHistory,
      pgnMoves = pgnMoves,
      currentTurn = if moves % 2 == 0 then "White" else "Black",
      status = gameStatus,
      result = gameResults(gameStatus),
      openingEco = selectedEco,
      openingName = selectedOpening,
      createdAt = now.minusSeconds(ageInSeconds),
      updatedAt = now.minusSeconds(Random.nextInt(ageInSeconds + 1))
    )

  /** Generate a batch of games for testing.
    *
    * @param count
    *   Number of games to generate
    * @param dataProfile
    *   Type of data distribution to use
    * @return
    *   List of generated games
    */
  def generateBatch(count: Int, dataProfile: DataProfile = DataProfile.Mixed): List[PersistedGame] =
    dataProfile match
      case DataProfile.Mixed =>
        (1 to count).map(_ => generateGame()).toList

      case DataProfile.ShortGames =>
        (1 to count).map(_ => generateGame(moveCount = Some(Random.between(5, 20)))).toList

      case DataProfile.LongGames =>
        (1 to count).map(_ => generateGame(moveCount = Some(Random.between(60, 120)))).toList

      case DataProfile.InProgress =>
        (1 to count).map(_ => generateGame(status = Some("InProgress"))).toList

      case DataProfile.Completed =>
        val statuses = List("Checkmate", "Stalemate", "Draw")
        (1 to count).map: _ =>
          generateGame(status = Some(statuses(Random.nextInt(statuses.length))))
        .toList

  /** Generate a FEN position string (simplified for testing).
    *
    * @param moveNumber
    *   The move number
    * @return
    *   A FEN position string
    */
  private def generateFenPosition(moveNumber: Int): String =
    // Simplified FEN - in reality would vary based on game state
    s"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 $moveNumber"

  /** Generate PGN moves (simplified algebraic notation).
    *
    * @param count
    *   Number of half-moves to generate
    * @return
    *   List of PGN move strings
    */
  private def generatePgnMoves(count: Int): List[String] =
    val commonMoves = List("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7", "d4", "d6", "c3", "c5")

    (1 to count).map: i =>
      val moveNumber = (i + 1) / 2
      val move = commonMoves(Random.nextInt(commonMoves.length))
      if i % 2 == 1 then s"$moveNumber. $move" else move
    .toList

  /** Data profiles for generating different types of test data. */
  enum DataProfile:
    case Mixed // Mix of all game types
    case ShortGames // Games with few moves (5-20)
    case LongGames // Games with many moves (60-120)
    case InProgress // Only ongoing games
    case Completed // Only finished games
