# Chess Application

[![CI](https://github.com/markoboger/chess/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/markoboger/chess/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/markoboger/chess/badge.svg?branch=main)](https://coveralls.io/github/markoboger/chess?branch=main)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=markoboger_chess&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=markoboger_chess)

A complete, production-ready chess application built in Scala with **multiple user interfaces** (ScalaFX GUI, Console, Dual UI), comprehensive move validation, en passant support, and full PGN/FEN notation support.

## Quick Start

### Run the Chess Game

#### Option 1: Use the Shell Script (Recommended for macOS)
```bash
./run-chess.sh
```

**This starts Dual UI Mode:**
- ✅ **ScalaFX GUI** - Interactive window for playing
- ✅ **Console Observer** - Terminal displays all moves in real-time
- Make moves in the GUI, see them instantly in the console!

#### Option 2: Use sbt directly (Interactive Console)
```bash
sbt run
```

**This also starts Dual UI Mode with:**
- ✅ **ScalaFX GUI** - Click pieces or type PGN moves
- ✅ **Interactive Console** - Type moves directly in terminal
- Moves from either UI update the other instantly!

**macOS Users:** The GUI window may not come to foreground automatically. Check:
- **Dock** (bottom of screen) - Click the Java/Chess icon
- **Mission Control** (F3) - Look for the Chess window
- **App Switcher** (⌘ Cmd + Tab) - Select the Java application

**Features:**
- ✅ Click-to-move or enter PGN notation
- ✅ Real-time synchronization between GUI and Console
- ✅ Load/Export FEN positions
- ✅ Comprehensive move validation

### Alternative Launch Methods

```bash
# GUI Only (recommended on macOS)
sbt "runMain chess.view.ChessGUI"

# Run Examples
sbt "runMain chess.example.PGNExample"  # See PGN notation examples
sbt "runMain chess.example.FENExample"  # See FEN notation examples
```

## Features

### ✅ Complete Chess Engine
- Full board representation with comprehensive move validation
- All piece types: King, Queen, Rook, Bishop, Knight, Pawn
- Special moves: Castling, En Passant
- Illegal move detection with clear error messages
- Turn management and game state

### ✅ Multiple User Interfaces
- **ScalaFX GUI**: Modern, idiomatic Scala graphical interface
- **Console**: Text-based terminal application
- **Dual UI**: ScalaFX + Console running simultaneously with shared state (default mode)

### ✅ Notation Support
- **PGN (Portable Game Notation)**: Standard chess notation
  - Pawn moves: `e4`, `e5`
  - Piece moves: `Nf3`, `Bc4`, `Qh5`
  - Captures: `exd5`, `Nxe5`
  - Castling: `O-O`, `O-O-O`
  - Check/Mate: `Qh5+`, `Qh5#`

- **FEN (Forsyth-Edwards Notation)**: Position encoding
  - Load any chess position
  - Export current position
  - Full round-trip support

### ✅ Comprehensive Testing
- **93 tests** covering all functionality
- Unit tests for board logic, pieces, and moves
- Integration tests for PGN/FEN parsing
- Edge case handling
- 100% pass rate

## Project Structure

```
chess/
├── src/main/scala/chess/
│   ├── model/
│   │   ├── Piece.scala          # Piece types and colors
│   │   └── Board.scala          # Board state and logic
│   ├── controller/
│   │   └── GameController.scala # Game management
│   ├── util/
│   │   ├── Observable.scala     # Observer pattern
│   │   ├── PGNParser.scala      # PGN parsing
│   │   └── FENParser.scala      # FEN parsing
│   ├── view/
│   │   ├── ChessGUI.scala       # ScalaFX GUI
│   │   ├── ChessView.scala      # View interface
│   │   └── ConsoleView.scala    # Console UI
│   ├── example/
│   │   ├── PGNExample.scala     # PGN examples
│   │   └── FENExample.scala     # FEN examples
│   └── ChessApp.scala           # Main app (Dual UI)
├── src/test/scala/chess/        # 93 comprehensive tests
├── build.sbt                    # Build configuration
└── README.md                    # This file
```

## Technology Stack

- **Language**: Scala 3.5.0
- **Build Tool**: SBT 1.9.8
- **Testing**: ScalaTest 3.2.19
- **GUI Framework**: ScalaFX 21.0.0-R32 (includes JavaFX 21)
- **Runtime**: Java 17+

## Running Tests

```bash
sbt test
```

## Quality Checks

The project is set up to support automated quality checks from the start:

- **GitHub Actions** for build and test automation
- **scoverage** for Scala coverage reports
- **Coveralls** for coverage history and pull request visibility
- **SonarQube** for static analysis and maintainability review

Useful local commands:

```bash
# Compile and run tests
sbt clean compile test

# Generate aggregated coverage reports
sbt clean coverage test coverageAggregate
```

GitHub Actions uses:

- [.github/workflows/ci.yml](/Users/markoboger/workspace/chess/.github/workflows/ci.yml)
- [sonar-project.properties](/Users/markoboger/workspace/chess/sonar-project.properties)

The live SonarQube badge URL depends on the concrete Sonar host and project endpoint.
Add that badge once the public Sonar dashboard URL is known for this repository.

Required GitHub secrets for SonarQube:

- `SONAR_TOKEN`
- `SONAR_HOST_URL`

Coveralls is uploaded through `sbt-coveralls` in CI.

**Expected Output:**
```
[info] Total number of tests run: 93
[info] Tests: succeeded 93, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

## ScalaFX GUI Features

The **ScalaFX GUI** provides a modern, interactive chess experience:

```bash
sbt run
```

**Features:**
- ✅ Beautiful visual chess board with piece sprites
- ✅ Click-to-move: Click a piece, then click destination
- ✅ PGN input field: Type moves like "e4", "Nf3", "O-O"
- ✅ FEN support: Load any position from FEN notation
- ✅ Export FEN: Get current position as FEN string
- ✅ Move validation: Invalid moves are rejected with clear error messages
- ✅ Turn indicator: Always shows whose turn it is
- ✅ New Game button: Reset to starting position anytime

## Move Validation

All moves are validated against official chess rules:

### Valid Moves
- **Pawn**: 1-2 squares forward from start, diagonal captures only
- **Knight**: L-shaped movement (2+1 or 1+2 squares)
- **Bishop**: Diagonal movement, path must be clear
- **Rook**: Horizontal/vertical movement, path must be clear
- **Queen**: Rook or bishop patterns, path must be clear
- **King**: 1 square in any direction

### Illegal Moves Rejected
- Pawns moving backwards or 3+ squares
- Pieces moving off the board
- Capturing own pieces
- Pieces jumping over others (except knights)
- Invalid piece-specific movements

### Special Rules
- **En Passant**: Pawn captures opponent pawn moving 2 squares
- **Castling**: King and rook movement (basic support)

## Usage Examples

### Making Moves (ScalaFX GUI)

1. **PGN Input**: Type "e4" and press Enter
2. **Click-Based**: Click a piece, then click destination
3. **FEN Loading**: Paste FEN string and click "Load FEN"

### Making Moves (Console)

```
White's move (PGN: e4, Nf3, O-O or coordinates: e2e4, or 'quit' to exit): e4
Black's move (PGN: e5, Nf6, O-O or coordinates: e7e5, or 'quit' to exit): e5
```

### Code Examples

```scala
val controller = new GameController(Board.initial)

// Apply PGN move
controller.applyPgnMove("e4") match {
  case Right(board) => println("Move successful")
  case Left(error) => println(s"Error: $error")
}

// Load FEN position
val fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR"
controller.loadFromFEN(fen) match {
  case Right(board) => println("Position loaded")
  case Left(error) => println(s"Error: $error")
}

// Export FEN
val currentFEN = controller.getBoardAsFEN
println(currentFEN)
```

## UI Comparison

| Feature | ScalaFX | Console |
|---------|---------|---------|
| Visual Board | ✅ | ✅ (ASCII) |
| Click Moves | ✅ | ❌ |
| PGN Input | ✅ | ✅ |
| FEN Support | ✅ | ❌ |
| Idiomatic Scala | ✅ | ✅ |
| Modern Look | ✅ | N/A |
| Platform Support | All | All |

## Common Moves

```
e4              # Pawn to e4
Nf3             # Knight to f3
Bc4             # Bishop to c4
Qh5             # Queen to h5
O-O             # Kingside castling
O-O-O           # Queenside castling
exd5            # Pawn captures on d5
Qh5+            # Queen to h5 with check
Qh5#            # Queen to h5 with checkmate
```

## Architecture Highlights

### Clean Separation of Concerns
- **Model**: Board state and piece logic
- **Controller**: Game management and move validation
- **View**: Multiple UI implementations
- **Util**: Parsers and utilities

### Design Patterns
- **Observer Pattern**: Decoupled board updates
- **Factory Pattern**: Piece creation
- **Strategy Pattern**: Different UI implementations

### Error Handling
- Comprehensive error messages
- Graceful failure handling
- User-friendly error dialogs

## Troubleshooting

### GUI Won't Display
- Ensure Java 17+ is installed
- ScalaFX requires JavaFX runtime (included in dependencies)
- Try Console mode if GUI issues persist
- Check system display settings

### Moves Not Working
- Verify PGN notation is correct
- Check it's the correct player's turn
- Try a simple move like "e4" first

### Tests Failing
- Ensure all dependencies are installed: `sbt update`
- Clean build: `sbt clean compile test`
- Check Java version: `java -version`

## Test Coverage

| Component | Tests | Status |
|-----------|-------|--------|
| Piece Logic | 4 | ✅ |
| Board State | 6 | ✅ |
| Illegal Moves | 17 | ✅ |
| En Passant | 6 | ✅ |
| Game Controller | 14 | ✅ |
| PGN Parser | 28 | ✅ |
| FEN Parser | 13 | ✅ |
| Observer Pattern | 2 | ✅ |
| Console View | 3 | ✅ |
| **TOTAL** | **93** | **✅** |

## Code Quality

✅ **93 comprehensive tests** - 100% pass rate
✅ **Clean architecture** - Well-organized code
✅ **Functional programming** - Immutable data structures
✅ **Error handling** - Comprehensive validation
✅ **Move validation** - All chess rules enforced
✅ **Documentation** - Extensive comments and guides

## Architecture

### Design Patterns
- **Observer Pattern**: Board updates notify UI observers
- **Factory Pattern**: Piece creation
- **Strategy Pattern**: Different UI implementations
- **MVC Pattern**: Model-View-Controller separation

### Key Components
- **Board**: Immutable board state with move validation
- **GameController**: Game management and move application
- **PGNParser**: Portable Game Notation parsing
- **FENParser**: Forsyth-Edwards Notation parsing
- **Observable**: Observer pattern for UI synchronization

## Future Enhancements

- Move history and PGN export
- Undo/Redo functionality
- Checkmate/Stalemate detection
- Piece promotion dialog
- Time controls for timed games
- Move suggestions and analysis
- Save/Load game functionality
- Network multiplayer
- Chess engine AI opponent

## License

This project is provided as-is for educational and personal use.

## Enjoy! ♟️

Choose your favorite UI and start playing chess! 🎉
