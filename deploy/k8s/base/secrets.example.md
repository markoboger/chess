# Secret `chess-db-secrets` (namespace `chess`)

## From your repo `.env` (recommended)

Use the same variables as [`.env.example`](../../../.env.example): `POSTGRES_*`, `MONGO_USER`, `MONGO_PASSWORD`, `MONGO_DATABASE`.

```bash
./deploy/k8s/apply-secrets-from-env.sh
# or:  ./deploy/k8s/apply-secrets-from-env.sh /path/to/.env
```

The script maps those names onto the Kubernetes secret keys expected by the manifests.

## Manual `kubectl`

```bash
kubectl create secret generic chess-db-secrets -n chess \
  --from-literal=postgres-user='REPLACE' \
  --from-literal=postgres-password='REPLACE' \
  --from-literal=postgres-database='chess' \
  --from-literal=mongo-user='REPLACE' \
  --from-literal=mongo-password='REPLACE' \
  --from-literal=mongo-database='chess'
```

Keys must match the `secretKeyRef` entries in `postgres.yaml`, `mongodb.yaml`, and `match-runner.yaml`.
