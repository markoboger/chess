package chess.controller.io.fen

import chess.model.{Board, CastlingRights, Color, File, Piece, Rank, Role, Square}
import scala.util.Try

final case class FullFenState(
    board: Board,
    whiteToMove: Boolean,
    halfmoveClock: Int,
    fullmoveNumber: Int
)

object FullFen:

  def parse(fen: String): Try[FullFenState] =
    for
      board <- RegexFenParser.parseFEN(fen)
      parts = fen.trim.split("\\s+")
      whiteToMove = parts.lift(1).forall(_.equalsIgnoreCase("w"))
      halfmoveClock = parts.lift(4).flatMap(_.toIntOption).getOrElse(0)
      fullmoveNumber = parts.lift(5).flatMap(_.toIntOption).getOrElse(1)
    yield FullFenState(
      board = board,
      whiteToMove = whiteToMove,
      halfmoveClock = halfmoveClock.max(0),
      fullmoveNumber = fullmoveNumber.max(1)
    )

  def render(
      board: Board,
      whiteToMove: Boolean,
      halfmoveClock: Int,
      fullmoveNumber: Int
  ): String =
    val placement = RegexFenParser.boardToFEN(board)
    val activeColor = if whiteToMove then "w" else "b"
    val castling = renderCastling(board.castlingRights)
    val enPassant = renderEnPassant(board)
    s"$placement $activeColor $castling $enPassant ${halfmoveClock.max(0)} ${fullmoveNumber.max(1)}"

  def openingKey(board: Board, whiteToMove: Boolean): String =
    render(board, whiteToMove, 0, 1).split("\\s+").take(4).mkString(" ")

  def parseCastling(part: String): CastlingRights =
    if part.isEmpty || part == "-" then CastlingRights(false, false, false, false)
    else
      CastlingRights(
        whiteKingside = part.contains("K"),
        whiteQueenside = part.contains("Q"),
        blackKingside = part.contains("k"),
        blackQueenside = part.contains("q")
      )

  def parseEnPassantTarget(part: String, whiteToMove: Boolean): Option[(Square, Square)] =
    if part.isEmpty || part == "-" then None
    else
      Square.fromString(part).flatMap { target =>
        if whiteToMove then
          if target.rank == Rank._6 then Some((Square(target.file, Rank._7), Square(target.file, Rank._5)))
          else None
        else if target.rank == Rank._3 then
          Some((Square(target.file, Rank._2), Square(target.file, Rank._4)))
        else None
      }

  def renderEnPassant(board: Board): String =
    board.lastMove.flatMap { case (from, to) =>
      board.pieceAt(to).collect {
        case Piece(Role.Pawn, color) if (to.rank - from.rank).abs == 2 =>
          val targetRank =
            if color == Color.White then Rank._3
            else Rank._6
          Square(to.file, targetRank).toString
      }
    }.getOrElse("-")

  private def renderCastling(rights: CastlingRights): String =
    val value =
      s"${if rights.whiteKingside then "K" else ""}${if rights.whiteQueenside then "Q" else ""}${if rights.blackKingside then "k" else ""}${if rights.blackQueenside then "q" else ""}"
    if value.isEmpty then "-" else value
