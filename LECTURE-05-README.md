# Lecture 5: REST API with fs2

## Learning Objectives

Students will learn:
- Alternative implementation of the same REST interface using fs2
- Streaming abstractions with fs2-core
- Performance comparison methodology
- Library evaluation criteria

## Architecture Overview

This lecture implements the **same API specification** as Lecture 4, but using fs2 instead of http4s:

```
┌─────────────────────────┐
│   HTTP REST API         │
│   (fs2-based)           │
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
2. **Different Library**: Use fs2 ecosystem instead of http4s
3. **Comparison**: Document differences in implementation approach
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
val fs2Version = "3.9.3"

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % fs2Version,
  "co.fs2" %% "fs2-io" % fs2Version,
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-literal" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6"
)
```

### Package Structure
```
chess.api.fs2/
├── server/
│   ├── ChessServerFs2.scala    # HTTP server with fs2
│   ├── ChessRoutesFs2.scala    # Route definitions
│   └── GameServiceFs2.scala    # Business logic
├── model/
│   └── (reuse from Lecture 4)
└── client/
    └── ChessClientFs2.scala    # Test client
```

## Implementation Focus

### Streaming Patterns
- Use `Stream[F, A]` for request/response handling
- Demonstrate resource management with `Resource[F, A]`
- Show backpressure handling

### Comparison Points
Document differences in:
1. **Setup complexity** - How much boilerplate?
2. **Route definition** - DSL ergonomics
3. **Error handling** - How are failures managed?
4. **Testing** - Test utilities and ease of testing
5. **Performance** - Latency, throughput, memory usage

## Performance Benchmarking

Set up JMH benchmarks comparing:
- Request latency (p50, p95, p99)
- Throughput (requests/second)
- Memory allocation per request
- Concurrent game handling

Create `benchmarks/RestApiBenchmark.scala`:
```scala
@State(Scope.Benchmark)
class RestApiBenchmark {
  @Benchmark
  def http4sMove(): Unit = ???

  @Benchmark
  def fs2Move(): Unit = ???
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
1. **Implementation Comparison** - Code patterns, complexity
2. **Performance Results** - Benchmark data with charts
3. **Developer Experience** - Subjective assessment
4. **Recommendation** - Which to use when and why

## References

- [fs2 documentation](https://fs2.io/)
- [fs2 guide](https://github.com/typelevel/fs2/blob/main/docs/guide.md)
- Lecture 4 implementation (http4s version)
