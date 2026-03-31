# Lecture 4: REST API with http4s

## Learning Objectives

Students will learn:
- How to expose the chess core engine via HTTP REST endpoints
- JSON serialization with circe
- http4s web framework fundamentals
- CRUD operations for chess games
- HTTP API design principles

## Architecture Overview

This lecture adds a REST API layer on top of the existing chess monolith:

```
┌─────────────────────────┐
│   HTTP REST API         │
│   (http4s + circe)      │
├─────────────────────────┤
│   Game Controller       │
│   (existing)            │
├─────────────────────────┤
│   Chess Model/Engine    │
│   (existing)            │
└─────────────────────────┘
```

## REST Endpoints to Implement

### Game Management
- `POST /games` - Create new game (returns game ID)
  - Request: `{ "startFen": "optional FEN string" }`
  - Response: `{ "gameId": "uuid", "fen": "starting position" }`

- `GET /games/:id` - Get game state
  - Response: `{ "gameId": "uuid", "fen": "current FEN", "pgn": "move history", "status": "in_progress|checkmate|stalemate" }`

- `DELETE /games/:id` - Delete game

### Move Operations
- `POST /games/:id/moves` - Apply move in PGN notation
  - Request: `{ "move": "e4" }`
  - Response: `{ "success": true, "fen": "new position", "event": "check|checkmate|null" }` or error

- `GET /games/:id/moves` - Get move history
  - Response: `{ "moves": ["e4", "e5", ...] }`

### Position Operations
- `GET /games/:id/fen` - Get current FEN position
  - Response: `{ "fen": "position string" }`

- `POST /games/:id/fen` - Load position from FEN
  - Request: `{ "fen": "position string" }`
  - Response: `{ "success": true, "fen": "loaded position" }` or error

## Technical Requirements

### Dependencies (add to build.sbt)
```scala
val http4sVersion = "0.23.23"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-literal" % "0.14.6"
)
```

### Package Structure
```
chess.api/
├── server/
│   ├── ChessServer.scala       # HTTP server setup
│   ├── ChessRoutes.scala       # Route definitions
│   └── GameService.scala       # Business logic layer
├── model/
│   ├── ApiGame.scala           # JSON models
│   ├── ApiMove.scala
│   └── ApiError.scala
└── client/
    └── ChessClient.scala       # Test client
```

## Implementation Steps

1. **JSON Models** - Define case classes for API requests/responses with circe codecs
2. **Game Service** - Bridge between HTTP layer and existing GameController
3. **Routes** - Implement HTTP endpoints using http4s DSL
4. **Server** - Set up Ember server on port 8080
5. **Client** - Build test client for manual testing
6. **Tests** - Unit tests for routes, integration tests with test client

## Testing Strategy

- Unit tests for JSON serialization/deserialization
- Route tests using http4s test utilities
- Integration tests with running server
- Example game scenarios (e.g., Scholar's Mate)

## Success Criteria

- All endpoints functional and returning correct JSON
- Handles invalid moves with proper error responses
- Test coverage >80%
- Client can play a complete game via API
- README with curl examples

## Example Usage

```bash
# Create new game
curl -X POST http://localhost:8080/games

# Make a move
curl -X POST http://localhost:8080/games/{id}/moves \
  -H "Content-Type: application/json" \
  -d '{"move": "e4"}'

# Get current position
curl http://localhost:8080/games/{id}/fen
```

## References

- [http4s documentation](https://http4s.org/)
- [circe documentation](https://circe.github.io/circe/)
- Existing chess core: `chess.model.Board`, `chess.controller.GameController`
