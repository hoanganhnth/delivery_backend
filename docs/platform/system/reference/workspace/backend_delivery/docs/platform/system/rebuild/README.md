# Clean-Room Reconstruction Guide

> Goal: recreate a platform with the same externally important behavior,
> security boundaries and recovery properties from this documentation. This is
> not a command to copy production data, secrets or provider credentials.

## 1. Define the compatible system

Before writing code, freeze these contracts:

- Roles: `USER`, `SHOP_OWNER`, `SHIPPER`, `ADMIN`.
- Client rule: Gateway only; no direct service, database or internal-token calls.
- Auth rule: Auth is RS256/JWKS issuer; every resource service validates bearer
  access token and performs ownership checks itself.
- Registration rule: Auth identity then User profile are two client-orchestrated,
  retryable calls.
- Data rule: one owning relational database/schema per service; no cross-DB
  query as a shortcut.
- Delivery rule: Order → Saga → Delivery → Match → Notification → Settlement
  flows through the documented event/state boundaries.
- COD rule: post ledger only from a valid `delivery.completed` and make it
  replay-safe.
- Realtime rule: raw, authenticated WebSocket location with Delivery participant
  authorization; Redis holds volatile current state.

If a proposed rebuild changes one of these, record an ADR and migrate consumers
explicitly rather than silently calling it compatible.

## 2. Technology baseline

| Area | Current implementation to reproduce or consciously replace |
| --- | --- |
| Backend | Java 17, Spring Boot 3.x, Maven multi-module services, Spring Cloud Gateway/Config/Eureka, Flyway |
| Auth | RSA/RS256 JWT, public JWKS, Spring Security resource servers, secure password/reset/verification/session logic |
| Data | PostgreSQL 16, database-per-service ownership, JPA/Flyway migrations |
| Events | Kafka (local KRaft), transactional outbox, consumer receipts/dedup, retry/DLT |
| Cache/realtime | Redis 7 for rate limits/cache/GEO/leases/PubSub; raw WebSocket JSON |
| Search | Elasticsearch projection for restaurants/dishes |
| Telemetry | Actuator, Micrometer/Prometheus/Grafana, OpenTelemetry and correlation IDs |
| Customer app | Flutter/Dart with Dio/Retrofit, Riverpod, Mapbox/Firebase integrations |
| Web portal | React/Vite/Axios for admin and restaurant owner |
| Shipper app | React Native/Redux/Axios, Mapbox and Firebase Messaging |
| Local runtime | Docker Compose plus generated untracked development secrets |

Changing a technology is feasible only if its behavioral contract is retained:
for example, Kubernetes DNS may replace Eureka only through an explicit
discovery migration, and a managed Kafka provider must still preserve topic,
ACL, DLT, ordering and recovery requirements.

## 3. Build order

### Phase A — foundations

1. Create monorepo/polyrepo boundaries and a shared contract package/fixture
   strategy.
2. Add a secure runtime configuration layer: non-secret config, mounted secret
   files, fail-fast startup, health/readiness, correlation/log redaction.
3. Provision local PostgreSQL, Kafka, Redis, search and telemetry with a
   Gateway-only public application boundary.
4. Create Flyway baseline/migration discipline and an outbox/consumer-receipt
   library pattern before first cross-service event.

### Phase B — identity and first client session

1. Build Auth credential/account/session model, password hashing, email
   verification/recovery and token rotation.
2. Produce RS256 access JWTs with `kid`; expose a public-only JWKS endpoint.
3. Build the resource-server adapter and test issuer/audience/algorithm/type
   rejection in every HTTP service.
4. Build User profile/address service and the opaque provisioning token
   resolve/create/complete workflow.
5. Add Gateway route allow-list, legacy identity header stripping and
   trusted-proxy rate-limit policy.

### Phase C — catalog and checkout

1. Build Restaurant/Menu ownership, availability, operating-hours and exact
   owner decision behavior.
2. Build Order preview/create/read/cancel with server-owned coordinate/money
   snapshot; validate current restaurant/menu facts synchronously.
3. Add Order/Restaurant outboxes, decision receipts and exact public/pagination
   response conventions.
4. Build the client adapters only against the Gateway contract.

### Phase D — asynchronous fulfillment

1. Build Saga state/receipts/command outbox.
2. Build Delivery state machine, offer identity/expiry, acceptance/reject/replay
   behavior and its transactional outbox.
3. Build Match as a Kafka-driven Redis GEO projection with one-offer reservation,
   cancellation tombstone and stale-event fencing.
4. Test normal, duplicate, reordered, expired-offer, no-shipper and cancellation
   paths against real PostgreSQL/Kafka/Redis.

### Phase E — realtime, notification and COD

1. Build Tracking raw WebSocket handshake validation, Delivery participant
   authorization, Redis GEO/lease/room model and asynchronous history.
2. Build Notification durable inbox/dedup and FCM wake-only integration.
3. Build Settlement COD eligibility and receipt/ledger/balance transaction.
4. Run crash-after-commit-before-ACK, duplicate/restart and money-conservation
   tests before exposing an end-to-end COD flow.

### Phase F — supporting and gated capabilities

1. Build Search as rebuildable event projection.
2. Keep Promotion/Flash Sale reservation model behind explicit flags until
   checkout policy is approved and integration proof exists.
3. Keep payment/refund provider, analytics and livestream disabled until their
   owner/ownership/idempotency/external-provider decisions are satisfied.

### Phase G — production foundation

Follow [operations/README.md](../operations/README.md): immutable images/config,
secrets, private network, HA data plane, observability, canary/rollback and
PITR/DR. Do not skip this phase because the Compose flow succeeds.

## 4. Rebuild command path for the local COD runtime

After cloning the backend repository, create only ignored development secrets,
package with JDK 17, then use the dependency-aware startup proof:

```bash
cd backend_delivery
bash scripts/gen-keys.sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -DskipTests package
bash scripts/verify-compose-config.sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  RUNTIME_REBUILD_IMAGES=true \
  STARTUP_TIMEOUT_SECONDS=480 \
  bash scripts/verify-runtime-startup.sh
bash scripts/verify-observability-runtime.sh
```

This path preserves existing PostgreSQL/Kafka volumes, starts the 13-service
COD core, and leaves Promotion, Flash Sale, Analytics and Livestream outside the
default runtime. A full capability rehearsal must explicitly enable the
`optional-capabilities` Compose profile and use a disposable/approved test
environment.

The startup proof waits for each service's `UP` Eureka lease in addition to its
Actuator readiness, so a recreated control plane cannot be mistaken for a
usable Gateway topology. A routine invocation reconciles existing images without
recreating healthy canonical containers; use `RUNTIME_REBUILD_IMAGES=true` after
packaging a release or on first startup. The disposable clean E2E runner enables
that flag itself.

For a fresh database/broker proof that does not alter that canonical local
runtime, first render the isolated E2E boundary, then run its full suite only on
a Docker host with capacity for a second core stack:

```bash
CLEAN_E2E_CONFIG_ONLY=true \
  bash scripts/verify-clean-compose-e2e.sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) \
  bash scripts/verify-clean-compose-e2e.sh
```

The runner uses a separate Compose project, PostgreSQL/Kafka volumes and a
Docker-assigned loopback Gateway port; it never uses `down` on the canonical
project. It remains a local/rehearsal test and must not be presented as cloud
production proof.

## 5. Minimum acceptance suite

| Layer | Required proof |
| --- | --- |
| Security | JWKS validation rejects wrong issuer/audience/algorithm/type; Gateway strips spoofed headers; internal routes reject missing/invalid credentials |
| Registration | Auth/User retry after partial failure returns same identity/profile link, without duplicate user |
| Order/fulfillment | Customer order, restaurant confirm, delivery create, matching, persisted offer, shipper recovery/accept and lifecycle progress work through real events |
| Reliability | Duplicate/reordered events, broker/database failure and restart retain idempotent effects/DLT behavior |
| COD | One completion creates exactly one receipt/four ledger entries; exact replay is no-op; conflicts/insufficient deposit leave no partial posting |
| Realtime | Non-participant and stale publisher are rejected; reconnect gets current permitted state; cross-instance fan-out/backpressure is bounded |
| Operations | All required workloads are readiness healthy, metrics/traces/log correlation are visible, backup restores into isolation and reconciliation succeeds |
| Clients | Client tests/builds pass; no direct service calls or doubled `/api` prefix; login/refresh/recovery and role paths follow canonical API |

The existing backend scripts are evidence aids, not a replacement for reviewing
what a rebuild actually changed. Start with `verify-compose-config.sh`,
`verify-runtime-startup.sh`, `verify-mvp-cod-flow.sh`,
`verify-settlement-crash-window.sh` and `verify-backup-restore-rehearsal.sh` in
the backend repository.

## 6. Non-goals and common reconstruction mistakes

- Do not collapse Auth and User into one API/DB just to make registration look
  simpler if the goal is behaviorally compatible ownership/retry semantics.
- Do not put JWT verification back in Gateway or trust client role/user headers.
- Do not replace durable offers/inbox/receipts with push notifications, cache or
  an in-memory queue.
- Do not infer payment/refund behavior from disabled controllers/enums.
- Do not restore Redis GEO/offers/leases from a database backup.
- Do not use generated fake data or a successful unit test as evidence of real
  multi-service/recovery/deployment behavior.

## 7. Reading and implementation sequence

1. [Architecture](../architecture.md) and [security](../security.md)
2. [Service catalog](../service-catalog.md) and [API contract](../api/README.md)
3. [Events/data](../events-and-data.md) and [workflows](../workflows.md)
4. [Clients](../clients.md)
5. [Operations](../operations/README.md)
6. [Source map](./source-map.md), then source/tests for the exact feature being
   recreated.
