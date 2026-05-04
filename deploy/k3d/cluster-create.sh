#!/usr/bin/env bash
# Create the local k3d cluster using deploy/k3d/chess-cluster.yaml (port 9080 -> ingress 80, --wait).
# Same as: export K3D_CONFIG=.../chess-cluster.yaml && k3d cluster create chess
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export K3D_CONFIG="${ROOT}/deploy/k3d/chess-cluster.yaml"

echo "Using K3D_CONFIG=${K3D_CONFIG}"
echo "Running: k3d cluster create chess"
k3d cluster create chess

echo "Done. Context: $(kubectl config current-context 2>/dev/null || true)"
echo "Next: follow deploy/k8s/README.md (secrets, apply manifests, docker build, k3d image import)."
