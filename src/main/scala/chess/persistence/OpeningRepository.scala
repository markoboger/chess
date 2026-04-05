package chess.persistence

import chess.persistence.model.Opening

/** Repository abstraction for chess opening library.
  *
  * Uses higher-kinded type F[_] to abstract over effect types. Provides access to a large collection of chess openings
  * indexed by ECO codes.
  *
  * @tparam F
  *   The effect type (e.g., cats.effect.IO)
  */
trait OpeningRepository[F[_]]:

  /** Saves an opening to the database. If an opening with the same ECO code exists, it will be updated.
    *
    * @param opening
    *   The opening to save
    * @return
    *   The saved opening wrapped in effect F
    */
  def save(opening: Opening): F[Opening]

  /** Saves multiple openings in batch for efficient seeding.
    *
    * @param openings
    *   List of openings to save
    * @return
    *   Number of openings saved wrapped in effect F
    */
  def saveAll(openings: List[Opening]): F[Int]

  /** Retrieves all openings for an ECO code (e.g., all A00 variations).
    *
    * @param eco
    *   The ECO code (e.g., "B12", "C45")
    * @return
    *   List of openings for this ECO code, wrapped in effect F
    */
  def findByEco(eco: String): F[List[Opening]]

  /** Retrieves a specific opening by its ECO code and name.
    *
    * @param eco
    *   The ECO code
    * @param name
    *   The full opening name
    * @return
    *   Some(opening) if found, None if not found, wrapped in effect F
    */
  def findByEcoAndName(eco: String, name: String): F[Option[Opening]]

  /** Searches for openings by name (case-insensitive substring match).
    *
    * @param nameQuery
    *   Search query for opening name
    * @param limit
    *   Maximum number of openings to retrieve
    * @return
    *   List of matching openings wrapped in effect F
    */
  def findByName(nameQuery: String, limit: Int = 50): F[List[Opening]]

  /** Retrieves all openings, ordered by ECO code.
    *
    * @param limit
    *   Maximum number of openings to retrieve
    * @param offset
    *   Number of openings to skip (for pagination)
    * @return
    *   List of openings wrapped in effect F
    */
  def findAll(limit: Int = 100, offset: Int = 0): F[List[Opening]]

  /** Finds openings suitable for the given move count (typically first 8-12 moves).
    *
    * @param maxMoves
    *   Maximum number of moves in the opening line
    * @param limit
    *   Maximum number of openings to retrieve
    * @return
    *   List of openings wrapped in effect F
    */
  def findByMoveCount(maxMoves: Int, limit: Int = 100): F[List[Opening]]

  /** Finds a random opening from the database (for game initialization).
    *
    * @return
    *   A random opening wrapped in effect F
    */
  def findRandom(): F[Option[Opening]]

  /** Counts total number of openings in the database.
    *
    * @return
    *   Total count wrapped in effect F
    */
  def count(): F[Long]

  /** Deletes all openings from the database. Use with caution!
    *
    * @return
    *   Number of openings deleted wrapped in effect F
    */
  def deleteAll(): F[Long]
