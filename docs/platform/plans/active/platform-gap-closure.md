# Execution Plan: Platform gap closure and Shipper/Backend clean refactor

Date: 2026-08-23

## Status

Active

## Outcome

Close the verified cross-platform gaps while preserving the existing
single-order, FCM wake-only and raw WebSocket contracts. Canonical shipper
identity and a durable batch snapshot become the authority for Shipper,
Delivery and Tracking. Remaining product gaps are implemented behind explicit
default-off capabilities and proved with repository code/tests only.

## Context and authority

- Root workflow: `docs/WORKFLOW.md`.
- Identity authority: JWT identity claims, `ShipperIdentityUpserted` and local
  `shipper_identity_projection` projections.
- Delivery/batch authority: `docs/product/features/delivery-matching.md` and
  `docs/plans/active/production-matching-v1.md`.
- Refund/payment authority: `docs/plans/active/refund-payment-decision-packet.md`.
- Existing completed matching, sandbox and refund work is not reimplemented;
  this plan tracks only residual work and cross-repo integration.

## Locked compatibility and policy decisions

- `principalId` is auth identity; `legacyUserId/userId` is retained only for
  notification/FCM and explicitly legacy surfaces; `shipper.id` is canonical
  for Delivery, Tracking, History, Active delivery and Batch.
- Single-order routes, request/response shapes, legacy transition vocabulary,
  raw location WebSocket payload/heartbeat and FCM wake-only semantics remain
  unchanged.
- Batch route authority uses a contiguous `2 * itemCount` stop sequence with
  strict `pickupSequence < dropoffSequence`.
- POD is mandatory for terminal delivery handoff, uses private object storage
  signed URLs, is limited to 10 MB and retained for 90 days.
- A failed delivery gets one retry within 15 minutes, then returns; the
  restaurant confirms `RETURNED`. Post-pickup refund cases go to manual review.
- Admin serviceability uses polygons; public ETA is driving duration plus prep
  time represented as a range.
- Inventory fails closed and never backorders. One `CRITICAL` or two `HIGH`
  risk signals in one case creates a review hold. Transactional notifications
  cannot be disabled; marketing defaults opt-out.
- PayOS is the first provider-neutral payment adapter at contract/sandbox
  level. PayOS payout follows payment core and remains default-off.
- All new capabilities default to `false`. Code/static/unit proof is required;
  Docker, real providers, staging and device/emulator proof remain release
  follow-ups.

## Scope and sequence

- [x] T0 — baseline, authority map and contract freeze.
- [x] T1 — canonical shipper identity resolver and enforcement rollout.
- [x] T2 — batch snapshot, route invariant, normalized Shipper state and
  recovery fencing.
- [x] T3 — delivery exception, POD, retry and return flow (default-off; code/H2/
  focused contract proof complete; provider/runtime/device rollout deferred).
- [x] T4 — polygon serviceability and ETA window (backend code-gated; client UX and
  provider/runtime proof remain release follow-ups).
- [x] T5 — menu-item inventory reservation (backend code-gated; admin/client UX,
  broker replay and concurrency runtime proof remain release follow-ups).
- [x] T6 — provider-neutral payment, refund boundary and payout contract
  (code-gated; provider execution, callback, staging and financial mutation
  remain blocked).
- [x] T7 — per-item analytics projection (code-gated; runtime/backfill,
  reconciliation, owner/admin UI and PostgreSQL/Kafka rehearsal remain open).
- [ ] T8 — risk/fraud decision and review hold.
- [x] T9 — notification preferences (code-gated; marketing dispatch enforcement,
  client UX and runtime rollout remain open).
- [ ] T10 — Firebase support-chat hardening.
- [ ] T11 — Agora RTC/RTM hardening and concurrent presence.
- [x] T12 — matching producer, simulator and sandbox residuals.
- [ ] T13 — cross-client alignment, generated contract/docs and closeout.

Dependencies: `T0 → T1 → T2`; T2 gates T3/T12 and most client work. T5 gates
per-item analytics; T3 and T6 share the refund boundary. T13 closes only after
all enabled task gates are green.

## Risks and recovery

- A projection lag or divergent identity must fail closed once enforcement is
  enabled; before enforcement, only a measured legacy fallback is permitted.
- Invalid legacy batch sequence data is never silently reinterpreted. Retire or
  repair it through a forward-only, audited recovery before canary.
- Disable the affected capability flag to roll back. Retain additive schema,
  outbox and immutable ledger data; do not run destructive migrations.
- A failed batch snapshot leaves current client state intact and marks
  hydration pending for bounded retry.
- Provider/Firestore/Agora failures create retry or manual-review state; they
  never mark money, delivery or support state successful by assumption.

## Validation contract

- Shipper: typecheck, lint, architecture gate, full Jest and coverage.
- Backend: focused Maven tests/compile for affected services plus Gateway route
  security and contract checks.
- Web/customer: existing lint/build/action-contract and Flutter analyze/test
  gates as each cross-client surface changes.
- No claim of runtime concurrency, provider, staging or mobile-device proof in
  this plan.

## Decisions recorded during implementation

- 2026-08-23: Do not create a separate Shipper plan; this file is the single
  cross-repo execution memory.
- 2026-08-23: Prefer a valid identity projection even while enforcement is off;
  legacy fallback is only for projection-missing compatibility rows.
- 2026-08-23: Batch sequences represent global pickup/drop-off stop positions,
  not an item order duplicated into both sequence fields.
- 2026-08-23: POD confirmation is the terminal handoff gate only when
  `DELIVERY_POD_ENABLED=true`; storage is private and provider selection is
  fail-closed until an explicit adapter is registered. Exception events use a
  dedicated `delivery.exception.reported` topic; legacy Saga status events never
  carry `RETURNING/RETURNED`. The Settlement bridge creates only a
  `DELIVERY_DISPUTE/MANUAL_REVIEW` case and never auto-refunds.

## Progress notes

- 2026-08-23: Added the shared `ShipperIdentityResolver`; a valid projection is
  used during dual-read, divergent mappings fail closed, and only a missing
  projection may use the measured legacy fallback. Delivery batch controller,
  current-offer recovery and reject/accept paths now resolve actor identity
  before comparing ownership. Shipper offline convergence sends canonical
  `shipper.id` to Tracking.
- 2026-08-23: Added the protected batch snapshot contract and Gateway route,
  strict global-stop validation (`0..2*n-1`), additive sequence metadata on
  batch offers, and Match producer output with pickup-before-dropoff stops.
  Shipper now has strict batch/snapshot parsers, a snapshot repository port,
  canonical recovery/history selectors, normalized aggregate metadata, and
  request-fenced batch hydration. Focused proof completed in the T2 validation
  run recorded below.
- 2026-08-23: T2 proof completed: backend batch/identity focused suite and
  Shipper typecheck/lint/architecture/Jest pass. Gateway route assertions now
  cover snapshot ownership surface and method scoping. T3 added V24/V25 schema,
  private POD metadata/signed URL ports, terminal POD gate, one-retry/return
  state machine, dedicated exception outbox event, Settlement manual-review
  listener, runtime flags and Kafka source/DLT provisioning. Focused T3 proof:
  Gateway 13, Delivery 53, Settlement 24 tests passed; combined T3 run 90
  tests passed. Shipper client suite currently passes typecheck plus 47 suites /
  160 tests, lint and architecture gate.
- 2026-08-23: Generated HTTP contract refreshed to 207 operations / 176 source
  schemas; backend inventory verifier is 207/207. Root product/event docs now
  describe batch snapshot, POD, exception/return and the deferred runtime
  boundary.
- 2026-08-23: Post-scheduler focused reactor rerun passed: Gateway 13,
  Delivery 63 and Settlement 24 tests (100 total), including V24/V25 Flyway
  validation, exception expiry ordering and manual-review refund boundaries.
  `git diff --check`, HTTP inventory (207/207), generated contract check
  (207 operations / 176 source schemas), reference-bundle sync (522 files) and
  system-doc verification all pass. The clean full-reactor baseline remains
  blocked by the pre-existing notification-service test/source signature and
  acknowledgement mismatch; it is outside T3 scope and remains unfixed.
- 2026-08-23: T4 implementation started as an additive, default-off backend
  slice. Restaurant is the authority for serviceability zones; Order consumes
  only the internal evaluation contract, and Routing remains the ETA authority.

- 2026-08-23: T4 focused rerun passed: Gateway route security, Restaurant
  serviceability/geometry plus Flyway/context validation, Routing ETA and Order
  checkout policy/contract tests. `git diff --check`, reference-bundle sync
  (526 source files), system-doc verification and HTTP contract check (213
  operations / 182 source schemas) also pass. The full Restaurant suite still
  has the pre-existing controller overload and null `MeterRegistry` test
  mismatches; those remain outside this plan.

- 2026-08-23: T5 added Restaurant V9 inventory/reservation schema, owner/admin
  revision-fenced stock mutation, private reserve/commit/release APIs, sorted
  pessimistic all-or-nothing holds, 15-minute expiry and committed-release
  compensation. Order now has additive `inventoryReservationId`, a guarded
  synchronous reserve/commit boundary and same-identity local compensation;
  order cancellation/refund events carry the ID for Restaurant's raw JSON
  receipt consumer. Focused proof passed: Restaurant inventory service 5,
  inventory event processor 3, enabled-context/Flyway validation, Order client
  contract 1 and Order checkout/pricing regression suite. HTTP inventory is
  218/218 with 188 source schemas; reference bundle sync and docs verification
  pass. No PostgreSQL concurrent race, Kafka replay/DLT, staging or client UX
  claim is made.

- 2026-08-23: Hardened the T5 inventory event consumer with an explicit
  owner-isolated `-retry-inventory-*` / `.inventory.DLT` policy. Retry topics do
  not auto-create; `IllegalArgumentException` remains poison/fail-closed and
  the inventory feature is still default-off. Focused annotation/processor
  proof and shell syntax validation pass. Broker replay and PostgreSQL race
  rehearsal remain release evidence, not claimed here.

- 2026-08-23: Closed the T6 code-gated contract slice. Added immutable
  provider-neutral payment/refund and payout ports, stable operation/idempotency
  identity, canonical money validation, explicit `UNKNOWN`/manual-review-safe
  result states and an unwired PayOS adapter seam. Payout is separately
  default-off (`PAYOUT_PROCESSING_ENABLED=false`); no Spring bean, credential,
  callback, HTTP call, ledger mutation or production-provider proof was added.

- 2026-08-23: Closed the T7 code-gated per-item analytics slice. Order emits
  an additive persisted line snapshot on create/cancellation; Analytics uses
  the whole-event receipt before a `daily_item_sales` projection keyed by day,
  restaurant and menu item. Ordered/cancelled quantity and revenue remain
  separate, malformed lines fail closed, and legacy events with no item list
  remain compatible. Analytics stays default-off; no backfill, public route,
  dashboard UI, PostgreSQL concurrent upsert or Kafka broker replay claim is
  made.

- 2026-08-23: Closed the T9 code-gated notification-preferences slice. The
  additive `notification_preferences` row is keyed only by canonical
  `principalId`; a missing row means marketing opt-out. Only marketing is
  mutable through a principal-scoped atomic upsert, while transactional
  lifecycle/safety delivery stays invariant with no opt-out field or route.
  `NOTIFICATION_PREFERENCES_ENABLED=false` fails both routes closed before
  persistence. Notification/Gateway compile plus focused controller, service,
  Flyway/JPA schema and Gateway route proof passed. HTTP inventory and generated
  contract are 220 operations / 190 source schemas; the 531-file reference
  bundle, system-doc verification and `git diff --check` pass. This only
  stores/returns policy: no marketing producer, dispatch enforcement, client
  UX, PostgreSQL replica race or runtime rollout is claimed.

- 2026-08-23: Closed T12 residuals. Batch dispatch now has a pessimistic-lock
  expiry sweep for `WAITING` pool items past the Saga-owned
  `matchingDeadlineAt`; it stages the deterministic `shipper.not-found`
  result through the Match command/outbox boundary and marks the pool row
  `EXPIRED` atomically. Exact scheduler replay is a no-op, and a missing
  command is left fail-closed for a later recovery tick. The Saga timeout is
  explicitly environment-configurable (`5` minutes by default); the
  synthetic sandbox selects `1` minute so its no-shipper scenario fits the
  runtime window without changing production defaults. Simulator polling
  treats Gateway `429` as transient backpressure only, while authentication
  and other failures remain fatal. Focused Match H2/Flyway proof passed for
  expiry, stable outbox identity and replay; focused simulator validation
  passed. Full sandbox startup remained healthy, and a real Gateway-driven
  `no-shipper` run `sim-a75d1582-6546-4cec-9814-10cc7c36b6e0` passed with
  Order/Delivery `7` both `SHIPPER_NOT_FOUND`; PostgreSQL showed one sent
  `shipper.not-found` outbox row and an `EXPIRED` pool item. Earlier same-run
  happy and restaurant-reject scenarios also passed. This proves the local
  synthetic path only; provider, staging, mobile-device and multi-replica
  replay evidence remain release follow-ups.

### T9 implementation decisions

- Preference ownership is canonical authentication `principalId` only; a
  notification/profile `legacyUserId` is deliberately not a fallback key.
- Transactional notifications have no mutable storage or API representation.
  Marketing defaults opt-out for an absent row and is the sole mutable setting.
- The default-off capability returns unavailable before any preference read or
  write. No marketing event currently exists to consult the preference, so a
  later dispatch rollout must add an explicit enforcement boundary and proof.

### T12 implementation decisions

- Batch expiry processes only `WAITING` rows. A row already claimed by an open
  round is allowed to finish that round; if it is requeued after the absolute
  cutoff, the next expiry tick terminalizes it. This avoids racing the batch
  proposal transaction while preserving the deadline fence.
- Gateway rate limits remain unchanged. The simulator treats `429` only on
  read/poll paths as retryable harness backpressure; it never retries a
  mutation automatically.

### T5 implementation decisions

- Restaurant-service is the sole authority for menu inventory and reservation
  ledger. A missing inventory row, non-AVAILABLE menu item, malformed quantity
  or insufficient `on_hand - reserved` capacity fails closed; there is no
  unlimited-stock or backorder fallback.
- Inventory uses a durable hold/commit/release state machine with one
  reservation per order, UUID reservation identity, sorted pessimistic row locks,
  all-or-nothing multi-line reservation and a default 15-minute expiry. Exact
  replay is allowed only when the full reservation identity and line set match.
- `COMMITTED -> RELEASED` is an explicit compensating transition that restores
  on-hand quantity. `RESERVED -> RELEASED/EXPIRED` only returns held capacity.
  Duplicate terminal transitions are no-ops; contradictory identity is rejected.
- `RESTAURANT_INVENTORY_ENABLED` and
  `ORDER_INVENTORY_RESERVATION_ENABLED` remain false by default. Order performs
  a synchronous reserve/commit boundary and releases on the local failure path;
  order cancellation/refund events carry the additive inventory reservation ID
  for the restaurant-side replay-safe release consumer.

### T4 implementation decisions

- Serviceability zones are WGS84 GeoJSON `Polygon` values with one closed outer
  ring in v1; malformed geometry, non-finite coordinates and polygons outside
  the documented Vietnam coordinate bounds fail closed.
- A point on a polygon boundary is serviceable. Overlapping active zones are
  resolved deterministically by descending priority, then ascending zone ID.
- Public ETA is additive metadata only while the capability is off. When
  enabled, the lower bound is `ceil(drivingSeconds / 60) + prepMinutes` and
  the upper bound is lower bound plus the existing matching allowance of 10
  minutes. Routing provider/fallback source is carried explicitly.

## Result

Complete only after T13 validation, flag/default scans, and a final report that
separates code proof from deferred runtime/provider/device evidence. T2/T3 are
complete as code-gated milestones; the following remain explicit release gates:
private object-storage adapter and signed-upload integration, Kafka/PostgreSQL
runtime replay/scheduler rehearsal, Settlement operational review workflow,
and Shipper/restaurant/customer device UX for upload/return confirmation.
