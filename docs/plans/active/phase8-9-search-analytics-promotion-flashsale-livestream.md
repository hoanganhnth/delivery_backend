# Phase 8–9: High-Change Domains and Regression Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize Search, Analytics, Promotion, Flash Sale, Livestream, Simulator, and operational regression work without leaking volatile policy into core domain modules.

**Architecture:** Keep each high-change service behind its existing HTTP/Kafka/provider boundary. Shared contracts contain only stable identifiers, event envelopes, and versioned schemas; business policy remains inside the owning service. All business records that can be referenced by orders, payments, analytics, or audit flows use soft deletion (`deleted_at`, actor, reason) and publish a tombstone/event; physical purge is a separately authorized retention job.

**Tech Stack:** Java 17, Spring Boot, Spring Data JPA, Flyway, Kafka, Redis, Elasticsearch, PostgreSQL, H2, Testcontainers, Maven, JaCoCo, Micrometer/Prometheus.

---

## Resumed closure sequence — 2026-10-01

### User-authorized local integration — 2026-10-02

User explicitly requested merging all phase work into `main`, then confirmed
"cứ merge đi, fix sau" after being informed of the failed integration tests.
This overrides the earlier no-merge stop checkpoint for local Git integration
only. It does not authorize push, deployment, new policy decisions, database
repair, or continued feature completion.

- Phase 1–7 committed histories are already ancestors of `main`. Integrate the
  Phase 8–9 branch history and its current source/test/tooling/documentation work.
- Accept the known Notification/Order Kafka integration failures for this merge;
  retain the failure backlog and unchanged quality thresholds. The fresh
  Notification preflight exited 1 with three missing-`retryKafkaTemplate` errors.
  No successful full-platform build or phase-completion claim is made.
- Preserve unrelated dirty documentation in the main checkout, all old
  worktrees, and their superseded experiments. Do not restore obsolete Kafka
  contract namespaces, remove newer event statuses or overwrite pure BFF
  contracts with older variants. Already-integrated phase code needs no reimport.
- Exclude generated Python caches and scratch artifacts from the commits.
  Keep this plan active: unchecked phase requirements and migration-checksum
  review remain outstanding after local integration.

### User-requested stop checkpoint — 2026-10-02

User requested stopping further phase completion and reporting progress. No
additional feature or rollout work is authorized by this checkpoint. The plan
remains active/incomplete; no commit, merge, push or deployment was performed.

- Latest per-service verified reports: Search 34, Analytics 151, Promotion 112,
  Flash Sale 100, Livestream 107, Simulator 159: 663 executed tests, zero
  failures/errors/skips. These are successive full service/reactor runs, not a
  single successful synchronized full-platform build. The mandatory integration
  evidence gate passed all ten Docker suites after the latest Livestream repair.
- Full-platform `clean verify` was attempted and exited 1. Notification and Order
  Kafka/PostgreSQL tests reference the missing `retryKafkaTemplate` bean after
  migration to the shared Kafka starter. An attempted fixture conversion to
  `commonKafkaTemplate` + JsonNode exposed a deeper transport issue: type headers
  identified ObjectNode, rejected by the configured consumer trusted packages;
  consumers repeatedly failed deserialization. That unsuccessful fixture-only
  experiment was removed exactly, leaving those test sources unchanged from
  the initial worktree. Production Kafka wiring was not changed opportunistically.
  This integration regression remains open; no full-platform pass is claimed.
- The new Livestream PostgreSQL exact-replay test initially collided with a
  default channel in its seed. Unique room/channel identities fixed the fixture;
  all three PostgreSQL receipt cases and the full 107-test Livestream suite now
  pass, including retirement, conflicting race and eight-worker exact replay.
- Promotion now rejects approval-audit rewriting of a deleted voucher. The
  ownership client propagates correlation and uses exact numeric status conversion
  so fractional/overflow status cannot grant ownership. RED tests reproduced
  those gaps. Approval/rejection/activation/retirement logic was then extracted
  into `VoucherLifecyclePolicy`, retaining repository locks and transaction
  ownership in PromotionService. Its 36 lines and 16 branches are fully covered;
  full Promotion verify passed 112 tests with real Kafka/PostgreSQL, no skips.
- Coverage reporting now partitions all service package counters into business,
  model/contracts, wiring and migrations; unknown packages remain business.
  Package totals must equal bundle totals. No production line is excluded and
  no threshold changes. Nine reporter fixture tests pass; existing core gates
  pass. Bundle line/branch percentages: Search 82.93/62.28, Analytics 87.63/78.42,
  Promotion 76.82/61.29, Flash Sale 73.72/57.14, Livestream 75.00/69.01,
  Simulator 63.73/49.38. Business line/branch percentages respectively:
  78.74/63.21, 86.08/78.14, 73.60/60.50, 67.68/55.81, 79.78/69.47,
  65.95/50.54. These are measurements, not blanket 85% service closure.
- HTTP inventory now reflects the existing Match nearby-shippers and Livestream
  order-context handlers. The canonical generator locates its own backend checkout
  in standalone clones/worktrees while preserving explicit root overrides.
  Regenerated source-derived artifacts contain 244 operations and 231 schemas;
  inventory, generator check, all 11 Node tests and public-edge manifest pass.
- Static regression/boundary and integration/report fixture gates pass, as does
  diff hygiene. Remote CI, production scrape/alerts, runtime load/recovery drills,
  all seven executable Simulator fault scenarios, broader reservation extraction,
  Analytics broker DLT/rebuild closure and lifecycle tombstone publication remain
  unfinished. Lifecycle topic authority is still pending. V4 Analytics checksum
  review is required for any environment that already applied the old migration;
  no Flyway repair or external database mutation was performed.

The failed Kafka experiment generated a large temporary diagnostic log. It is
compressed for recovery rather than retained as an unbounded live log; Surefire
reports remain available for diagnosis. All test processes have finished.

The approved continuation uses P0–P6 as ordered closure batches for the tasks
below. Existing implementations remain the starting point; unchecked original
items require evidence review rather than automatic reimplementation.

- [ ] P0: establish a synchronized baseline, executable dependency checks,
  reviewed coverage scope, and usable Docker-backed integration environment.
  Fresh affected reactor `mvn -B -fae -pl :search-service,:analytics-service,
  :promotion-service,:flashsale-service,:livestream-service,:simulator-service
  -am verify -q` exited 0. Six service Surefire reports contain 565 discovered
  tests, 540 executed, 25 skipped, zero failures/errors. Counts exclude dependency
  module tests. Current bundle line/branch percentages: Search 77.95/50.89,
  Analytics 81.11/73.68, Promotion 68.58/55.81, Flash Sale 61.42/48.50,
  Livestream 73.30/63.47, Simulator 63.73/49.38. Coverage reporter has no errors.
  Docker CLI is present but its selected local daemon socket is absent, so P0
  environment proof and reviewed service business-logic scope remain open.
  Boundary verification now checks static imports, Simulator's actual package,
  declared/profile production links to other service artifacts, and persistence/
  Redis dependencies in contracts. Ten fixture tests and the existing self-test
  pass; regression verification reuses the boundary gate and passes. This is
  declared/source dependency proof, not an effective/transitive graph audit.
  Eight integration-evidence fixture tests pass. The new evidence gate rejects
  missing, empty, malformed, failed or skipped reports from all eight existing
  mandatory Docker suites. It fails locally on the 25 skipped tests as intended.
  CI runs these fixtures/static checks before build, then coverage and mandatory
  integration evidence after `clean verify`; remote CI execution remains unproven.
  Subsequent Docker recovery supersedes the environment blocker: launching the
  installed nested Docker Desktop executable started daemon 29.4.2. The affected
  six-service reactor with Docker access exited 0, executing 589 tests with zero
  failures/errors/skips. All eight mandatory Docker suites passed the evidence
  gate. Runtime dependency graphs generated using pinned Maven dependency plugin
  3.8.1 also passed the new transitive boundary gate for all 54 discovered service,
  contract and core modules; seven graph fixture tests pass. CI now includes this
  gate after its fresh build. Service business coverage scope and remote CI
  execution remain open, but local Docker-backed integration is available.
- [ ] P1: close lifecycle/tombstone contracts, policy authority, transactional
  event publication, correlation/metrics and rollout controls (Tasks 1–2).
  Shared observability now exposes service-tagged Phase8Metrics from the real
  MeterRegistry, preserving explicit overrides and not inventing a registry.
  Search counts stale rejections, successful tombstone applications and failed
  projection attempts; it preserves exact-replay reapplication for crash repair.
  Failure logs no longer print complete entity payloads. Counter/bean/consumer
  tests passed in the affected reactor. Metrics measure attempts, not unique
  deletes; deployed scrape/alerts and other service call sites remain open.
- [ ] P2: complete Search replay/checkpoint integration and Analytics quarantine,
  out-of-order handling and bounded reconciliation/repair (Tasks 3–4).
  Reconciliation now zeroes same-date existing scopes without accepted order
  events, preserving IDs/raw receipts and leaving other dates unchanged. Two
  H2 transaction tests prove stale/empty-day repair and repeatability. Order and
  Payment listener matrices add 26 cases for malformed envelopes, positive
  integral IDs, nullable optional IDs, nonfinite amounts, ACK ordering and
  storage failures. Payload failures use the existing nonretryable DLT category;
  storage failures remain retryable. Fresh Analytics reactor verify exited 0:
  146 tests, zero failures/errors/skips; bundle line/branch 87.63%/78.42%.
  This does not yet prove real PostgreSQL receipt contention or Kafka DLT replay.
  Subsequent PostgreSQL Testcontainers tests proved exact-replay and distinct
  event races with eight workers, contradictory replay safety, and rollback /
  corrected same-ID retry across receipt/order/item writes. PostgreSQL first
  exposed unsupported V4 constraint syntax that H2 accepted; corrected SQL
  passes fresh migrations. V3 upgrade preserves raw receipts, enforces positive
  aggregate versions and repeated Flyway migrate executes no extra migration.
  V4 checksum changes require history review in any previously migrated environment;
  no repair or external database change was performed. Kafka DLT proof stays open.
- [ ] P3: isolate Promotion and Flash Sale lifecycle/reservation responsibilities;
  prove concurrent retry, stock and expiry invariants in PostgreSQL (Tasks 5–6).
  Flash Sale retry characterization adds 13 Docker-independent cases: exact retry
  preserves all four stored states, original expiry and authoritative prices even
  when input item order differs; changed order/user/principal/restaurant/quantity/
  item sets fail before stock or outbox access; a different reservation key for
  the same order cannot reserve again. Existing production logic passes these
  tests unchanged. Fresh `mvn -B -pl flashsale-service -am verify -q` exited 0
  with 89 discovered tests, 79 executed, 10 Docker skips, no failures/errors.
  Flash Sale bundle coverage is now 63.74% line / 52.69% branch, superseding its
  P0 measurement above. This does not establish real concurrent idempotency;
  PostgreSQL/Kafka integration proof remains required.
  The next Flash Sale increment extracts `FlashSaleAvailabilityPolicy`; quote
  and reserve retain their original validation order, exception messages and
  transaction/locking behavior. Six policy cases characterize inclusive campaign
  endpoints, one-nanosecond-outside rejection, last-unit availability and deleted
  short-circuit behavior. Policy coverage is 13/13 lines and 14/14 branches.
  Three Spring/DataJpa/H2 tests prove reserve, commit and release roll back
  persisted stock/reservation state when the outbox write fails; retry produces
  exactly one successful transition/event. They run without a surrounding test
  transaction, use generated schema and do not replace PostgreSQL migration or
  concurrency proof. The seed uses second precision to avoid `LocalTime.MAX`
  rounding to midnight in a database `time(6)` column.
  A RED approval test reproduced changing the status of a soft-deleted item.
  `approveItem` now treats that item as not found before mutation/save, preserving
  deletion audit metadata; a control test preserves active-item approval.
  Final `mvn -B -pl flashsale-service -am verify -q` exited 0 with 100 discovered
  tests, 90 executed, 10 Docker skips and no failures/errors. Bundle coverage is
  now 71.24% line / 55.95% branch. Static regression/boundary checks and diff
  hygiene pass. P3 remains open for lifecycle extraction and real contention
  evidence; no Phase 8/9 completion or rollout is claimed.
- [ ] P4: close Livestream durable handoff, authority and retry evidence (Task 7).
  Ownership adapter now rejects nonpositive IDs before HTTP and both Livestream
  authority adapters propagate correlation. Product authority rejects overflowing
  status values rather than truncating them to success. RED tests reproduced
  these gaps; full Livestream verify passed 104 tests before the new PostgreSQL
  suite. The shared retirement/conflicting-replay integration cases now also run
  on PostgreSQL, with an additional eight-worker exact-retry / fresh-service
  restoration test. Full reactor validation of that suite is in progress.
- [ ] P5: make all seven Simulator scenarios executable with persisted evidence,
  isolated fault controls and recovery assertions (Task 9).
- [ ] P6: finish full reactor/contracts, dependency/dead-code review, measured
  load and recovery/rollback drills before exit review (Tasks 8, 10–11).

Existing extracted module gates stay at 85% line and branch. Service bundle
percentages remain measurements until business-logic scope is reviewed under
the coverage closure spec; production business paths cannot be excluded to
inflate results. No new policy topic, restore semantics, or retention period is
chosen by this checkpoint. Required Docker proof stays open without preventing
independent source/contract work. No rollout or merge occurs at this checkpoint.

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
- [x] Implement durable Livestream receipts and test concurrent conflicting
  payloads, restart replay, immutable price context, and authority failure.
  Receipt V4 stores the first accepted versioned context under the actor/key
  uniqueness fence. A separate transaction owns the insert so a duplicate-key
  loser can load the committed winner safely; focused H2/Flyway integration
  proves replay after product retirement and conflicting concurrent payloads.
  Full `mvn -pl livestream-service test` passed 89 tests on 2026-09-30.
- [ ] Close FlashSaleStockService availability and state-transition test gaps;
  execute PostgreSQL concurrency tests. Input validation and unit state-transition
  coverage now reject malformed quote/reservation lines before persistence and
  cover commit, expired commit, release, and the expiry sweep. Focused tests
  passed 16/16. Availability tests additionally cover server prices, stable
  quote ordering, deleted/foreign/unapproved/inactive/out-of-window/exhausted
  items, and an exhausted second reservation line with no stock or persistence
  mutation. A late `order.created` now fails commit without an inline stock
  transition, so its event receipt cannot be acknowledged as a successful
  commit; terminal `RELEASED`/`EXPIRED` reservations also fail closed. Full
  `mvn -pl flashsale-service verify -q` passed 76 tests (10 Docker-dependent
  skips) on 2026-09-30; fresh module coverage is 61.42% line / 48.50% branch.
  PostgreSQL concurrency and transactional receipt rollback proof remain
  pending because Docker is unavailable here.
- [ ] Characterize PromotionService behavior, then extract lifecycle,
  eligibility and reservation orchestration preserving transaction boundaries.
  Characterization now covers the bulk reservation commit → release path:
  wallet reserved/used counters, global usage, line state, idempotent release,
  and outbox transitions. Focused test passed 7/7 and the full service suite
  passed 78 tests (10 Docker-dependent skips) on 2026-09-30. The remaining
  extraction must retain the existing locked transaction boundary. The two
  voucher-collection rails now share one availability check, with a new test
  confirming inactive vouchers are rejected before wallet lookup in both
  rails. Fresh `mvn -pl promotion-service verify -q` passed 79 tests (10
  Docker-dependent skips); bundle-wide coverage is 66.50% line / 54.24%
  branch. Lifecycle and reservation extraction remain open.
  Additional red/green regression tests now prevent approval/reactivation of
  deleted vouchers, preserve original deletion metadata on retries, exclude
  deleted vouchers from both wallet-read rails, and reject deleted vouchers
  in auto/manual stacking calculations. Wallet eligibility, collection and
  legacy reservation decisions were extracted into the pure
  `WalletVoucherPolicy`; repository locks and transaction boundaries remain
  in `PromotionService`. Deterministic tests characterize start/end boundaries,
  capacity, minimum spend and restaurant scope. Fresh
  `mvn -pl promotion-service verify -q` passed 88 discovered tests: 78 executed,
  10 Docker-dependent skips, zero failures/errors. Coverage is 67.93% line /
  55.71% branch; module boundaries passed. Normal admin/shop/merchant/pending
  lists now filter `deleted_at IS NULL` in their DB queries. A real H2 JPA
  regression test proves visibility, filtered pagination/count, and continued
  locked access to a tombstone. Fresh full Promotion verify passed 89 discovered
  tests (79 executed, 10 Docker skips), zero failures/errors; coverage is
  68.49% line / 55.71% branch. Versioned voucher tombstone outbox, lifecycle/reservation orchestration
  extraction and PostgreSQL concurrency evidence remain open. Docker Desktop
  is present but cannot launch (`kLSNoExecutableErr`, executable missing), so
  container validation could not be recovered by starting the installed app.
  The generic tombstone schema exists, but the repository does not define the
  voucher lifecycle Kafka topic or the actor identity for the legacy delete
  overload without a principal. Those externally observable contract choices
  need authority before a new producer is introduced. Simulator catalog
  execution also needs an approved non-production fault-control boundary;
  the existing runner exposes Gateway order flows, not Kafka pause/restart or
  direct Search/Promotion/FlashSale/Livestream failure controls.
  The public delete controller now forwards the authenticated stable principal
  and rejects an admin without that identity. Red/green controller tests prove
  it does not substitute the legacy profile ID or drop attribution. Latest
  full Promotion verify passed 91 discovered tests (81 executed, 10 Docker
  skips), zero failures/errors; coverage is 68.58% line / 55.81% branch and
  module boundaries passed. The no-principal delete overload remains only for
  compatibility/test callers; no production caller uses it after this fix.
- [ ] Cover Search checkpoint and Analytics event/projection failure paths.
  Analytics now covers dashboard aggregate derivation, monthly/quarterly
  zero-fill, and empty projection safety; full `mvn -pl analytics-service test`
  passed 26 tests on 2026-09-30. Search checkpoint unit coverage now exercises
  create, exact replay, contradictory payload, stale event, same-timestamp
  conflict, legacy compare-and-set upgrade, and transport failure. Full Search
  reactor `mvn -pl search-service -am test -q` passed 32 tests with 5
  Docker-dependent skips on 2026-09-30. Running only `-pl search-service`
  loaded a stale installed `search-contracts` JAR and produced
  `NoSuchMethodError`; use `-am` for Search validation. Real Elasticsearch
  integration evidence remains open.
  On 2026-10-01, payload regression tests reproduced seven accepted malformed
  cases: non-object JSON roots and fractional aggregate versions/item IDs/
  quantities silently coerced to longs. Parsing now requires an object and
  integral positive long values. Twelve parameterized validation cases pass.
  Four unit tests cover PostgreSQL claim success, concurrent exact replay,
  contradictory winner and conflict without a committed receipt; these are
  adapter-path tests, not PostgreSQL concurrency proof. A real Spring/H2 test
  proves a malformed second item rolls back receipt/order/earlier-item writes,
  then a corrected same-ID retry and exact replay aggregate once. Full
  `mvn -pl analytics-service verify -q` passed 43 tests, zero failures/errors/
  skips; coverage is 67.09% line / 52.30% branch. Analytics payload/replay/
  projection separation, quarantine, reconciliation and coverage closure are
  still open.
  Analytics replay comparison is now a pure `AnalyticsReplayPolicy` using
  stored/incoming snapshots, replacing the twelve-argument comparison method
  inside the transactional service. Fifteen characterization cases cover
  every contradictory identity field, raw/fingerprint comparison, equal money
  with different scales, cancelled events with absent amounts, and delivered
  replays attempting to remove accepted amounts. Full Analytics verify passed
  58 tests with zero failures/errors/skips on 2026-10-01. The extracted policy
  has 15/15 covered lines and 30/30 covered branches; bundle-wide service
  coverage is 67.41% line / 56.61% branch. This does not establish service
  coverage closure: payload parsing, projection rules, listeners and
  reconciliation still need their remaining tests and extraction work.
  Item parsing has now been extracted into `AnalyticsItemSnapshotParser`.
  It returns immutable validated item records and checks the complete batch
  before any item projection write. Eighteen additional characterization cases
  cover malformed/non-array/null/empty snapshots, missing/zero/fractional/
  overflowing IDs or quantities, invalid/negative/over-precision prices,
  nonnumeric totals, the 100-line limit, legacy price fallback and normalized
  blank names. Fresh full Analytics verify passed 76 tests without failures,
  errors or skips; parser coverage is 34/34 lines and 38/38 branches, and
  service coverage is 68.32% line / 61.27% branch. Replay and rollback
  integration tests still pass. Module boundaries and diff hygiene pass.
  Follow-up timestamp characterization adds nine cases for field precedence,
  null/blank fallback, invalid calendar dates and malformed timestamps. Full
  `mvn -pl analytics-service verify -q` passed after these tests, with 85 tests,
  zero failures, errors or skips. These adapter tests do not establish real
  PostgreSQL concurrency or reconciliation/recovery evidence.
  Reconciliation transaction closure: H2 failure injection reproduced a
  committed platform row after a direct restaurant-write failure, and the
  scheduled entry point swallowed that failure. `reconcileDate` now has an
  explicit transaction and the scheduler rethrows after logging so both
  paths roll back. Three H2 integration tests prove rollback, preserved raw
  events and repeatable overwrite; two additional unit tests characterize
  empty days, non-order events, missing delivered amount and average rounding.
  Full Analytics verify passed 90 tests with zero failures/errors/skips;
  service coverage is 69.74% line / 63.29% branch, and the reconciliation
  adapter has all 12 branches covered (55/59 lines). Boundary verification
  and diff hygiene pass. This does not close payment/item rebuilds, stale-row
  reconciliation or PostgreSQL concurrency/recovery evidence.
  Reconciliation reduction is now isolated in a framework-free
  `OrderReconciliationAccumulator`, accepting only event type and amount and
  returning an immutable snapshot. The job retains paged reads, transaction
  ownership and projection writes; counts, zero-clamped pending orders and
  HALF_UP average rounding remain unchanged. Six pure tests cover empty and
  immutable snapshots, terminal events without creation, null delivered amount
  and ignored non-order events. Full Analytics verify passed 96 tests with no
  failures/errors/skips; the accumulator covers 13/13 lines and 8/8 branches.
  Service coverage is 69.95% line / 63.29% branch. Existing H2 rollback/repeat
  tests, module boundaries, static Phase 8/9 regression and diff hygiene pass.
  Payment projection closure adds eight characterization cases covering new
  and existing platform aggregates, absent amounts, completed/failed exact
  replay and PostgreSQL atomic-increment adapter routing. Consumer search found
  both private revenue-helper callers always passed null restaurant ID, so the
  unreachable restaurant-write branch was removed and the helper renamed to
  express platform scope. Restaurant query APIs are unchanged; completed
  payments now reuse the already normalized amount. Full Analytics verify
  passed 104 tests with zero failures/errors/skips before and after refactor.
  Latest service coverage is 76.11% line / 66.96% branch; the increase includes
  both newly exercised payment behavior and removal of unreachable code.
  Module boundaries and diff hygiene pass. PostgreSQL routing tests use mocks
  and do not prove real database concurrency or broker replay.
  Delivered projection characterization adds eight cases crossing PostgreSQL/
  in-memory adapter routing, optional restaurant and nullable amount. Tests
  assert platform/restaurant increments, nonnegative pending, preserved other
  counters, revenue addition and HALF_UP average rounding, without payment or
  item side effects. Full Analytics verify passed 112 tests without failures,
  errors or skips; latest coverage is 80.28% line / 71.64% branch. Static
  Phase 8/9 regression audit and diff hygiene pass. Database adapter mocks
  remain distinct from the still-missing real PostgreSQL integration proof.
  Cancellation projection adds six cases for optional restaurant, zero or
  positive pending and PostgreSQL adapter routing. Tests preserve delivered,
  revenue and average counters while incrementing cancelled and clamping pending.
  The full six-service reactor with `-am verify -q` passed on 2026-10-01 after
  all current changes. Service reports: Search 32 discovered/5 skipped,
  Analytics 118/0, Promotion 91/10, Flash Sale 76/10, Livestream 89/0,
  Simulator 47/0; all have zero failures/errors. This is 453 discovered,
  428 executed, 25 skipped service tests (dependency-module tests additional).
  Fresh line/branch percentages: Search 77.95/50.89, Analytics 81.11/73.68,
  Promotion 68.58/55.81, Flash Sale 61.42/48.50, Livestream 73.30/63.47,
  Simulator 41.55/30.72. Core gates remain unchanged; reporter, module boundaries,
  static regression audit and diff hygiene pass. Docker-backed skips remain
  missing release evidence, not integration passes; service gates and full
  Phase 8/9 completion remain open.
- [ ] Connect Simulator scenarios to execution and test destination guards
  before side effects; separate runner, fault injection, and journal. A new
  `start` test proves a production-like Gateway target is rejected before
  actor binding, journal/persistence, or Gateway calls. Full
  `mvn -pl simulator-service test -q` passed 47 tests on 2026-09-30. The Phase 8
  catalog still contains metadata only and is not connected to execution.
  Follow-up run-control characterization adds 16 cases: pause/resume/abort,
  terminal states cannot be reactivated, active cleanup is rejected, terminal
  cleanup removes memory state, and missing-run operations fail. All cases
  assert zero Gateway interactions; fixtures insert memory states without
  starting executor or external work. Full `mvn -pl simulator-service verify -q`
  passed 63 tests with zero failures/errors/skips. Latest Simulator coverage is
  44.45% line / 32.48% branch; boundaries and diff hygiene pass. These tests
  prove control API behavior only, not executable Phase 8 fault scenarios,
  actor reconciliation or production recovery.
  Heartbeat safety adds eight tests for successful renewal, lost lease,
  renewal exception, all four terminal states and orphaned memory leases.
  RED reproduced that a renewal exception left the run RUNNING. The runner now
  aborts in memory before propagating the original runtime exception; timeline
  text does not expose exception details. Full Simulator verify passed 71 tests
  with zero failures/errors/skips; coverage is 45.31% line / 33.45% branch.
  Module boundaries and diff hygiene pass. Runbook distinguishes catalog
  metadata from executable scenarios and records the heartbeat safety scope.
  These unit tests do not prove concurrent database fencing or cancellation
  of business requests already in flight.
  Cleanup retry closure: RED reproduced that a thrown lease-store release
  removed the memory lease list, causing a subsequent cleanup to skip the
  original fence. `releaseLeases` now retains the list until every call completes.
  Four tests prove same-fence retry, no active-run release, expired-run abort
  without lease release, and fresh/terminal run preservation. Full Simulator
  verify passed 75 tests without failures/errors/skips; coverage is 46.07% line
  / 34.07% branch. Boundaries and diff hygiene pass. Runbook records conservative
  quarantine on repeated partially completed cleanup; real multi-worker
  database fencing and executable fault scenarios remain unverified.
  Lease boundary refactor extracts `SimulationLeaseCoordinator` from the large
  runner. It owns heartbeat/cleanup coordination over the same memory maps;
  scheduling, TTL, actor claim and Gateway workflow remain in the runner.
  Existing service safety tests pass unchanged. Three new coordinator tests
  prove first lost lease stops later renewals, partially completed cleanup
  retries the original fences (including conservative false/quarantine result),
  and legacy no-adapter fixture behavior. Full Simulator verify passed 78 tests
  with zero failures/errors/skips. Coordinator coverage is 29/29 lines and
  16/16 branches; service coverage is 46.33% line / 34.24% branch. Boundaries,
  static regression audit and diff hygiene pass. This extraction is not the
  remaining runner/fault-injection/journal execution split.
  Credential redaction closure: three RED tests reproduced known credential
  keys leaking through root/nested/array metadata in durable/public scenario
  copies. `SimulationCredentialRedactor` now supplies the same recursive
  token/accessToken/ownerToken policy to run state and journal, while retaining
  untouched runtime credentials and caller input. Full Simulator verify passed
  81 tests without failures/errors/skips. Redactor coverage is 11/11 lines and
  4/4 branches; bundle coverage is 46.17% line / 33.93% branch (structural
  refactoring changes the denominator; this is not a claimed coverage increase).
  Module boundaries and diff hygiene pass. This prevents future writes of
  those keys, not remediation of historical journal/scenario rows, free-text
  credentials, unknown credential field names or a full security audit.
  Journal read closure adds four tests for legacy credential redaction on read,
  malformed rows followed by valid rows, null input no-ops and default metadata/
  caller-input preservation. RED proved historical payloads were exposed raw;
  reads now redact a copy without modifying persisted rows. Full Simulator
  verify passed 85 tests with zero failures/errors/skips. Journal adapter
  coverage is 19/19 lines and 8/8 branches; bundle coverage is 46.17% line /
  34.28% branch. Boundaries and diff hygiene pass. Historical storage remediation
  remains separately scoped; unknown keys and free-text secrets are not covered.
  Runner location fence closure: RED reproduced an aborted run sending a
  tracking update when abort occurred after the caller's control check. Every
  location write attempt now checks control immediately before Gateway access.
  Two tests prove aborted suppression and preserved active request shape.
  Full Simulator verify passed 87 tests without failures/errors/skips;
  boundaries and diff hygiene pass. This is a cooperative pre-request check,
  not an atomic fence against abort between check and transport, and does not
  cancel requests already in flight or execute the Phase 8 catalog scenarios.
  Business-write fence follow-up: five tests cover aborted checkout/restaurant/
  delivery writes, abort during quote retrieval and preserved active delivery
  request/state updates. Four RED cases reproduced post-abort Gateway calls.
  Control checks now run immediately before checkout preview, order creation,
  restaurant confirmation and delivery transition writes; the separate cleanup
  cancellation path remains unchanged. Full Simulator verify passed 92 tests
  with zero failures/errors/skips; boundaries and diff hygiene pass. Checks are
  cooperative, not atomic transport fencing, and do not cancel in-flight calls.
  Batch-offer/checkout follow-up adds seven cases. Two RED cases proved batch
  accept/reject still sent after abort during offer retrieval; both writes now
  check control before Gateway access. Active accept/reject tests preserve batch
  identity and assignment behavior. Three checkout tests preserve COD request
  and quote/idempotency-key behavior, including blank/missing legacy quote.
  Full Simulator verify passed 99 tests without failures/errors/skips;
  coverage is 53.50% line / 38.72% branch. Boundaries and diff hygiene pass.
  Single-offer acceptance, assignment cancellation and trigger writes still
  need their own control-race coverage; no all-writes fence claim is made.
  Single-offer closure adds four accept/reject cases, including abort during
  the offer GET. Two RED cases reproduced a post-abort response write. The
  single-offer endpoint now checks control immediately before sending its
  action. Active cases preserve ACCEPT/REJECT payload and assignment behavior.
  Full Simulator verify passed 103 tests without failures/errors/skips;
  boundaries and diff hygiene pass. Assignment cancellation and trigger writes
  remain to be covered; this is still cooperative, not atomic transport fencing.
  Assignment/trigger closure adds eight active/aborted cases for customer cancel,
  restaurant reject, shipper disconnect and assignment cancellation. Four RED
  cases proved zero-delay sleep skipped control checks and allowed aborted
  writes. Both paths now check control after delay; active tests assert original
  endpoint/token/payload and one-shot behavior across repeated invocation.
  Full Simulator verify passed 111 tests without failures/errors/skips;
  boundaries, static regression audit and diff hygiene pass. Separate cleanup
  cancellation remains intentionally available for aborted-run reconciliation;
  cooperative control checks still do not provide atomic transport fencing.
  Existing NETWORK_DELAY execution proof now exercises trigger → runner poll →
  real GatewayClient adapter with only HttpClient transport mocked. The armed
  synthetic 429 sends no HTTP request and leaves state intact; next polling
  updates order/delivery, and repeated trigger does not rearm. Captured requests
  are read-only with the same correlation/auth headers. Full Simulator verify
  passed 112 tests without failures/errors/skips; boundaries and diff hygiene
  pass. This proves the existing transport-fault seam composition, not live
  HTTP/Kafka recovery or execution of the seven Phase8ScenarioCatalog entries.
  Poll characterization adds 11 cases: order 429 is tolerated but order 404/500
  propagate; delivery 404/429 are tolerated while 500 propagates; non-object
  delivery payloads preserve known state; legacy deliveryId/userId mapping
  remains compatible; runs without orders never poll. Assertions distinguish
  partial order updates from unchanged delivery state and ensure fatal errors
  are not silently acknowledged. Full Simulator verify passed 123 tests with
  zero failures/errors/skips; boundaries and diff hygiene pass. These focused
  adapter tests use mocked Gateway responses, not a live recovery environment.
  Delivery interpretation is now isolated in immutable `SimulationDeliverySnapshot`.
  It has no Gateway, scheduling or persistence dependency and maps legacy IDs,
  actor aliases and status fallback without mutating inputs. The runner retains
  transport failure handling and applies the same conditional state updates.
  Consumer search confirmed the moved canonical shipper helper had only the two
  poll callers. Nine pure cases cover null/scalar/array responses, status and
  actor fallback, alias mapping and source preservation. Existing poll/fault
  tests pass unchanged. Full Simulator verify passed 132 tests with zero
  failures/errors/skips; mapper coverage is 13/13 lines and 18/18 branches.
  Bundle coverage is 61.76% line / 46.44% branch; boundaries and diff hygiene pass.
  This does not close the complete runner split or live fault scenario evidence.
  Assertion outcome characterization adds nine cases for terminal matching,
  REJECTED/CANCELLED compatibility, expected actor matching, ledger-observer
  SKIPPED→PARTIAL and failure precedence over skipped observers. Tests assert
  per-assertion IDs/status/actual values and zero Gateway access. Full Simulator
  verify passed 141 tests with zero failures/errors/skips; bundle coverage is
  63.24% line / 48.31% branch. Boundaries and diff hygiene pass. This preserves
  current outcome policy; it does not install the missing ledger/Kafka observer
  or convert skipped evidence into a pass.
  Assertion policy is now framework-free in `SimulationAssertionPolicy`, with
  immutable assertion results and explicit run outcome precedence. Runner keeps
  JSON configuration, observation/journal updates and partial-run event emission;
  existing service characterization passes unchanged. Six pure cases verify all
  outcome combinations and both positive/negative REJECTED alias cases. Full
  Simulator verify passed 147 tests with zero failures/errors/skips; policy
  coverage is 12/12 lines and 18/18 branches. Bundle coverage is 63.30% line /
  48.49% branch; boundaries, static regression audit and diff hygiene pass.
  Missing observers and live fault scenario evidence remain open.
  Quarantine retry closure: a new RED test reproduced removal of memory lease
  handles before a failing quarantine operation completed. The runner now
  retains the original list/fences until all calls complete, allowing retry
  without losing the affected actors. Full Simulator verify passed 148 tests
  without failures/errors/skips; boundaries and diff hygiene pass. This is
  memory retry evidence, not durable restart or concurrent database proof.
  Recovery identity fix: controller authority distinguishes delivery-by-ID
  `/api/deliveries/{id}` from delivery-by-order `/api/deliveries/order/{orderId}`.
  Recovery collected journal deliveryIds but queried the order route. Updated
  tests first failed on both terminal/in-flight journal paths, then passed after
  routing to delivery-by-ID; assertions forbid the mistaken order route.
  Full Simulator verify passed 148 tests without failures/errors/skips;
  boundaries and diff hygiene pass. Private run-ID fallback is unchanged;
  live Gateway/database recovery remains release evidence not yet recorded.
  Recovery guards add six cases for null run ID and RUNNING/PAUSED/PROVISIONING/
  PASSED/PARTIAL states. Tests prove rejection before journal, actor binding or
  Gateway access (null ID also avoids run storage). Full Simulator verify passed
  after these additions; diff hygiene passes. Live recovery and the remaining
  Phase 8/9 deliverables remain open.
  Recovery journal identity validation adds five cases for zero, negative,
  fractional, overflowing and null delivery IDs. RED showed the first three
  reached actor binding. Extraction now requires a positive integral long;
  a journal without valid identities remains unreconciled without actor/Gateway
  side effects. A fresh full Simulator verify passed after the fix; module
  boundaries and diff hygiene pass. This does not replace live recovery proof.
- [ ] Repeat coverage measurement after each changed service suite; set service
  business-logic gates from explicit scope and reviewed exclusions.
  The six-service reactor `mvn -B -fae -pl :search-service,:analytics-service,
  :promotion-service,:flashsale-service,:livestream-service,:simulator-service
  -am verify -q` passed on 2026-09-30; fresh JaCoCo line/branch percentages are
  Search 77.95/50.89, Analytics 64.94/49.12, Promotion 65.77/53.17,
  Flash Sale 54.08/42.17, Livestream 73.30/63.47, Simulator 41.55/30.72.
  `report-phase8-coverage.py` found all reports and no core gate failures;
  module-boundary and Phase 8/9 static regression checks passed. These are
  bundle-wide measurements, not reviewed service-specific gates.
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
