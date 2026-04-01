# Lecture 7: Database Integration & Multiple Persistence

## Overview

This lecture demonstrates how to add persistent storage to a chess application using different database technologies. The implementation showcases the **Repository Pattern** with multiple backends, allowing for polyglot persistence and easy database switching.

## Architecture

### Layered Design

```
┌─────────────────────────────────────┐
│     Application Layer               │
│  (GameController, Services)         │
├─────────────────────────────────────┤
│     Repository Abstraction Layer    │
│  GameRepository[F[_]]                │
│  OpeningRepository[F[_]]             │
├─────────────────────────────────────┤
│     Implementation Layer             │
│  ┌─────────────┬─────────────────┐  │
│  │  MongoDB    │   PostgreSQL    │  │
│  │ (NoSQL)     │   (Relational)  │  │
│  └─────────────┴─────────────────┘  │
└─────────────────────────────────────┘
```

### Key Components

**Domain Models** (`chess.persistence.model`)
- `PersistedGame` - Represents a chess game with history, status, and metadata
- `Opening` - Represents a chess opening with ECO code, name, and moves

**Repository Interfaces** (`chess.persistence.repository`)
- `GameRepository[F[_]]` - Abstract interface for game persistence
- `OpeningRepository[F[_]]` - Abstract interface for opening library

**MongoDB Implementation** (`chess.persistence.mongodb`)
- Uses mongo4cats for functional MongoDB access
- Circe for automatic JSON serialization
- Document-based storage (natural fit for game history)

**PostgreSQL Implementation** (`chess.persistence.postgres`)
- Uses Doobie for type-safe SQL
- HikariCP for connection pooling
- Relational storage with proper indexing
- Automatic schema creation

## Architecture Patterns

### 1. Repository Pattern

The Repository Pattern provides an abstraction layer between the application logic and data access:

```scala
trait GameRepository[F[_]]:
  def save(game: PersistedGame): F[PersistedGame]
  def findById(id: UUID): F[Option[PersistedGame]]
  def findAll(limit: Int, offset: Int): F[List[PersistedGame]]
  def delete(id: UUID): F[Boolean]
```

Benefits:
- Business logic doesn't know about database details
- Easy to swap implementations
- Testable with mock repositories
- Supports multiple databases simultaneously

### 2. Higher-Kinded Types for Effect Abstraction

Using `F[_]` allows the repository to work with different effect types:

```scala
trait GameRepository[F[_]]  // F can be IO, Future, Try, etc.
```

This enables:
- Functional error handling
- Composable operations
- Referential transparency
- Effect system flexibility (cats-effect, ZIO, etc.)

### 3. Polyglot Persistence

Different databases excel at different tasks:

**MongoDB (Document Store)**
- Pros: Flexible schema, natural fit for nested data (game history)
- Cons: Less suited for complex queries across documents
- Use case: Storing complete game states with move history

**PostgreSQL (Relational)**
- Pros: ACID transactions, complex queries, data integrity
- Cons: Schema changes require migrations
- Use case: Structured queries, analytics, reporting

### 4. Database Abstraction Layer

Configuration-based database selection allows runtime choice:

```scala
// Pseudo-code for configuration
val gameRepo: GameRepository[IO] = config.dbType match
  case "mongodb" => new MongoGameRepository(mongoClient)
  case "postgres" => new PostgresGameRepository(transactor)
```

## Implementation Details

### MongoDB Implementation

```scala
class MongoGameRepository(collection: MongoCollection[IO, PersistedGame])
  extends GameRepository[IO]:

  override def save(game: PersistedGame): IO[PersistedGame] =
    collection.findOneAndReplace(
      Filter.eq("id", game.id.toString),
      game.copy(updatedAt = Instant.now()),
      upsert = true
    ).as(game)
```

**Features:**
- Automatic upsert (insert or update)
- JSON serialization via Circe
- Indexed queries for performance
- Type-safe filter construction

### PostgreSQL Implementation

```scala
class PostgresGameRepository(xa: Transactor[IO])
  extends GameRepository[IO]:

  override def save(game: PersistedGame): IO[PersistedGame] =
    sql"""
      INSERT INTO games (id, fen_history, pgn_moves, ...)
      VALUES (${game.id}, ${game.fenHistory}, ...)
      ON CONFLICT (id) DO UPDATE SET ...
    """.update.run.transact(xa).as(game)
```

**Features:**
- Type-safe SQL with Doobie
- Automatic schema creation
- Connection pooling (HikariCP)
- PostgreSQL-specific optimizations (arrays, JSON)

### Opening Library

**500 ECO-Coded Chess Openings** (A00-E99)

The opening library contains:
- ECO codes (Encyclopedia of Chess Openings classification)
- Opening names and variations
- PGN move sequences
- Computed FEN positions
- Move counts for filtering

**Seeding Process:**
1. Read CSV from `src/main/resources/openings/eco-openings.csv`
2. Parse ECO code, name, and moves
3. Apply moves to compute FEN position
4. Save to database (batch insert for efficiency)

## Running the Example

### 1. Start Databases

```bash
docker-compose up -d
```

This starts:
- MongoDB on `localhost:27017` (user: chess, password: chess123)
- PostgreSQL on `localhost:5432` (user: chess, password: chess123)

### 2. Verify Databases

```bash
# MongoDB
docker exec -it chess-mongodb mongosh -u chess -p chess123

# PostgreSQL
docker exec -it chess-postgres psql -U chess -d chess
```

### 3. Seed Opening Library

```scala
// TODO: Create seeding application
// This would:
// 1. Connect to database
// 2. Load OpeningSeeder
// 3. Seed from CSV resource
// 4. Print statistics
```

### 4. Run Application

```bash
sbt run
```

## Database Comparison

| Feature | MongoDB | PostgreSQL |
|---------|---------|------------|
| **Data Model** | Document (BSON) | Relational (SQL) |
| **Schema** | Flexible | Strict |
| **Queries** | JSON-based | SQL |
| **Transactions** | Limited | Full ACID |
| **Scalability** | Horizontal | Vertical (+ replication) |
| **Best For** | Nested data, rapid iteration | Structured data, complex queries |

## Performance Considerations

**MongoDB:**
- Fast for document retrieval by ID
- Good for write-heavy workloads
- Indexed queries are efficient
- Aggregation pipeline for analytics

**PostgreSQL:**
- Excellent for complex JOIN queries
- Strong consistency guarantees
- Advanced indexing (B-tree, GiST, GIN)
- Query optimization and explain plans

## Testing Strategy

**Unit Tests:**
- Test repository methods in isolation
- Mock database connections
- Verify query construction

**Integration Tests:**
- Use Testcontainers to spin up real databases
- Test actual database operations
- Verify schema creation and migrations
- Test concurrent access patterns

**Example:**
```scala
class MongoGameRepositorySpec extends AnyFlatSpec:
  // Use Testcontainers for MongoDB
  val container = MongoDBContainer()
  container.start()

  // Create repository with test connection
  val repo = new MongoGameRepository(...)

  "save" should "persist and retrieve games" in {
    // Test implementation
  }
```

## Learning Outcomes

After completing this lecture, students understand:

1. **Repository Pattern** - Abstracting data access
2. **Polyglot Persistence** - Using multiple databases
3. **Effect Systems** - Functional programming with IO
4. **Database Design** - Schema design for different database types
5. **Type Safety** - Compile-time guarantees for database operations
6. **Testing** - Integration testing with Testcontainers

## Next Steps

**Lecture 8: REST API Layer**
- Expose persistence layer via HTTP
- JSON serialization of game state
- API design and versioning
- Integration with frontend

**Future Enhancements:**
- Caching layer (Redis)
- Read replicas for scalability
- Event sourcing for game history
- Analytics queries (Spark integration)

## Code Structure

```
src/main/scala/chess/persistence/
├── model/
│   ├── PersistedGame.scala      # Domain model for games
│   └── Opening.scala             # Domain model for openings
├── repository/
│   ├── GameRepository.scala      # Abstract interface
│   └── OpeningRepository.scala   # Abstract interface
├── mongodb/
│   ├── MongoGameRepository.scala       # MongoDB implementation
│   └── MongoOpeningRepository.scala    # MongoDB implementation
├── postgres/
│   ├── PostgresGameRepository.scala    # PostgreSQL implementation
│   └── PostgresOpeningRepository.scala # PostgreSQL implementation
└── util/
    └── OpeningSeeder.scala       # CSV loading utility

src/main/resources/openings/
└── eco-openings.csv              # 500 chess openings

docker-compose.yml                 # Database containers
```

## References

- [Repository Pattern](https://martinfowler.com/eaaCatalog/repository.html)
- [mongo4cats Documentation](https://github.com/Kirill5k/mongo4cats)
- [Doobie Documentation](https://tpolecat.github.io/doobie/)
- [ECO Chess Opening Codes](https://www.chessgames.com/chessecohelp.html)
- [cats-effect Documentation](https://typelevel.org/cats-effect/)
