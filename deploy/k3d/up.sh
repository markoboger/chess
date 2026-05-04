#!/usr/bin/env bash
# One-shot: apply k8s manifests, build images, import into k3d, restart workloads.
# Prereqs: k3d cluster "chess" exists, K3D_CONFIG set (direnv or export), Docker running, repo-root .env for secrets.
#
# Usage from repo root:
#   ./deploy/k3d/up.sh
#
# Builds images via `docker compose build` by default (reuses docker-compose.yml `platform: linux/amd64`
# for Scala services — avoids OpenJFX "linux-aarch64" missing-jar errors on Apple Silicon).
# To build with plain Dockerfile + sbt instead: CHESS_K3D_DOCKERFILE_BUILD=1 ./deploy/k3d/up.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CLUSTER="${K3D_CLUSTER_NAME:-chess}"
cd "$ROOT"

# linux/amd64 for CHESS_K3D_DOCKERFILE_BUILD=1 only (matches compose)
PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"

need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "Missing command: $1" >&2; exit 1; }; }
need_cmd kubectl
need_cmd docker
need_cmd k3d

if ! k3d cluster list 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx "$CLUSTER"; then
  echo "k3d cluster '$CLUSTER' not found. Create it first, e.g.:" >&2
  echo "  export K3D_CONFIG=\"\$PWD/deploy/k3d/chess-cluster.yaml\"   # or: direnv allow" >&2
  echo "  k3d cluster create $CLUSTER" >&2
  exit 1
fi

echo "==> Kubernetes: namespace, secrets, workloads"
kubectl apply -f deploy/k8s/base/namespace.yaml
./deploy/k8s/apply-secrets-from-env.sh
kubectl apply -f deploy/k8s/base/mongodb.yaml \
  -f deploy/k8s/base/postgres.yaml \
  -f deploy/k8s/base/game-service.yaml \
  -f deploy/k8s/base/api-gateway.yaml \
  -f deploy/k8s/base/vue-ui.yaml \
  -f deploy/k8s/base/match-runner.yaml \
  -f deploy/k8s/base/ingress.yaml

if [[ "${CHESS_K3D_DOCKERFILE_BUILD:-}" == "1" ]]; then
  echo "==> Docker build from Dockerfiles ($PLATFORM)"
  docker build --platform "$PLATFORM" -t chess-game-service:local -f Dockerfile.game-service .
  docker build --platform "$PLATFORM" -t chess-api-gateway:local -f Dockerfile.gateway .
  docker build --platform "$PLATFORM" -t chess-vue-ui:local -f Dockerfile.vue-ui .
  docker build --platform "$PLATFORM" -t chess-match-runner:local -f Dockerfile.match-runner-service .
else
  echo "==> docker compose build (see docker-compose.yml platform for JVM services)"
  docker compose build game-service api-gateway vue-ui match-runner-service
  docker tag chess-game-service:latest chess-game-service:local
  docker tag chess-api-gateway:latest chess-api-gateway:local
  docker tag chess-vue-ui:latest chess-vue-ui:local
  docker tag chess-match-runner-service:latest chess-match-runner:local
fi

echo "==> k3d image import -> cluster '$CLUSTER'"
k3d image import chess-game-service:local chess-api-gateway:local chess-vue-ui:local chess-match-runner:local -c "$CLUSTER"

echo "==> Rollout restart"
kubectl rollout restart deployment -n chess

echo ""
echo "Done. Watch: kubectl get pods -n chess -w"
echo "Open:    http://127.0.0.1:9080/"
