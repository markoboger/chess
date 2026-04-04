# Lecture 6: Basic Microservices Architecture

## Learning Objectives

Students will learn:
- How to decompose a monolithic application into microservices
- Service boundaries and responsibilities
- Inter-service communication via REST APIs
- API Gateway pattern
- Service discovery and configuration
- Microservices deployment with Docker Compose
- Tradeoffs between monolithic and microservices architectures

## Architecture Overview

This lecture decomposes the monolithic chess application into three independent services:

```
                    ┌─────────────────────┐
                    │    API Gateway      │
                    │   (Port 8080)       │
                    └──────────┬──────────┘
                               │
                ┌──────────────┴──────────────┐
                │                             │
        ┌───────▼────────┐           ┌───────▼────────┐
        │  Game Service  │           │   UI Service   │
        │  (Port 8081)   │           │  (Port 8082)   │
        │                │           │                │
        │ Chess Engine   │           │   Vue.js App   │
        │ Game State     │           │   Static Files │
        └────────────────┘           └────────────────┘
```

### Service Responsibilities

**API Gateway (Port 8080)**
- Single entry point for all clients
- Routes requests to appropriate backend services
- Request/response transformation
- Cross-cutting concerns (logging, rate limiting, CORS)

**Game Service (Port 8081)**
- Core chess logic and game state management
- Game CRUD operations
- Move validation and execution
- FEN/PGN import/export
- Stateful: maintains active games in memory

**UI Service (Port 8082)**
- Serves static frontend assets (HTML, CSS, JS)
- Vue.js single-page application
- Chessboard visualization
- User interaction handling
- Communicates with API Gateway

## Goals

1. **Service Decomposition**: Split monolith into focused, independently deployable services
2. **API Gateway Pattern**: Implement centralized routing and request handling
3. **Service Contracts**: Define clear REST APIs between services
4. **Independent Deployment**: Each service can be built, tested, and deployed separately
5. **Docker Compose**: Local development environment with all services
6. **Architecture Analysis**: Compare monolithic vs microservices tradeoffs

## Service API Contracts

### API Gateway → Game Service

All endpoints proxied from Gateway (`:8080`) to Game Service (`:8081`):

- `POST /api/games` - Create new game
- `GET /api/games/:id` - Get game state
- `DELETE /api/games/:id` - Delete game
- `POST /api/games/:id/moves` - Apply move
- `GET /api/games/:id/moves` - Get move history
- `GET /api/games/:id/fen` - Get FEN position
- `POST /api/games/:id/fen` - Load FEN position

### API Gateway → UI Service

- `GET /` - Serve index.html
- `GET /assets/*` - Serve static assets (JS, CSS, images)

### UI Service → API Gateway

Frontend makes API calls to Gateway at `http://localhost:8080/api/*`

## Technical Requirements

### Dependencies (update build.sbt)

```scala
// Existing http4s dependencies from Lecture 4
val http4sVersion = "0.23.23"

libraryDependencies ++= Seq(
  // Core http4s for all services
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,

  // JSON handling
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-literal" % "0.14.6",

  // Configuration
  "com.typesafe" % "config" % "1.4.3"
)
```

### Package Structure

```
chess.microservices/
├── gateway/
│   ├── GatewayServer.scala        # Main entry point
│   ├── GatewayRoutes.scala        # Route definitions
│   ├── ServiceProxy.scala         # Proxy to backend services
│   └── GatewayConfig.scala        # Service discovery config
├── game/
│   ├── GameServer.scala           # Main entry point
│   ├── GameRoutes.scala           # Game API routes
│   └── GameService.scala          # Business logic (reuses existing)
├── ui/
│   └── frontend/                  # Vue.js application
│       ├── index.html
│       ├── src/
│       │   ├── App.vue
│       │   ├── components/
│       │   │   ├── ChessBoard.vue
│       │   │   └── MoveHistory.vue
│       │   └── api/
│       │       └── gameClient.js  # API Gateway client
│       └── package.json
└── shared/
    └── models/                    # Shared API models
```

## Implementation Steps

### Phase 1: Service Separation

1. **Extract Game Service**
   - Create standalone HTTP server on port 8081
   - Reuse existing chess logic (`chess.model`, `chess.controller`)
   - Implement REST endpoints from Lecture 4/5
   - In-memory game storage with UUID keys

2. **Create API Gateway**
   - HTTP server on port 8080
   - HTTP client to proxy requests to Game Service
   - Route mapping: `/api/games/*` → `http://game-service:8081/games/*`
   - Error handling and timeout management

3. **Build UI Service**
   - Simple HTTP server on port 8082 serving static files
   - Vue.js SPA with chessboard component
   - REST client calling API Gateway
   - Move input, game state display

### Phase 2: Configuration & Deployment

4. **Service Configuration**
   - Externalize service URLs (environment variables)
   - Health check endpoints for each service
   - Graceful shutdown handling

5. **Docker Compose Setup**
   - Dockerfile for each service
   - docker-compose.yml orchestrating all three
   - Service networking and port mapping
   - Volume mounts for development

### Phase 3: Testing & Documentation

6. **Integration Tests**
   - Test end-to-end flow through all services
   - Gateway routing correctness
   - Service failure scenarios
   - Contract testing between services

7. **Documentation**
   - Service contracts (API specifications)
   - Architecture decision records
   - Deployment guide
   - Comparison: monolith vs microservices

## Docker Compose Configuration

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  game-service:
    build:
      context: .
      dockerfile: services/game/Dockerfile
    ports:
      - "8081:8081"
    environment:
      - PORT=8081
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/health"]
      interval: 10s
      timeout: 5s
      retries: 3

  ui-service:
    build:
      context: .
      dockerfile: services/ui/Dockerfile
    ports:
      - "8082:8082"
    environment:
      - PORT=8082

  api-gateway:
    build:
      context: .
      dockerfile: services/gateway/Dockerfile
    ports:
      - "8080:8080"
    environment:
      - PORT=8080
      - GAME_SERVICE_URL=http://game-service:8081
      - UI_SERVICE_URL=http://ui-service:8082
    depends_on:
      - game-service
      - ui-service
```

## Success Criteria

- ✅ All three services start independently
- ✅ API Gateway correctly routes to backend services
- ✅ UI can play a complete chess game through the Gateway
- ✅ Docker Compose brings up entire stack with one command
- ✅ Service contracts documented
- ✅ Integration tests pass
- ✅ Architecture comparison document complete
- ✅ Each service has independent test suite

## Key Concepts to Explore

### Service Boundaries

**Well-defined responsibilities:**
- Game Service: Chess domain logic only
- UI Service: Presentation only
- Gateway: Routing and cross-cutting concerns only

**Loose coupling:**
- Services communicate only via HTTP APIs
- No shared in-memory state
- No direct database access between services

### API Gateway Pattern

**Responsibilities:**
- Single entry point (simplifies client)
- Request routing and composition
- Protocol translation (if needed)
- Authentication/authorization (future lecture)
- Rate limiting, caching (future lecture)

**Tradeoffs:**
- ➕ Simplifies client logic
- ➕ Centralizes cross-cutting concerns
- ➖ Single point of failure
- ➖ Potential bottleneck

### Service Discovery

**Static configuration (this lecture):**
- Hardcoded service URLs in environment variables
- Simple, works for small deployments
- Doesn't scale to many services

**Dynamic discovery (future lectures):**
- Service registry (Consul, Eureka)
- Client-side or server-side discovery
- Handles service instances coming/going

## Microservices vs Monolith Comparison

Create `docs/MICROSERVICES-COMPARISON.md` covering:

### Deployment
- **Monolith**: Single deployment unit, all-or-nothing
- **Microservices**: Independent deployment, gradual rollout

### Scaling
- **Monolith**: Scale entire application (wasteful if only one part is busy)
- **Microservices**: Scale only busy services (efficient resource use)

### Technology Diversity
- **Monolith**: One language/framework for everything
- **Microservices**: Pick best tool for each service (future: could add Python ML service)

### Complexity
- **Monolith**: ➕ Simple deployment, ➖ codebase can become tangled
- **Microservices**: ➕ Clean boundaries, ➖ distributed systems complexity

### Development Velocity
- **Monolith**: ➕ Easy local development, ➖ merge conflicts in large teams
- **Microservices**: ➕ Team autonomy, ➖ coordination overhead

### Failure Modes
- **Monolith**: Complete outage if process crashes
- **Microservices**: Partial degradation, requires resilience patterns

## Testing Strategy

### Unit Tests
- Each service has independent test suite
- Mock external service dependencies
- Test business logic in isolation

### Integration Tests
- Spin up all services (or use test containers)
- Test end-to-end scenarios through Gateway
- Verify service contract compliance

### Contract Tests
- Validate API contracts between services
- Producer tests (Game Service provides what Gateway expects)
- Consumer tests (Gateway sends what Game Service expects)

## Extensions for Advanced Lectures

This basic microservices setup provides foundation for:
- **Lecture 7**: Database per service, multiple persistence technologies
- **Lecture 8**: Event-driven communication (message queues)
- **Lecture 9**: Service mesh (Istio/Linkerd)
- **Lecture 10**: Observability (distributed tracing, metrics)
- **Lecture 11**: Resilience patterns (circuit breakers, retries)

## Running the System

```bash
# Build all services
sbt compile

# Run with Docker Compose
docker-compose up --build

# Access the application
# UI: http://localhost:8080
# Game Service directly: http://localhost:8081
# UI Service directly: http://localhost:8082

# Run integration tests
sbt it:test

# Shutdown
docker-compose down
```

## Development Workflow

```bash
# Run services individually for development

# Terminal 1: Game Service
sbt "runMain chess.microservices.game.GameServer"

# Terminal 2: UI Service
cd services/ui/frontend && npm run dev

# Terminal 3: API Gateway
sbt "runMain chess.microservices.gateway.GatewayServer"
```

## References

- [Microservices.io patterns](https://microservices.io/patterns/index.html)
- [API Gateway pattern](https://microservices.io/patterns/apigateway.html)
- [Service decomposition strategies](https://microservices.io/patterns/decomposition/decompose-by-business-capability.html)
- [Building Microservices (Sam Newman)](https://samnewman.io/books/building_microservices_2nd_edition/)
- Previous lectures: Monolith (1-3), REST APIs (4-5)
