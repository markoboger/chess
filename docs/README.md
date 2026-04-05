# Architecture Documentation

The architecture is described using the [Structurizr DSL](https://docs.structurizr.com/dsl) in `workspace.dsl`,
following the [C4 model](https://c4model.com/) (Context → Containers → Components).

## Diagrams included

| View | Description |
|------|-------------|
| **SystemContext** | Chess Application in relation to users and the Lichess dataset |
| **Containers** | The five runtime processes: Standalone App, API Gateway, Game Service, UI Service, and the two databases |
| **StandaloneComponents** | Internal structure of the desktop application (GUI, engine, strategies, I/O parsers, opening book) |
| **GatewayComponents** | Internal structure of the API Gateway (routes, proxy, config) |
| **GameServiceComponents** | Internal structure of the Game Service (routes, service, engine, repository, models) |

## Quickstart — Structurizr Lite (Docker, no account needed)

```bash
docker pull structurizr/lite
docker run -it --rm -p 8080:8080 \
  -v "$(pwd)/docs":/usr/local/structurizr \
  structurizr/lite
```

Open <http://localhost:8080> in your browser.  
All five diagrams will be available in the left-hand panel.

## Structurizr CLI (export to PNG/SVG/PlantUML)

```bash
# Install
brew install structurizr-cli        # macOS
# or download from https://github.com/structurizr/cli/releases

# Export all views to PlantUML
structurizr-cli export -workspace docs/workspace.dsl -format plantuml -output docs/diagrams

# Export all views to PNG (requires a Structurizr account or Lite running)
structurizr-cli export -workspace docs/workspace.dsl -format png -output docs/diagrams
```

## Online editor (no install)

Paste the contents of `workspace.dsl` into <https://structurizr.com/dsl> for an instant preview.
No account required for the online DSL editor.
