package chess.io

import chess.io.FileIO

/** FEN-specific board serialization interface.
  *
  * Extends [[FileIO]] because FEN, like JSON, serializes a single board
  * position. This allows FEN implementations to be used polymorphically
  * wherever a [[FileIO]] is expected.
  */
trait FenIO extends FileIO
