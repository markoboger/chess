package chess.matchrunner.domain

enum ExperimentStatus:
  case Draft, Running, Completed, Failed

object ExperimentStatus:
  def fromString(value: String): ExperimentStatus =
    values.find(_.toString.equalsIgnoreCase(value)).getOrElse(Failed)
