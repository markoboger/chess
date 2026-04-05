package chess.model

enum Color:
  case White, Black

  def fold[A](white: => A, black: => A): A = this match
    case White => white
    case Black => black

  def opposite: Color = this match
    case White => Black
    case Black => White

enum Role(val whiteSymbol: String, val blackSymbol: String):
  case King extends Role("♔", "♚")
  case Queen extends Role("♕", "♛")
  case Rook extends Role("♖", "♜")
  case Bishop extends Role("♗", "♝")
  case Knight extends Role("♘", "♞")
  case Pawn extends Role("♙", "♟")

  def isPromotable: Boolean = PromotableRole.fromRole(this).isDefined

object Role:
  def all: Vector[Role] = values.toVector

enum PromotableRole:
  case Queen, Rook, Bishop, Knight

  def toRole: Role = this match
    case Queen  => Role.Queen
    case Rook   => Role.Rook
    case Bishop => Role.Bishop
    case Knight => Role.Knight

object PromotableRole:
  def fromRole(role: Role): Option[PromotableRole] = role match
    case Role.Queen  => Some(Queen)
    case Role.Rook   => Some(Rook)
    case Role.Bishop => Some(Bishop)
    case Role.Knight => Some(Knight)
    case _           => None

  val all: Vector[PromotableRole] = values.toVector

final case class Piece(role: Role, color: Color):
  override def toString: String =
    color.fold(role.whiteSymbol, role.blackSymbol)
