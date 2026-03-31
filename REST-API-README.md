# Chess REST API

A RESTful HTTP API for the chess game engine, built with http4s and circe.

## Running the Server

```bash
# Start the server
sbt "runMain chess.api.server.ChessServer"

# The server will run on http://localhost:8080
```

## Running the Example Client

```bash
# Run the example client (plays Scholar's Mate)
sbt "runMain chess.api.client.ChessClientExample"
```

## API Endpoints

### Game Management

#### Create New Game

```bash
# Create a game with default starting position
curl -X POST http://localhost:8080/games \
  -H "Content-Type: application/json" \
  -d '{}'

# Response:
# {"gameId":"550e8400-e29b-41d4-a716-446655440000","fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}
```

```bash
# Create a game with custom starting position (FEN)
curl -X POST http://localhost:8080/games \
  -H "Content-Type: application/json" \
  -d '{"startFen":"8/8/8/8/8/8/8/4K2R w K - 0 1"}'
```

#### Get Game State

```bash
# Get current game state (replace {gameId} with actual game ID)
curl http://localhost:8080/games/{gameId}

# Response:
# {
#   "gameId":"550e8400-e29b-41d4-a716-446655440000",
#   "fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
#   "pgn":"",
#   "status":"White to move"
# }
```

#### Delete Game

```bash
# Delete a game
curl -X DELETE http://localhost:8080/games/{gameId}

# Response: 204 No Content (on success)
```

### Move Operations

#### Make a Move

```bash
# Make a move in PGN algebraic notation
curl -X POST http://localhost:8080/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"e4"}'

# Response:
# {"success":true,"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1","event":null}
```

```bash
# Move resulting in check
curl -X POST http://localhost:8080/games/{gameId}/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"Qh5"}'

# Response may include event: "check", "checkmate", "stalemate", or "threefold_repetition"
```

#### Get Move History

```bash
# Get all moves played in the game
curl http://localhost:8080/games/{gameId}/moves

# Response:
# {"moves":["e4","e5","Nf3","Nc6"]}
```

### Position Operations

#### Get Current FEN

```bash
# Get current position in FEN notation
curl http://localhost:8080/games/{gameId}/fen

# Response:
# {"fen":"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"}
```

#### Load Position from FEN

```bash
# Load a specific position (resets the game)
curl -X POST http://localhost:8080/games/{gameId}/fen \
  -H "Content-Type: application/json" \
  -d '{"fen":"r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"}'

# Response:
# {"success":true,"fen":"r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"}
```

## Complete Example: Scholar's Mate

```bash
# 1. Create a new game
GAME_ID=$(curl -s -X POST http://localhost:8080/games \
  -H "Content-Type: application/json" \
  -d '{}' | jq -r '.gameId')

echo "Game ID: $GAME_ID"

# 2. White plays e4
curl -X POST http://localhost:8080/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"e4"}'

# 3. Black plays e5
curl -X POST http://localhost:8080/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"e5"}'

# 4. White plays Bc4
curl -X POST http://localhost:8080/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"Bc4"}'

# 5. Black plays Nc6
curl -X POST http://localhost:8080/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"Nc6"}'

# 6. White plays Qh5
curl -X POST http://localhost:8080/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"Qh5"}'

# 7. Black plays Nf6
curl -X POST http://localhost:8080/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"Nf6"}'

# 8. White plays Qxf7# - Checkmate!
curl -X POST http://localhost:8080/games/$GAME_ID/moves \
  -H "Content-Type: application/json" \
  -d '{"move":"Qxf7"}'

# Response should show: {"success":true,"fen":"...","event":"checkmate"}

# 9. Get final game state
curl http://localhost:8080/games/$GAME_ID

# Response should show status: "Checkmate! White wins!"
```

## Error Responses

All errors return JSON with an `error` field and optional `details`:

```json
{
  "error": "Game not found"
}
```

```json
{
  "error": "Invalid move",
  "details": "The move e9 is not valid"
}
```

### HTTP Status Codes

- `200 OK` - Successful request
- `204 No Content` - Successful deletion
- `400 Bad Request` - Invalid input (bad FEN, invalid move, etc.)
- `404 Not Found` - Game not found

## Move Notation

Moves use standard PGN algebraic notation:

- Pawn moves: `e4`, `d5`
- Piece moves: `Nf3`, `Bc4`, `Qh5`
- Captures: `Nxe5`, `Qxf7`
- Castling: `O-O` (kingside), `O-O-O` (queenside)
- Promotion: `e8=Q`, `a1=N`
- Check/Checkmate symbols are optional: `Qh5+`, `Qxf7#`

## FEN Notation

FEN (Forsyth-Edwards Notation) represents chess positions:

```
rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
```

Components:
1. Piece placement (rank 8 to rank 1)
2. Active color (`w` or `b`)
3. Castling rights (`KQkq` or combinations)
4. En passant target square (or `-`)
5. Halfmove clock
6. Fullmove number

## Testing

```bash
# Run all tests
sbt test

# Run only API tests
sbt "testOnly chess.api.*"

# Run with coverage
sbt coverage test coverageReport
```

## Architecture

```
┌─────────────────────────┐
│   HTTP REST API         │
│   (http4s + circe)      │
├─────────────────────────┤
│   GameService           │
│   (manages games)       │
├─────────────────────────┤
│   GameController        │
│   (existing)            │
├─────────────────────────┤
│   Chess Model/Engine    │
│   (existing)            │
└─────────────────────────┘
```

## Dependencies

- **http4s 0.23.23** - HTTP server and routing
- **http4s-ember-server** - HTTP server implementation
- **http4s-ember-client** - HTTP client for testing
- **http4s-circe** - JSON integration
- **circe 0.14.10** - JSON serialization/deserialization

## Project Structure

```
chess.api/
├── server/
│   ├── ChessServer.scala       # HTTP server entry point
│   ├── ChessRoutes.scala       # Route definitions
│   └── GameService.scala       # Game management service
├── model/
│   └── ApiModels.scala         # JSON request/response models
└── client/
    └── ChessClient.scala       # HTTP client and example
```
