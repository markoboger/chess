#!/usr/bin/env bash
# Create/update chess-db-secrets from a .env file (same keys as .env.example).
# Usage:
#   ./deploy/k8s/apply-secrets-from-env.sh
#   ./deploy/k8s/apply-secrets-from-env.sh /path/to/.env
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${1:-$ROOT/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  echo "Copy .env.example to .env in the repo root and fill in credentials." >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

: "${POSTGRES_USER:?Set POSTGRES_USER in $ENV_FILE}"
: "${POSTGRES_PASSWORD:?Set POSTGRES_PASSWORD in $ENV_FILE}"
: "${POSTGRES_DATABASE:?Set POSTGRES_DATABASE in $ENV_FILE}"
: "${MONGO_USER:?Set MONGO_USER in $ENV_FILE}"
: "${MONGO_PASSWORD:?Set MONGO_PASSWORD in $ENV_FILE}"
: "${MONGO_DATABASE:?Set MONGO_DATABASE in $ENV_FILE}"

if ! kubectl get namespace chess >/dev/null 2>&1; then
  echo "Namespace chess not found; creating it..." >&2
  kubectl apply -f "$ROOT/deploy/k8s/base/namespace.yaml"
fi

kubectl create secret generic chess-db-secrets -n chess \
  --from-literal=postgres-user="$POSTGRES_USER" \
  --from-literal=postgres-password="$POSTGRES_PASSWORD" \
  --from-literal=postgres-database="$POSTGRES_DATABASE" \
  --from-literal=mongo-user="$MONGO_USER" \
  --from-literal=mongo-password="$MONGO_PASSWORD" \
  --from-literal=mongo-database="$MONGO_DATABASE" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Applied secret chess-db-secrets in namespace chess (from $ENV_FILE)."
