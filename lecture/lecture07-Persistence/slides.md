---
marp: true
title: "Lecture 07: Persistence"
description: "Persistence Layer, PostgreSQL, Slick, MongoDB, and Polyglot Persistence"
paginate: true
theme: htwg
---

<!-- _class: title -->
# Lecture 07
## Persistence Layer

<span class="eyebrow">Software Architecture with AI</span>

PostgreSQL, Slick, MongoDB, and a practical persistence strategy for the chess system

<p class="small">Prof. Dr. Marko Boger</p>

---

# Why We Need Persistence

- without persistence, every restart loses information
- no game history
- no opening analytics
- no cross-session continuity
- no data foundation for performance tests or Spark later

**Persistence turns a program into a system with memory.**

---

# First Principle

## A persistence layer is not "the database"

It is the part of the architecture that:

- decides what should be stored
- defines abstractions for reading and writing data
- shields the application from storage details
- allows us to switch or combine storage technologies

---

# General Architecture

```text
┌──────────────────────────────┐
│ UI / Services                │
├──────────────────────────────┤
│ Application Layer            │
│ Game flow, strategy, use     │
│ cases                        │
├──────────────────────────────┤
│ Persistence Layer            │
│ Repositories, mapping, IO    │
├──────────────────────────────┤
│ Database Engines             │
│ PostgreSQL, MongoDB          │
└──────────────────────────────┘
```

- the application should not talk SQL everywhere
- the UI should never know storage details

---

# Persistence Mechanisms

Common options in software architecture:

- in-memory storage
- flat files
- relational databases
- document databases
- key-value stores
- graph databases
- event stores

Today we use:

- **PostgreSQL** for structured, relational, query-friendly data
- **MongoDB** for flexible raw documents

---

# Why Start With PostgreSQL?

- students already know relational thinking
- schema is explicit
- joins and aggregation are familiar
- SQL remains important in real systems
- strong consistency and constraints are excellent teaching tools

PostgreSQL gives us a stable baseline before we move to a more flexible model.

---

# PostgreSQL Strengths

- tables with clear structure
- strong typing
- constraints and integrity rules
- expressive queries
- excellent for analytics and reporting
- mature tooling

In our project, PostgreSQL is a good home for:

- derived opening statistics
- structured lookup tables
- recommendations used by strategy

---

# Relational Thinking

## Typical relational questions

- Which opening has the highest win rate for White?
- Which opening appears most often?
- Which continuations are most successful from a known position?
- Which import batch produced these statistics?

These are natural SQL questions.

---

# Main PostgreSQL Structures

```text
opening_import_batches
- id
- source
- imported_at
- games_imported

opening_stats
- eco
- opening_name
- games_count
- white_wins
- draws
- black_wins

opening_move_stats
- eco
- ply
- san
- games_count
```

**These tables store derived knowledge, not the raw imported games.**

---

# Why Not Write SQL Everywhere?

Because application code should express:

- domain intent
- use cases
- repository contracts

not:

- JDBC boilerplate
- stringly typed queries in every service
- manual row mapping all over the codebase

This is where mapping libraries become useful.

---

# Enter Slick

## Slick = Functional Relational Mapping

Slick lets us describe:

- tables as Scala types
- queries as Scala expressions
- results as case classes

It sits between:

- plain SQL
- full ORM magic

So it is often a good teaching tool for relational persistence in Scala.

---

# Main Slick Concepts

- `Table[A]`
  maps one database table to one Scala shape
- `TableQuery[T]`
  represents the collection of rows of a table
- lifted embedding
  columns are values in a query language, not plain Scala values
- `DBIO`
  represents a database action
- profile
  selects the SQL dialect, e.g. `PostgresProfile`

---

# Simplified Slick Example

```scala
final case class OpeningStatRow(
  eco: String,
  openingName: String,
  gamesCount: Int,
  whiteWins: Int,
  draws: Int,
  blackWins: Int
)

final class OpeningStatsTable(tag: Tag)
  extends Table[OpeningStatRow](tag, "opening_stats"):

  def eco         = column[String]("eco", O.PrimaryKey)
  def openingName = column[String]("opening_name")
  def gamesCount  = column[Int]("games_count")
  def whiteWins   = column[Int]("white_wins")
  def draws       = column[Int]("draws")
  def blackWins   = column[Int]("black_wins")

  def * =
    (eco, openingName, gamesCount, whiteWins, draws, blackWins)
      .mapTo[OpeningStatRow]
```

---

# Reading the Slick Code

- `OpeningStatRow` is our Scala representation
- `OpeningStatsTable` describes the SQL table
- each `column[...]` corresponds to a typed table column
- `def *` is the mapping between row and case class

This is tangible because students can still see the table structure clearly.

---

# Simplified Slick Query

```scala
val openingStats = TableQuery[OpeningStatsTable]

def topOpenings(limit: Int): DBIO[Seq[OpeningStatRow]] =
  openingStats
    .sortBy(_.gamesCount.desc)
    .take(limit)
    .result
```

This reads almost like:

- start with the table
- sort descending by game count
- limit the number of rows
- execute later

---

# Where MongoDB Fits Better

MongoDB is not better "in general".
It is better for some kinds of data.

Typical strengths:

- flexible schema
- natural storage of nested documents
- good fit for raw imported payloads
- easier evolution when data shape changes often
- simple storage of semi-structured external data

---

# MongoDB Concepts

- database
- collection
- document
- BSON
- flexible schema
- nested objects and arrays
- query by document shape and fields

This is a better fit when we first import external game data and do not yet know every future field.

---

# Simplified Mongo Document

```json
{
  "_id": "xPFtHjYu",
  "eco": "B01",
  "openingName": "Scandinavian Defense",
  "result": "0-1",
  "white": "PlayerA",
  "black": "PlayerB",
  "moves": ["e4", "d5", "exd5", "Qxd5"],
  "pgn": "1. e4 d5 2. exd5 Qxd5",
  "source": "lichess-broadcast"
}
```

This is one raw imported game.

---

# Why Use Both Together?

## Polyglot persistence with clear roles

MongoDB stores:

- raw imported Lichess games
- semi-structured data
- documents that may evolve

PostgreSQL stores:

- cleaned, derived opening statistics
- ranked continuations
- efficient analytical queries

So we do not duplicate the same idea twice.

---

# Dataflow Strategy

```text
Lichess bulk PGN
        │
        ▼
MongoDB raw game documents
        │
        ▼
aggregation / transformation
        │
        ▼
PostgreSQL opening statistics
        │
        ▼
opening strategy in the application
        │
        ▼
visible recommendation in the UI
```

This is the key architectural idea of this lecture.

---

# Repository Abstraction

The application should depend on abstractions such as:

```scala
trait OpeningRepository[F[_]]:
  def findByFen(fen: String): F[Option[Opening]]
  def findAll(limit: Int, offset: Int): F[List[Opening]]
  def count(): F[Long]
```

and not on a specific database driver.

Later we can have:

- in-memory repository
- PostgreSQL repository
- MongoDB repository

---

# Simplified Scala Example

```scala
trait OpeningStatsRepository[F[_]]:
  def topOpenings(limit: Int): F[List[OpeningStat]]
  def findByEco(eco: String): F[Option[OpeningStat]]
```

Application code:

```scala
class OpeningIntelligenceService(repo: OpeningStatsRepository[IO]):
  def recommendedOpenings: IO[List[OpeningStat]] =
    repo.topOpenings(10)
```

The service does not know whether data comes from SQL, Mongo, or a test double.

---

# What the Code Is Really Doing

1. import raw games into MongoDB
2. parse and aggregate by opening
3. compute counts and success values
4. store the aggregated result in PostgreSQL
5. query the PostgreSQL result from strategy code

That is already a small but meaningful data pipeline.

---

# How This Becomes Visible in the UI

The user interface should not show databases directly.

It should show:

- current opening name
- popularity of this opening
- score for White / Black
- whether the recommendation comes from DB-backed analysis

Example UI message:

> Opening Intelligence (DB): 971 imported games, 507 opening statistics available

---

# Suggested UI Integration

- strategy dropdown contains `Opening Intelligence (DB)`
- opening panel shows:
  - ECO code
  - opening name
  - number of games in the sample
  - white wins / draws / black wins
- optional small note:
  - "based on imported Lichess archive data"

This makes the database work visible and meaningful to the user.

---

# Why This Architecture Helps Later

Lecture 08:

- compare lookup and aggregation performance
- benchmark in-memory vs PostgreSQL vs MongoDB

Lecture 12:

- replace or complement the aggregation stage with Spark
- analyze much larger batches of raw games

So persistence is not an isolated topic.
It is a foundation for later lectures.

---

<!-- _class: compact -->
# Task Assignment

## Build a persistence pipeline for opening intelligence

1. Define a repository abstraction for the data you need.
2. Import a small Lichess archive into MongoDB as raw game documents.
3. Design PostgreSQL tables for derived opening analytics.
4. Implement the PostgreSQL mapping with Slick.
5. Aggregate a first dataset and store the result in PostgreSQL.
6. Expose the analysis through the application layer.
7. Make the result visible in the UI.

Deliverables:

- schema design
- Scala persistence code
- one working dataflow
- one screenshot or demo of the UI using DB-backed analysis

---

# Key Message

## One problem, two databases, different responsibilities

- MongoDB remembers the raw world
- PostgreSQL organizes derived knowledge
- the persistence layer keeps the application clean
- the UI turns stored data into visible value

That is the architectural lesson of Lecture 07.
