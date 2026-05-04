package chess.lichess

import chess.controller.MoveStrategy
import chess.controller.strategy.{
  DeepeningOpeningEndgameStrategy,
  EndgameMinimaxStrategy,
  GreedyStrategy,
  IterativeDeepeningEndgameStrategy,
  IterativeDeepeningStrategy,
  MaterialBalanceStrategy,
  MinimaxStrategy,
  OpeningBookStrategy,
  OpeningContinuationStrategy,
  PieceSquareStrategy,
  QuiescenceStrategy,
  RandomStrategy
}

/** Mirrors [[chess.application.game.GameSessionService]] strategy ids for bot play. */
object BotStrategy:

  def apply(strategyId: String, timeBudgetMs: Long): MoveStrategy =
    strategyId match
      case "random"                       => new RandomStrategy
      case "material-balance"             => new MaterialBalanceStrategy
      case "piece-square"                 => new PieceSquareStrategy
      case "minimax"                      => new MinimaxStrategy
      case "endgame-minimax"              => new EndgameMinimaxStrategy
      case "quiescence"                   => new QuiescenceStrategy
      case "iterative-deepening"          => new IterativeDeepeningStrategy(timeBudgetMs)
      case "iterative-deepening-endgame"  => new IterativeDeepeningEndgameStrategy(timeBudgetMs)
      case "opening-continuation"         =>
        new OpeningContinuationStrategy(fallback = new IterativeDeepeningStrategy(timeBudgetMs))
      case "opening-continuation-endgame" =>
        new OpeningContinuationStrategy(fallback = new IterativeDeepeningEndgameStrategy(timeBudgetMs))
      case "opening-intelligence"         =>
        new OpeningBookStrategy(fallback = new IterativeDeepeningStrategy(timeBudgetMs))
      case "opening-intelligence-endgame" =>
        new OpeningBookStrategy(fallback = new IterativeDeepeningEndgameStrategy(timeBudgetMs))
      case "deepening-opening-endgame"    => new DeepeningOpeningEndgameStrategy(timeLimitMs = timeBudgetMs)
      case _                              => new IterativeDeepeningStrategy(timeBudgetMs)

end BotStrategy
