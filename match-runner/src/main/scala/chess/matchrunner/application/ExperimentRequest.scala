package chess.matchrunner.application

final case class ExperimentRequest(
    name: String,
    description: Option[String],
    whiteStrategy: String,
    blackStrategy: String,
    games: Int,
    startFen: Option[String] = None,
    mirroredPairs: Boolean = false,
    clockInitialMs: Option[Long] = None,
    clockIncrementMs: Option[Long] = None
)
