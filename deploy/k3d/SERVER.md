# Deploy on 141.37.123.133 (k3d → k3s inside Docker)

Stack: MongoDB, PostgreSQL, game-service, api-gateway, vue-ui, match-runner.  
Tournament API stays external at **http://141.37.123.132:8086** (VPN + CORS).

**App URL after deploy:** http://141.37.123.133:9080/

## One-time on the server

```bash
# 1) Clone (or git pull)
git clone <your-repo-url> chess && cd chess

# 2) Host tools (Docker, k3d, kubectl) — run once with sudo
sudo ./deploy/k3d/install-host-deps.sh
# log out/in so docker group applies

# 3) Secrets
cp .env.example .env
# edit POSTGRES_PASSWORD, MONGO_PASSWORD (required)

# 4) Full deploy (creates k3d cluster if missing, builds, imports, applies)
./deploy/k3d/bootstrap-server.sh
```

`bootstrap-server.sh` sets:

- `K3D_CONFIG=deploy/k3d/chess-cluster.server.yaml` (host **9080** → Traefik **80**)
- `CHESS_K3D_DOCKERFILE_BUILD=1` (native `docker build`, linux/amd64)
- `VITE_TOURNAMENT_API_URL` from `.env` or default **132:8086**

## Updates (after git pull)

```bash
cd chess
export K3D_CONFIG="$PWD/deploy/k3d/chess-cluster.server.yaml"
export CHESS_PUBLIC_HOST=141.37.123.133
./deploy/k3d/up.sh
```

Or re-run `./deploy/k3d/bootstrap-server.sh` (skips cluster create if it exists).

## Verify

```bash
kubectl get pods -n chess
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:9080/
curl -sS http://127.0.0.1:9080/api/games  # may 401/404 depending on API; UI should load
```

Browser (VPN for tournament): http://141.37.123.133:9080/ → **Tournaments** tab.

## Tournament CORS (required)

On **141.37.123.132**, allow browser origin:

`http://141.37.123.133:9080`

Without this, the UI loads but tournament API calls fail from the browser.

## Teardown

```bash
k3d cluster delete chess
```

## Files

| Script | Purpose |
|--------|---------|
| `install-host-deps.sh` | Docker + k3d + kubectl (sudo) |
| `chess-cluster.server.yaml` | k3d port map 9080:80 |
| `cluster-create-server.sh` | `k3d cluster create chess` |
| `bootstrap-server.sh` | First-time cluster + `up.sh` |
| `up.sh` | Manifests + build + import + rollout |

## Notes

- **k3d only** — we do not install standalone k3s on the host; k3s runs inside k3d’s containers.
- **Realtime WebSocket** (`:8083`) is not in the k8s manifests yet; local-game live WS won’t work until added.
- **Firewall:** open TCP **9080** on 133 for campus access.
