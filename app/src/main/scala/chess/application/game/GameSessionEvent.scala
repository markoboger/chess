package chess.application.game

import java.time.Instant

/** Event emitted whenever an active game session changes in a way that a realtime consumer may
  * care about.
  */
final case class GameSessionEvent(
    gameId: String,
    eventType: String,
    fen: Option[String],
    pgn: Option[String],
    status: Option[String],
    move: Option[String],
    gameEvent: Option[String],
    occurredAt: Instant
)

