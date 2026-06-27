#!/usr/bin/env bash
# Create k3d cluster on the university host (k3s inside Docker).
# From repo root on 141.37.123.133:
#   ./deploy/k3d/cluster-create-server.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export K3D_CONFIG="${K3D_CONFIG:-$ROOT/deploy/k3d/chess-cluster.server.yaml}"
CLUSTER="${K3D_CLUSTER_NAME:-chess}"

cd "$ROOT"

if k3d cluster list 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx "$CLUSTER"; then
  echo "Cluster '$CLUSTER' already exists. Delete with: k3d cluster delete $CLUSTER"
  exit 0
fi

echo "K3D_CONFIG=$K3D_CONFIG"
echo "Creating k3d cluster '$CLUSTER' (k3s in Docker, host :9080 -> ingress :80)..."
k3d cluster create "$CLUSTER"

echo ""
echo "Cluster ready. Next:"
echo "  cp .env.example .env   # edit passwords"
echo "  ./deploy/k3d/up.sh"
echo "Open: http://141.37.123.133:9080/"
