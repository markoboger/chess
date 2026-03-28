# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
sbt compile

# Run all tests
sbt test

# Run a single test file
sbt "testOnly chess.model.BoardSpec"

# Run with coverage
sbt coverage test coverageReport

# Run the app (Dual UI: ScalaFX GUI + interactive console)
sbt run

# GUI only
sbt "runMain chess.view.ChessGUI"

# Run examples
sbt "runMain chess.controller.parser.PGNExample"
sbt "runMain chess.controller.parser.FENExample"

# Clean build
sbt clean compile
```

## Architecture

### Layers

**Model** (`chess.model`) — pure, immutable chess logic.
- `Board`: 8×8 `Vector[Vector[Option[Piece]]]`, all move validation, en passant, castling, check/checkmate/stalemate detection.
- `MoveResult`: ADT (`Moved(board, gameEvent)` | `Failed(board, error)`) with `flatMap`/`map` for monadic chaining of moves.
- `GameEvent`, `MoveError`: typed events and error reasons.

**Controller** (`chess.controller`) — stateful game management.
- `GameController` extends `Observable[MoveResult]`: holds current `Board`, move history (`boardStates`, `pgnMoves`), turn state, and exposes `applyMove`, `applyPgnMove`, `loadFromFEN`, `loadPgnMoves`, `forward`, `backward`.
- Takes `given FenIO` and `given PgnIO` via context parameters.

**IO / Parsers** (`chess.io`) — trait-based, swappable backends.
- `FileIO` trait: `save(Board): String` / `load(String): Try[Board]`.
- `FenIO extends FileIO` — three implementations: `RegexFenParser` (default), `CombinatorFenParser`, `FastParseFenParser`.
- `PgnIO` — three implementations: `PgnFileIO` (default), `CombinatorPgnParser`, `FastParsePgnParser`.
- Active implementations are wired in `AppBindings.scala` via Scala 3 `given` instances. To swap parsers, edit only that file.

**View** (`chess.view`)
- `ChessGUI`: ScalaFX GUI — click-to-move, PGN input field, FEN load/export, move history navigation.
- `ConsoleView`: terminal ASCII board, implements `Observer[MoveResult]`.
- Both register as observers on `GameController`.

**Entry point** (`ChessApp.scala`) — starts Dual UI mode (GUI + console observer).

### Observer pattern

`Observable[E]` / `Observer[E]` in `chess.util`. `GameController` calls `notifyObservers(result)` after every move, FEN load, or navigation. Views implement `Observer[MoveResult]` and react in `update(event)`.

### Dependency injection

`GameController` requires `given FenIO` and `given PgnIO` in scope. Import `chess.AppBindings.given` (or `chess.AppBindings.*`) where constructing a controller.

### Coverage exclusions

The following are excluded from coverage checks (see `build.sbt`):
`view/*`, `ChessApp`, `FENExample`, `PGNExample`, `FastParseFenParser`, `FastParsePgnParser`, `AppBindings`.
