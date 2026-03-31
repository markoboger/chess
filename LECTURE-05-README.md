# Lecture 5: REST API with Pekko HTTP

## Learning Objectives

Students will learn:
- Alternative implementation of the same REST interface using Pekko HTTP
- Different streaming models: Pekko Streams vs fs2
- Different programming paradigms: Future-based vs Effect-based
- Performance comparison methodology
- Library evaluation criteria

## Architecture Overview

This lecture implements the **same API specification** as Lecture 4, but using Pekko HTTP instead of http4s:

```
┌─────────────────────────┐
│   HTTP REST API         │
│   (Pekko HTTP)          │
├─────────────────────────┤
│   Game Controller       │
│   (existing)            │
├─────────────────────────┤
│   Chess Model/Engine    │
│   (existing)            │
└─────────────────────────┘
```

## Goals

1. **Same Interface**: Implement identical REST endpoints as Lecture 4
2. **Different Stack**: Use Pekko HTTP/Pekko Streams/Futures instead of http4s/fs2/Cats Effect
3. **Comparison**: Document differences in implementation approach and programming models
4. **Performance**: Set up benchmarks to compare both implementations

## REST Endpoints

All endpoints from Lecture 4:
- `POST /games` - Create new game
- `GET /games/:id` - Get game state
- `DELETE /games/:id` - Delete game
- `POST /games/:id/moves` - Apply move
- `GET /games/:id/moves` - Get move history
- `GET /games/:id/fen` - Get FEN position
- `POST /games/:id/fen` - Load FEN position

## Technical Requirements

### Dependencies (add to build.sbt)
```scala
val pekkoVersion = "1.1.2"
val pekkoHttpVersion = "1.1.0"

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
  "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
  "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
  "org.apache.pekko" %% "pekko-http-testkit" % pekkoHttpVersion % Test,
  "io.circe" %% "circe-generic" % "0.14.10",
  "io.circe" %% "circe-literal" % "0.14.10",
  "io.circe" %% "circe-parser" % "0.14.10"
)
```

### Package Structure
```
chess.api.pekko/
├── server/
│   ├── ChessServerPekko.scala    # HTTP server with Pekko HTTP
│   ├── ChessRoutesPekko.scala    # Route definitions
│   └── GameServicePekko.scala    # Business logic
├── model/
│   └── (reuse from Lecture 4)
└── client/
    └── ChessClientPekko.scala    # Test client
```

## Implementation Focus

### Streaming Patterns
- Use `Source[A, NotUsed]` for streaming responses
- Demonstrate resource management with Pekko lifecycle
- Show backpressure handling with Pekko Streams

### Comparison Points
Document differences in:
1. **Programming Model** - Future-based (Pekko) vs Effect-based (http4s/Cats)
2. **Setup complexity** - How much boilerplate?
3. **Route definition** - DSL ergonomics (Pekko directives vs http4s DSL)
4. **Error handling** - How are failures managed?
5. **Testing** - Test utilities and ease of testing
6. **Streaming model** - Pekko Streams vs fs2
7. **Performance** - Latency, throughput, memory usage

## Performance Benchmarking

Set up JMH benchmarks comparing:
- Request latency (p50, p95, p99)
- Throughput (requests/second)
- Memory allocation per request
- Concurrent game handling

Create `benchmark/RestApiBenchmark.scala`:
```scala
@State(Scope.Benchmark)
class RestApiBenchmark {
  @Benchmark
  def http4sMove(): Unit = ???

  @Benchmark
  def pekkoMove(): Unit = ???
}
```

## Success Criteria

- Identical API contract as Lecture 4
- All endpoints functional
- Performance benchmark suite
- Comparison document (markdown) analyzing tradeoffs
- Test coverage >80%

## Comparison Document Structure

Create `docs/REST-API-COMPARISON.md`:
1. **Programming Model Comparison** - Future-based vs Effect-based approaches
2. **Implementation Comparison** - Code patterns, complexity, DSL differences
3. **Streaming Models** - Pekko Streams vs fs2
4. **Performance Results** - Benchmark data with analysis
5. **Developer Experience** - Subjective assessment, learning curve
6. **Recommendation** - Which to use when and why

## Key Differences to Explore

**Programming Model:**
- http4s: Pure functional with Cats Effect IO
- Pekko HTTP: Imperative style with Futures

**Streaming:**
- http4s: fs2 streams with functional composition
- Pekko HTTP: Pekko Streams with graph DSL

**Error Handling:**
- http4s: MonadError, ApplicativeError
- Pekko HTTP: Try, Future failures, exception directives

**Testing:**
- http4s: http4s-dsl test utilities, pure IO testing
- Pekko HTTP: RouteTest trait, ScalatestRouteTest

## References

- [Pekko HTTP documentation](https://pekko.apache.org/docs/pekko-http/current/)
- [Pekko Streams guide](https://pekko.apache.org/docs/pekko/current/stream/)
- [Pekko routing DSL](https://pekko.apache.org/docs/pekko-http/current/routing-dsl/)
- Lecture 4 implementation (http4s version)
