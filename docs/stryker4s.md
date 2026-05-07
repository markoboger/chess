# Stryker4s (mutation testing)

This repo uses [Stryker4s](https://stryker-mutator.io/docs/stryker4s/getting-started) to mutation-test our test suites.

## Why

Mutation testing helps detect **tests that pass for the wrong reasons** by introducing small changes ("mutants") to production code and verifying the tests fail.

## Install / prerequisites

- The sbt plugin is declared in `project/plugins.sbt`.
- **sbt >= 1.11.2** is required by Stryker4s (see `project/build.properties`).

## Quick start (Core only)

The first integration focuses on the pure chess logic in `core/`.

Run from repo root:

```bash
sbt "project Core" "stryker"
```

Reports are written under:

- `core/target/stryker4s-report/<timestamp>/index.html`

## Current defaults

- **Scope**: `core/src/main/scala/**/*.scala`
- **Excluded mutations**: `StringLiteral` (configured via `Core / strykerExcludedMutations` in `build.sbt`)
- **Concurrency**: 2
- **Timeout**: net test time × 2.0 + 5s

## Next steps

Once Core is stable, we can expand to:

- `app/` strategies and application services (avoid flaky integration tests at first)
- `realtime/` and HTTP routes (likely needs careful `test-filter` configuration)

