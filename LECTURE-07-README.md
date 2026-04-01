# Lecture 7: Database Integration & Multiple Persistence

## Overview

This lecture demonstrates how to add persistent storage to a microservices architecture with **multiple database implementations**. The same business logic works with both MongoDB (document database) and PostgreSQL (relational database) through a clean abstraction layer.

## Learning Objectives

1. **Repository Pattern**: Abstract persistence behind clean interfaces
2. **Multiple Persistence**: Support different database technologies interchangeably
3. **Functional Database Access**: Use Cats Effect IO for type-safe database operations
4. **Configuration Management**: Switch databases via configuration
5. **Data Seeding**: Populate databases with reference data (504 chess openings)
6. **Docker Compose**: Run microservices with multiple database containers

## Architecture

### Persistence Layer Structure

```
chess.microservices.persistence/
├── domain/
│   ├── PersistedGame.scala      # Domain model for stored games
│   └── Opening.scala             # Domain model for chess openings
├── GameRepository.scala          # Abstract trait for game persistence
├── OpeningRepository.scala       # Abstract trait for opening persistence
├── mongodb/
│   ├── MongoGameRepository.scala      # MongoDB implementation
│   └── MongoOpeningRepository.scala
├── postgres/
│   ├── PostgresGameRepository.scala   # PostgreSQL implementation
│   └── PostgresOpeningRepository.scala
├── DatabaseConfig.scala          # Configuration loading
├── DatabaseFactory.scala         # Repository creation & connection management
└── OpeningSeeder.scala           # CSV loading & database seeding
```

### Key Design Patterns

#### 1. Repository Pattern

Abstract interface for data access:

```scala
trait GameRepository[F[_]]:
  def save(game: PersistedGame): F[PersistedGame]
  def findById(gameId: String): F[Option[PersistedGame]]
  def delete(gameId: String): F[Boolean]
  def findAll(): F[Vector[PersistedGame]]
  def findByStatus(status: String): F[Vector[PersistedGame]]
```

- **Benefits**: Business logic doesn't depend on database choice
- **Higher-Kinded Types**: `F[_]` allows different effect types (IO, Task, etc.)
- **Immutability**: Domain models are immutable case classes

#### 2. Configuration-Based Selection

```hocon
database {
  active = "mongodb"  # or "postgres"

  mongodb {
    uri = "mongodb://localhost:27017"
    database = "chess"
  }

  postgres {
    host = "localhost"
    port = 5432
    database = "chess"
    user = "chess"
    password = "chess123"
  }
}
```

Switch databases via:
- Config file: `application.conf`
- Environment variable: `CHESS_DB=postgres`

#### 3. Resource Management

```scala
DatabaseFactory.createRepositories(config): Resource[IO, (GameRepository[IO], OpeningRepository[IO])]
```

- **Resource**: Guarantees proper cleanup (connections, pools)
- **Composable**: Repositories created together, released together
- **Functional**: No manual connection management

## Implementation Details

### MongoDB Implementation

**Technology Stack:**
- `mongo4cats`: Functional MongoDB driver for Cats Effect
- `Circe`: JSON serialization (automatic BSON mapping)

**Key Features:**
- Document-based storage (natural fit for game state)
- Automatic upsert logic (insert or replace)
- Filter-based queries

**Example:**
```scala
def save(game: PersistedGame): IO[PersistedGame] =
  for
    exists <- findById(game.gameId)
    _ <- exists match
      case Some(_) => collection.replaceOne(Filter.eq("gameId", game.gameId), game)
      case None    => collection.insertOne(game)
  yield game
```

### PostgreSQL Implementation

**Technology Stack:**
- `Doobie`: Functional JDBC layer for Cats Effect
- `HikariCP`: Connection pooling
- Type-safe SQL queries

**Key Features:**
- Relational storage with SQL DDL
- Automatic schema creation
- UPSERT via `ON CONFLICT`
- Indexed queries for performance

**Example:**
```scala
def save(game: PersistedGame): IO[PersistedGame] =
  sql"""
    INSERT INTO games (game_id, fen, pgn, status, created_at, updated_at)
    VALUES (${game.gameId}, ${game.fen}, ${game.pgn}, ${game.status},
            ${game.createdAt.toEpochMilli}, ${game.updatedAt.toEpochMilli})
    ON CONFLICT (game_id)
    DO UPDATE SET fen = EXCLUDED.fen, pgn = EXCLUDED.pgn, ...
  """.update.run.transact(transactor).as(game)
```

### Opening Library

**Data Source:** ECO (Encyclopaedia of Chess Openings)
- **Count:** 504 standard chess openings (A00-E99)
- **Format:** CSV with eco, name, moves, variation
- **FEN Computation:** Automatically computed from PGN moves

**Seeding Process:**
1. Load CSV from resources
2. Parse each opening
3. Compute FEN position using `GameController`
4. Batch insert into database

**Example Opening:**
```csv
C60,Ruy Lopez,1. e4 e5 2. Nf3 Nc6 3. Bb5,Spanish
```

## Running the Application

### Prerequisites

- **Java 17+**
- **sbt 1.9+**
- **Docker & Docker Compose** (for databases)

### Start Databases

```bash
# Start MongoDB and PostgreSQL
docker compose up mongodb postgres -d

# Verify both are running
docker ps
```

### Run with MongoDB (Default)

```bash
# Set environment
export CHESS_DB=mongodb

# Run game service
sbt "runMain chess.microservices.game.GameServer"
```

### Run with PostgreSQL

```bash
# Set environment
export CHESS_DB=postgres

# Run game service
sbt "runMain chess.microservices.game.GameServer"
```

### Run Full Stack

```bash
# All microservices + databases
docker compose up --build
```

**Services:**
- MongoDB: `localhost:27017`
- PostgreSQL: `localhost:5432`
- Game Service: `localhost:8081`
- UI Service: `localhost:8082`
- API Gateway: `localhost:8080`

### Database Configuration

**MongoDB Connection:**
```bash
# Connect with mongosh
docker exec -it chess-mongodb mongosh chess

# List games
db.games.find()

# List openings
db.openings.find()
```

**PostgreSQL Connection:**
```bash
# Connect with psql
docker exec -it chess-postgres psql -U chess -d chess

# List games
SELECT * FROM games;

# List openings
SELECT * FROM openings LIMIT 10;
```

## Code Quality & Testing

### Functional Programming Principles

1. **Immutability**: All domain models are immutable case classes
2. **Pure Functions**: Repository methods have no side effects (wrapped in IO)
3. **Composition**: Use `flatMap`, `map`, `traverse` for operation chaining
4. **Type Safety**: Compile-time guarantees via Doobie's type-checked SQL

### Testing Strategy

**Repository Tests** (TODO: Implement)
- Test both MongoDB and PostgreSQL implementations
- Use embedded/test containers
- Verify CRUD operations
- Test concurrent access
- Verify transaction handling

**Integration Tests** (TODO: Implement)
- End-to-end game persistence
- Opening library queries
- Database switching

## Dependencies Added

```scala
// MongoDB (functional driver)
"io.github.kirill5k" %% "mongo4cats-core" % "0.7.9"
"io.github.kirill5k" %% "mongo4cats-circe" % "0.7.9"

// PostgreSQL (functional SQL)
"org.tpolecat" %% "doobie-core" % "1.0.0-RC5"
"org.tpolecat" %% "doobie-postgres" % "1.0.0-RC5"
"org.tpolecat" %% "doobie-hikari" % "1.0.0-RC5"
```

## Architecture Evolution

### From Lecture 6 to Lecture 7

**Lecture 6** (Basic Microservices):
- In-memory game storage (`Ref[IO, Map[String, GameController]]`)
- Games lost on restart
- No persistence layer

**Lecture 7** (Database Integration):
- **Added:** Abstract repository traits
- **Added:** MongoDB & PostgreSQL implementations
- **Added:** Configuration-based database selection
- **Added:** Opening library with 504 chess openings
- **Added:** Automatic database seeding
- **Improved:** Games persist across restarts
- **Improved:** Support for both document and relational storage

## Performance Considerations

### MongoDB Advantages
- **Schema Flexibility**: Easy to evolve game state structure
- **Natural JSON Mapping**: Direct serialization with Circe
- **Horizontal Scaling**: Built-in sharding support
- **Document Queries**: Efficient nested data access

### PostgreSQL Advantages
- **ACID Transactions**: Strong consistency guarantees
- **Complex Queries**: Rich SQL support with joins
- **Mature Tooling**: Extensive monitoring & backup tools
- **Data Integrity**: Foreign keys, constraints, triggers

### Comparison Metrics (TODO: Benchmark)
- Insert performance (single game)
- Bulk insert (opening library)
- Query performance (find by ID, status, etc.)
- Memory usage
- Connection pool behavior

## Future Enhancements

1. **Caching Layer**: Redis for frequently accessed games
2. **Read Replicas**: Separate read/write databases
3. **Event Sourcing**: Store game moves as events
4. **Sharding**: Distribute games across database instances
5. **Full-Text Search**: ElasticSearch for opening searches
6. **Analytics**: Time-series data for game statistics
7. **Backup & Recovery**: Automated database backups
8. **Migration Tool**: Schema migration support (Flyway/Liquibase)

## Key Takeaways

1. **Abstraction Enables Flexibility**: Repository pattern allows swapping databases without changing business logic
2. **Functional Effects**: Cats Effect IO provides composable, type-safe database operations
3. **Configuration Over Code**: Database selection via config, not code changes
4. **Resource Safety**: Automatic connection management with Resource
5. **Production Ready**: Connection pooling, health checks, Docker deployment

## References

- **ECO Codes**: [Wikipedia](https://en.wikipedia.org/wiki/List_of_chess_openings)
- **Doobie**: [Functional JDBC](https://tpolecat.github.io/doobie/)
- **mongo4cats**: [Functional MongoDB](https://github.com/Kirill5k/mongo4cats)
- **Repository Pattern**: [Martin Fowler](https://martinfowler.com/eaaCatalog/repository.html)

## Build & Run Summary

```bash
# Compile
sbt compile

# Run with MongoDB
export CHESS_DB=mongodb
sbt "runMain chess.microservices.game.GameServer"

# Run with PostgreSQL
export CHESS_DB=postgres
sbt "runMain chess.microservices.game.GameServer"

# Full Docker stack
docker compose up --build

# Run tests (TODO)
sbt test
```

---

**Branch:** `lecture/microservices-database`
**Base:** `lecture/microservices-basic`
**Status:** ✅ Implementation Complete, Tests Pending
