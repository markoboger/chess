package chess.lichess

import chess.model.{Board, MoveResult, PromotableRole, Square}

/** Standard UCI move encoding (e.g. `e2e4`, `e7e8q`) for the Lichess Bot API. */
object Uci:

  def encode(from: Square, to: Square, promotion: Option[PromotableRole]): String =
    val base = s"${from.toString}${to.toString}"
    promotion match
      case None    => base
      case Some(p) => base + (p match
          case PromotableRole.Queen  => "q"
          case PromotableRole.Rook   => "r"
          case PromotableRole.Bishop => "b"
          case PromotableRole.Knight => "n"
        )

  /** Parse a single UCI move (4 or 5 characters). */
  def parseSingle(uci: String): Either[String, (Square, Square, Option[PromotableRole])] =
    val s = uci.trim
    if s.length < 4 then Left(s"UCI too short: $uci")
    else
      val fromS = s.substring(0, 2)
      val toS   = s.substring(2, 4)
      val promo =
        if s.length >= 5 then
          s(4).toLower match
            case 'q' => Right(Some(PromotableRole.Queen))
            case 'r' => Right(Some(PromotableRole.Rook))
            case 'b' => Right(Some(PromotableRole.Bishop))
            case 'n' => Right(Some(PromotableRole.Knight))
            case c   => Left(s"Invalid promotion suffix: $c")
        else Right(None)
      promo.flatMap { pOpt =>
        (Square.fromString(fromS), Square.fromString(toS)) match
          case (Some(from), Some(to)) => Right((from, to, pOpt))
          case _                       => Left(s"Invalid squares in UCI: $uci")
      }

  /** Apply a space-separated list of UCI half-moves from the standard start position. */
  def applyMovesFromStart(movesUci: String): Either[String, Board] =
    val tokens = movesUci.trim.split("\\s+").filter(_.nonEmpty).toList
    tokens.foldLeft[Either[String, Board]](Right(Board.initial)) { (eb, uci) =>
      eb.flatMap { board =>
        parseSingle(uci).flatMap { case (from, to, promo) =>
          board.move(from, to, promo) match
            case MoveResult.Moved(b, _) => Right(b)
            case MoveResult.Failed(_, e) => Left(s"Illegal UCI $uci: ${e.message}")
        }
      }
    }

  /** After `plies` half-moves from the start, which side is on the clock (White moved first from ply 0). */
  def sideToMoveAfterPlies(plies: Int): chess.model.Color =
    if plies % 2 == 0 then chess.model.Color.White else chess.model.Color.Black

end Uci
