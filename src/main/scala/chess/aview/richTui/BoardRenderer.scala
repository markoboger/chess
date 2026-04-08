package chess.aview.richTui

import chess.model.{Board, Color, File, Rank, Square}

object BoardRenderer:
  private val CellWidth = 5
  private val CellHeight = 2
  val VisibleWidth = 45

  private def squareToken(board: Board, square: Square, selected: Option[Square], content: String): String =
    val squareBg =
      if selected.contains(square) then TerminalPalette.HighlightSquare
      else if (square.file.index + square.rank.index) % 2 == 0 then TerminalPalette.LightSquare
      else TerminalPalette.DarkSquare

    board.pieceAt(square) match
      case Some(piece) if content.nonEmpty =>
        val fg = if piece.color == Color.White then TerminalPalette.WhitePiece else TerminalPalette.BlackPiece
        s"$squareBg$fg${TerminalPalette.Bold}$content${TerminalPalette.Reset}"
      case _ =>
        s"$squareBg$content${TerminalPalette.Reset}"

  private def centered(text: String, width: Int): String =
    val visible = text.take(width)
    val totalPadding = (width - visible.length).max(0)
    val left = totalPadding / 2
    val right = totalPadding - left
    (" " * left) + visible + (" " * right)

  private def pieceGlyph(board: Board, square: Square): String =
    board.pieceAt(square) match
      case Some(piece) => piece.toString
      case None => ""

  private def squareLines(board: Board, square: Square, selected: Option[Square]): Vector[String] =
    val pieceLine =
      if board.pieceAt(square).isDefined then centered(pieceGlyph(board, square), CellWidth)
      else " " * CellWidth

    Vector.tabulate(CellHeight) { row =>
      val content =
        if row == CellHeight / 2 then pieceLine
        else " " * CellWidth
      squareToken(board, square, selected, content)
    }

  def render(
      board: Board,
      flipped: Boolean = false,
      selected: Option[Square] = None
  ): String =
    val ranks = if flipped then Rank.all else Rank.all.reverse
    val files = if flipped then File.all.reverse else File.all
    val fileLabels = files.map(file => centered(file.letter.toString, CellWidth)).mkString("")

    val rows = ranks.flatMap { rank =>
      val renderedSquares = files.map(file => squareLines(board, Square(file, rank), selected))
      Vector.tabulate(CellHeight) { rowIndex =>
        val cells = renderedSquares.map(_(rowIndex)).mkString
        val rankLabel =
          if rowIndex == CellHeight / 2 then s"${rank.index}"
          else " "
        s" $rankLabel $cells $rankLabel"
      }
    }

    val header = s"    $fileLabels"
    (header +: rows :+ header).mkString("\n")
