package chess.model

case class Puzzle(
    id: String,
    fen: String,
    moves: List[String],
    rating: Int,
    ratingDeviation: Int,
    popularity: Int,
    nbPlays: Int,
    themes: List[String],
    gameUrl: String,
    openingTags: List[String]
)
