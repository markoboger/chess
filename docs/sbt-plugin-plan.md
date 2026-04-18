# Plan: sbt plugin for Ilograph YAML generation

## Goal

Create an **sbt plugin** that generates **Ilograph YAML** (e.g. `ilograph.yaml`) from a **multi-module Scala sbt build** by analyzing project structure and Scala sources, so it is reusable across repositories and easy for students to adopt.

## High-level design

- **Split into two artifacts**
  - **`ilograph-core` (Scala 3 library)**: pure logic that turns *inputs* (project graph + source files) into an Ilograph YAML string.
  - **`sbt-ilograph` (sbt plugin, Scala 2.12)**: gathers sbt build data and source paths, calls `ilograph-core`, writes the output file(s).

This keeps the plugin thin and makes the core reusable from a CLI later.

## Deliverables

### `ilograph-core`

- Model types: build meta, projects, packages, members, relations.
- Parser/analyzer: scan Scala files, infer relations (imports, calls, implemented-by, companion, observes, etc.).
- Renderer: output valid Ilograph YAML.
- Unit tests with fixture Scala snippets.

### `sbt-ilograph`

- `AutoPlugin` with tasks and settings.
- Scripted tests (`sbt-test`) validating generation on sample builds.
- Documentation: quick start, config reference, CI example.

## Plugin UX (what the user experiences)

### Commands

- `ilographGenerate` — generate YAML for the current build
- `ilographGenerateAll` — optional; generate per subproject or per selected set
- `ilographOpenOutput` — optional; prints the output path(s)

### Output behavior (defaults)

- Writes to: `target/ilograph/ilograph.yaml` (or configurable)
- Optionally also write to `docs/ilograph.yaml` if the user wants committed docs
- Deterministic filename by default (timestamps optional)

### Settings (config surface)

- `ilographOutputDir: File`
- `ilographOutputFileName: String` (default `ilograph.yaml`)
- `ilographIncludeProjects: Set[String]` / `ilographExcludeProjects: Set[String]`
- `ilographIncludeTests: Boolean` (default false)
- `ilographMaxMembersPerPackage: Int` (or similar guardrails)
- `ilographRelationToggles` (enable/disable inference types like calls/observes)
- `ilographIconTheme` (optional)
- `ilographPackageRootFilter: Set[String]` (optional; focus on `chess.*` etc.)

## Data sources (replace script `build.sbt` regex parsing)

Use sbt’s build model instead of reading `build.sbt` as text:

- **Build metadata**: `organization.value`, `version.value`, `scalaVersion.value`
- **Project graph**: `thisProjectRef.value`, `loadedBuild.value`, `buildStructure.value` (or `Project.extract(state).structure`)
  - Project id: `thisProject.value.id` / iterate all `ProjectRef`s
  - Base dir: `baseDirectory.value`
  - Dependencies: `thisProject.value.dependencies` (project-to-project)
- **Source roots and sources**: `Compile / unmanagedSourceDirectories` and/or `Compile / sources`
  - Exclude tests unless configured: `Test / sources`

## Core analysis (port from `generate-ilograph.sc`)

Refactor the script logic into `ilograph-core` modules:

- **Source reading**: normalize newlines, strip comments (best-effort)
- **Discover packages**: `package x.y.z`
- **Discover members**: `class|trait|object|enum` definitions (support Scala 3 indentation)
  - Track kind and basic doc summary (optional)
  - Companion handling (rename objects if needed, create `companion` relation)
- **Relations**
  - **Package imports**: infer package dependency edges from `import ...`
  - **Member implements**: from `extends/with` (resolve types to member ids)
  - **Member calls**: from constructor param types (existing heuristic)
  - **Observer relationship**: `Y extends Observer[X]` → `Y observes X` (already implemented in script; port as-is)
- **Rendering**: produce Ilograph YAML with `resources` tree (Build → projects → packages → members) and `perspectives` (per project packages; relations for imports/declares/calls/implements/observes/companion); stable ids (e.g. `pkg~Member`)

## sbt plugin implementation steps

1. **Create a new repo or subproject**  
   Recommended: separate repo with multi-module build: `ilograph-core` (Scala 3) and `sbt-ilograph` (sbt plugin). Use `sbt` with `inThisBuild(...)` metadata and publish settings.

2. **Move script logic into `ilograph-core`**  
   Turn `object GenerateIlograph` into library entrypoints such as `IlographGenerator.generate(input: BuildInput): String`. Replace “read build.sbt” with structured inputs.

3. **Implement `IlographPlugin`**  
   `object IlographPlugin extends AutoPlugin`; define `autoImport` keys (task + settings); task collects all projects and their `Compile / sources`; convert sbt inputs into `BuildInput` and call core.

4. **Write files**  
   Use `IO.write(outputFile, yaml, utf8)`; create directories if missing; print a friendly message with relative path.

5. **Scripted tests**  
   Add `sbt-ilograph/src/sbt-test/ilograph/simple/`: minimal build with two subprojects and a few Scala files. Assert task runs, output exists, and output contains expected resource ids / relation labels.

6. **Docs**  
   README for the plugin: install (`addSbtPlugin`), run task, settings examples, sample GitHub Actions snippet.

## Optional: Ilograph upload integration (future)

Do **not** bundle cloud upload by default (secrets, IP allowlist, Team+ requirement). If added:

- Separate task `ilographUpload` that reads generated YAML and `PUT`s to `https://api.ilograph.com/m0/@Workspace/path/to/diagram` (see [Team Workspace API](https://ilograph.com/docs/team-workspace-api)).
- Requires `accessKey` via env var and a configured diagram path.
- Make it explicitly opt-in and document limitations (diagram must pre-exist; IP allowlist).

## Risks / gotchas

- **Plugin Scala version**: sbt plugins typically compile with **Scala 2.12**; keep plugin code small and push logic into the Scala 3 core library.
- **Type resolution**: current heuristics resolve by simple name only if unambiguous; document this limitation.
- **Performance**: scanning all `Compile / sources` across modules can be slow; add caching or incremental checks later (e.g. compare file mtimes, or hash source contents).
- **Scala syntax coverage**: regex-based parsing is heuristic; keep it “good enough” for architecture diagrams, not compiler-accurate.

## Suggested milestone breakdown

- **M1 (MVP)**: `ilographGenerate` produces a single YAML from a simple multi-module build (no upload).
- **M2 (Quality)**: settings, deterministic output, scripted tests, docs.
- **M3 (Nice-to-have)**: per-project outputs, caching, optional upload task, CLI wrapper.
