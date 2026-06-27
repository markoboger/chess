#!/usr/bin/env bash
# Full first-time deploy on 141.37.123.133: k3d (k3s in Docker) + build + apply.
# Run on the server from repo root after git clone and .env setup.
#
#   cp .env.example .env && $EDITOR .env
#   ./deploy/k3d/bootstrap-server.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

export K3D_CONFIG="${K3D_CONFIG:-$ROOT/deploy/k3d/chess-cluster.server.yaml}"
CLUSTER="${K3D_CLUSTER_NAME:-chess}"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing: $1 — run ./deploy/k3d/install-host-deps.sh first" >&2
    exit 1
  }
}

need docker
need k3d
need kubectl

if [[ ! -f "$ROOT/.env" ]]; then
  echo "Missing $ROOT/.env — copy from .env.example and set passwords." >&2
  exit 1
fi

if ! k3d cluster list 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx "$CLUSTER"; then
  ./deploy/k3d/cluster-create-server.sh
fi

# Server builds are always linux/amd64
export DOCKER_PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"
export CHESS_K3D_DOCKERFILE_BUILD="${CHESS_K3D_DOCKERFILE_BUILD:-1}"
export CHESS_PUBLIC_HOST="${CHESS_PUBLIC_HOST:-141.37.123.133}"

./deploy/k3d/up.sh

echo ""
echo "Deployed. Verify:"
echo "  kubectl get pods -n chess"
echo "  curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9080/"
echo "Browser: http://141.37.123.133:9080/"
echo "Tournament API (external): ensure CORS allows http://141.37.123.133:9080 on 141.37.123.132:8086"
