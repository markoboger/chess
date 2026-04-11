package chess.matchrunner.domain

enum MatchResult:
  case WhiteWin, BlackWin, Draw

object MatchResult:
  def fromString(value: String): MatchResult =
    values.find(_.toString.equalsIgnoreCase(value)).getOrElse(Draw)
