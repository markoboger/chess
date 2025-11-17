package chess.model

enum PieceType(val whiteSymbol: String, val blackSymbol: String):
  case King   extends PieceType("♔", "♚")
  case Queen  extends PieceType("♕", "♛")
  case Rook   extends PieceType("♖", "♜")
  case Bishop extends PieceType("♗", "♝")
  case Knight extends PieceType("♘", "♞")
  case Pawn   extends PieceType("♙", "♟")

enum PieceColor:
  case White, Black

final case class Piece(pieceType: PieceType, color: PieceColor):
  override def toString: String =
    if color == PieceColor.White then pieceType.whiteSymbol
    else pieceType.blackSymbol
