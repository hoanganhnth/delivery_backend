# Execution Plan: Saga Orchestrator Production Hardening

Date: 2026-08-09

## Status

Active

## Outcome

The COD order lifecycle has deterministic timeout handling, stale-event fencing,
late-result cleanup, visible compensation failures, and generation-aware Match
cancellation. A duplicate, delayed, or concurrent timeout cannot overwrite a
newer Saga transition; an old `stop-matching` cannot cancel a legitimate rematch
or leave a Delivery orphaned without a durable recovery path.

## Context

- `docs/product/features/order-lifecycle.md`
- `docs/product/features/delivery-matching.md`
- `docs/system/events-and-data.md`
- `backend_delivery/saga-orchestrator-service/`
- `backend_delivery/delivery-service/`
- `backend_delivery/match-service/`

The 2026-08-09 audit found that the scheduled generic timeout payload omitted
the `eventId` required by the Saga inbox, had no observed-state/version fence,
and could leave a late Delivery creation orphaned.  It also found cancellation
compensation could be marked terminal before a downstream cancellation refusal
was observed.

## Scope

In scope:

- P0 Saga timeout identity, expected-state/version fencing and duplicate-safe
  handling.
- Late `delivery.created.result` cleanup after a failed/cancelled Saga.
- Make delivery cancellation refusal observable and non-silent at the Saga
  boundary.
- Durable staging/replay for early `order.cancelled` and
  `restaurant.order-confirmed` facts before Saga creation.
- Propagate the existing Saga matching SLA as an absolute deadline so Match
  cannot retry beyond the Saga timeout.
- Match command receipt/fingerprint, candidate-before-reservation staging and
  deterministic result outbox with relay retry/dead state.
- Add explicit Match database ownership, migration, Compose/Kubernetes wiring
  and backup/restore rehearsal coverage for the new replay boundary.
- Add a Saga-owned `matchingSessionId` to Find/result/stop contracts and a
  durable Match tombstone keyed by `(deliveryId, matchingSessionId)` so a stop
  received before its find command cannot cancel a later rematch.
- Focused tests for production inbox, stale timeout, late result, and
  compensation failure behavior.
- Record the remaining cross-service and public-contract work required for the
  full production plan.

Out of scope for this first implementation slice:

- Changing customer cancellation/refund policy or public cancellation API.
- A full cross-service durable early-event reducer/inbox migration for every
  topic beyond cancellation and restaurant confirmation.
- Making Redis GEO/reservations relationally authoritative, Kafka ACL
  provisioning, bulk/consumer-offset replay automation, or all three client
  contracts. A narrow operator-only, single-record DLT replay command is in
  scope because the existing resilience runbook already authorizes that
  recovery procedure.

## Approach

1. Introduce a typed internal timeout command with a deterministic identity
   derived from Saga ID, observed status, and optimistic-lock version.
2. Re-lock the Saga and compare its observed status/version/deadline before any
   timeout mutation.  A stale candidate is an acknowledged no-op.
3. Reuse the same timeout transaction for generic failure compensation or offer
   expiration; do not synthesize anonymous inbound JSON.
4. Preserve a late delivery identity and enqueue cancellation when a Delivery
   result arrives after `FAILED` as well as after `CANCELLED`.
5. Stage early cancellation/confirmation facts in `saga_db`, promote them into
   the normal Saga inbox once the aggregate exists, and sweep the narrow
   concurrent-arrival window.
6. Carry the Saga-owned matching deadline to Match and stop reactive retries at
   that cutoff.
7. Keep a delivery-cancellation refusal visible in the Saga step history and
   fail closed instead of silently discarding it.  Final product policy for
   cancel-vs-pickup remains an owner decision.
8. Persist each Match find command before volatile work, fingerprint exact
   replays, and stage the first candidate before Redis reservation.
9. Commit the deterministic found/not-found result and its Kafka outbox row in
   one Match transaction; relay rows with bounded retry and explicit DEAD state.
10. Treat match_db as command/result coordination state only; keep Redis
    GEO, offers and cancellation projections rebuildable.
11. Derive and persist one matching generation for every Saga attempt; require
    stop/result commands to name that generation, and persist a Match tombstone
    before Redis so cross-topic stop-before-find is deterministic.
12. After the tombstone commits, treat Redis cancellation/release as a durable
    `PENDING` projection relay: acknowledge Stop, retry from PostgreSQL with
    row-locked claims, and never let a finite Kafka retry budget lose the
    volatile projection.
13. Provision every retry/DLT destination from its source topic's partition
    count, explicitly include Match's `.retry` and stop/location/status DLT
    names, reconcile retention on rerun, and prove the manifest against a
    disposable auto-create-disabled Kafka broker.
14. Give every shared non-blocking source an owner-specific retry/DLT suffix,
    retain legacy generic targets during a rolling drain, and prove both new
    topic isolation and legacy manifest reconciliation on a disposable broker.
15. Provide a guarded operator-only replay path for one approved DLT record:
    default dry-run, exact coordinate and identity validation, explicit
    incident confirmation, canonical retry-topic resolution only, and no
    ACL/offset/bulk mutation.

## Risks And Recovery

- A timeout change touches durable state and Kafka command ordering.  All
  mutations remain transactional with the existing Saga outbox; rollback is a
  normal deployment rollback before the new Flyway schema is depended on.
- A stale scheduler candidate must never create a repeated compensation.  Tests
  cover version/state mismatches and exact timeout replay.
- Cancellation after pickup has customer-visible semantics.  This slice records
  the conflict and surfaces it for recovery; it does not choose whether the
  customer receives a refund or a final cancellation.
- The generation contract crosses independently ordered topics. Rollout must
  quiesce broad legacy Saga stops, migrate/deploy every Match V2 replica, then
  enable Saga V2. Match supports a V1 Find only through an event-ID fallback;
  Saga V2 skips a stop for a persisted legacy attempt without a safe session.
- Shared-source retry migration is rolling and operationally sensitive. Saga,
  Order and Notification now emit to owner-specific retry/DLT destinations, but
  old generic retry records must stay consumable by an old replica until each
  owner has zero lag. The provisioning manifest retains legacy destinations by
  default; removal from future manifests is gated on recorded drain evidence,
  not simply on code deployment completion.

## Progress

- [x] Inspect current state and establish P0 evidence.
- [x] Create this cross-repository implementation plan.
- [x] Implement typed timeout fence and durable identity.
- [x] Implement late-result and cancellation-failure convergence.
- [x] Implement durable staging for early cancellation/restaurant confirmation.
- [x] Bound Match retries by the existing Saga matching deadline.
- [x] Add focused regression tests with the real inbox boundary.
- [x] Run focused Saga/Delivery tests and record outcomes.
- [x] Prove the clean isolated Compose lifecycle, COD, raw WebSocket and failure matrix.
- [x] Correct clean-runtime blockers found by that proof without changing product policy.
- [x] Implement Match durable command/result receipt and outbox increment.
- [x] Add Match ownership, migration, runtime database wiring and backup
  inventory/rehearsal fixture coverage.
- [x] Rehearse encrypted backup/restore of every protected service database and
  Kafka metadata on disposable PostgreSQL/Kafka.
- [x] Prove Match stop/find and Saga timeout duplicate races against PostgreSQL
  with concurrent Testcontainers writers.
- [x] Fence durable Match cancellation before the volatile Redis cancellation
  projection, so a Redis outage cannot relay an already-staged result after
  Saga has stopped matching.
- [x] Add generation-aware `stop-matching`, durable Match cancellation tombstones
  and session-safe Redis offer release for cross-topic stop-before-find.
- [x] Add and rehearse canonical retry/DLT provisioning for Match and the core
  Saga flow, including source-matched partitions and retention reconciliation.
- [x] Implement owner-isolated retry/DLT suffixes for Saga, Order and
  Notification, plus a legacy-target provisioning mode for the rolling drain.
- [x] Complete the disposable runtime isolation/replay rehearsal, including
  transient Kafka AdminClient-read resilience without weakening fail-closed
  manifest validation.
- [x] Rehearse Match Redis projection loss: preserve the PostgreSQL command/
  outbox result, rebuild GEO/availability from fresh Kafka facts, and fence a
  recovered busy shipper from a new offer.
- [x] Prove two concurrent Saga timeout schedulers against PostgreSQL create
  one timeout receipt and one compensation/outbox set.
- [x] Prove two Saga outbox relay replicas use PostgreSQL `SKIP LOCKED` to
  claim/send a pending command once.
- [x] Rehearse the cross-service Match crash window after durable result staging
  and prove restart plus raw command replay converge to one offer, notification
  and Saga cache command.
- [x] Rehearse a real Saga cancellation whose generation-scoped stop reaches
  Match before its paused Find listener, then prove delayed Find persists
  CANCELLED without an offer, result outbox, notification or cache command.
- [x] Rehearse the same stop-before-find and crash/replay boundaries with two
  independent Match replicas in one Kafka consumer group.
- [x] Persist and relay a pending Match cancellation projection across a Redis
  outage after the Stop tombstone commits; rehearse two-replica Kafka ACK,
  DLT-empty recovery, Redis projection rebuild, delayed Find fencing and the
  existing crash/replay boundary in one disposable Compose run.
- [x] Add a read-only production drain gate for legacy shared retry topics: it
  verifies owner group zero lag, rejects active old assignments, and requires
  unchanged end offsets for at least one full maximum retry delay. It remains
  pending against an approved broker before disabling legacy-target
  provisioning.
- [x] Audit and harden the remaining Saga-to-core replay boundaries: Delivery
  has `delivery_inbound_receipts` for all five Saga commands, and Order has
  `saga_command_receipts` for `saga.command.update-order-status`; both fence
  exact raw-payload replay and reject contradictory reuse in the same
  transaction as the local mutation/outbox. PostgreSQL atomic conflict claims
  plus two-writer receipt/transition-or-outbox tests now prove replica
  convergence before Kafka ACK.
- [x] Rehearse the newly hardened Saga→Delivery and Saga→Order inboxes with
  Kafka plus PostgreSQL: two application replicas share one group and consume
  duplicate command IDs across separate partitions; exact same-group and
  fresh-group replay leave one receipt/effect, while contradictory identity
  reuse reaches the owner DLT. Delivery now covers all five commands (create,
  cancel, cache/expire offer and no-shipper) with their own state/outbox
  invariants; the Order rehearsal exposed and corrected a
  global JSON converter that prevented raw String Saga commands from reaching
  the listener and a fallback DLT template that JSON-quoted raw payloads.
- [x] Complete the remaining durable-consumer audit: Tracking Kafka listeners
  are rebuildable routing/history projections, while Settlement
  `delivery.completed` is the financial boundary. Replace Settlement's
  read-then-insert receipt with PostgreSQL `ON CONFLICT DO NOTHING`, then
  rehearse two Kafka replicas across duplicate partitions, exact same/fresh
  group replay and contradictory raw reuse. One receipt/four ledger entries
  converge and only the contradictory record reaches same-partition `.DLT`.
- [x] Harden Settlement's explicitly feature-gated refund intake without
  enabling it: atomically claim `refund_cases` on every event/idempotency/
  order-trigger constraint before a possible provider outbox handoff. Kafka +
  PostgreSQL two-replica rehearsal covers duplicate partitions, exact same/
  fresh group replay and contradictory raw reuse; one case remains and only
  the contradiction reaches same-partition `.DLT`. Existing manual-review,
  provider and default-off policy is unchanged.
- [x] Harden Notification's customer-visible durable ingress without changing
  optional-FCM policy: atomically insert one `PENDING` row by deduplication key
  with PostgreSQL `ON CONFLICT DO NOTHING` in a committed `REQUIRES_NEW`
  transaction before provider I/O, compare the full semantic payload on every
  replay, and classify a contradictory reuse as non-retryable. Kafka +
  PostgreSQL two-replica rehearsals cover all three customer-visible sources:
  `order.created`, `delivery.status-updated` and `delivery.shipper-offered`.
  Each covers duplicate partitions, same/fresh-group replay and direct
  owner-DLT recovery; one stable `SENT` row remains. FCM ambiguous/multi-token
  success remains explicitly at-least-once.
- [x] Harden the default-off Promotion reservation consumer without enabling
  voucher checkout or its outbox relay: atomically claim Order `eventId`,
  source topic, COMMIT/RELEASE action, order/reservation identity and SHA-256
  raw payload in `promotion_db` before the reservation transition; a failure
  rolls the receipt back for Kafka replay. Add owner-isolated Promotion
  retry/DLT provisioning. Kafka + PostgreSQL two-replica `order.created`
  rehearsal covers duplicate partitions, same/fresh-group replay and direct
  contradiction-to-DLT; one receipt and one COMMITTED reservation/outbox
  transition converge. Two-replica Kafka rehearsals for `order.cancelled` and
  `order.refund-eligible` provide the same release/replay/contradiction proof;
  all voucher flags remain off.
- [x] Harden the default-off Flash-sale reservation consumer without enabling
  checkout or its outbox relay: atomically claim Order `eventId`, source topic,
  COMMIT/RELEASE action, order/reservation identity and SHA-256 raw payload in
  `flashsale_db` before the stock transition; a failure rolls the receipt back
  for Kafka replay. Add owner-isolated Flash-sale retry/DLT provisioning. Kafka
  + PostgreSQL two-replica `order.created` rehearsal covers duplicate
  partitions, same/fresh-group replay and direct contradiction-to-DLT; one
  receipt and one COMMITTED reservation/outbox transition converge. Two-replica
  Kafka rehearsals for `order.cancelled` and `order.refund-eligible` provide
  the same release/replay/contradiction proof; all Flash-sale flags remain off.
- [x] Harden the rebuildable Restaurant→Search `entity-sync` projection: replace
  the replica-unsafe Elasticsearch read/save checkpoint with an atomic scripted
  claim and use `external_gte` nanosecond event versions for document writes.
  Testcontainers Elasticsearch and Kafka+Elasticsearch rehearsals prove
  concurrent/reordered replica claims, delayed old update/delete writers,
  same/fresh-group replay and contradiction-to-DLT convergence.
- [x] Rehearse the remaining Saga→Match find ingress with Kafka, PostgreSQL and
  Redis: two independently booted Match replicas share a configured consumer
  group and consume the same command ID on two partitions. Duplicate,
  same-group and fresh-group exact replay converge on one durable command,
  result outbox and Redis offer; contradictory raw reuse reaches the source
  partition DLT. The rehearsal exposed and corrected the hard-coded Match
  group, test-replica H2 fallback, and a race where a concurrent exact replay
  could release its own already-staged offer.
- [x] Harden Saga's own inbound receipt and pre-aggregate early-event staging:
  PostgreSQL `ON CONFLICT DO NOTHING` claims now converge a concurrent exact
  `eventId` to a reload/no-op before ACK, while topic/order/SHA-256 raw-payload
  reuse remains poison. Kafka + PostgreSQL 16 two-Saga-replica/two-partition
  rehearsals cover `order.created` (one `STARTED` Saga/create-delivery outbox),
  `delivery.created.result` (one `DELIVERY_CREATED` transition), and early
  `order.cancelled` (one staged fact), same/fresh-group replay, and
  same-partition `.saga.DLT` recovery for contradictory raw reuse.
- [x] Harden Tracking's support-history ingress without changing its realtime
  Redis/WebSocket or Match eligibility projections: atomically claim a
  `PENDING` receipt keyed by `eventId` with delivery/shipper/time identity and
  SHA-256 raw payload before persisting exactly one sampled support point or
  terminal sampling outcome. PostgreSQL `ON CONFLICT DO NOTHING` lets a losing
  replica reload an exact committed receipt as a no-op; identity/fingerprint
  reuse is non-retryable poison. Tracking now uses owner-isolated
  `-retry-tracking-*` / `.tracking.DLT`, provisioned with the source partition
  count. PostgreSQL concurrent-writer and Kafka/PostgreSQL two-Tracking-replica
  rehearsals prove duplicate two-partition, same-group and fresh-group replay
  converges to one receipt/point while contradictory raw reuse reaches the
  original partition's `.tracking.DLT`.
- [x] Close the adjacent Tracking routing-projection replica race: its prior
  in-process `synchronized` read/write only fenced one JVM, so concurrent
  `shipper.status-change` consumers could regress a shared Redis assignment.
  Redis Lua now atomically compares `(deliveryId,timestamp,eventId)` before
  routing a delivery room; stale events no-op and distinct BUSY facts at the
  same timestamp are poison. Routing gets bounded same-partition owner-DLT
  recovery as `.tracking.DLT`, distinct from Match's projection DLT. Mock and
  real-Redis Testcontainers proof cover stale, contradiction and terminal
  AVAILABLE convergence.
- [x] Close the equivalent Match live-location race: a separate Redis
  read/check/GEOADD/SADD sequence could let an old online record race after a
  newer offline tombstone from another partition/replica. Redis Lua now
  atomically fences timestamp freshness with GEO/online membership mutation.
  Redis 7 concurrency proof sends older online and newer offline together and
  leaves the shipper offline; the legacy repository call with
  `isOnline=false` delegates to the same atomic offline tombstone rather than
  accidentally creating an online GEO entry. Existing Match
  Kafka/PostgreSQL/Redis ingress rehearsal and full suite stay green. No
  ranking, radius or eligibility product policy changed.
- [x] Reconcile the Tracking routing DLT in the Kafka resilience manifest:
  `shipper.status-change.tracking.DLT` is provisioned and verified separately
  from Match's required `shipper.status-change.DLT`, each with the canonical
  source partition count and retention. This closes an operational gap only;
  no listener recovery behavior or business policy changed.
- [x] Implement and rehearse the repository-owned, operator-only DLT replay
  command. It reads one exact DLT coordinate, defaults to dry-run and requires
  an incident ID plus an exact confirmation token before producing to the
  original source topic/partition. Its Spring-header, identity, retry-topic
  canonicalization and Kafka/Testcontainers proofs pass; it neither grants
  ACLs nor changes consumer offsets.
- [x] Code-harden the default-off Analytics ingress without enabling it:
  atomically claim raw events with payload fingerprints, reject contradictory
  replay, use PostgreSQL atomic aggregate increments, and add owner-isolated
  retry/DLT topology behind a separate default-false provisioning flag. Long
  Kafka/PostgreSQL replica rehearsal is deliberately deferred; short module
  compile/unit and static validation pass in this increment.
- [ ] Prepare the remaining phase for cross-service inbox expansion, Kafka ACL
  grants and an approved live operator replay exercise, observability and
  clients.

## Decisions

- 2026-08-09: Treat timeout identity/state/version fencing as an internal
  correctness change; it does not alter the public product contract.
- 2026-08-09: Do not choose a cancellation-vs-pickup winner, asynchronous
  cancellation API behavior, refund policy, or public `SHIPPER_NOT_FOUND` UX
  without product authority.  Those remain required decisions for the next
  cross-service phase.

- 2026-08-09: The raw Gateway WebSocket endpoint rejects a missing credential
  with HTTP 401 while preserving both `Authorization: Bearer` and browser
  `Sec-WebSocket-Protocol: bearer.<token>` transports. Tracking remains the
  JWT and participant authorization authority.
- 2026-08-09: The disposable clean E2E runner may mark only its fresh fixture
  accounts email-verified because it has no email inbox. Normal seeding and
  production registration continue to require email verification.
- 2026-08-09: Match owns match_db only for source command fingerprints,
  candidate/result replay state and the result outbox. Redis remains the
  authority for live GEO, availability, offers and cancellation projection;
  no new Match product or refund policy is introduced.
- 2026-08-12: A `stop-matching` command fences/suppresses durable Match results
  before its Redis cancellation/release projection. If that PostgreSQL fence
  cannot commit, Kafka still retries/fails closed. If only Redis is unavailable
  after the fence commits, Match records `PENDING`, ACKs Stop, and a
  PostgreSQL `SKIP LOCKED` relay retries until `PROJECTED`; this prevents the
  finite Kafka retry budget from permanently losing a volatile projection.
  This is an internal correctness policy, not a change to customer
  cancellation/refund behavior.
- 2026-08-12: The approved Gateway policy already fails closed for mutations
  when Redis is unavailable, returning a bounded standard HTTP 503. The
  rehearsal verifies that edge behavior separately, restores Redis, and then
  isolates Redis unavailability to Match to exercise the post-tombstone relay.
  It does not reinterpret a deliberately rejected customer mutation as an
  orchestration failure.
- 2026-08-12: The legacy generic-retry drain gate checks the configured base
  `ConsumerFactory` groups, not a group name suffixed with the retry topic.
  All affected `@KafkaListener`s omit `groupId`; Spring Kafka therefore keeps
  the retry endpoint group override null and each retry container inherits the
  configured service group. A focused Spring Kafka test guards this assumption.
  The gate derives generic retry delays from the retired replicas'
  `KAFKA_RETRY_*` policy, so a non-default rolling deployment cannot
  accidentally certify only the default `1000/2000/4000` topics.
- 2026-08-09: Retry/DLT topics are provisioned from the canonical source topic's
  partition count. The operator helper increases an undersized target and
  reconciles 14-day delete retention on every run; it does not silently use a
  one-partition target that cannot receive a source-partition-preserving
  recoverer publish. An insufficient existing replication factor fails closed
  for an operator-controlled Kafka reassignment rather than being mutated
  blindly.
- 2026-08-09: `matchingSessionId` is a deterministic Saga-owned generation
  derived from Saga identity plus persisted `MATCHING_STARTED` ordinal. It is
  distinct from each command outbox `eventId`; Match results and stop commands
  must carry it, and Saga ignores result generations that are no longer current.
- 2026-08-09: Match V2 persists a cancellation tombstone keyed by
  `(deliveryId, matchingSessionId)` at `SERIALIZABLE` before Redis. A delayed
  find for that session is stored `CANCELLED` without GEO work; a stale stop
  cannot release a newer session's offer. V1 Find falls back to its `eventId`;
  a V2 Saga never emits a broad stop for a legacy persisted attempt.
- 2026-08-12: Saga-to-Delivery commands use a durable primary-key inbox
  (`delivery_inbound_receipts`) carrying command type, order/delivery identity
  and a SHA-256 fingerprint of the raw payload. It flushes before Delivery
  side effects, then commits or rolls back with their local mutation/correlated
  failure outbox. PostgreSQL uses `INSERT .. ON CONFLICT DO NOTHING` so a
  concurrent identical consumer converges to a no-op receipt rather than a
  duplicate-key retry. This closes the general command-replay gap beyond Delivery’s
  create-event and aggregate-state idempotency, without changing cancellation
  or refund policy.
- 2026-08-12: `saga.command.update-order-status` uses an Order
  `saga_command_receipts` inbox carrying command type, order, Saga status and
  a SHA-256 raw payload fingerprint. A dedicated transactional processor
  commits receipt plus Order mutation before the Kafka listener ACKs; PostgreSQL
  uses `INSERT .. ON CONFLICT DO NOTHING` for concurrent receipt claims; exact
  replay ACKs/no-ops and contradictory reuse fails closed. This is an internal
  orchestration correctness policy, not a public lifecycle-policy change.
- 2026-08-12: Order's shared Kafka converter preserves a Saga command whose
  listener parameter is `String` verbatim. Its receipt fingerprint is a
  contract over raw text, so treating a JSON object as a JSON string would make
  real consumer ingress fail before the processor. DTO listeners continue JSON
  deserialization; Order's fallback DLT likewise uses the raw-string template
  so rejected commands remain replayable/auditable without another quoting
  layer.
- 2026-08-09: Shared non-blocking retry destinations are service-owner
  specific: Saga uses `-retry-saga-*` / `.saga.DLT`, Order uses
  `-retry-order-*` / `.order.DLT`, and Notification uses
  `-retry-notification-*` / `.notification.DLT`. Their common
  `DefaultErrorHandler` recoverers use the same owner DLT suffix, so a fallback
  exception cannot bypass isolation. The old generic topology is provisioned
  only as a migration drain surface; it is not a new-code fallback.
- 2026-08-12: A keyed Notification is a durable customer-inbox record before
  it is an FCM wake-up. PostgreSQL atomically claims its unique dedup key in a
  separate committed transaction; the later row-locked delivery transaction
  owns `PENDING -> SENT`. Exact concurrent/replayed events therefore reuse one
  stable row, while the same key with different semantic payload is poison and
  bypasses retry to the Notification-owned DLT. This preserves the existing
  optional-FCM and at-least-once provider policy; it does not promise exactly
  one provider delivery after a provider-side ambiguous success.
- 2026-08-12: Promotion's feature-gated reservation consumer treats every
  Order source event as a durable input, not merely a reservation-ID hint. Its
  PostgreSQL receipt and reservation mutation share the listener transaction;
  exact concurrent/replayed input ACKs/no-ops, while a reused ID with different
  source/action/order/reservation/raw payload is poison and bypasses retry to
  `.promotion.DLT`. Voucher checkout and relay remain explicitly false; this
  adds no customer-facing discount or compensation policy.
- 2026-08-12: Flash-sale's feature-gated reservation consumer follows the
  same receipt-plus-local-transition rule in `flashsale_db`. Its owner-specific
  `-retry-flashsale-*` / `.flashsale.DLT` topology keeps shared Order sources
  isolated from Promotion and core consumer recovery. Exact replay no-ops only
  when source/action/order/reservation/raw payload match; reuse with different
  input is poison. Checkout and relay remain explicitly false, so this adds no
  customer-facing pricing, inventory or compensation policy.
- 2026-08-12: Search is a rebuildable projection, but must still fence durable
  Kafka replay before mutating its read store. `entity-sync` now claims each
  entity version atomically in Elasticsearch and writes documents with the
  producer's nanosecond `occurredAt` as an `external_gte` version. This makes
  cross-partition delayed writes monotonic even when an older record claimed
  before a newer record; it is an internal projection-correctness rule, not an
  ordering guarantee for unrelated aggregates or a production replay policy.
- 2026-08-13: A Match deployment consumer group is an operational input, not a
  source-code constant. All Match command/projection listeners now inherit
  `spring.kafka.consumer.group-id` (default `match-service`), so replica
  assignment and a planned replay group have one authority. Exact duplicate
  commands may race after the Redis reservation; once the stored result is the
  same candidate, the late worker acknowledges without deleting that offer.
- 2026-08-13: Saga inbound and pre-aggregate early-event IDs are a strict raw
  message contract: PostgreSQL atomically claims `(eventId, topic, orderId,
  SHA-256(rawPayload))` by primary key. Exact replay reloads and ACKs; any
  mismatch fails closed to the existing Saga owner DLT. This is a durability
  correction only and does not change cancellation, refund or public API policy.
- 2026-08-13: Tracking support-history is a durable, sampled audit projection,
  not a realtime or matching authority. Its receipt atomically claims
  `eventId`, delivery/shipper/time identity and SHA-256 raw payload as
  `PENDING`, then commits the one history/outcome terminal state in the same
  transaction. Its owner-specific retry/DLT topology is additive: Match keeps
  its existing generic source DLT for the separate live-location projection.
- 2026-08-13: Tracking's `shipper.status-change` routing projection is shared
  Redis state. JVM-local synchronization cannot coordinate replicas, so a Redis
  Lua compare-and-set now owns its timestamp/event fence. The delivery-room
  listener has a separate owner DLT, while Match remains the availability
  authority and keeps its own recovery target for the same source.
- 2026-08-13: Match location freshness is also shared Redis state. Redis Lua
  now applies each online or offline timestamp together with its GEO/online-set
  mutation, making concurrent cross-partition projections monotonic. Equal
  timestamps retain the existing state just as the prior stale fence did; this
  increment does not introduce a new business ordering, ranking or availability
  policy.
- 2026-08-13: `shipper.status-change` has two distinct error owners. Tracking
  routing sends poison input to `.tracking.DLT`; Match's availability projection
  continues to send its independently handled poison input to source `.DLT`.
  The operator manifest must provision both and must not merge the two recovery
  paths.
- 2026-08-13: The DLT replay tool is deliberately a one-record recovery
  primitive, not a consumer-group replay mechanism. It requires an incident ID
  and `REPLAY:<dlt-topic>:<partition>:<offset>` confirmation, refuses missing
  or contradictory Spring/application identity metadata, preserves application
  headers and strips prior DLT/retry diagnostics. It maps only the repository's
  known final retry suffixes to a canonical source topic, never guesses an
  arbitrary target, and leaves production ACL grants and offsets to approved
  operators.

## Remaining Production Acceptance Matrix

The next phase is not complete until the following paths have executable proof
against production-like PostgreSQL, Kafka and Redis—not only H2 or mocks.

| Boundary | Happy path | Error/recovery cases | Required proof |
|---|---|---|---|
| Saga command → Match | One canonical command selects, reserves and publishes one result | duplicate partitions/replicas, exact same/fresh-group replay, contradictory same-event replay, concurrent first receipt, crash before/after candidate staging | Kafka + PostgreSQL + Redis two-Match-replica/two-partition ingress rehearsal PASS: one command/result/offer survives duplicate and same/fresh-group replay; contradictory raw reuse reaches same-partition DLT. PostgreSQL concurrent-receipt and two-replica disposable Compose crash/restart after Match result staging plus raw Find replay also PASS. |
| Match result → Saga/Delivery | Result advances the expected Saga once | duplicate/late result, timeout race, cancellation before candidate/reservation/relay, delivery cancellation refusal | PostgreSQL two-scheduler timeout race and two-Saga-relay `SKIP LOCKED` claim PASS; two-replica disposable Compose crash/restart plus raw Find replay converged to one Delivery `WAIT_SHIPPER_CONFIRM`, one `MATCH_FOUND` notification and one Saga cache command |
| Stop → find cross-topic ordering | A stop fences only its intended matching generation without blocking a legitimate rematch | stop arrives before find, Redis unavailable after the fence, delayed old find/result after a new rematch | H2/Flyway Match tombstone + Saga generation contract tests PASS. Disposable Compose with two Match replicas made Redis unreachable only to Match: Stop committed the tombstone `PENDING` with retry metadata, committed its source offset, left Stop DLT empty, then recovered to `PROJECTED` plus the Redis cancellation key; resumed Find persisted one `CANCELLED` command with no outbox/offer/notification/cache side effect. |
| Notification ingress | One Kafka event commits one customer-inbox row, then delivers its optional FCM wake-up | duplicate partitions/replicas, same/fresh-group replay, conflicting identity reuse, provider failure after durable `PENDING` | Kafka + PostgreSQL 16 two Notification replicas prove `order.created`, `delivery.status-updated` and `delivery.shipper-offered` duplicate partitions plus same/fresh groups converge to one `SENT` row each; contradictory reuse bypasses retry to that source's same-partition `.notification.DLT`. Focused tests cover pending provider retry. FCM ambiguous/multi-token success remains at-least-once. |
| Promotion reservation ingress (default-off) | One Order event commits/release one bound voucher reservation | duplicate partitions/replicas, same/fresh-group replay, conflicting event-ID reuse, reservation-transition failure | Kafka + PostgreSQL 16 two Promotion replicas prove `order.created`, `order.cancelled` and `order.refund-eligible` duplicate partitions plus same/fresh groups converge to one receipt and expected COMMITTED/RELEASED reservation/outbox transition; conflicting reuse bypasses retry to each source's same-partition `.promotion.DLT`. PostgreSQL concurrent receipt and rollback proof PASS; all voucher flags stay false. |
| Flash-sale reservation ingress (default-off) | One Order event commits/releases one bound stock reservation | duplicate partitions/replicas, same/fresh-group replay, conflicting event-ID reuse, stock-transition failure | Kafka + PostgreSQL 16 two Flash-sale replicas prove `order.created`, `order.cancelled` and `order.refund-eligible` duplicate partitions plus same/fresh groups converge to one receipt and expected COMMITTED/RELEASED reservation/outbox transition; conflicting reuse bypasses retry to each source's same-partition `.flashsale.DLT`. PostgreSQL concurrent receipt and rollback proof PASS; all Flash-sale flags stay false. |
| Restaurant → Search projection | Latest restaurant/dish mutation reaches one searchable document | two partitions/replicas claim then write out of order, old upsert delayed after newer update/delete, exact same/fresh-group replay, contradictory same-ID reuse | Elasticsearch 7.17 Testcontainers concurrent-claim rehearsal PASS (4 cases); Kafka + Elasticsearch two Search replicas on two partitions PASS with newest document retained after reorder/replay and contradictory input sent to same-partition `.DLT`. Search remains rebuildable; production cluster outage/index recreation and controlled replay are still required. |
| Redis live projection | Eligible nearby shipper receives one live offer | Redis down/restart, expired offer, reservation race, stale location/cancellation tombstone | Testcontainers projection-loss/rebuild PASS: an exact replay does not recompute the durable result, fresh Kafka location/status facts rebuild availability and a recovered BUSY shipper is fenced. The disposable runner verifies the approved bounded Gateway mutation 503 during a global Redis outage, then proves Match-isolated cancellation-projection outage/recovery. |
| Kafka/outbox | Ordered result relay reaches the expected consumer | broker outage, send succeeds then DB commit fails, retry exhaustion/DEAD, DLT replay | Match Kafka/PostgreSQL/Redis ingress with two replicas, exact same/fresh-group replay, contradiction DLT and stop fence PASS. The owner-isolated retry manifest/rehearsal and guarded DLT replay package/unit/Testcontainers proof PASS; production legacy-drain evidence, ACL grants, Alertmanager/broker-age telemetry and an approved live replay exercise remain required. |
| Recovery and clients | Restored lifecycle resumes from one common recovery point | backup corruption, restored DB behind/ahead of consumer offsets, retry visibility in admin/customer/shipper clients | Docker backup/restore rehearsal PASS; Kubernetes reconciliation report and end-to-end client contract tests remain required |

## Validation

Passed:

- `mvn -q -pl analytics-service -DskipTests compile` and
  `mvn -q -pl analytics-service test` passed after the default-off Analytics
  ingress hardening (16 tests, zero failure/error/skip). Focused replay tests
  prove an exact duplicate does not touch aggregates and contradictory identity
  reuse fails before aggregate mutation. `bash -n` accepts the expanded Kafka
  provisioner and `git diff --check` passes. No Docker/Testcontainers or live
  Kafka/PostgreSQL two-replica rehearsal was run in this fast increment; the
  capability and its provisioning flag both remain false by default.

- `mvn -q -pl saga-orchestrator-service test` passed: Saga/H2/Flyway inbox,
  timeout, convergence, offer-timeout and Delivery command tests, including the
  early-timeout no-op regression.
- `mvn -q -pl match-service test` passed outside the socket-restricted sandbox,
  including listener, H2/Flyway command-store replay/conflict/candidate-crash,
  durable stop-before-find, stale-generation cancellation, outbox retry/DEAD
  and readiness tests. An unavailable Redis produced the expected readiness
  `503/DOWN` response without exposed components.
- `mvn -q -pl saga-orchestrator-service
  -Dtest=SagaManagerMatchingGenerationTest test` passed: initial generation is
  persisted, rematch gets a new generation, stop targets the current generation,
  stale found/not-found is ignored, and legacy persisted attempts never emit a
  broad stop. `mvn -q -pl match-service
  -Dtest=MatchCommandStoreIntegrationTest test` passed with V1 Find fallback.
- `bash -n scripts/backup-data-plane.sh`,
  `bash -n scripts/verify-backup-restore-rehearsal.sh`, and
  `bash -n scripts/verify-restored-critical-data.sh` passed.
- `bash scripts/verify-compose-config.sh`,
  `bash scripts/verify-kubernetes-manifests.sh`, and
  `node deploy/kubernetes/generate.mjs --check` passed; 58 generated Kubernetes
  manifests are current.
- `JAVA_HOME=... mvn -q -pl settlement-service test`: Flyway applied V1-V4
  on a clean schema and Hibernate validated `refund_cases` and refund outbox.
- `JAVA_HOME=... mvn -q -pl api-gateway
  -Dtest=TrustedIdentityHeaderFilterTest,GatewayRouteSecurityTest test`.
- `delivery_app/.fvm/flutter_sdk/bin/flutter test test/features/orders`:
  47 tests passed, including the `SHIPPER_NOT_FOUND` and cancellation
  affordance contract cases.
- `CLEAN_E2E_CONFIG_ONLY=true bash scripts/verify-clean-compose-e2e.sh`.
- `JAVA_HOME=... CLEAN_RUN_ID=saga_hardening_20260809_1520
  bash scripts/verify-clean-compose-e2e.sh`: clean startup; COD order 1 /
  delivery 1; unauthenticated WebSocket 401, outsider forbidden and
  participant live-location propagation; four-entry settlement and duplicate
  replay; restaurant rejection, no-shipper and rematch failure cases all
  passed. The project and run-scoped volumes were removed by the runner.
- `git diff --check` passed for both `backend_delivery` and `delivery_app`.
- `bash -n scripts/provision-kafka-resilience-topics.sh` and
  `bash -n scripts/verify-kafka-resilience-topics.sh` passed.
- `bash scripts/verify-backup-restore-rehearsal.sh` passed: all 15 protected
  service databases restored; encrypted checksum/tamper rejection, Kafka
  metadata, uniqueness and the critical smoke flow matched.
- `mvn -q -pl match-service
  -Dtest=MatchCommandStorePostgresConcurrencyTest test` and
  `mvn -q -pl saga-orchestrator-service
  -Dtest=SagaTimeoutPostgresConcurrencyTest test` passed against PostgreSQL 16.
- `bash scripts/verify-kafka-resilience-topics.sh` passed against an
  auto-create-disabled disposable Kafka broker: all canonical targets inherited
  three source partitions, retention/cleanup policy were reconciled, the
  Match `.retry` names existed, an initially one-partition DLT was increased,
  the second provisioning run was idempotent, and a target below the requested
  replication factor was rejected with a reassignment error. The disposable
  broker was removed by the verifier.
- `mvn -q -pl match-service
  -Dtest=KafkaListenerTopicConfigurationTest,FindShipperEventListenerTest test`
  and `mvn -q -pl saga-orchestrator-service
  -Dtest=SagaManagerMatchingGenerationTest,SagaManagerConvergenceTest test`
  passed after the topic-manifest changes.
- `mvn -q -pl match-service
  -Dtest=MatchKafkaPostgresRedisIntegrationTest test` passed with a disposable
  Kafka, PostgreSQL 16 and Redis 7: an eligible shipper produces one durable
  Redis offer/outbox result and relays it to a fresh Kafka consumer; an exact
  Kafka replay commits without another result; stop-before-find persists the
  PostgreSQL tombstone and Redis fence without a GEO offer/outbox; and a stop
  after result staging cancels the unsent outbox before it can reach Kafka.
- `mvn -q -pl saga-orchestrator-service,order-service,notification-service
  -Dtest=KafkaConfigTest,KafkaListenerTopicConfigurationTest test` passed after
  explicit owner-specific retry/DLT suffixes and matching fallback
  `DefaultErrorHandler` DLT destinations were added.
- Final `bash scripts/verify-kafka-resilience-topics.sh` passed after adding
  bounded retry for transient Kafka AdminClient metadata and idempotent
  create/config calls. It still fails closed after three failed attempts; the
  disposable auto-create-disabled broker verified all source-matched
  partition/retention targets, owner-isolated records, legacy-drain targets,
  idempotent rerun and low-replication rejection. The verifier removed its
  broker on exit.
- Final `mvn -q -pl match-service
  -Dtest=MatchKafkaPostgresRedisIntegrationTest test` passed; the three
  Kafka/PostgreSQL/Redis happy, exact-replay and stop-fencing tests remain
  green. Final syntax checks for both Kafka scripts, the core retry/DLT
  configuration tests and `git -C backend_delivery diff --check` also passed.
- Final recovery extension of `MatchKafkaPostgresRedisIntegrationTest` passed
  four tests with Kafka, PostgreSQL and Redis: Redis `FLUSHDB` keeps the one
  durable Match result, exact Kafka replay does not reserve/recompute it,
  fresh location/status events rebuild the volatile projection, and a new
  command receives `shipper.not-found` while the recovered shipper is BUSY.
- Final `mvn -q -pl match-service test` passed: 79 run, 0 failures/errors, 5
  Docker-disabled Redis integration skips. Its six Kafka/PostgreSQL/Redis
  ingress cases include two independently booted Match replicas on two command
  partitions, duplicate cross-partition delivery, same/fresh-group exact
  replay, and contradictory same-ID recovery to the source partition DLT.

- `mvn -q -pl notification-service -Dtest=NotificationKafkaPostgresIntegrationTest test`
  passed with Kafka and PostgreSQL 16: two Notification replicas consumed exact
  duplicates on both partitions for all customer-visible ingress sources
  (`order.created`, `delivery.status-updated`, `delivery.shipper-offered`);
  same-group and fresh-group replays left one `SENT` inbox row, and conflicting
  identity reuse reached each source's owner-specific same-partition
  `.notification.DLT`.
  The fresh-consumer assertion now filters the deterministic outbox `eventId`,
  eliminating test-order dependence from historical `shipper.found` records.
- Final `mvn -q -pl saga-orchestrator-service
  -Dtest=SagaTimeoutPostgresConcurrencyTest test` passed with a second,
  independently constructed timeout scheduler polling the same overdue Saga;
  PostgreSQL row locking plus the timeout status/version fence left one receipt
  and one compensation/outbox set.
- The same PostgreSQL test now also holds the first Saga relay inside its
  transaction while a second relay polls the same pending command. The second
  `SKIP LOCKED` query sends nothing; exactly one send and one `SENT` outbox row
  are observed.
- Match listener regression suite, H2 Flyway context, command-store replay/
  conflict/candidate-crash/cancellation tests and outbox relay retry/DEAD tests
  passed.
- `bash scripts/verify-saga-match-crash-replay.sh` passed in a fresh,
  disposable Compose project: Match was killed after its one durable command
  and `PENDING` result outbox row were staged, restarted with the relay
  enabled, and then received the original raw `saga.command.find-shipper`
  again. The run observed exactly one Match command/outbox (`SENT`), one
  Delivery in `WAIT_SHIPPER_CONFIRM`, one `MATCH_FOUND` notification and
  one `saga.command.cache-shipper-found` row. Its project and volumes were
  removed on exit.
- The final runner now scales Match to two independently healthy replicas
  before the event flow. It passed the live cross-topic ordering boundary:
  Find was paused while Stop stayed active, a confirmed COD order was
  cancelled through the customer API, and Saga's generation-scoped stop
  committed the tombstone before Find. After both Find listeners resumed, the
  delayed Kafka record created exactly one CANCELLED command and no result
  outbox, shipper offer, MATCH_FOUND notification, or Saga cache command. The
  same run then killed both replicas after one result outbox was staged; both
  were recreated and raw Find replay still converged exactly once.
- `mvn -q -pl match-service -Dtest=FindShipperEventListenerTest,`
  `MatchCancellationProjectionRelayTest,MatchCommandStoreIntegrationTest test`
  passed (31 tests). Flyway V3 was verified on H2; its PostgreSQL 16/Kafka/
  Redis integration suite passed five tests, including a real `SKIP LOCKED`
  claim of a pending cancellation projection and Redis cancellation key.
- `bash scripts/verify-saga-match-crash-replay.sh` passed on 2026-08-12 with
  two Match replicas. It verifies the approved bounded Gateway HTTP 503 for a
  mutation while Redis is globally down and compares Order status/cancellation
  fields plus `order.cancelled` outbox cardinality before/after to prove that
  the rejected request made no business mutation. It then restores Redis and
  makes Redis unreachable only to Match while a real customer cancellation
  emits Stop: the durable fence becomes `PENDING` with retry metadata, Match
  commits the Stop source offset, the Stop DLT remains empty, and restoring
  Match's Redis endpoint converges to `PROJECTED` plus the cancellation key
  before delayed Find is resumed. The existing two-replica crash/replay
  assertions then pass and the disposable project and volumes are removed.
- `bash scripts/test-verify-kafka-legacy-retry-drain.sh` passed after the gate
  was corrected to inspect the configured base consumer groups and derive
  retired generic retry topic names from `KAFKA_RETRY_*`; fixture cases prove
  active-assignment, lag, Kafka-read, quiet-window-write and custom-policy
  failures are rejected. `mvn -q -pl saga-orchestrator-service
  -Dtest=KafkaConfigTest test`, `bash scripts/verify-build-baseline.sh`,
  `bash scripts/verify-compose-config.sh` and `git diff --check` also passed.
- `bash scripts/verify-prometheus-resilience-rules.sh` passed using the pinned
  local Prometheus image: `promtool` accepts the mounted config/rules and a
  synthetic counter increase fires the approved immediate five-minute
  DLT-increase alert with its expected labels/annotations. This is static
  repository evidence only; production routing, broker-age metrics and SLO
  telemetry still require the approved observability overlay.
- `mvn -q -pl delivery-service test` and focused Delivery inbound-receipt
  transaction/Flyway/listener tests passed: first claim flushes before the
  command effect, exact replay ACKs/no-ops, contradictory payload fails
  closed, and rollback leaves the Kafka command replayable.
- `mvn -q -pl order-service test` passed after adding the Order Saga receipt
  boundary. Focused listener, receipt, Flyway/JPA and processor integration
  tests prove exact replay ACK/no-op, contradictory payload rejection, and
  receipt rollback when the Order transition fails.
- `mvn -q -pl delivery-service -Dtest=DeliverySagaKafkaPostgresIntegrationTest
  test` passed with Kafka and PostgreSQL 16: two Delivery application replicas
  shared one group and processed duplicated IDs on two partitions for every
  Saga command: create retained one `FINDING_SHIPPER` delivery plus
  `DELIVERY_CREATED_RESULT`, cancel one `CANCELLED` delivery plus status outbox,
  cache-offer one `WAIT_SHIPPER_CONFIRM` offer plus `SHIPPER_OFFERED`, expire
  one cleared `FINDING_SHIPPER` offer without a new outbox, and no-shipper one
  terminal status outbox. Each exact same-group/fresh-group replay left one
  receipt/effect; contradictory reuse reached that command's same-partition
  DLT. Final `mvn -q -pl delivery-service test` passed 99 tests with zero
  failure/error/skip.
- `mvn -q -pl order-service -Dtest=KafkaConfigTest,`
  `KafkaListenerTopicConfigurationTest,SagaOrderKafkaPostgresIntegrationTest
  clean test` passed with Kafka and PostgreSQL 16: two Order application
  replicas shared one group and converged to one receipt/`PENDING ->
  FINDING_SHIPPER` transition across duplicate partitions and fresh-group
  replay; contradictory reuse reached `.order.DLT` with the original raw JSON.
  This rehearsal exposed and corrected the global raw-String converter and
  JSON-quoting fallback-DLT defects.
- Final `mvn -q -pl delivery-service test`, `mvn -q -pl order-service test`,
  `bash scripts/verify-build-baseline.sh`, `bash scripts/verify-compose-config.sh`
  and `git diff --check` passed after the Kafka inbox rehearsal changes.
- `mvn -q -pl delivery-service -Dtest=DeliverySagaCommandPostgresConcurrencyTest test`
  passed against PostgreSQL 16: two concurrent identical no-shipper commands
  committed one receipt, one Delivery terminal transition and one canonical
  status outbox. The first implementation exposed duplicate-key loss for the
  loser; atomic PostgreSQL conflict handling corrected it.
- `mvn -q -pl order-service -Dtest=SagaOrderCommandPostgresConcurrencyTest test`
  passed against PostgreSQL 16: two concurrent identical update-status
  commands committed one receipt and one `PENDING -> FINDING_SHIPPER`
  transition.
- Final `bash scripts/verify-build-baseline.sh`,
  `bash scripts/verify-compose-config.sh`, and `git diff --check` passed
  after static guards were added for both Delivery and Order Saga inboxes.
- `mvn -q -pl settlement-service -Dtest=SettlementKafkaPostgresIntegrationTest
  clean test` passed with Kafka and PostgreSQL 16: two Settlement replicas
  shared one group and consumed the same completion event ID from two
  partitions. Exact same-group and fresh-group replay left one receipt and
  exactly four COD ledger entries; contradictory raw event-ID reuse reached
  the same-partition `.DLT`. The rehearsal required an atomic PostgreSQL
  receipt claim rather than read-then-insert so the competing replica can
  converge directly to an ACK/no-op.
- `mvn -q -pl settlement-service
  -Dtest=SettlementRefundKafkaPostgresIntegrationTest clean test` passed with
  Kafka and PostgreSQL 16 while enabling only the test fixture's refund
  listener: two replicas converge duplicate `order.cancelled` records from
  two partitions to one `NO_REFUND_REQUIRED` COD case/no provider outbox;
  exact same-group/fresh-group replays are no-ops and contradictory raw
  event-ID reuse reaches same-partition `.DLT`. Production refund flags remain
  false.
- `mvn -q -pl notification-service test` passed after Notification's atomic
  durable-ingress change. `NotificationKafkaPostgresIntegrationTest` passed
  with Kafka and PostgreSQL 16: two replicas in one group consume an
  `order.created` identity duplicated across partitions; same-group and fresh-
  group replay leave one stable `SENT` notification, while a semantically
  contradictory reuse goes directly to `.notification.DLT`. The focused retry-
  topic configuration tests confirm `NotificationConflictException` bypasses
  retry, preserving the original raw payload for owner-DLT recovery.
- `mvn -q -pl search-service
  -Dtest=ElasticsearchProjectionConcurrencyIntegrationTest test` passed with
  Elasticsearch 7.17 Testcontainers: two concurrent claims plus delayed old
  writer retain the newest update; exact replay is allowed and contradictory
  reuse fails closed; a legacy checkpoint only upgrades on exact metadata
  replay (including zero-second/UTC-offset legacy timestamp serialization)
  using Elasticsearch `_seq_no`/`_primary_term` compare-and-set; and a delayed
  old upsert cannot resurrect a newer DELETE. The
  existing `LocalDateTime` event-time contract is deliberately unchanged:
  timezone-aware migration needs explicit rollout authority, so this increment
  does not risk a version regression against existing checkpoints.
  `mvn -q -pl search-service
  -Dtest=SearchKafkaElasticsearchIntegrationTest test` passed with Kafka plus
  Elasticsearch and two application contexts: cross-partition reorder,
  same-group and fresh-group replay retain one newest document, while a reused
  event ID with changed payload reaches the same-partition `.DLT`. Final
  `mvn -q -pl search-service test` passed 25 tests with zero
  failure/error/skip; `git -C backend_delivery diff --check` passed.
- `mvn -q -pl promotion-service test` passed after the default-off voucher
  ingress hardening. `PromotionReservationKafkaPostgresIntegrationTest` passed
  with Kafka and PostgreSQL 16: two replicas consume duplicated
  `order.created` records from distinct partitions, same-group and fresh-group
  replay converge to one receipt/COMMITTED reservation and two deterministic
  reservation outbox states, and a conflicting reused event ID goes directly to
  `.promotion.DLT`. `PromotionOrderReservationReceiptPostgresConcurrencyTest`
  also proves concurrent exact claims and receipt rollback when the local
  reservation transition fails. No Promotion feature flag was enabled.
- `mvn -q -pl flashsale-service test` passed after the default-off stock
  reservation ingress hardening. `FlashSaleReservationKafkaPostgresIntegrationTest`
  passed with Kafka and PostgreSQL 16: two replicas consume duplicated
  `order.created` records from distinct partitions; same-group and fresh-group
  replay converge to one receipt/COMMITTED reservation and two deterministic
  outbox states, while a conflicting reused event ID goes directly to the
  same-partition `.flashsale.DLT`. The PostgreSQL receipt concurrency test also
  proves exact concurrent claims and receipt rollback when the local stock
  transition fails. `KafkaConfigTest` verifies the outbox template remains the
  unambiguous primary bean while retry/DLT uses the explicitly named raw-string
  template. No Flash-sale feature flag was enabled.
- `bash scripts/verify-kafka-resilience-topics.sh` passed after Flash-sale
  `-retry-flashsale-*` / `.flashsale.DLT` was added to the manifest: on a
  disposable auto-create-disabled broker it verified source-matched partitions,
  retention/cleanup reconciliation, idempotent two-pass provisioning,
  owner-isolated topics, legacy-drain targets and fail-closed low replication.
- Final `mvn -q -pl flashsale-service -DskipTests compile`,
  `bash scripts/verify-compose-config.sh`, `bash scripts/verify-build-baseline.sh`,
  both Kafka-script syntax checks, and `git diff --check` passed after the
  Flash-sale increment.
- Final `mvn -q -pl saga-orchestrator-service test` passed: 83 tests, zero
  failures/errors/skips. `SagaKafkaPostgresIntegrationTest` uses Kafka and
  PostgreSQL 16 with two independently booted Saga contexts sharing a group:
  duplicate cross-partition `order.created` commits one `STARTED` Saga and one
  create-delivery outbox, `delivery.created.result` one `DELIVERY_CREATED`
  transition, and early `order.cancelled` one staged event. Exact same-group
  and fresh-group replays are no-ops; contradictory raw event-ID reuse reaches
  each source partition's `.saga.DLT`. Focused tests cover the atomic conflict
  reload path; `git diff --check` passed.
- Final `mvn -q -pl tracking-service test` passed after Tracking's support
  history ingress was made replica-safe. `LocationHistoryPostgresConcurrencyTest`
  proves two PostgreSQL writers retain one fingerprinted `PERSISTED` receipt
  and one point, while a changed raw payload for the same event ID is poison.
  `LocationHistoryKafkaPostgresIntegrationTest` boots two independent Tracking
  application contexts against Kafka and PostgreSQL 16: duplicate records on
  two source partitions, same-group replay and a fresh replay group converge
  to one receipt/point; only the contradictory raw reuse publishes unchanged
  JSON to the original partition of `.tracking.DLT`. Configuration tests lock
  the raw recovery serializer and retain Tracking's normal JSON publisher.
- `ShipperDeliveryRoomKafkaRedisIntegrationTest` passed with Kafka,
  PostgreSQL 16 and Redis: two independent Tracking contexts shared a routing
  consumer group over two partitions; exact same/fresh-group replays retained
  one active delivery-room assignment and a contradictory same-timestamp BUSY
  record reached the original source partition of `.tracking.DLT` without
  overwriting it. `ShipperDeliveryAssignmentRedisIntegrationTest` separately
  executes the Lua stale/contradiction/AVAILABLE fence on Redis 7.
- `bash scripts/verify-kafka-resilience-topics.sh` passed after the Tracking
  owner-specific `shipper.location-updated-retry-tracking-*` /
  `.tracking.DLT` targets were added: a disposable auto-create-disabled broker
  reconciled the expanded manifest twice, then verified source-matched
  partitions, retention and the low-replication fail-closed gate. Match keeps
  the generic source `.DLT` for its separate live-location projection.
- Final `mvn -q -pl match-service test` passed after the Match location Lua
  increment (79 tests, zero failures/errors; five Docker-disabled integration
  skips). `MatchKafkaPostgresRedisIntegrationTest` passed independently (six
  Kafka/PostgreSQL 16/Redis 7 cases), including the pending cancellation
  tombstone relay that was previously intermittent. Focused Redis 7 proof also
  covers the legacy `addOrUpdateShipperLocation(..., false, ...)` call and
  confirms it performs the atomic offline tombstone instead of resurrecting a
  shipper. `git diff --check` and both Kafka-script syntax checks passed.
- The expanded Kafka verifier was started against a fresh auto-create-disabled
  broker. Before its intentionally long full-manifest assertion phase was
  stopped, it completed creation/reconciliation far enough to directly observe
  `shipper.status-change.tracking.DLT` with three source-matched partitions,
  `retention.ms=1209600000`, and `cleanup.policy=delete`. This is direct proof
  of the added target but not a replacement for a future completed full verifier
  run; the existing pre-change full verifier result remains recorded above.

Runtime proof exposed and corrected a pre-existing Settlement V4 migration
outside Flyway's resource classpath, clean-fixture email verification, an
unauthenticated Gateway WebSocket HTTP-upgrade gap, a cached-location probe
false negative, and a failure-matrix secondary-login rate-limit retry gap.

Still required before claiming the full production program: recorded
production zero-lag completion of the shared-source retry-topic migration,
full cross-service recovery rehearsal beyond the exercised Gateway global-Redis,
Match-local Redis and crash windows,
Kafka ACL grants, Alertmanager/broker-age alert routing and an approved live
controlled DLT replay exercise (including the rebuildable Search index),
remaining cross-service inbox expansion beyond the core Saga boundaries,
end-to-end client recovery contracts,
observability SLOs, and explicit
cancellation/refund ownership.

## Result

This first Saga hardening slice is implemented and verified through focused
tests, PostgreSQL concurrency tests, a clean isolated runtime E2E, encrypted
backup/restore rehearsal, explicit Kafka retry/DLT provisioning proof and a
two-replica disposable cross-service Match crash/replay plus stop-before-find
rehearsal. Its
final Match increment also ensures a Redis cancellation outage cannot bypass
the durable stop fence or strand its volatile projection after the durable
fence, including when Stop and Find arrive on different topics. The plan remains Active for the
explicitly scoped next production phase. The later default-off Promotion and
Flash-sale ingress increments add receipt/fingerprint fences, isolated
retry/DLT recovery and two-replica `order.created` plus both release-source
convergence proof without enabling either checkout path. No unresolved product policy was
chosen as part of this slice. The Search increment further fences its
rebuildable `entity-sync` projection across Kafka partitions/replicas with an
atomic Elasticsearch checkpoint and externally versioned writes; it does not
claim Elasticsearch cluster-recovery, ACL or controlled-production-replay
readiness.
The final Match ingress extension additionally makes its Kafka consumer group
deployment-configurable and proves duplicate command ownership/replay against
the real PostgreSQL/Redis/Kafka boundary; it does not claim a production ACL,
controlled replay, alert-routing or multi-service recovery rollout.
The final Saga ingress extension removes the remaining read-then-insert race in
the orchestrator's own receipt and early-event staging paths, with two-replica
Kafka/PostgreSQL replay/DLT proof. It does not expand the two permitted early
facts, or claim Kafka ACL, controlled production DLT replay, alert routing or
broad multi-service recovery readiness.
The final Tracking/Match projection increment preserves the legacy explicit
offline repository contract while closing the multi-replica Redis freshness
race, and reconciles Tracking's independently owned status-routing DLT in the
operator manifest. It does not merge Tracking's recovery path with Match's
source DLT, enable any disabled payment/refund/promotion capability, or claim
completion of the remaining production acceptance phase.
The final DLT recovery increment adds a default-dry-run, incident-confirmed
single-record replay command with package, unit and Kafka/Testcontainers
evidence. It preserves the durable inbox/replay contract but does not grant
Kafka ACLs, mutate consumer offsets, bulk replay records, certify production
Alertmanager/broker-age telemetry or replace an approved live operator
exercise.

## Offer Confirmation Increment

- [x] Gate Order `WAIT_SHIPPER_CONFIRM` on Delivery's transactional
  `delivery.offer-persisted` acknowledgement and add per-order Saga status
  sequence fencing at the Order inbox.
