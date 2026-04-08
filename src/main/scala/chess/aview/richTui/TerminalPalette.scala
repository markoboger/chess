package chess.aview.richTui

private[richTui] object TerminalPalette:
  val Reset = "\u001b[0m"
  val Bold = "\u001b[1m"
  val Dim = "\u001b[2m"
  val Reverse = "\u001b[7m"

  val WhitePiece = "\u001b[97m"
  val BlackPiece = "\u001b[30m"
  val LightSquare = "\u001b[48;5;230m"
  val DarkSquare = "\u001b[48;5;101m"
  val HighlightSquare = "\u001b[48;5;45m"
  val Accent = "\u001b[38;5;45m"
  val Warning = "\u001b[38;5;214m"
  val Error = "\u001b[38;5;196m"
  val Success = "\u001b[38;5;40m"
  val Muted = "\u001b[38;5;245m"

  def colorize(text: String, color: String): String =
    s"$color$text$Reset"
