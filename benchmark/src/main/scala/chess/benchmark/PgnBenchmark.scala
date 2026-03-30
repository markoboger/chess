package chess.benchmark

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import chess.controller.io.pgn.{CombinatorPgnParser, FastParsePgnParser, PgnFileIO}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
class PgnBenchmark:

  // A realistic 10-move game excerpt
  val pgn =
    "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. b4 Bxb4 5. c3 Ba5 " +
      "6. d4 exd4 7. O-O d3 8. Qb3 Qf6 9. e5 Qg6 10. Re1 Nge7"

  // Parsed once for the save benchmarks
  val moves: Vector[String] = PgnFileIO().load(pgn).get

  @Benchmark def fileIOLoad      = PgnFileIO().load(pgn)
  @Benchmark def combinatorLoad  = CombinatorPgnParser.load(pgn)
  @Benchmark def fastparseLoad   = FastParsePgnParser.load(pgn)

  @Benchmark def fileIOSave      = PgnFileIO().save(moves)
  @Benchmark def combinatorSave  = CombinatorPgnParser.save(moves)
  @Benchmark def fastparseSave   = FastParsePgnParser.save(moves)
