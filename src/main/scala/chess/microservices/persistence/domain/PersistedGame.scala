package chess.microservices.persistence.domain

import java.time.Instant

/** Domain model for a persisted chess game
  *
  * Represents the complete state of a chess game for storage in a database.
  *
  * @param gameId
  *   Unique identifier for the game
  * @param fen
  *   Current board position in FEN notation
  * @param pgn
  *   Complete move history in PGN notation
  * @param status
  *   Game status (in_progress, checkmate, stalemate, etc.)
  * @param createdAt
  *   Timestamp when the game was created
  * @param updatedAt
  *   Timestamp when the game was last updated
  */
case class PersistedGame(
    gameId: String,
    fen: String,
    pgn: String,
    status: String,
    createdAt: Instant,
    updatedAt: Instant
)

object PersistedGame:
  /** Create a new persisted game with current timestamps
    */
  def create(gameId: String, fen: String, pgn: String = "", status: String = "in_progress"): PersistedGame =
    val now = Instant.now()
    PersistedGame(
      gameId = gameId,
      fen = fen,
      pgn = pgn,
      status = status,
      createdAt = now,
      updatedAt = now
    )

  /** Update an existing game with a new timestamp
    */
  def update(game: PersistedGame, fen: String, pgn: String, status: String): PersistedGame =
    game.copy(
      fen = fen,
      pgn = pgn,
      status = status,
      updatedAt = Instant.now()
    )
