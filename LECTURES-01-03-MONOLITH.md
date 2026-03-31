# Lectures 1-3: Monolithic Architecture

## Overview

The main branch contains a complete, working chess implementation demonstrating fundamental software architecture patterns. This serves as the baseline for all subsequent architecture explorations.

## Lecture 1: Layered Monolith Basics

### Architecture Principles

**Strict Layering:**
```
┌─────────────────────────────┐
│         View Layer          │  ← User interaction (GUI, Console)
│  chess.view.ChessGUI        │
│  chess.view.ConsoleView     │
├─────────────────────────────┤
│      Controller Layer       │  ← Game management, observers
│  chess.controller.*         │
├─────────────────────────────┤
│        Model Layer          │  ← Pure chess logic
│  chess.model.Board          │
│  chess.model.Piece          │
└─────────────────────────────┘
```

**Dependency Rules:**
- Dependencies flow **downward only**: View → Controller → Model
- Higher layers depend on lower layers
- Lower layers **never** import from higher layers
- This enforces separation of concerns and testability

**Inversion of Control:**
- Model can't call View directly (would violate layering)
- Solution: **Observer pattern**
- Controller extends `Observable[MoveResult]`
- Views implement `Observer[MoveResult]`
- Controller notifies observers when state changes

### Key Classes

**chess.model.Board** (Pure logic, no dependencies)
- Immutable `Vector[Vector[Option[Piece]]]` representation
- All chess rules: legal moves, check, checkmate, stalemate
- Special moves: castling, en passant, promotion
- Returns `MoveResult` ADT (Moved | Failed)

**chess.controller.GameController** (Stateful coordinator)
- Holds current `Board` and move history
- Implements `Observable[MoveResult]`
- Methods: `applyMove`, `applyPgnMove`, `loadFromFEN`, `forward`, `backward`
- Notifies all registered observers on state change

**chess.view.ChessGUI** (ScalaFX UI)
- Click-to-move interface
- PGN input field
- FEN import/export
- Move history navigation
- Implements `Observer[MoveResult]`

**chess.view.ConsoleView** (Text UI)
- ASCII board rendering
- Implements `Observer[MoveResult]`
- Updates automatically via observer pattern

### Learning Outcomes

1. **Separation of Concerns** - Each layer has a single responsibility
2. **Dependency Management** - One-way dependencies prevent tangled code
3. **Observer Pattern** - Solve the "call upward" problem
4. **Immutability** - Model uses immutable data structures for safety
5. **Testability** - Pure model layer is easy to test

### Exploration Tasks

1. Trace a move from GUI click → Controller → Model → Observer update
2. Add a new observer (e.g., logger) without changing existing code
3. Try breaking layering (e.g., import View in Model) - observe compilation errors
4. Write unit tests for `Board` - note how easy it is (no mocking needed)

---

## Lecture 2: Parser Variations

### Problem

Chess has standard notations that need parsing:
- **PGN** (Portable Game Notation): "e4 e5 Nf3 Nc6"
- **FEN** (Forsyth-Edwards Notation): "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

How do we parse these? What are the tradeoffs?

### Three Implementations

**1. Regex Parsers** (`chess.io.RegexFenParser`, `PgnFileIO`)
- **Approach:** Pattern matching with regular expressions
- **Pros:** Simple, no external dependencies, fast for simple formats
- **Cons:** Complex for nested structures, harder to debug, less composable
- **Use case:** FEN is simple enough for regex

**2. Parser Combinators** (`chess.io.CombinatorFenParser`, `CombinatorPgnParser`)
- **Approach:** Build complex parsers from simple combinators
- **Pros:** Highly composable, declarative, mirrors grammar structure
- **Cons:** Can be slower, more overhead
- **Use case:** Great for learning parser theory

**3. FastParse** (`chess.io.FastParseFenParser`, `FastParsePgnParser`)
- **Approach:** Optimized combinator library
- **Pros:** Speed of regex, composability of combinators
- **Cons:** External dependency
- **Use case:** Production use when performance matters

### Interface Pattern

All implement the same trait:
```scala
trait FileIO:
  def save(board: Board): String
  def load(content: String): Try[Board]
```

This allows **swappable implementations** without changing client code.

### Performance Comparison

See `src/main/scala/chess/benchmarks/` for JMH benchmarks:
- FEN parsing: Regex ≈ FastParse > Combinators
- PGN parsing: FastParse > Regex > Combinators

Benchmark results in `benchmarks/results/` directory.

### Learning Outcomes

1. **Interface Segregation** - Define minimal, focused interfaces
2. **Parser Design** - Different approaches to the same problem
3. **Performance Tradeoffs** - Measure, don't guess
4. **Benchmarking** - Use JMH for accurate performance testing

### Exploration Tasks

1. Run benchmarks: `sbt "Jmh/run -i 3 -wi 2 -f1 -t1"`
2. Compare parser implementations side-by-side
3. Implement a new parser (e.g., for UCI notation)
4. Profile memory allocation differences

---

## Lecture 3: Dependency Injection

### Problem

`GameController` needs `FenIO` and `PgnIO` implementations. How do we provide them without hard-coding dependencies?

### Scala 3 Given Instances

**Old approach (bad):**
```scala
class GameController(fenIO: FenIO, pgnIO: PgnIO) { ... }
val controller = new GameController(new RegexFenParser, new PgnFileIO)
```

**Scala 3 approach:**
```scala
class GameController(using FenIO, PgnIO) { ... }

// In AppBindings.scala
given FenIO = RegexFenParser()
given PgnIO = PgnFileIO()

// At use site
import chess.AppBindings.given
val controller = GameController() // Dependencies auto-injected
```

### Benefits

1. **Single Point of Configuration** - All bindings in `AppBindings.scala`
2. **Type-Safe** - Compiler ensures dependencies exist
3. **No Reflection** - Unlike Spring/Guice, zero runtime overhead
4. **Swappable** - Change parser by editing one line
5. **Testable** - Provide test doubles via local `given` instances

### Swapping Implementations

To switch from Regex to FastParse parsers:

**Before:**
```scala
// AppBindings.scala
given FenIO = RegexFenParser()
given PgnIO = PgnFileIO()
```

**After:**
```scala
// AppBindings.scala
given FenIO = FastParseFenParser()
given PgnIO = FastParsePgnParser()
```

That's it. All code using `GameController` now uses the new parsers.

### Testing Pattern

```scala
class GameControllerSpec extends AnyFlatSpec:
  given FenIO = MockFenParser()  // Test double
  given PgnIO = MockPgnParser()  // Test double

  val controller = GameController()
  // Tests use mocks, production uses real implementations
```

### Learning Outcomes

1. **Dependency Inversion Principle** - Depend on abstractions, not concretions
2. **Compile-Time DI** - Type safety without runtime cost
3. **Context Parameters** - Scala 3's elegant solution to DI
4. **Configuration Management** - Centralized, explicit wiring

### Exploration Tasks

1. Change parser implementations in `AppBindings.scala`
2. Add a new dependency (e.g., game logger)
3. Create a test configuration with mocks
4. Trace how `given` instances are resolved

---

## Current Implementation Status

### What Works

✅ **Fully functional chess engine**
- All standard rules (castling, en passant, promotion)
- Check and checkmate detection
- Move validation
- PGN/FEN import/export

✅ **Multiple UIs**
- ScalaFX graphical interface
- Console text interface
- Dual mode (both simultaneously)

✅ **Computer vs Computer mode**
- Random move AI
- Self-play capability
- Move history with undo/redo

✅ **Comprehensive tests**
- 93 test cases
- High coverage (see `build.sbt` for exclusions)
- Run with `sbt test`

### Code Statistics

```bash
# Lines of code
src/main/scala/        ~2500 lines
src/test/scala/        ~1500 lines

# Test coverage
sbt clean coverage test coverageReport
# View: target/scala-3.5.0/scoverage-report/index.html
```

### Running the Application

```bash
# GUI + Console (dual mode)
sbt run

# GUI only
sbt "runMain chess.view.ChessGUI"

# Example programs
sbt "runMain chess.controller.parser.PGNExample"
sbt "runMain chess.controller.parser.FENExample"
```

---

## Teaching Notes

### Lecture Sequence

1. **Lecture 1** (2 hours)
   - Present layered architecture diagram
   - Live trace of a move through all layers
   - Observer pattern explanation
   - Students add a simple observer

2. **Lecture 2** (2 hours)
   - Compare three parser implementations
   - Run benchmarks together
   - Discuss when to use each approach
   - Students implement simple parser

3. **Lecture 3** (1.5 hours)
   - Demonstrate swapping implementations
   - Show `AppBindings.scala` pattern
   - Compare to other DI frameworks
   - Students create test configuration

### Assessment Ideas

- **Lab 1:** Add a new observer (e.g., move logger to file)
- **Lab 2:** Implement UCI notation parser
- **Lab 3:** Create configuration for engine-only mode (no UI)

### Common Student Questions

**Q: Why not just use Spring/Guice for DI?**
A: Scala 3's `given` is compile-time, zero-overhead, and type-safe. No reflection, no XML, no annotations.

**Q: Why immutable Board instead of mutable state?**
A: Immutability enables safe undo/redo, easy testing, and prevents bugs from shared mutable state.

**Q: Could we violate layering and have Model call View directly?**
A: You can't - the compiler prevents it. That's the power of one-way dependencies.

---

## Next Steps

After mastering the monolith:
- **Lecture 4:** Add REST API layer (http4s)
- **Lecture 5:** Alternative REST implementation (fs2)
- **Lecture 6+:** Microservices, databases, distributed systems

The monolith is the foundation. Master it before moving to distributed architectures.
