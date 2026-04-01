package chess.microservices.persistence

import chess.microservices.persistence.domain.PersistedGame

/** Abstract repository for chess game persistence
  *
  * Defines the contract for storing and retrieving chess games. Implementations should be provided for different
  * database backends (MongoDB, PostgreSQL, etc.).
  *
  * @tparam F
  *   Effect type (e.g., IO, Task, Future)
  */
trait GameRepository[F[_]]:

  /** Save a new game or update an existing one
    *
    * @param game
    *   The game to persist
    * @return
    *   The saved game, potentially with updated fields
    */
  def save(game: PersistedGame): F[PersistedGame]

  /** Find a game by its unique identifier
    *
    * @param gameId
    *   The game identifier
    * @return
    *   Some(game) if found, None otherwise
    */
  def findById(gameId: String): F[Option[PersistedGame]]

  /** Delete a game by its identifier
    *
    * @param gameId
    *   The game identifier
    * @return
    *   true if the game was deleted, false if not found
    */
  def delete(gameId: String): F[Boolean]

  /** Find all games
    *
    * @return
    *   All persisted games
    */
  def findAll(): F[Vector[PersistedGame]]

  /** Find games by status
    *
    * @param status
    *   The game status (e.g., "in_progress", "checkmate")
    * @return
    *   All games matching the status
    */
  def findByStatus(status: String): F[Vector[PersistedGame]]

  /** Check if a game exists
    *
    * @param gameId
    *   The game identifier
    * @return
    *   true if the game exists, false otherwise
    */
  def exists(gameId: String): F[Boolean]

  /** Delete all games (useful for testing)
    *
    * @return
    *   Number of games deleted
    */
  def deleteAll(): F[Long]
