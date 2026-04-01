package chess.microservices.persistence

import chess.microservices.persistence.domain.Opening

/** Abstract repository for chess opening persistence
  *
  * Defines the contract for storing and retrieving chess openings. Implementations should be provided for different
  * database backends (MongoDB, PostgreSQL, etc.).
  *
  * @tparam F
  *   Effect type (e.g., IO, Task, Future)
  */
trait OpeningRepository[F[_]]:

  /** Save a single opening
    *
    * @param opening
    *   The opening to persist
    * @return
    *   The saved opening
    */
  def save(opening: Opening): F[Opening]

  /** Save multiple openings in batch
    *
    * @param openings
    *   The openings to persist
    * @return
    *   Number of openings saved
    */
  def saveAll(openings: Vector[Opening]): F[Long]

  /** Find an opening by ECO code
    *
    * @param eco
    *   The ECO code (e.g., "C00")
    * @return
    *   Some(opening) if found, None otherwise
    */
  def findByEco(eco: String): F[Option[Opening]]

  /** Find all openings by name (case-insensitive partial match)
    *
    * @param name
    *   Part of the opening name
    * @return
    *   All openings matching the name
    */
  def findByName(name: String): F[Vector[Opening]]

  /** Find all openings that match a given position (by FEN)
    *
    * @param fen
    *   The FEN position
    * @return
    *   All openings that lead to this position
    */
  def findByFen(fen: String): F[Vector[Opening]]

  /** Find all openings
    *
    * @return
    *   All persisted openings
    */
  def findAll(): F[Vector[Opening]]

  /** Get total count of openings
    *
    * @return
    *   Total number of openings in the database
    */
  def count(): F[Long]

  /** Delete all openings (useful for testing and re-seeding)
    *
    * @return
    *   Number of openings deleted
    */
  def deleteAll(): F[Long]

  /** Search for openings relevant to the first N moves of a game
    *
    * This is used to suggest openings during game initialization.
    *
    * @param moveCount
    *   Maximum number of moves to consider
    * @return
    *   Openings that fit within the move count
    */
  def findByMoveCount(moveCount: Int): F[Vector[Opening]]
