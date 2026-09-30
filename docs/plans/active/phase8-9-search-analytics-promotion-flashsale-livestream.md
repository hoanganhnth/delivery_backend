# Phase 8–9: High-Change Domains and Regression Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize Search, Analytics, Promotion, Flash Sale, Livestream, Simulator, and operational regression work without leaking volatile policy into core domain modules.

**Architecture:** Keep each high-change service behind its existing HTTP/Kafka/provider boundary. Shared contracts contain only stable identifiers, event envelopes, and versioned schemas; business policy remains inside the owning service. All business records that can be referenced by orders, payments, analytics, or audit flows use soft deletion (`deleted_at`, actor, reason) and publish a tombstone/event; physical purge is a separately authorized retention job.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, Flyway, Kafka, Redis, Elasticsearch, PostgreSQL, H2, Testcontainers, Maven, JaCoCo, Micrometer/Prometheus.

---

## Non-negotiable invariants

- No `DELETE` from an application command for business entities. Commands set `deleted_at`, `deleted_by`, and `deletion_reason` in one transaction.
- Default repositories and queries exclude deleted rows; audit, reconciliation, and recovery queries may explicitly include them.
- Soft delete emits a versioned tombstone event carrying entity ID, aggregate version, deletion timestamp, actor, reason, and correlation ID.
- Consumers are idempotent by event ID and aggregate version. An older event must never resurrect a deleted projection.
- A physical purge, if required by retention policy, runs only after an explicit retention window and writes a purge audit record. It is never part of request handling.
- Every tranche must pass module-boundary verification, relevant JaCoCo gates (minimum 85%), service tests, and rollback validation before rollout.

## Rollout order

1. Establish cross-service deletion/event contracts and observability.
2. Extract Search projection and replay/tombstone handling.
3. Harden Analytics event ingestion and reconciliation.
4. Isolate Promotion/Voucher policy and reservation idempotency.
5. Isolate Flash Sale inventory contention and reservation expiry.
6. Isolate Livestream order context and checkout handoff.
7. Build Simulator scenarios and the regression/performance closure suite.
8. Roll out each service behind a feature flag, compare shadow metrics, then increase traffic gradually.

## Task 1: Freeze the soft-delete and tombstone contract

**Files:**
- Create: `docs/contracts/soft-delete-and-tombstone-v1.md`
- Create: `docs/contracts/events/entity-tombstone-v1.json`
- Create: `docs/decisions/0003-soft-delete-over-hard-delete.md`
- Modify: `docs/contracts/README.md`
- Test: `scripts/verify-module-boundaries.py` and service contract tests

- [ ] Define the schema fields (`deleted_at`, `deleted_by`, `deletion_reason`, `aggregate_version`) and allowed state transitions.
- [ ] Define the tombstone payload and compatibility rules. Require `event_id`, `event_type`, `schema_version`, `aggregate_id`, `aggregate_version`, `occurred_at`, `deleted_at`, `actor_id`, `reason`, and correlation metadata.
- [ ] Add examples for duplicate delivery, out-of-order delivery, retry, and replay after deletion.
- [ ] Add a contract test proving a deleted aggregate cannot be re-created by an older projection event.
- [ ] Document retention/purge authority, audit requirements, and restore behavior.
- [ ] Commit: `docs: define soft-delete and tombstone contract`.

## Task 2: Add shared observability and rollout controls

**Files:**
- Modify: `observability-starter/src/main/java/com/delivery/observability/**`
- Modify: `runtime-platform-starter/src/main/java/com/delivery/runtime/**`
- Modify: each affected service `src/main/resources/application*.yml` or `.properties`
- Test: observability and feature-flag configuration tests in each service

- [ ] Add counters for tombstones emitted/applied, stale events rejected, projection replay failures, reservation conflicts, and soft-delete command failures.
- [ ] Add feature flags for read shadowing, write path, replay, and rollout percentage per service.
- [ ] Add correlation ID propagation to Kafka headers and internal HTTP calls.
- [ ] Verify metrics are emitted without changing business responses.
- [ ] Commit: `feat: add phase 8 rollout controls and deletion metrics`.

## Task 3: Search projection and replay

**Files:**
- Modify: `search-service/src/main/java/com/delivery/search_service/consumer/ElasticsearchSyncConsumer.java`
- Modify: `search-service/src/main/java/com/delivery/search_service/consumer/ElasticsearchEntitySyncCheckpointStore.java`
- Modify: `search-service/src/main/java/com/delivery/search_service/consumer/ElasticsearchSearchProjectionWriter.java`
- Modify: `search-service/src/main/java/com/delivery/search_service/document/RestaurantDocument.java`
- Modify: `search-service/src/main/java/com/delivery/search_service/document/DishDocument.java`
- Create: `search-service/src/test/java/com/delivery/search_service/consumer/SearchTombstoneReplayTest.java`
- Create: `search-service/src/test/java/com/delivery/search_service/consumer/SearchProjectionReplayIntegrationTest.java`

- [ ] Write failing tests for delete tombstones, duplicate tombstones, stale update-after-delete, checkpoint restart, and replay from a chosen offset.
- [x] Add projection metadata (`aggregateVersion`, `deletedAt`) and reject stale events before writing Elasticsearch.
- [ ] Implement idempotent delete-by-aggregate-ID and checkpoint advancement only after successful application.
- [x] Add a replay command/runbook that can rebuild a projection without re-enabling deleted documents.
- [ ] Verify search API excludes deleted documents and returns the existing unavailable response when Elasticsearch is unhealthy.
- [ ] Commit: `refactor: make search projection tombstone-safe`.

## Task 4: Analytics event durability and reconciliation

**Files:**
- Modify: `analytics-service/src/main/java/com/delivery/analytics_service/entity/AnalyticsEvent.java`
- Modify: `analytics-service/src/main/java/com/delivery/analytics_service/service/EventProcessingService.java`
- Modify: `analytics-service/src/main/java/com/delivery/analytics_service/listener/**`
- Modify: `analytics-service/src/main/java/com/delivery/analytics_service/scheduler/StatsReconciliationJob.java`
- Create: `analytics-service/src/main/java/db/migration/V4__analytics_event_tombstone_and_version.sql`
- Create: `analytics-service/src/test/java/com/delivery/analytics_service/service/AnalyticsEventIdempotencyTest.java`
- Create: `analytics-service/src/test/java/com/delivery/analytics_service/service/AnalyticsReconciliationTest.java`

- [ ] Write tests for duplicate event IDs, out-of-order versions, malformed payload quarantine, and replay after a source entity is soft-deleted.
- [ ] Add a durable event fingerprint/version constraint and a quarantine path that does not block unrelated events.
- [ ] Ensure daily projections are derived from accepted events and can be reconciled against raw events.
- [ ] Add reconciliation metrics and a bounded repair command; repair must be idempotent.
- [ ] Verify dashboard queries never expose deleted source entities unless explicitly requested for audit.
- [ ] Commit: `feat: harden analytics event processing and reconciliation`.

## Task 5: Promotion and voucher policy isolation

**Files:**
- Modify: `promotion-service/src/main/java/com/delivery/promotion_service/service/**`
- Modify: `promotion-service/src/main/java/com/delivery/promotion_service/entity/Voucher.java`
- Modify: `promotion-service/src/main/java/com/delivery/promotion_service/entity/UserVoucher.java`
- Modify: `promotion-service/src/main/java/com/delivery/promotion_service/entity/PromotionReservation.java`
- Create: `promotion-service/src/main/resources/db/migration/V8__promotion_soft_delete_and_active_indexes.sql`
- Create: `promotion-service/src/test/java/com/delivery/promotion_service/service/VoucherReservationIdempotencyTest.java`
- Create: `promotion-service/src/test/java/com/delivery/promotion_service/service/PromotionSoftDeleteTest.java`

- [ ] Write tests for voucher eligibility, reservation idempotency, expiry, cancellation, duplicate order events, and deleted voucher exclusion.
- [ ] Keep discount calculation behind promotion-service APIs; do not add voucher policy to Order or common modules.
- [ ] Add active-row uniqueness/index rules that coexist with soft-deleted vouchers and campaigns.
- [ ] Publish reservation-created, reservation-released, and voucher-deleted events with aggregate versions.
- [ ] Verify an order retry reuses the same reservation and cannot double-consume a voucher.
- [ ] Commit: `refactor: isolate promotion policy and voucher reservations`.

## Task 6: Flash Sale inventory contention

**Files:**
- Modify: `flashsale-service/src/main/java/com/delivery/flashsale_service/service/**`
- Modify: `flashsale-service/src/main/java/com/delivery/flashsale_service/entity/FlashSaleItem.java`
- Modify: `flashsale-service/src/main/java/com/delivery/flashsale_service/entity/FlashSaleReservation.java`
- Create: `flashsale-service/src/main/resources/db/migration/V6__flashsale_soft_delete_and_reservation_fences.sql`
- Create: `flashsale-service/src/test/java/com/delivery/flashsale_service/service/FlashSaleConcurrencyTest.java`
- Create: `flashsale-service/src/test/java/com/delivery/flashsale_service/service/FlashSaleReservationExpiryTest.java`

- [ ] Write concurrent tests proving stock cannot become negative, duplicate idempotency keys do not reserve twice, and expired reservations release exactly once.
- [ ] Choose and document one contention strategy per stock path: atomic SQL update or Redis Lua fence; do not mix strategies in one operation.
- [ ] Add campaign/item soft delete and prevent deleted items from being quoted or reserved.
- [ ] Emit reservation and stock-adjustment events with sequence/version fields.
- [ ] Run a bounded load test before enabling production traffic and record p95 latency, conflict rate, and lock wait time.
- [ ] Commit: `feat: make flash sale reservations contention-safe`.

## Task 7: Livestream order context

**Files:**
- Modify: `livestream-service/src/main/java/com/delivery/livestream_service/controller/InternalLivestreamCheckoutController.java`
- Modify: `livestream-service/src/main/java/com/delivery/livestream_service/client/LivestreamProductAuthorityClient.java`
- Modify: `livestream-service/src/main/java/com/delivery/livestream_service/**` event producers/consumers
- Create: `livestream-service/src/test/java/com/delivery/livestream_service/checkout/LivestreamOrderContextContractTest.java`
- Create: `livestream-service/src/test/java/com/delivery/livestream_service/checkout/LivestreamCheckoutRetryTest.java`

- [x] Define a versioned order-context DTO containing stream ID, pinned product ID, seller/restaurant ID, price snapshot ID, actor, and correlation ID.
- [ ] Validate product authority and soft-deleted product behavior before checkout handoff.
- [ ] Make checkout handoff idempotent by livestream event/order key; retries must return the original result.
- [ ] Keep livestream presentation and moderation policy inside livestream-service; only stable checkout contracts cross service boundaries.
- [x] Commit: `feat: stabilize livestream checkout order context`.

## Task 8: Phase 8 integration and staged rollout

**Files:**
- Modify: `docs/runbooks/**`
- Create: `docs/runbooks/phase8-rollout.md`
- Create: `docs/reviews/phase8-exit-review.md`
- Test: service-level contract suites and deployment smoke tests

- [ ] Run each service’s unit, integration, migration, and contract tests.
- [ ] Run a replay/tombstone drill in a non-production environment.
- [ ] Enable read shadowing, compare old/new decisions, then enable writes for one tenant/traffic slice.
- [ ] Define rollback per service: disable flag, stop consumer, preserve offsets, restore previous deployment, and replay from checkpoint.
- [ ] Record exit evidence: no stale resurrection, no duplicate reservations, no unbounded lag, and no increase in 5xx/error budget burn.
- [ ] Commit: `docs: record phase 8 rollout and exit evidence`.

## Task 9: Simulator and deterministic load scenarios

**Files:**
- Modify: `simulator-service/src/main/java/com/delivery/simulator/service/**`
- Modify: `simulator-service/src/main/java/com/delivery/simulator/entity/**`
- Create: `simulator-service/src/main/java/com/delivery/simulator/service/Phase8ScenarioCatalog.java`
- Create: `simulator-service/src/test/java/com/delivery/simulator/service/Phase8ScenarioTest.java`
- Create: `docs/runbooks/simulator-scenarios-phase8-9.md`

- [ ] Add deterministic scenarios for duplicate Kafka delivery, consumer restart, search replay, voucher contention, flash-sale stock contention, livestream checkout retry, and soft-delete recovery.
- [ ] Persist scenario seed, actor lease, decision trace, and recovery result so a failed run is reproducible.
- [ ] Add fault injection for provider timeout, Kafka pause, Redis outage, DB deadlock, and stale event delivery.
- [ ] Verify simulator runs do not write to production databases or publish to production topics.
- [x] Commit: `feat: add deterministic phase 8 simulator scenarios`.

## Coverage and maintainability execution checkpoint (2026-09-30)

Evidence: [measured baseline](../../reviews/phase8-coverage-baseline.md).
This checkpoint implements the approved coverage-closure design while keeping
the execution history in this existing plan.

- [x] Run full reactor verify and verify 20 existing core coverage gates.
- [x] Instrument all six Phase 8 services without lowering thresholds.
- [x] Add a JSON coverage reporter with tests for missing reports, exact gate
  comparisons, absent branch opportunities, and weakened configured gates.
- [x] Reproduce checkout null-request failure and fix validation ordering;
  run all six instrumented service suites.
- [x] Correct previously checked items that had only partial implementation:
  analytics quarantine, campaign soft-delete, checkout authority and durable
  idempotency, executable simulator scenarios and production isolation.
- [ ] Implement durable Livestream receipts and test concurrent conflicting
  payloads, restart replay, immutable price context, and authority failure.
- [ ] Close FlashSaleStockService availability and state-transition test gaps;
  execute PostgreSQL concurrency tests.
- [ ] Characterize PromotionService behavior, then extract lifecycle,
  eligibility and reservation orchestration preserving transaction boundaries.
- [ ] Cover Search checkpoint and Analytics event/projection failure paths.
- [ ] Connect Simulator scenarios to execution and test destination guards
  before side effects; separate runner, fault injection, and journal.
- [ ] Repeat coverage measurement after each changed service suite; set service
  business-logic gates from explicit scope and reviewed exclusions.
- [ ] Finish Docker-backed integration evidence and final review before merge.

Validation commands and exact baseline counts are recorded in the linked review.
No new merge, deployment or performance claim is made by this checkpoint.

## Task 10: Regression closure and dependency audit

**Files:**
- Create: `scripts/verify-phase8-9-regression.py`
- Create: `docs/reviews/phase8-9-dependency-audit.md`
- Modify: `docs/plans/active/README.md`
- Test: all affected Maven modules

- [ ] Run a full dependency audit for forbidden framework imports in domain/application modules and accidental service-to-service implementation coupling.
- [ ] Search for hard-delete paths in business services and replace only in-scope paths with the soft-delete command/event flow.
- [ ] Remove dead adapters, compatibility shims, fallback code, and unused feature flags only after consumer search confirms no references.
- [ ] Close coverage gaps without lowering thresholds; retain 85% line/branch gates for extracted modules.
- [ ] Run the full Maven reactor and contract suite with Docker-dependent tests explicitly reported as pass/skip/fail.
- [ ] Commit: `chore: close phase 8 and 9 regression gaps`.

## Task 11: Operational baseline and recovery evidence

**Files:**
- Create: `docs/operations/phase8-9-baseline.md`
- Create: `docs/runbooks/phase8-9-recovery.md`
- Modify: `docs/operations/metrics-catalog.md`
- Test: simulator/load scripts and recovery drills

- [ ] Capture before/after baselines for throughput, p95/p99 latency, DB connection usage, Kafka lag, cache hit/miss, outbox relay delay, retry volume, lock contention, and recovery time.
- [ ] Run failure injection and recovery drills for each service; record start time, detection time, mitigation time, and data correctness result.
- [ ] Set alert thresholds from observed baselines, not guesses.
- [ ] Do not claim performance optimization unless the baseline comparison shows a measured improvement with workload and environment recorded.
- [ ] Commit: `docs: publish phase 8 and 9 operational baseline`.

## Exit criteria

- [ ] Every Phase 8 service has a versioned boundary contract, soft-delete/tombstone behavior, idempotency tests, and rollback procedure.
- [ ] Search replay cannot resurrect deleted documents.
- [ ] Analytics reconciliation is repeatable and raw events remain auditable.
- [ ] Promotion and Flash Sale reservations are idempotent under concurrent retries; stock never becomes negative.
- [ ] Livestream checkout retries preserve the same order context and result.
- [ ] Simulator scenarios reproduce the documented failure modes without production writes.
- [ ] Full dependency audit, dead-code review, coverage closure, recovery drill, and performance baseline are attached to the Phase 9 review.
- [ ] Phase 8/9 is not marked complete until all evidence is recorded and the orchestrator parent review is accepted.
