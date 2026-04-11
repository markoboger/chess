package chess.model

/** Settings that govern how a game session is played.
  *
  * @param whiteIsHuman
  *   Whether the white player is human (true) or AI (false)
  * @param blackIsHuman
  *   Whether the black player is human (true) or AI (false)
  * @param whiteStrategy
  *   AI strategy for white (ignored when whiteIsHuman = true)
  * @param blackStrategy
  *   AI strategy for black (ignored when blackIsHuman = true)
  * @param clockInitialMs
  *   Optional initial clock time per player in milliseconds
  * @param clockIncrementMs
  *   Optional clock increment per move in milliseconds
  * @param backendAutoplay
  *   Whether the backend service should autonomously continue non-human turns after create/move/load
  */
case class GameSettings(
    whiteIsHuman: Boolean = true,
    blackIsHuman: Boolean = true,
    whiteStrategy: String = "opening-continuation",
    blackStrategy: String = "opening-continuation",
    clockInitialMs: Option[Long] = None,
    clockIncrementMs: Option[Long] = None,
    backendAutoplay: Boolean = false
)
