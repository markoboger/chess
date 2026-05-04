# k3d (local Kubernetes)

Cluster name: **`chess`**. HTTP ingress is published on the host as **port 9080** → Traefik **80** inside the cluster.

## Option A — bare `k3d cluster create chess` (recommended)

k3d reads extra settings from the file in **`K3D_CONFIG`**. From the **repository root**:

### With [direnv](https://direnv.net/) (automatic when you `cd` into the repo)

```bash
direnv allow   # once per clone, after reviewing .envrc
k3d cluster create chess
```

### Without direnv (one line per shell, or add to `~/.zshrc` / `~/.bashrc`)

```bash
export K3D_CONFIG="$PWD/deploy/k3d/chess-cluster.yaml"
k3d cluster create chess
```

Use an **absolute** path in your profile if you are not starting from the repo root:

```bash
export K3D_CONFIG="/path/to/chess/deploy/k3d/chess-cluster.yaml"
```

Then open the app at **http://127.0.0.1:9080/** after you deploy workloads (see `deploy/k8s/README.md`).

## Option B — helper script

```bash
./deploy/k3d/cluster-create.sh
```

This sets `K3D_CONFIG` for the subprocess and runs `k3d cluster create chess` with no further arguments.

## One command: build images + apply Kubernetes + import into k3d

From repo root (after `direnv allow` or `export K3D_CONFIG=...`, and **once** `k3d cluster create chess`):

```bash
./deploy/k3d/up.sh
```

This applies manifests, reads `.env` into `chess-db-secrets`, builds the four app images (**via `docker compose build`** so the same `platform: linux/amd64` as Compose is used), imports them into the cluster, and restarts deployments.

- Plain Dockerfile builds instead: `CHESS_K3D_DOCKERFILE_BUILD=1 ./deploy/k3d/up.sh` (still passes `--platform linux/amd64`).

### Why bare `docker build` (step 2) failed on some Macs

Inside Docker, the OS is **Linux**. On **Apple Silicon**, the default architecture is often **linux/arm64**. The root `build.sbt` pulls OpenJFX with a **Linux classifier**; **`linux-aarch64` jars are not published** the same way on Maven Central, so `sbt update` fails. Your `docker-compose.yml` already sets **`platform: linux/amd64`** for the JVM services so the resolver uses **`linux` amd64** classifiers instead. `up.sh` uses **Compose builds** by default so you get that behavior without remembering flags.

### k3d vs docker compose

**Compose** is simpler for “run these containers on my laptop.” **Kubernetes** is more moving parts, but the manifests are what you reuse on the **uni server (k3s)**. The script is there so day-to-day local flow is still **one command** after the one-time cluster + direnv setup.

## Teardown

```bash
k3d cluster delete chess
```
