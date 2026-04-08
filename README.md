# Chess Application

[![CI](https://github.com/markoboger/chess/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/markoboger/chess/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/markoboger/chess/badge.svg?branch=main)](https://coveralls.io/github/markoboger/chess?branch=main)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=markoboger_chess&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=markoboger_chess)

A modular Scala chess application used for software architecture teaching. The repository contains a playable desktop app, HTTP services, a Vue frontend, persistence adapters for MongoDB and PostgreSQL, realtime event delivery, benchmarks, and lecture material.

## Quick Start

### Desktop App

Run the classic dual UI mode:

```bash
sbt run
```

This starts:
- a ScalaFX GUI
- the console observer / console input loop

The main entry point is [ChessApp.scala](/Users/markoboger/workspace/chess/src/main/scala/chess/ChessApp.scala).

### Frontend Dev Server

```bash
cd frontend
npm install
npm run dev
```

The Vite frontend runs on `http://localhost:5173` by default and talks to the backend through `/api`.

### Backend Services

Run the game service directly:

```bash
sbt "runMain chess.microservices.game.GameServer"
```

The main HTTP entrypoints live under:
- [src/main/scala/chess/microservices/game](/Users/markoboger/workspace/chess/src/main/scala/chess/microservices/game)
- [src/main/scala/chess/microservices/gateway](/Users/markoboger/workspace/chess/src/main/scala/chess/microservices/gateway)
- [realtime/src/main/scala/chess/realtime](/Users/markoboger/workspace/chess/realtime/src/main/scala/chess/realtime)

### Docker Compose

The compose setup includes:
- `mongodb`
- `postgres`
- `game-service`
- `api-gateway`
- `vue-ui`

See [docker-compose.yml](/Users/markoboger/workspace/chess/docker-compose.yml).

## Architecture

The codebase is split into a few clear modules:

- `core/`
  Pure chess rules, notation support, and domain models
- `app/`
  Stateful application logic, controllers, AI strategies, opening/puzzle logic
- `data/`
  Repository traits plus in-memory, MongoDB, and PostgreSQL implementations
- `realtime/`
  Realtime event transport and websocket support
- `src/`
  Composition roots, desktop UI, JSON adapters, and HTTP service entrypoints
- `frontend/`
  Vue web UI
- `lecture/`
  Marp slide decks and teaching material

The high-level architecture model is documented in [docs/workspace.dsl](/Users/markoboger/workspace/chess/docs/workspace.dsl).

## Features

- full chess move validation
- FEN, PGN, and JSON import/export
- ScalaFX desktop GUI
- console UI
- HTTP API with http4s
- gateway + backend service split
- realtime game-event pipeline
- MongoDB and PostgreSQL persistence
- opening ingestion and analytics pipeline
- Vue frontend
- benchmarks and lecture material

## Project Layout

Key locations:

- [core/src/main/scala/chess/model](/Users/markoboger/workspace/chess/core/src/main/scala/chess/model)
- [core/src/main/scala/chess/controller/io](/Users/markoboger/workspace/chess/core/src/main/scala/chess/controller/io)
- [app/src/main/scala/chess/controller](/Users/markoboger/workspace/chess/app/src/main/scala/chess/controller)
- [app/src/main/scala/chess/application](/Users/markoboger/workspace/chess/app/src/main/scala/chess/application)
- [data/src/main/scala/chess/persistence](/Users/markoboger/workspace/chess/data/src/main/scala/chess/persistence)
- [realtime/src/main/scala/chess/realtime](/Users/markoboger/workspace/chess/realtime/src/main/scala/chess/realtime)
- [src/main/scala/chess/aview](/Users/markoboger/workspace/chess/src/main/scala/chess/aview)
- [src/main/scala/chess/microservices](/Users/markoboger/workspace/chess/src/main/scala/chess/microservices)
- [frontend/src](/Users/markoboger/workspace/chess/frontend/src)

## Development Commands

### Scala

```bash
# compile everything
sbt compile

# run all tests
sbt test

# focused coverage report
sbt coverage test coverageAggregate

# run desktop app
sbt run

# run game service
sbt "runMain chess.microservices.game.GameServer"
```

### Frontend

```bash
cd frontend
npm install
npm run dev
npm run test
npm run build
```

## Quality Checks

The repository is wired for automated quality checks from the start:

- GitHub Actions for build and test automation
- `scoverage` for Scala coverage
- Coveralls for hosted coverage reporting
- SonarQube / SonarCloud for static analysis and quality gates

Useful local commands:

```bash
# build + test
sbt clean compile test

# aggregate Scala coverage
sbt clean coverage test coverageAggregate
```

CI configuration:
- [.github/workflows/ci.yml](/Users/markoboger/workspace/chess/.github/workflows/ci.yml)
- [sonar-project.properties](/Users/markoboger/workspace/chess/sonar-project.properties)

Required GitHub secrets for SonarQube:
- `SONAR_TOKEN`
- `SONAR_HOST_URL`

## Persistence and Data

The repo contains both raw and analytical persistence flows:

- MongoDB for raw or document-oriented storage
- PostgreSQL for relational game/opening data
- seed/import utilities under [seeder](/Users/markoboger/workspace/chess/seeder)

The data-ingestion flow is described in [docs/data-ingestion-flow.md](/Users/markoboger/workspace/chess/docs/data-ingestion-flow.md).

## Teaching Material

Lecture decks live under [lecture](/Users/markoboger/workspace/chess/lecture) and use Marp with the shared theme in [lecture/themes/htwg.css](/Users/markoboger/workspace/chess/lecture/themes/htwg.css).

Marp config:
- [marp.config.js](/Users/markoboger/workspace/chess/marp.config.js)

## Notes

- the desktop app still provides the most direct way to explore the chess logic
- the web app and service stack reflect the later lecture stages
- coverage exclusions intentionally leave out bootstrap-heavy classes such as GUI startup and server wrappers so the report focuses on meaningful behavior
- CI publishes coverage to Coveralls and static-analysis results to SonarCloud
