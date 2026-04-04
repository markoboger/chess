# Lecture 9: Lichess Bot API Integration

## Overview

This lecture demonstrates how to integrate a chess engine with the [Lichess Bot API](https://lichess.org/api#tag/Bot) to create an automated chess bot that can play games on Lichess.org. This integration showcases real-world API consumption, event streaming, and asynchronous programming with Cats Effect and FS2.

## Learning Objectives

- **API Integration**: Learn to consume REST APIs and handle streaming endpoints
- **Functional Effects**: Use Cats Effect `IO` for managing side effects
- **Stream Processing**: Use FS2 for handling Server-Sent Events (SSE) streams
- **HTTP Clients**: Work with http4s for HTTP communication
- **Configuration Management**: Handle API tokens and application configuration
- **Asynchronous Programming**: Coordinate multiple concurrent games

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│                         BotApp                              │
│  ┌───────────────┐         ┌──────────────────────┐       │
│  │ Configuration │────────▶│   EmberClient        │       │
│  │   Loader      │         │   (HTTP/2)           │       │
│  └───────────────┘         └──────────────────────┘       │
│                                     │                       │
│                                     ▼                       │
│                          ┌──────────────────┐              │
│                          │  LichessClient   │              │
│                          │                  │              │
│                          │  - getAccount()  │              │
│                          │  - streamEvents()│              │
│                          │  - streamGame()  │              │
│                          │  - makeMove()    │              │
│                          └──────────────────┘              │
│                                     │                       │
│                                     ▼                       │
│                          ┌──────────────────┐              │
│                          │   BotService     │              │
│                          │                  │              │
│                          │ - Event Loop     │              │
│                          │ - Game Manager   │              │
│                          │ - Move Engine    │              │
│                          └──────────────────┘              │
│                                     │                       │
│                                     ▼                       │
│                          ┌──────────────────┐              │
│                          │ GameController   │              │
│                          │ + MinimaxStrategy│              │
│                          └──────────────────┘              │
└─────────────────────────────────────────────────────────────┘
```

### Package Structure

```
chess.controller.lichess/
├── LichessModels.scala      # Domain models for API data
├── LichessClient.scala      # HTTP client for Lichess API
├── BotService.scala         # Bot orchestration and game management
├── BotConfigLoader.scala    # Configuration loading
├── UciHelper.scala          # UCI notation parser/converter
└── BotApp.scala            # Application entry point
```

## Key Concepts

### 1. Lichess Bot API

The Lichess Bot API allows automated bots to play chess games. Key features:

- **Account Upgrade**: Regular accounts must be upgraded to BOT accounts
- **OAuth Authentication**: Requires API token with bot permissions
- **Event Streaming**: Server-sent events for challenges and game starts
- **Game Streaming**: Real-time game state updates
- **Move Submission**: UCI notation for making moves

### 2. UCI (Universal Chess Interface)

UCI is a standard protocol for chess engines. Move notation examples:

- `e2e4` - pawn from e2 to e4
- `g1f3` - knight from g1 to f3
- `e7e8q` - pawn promotion to queen
- `e1g1` - kingside castling (for white)

Our `UciHelper` provides conversion between UCI and internal `Square` representation.

### 3. Cats Effect IO

`IO[A]` represents a computation that produces a value of type `A` while performing side effects:

```scala
val getProfile: IO[BotProfile] = lichessClient.getAccount
val printProfile: IO[Unit] = getProfile.flatMap(p => IO.println(p.username))
```

Benefits:
- **Referential transparency**: effects are values that can be composed
- **Resource safety**: automatic cleanup with `Resource` and `use`
- **Concurrency**: structured concurrency with fibers
- **Error handling**: explicit error tracking in types

### 4. FS2 Streaming

FS2 provides functional, composable streams for handling continuous data:

```scala
def streamEvents: Stream[IO, String] =
  client.stream(request).flatMap(
    _.body.through(fs2.text.utf8.decode).through(fs2.text.lines)
  )
```

Benefits:
- **Backpressure**: automatic flow control
- **Resource safety**: streams are properly cleaned up
- **Composition**: combine streams functionally
- **Concurrency**: parallel stream processing

## Implementation Details

### Configuration Management

The bot supports multiple configuration methods:

1. **Configuration File**: Plain text file with API token
2. **Environment Variables**:
   - `LICHESS_API_TOKEN` (required)
   - `LICHESS_BASE_URL` (default: https://lichess.org)
   - `LICHESS_ACCEPT_RATED` (default: false)
   - `LICHESS_ACCEPT_CASUAL` (default: true)
   - `LICHESS_ACCEPT_VARIANTS` (default: standard)
   - `LICHESS_MIN_TIME` (default: 60 seconds)
   - `LICHESS_MAX_TIME` (default: 3600 seconds)
   - `LICHESS_MAX_GAMES` (default: 1)

### Event Processing Flow

1. **Event Stream Connection**: Bot connects to `/api/stream/event`
2. **Challenge Received**: Evaluate based on time control, variant, rated/casual
3. **Accept/Decline**: Respond to challenge based on configuration
4. **Game Start**: Begin streaming game state on `/api/bot/game/stream/{gameId}`
5. **Move Calculation**: Use chess engine (Minimax) to select best move
6. **Move Submission**: POST move in UCI notation
7. **Game End**: Clean up and return to event stream

### Error Handling

The bot implements robust error handling:

- **Connection Errors**: Automatic retry with exponential backoff
- **Invalid Moves**: Log error and resign game
- **Rate Limiting**: Respect Lichess API rate limits
- **Graceful Shutdown**: Release all games on exit

## Setup and Usage

### Prerequisites

1. **Lichess Account**: Create account at https://lichess.org
2. **Bot Upgrade**: Upgrade account to BOT at https://lichess.org/api#tag/Bot/operation/botAccountUpgrade
3. **API Token**: Generate token at https://lichess.org/account/oauth/token
   - Required scopes: `bot:play`

### Configuration

Create a config file `lichess-token.txt`:

```
lip_your_api_token_here
```

Or set environment variable:

```bash
export LICHESS_API_TOKEN="lip_your_api_token_here"
```

### Running the Bot

```bash
# With config file
sbt "runMain chess.controller.lichess.BotApp lichess-token.txt"

# With environment variable
export LICHESS_API_TOKEN="lip_your_token"
sbt "runMain chess.controller.lichess.BotApp"
```

### Testing Locally

For development, you can run a local Lichess instance or use the API in "test mode":

```bash
export LICHESS_BASE_URL="http://localhost:9663"
export LICHESS_ACCEPT_CASUAL="true"
export LICHESS_MAX_GAMES="1"
```

## Code Walkthrough

### LichessClient

The client is a pure interface with a single implementation:

```scala
trait LichessClient[F[_]]:
  def getAccount: F[BotProfile]
  def streamEvents: Stream[F, String]
  def streamGame(gameId: String): Stream[F, String]
  def makeMove(gameId: String, move: String): F[Boolean]
  def acceptChallenge(challengeId: String): F[Boolean]
  def declineChallenge(challengeId: String): F[Boolean]
  def resignGame(gameId: String): F[Boolean]
```

Benefits:
- Testable via mocking
- Polymorphic over effect type `F[_]`
- Clear separation of concerns

### BotService

Manages the bot's lifecycle and game coordination:

```scala
trait BotService[F[_]]:
  def start: F[Unit]
  def stop: F[Unit]
```

Key responsibilities:
- Stream incoming events
- Evaluate and respond to challenges
- Manage concurrent games (up to `maxConcurrentGames`)
- Coordinate between Lichess API and chess engine
- Handle errors and reconnections

### UCI Helper

Bidirectional conversion between UCI strings and `Square` objects:

```scala
object UciHelper:
  def parseUciMove(uci: String): Try[(Square, Square, Option[PromotableRole])]
  def moveToUci(from: Square, to: Square, promotion: Option[PromotableRole]): String
  def squareToUci(square: Square): String
```

Fully unit tested with 16 test cases covering:
- Simple moves
- Castling
- Promotions
- Error cases

## Design Patterns

### 1. Tagless Final

The `LichessClient` uses tagless final style with higher-kinded type `F[_]`:

```scala
trait LichessClient[F[_]]:
  def getAccount: F[BotProfile]
```

Benefits:
- Abstract over concrete effect types
- Testable with different interpreters
- Composable with other tagless final libraries

### 2. Resource Management

Http clients are managed safely with `Resource`:

```scala
EmberClientBuilder.default[IO].build.use { httpClient =>
  // Client automatically closed when done
  ...
}
```

### 3. Functional Error Handling

Errors are explicitly tracked in types:

```scala
def parseUciMove(uci: String): Try[(Square, Square, Option[PromotableRole])]
```

No exceptions thrown - errors are values that can be composed.

### 4. Observer Pattern (from earlier lectures)

The `GameController` notifies observers when moves are made, enabling:
- Logging move history
- Updating UI (if present)
- Debugging game state

## Testing

### Unit Tests

- **UciHelperSpec**: 16 tests covering all UCI parsing scenarios
- Round-trip testing: parse → convert → should equal original

### Integration Testing Strategy

For full integration testing:

1. Mock `LichessClient` to simulate API responses
2. Test `BotService` game logic without network calls
3. Use Testcontainers for local Lichess instance
4. End-to-end testing against test account

## Deployment Considerations

### Production Checklist

- [ ] Use production API token (not development token)
- [ ] Configure appropriate time controls
- [ ] Set reasonable `maxConcurrentGames` (1-3 recommended)
- [ ] Implement structured logging
- [ ] Add metrics and monitoring
- [ ] Handle rate limiting (currently 30 requests/min for bots)
- [ ] Graceful shutdown on SIGTERM

### Scaling

For multiple concurrent games:

- Increase `maxConcurrentGames` in configuration
- Ensure sufficient CPU for move calculation
- Consider using faster strategies for shorter time controls
- Monitor memory usage (each game holds move history)

## Further Enhancements

Potential improvements for students:

1. **Opening Book**: Add opening theory for first 10 moves
2. **Endgame Tablebases**: Use Syzygy or similar for perfect endgame play
3. **Time Management**: Allocate more time for complex positions
4. **Learning**: Store and analyze games to improve strategy
5. **Chat**: Respond to opponent chat messages
6. **Multiple Variants**: Support Chess960, Crazyhouse, etc.
7. **Neural Network**: Replace Minimax with neural network evaluation

## References

- **Lichess Bot API**: https://lichess.org/api#tag/Bot
- **UCI Protocol**: https://www.chessprogramming.org/UCI
- **Cats Effect**: https://typelevel.org/cats-effect/
- **FS2**: https://fs2.io/
- **http4s**: https://http4s.org/

## Conclusion

This lecture demonstrates real-world integration with external APIs using functional programming principles. Key takeaways:

- **Functional Effects**: Manage side effects explicitly with `IO`
- **Stream Processing**: Handle continuous data with FS2
- **Resource Safety**: Automatic cleanup prevents resource leaks
- **Composability**: Small, focused components that work together
- **Testability**: Pure functions and mocked interfaces

The Lichess bot showcases how functional programming enables building robust, maintainable systems for real-world applications.
