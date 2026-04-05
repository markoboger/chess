package chess

import cats.effect.IO
import chess.application.opening.{OpeningIO, OpeningParser}
import chess.controller.io.{FenIO, PgnIO}
import chess.controller.io.fen.{RegexFenParser, CombinatorFenParser, FastParseFenParser}
import chess.controller.io.pgn.{PgnFileIO, CombinatorPgnParser, FastParsePgnParser}
import chess.persistence.OpeningRepository
import chess.persistence.memory.InMemoryOpeningRepository

/** Wiring of trait-based interfaces to their concrete implementations.
  *
  * Import `chess.AppBindings.given` (or `chess.AppBindings.*`) wherever a `given FenIO` or `given PgnIO` is needed. To
  * swap an implementation, uncomment the desired line and comment out the others — no other code needs to be touched.
  */
object AppBindings:

  // --- FenIO ----------------------------------------------------------------
  given FenIO = RegexFenParser
  // given FenIO = CombinatorFenParser
  // given FenIO = FastParseFenParser

  // --- PgnIO ----------------------------------------------------------------
  given PgnIO = PgnFileIO()
  // given PgnIO = CombinatorPgnParser
  // given PgnIO = FastParsePgnParser

  // --- OpeningIO ------------------------------------------------------------
  given OpeningIO = OpeningParser

  // --- OpeningRepository ----------------------------------------------------
  given OpeningRepository[IO] = InMemoryOpeningRepository.fromLichess()
  // To use PostgreSQL or MongoDB instead, replace with a resource-based
  // initializer in your IOApp (see SeedOpeningsApp for connection examples):
  //   given OpeningRepository[IO] = PostgresOpeningRepository.create(xa)
  //   given OpeningRepository[IO] = MongoOpeningRepository(collection)
