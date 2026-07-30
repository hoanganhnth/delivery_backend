# Health, liveness and readiness

Every backend application, including `api-gateway`, runs Actuator management
endpoints on `${MANAGEMENT_SERVER_PORT:9090}`. Canonical Compose exposes this
port only inside `delivery-network`; it is never a host `ports` mapping and the
Gateway has no `/actuator/**` route.

Available probes are:

- `/actuator/health` — aggregate health.
- `/actuator/health/liveness` — process liveness.
- `/actuator/health/readiness` — readiness for traffic. It aggregates every
  registered health contributor, including the service's JDBC, Kafka, Redis or
  Elasticsearch dependency where configured.

Health responses return only the aggregate status. Component diagnostics,
connection strings, credentials, tokens and exception details are deliberately
not returned.

## Local Compose

The image healthcheck requests its own readiness endpoint. Start the stack with
the normal Compose workflow, then inspect status with:

```bash
docker compose ps
docker inspect --format '{{.State.Health.Status}}' api-gateway
```

For an internal probe, run the request from a container on `delivery-network`:

```bash
docker compose exec api-gateway wget -qO- http://localhost:9090/actuator/health/readiness
```

Do not add `9090:9090` to canonical Compose. A temporary diagnostic mapping is
permitted only in an isolated local override and must be removed afterwards.

## Production

Configure the platform healthcheck to call
`http://localhost:${MANAGEMENT_SERVER_PORT:-9090}/actuator/health/readiness`.
Use liveness only to decide restart; use readiness to remove an instance from
traffic. A readiness `DOWN`/HTTP 503 signals an unavailable mandatory
dependency and must not be bypassed by routing through API Gateway.
