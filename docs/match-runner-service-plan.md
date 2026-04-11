# Match Runner Service Plan

Branch: `service/match-runner`

Status: Slice 7 complete

## Slice 0 Result

Result: passive mode is now viable through the backend when the new setting `backendAutoplay=true` is used.

What changed:

- `GameSettings` now includes `backendAutoplay: Boolean = false`
- `GameSessionService` now owns backend-side autonomous progression for non-human turns
- the loop starts after:
  - game creation
  - successful move application
  - successful FEN load

Important compatibility choice:

- backend autoplay is **opt-in**
- default remains `false`
- this avoids fighting the existing GUI / web client orchestration

Evidence:

- application-level tests prove backend-owned autoplay works:
  - [GameSessionServiceSpec.scala](/Users/markoboger/workspace/chess/app/src/test/scala/chess/application/game/GameSessionServiceSpec.scala)
- route-level feasibility test now proves a `CvC` game created over HTTP advances on its own when `backendAutoplay=true`:
  - [PassiveCvcFeasibilitySpec.scala](/Users/markoboger/workspace/chess/src/test/scala/chess/microservices/game/PassiveCvcFeasibilitySpec.scala)

Conclusion:

- a passive `match-runner-service` is now architecturally feasible
- it should create games with:
  - `whiteIsHuman=false`
  - `blackIsHuman=false`
  - `backendAutoplay=true`

## Goal

Build a new Scala microservice called `match-runner-service` that uses the chess game's HTTP API to run many computer-vs-computer matches, compare strategies across many games, and persist results in PostgreSQL so we can evaluate whether newer strategy versions actually improve performance.

The service should:

- be a separate sbt module
- run as its own Docker container
- communicate with the existing chess backend via HTTP
- store experiment and match results in PostgreSQL
- start with a text UI
- later grow a more graphical web UI
- be fully tested

## Important Constraint

The current backend clearly exposes:

- `POST /games`
- `POST /games/:id/moves`
- `POST /games/:id/ai-move`
- `GET /games/:id`

But the backend does **not yet obviously own autonomous CvC progression** end-to-end.

Today, CvC autoplay appears to be orchestrated mainly in the GUI / frontend layers, while the backend exposes a single-step AI move computation endpoint.

That means there are two possible realities:

1. the backend already supports passive full-game CvC progression implicitly
2. the new service must still observe and possibly poll while another runtime component advances the game

Because you want a **passive runner**, we should treat backend-supported autonomous CvC completion as a required assumption to verify very early.

If that assumption is false, we will need a very small backend extension later, but the first slice should verify this instead of assuming it.

## Recommended Scope

Keep version 1 intentionally small:

- no Spark yet
- no tournament brackets yet
- no graphical dashboard yet
- no multiple databases
- no Kafka

Just:

- create experiments
- run batches of CvC games
- wait for completion
- record winners / draws / move counts / durations
- show standings in a text UI

## Service Responsibilities

`match-runner-service` should own:

- experiment definitions
- matchup generation
- batch execution
- progress tracking
- result persistence
- aggregated comparison views

It should **not** own:

- chess rules
- move generation
- board legality
- strategy implementation

Those remain in the chess backend, which is the system under test.

## Proposed Architecture

### Runtime

- `match-runner-service`
  - Scala service
  - HTTP client to chess API
  - PostgreSQL persistence
  - TUI for local operation

### Internal Modules inside the new sbt project

- `domain`
  - `Experiment`
  - `Matchup`
  - `MatchRun`
  - `MatchResult`
  - `AggregateStats`
- `application`
  - `ExperimentRunner`
  - `MatchScheduler`
  - `ResultAggregator`
  - `ProgressTracker`
- `http`
  - `ChessApiClient`
  - optional service routes later
- `data`
  - repository traits
  - Postgres implementations
- `tui`
  - command-line experiment control

## Suggested Data Model

### Table: `match_experiments`

- `id`
- `name`
- `description`
- `created_at`
- `status`
- `requested_games`

### Table: `match_runs`

- `id`
- `experiment_id`
- `chess_game_id`
- `white_strategy`
- `black_strategy`
- `started_at`
- `finished_at`
- `result`
- `winner`
- `move_count`
- `final_fen`
- `pgn`
- `error_message`

### Table: `match_aggregates`

Optional in v1.

Could also be derived on query.

Fields if materialized:

- `experiment_id`
- `white_strategy`
- `black_strategy`
- `games`
- `white_wins`
- `black_wins`
- `draws`
- `average_moves`
- `average_duration_ms`

## Proposed Execution Model

### Passive Runner Model

For each match:

1. create a new backend game configured as CvC
2. store the returned `gameId`
3. poll `GET /games/:id`
4. detect completion from returned status / game state
5. persist the final result

This only works if the backend really advances CvC games on its own after creation.

### Early Verification Slice

Before building the full service, verify:

- if a game is created with `whiteIsHuman=false` and `blackIsHuman=false`
- does the backend eventually reach a terminal state without any further external move calls?

If yes:
- proceed with passive runner

If no:
- document that the backend needs a minimal autonomous match loop
- revisit the passive assumption

## Project Slices

### Slice 0: Feasibility Check

Goal:

- verify whether passive CvC execution is already supported by the chess backend

Deliverables:

- tiny Scala script or test
- create one CvC game over HTTP
- poll until timeout or finish
- record observed behavior

Success criterion:

- we know whether passive mode is viable

### Slice 1: New Module Skeleton

Goal:

- create `match-runner` sbt module and basic Docker/runtime wiring

Deliverables:

- new sbt project
- module directories
- basic `main`
- config loading
- docker container entry

Success criterion:

- service compiles and starts

Implementation notes:

- sbt module: `MatchRunner`
- runtime main: `chess.matchrunner.MatchRunnerServer`
- current HTTP surface: `GET /health`
- current config:
  - `PORT`
  - `CHESS_API_URL`
  - `POSTGRES_HOST`
  - `POSTGRES_PORT`
  - `POSTGRES_DATABASE`
  - `POSTGRES_USER`
  - `POSTGRES_PASSWORD`
- Docker wiring:
  - `Dockerfile.match-runner-service`
  - `match-runner-service` in `docker-compose.yml`

Current Slice 1 status:

- module exists
- server exists
- health route exists
- compose entry exists
- next step is Slice 2: typed chess HTTP client

### Slice 2: Chess HTTP Client

Goal:

- clean typed Scala client for the chess backend

Endpoints needed initially:

- create game
- get game state

Possibly later:

- list games
- delete game

Success criterion:

- client tested with mocked HTTP responses

Implementation notes:

- client trait: `ChessApiClient`
- first concrete implementation: `HttpChessApiClient`
- current supported operations:
  - create game
  - create passive CvC game
  - get game state
- tests use mocked http4s routes instead of a live backend

### Slice 3: Experiment Domain + Persistence

Goal:

- store experiment and match results in PostgreSQL

Deliverables:

- repositories
- schema creation
- save / query methods
- aggregate query support

Success criterion:

- experiment and match results survive restarts

Implementation notes:

- persistence stack: Doobie + PostgreSQL
- current domain:
  - `Experiment`
  - `ExperimentStatus`
  - `MatchRun`
  - `MatchResult`
- current repository:
  - `PostgresMatchRunnerRepository`
- current schema:
  - `match_experiments`
  - `match_runs`

### Slice 4: Batch Runner

Goal:

- run one experiment with many games

Behavior:

- generate match list
- create backend games
- poll for completion
- persist final outcomes

Success criterion:

- one strategy matchup can be run in batch and produces persisted results

Implementation notes:

- current application service: `ExperimentRunner`
- current behavior:
  - create experiment with `Running` status
  - create passive CvC games through `ChessApiClient`
  - poll `GET /games/:id` until terminal human-readable status
  - persist enriched `MatchRun` rows
  - mark experiment `Completed` or `Failed`
- current terminal detection supports:
  - `Checkmate! White wins!`
  - `Checkmate! Black wins!`
  - `Stalemate! The game is a draw.`
  - `Draw by threefold repetition.`
  - timeout / client errors

### Slice 5: TUI

Goal:

- simple operator interface

Features:

- create experiment
- select white / black strategies
- choose number of repetitions
- start run
- watch progress
- inspect summary table

Success criterion:

- experiments can be launched and reviewed from terminal

Implementation notes:

- entrypoint: `chess.matchrunner.MatchRunnerTuiApp`
- shell: `MatchRunnerShell`
- current features:
  - create experiment interactively
  - choose white / black strategy
  - choose number of games
  - run batch
  - show per-game completion lines
  - print final summary
  - list persisted experiments

### Slice 6: Comparison Features

Goal:

- make results useful for strategy evolution

Features:

- paired color-swapped comparisons
- win/draw/loss summary
- average move count
- strategy-vs-strategy matrix

Success criterion:

- can compare strategy A vs B fairly

Implementation notes:

- `ExperimentRequest` gains `mirroredPairs: Boolean = false`
- when `mirroredPairs = true`, `ExperimentRunner` runs N games A vs B then N games B vs A (2N total)
- `ExperimentSummary` gains `directions: List[DirectionStats]` — per (white, black) strategy pair breakdown
- `DirectionStats`: whiteWins, blackWins, draws, errors, averageMoves, averageGameMs per direction
- TUI prompt asks "Run mirrored pairs?" and shows per-direction table + combined win-rate matrix when mirrored
- `ExperimentRunner` no longer uses `updateExperimentStatus`; final experiment saved via `saveExperiment` upsert

### Slice 7: Service HTTP API

Goal:

- make match-runner-service observable by other tools and later UI

Possible endpoints:

- `POST /experiments`
- `POST /experiments/:id/start`
- `GET /experiments/:id`
- `GET /experiments/:id/runs`
- `GET /experiments/:id/summary`

Success criterion:

- external clients can query progress and results

Implementation notes:

- new files: `ExperimentApiModels.scala` (Circe codecs for domain types), `ExperimentRoutes.scala`
- `MatchRunnerServer` now builds the full resource stack (Postgres, HTTP client, runner, routes)
- `POST /experiments` → `202 Accepted` + initial `Experiment` JSON; batch runs in a background fiber via `startAsync`
- `ExperimentRunner.startAsync` saves the experiment record, forks `runBatches`, returns immediately
- `ExperimentRunner.runExperiment` (TUI path) is unchanged — still blocking/sequential
- route middleware: `Logger.httpApp[IO]` for request/response logging
- `<+>` route combining via `cats.syntax.semigroupk.*`

### Slice 8: Graphical UI Later

Goal:

- richer operator / analysis UI

Not for the first iteration.

## Testing Strategy

### Unit Tests

Test:

- matchup generation
- aggregate scoring
- result classification
- experiment state transitions

### HTTP Client Tests

Test:

- JSON decoding
- HTTP failure handling
- retry / timeout behavior

### Integration Tests

Test:

- service against the real chess backend
- service against real PostgreSQL
- at least one complete experiment run

### End-to-End Test

Optional later:

- start chess stack plus match-runner stack
- run a tiny experiment
- verify DB records and summary output

## Initial Technology Choice

Recommended:

- Scala 3
- Cats Effect
- http4s client/server
- Doobie or Slick for PostgreSQL

Because the project already uses Cats Effect and http4s heavily, staying with those is the lowest-friction choice.

For persistence, either works.

Recommendation:

- Doobie if we want smaller, clearer SQL-centered code
- Slick if we want stronger continuity with the persistence lecture ideas

## Risks

### Risk 1: Passive mode may not yet be supported

This is the biggest risk and should be validated first.

### Risk 2: Strategy comparison may be biased by color

Mitigation:

- always run mirrored pairings with swapped colors

### Risk 3: Long-running batches may need resumability

Not needed in v1, but experiment status should still be persisted.

### Risk 4: Current chess API may not expose terminal result strongly enough

If needed, add a small backend response improvement later.

## Suggested First Implementation Order

1. Slice 0 feasibility check
2. Slice 1 module skeleton
3. Slice 2 typed chess HTTP client
4. Slice 3 PostgreSQL schema + repositories
5. Slice 4 simple batch runner for one fixed matchup
6. Slice 5 TUI

## Minimal v1 Definition of Done

The first useful version is complete when:

- a new `match-runner-service` module exists
- it runs in Docker
- it can create an experiment for one strategy pair
- it can run N CvC games passively through the chess API
- it stores results in PostgreSQL
- it provides a terminal summary of wins / draws / losses
- it has unit and integration tests

## Open Questions for the Start of Implementation

1. Does backend-created CvC really progress to completion without extra calls?
2. Which persistence stack do we want for the new module: Doobie or Slick?
3. Should the service start with TUI only, or should we add a tiny read-only HTTP endpoint immediately?

My recommendation:

- verify passive CvC first
- use PostgreSQL only
- start with TUI only
