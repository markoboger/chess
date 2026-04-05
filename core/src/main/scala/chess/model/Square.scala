package chess.model

enum File(val index: Int, val letter: Char):
  case A extends File(1, 'a')
  case B extends File(2, 'b')
  case C extends File(3, 'c')
  case D extends File(4, 'd')
  case E extends File(5, 'e')
  case F extends File(6, 'f')
  case G extends File(7, 'g')
  case H extends File(8, 'h')

  def -(other: File): Int = this.index - other.index
  def offset(n: Int): Option[File] = File.fromInt(index + n)

object File:
  def fromInt(i: Int): Option[File] =
    if i >= 1 && i <= 8 then Some(values(i - 1)) else None
  def fromChar(c: Char): Option[File] = fromInt(c.toLower - 'a' + 1)
  val all: Vector[File] = values.toVector

enum Rank(val index: Int):
  case _1 extends Rank(1)
  case _2 extends Rank(2)
  case _3 extends Rank(3)
  case _4 extends Rank(4)
  case _5 extends Rank(5)
  case _6 extends Rank(6)
  case _7 extends Rank(7)
  case _8 extends Rank(8)

  def -(other: Rank): Int = this.index - other.index
  def offset(n: Int): Option[Rank] = Rank.fromInt(index + n)

object Rank:
  def fromInt(i: Int): Option[Rank] =
    if i >= 1 && i <= 8 then Some(values(i - 1)) else None
  val all: Vector[Rank] = values.toVector

final case class Square(file: File, rank: Rank):
  override def toString: String = s"${file.letter}${rank.index}"

object Square:
  /** Convenience factory from algebraic notation, e.g. Square("e4"). Throws on invalid input. */
  def apply(notation: String): Square =
    fromString(notation).getOrElse(
      throw new IllegalArgumentException(s"Invalid square notation: $notation")
    )

  def fromCoords(file: Int, rank: Int): Option[Square] =
    for
      f <- File.fromInt(file)
      r <- Rank.fromInt(rank)
    yield new Square(f, r)

  def fromString(s: String): Option[Square] =
    if s.length == 2 then
      for
        f <- File.fromChar(s(0))
        r <- Rank.fromInt(s(1).asDigit)
      yield new Square(f, r)
    else None

  val all: Vector[Square] =
    for
      r <- Rank.all
      f <- File.all
    yield new Square(f, r)
