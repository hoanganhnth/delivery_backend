# Simulator Service

`simulator-service` is a dev/test-only Scenario Lab runner. It is intentionally
not registered in the public Gateway route table and is disabled by default.

## Local run

```sh
SIMULATOR_ENABLED=true \
  SIMULATOR_GATEWAY_BASE_URL=http://127.0.0.1:8079 \
  mvn -pl simulator-service spring-boot:run
```

For the isolated Compose stack, package the module first and use the separate
overlay:

```sh
mvn -pl simulator-service -am package
docker compose -f docker-compose.yml -f docker-compose.secrets.yml \
  -f docker-compose.simulator.yml up -d simulator-service
```

The runner accepts real actor access tokens in the request body only in the
isolated test environment. Tokens are removed from every response and timeline.
Set `SIMULATOR_API_TOKEN` when the console must authenticate to the runner.

## API

- `POST /api/simulator/validate`
- `POST /api/simulator/runs`
- `GET /api/simulator/runs/{runId}`
- `GET /api/simulator/runs/{runId}/algorithm-traces`
- `GET /api/simulator/runs/{runId}/stream` (SSE)
- `POST /api/simulator/runs/{runId}/pause|resume|abort`
- `DELETE /api/simulator/runs/{runId}`

The current MVP performs real Gateway checkout-quote/order, restaurant,
delivery actions and Tracking REST location updates. It sends a server-issued
checkout quote plus UUID idempotency key when the Gateway returns a quote, so
the runner can be used with quote enforcement enabled in an isolated stack.
The runner now has a read-only Kafka observer for Match's
`matching.decision-trace` source. It correlates versioned `nearest-cod-v1`
decisions by both order and delivery IDs and exposes them in the endpoint/SSE
snapshot. It does not consume or mutate any business aggregate. Settlement/DB
ledger observation and durable run storage remain later phases; such assertions
are reported as `SKIPPED`/`PARTIAL`, never silently passed.

Runner authentication uses the `X-Simulator-Token` request header when
`SIMULATOR_API_TOKEN` is configured. SSE deliberately does not accept a
query-string token, so credentials do not leak into browser history or proxy
logs. Direct browser access is limited to the local console origins in
`SIMULATOR_ALLOWED_ORIGINS`; keep the list explicit when hosting the console
on another test-only origin.

For the complete production-like synthetic environment, use the backend
runbook [`docs/runbooks/production-like-sandbox.md`](../docs/runbooks/production-like-sandbox.md)
and run `bash scripts/sandbox-up.sh` from `backend_delivery/`. The sandbox
starts the real Gateway/Kafka/Saga/Match/Delivery/Tracking graph with
run-scoped secrets and volumes; do not point this service at a shared
staging/production target.
