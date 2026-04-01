package chess.microservices.persistence.domain

/** Domain model for a chess opening
  *
  * Represents a standard chess opening from the ECO (Encyclopaedia of Chess Openings) classification.
  *
  * @param eco
  *   ECO code (e.g., "C00", "E60")
  * @param name
  *   Human-readable name of the opening
  * @param moves
  *   Opening moves in PGN notation (e.g., "1. e4 e5 2. Nf3")
  * @param fen
  *   Resulting FEN position after the opening moves
  * @param variation
  *   Specific variation name if applicable
  */
case class Opening(
    eco: String,
    name: String,
    moves: String,
    fen: String,
    variation: Option[String] = None
)

object Opening:
  /** Parse opening from a simple format: ECO|Name|Moves|Variation (optional)
    *
    * Example: "C00|French Defense|1. e4 e6|"
    */
  def parse(line: String): Option[Opening] =
    line.split('|').toList match
      case eco :: name :: moves :: rest =>
        val variation = rest.headOption.filter(_.nonEmpty)
        // For now, we'll compute FEN separately or leave it empty
        // In production, you'd want to compute actual FEN from moves
        Some(Opening(eco.trim, name.trim, moves.trim, "", variation))
      case _ => None

  /** Create an opening with computed FEN
    *
    * This would use the chess engine to compute the FEN from the moves. For now, we accept it as a parameter.
    */
  def withFen(eco: String, name: String, moves: String, fen: String, variation: Option[String] = None): Opening =
    Opening(eco, name, moves, fen, variation)
