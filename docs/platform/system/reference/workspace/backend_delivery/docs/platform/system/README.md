# Delivery System Documentation

> As-built documentation index — verified against repository documentation,
> configuration and source on 2026-08-24. This is the canonical entry point for
> understanding or rebuilding the platform. It is deliberately explicit about
> what is implemented, hidden, proposed, or still awaiting an owner decision.

## Purpose

This folder is the smallest complete reading path for a new engineer or an AI
agent to reconstruct a system with the same boundaries and behavior. It does
not replace executable truth:

1. Source code, tests, Flyway migrations, Compose/Kubernetes manifests and
   observed runtime evidence are authoritative when they disagree with prose.
2. Backend contract inventories are the exact reviewed mapping of HTTP routes,
   services, topics, persistence and capability status.
3. This folder is the canonical architecture/reconstruction guide. It collects
   the intent and links to the detailed, service-owned source material rather
   than copying a fact that cannot be maintained safely.

Never put secret values, JWTs, private keys, live database dumps, production
domains, provider credentials, customer data, or a production topology guess in
this folder.

## Start here

| If you need to… | Read |
| --- | --- |
| Understand the whole platform and trust boundaries | [architecture.md](./architecture.md) |
| Read the diagram inventory and Mermaid rules | [diagram-standards.md](./diagram-standards.md) |
| Find ownership, database, capability and responsibility of a service | [service-catalog.md](./service-catalog.md) |
| Reproduce business behavior and failure handling | [workflows.md](./workflows.md) |
| Build a compatible HTTP client or service | [API guide](./api/README.md), [human-readable API catalog](./api/http-contract-catalog.md) and [machine-readable HTTP contract](./api/http-contract.json) |
| Reproduce eventing, state, storage and recovery rules | [events-and-data.md](./events-and-data.md) |
| Implement identity, authorization and secrets safely | [security.md](./security.md) |
| Recreate the three clients | [clients.md](./clients.md) |
| Reproduce SDKs, direct dependency roles, local images and CI/tooling | [technology-and-tooling.md](./technology-and-tooling.md) |
| Run locally or design a production environment | [operations/README.md](./operations/README.md) |
| Build the platform clean-room, in dependency order | [rebuild/README.md](./rebuild/README.md) |
| Browse the product and technical system handbook in the Web UI | `/system-overview` in `delivery_web` — public read-only Flow Explorer plus searchable Markdown docs portal with source/status badges |
| Trace every document claim back to code/config/runbook | [rebuild/source-map.md](./rebuild/source-map.md) |
| Browse a single-folder offline snapshot of first-party docs/config/runbooks | [reference/README.md](./reference/README.md) |
| Run the dev/test multi-actor simulator | [simulator/README.md](./simulator/README.md) |

## Platform at a glance

| Layer | Current implementation |
| --- | --- |
| Product clients | Flutter customer app, React/Vite admin and restaurant portal, React Native shipper app |
| Public edge | Spring Cloud API Gateway on `:8079`; it routes exact allowed paths, rate-limits and strips legacy identity headers |
| Identity | Auth Service issues RS256 access JWTs with `kid` and exposes a public JWKS; resource services verify their own bearer tokens |
| Core domain | User, Restaurant/Menu, Order, Saga, Delivery, Match, Shipper and Settlement services |
| Async/realtime | Kafka event flows, Redis GEO and Pub/Sub, raw WebSocket tracking, durable notification inbox plus best-effort FCM wake-up |
| Data | PostgreSQL database per JPA service; Elasticsearch search projection; Redis only for cache, rate limit, GEO, freshness and fan-out state |
| Control/observability | Spring Cloud Config, Eureka, Actuator readiness/liveness, Prometheus, Grafana, OpenTelemetry collector and structured correlation IDs |
| Local runtime | Docker Compose, generated local secrets, explicit migration/backup/restore/rehearsal scripts |

## System invariants that must survive a rebuild

- Clients call only the Gateway origin. Internal service ports, management
  endpoints, Kafka, databases, Config Server and Eureka are private.
- Gateway does **not** verify JWTs or inject a user identity. It removes any
  client-supplied legacy `X-User-Id` and `X-Role` headers; each resource service
  validates Auth JWKS itself and enforces both role and resource ownership.
- Password registration is a two-request client-orchestrated workflow: create or
  resume the Auth identity, then create/resume the User profile with an opaque
  provisioning hand-off token. It is not a single distributed write endpoint.
- A service owns its database. Cross-service data comes through an internal
  authenticated API or a documented event, never a direct database query.
- Core state-changing events use a transactional outbox. Consumers are
  idempotent, acknowledge only after local commit and have retry/DLT treatment.
- `delivery.completed` is the only COD settlement trigger. A duplicate event
  cannot post the ledger twice; amount or identity conflicts fail closed.
- Location is live Redis/WebSocket state, not a per-ping relational write.
  Tracking history is asynchronous, sampled support data and is not an input to
  matching or delivery authorization.
- Features marked hidden/disabled are not public contracts, even if a controller
  or schema exists. Do not re-enable them merely because code is present.

## Capability status vocabulary

| Status | Meaning |
| --- | --- |
| `active` | Part of the current supported MVP path and routed/started under its documented configuration. |
| `protected` | Active only for the documented authenticated role/ownership boundary. |
| `internal` | A private service-to-service path requiring the internal credential; never exposed by Gateway. |
| `hidden` / `disabled` | Code or schema may exist, but the Gateway/service flags keep it out of the supported MVP contract. |
| `decision required` | A production or product policy is not authorized yet; documentation may state alternatives but must not claim a selected behavior. |

## Reconstruction order

1. Read the trust boundaries and service catalog; choose the same roles,
   ownership model and public/private network split.
2. Build the control plane and shared libraries: config bootstrap, health,
   correlation, secure secret loading, JWKS resource-server validation and
   outbox/dedup conventions.
3. Implement identity and profiles before client login; preserve the two-step
   registration recovery model.
4. Implement Restaurant/Menu, then Order, Saga, Delivery, Match, Tracking,
   Notification and Settlement in that event dependency order.
5. Add search and all disabled/experimental capabilities only after their
   separate authorization, idempotency and operations requirements are met.
6. Add the three clients against the Gateway contract, then run the COD and
   recovery checks documented under operations.

The detailed sequence, validation gates and artefacts are in
[rebuild/README.md](./rebuild/README.md).

## What has been verified versus what remains

The repository has local/isolated runtime proof for important COD, outbox,
settlement crash-window, JWKS Compose rollout, backup/restore and service startup
paths. It does **not** yet prove a cloud production rollout, sustained load,
managed Kafka/PostgreSQL high availability, real payment-provider behavior or
native device E2E. Read [operations/README.md](./operations/README.md) before
using a local Compose success as a production claim.

## Documentation coverage and known gaps

This corpus is sufficient for a new engineer to reconstruct the supported COD
MVP boundary at the system level: context and containers, service ownership,
HTTP/event contracts, dynamic workflows, state transitions, security, clients,
deployment, observability, recovery and simulator design are all represented.
The [diagram inventory](./diagram-standards.md) maps each view to its abstraction
level and authority so that a diagram is not mistaken for an executable
contract.

The following are deliberately documented gaps, not missing prose to fill with
assumptions:

| Gap | Current treatment | Required evidence before calling it closed |
| --- | --- | --- |
| Event envelope/version and aggregate-key normalization | Legacy events still mix typed DTO/JSON forms and topic keys; the active matrix is the authority | Compatibility/schema tests and an approved migration plan |
| Order/Delivery/Saga vocabulary and transition compatibility | The canonical COD path is documented, while legacy transition paths remain flagged in the backend inventory | Cross-service state/convergence tests and removal of unsafe compatibility paths |
| Shipper availability authority | PostgreSQL `isOnline` and Redis busy/freshness state both exist | One owner-approved source-of-truth policy plus concurrent replay proof |
| Production topology, capacity, SLO, retention and provider choices | Operations pages mark these as `decision required`; local Compose is not production proof | Approved platform decisions, load/HA/DR evidence and on-call runbooks |
| Native device and multi-instance realtime behavior | Client and Tracking contracts are documented; emulator/device and scaled WebSocket evidence remains limited | Device E2E, reconnect/fan-out and stale-generation tests |
| Polygon serviceability and provider-backed ETA | Restaurant zones, internal decision, driving+prep ETA window and explicit fallback source exist behind default-off flags | Approved rollout, provider/runtime replay, stale-zone/cache and load evidence |
| Menu inventory reservation | Restaurant-owned durable on-hand/reserved ledger, all-or-nothing hold/commit/release and additive Order identity exist behind default-off flags | PostgreSQL concurrency, Kafka replay/DLT, expiry/reconciliation, admin UX and staged rollout evidence |

Do not remove a row because the design has been described. Remove it only when
the linked source, decision and executable/observable proof have changed status.

## Maintenance protocol

When a behavior changes, update in the same change set:

1. The owning code, tests, migration and configuration.
2. The service-local contract inventory or workflow document.
3. The relevant document in this folder and its source-map row.
4. The rollout/recovery material if a data, topic, secret, claim or public route
   changes.

For changes crossing repositories, keep one active implementation plan under
`docs/plans/active/`; use the root `AGENTS.md` and `docs/WORKFLOW.md` as the
workspace process authority.

Run `node backend_delivery/docs/platform/system/verify-docs.mjs` after changing this corpus. It verifies
the required documentation set, local Markdown targets, Markdown fences, Mermaid
block presence/style, expected service catalog coverage, source-derived HTTP
contract freshness and the checksum-backed offline reference bundle. It does not
replace source/test/runtime verification or render every diagram in a browser.

Run `node backend_delivery/docs/platform/system/rebuild/sync-reference-bundle.mjs --write` after changing
any source documentation/configuration that belongs in the offline snapshot;
then use `--check` to prove the mirror has not drifted. The bundle excludes
secrets, vendor dependencies, build output and source-code copies deliberately.
