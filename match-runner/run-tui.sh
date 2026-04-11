#!/usr/bin/env bash
# Starts the Match Runner TUI (interactive shell).
# Loads env vars from the project root .env if present.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."

# Load .env from project root if it exists
if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ROOT/.env"
  set +a
fi

cd "$ROOT"
exec sbt "MatchRunner/runMain chess.matchrunner.MatchRunnerTuiMain"
