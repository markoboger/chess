package chess.persistence.repository

import chess.persistence.model.PersistedGame
import java.util.UUID

/** Repository abstraction for persisting and retrieving chess games.
  *
  * Uses higher-kinded type F[_] to abstract over effect types (e.g., IO, Future, Try). This allows the same interface
  * to work with different effect systems and database implementations.
  *
  * @tparam F
  *   The effect type (e.g., cats.effect.IO)
  */
trait GameRepository[F[_]]:

  /** Saves a game to the database. If a game with the same ID exists, it will be updated.
    *
    * @param game
    *   The game to save
    * @return
    *   The saved game wrapped in effect F
    */
  def save(game: PersistedGame): F[PersistedGame]

  /** Retrieves a game by its ID.
    *
    * @param id
    *   The game ID
    * @return
    *   Some(game) if found, None if not found, wrapped in effect F
    */
  def findById(id: UUID): F[Option[PersistedGame]]

  /** Retrieves all games, ordered by creation date (newest first).
    *
    * @param limit
    *   Maximum number of games to retrieve
    * @param offset
    *   Number of games to skip (for pagination)
    * @return
    *   List of games wrapped in effect F
    */
  def findAll(limit: Int = 100, offset: Int = 0): F[List[PersistedGame]]

  /** Finds games by status (e.g., "InProgress", "Checkmate").
    *
    * @param status
    *   The status to filter by
    * @param limit
    *   Maximum number of games to retrieve
    * @return
    *   List of games wrapped in effect F
    */
  def findByStatus(status: String, limit: Int = 100): F[List[PersistedGame]]

  /** Finds games that start with a specific opening.
    *
    * @param eco
    *   The ECO code of the opening
    * @param limit
    *   Maximum number of games to retrieve
    * @return
    *   List of games wrapped in effect F
    */
  def findByOpening(eco: String, limit: Int = 100): F[List[PersistedGame]]

  /** Deletes a game by its ID.
    *
    * @param id
    *   The game ID
    * @return
    *   true if deleted, false if not found, wrapped in effect F
    */
  def delete(id: UUID): F[Boolean]

  /** Counts total number of games in the database.
    *
    * @return
    *   Total count wrapped in effect F
    */
  def count(): F[Long]

  /** Deletes all games from the database. Use with caution!
    *
    * @return
    *   Number of games deleted wrapped in effect F
    */
  def deleteAll(): F[Long]
