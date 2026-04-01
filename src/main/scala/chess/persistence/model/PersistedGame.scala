package chess.persistence.model

import java.time.Instant
import java.util.UUID

/** Represents a persisted chess game in the database.
  *
  * @param id
  *   Unique identifier for the game
  * @param fenHistory
  *   List of FEN positions representing the game history
  * @param pgnMoves
  *   List of PGN moves in algebraic notation
  * @param currentTurn
  *   Current player's turn ("White" or "Black")
  * @param status
  *   Game status (e.g., "InProgress", "Checkmate", "Stalemate", "Draw")
  * @param result
  *   Game result (e.g., "1-0", "0-1", "1/2-1/2", or None if ongoing)
  * @param openingEco
  *   Optional ECO code of the opening played
  * @param openingName
  *   Optional name of the opening played
  * @param createdAt
  *   Timestamp when the game was created
  * @param updatedAt
  *   Timestamp when the game was last updated
  */
final case class PersistedGame(
    id: UUID,
    fenHistory: List[String],
    pgnMoves: List[String],
    currentTurn: String,
    status: String,
    result: Option[String],
    openingEco: Option[String],
    openingName: Option[String],
    createdAt: Instant,
    updatedAt: Instant
)

object PersistedGame:
  def create(
      fenHistory: List[String] = List.empty,
      pgnMoves: List[String] = List.empty,
      currentTurn: String = "White",
      status: String = "InProgress"
  ): PersistedGame =
    val now = Instant.now()
    PersistedGame(
      id = UUID.randomUUID(),
      fenHistory = fenHistory,
      pgnMoves = pgnMoves,
      currentTurn = currentTurn,
      status = status,
      result = None,
      openingEco = None,
      openingName = None,
      createdAt = now,
      updatedAt = now
    )
