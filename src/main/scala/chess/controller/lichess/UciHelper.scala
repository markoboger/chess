package chess.controller.lichess

import chess.model.{Square, File, Rank, PromotableRole}

import scala.util.{Try, Success, Failure}

/** Helper for converting between UCI notation and internal move representation */
object UciHelper:

  /** Parse a UCI move string (e.g., "e2e4", "e7e8q") into from/to squares and optional promotion
    * @param uci
    *   The UCI move string
    * @return
    *   A tuple of (from square, to square, optional promotion)
    */
  def parseUciMove(uci: String): Try[(Square, Square, Option[PromotableRole])] =
    Try {
      if uci.length < 4 then throw new IllegalArgumentException(s"Invalid UCI move: $uci")

      val fromFile = parseFile(uci(0))
      val fromRank = parseRank(uci(1))
      val toFile = parseFile(uci(2))
      val toRank = parseRank(uci(3))

      val promotion = if uci.length >= 5 then Some(parsePromotion(uci(4))) else None

      val from = Square(fromFile, fromRank)
      val to = Square(toFile, toRank)

      (from, to, promotion)
    }

  /** Convert a Square to UCI notation (e.g., Square(File.E, Rank._2) -> "e2")
    * @param square
    *   The square to convert
    * @return
    *   UCI notation string
    */
  def squareToUci(square: Square): String =
    s"${fileToChar(square.file)}${rankToChar(square.rank)}"

  /** Convert a move to UCI notation (e.g., (e2, e4) -> "e2e4")
    * @param from
    *   Starting square
    * @param to
    *   Destination square
    * @param promotion
    *   Optional promotion piece
    * @return
    *   UCI notation string
    */
  def moveToUci(from: Square, to: Square, promotion: Option[PromotableRole] = None): String =
    val base = s"${squareToUci(from)}${squareToUci(to)}"
    promotion match
      case Some(role) => base + promotionToChar(role)
      case None       => base

  private def parseFile(c: Char): File =
    c.toLower match
      case 'a' => File.A
      case 'b' => File.B
      case 'c' => File.C
      case 'd' => File.D
      case 'e' => File.E
      case 'f' => File.F
      case 'g' => File.G
      case 'h' => File.H
      case _   => throw new IllegalArgumentException(s"Invalid file: $c")

  private def parseRank(c: Char): Rank =
    c match
      case '1' => Rank._1
      case '2' => Rank._2
      case '3' => Rank._3
      case '4' => Rank._4
      case '5' => Rank._5
      case '6' => Rank._6
      case '7' => Rank._7
      case '8' => Rank._8
      case _   => throw new IllegalArgumentException(s"Invalid rank: $c")

  private def parsePromotion(c: Char): PromotableRole =
    c.toLower match
      case 'q' => PromotableRole.Queen
      case 'r' => PromotableRole.Rook
      case 'b' => PromotableRole.Bishop
      case 'n' => PromotableRole.Knight
      case _   => throw new IllegalArgumentException(s"Invalid promotion: $c")

  private def fileToChar(file: File): Char =
    file match
      case File.A => 'a'
      case File.B => 'b'
      case File.C => 'c'
      case File.D => 'd'
      case File.E => 'e'
      case File.F => 'f'
      case File.G => 'g'
      case File.H => 'h'

  private def rankToChar(rank: Rank): Char =
    rank match
      case Rank._1 => '1'
      case Rank._2 => '2'
      case Rank._3 => '3'
      case Rank._4 => '4'
      case Rank._5 => '5'
      case Rank._6 => '6'
      case Rank._7 => '7'
      case Rank._8 => '8'

  private def promotionToChar(role: PromotableRole): Char =
    role match
      case PromotableRole.Queen  => 'q'
      case PromotableRole.Rook   => 'r'
      case PromotableRole.Bishop => 'b'
      case PromotableRole.Knight => 'n'
