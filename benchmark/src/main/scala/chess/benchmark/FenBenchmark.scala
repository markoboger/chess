package chess.benchmark

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import chess.controller.io.fen.{CombinatorFenParser, FastParseFenParser, RegexFenParser}
import chess.model.Board

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
class FenBenchmark:

  // A realistic mid-game position
  val fen = "r1bqkb1r/pp3ppp/2nppn2/8/2BPP3/2N2N2/PP3PPP/R1BQK2R"

  // Parsed once for the save benchmarks
  val board: Board = RegexFenParser.parseFEN(fen).get

  @Benchmark def regexParse       = RegexFenParser.parseFEN(fen)
  @Benchmark def combinatorParse  = CombinatorFenParser.parseFEN(fen)
  @Benchmark def fastparseParse   = FastParseFenParser.parseFEN(fen)

  @Benchmark def regexSave        = RegexFenParser.save(board)
  @Benchmark def combinatorSave   = CombinatorFenParser.save(board)
  @Benchmark def fastparseSave    = FastParseFenParser.save(board)
