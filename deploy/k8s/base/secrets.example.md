# Secret `chess-db-secrets` (namespace `chess`)

Create with:

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
