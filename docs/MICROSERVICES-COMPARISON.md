# Monolithic vs Microservices Architecture

## Comparison for Chess Application

This document compares the monolithic architecture (Lectures 1-5) with the microservices architecture (Lecture 6) for the chess application.

---

## Architecture Diagrams

### Monolithic Architecture (Lectures 1-5)

```
┌─────────────────────────────────────┐
│        Single Process/JVM            │
│                                     │
│  ┌───────────────────────────────┐  │
│  │     REST API Layer            │  │  Port 8080
│  │   (http4s or Pekko HTTP)      │  │
│  ├───────────────────────────────┤  │
│  │     Controller Layer          │  │
│  │   (GameController)            │  │
│  ├───────────────────────────────┤  │
│  │     Model Layer               │  │
│  │   (Board, Piece, MoveResult)  │  │
│  └───────────────────────────────┘  │
│                                     │
└─────────────────────────────────────┘
```

### Microservices Architecture (Lecture 6)

```
┌──────────────────┐
│  API Gateway     │  Port 8080
│  (http4s)        │
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
┌───▼──────┐  ┌───▼──────┐
│  Game    │  │  UI      │
│ Service  │  │ Service  │
│ (http4s) │  │ (http4s) │
│ Port     │  │ Port     │
│ 8081     │  │ 8082     │
└──────────┘  └──────────┘
```

---

## Detailed Comparison

### 1. Deployment

#### Monolith
✅ **Advantages:**
- Single artifact to build and deploy
- Simple deployment pipeline
- One server to manage
- Easier to test end-to-end locally

❌ **Disadvantages:**
- All-or-nothing deployment
- Any change requires full redeployment
- Downtime affects entire application
- Cannot scale individual components independently

**Example:**
```bash
sbt compile
sbt run
# Entire app on port 8080
```

#### Microservices
✅ **Advantages:**
- Independent deployment of services
- Can update Game Service without touching UI
- Gradual rollout possible
- Blue-green deployment per service
- Rollback individual services

❌ **Disadvantages:**
- More complex deployment pipeline
- Need orchestration (Docker Compose, Kubernetes)
- More infrastructure to manage
- Coordination needed for cross-service changes

**Example:**
```bash
docker-compose up --build
# Game Service: 8081
# UI Service: 8082
# Gateway: 8080
```

---

### 2. Scaling

#### Monolith
✅ **Advantages:**
- Simple to scale vertically (bigger machine)
- No inter-service communication overhead

❌ **Disadvantages:**
- Must scale entire application (wasteful)
- If only chess logic is slow, still scale UI/API layers
- Resource allocation inflexible

**Scaling Example:**
```
Before: 1 instance handling 100 req/s
After:  3 instances handling 300 req/s
        (but only needed to scale game logic)
```

#### Microservices
✅ **Advantages:**
- Scale only what needs scaling
- Game Service CPU-bound? Scale just that
- UI Service serving static files? Scale independently
- Efficient resource utilization

❌ **Disadvantages:**
- More complex scaling logic
- Need service discovery
- Load balancing required
- Monitoring more complex

**Scaling Example:**
```
Game Service:  5 instances (CPU-intensive)
UI Service:    2 instances (lightweight)
API Gateway:   3 instances (network-bound)
```

---

### 3. Technology Diversity

#### Monolith
✅ **Advantages:**
- Single technology stack
- Easier to hire (one skill set)
- Consistent patterns

❌ **Disadvantages:**
- Stuck with initial technology choices
- Can't use best tool for each job
- Difficult to adopt new technologies

**Current Stack:**
- Language: Scala 3
- Web: http4s or Pekko HTTP
- Serialization: Circe
- UI: ScalaFX (desktop) or JavaScript (web)

#### Microservices
✅ **Advantages:**
- Can pick best technology for each service
- UI Service could be Node.js/React
- Game Service could be Scala (great for business logic)
- Future: Add Python ML service for AI analysis

❌ **Disadvantages:**
- Multiple languages/frameworks to maintain
- Context switching for developers
- More complex build process

**Possible Diverse Stack:**
```
Game Service:     Scala + http4s (strong typing, chess logic)
UI Service:       Node.js + Express (NPM ecosystem)
Analytics Service: Python + Flask (ML libraries)
API Gateway:      Go (performance, simplicity)
```

---

### 4. Development Complexity

#### Monolith
✅ **Advantages:**
- Easy local development (one `sbt run`)
- Simple debugging (single process)
- IDE works great (single project)
- No network calls to debug

❌ **Disadvantages:**
- Large codebase can be overwhelming
- Long build times as project grows
- Merge conflicts in large teams
- Tight coupling risk

**Local Development:**
```bash
sbt run
# Everything works immediately
# Set breakpoint anywhere
# Debug across layers easily
```

#### Microservices
✅ **Advantages:**
- Smaller, focused codebases
- Team autonomy (own a service)
- Faster builds per service
- Clear boundaries enforce decoupling

❌ **Disadvantages:**
- Need to run multiple services locally
- Network debugging required
- Distributed tracing needed
- Integration testing complex

**Local Development:**
```bash
# Terminal 1
sbt "runMain chess.microservices.game.GameServer"

# Terminal 2
sbt "runMain chess.microservices.ui.UIServer"

# Terminal 3
sbt "runMain chess.microservices.gateway.GatewayServer"

# Or use Docker Compose
docker-compose up
```

---

### 5. Fault Tolerance & Resilience

#### Monolith
✅ **Advantages:**
- Simple failure model (up or down)
- No network failures between layers
- Consistent state (in same JVM)

❌ **Disadvantages:**
- Single point of failure
- Memory leak affects everything
- CPU spike affects entire app
- One bad dependency can crash all

**Failure Scenario:**
```
Bug in FEN parser → Entire app crashes
Memory leak in UI → OOM kills app
Database connection lost → All features down
```

#### Microservices
✅ **Advantages:**
- Partial degradation possible
- Game Service down? UI still serves cached state
- Bulkheads isolate failures
- Can implement circuit breakers

❌ **Disadvantages:**
- Network can fail
- Partial failures hard to debug
- Need resilience patterns (retry, timeout, circuit breaker)
- More complex failure modes

**Failure Scenario:**
```
Bug in Game Service → UI still loads, shows error message
Game Service slow → Gateway timeout, doesn't block UI
UI Service down → Can still access API directly for testing
```

**Resilience Patterns Needed:**
- Circuit breakers (prevent cascade failures)
- Timeouts (don't wait forever)
- Retries (transient failures)
- Fallbacks (degrade gracefully)

---

### 6. Data Management

#### Monolith
✅ **Advantages:**
- Single database (simple)
- ACID transactions work
- Joins across entities easy
- No data duplication

❌ **Disadvantages:**
- Database becomes bottleneck
- All services share schema
- Difficult to migrate database

**Data Access:**
```scala
// In-memory state
val games: Map[String, GameController] = ???

// Or single database
val db = Database.connect()
val game = db.games.findById(id)
```

#### Microservices
✅ **Advantages:**
- Database per service (independence)
- Can use different databases
- Game Service: PostgreSQL (relational)
- UI Service: Redis (cache)
- Analytics: MongoDB (document store)

❌ **Disadvantages:**
- No cross-service transactions
- Data duplication
- Eventual consistency
- Need event-driven patterns

**Data Access:**
```
Game Service: Has own game state
UI Service: May cache game data
Analytics: Subscribes to game events

When game updates → Event published → Analytics updates
```

**Consistency Challenges:**
- Distributed transactions (2PC, Saga pattern)
- Event sourcing
- CQRS (Command Query Responsibility Segregation)

---

### 7. Testing Strategy

#### Monolith
✅ **Advantages:**
- Unit tests straightforward
- Integration tests run in-process
- Easy to test full stack
- No network mocking needed

❌ **Disadvantages:**
- Large test suites take long
- Hard to test in isolation
- Changes can break unrelated tests

**Testing Pyramid:**
```
Unit Tests: 80% (fast, isolated)
Integration Tests: 15% (same JVM)
E2E Tests: 5% (full app)
```

#### Microservices
✅ **Advantages:**
- Unit tests per service (fast)
- Services can test independently
- Contract testing ensures compatibility
- Parallel test execution

❌ **Disadvantages:**
- Integration testing complex (need all services)
- Contract testing overhead
- Test data management hard
- Environment setup complex

**Testing Pyramid:**
```
Unit Tests: 70% (per service)
Contract Tests: 20% (service interfaces)
Integration Tests: 5% (all services)
E2E Tests: 5% (through gateway)
```

**Contract Testing:**
```
Game Service publishes contract:
  POST /games → returns { gameId, fen }

Gateway tests against contract:
  Expects gameId and fen fields

If Game Service changes response → Contract test fails
```

---

### 8. Code Organization

#### Monolith

**Structure:**
```
chess/
├── model/           # Domain logic
│   ├── Board.scala
│   ├── Piece.scala
│   └── MoveResult.scala
├── controller/      # Business logic
│   ├── GameController.scala
│   └── io/
├── view/            # Presentation
│   └── ChessGUI.scala
└── api/             # REST layer
    └── server/
```

✅ **Advantages:**
- Clear package structure
- Easy to navigate
- Refactoring tools work well

❌ **Disadvantages:**
- All code in one repo
- Difficult to enforce boundaries
- Import violations possible

#### Microservices

**Structure:**
```
chess/
├── microservices/
│   ├── game/              # Game Service
│   │   ├── GameServer.scala
│   │   ├── GameRoutes.scala
│   │   └── GameService.scala
│   ├── gateway/           # API Gateway
│   │   ├── GatewayServer.scala
│   │   ├── GatewayRoutes.scala
│   │   └── ServiceProxy.scala
│   ├── ui/                # UI Service
│   │   └── UIServer.scala
│   └── shared/            # Shared models
│       └── ApiModels.scala
└── services/ui/static/    # Frontend assets
```

✅ **Advantages:**
- Physical boundaries (separate deployables)
- Clear ownership
- Shared models explicit

❌ **Disadvantages:**
- Code duplication risk
- Dependency management complex
- Refactoring across services hard

---

### 9. Performance

#### Monolith
✅ **Advantages:**
- No network overhead
- In-process method calls (nanoseconds)
- Optimizations easier (JIT, inlining)

❌ **Disadvantages:**
- Single JVM memory limits
- GC pauses affect everything
- Resource contention

**Latency:**
```
UI → Controller → Model: < 1ms (in-process)
Total request: ~5-10ms
```

#### Microservices
✅ **Advantages:**
- Can optimize each service independently
- Scale bottlenecks independently

❌ **Disadvantages:**
- Network latency (1-5ms per hop)
- Serialization overhead (JSON encoding/decoding)
- More HTTP connections

**Latency:**
```
Client → Gateway: 2ms
Gateway → Game Service: 3ms
Game Service processing: 5ms
Response back: 3ms
Total: ~13ms (+ serialization)
```

**Network Overhead:**
- Each service hop adds latency
- JSON serialization (1-2ms)
- Network round-trip (1-5ms LAN, more WAN)

---

### 10. Observability

#### Monolith
✅ **Advantages:**
- Single log file
- Stack traces show full picture
- Profiling straightforward

❌ **Disadvantages:**
- Metrics aggregated (can't see per-component)
- Log volume high

**Monitoring:**
```
Single metric: Request rate
Single log: All components mixed
Single trace: Full stack trace
```

#### Microservices
✅ **Advantages:**
- Per-service metrics
- Clear performance attribution
- Independent monitoring

❌ **Disadvantages:**
- Need distributed tracing (Jaeger, Zipkin)
- Log aggregation required (ELK stack)
- Correlation IDs essential

**Monitoring:**
```
Metrics: Per-service dashboards
  - Game Service: CPU 80% (bottleneck!)
  - UI Service: CPU 10%
  - Gateway: Network 50%

Logs: Aggregated with correlation IDs
  [trace-id: abc123] Gateway received request
  [trace-id: abc123] Game Service processing move
  [trace-id: abc123] Game Service returned response

Tracing: Spans across services
  Request → Gateway (5ms) → Game Service (10ms) → Response
```

---

## Decision Matrix

### When to Use Monolith

✅ **Use Monolith When:**
- **Small team** (< 10 developers)
- **Tight deadlines** (need to ship fast)
- **Simple domain** (not much complexity)
- **Limited scalability needs** (vertical scaling sufficient)
- **Prototyping / MVP**
- **Learning the domain** (not ready to define service boundaries)

**Example: Our Chess Application**
- Teaching tool (simplicity matters)
- Single domain (chess)
- Vertical scaling sufficient
- Small codebase

### When to Use Microservices

✅ **Use Microservices When:**
- **Large teams** (> 20 developers, need autonomy)
- **Different scaling needs** (some components hot, others cold)
- **Technology diversity required** (ML service in Python, API in Go)
- **Organizational boundaries** (different teams own different features)
- **Independent deployment critical** (can't afford full app downtime)

**Example: E-commerce Platform**
- Product catalog (Python + ML recommendations)
- Payment processing (Java + high security)
- User profiles (Node.js + fast iteration)
- Analytics (Scala + Spark)

---

## Migration Path

### Strangler Fig Pattern

Don't rewrite monolith all at once. Gradually extract services:

```
Step 1: Monolith with everything

Step 2: Extract Authentication Service
  Monolith + Auth Service

Step 3: Extract Game Service
  Monolith (UI only) + Auth + Game

Step 4: Add API Gateway
  Gateway → (Auth, Game, UI)

Step 5: Decompose further as needed
```

**For Chess Application:**
1. Start with monolith (Lectures 1-5)
2. Add REST API layer (Lectures 4-5)
3. Extract Game Service (Lecture 6)
4. Add Gateway pattern
5. Could extract: Analytics, AI, Multiplayer services

---

## Hybrid Approach: Modular Monolith

Best of both worlds for medium complexity:

```
Single deployment unit, but:
- Clear module boundaries
- Enforced interfaces
- Could extract later if needed
```

**Example:**
```scala
// Modular Monolith
chess/
├── modules/
│   ├── game/         # Could become Game Service
│   ├── analytics/    # Could become Analytics Service
│   ├── multiplayer/  # Could become Multiplayer Service
│   └── ui/           # Could become UI Service

// Each module has clear API
// Modules communicate via interfaces
// Can extract to service when needed
```

---

## Conclusion

### For Chess Application (Educational Context)

**Monolith (Lectures 1-5):**
- Perfect for teaching fundamentals
- Simple to understand and debug
- Good for small projects

**Microservices (Lecture 6):**
- Teaches distributed systems concepts
- Prepares students for real-world architectures
- Shows tradeoffs explicitly

### General Guidance

**Start with Monolith:**
- Get domain right first
- Prove the concept
- Iterate quickly

**Move to Microservices:**
- When team scales
- When scalability needs diverge
- When organizational structure demands it

**Remember:** Microservices are an optimization for organizational complexity, not technical complexity. Don't use microservices to solve technical problems.

---

## Further Reading

- [Monolith First (Martin Fowler)](https://martinfowler.com/bliki/MonolithFirst.html)
- [Microservices (martinfowler.com)](https://martinfowler.com/articles/microservices.html)
- [Building Microservices (Sam Newman)](https://samnewman.io/books/building_microservices_2nd_edition/)
- [Microservices Patterns (Chris Richardson)](https://microservices.io/patterns/)
