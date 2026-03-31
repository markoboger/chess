# REST API Comparison: http4s vs Pekko HTTP

This document compares two implementations of the same Chess REST API:
- **Lecture 4**: http4s with Cats Effect and fs2
- **Lecture 5**: Pekko HTTP with Pekko Streams and Futures

## 1. Programming Model Comparison

### http4s (Lecture 4): Effect-Based with Cats Effect IO

```scala
def createGame(startFen: Option[String] = None): IO[Either[String, (String, String)]] =
  startFen match
    case Some(fen) =>
      fenIO.load(fen) match
        case scala.util.Success(board) =>
          val gameId = UUID.randomUUID().toString
          val controller = GameController(board)
          val fenStr = controller.getBoardAsFEN
          games.update(_.updated(gameId, controller)).map(_ => Right((gameId, fenStr)))
        case scala.util.Failure(e) =>
          IO.pure(Left(s"Invalid FEN: ${e.getMessage}"))
    case None =>
      val gameId = UUID.randomUUID().toString
      val controller = GameController(Board.initial)
      val fen = controller.getBoardAsFEN
      games.update(_.updated(gameId, controller)).map(_ => Right((gameId, fen)))
```

**Key Characteristics:**
- **Effect type**: `IO[A]` - describes a computation, executed later
- **State management**: `Ref[IO, Map[String, GameController]]` - purely functional, thread-safe
- **Composition**: `flatMap`, `map`, `*>` for sequencing effects
- **Error handling**: `IO.pure(Left(...))` for errors within the effect
- **Execution**: Lazy - `IO` values are descriptions of work, run with `.unsafeRunSync()`

### Pekko HTTP (Lecture 5): Future-Based with Scala Futures

```scala
def createGame(startFen: Option[String] = None): Future[Either[String, (String, String)]] =
  Future {
    startFen match
      case Some(fen) =>
        fenIO.load(fen) match
          case scala.util.Success(board) =>
            val gameId = UUID.randomUUID().toString
            val controller = GameController(board)
            val fenStr = controller.getBoardAsFEN
            games.put(gameId, controller)
            Right((gameId, fenStr))
          case scala.util.Failure(e) =>
            Left(s"Invalid FEN: ${e.getMessage}")
      case None =>
        val gameId = UUID.randomUUID().toString
        val controller = GameController(Board.initial)
        val fen = controller.getBoardAsFEN
        games.put(gameId, controller)
        Right((gameId, fen))
  }
```

**Key Characteristics:**
- **Effect type**: `Future[A]` - computation starts immediately when created
- **State management**: `ConcurrentHashMap[String, GameController]` - imperative, thread-safe
- **Composition**: `map`, `flatMap`, `foreach` for transforming futures
- **Error handling**: `Future.failed(...)` or `Try` within futures
- **Execution**: Eager - `Future` starts executing as soon as it's created

## 2. Route Definition Comparison

### http4s Routes

```scala
HttpRoutes.of[IO] {
  // POST /games - Create new game
  case req @ POST -> Root / "games" =>
    req.asJsonDecode[CreateGameRequest].flatMap { request =>
      gameService.createGame(request.startFen).flatMap {
        case Right((gameId, fen)) =>
          Ok(CreateGameResponse(gameId, fen))
        case Left(error) =>
          BadRequest(ErrorResponse(error))
      }
    }

  // GET /games/:id - Get game state
  case GET -> Root / "games" / gameId =>
    gameService.getGameState(gameId).flatMap {
      case Some((fen, pgn, status)) =>
        Ok(GameStateResponse(gameId, fen, pgn, status))
      case None =>
        NotFound(ErrorResponse("Game not found"))
    }
}
```

**Characteristics:**
- Pattern matching on HTTP method and path
- Extraction via `->` operator and `/` path segments
- Request body parsing with `.asJsonDecode[T]`
- Response construction with status methods (`Ok`, `BadRequest`, etc.)
- Flat, declarative style

### Pekko HTTP Routes

```scala
pathPrefix("games") {
  concat(
    // POST /games - Create new game
    pathEnd {
      post {
        entity(as[CreateGameRequest]) { request =>
          onSuccess(gameService.createGame(request.startFen)) {
            case Right((gameId, fen)) =>
              complete(StatusCodes.OK, CreateGameResponse(gameId, fen))
            case Left(error) =>
              complete(StatusCodes.BadRequest, ErrorResponse(error))
          }
        }
      }
    },
    // GET /games/:id - Get game state
    path(Segment) { gameId =>
      get {
        onSuccess(gameService.getGameState(gameId)) {
          case Some((fen, pgn, status)) =>
            complete(StatusCodes.OK, GameStateResponse(gameId, fen, pgn, status))
          case None =>
            complete(StatusCodes.NotFound, ErrorResponse("Game not found"))
        }
      }
    }
  )
}
```

**Characteristics:**
- Directives-based DSL (`pathPrefix`, `path`, `get`, `post`)
- Nested structure with `concat` for combining routes
- Path segments extracted with `Segment`
- Request body parsing with `entity(as[T])`
- `onSuccess` directive for handling futures
- `complete` for response construction

## 3. Streaming Model Comparison

### fs2 (http4s)

- **Core abstraction**: `Stream[F[_], A]` - effectful streams parameterized by effect type
- **Backpressure**: Built-in, composable
- **Operators**: `map`, `flatMap`, `filter`, `compile`, `drain`
- **Resource management**: `Resource[F, A]` for safe resource handling
- **Pull-based**: Consumers drive the stream

### Pekko Streams (Pekko HTTP)

- **Core abstractions**: `Source[A, Mat]`, `Flow[A, B, Mat]`, `Sink[A, Mat]`
- **Backpressure**: Built-in reactive streams protocol
- **Operators**: `map`, `filter`, `fold`, `runWith`, materialization
- **Resource management**: Graph-based lifecycle, automatic cleanup
- **Push-pull**: Hybrid model with dynamic push/pull

## 4. Error Handling

### http4s

```scala
case req @ POST -> Root / "games" / gameId / "moves" =>
  req.asJsonDecode[MakeMoveRequest].flatMap { request =>
    gameService.makeMove(gameId, request.move).flatMap {
      case Right((fen, event)) =>
        Ok(MakeMoveResponse(success = true, fen, event))
      case Left(error) =>
        BadRequest(ErrorResponse(error))
    }
  }
```

- Errors within `IO`: handled with `attempt`, `handleErrorWith`, `recover`
- HTTP errors: explicit status codes with typed responses
- Composable error handling through effect type

### Pekko HTTP

```scala
post {
  entity(as[MakeMoveRequest]) { request =>
    onSuccess(gameService.makeMove(gameId, request.move)) {
      case Right((fen, event)) =>
        complete(StatusCodes.OK, MakeMoveResponse(success = true, fen, event))
      case Left(error) =>
        complete(StatusCodes.BadRequest, ErrorResponse(error))
    }
  }
}
```

- Future failures: handled with `onComplete`, `recover`, `recoverWith`
- HTTP errors: explicit status codes via `StatusCodes`
- Exception directives: `handleExceptions`, `handleRejections`

## 5. Testing Comparison

### http4s Tests

```scala
val request = Request[IO](Method.POST, uri"/games")
  .withEntity(CreateGameRequest(None))

val response = routes.run(request).unsafeRunSync()
response.status shouldBe Status.Ok

val createResp = response.as[CreateGameResponse].unsafeRunSync()
createResp.gameId should not be empty
```

**Characteristics:**
- Create `Request[IO]` with method, URI, entity
- Run through routes with `.run(request)`
- Execute effect with `.unsafeRunSync()`
- Parse response body with `.as[T]`

### Pekko HTTP Tests

```scala
Post("/games", jsonEntity(CreateGameRequest(None))) ~> routes ~> check {
  status shouldBe StatusCodes.OK
  val response = parseJson[CreateGameResponse](responseAs[String])
  response.gameId should not be empty
}
```

**Characteristics:**
- ScalatestRouteTest trait provides `~>` operator
- Fluent DSL for testing (`Post`, `Get`, etc.)
- `check` block for assertions
- Direct access to `status`, `responseAs[T]`

## 6. Performance Characteristics

### Expected Performance Profile

**http4s with fs2:**
- Pure functional overhead minimal with modern JIT
- Effect suspension adds indirection but enables referential transparency
- Excellent for concurrent scenarios due to Cats Effect fiber model
- Resource safety guarantees may add small overhead

**Pekko HTTP with Futures:**
- Direct execution, lower abstraction overhead
- Mature, battle-tested in production at scale
- Excellent connection pooling and HTTP/2 support
- Akka Streams provides efficient backpressure

**Note**: Detailed JMH benchmarks would provide concrete measurements. Both frameworks are production-ready and performance differences are likely negligible for most use cases.

## 7. Developer Experience

### Learning Curve

**http4s:**
- Requires understanding of Cats Effect, `IO`, effects
- Pure FP concepts (referential transparency, effect suspension)
- Composable but abstract
- Excellent for teams already using Typelevel stack

**Pekko HTTP:**
- Familiar Future-based model
- Directives DSL has a learning curve
- More imperative style, easier for newcomers
- Akka/Pekko ecosystem maturity

### Code Complexity

**Lines of Code (approximate):**
- http4s GameService: ~100 lines
- Pekko HTTP GameService: ~95 lines
- http4s Routes: ~85 lines
- Pekko HTTP Routes: ~110 lines

Both implementations are comparable in complexity. Pekko HTTP routes are slightly more verbose due to nested directives structure.

## 8. When to Use Each

### Choose http4s when:
- You want pure functional programming with Cats Effect
- You need effect tracking and referential transparency
- Your team is familiar with Typelevel ecosystem (Cats, fs2)
- You want composable, type-safe effects
- You value testability through pure functions

### Choose Pekko HTTP when:
- You want proven, battle-tested production framework
- Your team prefers Future-based programming
- You're already using Akka/Pekko actors
- You need extensive HTTP features (HTTP/2, WebSocket, etc.)
- You value mature ecosystem and extensive documentation

## 9. Recommendation

**Both frameworks are excellent choices.** The decision comes down to:

1. **Team expertise**: Choose what your team knows
2. **Ecosystem**: http4s integrates with Typelevel, Pekko HTTP with Akka ecosystem
3. **Programming philosophy**: Pure FP (http4s) vs pragmatic functional (Pekko)
4. **Project requirements**: Consider existing dependencies and architectural patterns

For **educational purposes**, studying both provides valuable insights into:
- Different approaches to effect management
- Trade-offs between abstraction and directness
- Functional programming paradigms in Scala
- REST API design patterns

## 10. Further Exploration

Students are encouraged to:
1. Run both servers side-by-side (ports 8080 vs 8081)
2. Compare route definition patterns
3. Experiment with error handling in each framework
4. Explore streaming capabilities (fs2 vs Pekko Streams)
5. Write custom directives (Pekko) or middleware (http4s)

## References

- [http4s documentation](https://http4s.org/)
- [Cats Effect documentation](https://typelevel.org/cats-effect/)
- [fs2 documentation](https://fs2.io/)
- [Pekko HTTP documentation](https://pekko.apache.org/docs/pekko-http/current/)
- [Pekko Streams documentation](https://pekko.apache.org/docs/pekko/current/stream/)
