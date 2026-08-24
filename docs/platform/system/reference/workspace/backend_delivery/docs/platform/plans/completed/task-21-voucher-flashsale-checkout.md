# Execution Plan: Task 21 Voucher And Flash-Sale Checkout

Date: 2026-07-30

## Status

Completed (2026-08-01)

## Outcome

Voucher and flash-sale checkout can be enabled without overselling, double use,
or divergent totals. The backend owns pricing and durable reservation state;
Gateway and Flutter/Web expose the capability only after contract, concurrency,
replay, compensation, and amount-reconciliation proof passes.

## Context

- Approved product and architecture policy: [Decision 0003](../../decisions/0003-voucher-flashsale-checkout-policy.md).
- Current product truth: [order lifecycle](../../product/features/order-lifecycle.md)
  and [feature inventory](../../product/features/README.md) intentionally keep
  voucher/flash-sale checkout disabled.
- Backend contract truth: `backend_delivery/docs/workflows/promotion_voucher_flow.md`,
  `backend_delivery/docs/workflows/flash_sale_flow.md`, and
  `backend_delivery/docs/system-contract-inventory.md`.
- Existing implementation has no durable voucher reservation, flash-sale stock
  reservation can partially decrement a multi-item request, and cancellation
  events do not carry stable reservation identities.
- The worktrees contain unrelated in-progress work. This plan must preserve it
  and keep Task 21 edits isolated by file and behavior.

## Scope

In scope:

- Durable voucher and flash-sale reservation state machines and migrations.
- Atomic stock/use reservation, idempotent commit/release/expiry, transactional
  outbox contracts, and replay-safe consumers.
- Server-owned preview/create pricing, Order monetary snapshot, cancellation and
  payment-failure compensation, and settlement reconciliation.
- Gateway route/security contracts and coordinated rollout flags.
- Flutter checkout contract/presentation and Web admin/merchant capability
  surfaces after backend gates pass.
- Concurrent, failure-window, replay, expiry, and end-to-end validation.

Out of scope:

- Supporting more than one voucher per order.
- Voucher/flash-sale stacking in the first rollout.
- Client-computed prices, best-effort fake reserve success, or price fallback.
- Replacing the current payment architecture beyond emitting/consuming the
  compensation signals needed by this checkout contract.

## Approach

1. Freeze the API/event/state/ownership contract and inventory all affected
   promotion, flash-sale, order, settlement, Gateway, Flutter, and Web surfaces.
2. Implement voucher reservations first: durable records, uniqueness/locking,
   reserve/commit/release/expiry, outbox, and race/replay proof.
3. Implement flash-sale reservations: durable audit/source state plus one atomic
   all-items stock operation, idempotent lifecycle, outbox, and recovery proof.
4. Integrate Order preview/create with canonical prices and stable reservation
   identities. Snapshot menu/flash price, discount, shipping, and final total;
   emit replay-safe compensation for all failure/cancel/payment paths.
5. Prove settlement consumes the same final total, then freeze docs/contracts.
6. Add Gateway routes and enable Flutter/Web capability only in a coordinated
   rollout with all backend flags. Keep every new flag off until proof passes.

## Risks And Recovery

- Distributed failure between reserve and Order persistence can strand quota.
  Mitigation: stable `reservationId + orderId`, 15-minute expiry, idempotent
  release, and transactional outbox. Recovery: expiry sweep plus replay of the
  recorded release command/event.
- Cache/database divergence can oversell or leak stock if cache decrements are
  authoritative. Mitigation: the database is the durable stock source and the
  full item set is locked, validated, and updated in one transaction; Redis is
  not used as reservation authority. Recovery: disable checkout flags and
  reconcile active reservations against durable item counters before rollout.
- Contract drift can expose unusable UI. Mitigation: backend-first rollout and
  Gateway/client contract tests. Recovery: turn off client/Gateway/backend flags
  in that order; retained catalog/admin reads remain available.
- Existing dirty worktrees can be overwritten accidentally. Mitigation: inspect
  every target diff before editing and never restore/reset unrelated changes.
- A monetary mismatch can corrupt settlement. Mitigation: immutable Order price
  components and reconciliation tests. Recovery: fail closed before settlement,
  retain the order/reservation audit, and require operator reconciliation.

## Progress

- [x] Product authority approved and recorded.
- [x] Inventory current API, event, schema, ownership, pricing, and client gates.
- [x] Freeze voucher reservation API/event contract and implement lifecycle:
  durable state/locking/TTL, deterministic outbox IDs, and PostgreSQL duplicate
  checkout race proof.
- [x] Freeze flash-sale reservation API/event contract and implement lifecycle:
  durable atomic all-items state/TTL, transactional outbox, terminal replay,
  expiry/compensation, and PostgreSQL last-stock/all-lines proof.
- [x] Integrate Order canonical preview/create snapshot and compensation.
- [x] Prove settlement amount reconciliation in executable module tests.
- [x] Freeze Gateway and client contracts with coordinated flags defaulting off.
- [x] Run the approved persistent Compose runtime rehearsal through Gateway on
  retained PostgreSQL, Redis, Kafka, Eureka, Config Server, Delivery, and
  Settlement infrastructure; rollback restored every checkout/relay flag.
- [x] Run module, concurrency, replay, crash-window, Gateway, Compose-config,
  Flutter, and Web gates. The repository-wide baseline remains red only because
  unrelated in-progress `notification-service` changes fail context startup.
- [x] Update product, contract, workflow, and rollback/runbook truth.
- [x] Record the runtime result and move this plan to completed.

## Decisions

- 2026-07-30: The user accepted the Task 21 baseline captured in Decision 0003.
- 2026-07-30: Backend implementation and proof precede any Gateway or client
  exposure because current repository authority explicitly keeps checkout off.
- 2026-08-01: Promotion and Flash-sale join the active Eureka runtime platform
  because Task 21 makes their Gateway `lb://` routes executable; checkout and
  relay flags remain default-off and rollback restores them to false.
- 2026-08-01: Password-registration email verification remains production
  authority. The retained-volume rehearsal uses an explicit default-off local
  fixture opt-in rather than weakening login policy.

## Validation

- Focused proof: state transitions, invalid ownership/scope, one-voucher rule,
  no-stacking, exact price components, idempotent reserve/commit/release/expiry.
- Concurrency proof: last-stock boundary, simultaneous duplicate voucher use,
  multi-item all-or-nothing stock, concurrent release/commit, and reset/expiry.
- Failure proof: dependency timeout/5xx, Order rollback after reserve, outbox
  crash window, consumer restart, exact replay, conflicting replay, cancel, and
  payment failure.
- Integration or end-to-end proof: normal/voucher/flash checkout through Gateway,
  unavailable/expired/exhausted cases, client capability gating, and
  Order-to-settlement amount reconciliation on real infrastructure where races
  cannot be proven by mocks or H2.
- Repository-required checks: affected Maven modules, Gateway route/security and
  contract gates, Compose contract/smoke gates, Flutter analyze/tests, and Web
  lint/type/test/build gates.

Evidence recorded on 2026-07-30/31:

- Promotion, Flash-sale, Order, Delivery, and Settlement module suites pass;
  PostgreSQL Testcontainers cover duplicate voucher reserve, last-stock race,
  all-lines rollback, terminal replay, expiry, and compensating release.
- Gateway route/security contract passes; Compose configuration contract passes.
- Order's private HTTP contract now pins Promotion/Flash to Compose ports
  `8096`/`8092`; `CheckoutReservationClientContractTest` proves the exact URL,
  internal credential, stable reservation identity, and same-identity release.
- Flutter full analyze and full test suite pass (216 tests); explicit OFF/ON
  checkout builder/capability contracts also pass.
- Web full tests pass (39 tests); lint and production build pass after the final
  ownership/scope parser changes.
- `scripts/verify-voucher-flashsale-checkout.sh` is syntax-checked and packages
  normal/voucher/flash checkout, duplicate/exhausted/no-stack, cancel/replay,
  and Order→Delivery→Settlement amount reconciliation into one retained-volume
  rehearsal. Its rollback disables Order first and refuses to disable the
  reservation services/relays if fixture state or `PENDING`/`DEAD` outbox rows
  still require recovery.
- `scripts/verify-build-baseline.sh` currently cannot turn green because the
  unrelated dirty `notification-service` fails
  `NotificationServiceApplicationTests.contextLoads`: Spring cannot instantiate
  the in-progress `FirebaseService` constructor. Task 21 modules are not the
  source of that failure.

Runtime evidence recorded on 2026-08-01:

- `bash scripts/verify-voucher-flashsale-checkout.sh` exited 0 after proving
  normal checkout, voucher canonical preview/create, duplicate rejection,
  voucher release/replay, flash canonical price, exhausted-stock rejection,
  flash release/replay, no-stacking, and Delivery/Settlement replay safety.
- The completed flash checkout is Order `23` / Delivery `14`. Order total,
  `delivery.completed.totalPrice`, Delivery row total, and COD debit are all
  `42000.00`; Settlement contains exactly four ledger rows and one receipt after
  exact `delivery.completed` replay.
- Voucher order `19` and flash order `21` reservations are `RELEASED`; delivered
  flash order `23` is the only fixture `COMMITTED` reservation. Promotion and
  Flash-sale have zero `PENDING`/`DEAD` reservation outbox rows.
- Order, Promotion, Flash-sale, Delivery, and Settlement are healthy and each
  has exactly one `UP` Eureka instance. The two Order checkout flags, two
  Promotion flags, and two Flash-sale flags are observably `false` after
  rollback.
- Runtime discovery and fixture drift found by the rehearsal was corrected:
  explicit Auth/User application ports, current Gateway registration handoff,
  Promotion/Flash runtime-platform membership, one-shot Auth discovery behavior,
  mutable Hibernate Order item collections, timezone-independent fixtures, and
  Delivery acceptance of non-negative canonical discounts.
- Final focused Auth/User config, Order pricing/persistence, and Delivery event
  validation tests pass; Compose configuration, shell syntax, and
  `git diff --check` pass.

## Result

Task 21 is complete. Product authority, state machines, migrations, event/API
contracts, Gateway/client gates, concurrency/replay/compensation proof, and the
retained-stack runtime rehearsal all pass. Runtime rollback restored the six
checkout/relay flags and left no unsafe reservation outbox state. The unrelated
dirty `notification-service` baseline failure remains disclosed and was not
used as evidence for or against the affected Task 21 behavior.
