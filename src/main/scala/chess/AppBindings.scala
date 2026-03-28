package chess

import chess.controller.io.{FenIO, PgnIO}
import chess.controller.io.fen.{RegexFenParser, CombinatorFenParser, FastParseFenParser}
import chess.controller.io.pgn.{PgnFileIO, CombinatorPgnParser, FastParsePgnParser}

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
  // given PgnIO = PgnFileIO()
  given PgnIO = CombinatorPgnParser
  // given PgnIO = FastParsePgnParser
