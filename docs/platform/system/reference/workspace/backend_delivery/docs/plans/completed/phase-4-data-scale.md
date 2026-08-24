# Execution Plan: Phase 4 — Data And Scale

Date: 2026-07-30

## Status

Completed

## Outcome

The backend can rehearse an encrypted, integrity-checked restore into isolated
PostgreSQL databases; hot production queries are bounded and supported by
evidence-based indexes; raw WebSocket location fan-out scales with authorized
delivery rooms and bounded per-session pressure; and sampled shipper location
history is persisted asynchronously and idempotently for support investigations.

## Context

- `ROADMAP_MVP_TO_PRODUCTION.md`
- `docs/product/overview.md`
- `docs/services/tracking_service.md`
- `docs/system-contract-inventory.md`
- `docs/runbooks/resilience-operations.md`
- `docker-compose.yml` and `docker/postgres/init-db.sql`

The repository currently uses one PostgreSQL 16 instance with a database per
stateful service, Kafka for asynchronous contracts, and Redis for realtime
GEO/freshness state. Redis location and availability state is deliberately not
restored: it is transient realtime state and must be rebuilt by reconnecting
publishers so stale locations or online membership cannot be resurrected.

## Scope

In scope:

- Per-service PostgreSQL backup inventory, encrypted backup/checksum/retention
  tooling, Kafka recovery metadata export, isolated restore and reconciliation.
- Representative hot-query plans, bounded repository/service contracts,
  minimal Flyway indexes and simple before/after benchmark evidence.
- Delivery-room WebSocket subscriptions, authorized fan-out, bounded
  per-session backpressure/coalescing and fan-out/load/security regressions.
- Async Kafka location-history ingestion, sampling, idempotency, restricted
  delivery-scoped support reads, encryption/access documentation and cleanup.

Out of scope:

- Running destructive or restore operations against production.
- Restoring Redis realtime GEO, publisher leases, generation fences or offline
  deadlines.
- Client-visible location-history features or unrestricted fleet/history APIs.
- Managed-cloud backup provisioning; scripts and runbooks define the portable
  contract and rehearsal.

## Approach

1. Inventory state ownership and establish recovery policy and isolated safety
   guards. Implement fixture → backup → drop isolated target → restore →
   reconciliation/smoke rehearsal with timing and integrity evidence.
2. Inventory priority repository methods and schemas. Capture representative
   PostgreSQL plans before/after, add only indexes justified by those plans,
   bound production reads and validate clean Flyway upgrades and focused tests.
3. Replace shipper-keyed synchronous socket fan-out with authorized
   delivery-room membership and per-session coalescing send queues while
   preserving the raw payload/actions, generation fence and tombstone behavior.
4. Enrich location events with stable identity and delivery association without
   adding database work to the publisher hot path. Persist sampled points via an
   async consumer with uniqueness fencing, restricted support reads and cleanup.
5. Run focused, integration, load/benchmark and recovery proof; update lasting
   runbooks/contracts and move this plan to `completed/` only when all evidence
   passes.

## Risks And Recovery

- Existing uncommitted work spans many backend modules. Changes remain tightly
  scoped, are applied on top of current files, and unrelated modifications are
  not reverted or reformatted.
- Restore scripts could destroy data. They require an explicit isolated prefix,
  reject known production-like hosts/databases and never accept the canonical
  source database as a restore target.
- Extra indexes can amplify write cost and lock migrations. Baselines and index
  size/write-path review are recorded; migrations use PostgreSQL-safe rollout
  guidance and can be rolled forward by dropping only the named new indexes.
- Socket queues can drop intermediate updates. Coalescing is allowed only for
  online location updates per delivery; offline tombstones and the newest
  location are retained. Rollback restores the prior handler implementation.
- History ingestion may associate a point with the wrong delivery or duplicate
  replay. Stable event IDs, delivery assignment timestamps and database unique
  constraints fence replay/out-of-order input; ambiguous events are rejected or
  retried rather than attributed speculatively.

## Progress

- [x] Policy authority approved by the user.
- [x] Inventory and baseline current state.
- [x] Implement and rehearse backup/restore.
- [x] Audit and optimize hot queries.
- [x] Optimize WebSocket fan-out/backpressure.
- [x] Implement async location history.
- [x] Run aggregate validation and update durable docs.

## Decisions

- 2026-07-30: MVP RPO/RTO is 24h/4h for ordinary service data and 4h/2h for
  order, delivery and settlement. Production RPO/RTO is 5m/60m for
  order/delivery/settlement/outbox and 15m/2h for other durable services.
- 2026-07-30: Backup retention is daily 14 days plus weekly 8 weeks for MVP;
  production uses PITR/daily 35 days plus monthly 12 months. This is operational
  backup retention, not the legal business-record retention policy.
- 2026-07-30: Redis realtime data is not durable business source-of-truth and is
  excluded from restore. Kafka recovery captures topic configuration, ACL and
  consumer-offset metadata; database backups remain the business-data source.
- 2026-07-30: Location history is audit/support-only, retained 90 days, sampled
  at most once per 10 seconds or after at least 25 metres of movement, stored at
  five decimal coordinate precision, encrypted at rest/in backups, and exposed
  only through audited internal support/admin delivery-scoped access.

## Validation

- Focused proof: repository/service tests, socket authorization/fan-out/
  reconnect/stale/coalescing tests, history ordering/replay/cleanup tests.
- Integration proof: isolated PostgreSQL fixture/backup/drop/restore/reconcile;
  Flyway clean-upgrade; Kafka consumer restart/replay history proof.
- Performance proof: representative `EXPLAIN (ANALYZE, BUFFERS)` before/after,
  bounded dataset benchmark, and increasing connection/subscriber socket load.
- Repository-required checks: affected module tests, aggregate Maven test/build
  where practical, Compose configuration and `git diff --check`.

## Result

Completed on 2026-07-30.

- The disposable recovery rehearsal restored all 14 service databases into the
  guarded `phase4_restore_*_` namespace in four seconds. AES-256-CBC/PBKDF2
  encryption, outer and inner SHA-256 verification, encrypted Kafka metadata,
  tamper rejection, critical-data fingerprints, uniqueness and the cross-domain
  smoke flow all passed. Retention cleanup passed in dry-run and delete modes.
- The representative PostgreSQL benchmark passed with index-supported priority
  paths. The two evidenced regressions improved from 23.072 ms to 0.069 ms for
  the global order timeline and 5.354 ms to 0.219 ms for pending withdrawals;
  source scan found no parameterless production `findAll()`.
- The 50,000-room WebSocket benchmark reduced disconnect lookup from 7,230,458
  ns to 62,666 ns and fenced a reused shipper audience from two deliveries to
  one. Authorization, stale assignment, reconnect/final-state recovery,
  cross-instance Redis fan-out and bounded coalescing tests passed.
- Location history tests passed for five-decimal precision, 10-second/25-metre
  sampling, out-of-order input, restart/replay idempotency, delivery-scoped
  reads, missing/legacy assignment handling, access rejection and retention.
- Aggregate module evidence passed: Tracking 54 tests, Order 93, Delivery 82 and
  Settlement 37. Compose validation, shell syntax checks and `git diff --check`
  passed. Order test databases now use a context-unique H2 name so independent
  `create-drop` contexts cannot invalidate another context's Flyway schema.

The scripts define portable backup and rehearsal behavior. Production PITR,
KMS-managed storage encryption, scheduled retention jobs and measured restore
drills still require deployment-environment provisioning; these are explicit
operational prerequisites rather than unverified repository claims.
