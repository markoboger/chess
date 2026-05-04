# Chess stack on Kubernetes (k3d locally, k3s on server)

These manifests mirror `docker-compose.yml`: MongoDB, PostgreSQL, game-service, api-gateway, Vue UI (nginx), and match-runner.

**Target server (your uni):** `141.37.74.153` — use the same YAML on k3s; only networking (Ingress / ports) and image registry differ from k3d.

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [k3d](https://k3d.io/) for local clusters (wraps k3s in Docker)

## 1. Local cluster with k3d

From the **repo root**, enable the bundled k3d config (port **9080** → ingress **80**, `--wait`), then create the cluster **with no extra flags**:

**If you use [direnv](https://direnv.net/):** run `direnv allow` once, then:

```bash
k3d cluster create chess
```

**Otherwise** set `K3D_CONFIG` once per shell (or put it in `~/.zshrc` with an absolute path):

```bash
export K3D_CONFIG="$PWD/deploy/k3d/chess-cluster.yaml"
k3d cluster create chess
```

Details: `deploy/k3d/README.md`. Equivalent helper: `./deploy/k3d/cluster-create.sh`.

## 2. Namespace, secrets, and manifests

Apply the namespace first, **then create the database secret** (MongoDB and PostgreSQL will not start without it), then apply the workloads and Ingress:

```bash
kubectl apply -f deploy/k8s/base/namespace.yaml
```

### 2a. Secrets (PostgreSQL + MongoDB)

Do **not** commit real passwords. If you already maintain a **repo-root `.env`** (see [`.env.example`](../../.env.example)), apply the Kubernetes secret from it:

```bash
./deploy/k8s/apply-secrets-from-env.sh
```

That reads `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DATABASE`, `MONGO_USER`, `MONGO_PASSWORD`, and `MONGO_DATABASE`, and creates/updates `chess-db-secrets` with the key names the YAML manifests expect.

**Manual alternative** (no `.env`): see [deploy/k8s/base/secrets.example.md](base/secrets.example.md). If the secret already exists and you need a clean recreate: `kubectl delete secret chess-db-secrets -n chess`.

### 2b. Workloads and Ingress

```bash
kubectl apply -f deploy/k8s/base/mongodb.yaml \
  -f deploy/k8s/base/postgres.yaml \
  -f deploy/k8s/base/game-service.yaml \
  -f deploy/k8s/base/api-gateway.yaml \
  -f deploy/k8s/base/vue-ui.yaml \
  -f deploy/k8s/base/match-runner.yaml \
  -f deploy/k8s/base/ingress.yaml
```

Pods may sit in `ImagePullBackOff` or `ErrImageNeverPull` until step 3 completes—that is expected.

## 3. Build images and load them into k3d

k3d does not build images for you. Build on the host, then import into the cluster (image names must match the manifests: `chess-*:local`):

```bash
docker build -t chess-game-service:local -f Dockerfile.game-service .
docker build -t chess-api-gateway:local -f Dockerfile.gateway .
docker build -t chess-vue-ui:local -f Dockerfile.vue-ui .
docker build -t chess-match-runner:local -f Dockerfile.match-runner-service .

k3d image import chess-game-service:local chess-api-gateway:local chess-vue-ui:local chess-match-runner:local -c chess
```

Restart workloads so they pick up newly imported tags:

```bash
kubectl rollout restart deployment -n chess
```

## 4. Open the app (local)

After pods are ready (`kubectl get pods -n chess`), Traefik is exposed on **http://127.0.0.1:9080** (from the `k3d cluster create` port mapping).

The Ingress routes all paths to **vue-ui**; the UI nginx proxies `/api/` to **api-gateway** inside the cluster (see `frontend/nginx.conf`).

```bash
kubectl get ingress -n chess
curl -sS -o /dev/null -w "%{http_code}\n" http://127.0.0.1:9080/
```

Open in a browser: **http://127.0.0.1:9080/**

## 5. Move to the university server (k3s on `141.37.74.153`)

On the server (Linux with k3s installed — see [k3s.io](https://k3s.io/)):

1. **Copy this repo** (or CI artifact) to the server.
2. **Install k3s** (single-node is fine to start): `curl -sfL https://get.k3s.io | sh -`
3. Use kubeconfig: `sudo kubectl` or copy `/etc/rancher/k3s/k3s.yaml` locally and set `KUBECONFIG`.

**Images:** k3d’s `image import` does not exist on a real server. Choose one:

- **Build on the server** with the same `docker build` commands (Docker + buildkit), **or**
- **Push to a registry** (GitHub Container Registry, university registry) and change the `image:` fields in the deployments to e.g. `ghcr.io/yourorg/chess-game-service:0.1.0`.

Then (same order as local: namespace → secret → manifests):

```bash
kubectl apply -f deploy/k8s/base/namespace.yaml
./deploy/k8s/apply-secrets-from-env.sh   # or create the secret manually (section 2)
kubectl apply -f deploy/k8s/base/mongodb.yaml \
  -f deploy/k8s/base/postgres.yaml \
  -f deploy/k8s/base/game-service.yaml \
  -f deploy/k8s/base/api-gateway.yaml \
  -f deploy/k8s/base/vue-ui.yaml \
  -f deploy/k8s/base/match-runner.yaml \
  -f deploy/k8s/base/ingress.yaml
```

**Ingress / DNS:**

- The default `ingress.yaml` uses a **catch-all rule** (no `host`), which is enough for **IP:port** access on k3d (`http://127.0.0.1:9080/`).
- On the server, point a DNS name to **`141.37.74.153`**, or add a `host` rule such as **`141.37.74.153.nip.io`** (see [nip.io](https://nip.io)) and browse `http://141.37.74.153.nip.io` if your Ingress is reachable on port 80.
- If the uni only exposes **high ports**, map them with `k3d`-style port publishing on the server load balancer / `iptables`, or switch the Ingress controller to a **NodePort** Service (ask if you want that variant checked in).

k3s ships Traefik by default (similar to k3d). If your server uses plain **NodePort** instead, we can add a `NodePort` Service for `vue-ui`; say if you want that variant.

## 6. Tear down local k3d

```bash
k3d cluster delete chess
```

## Files

| File | Role |
|------|------|
| `namespace.yaml` | Namespace `chess` |
| `apply-secrets-from-env.sh` | Create `chess-db-secrets` from repo `.env` |
| `secrets.example.md` | Reminder for secret keys (no real secrets) |
| `mongodb.yaml` | MongoDB Deployment + Service |
| `postgres.yaml` | PostgreSQL Deployment + Service + PVC |
| `game-service.yaml` | Game HTTP API |
| `api-gateway.yaml` | Gateway → game-service |
| `vue-ui.yaml` | Static UI + `/api` proxy to gateway |
| `match-runner.yaml` | Match runner → Postgres + game-service |
| `ingress.yaml` | Routes external HTTP to `vue-ui` |

## Notes

- **Realtime WebSocket** (`:8083`) is not in `docker-compose.yml` / these manifests; add a Deployment if you need it in k8s.
- **TLS:** for production, add cert-manager or university TLS termination in front of Ingress.
- **Persistence:** MongoDB and Postgres use a **1Gi** PVC each; adjust sizes in the YAML for your server quota.
