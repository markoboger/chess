#!/usr/bin/env bash
# Create a local k3d cluster for the chess stack (same k3s Traefik ingress as production k3s).
set -euo pipefail
CLUSTER_NAME="${K3D_CLUSTER_NAME:-chess}"
HTTP_PORT="${CHESS_K3D_HTTP_PORT:-9080}"

echo "Creating k3d cluster '${CLUSTER_NAME}' (map http://127.0.0.1:${HTTP_PORT} -> ingress:80)..."
k3d cluster create "${CLUSTER_NAME}" \
  --port "${HTTP_PORT}:80@loadbalancer:0" \
  --wait

echo "Done. Context: $(kubectl config current-context 2>/dev/null || true)"
echo "Next: follow deploy/k8s/README.md (secrets, apply manifests, docker build, k3d image import)."
