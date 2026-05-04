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

## Teardown

```bash
k3d cluster delete chess
```
