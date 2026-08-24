# Execution Plan: Order resilience hardening

Date: 2026-08-22

## Status

Active

## Outcome

Reduce order-path cascade failure risk under slow/down HTTP dependencies and
Kafka outage while preserving the current compatibility rollout contract. The
write path must keep local DB transactions short, publish outbox records without
holding DB row locks, and expose dependency failures with retryable semantics.

## Context

- `backend_delivery/docs/workflows/order_lifecycle_flow.md`
- `backend_delivery/docs/runbooks/rollout-and-rollback.md`
- `backend_delivery/order-service/`
- `backend_delivery/saga-orchestrator-service/`
- `backend_delivery/delivery-service/`
- `backend_delivery/restaurant-service/`
- `backend_delivery/match-service/`

The backend worktree already contains unrelated uncommitted matching, batch,
promotion, settlement and deployment changes. Do not reset, checkout, or
rewrite those changes.

## Scope

In scope:

- Order create transaction and dependency error classification.
- Lease-based outbox relay for core order lifecycle services where the existing
  schema can support a safe, backward-compatible implementation.
- Small timeout/bulkhead/input guardrails that do not change the public checkout
  contract.

Out of scope:

- Enabling quote/idempotency enforcement before client rollout evidence.
- Enabling payment, voucher, flash-sale or batch capabilities.
- Production infrastructure provisioning or destructive data changes.
- Full outage/load rehearsal; only compile/static checks and focused checks are
  required for this pass.

## Approach

1. Inventory current source/config and preserve unrelated dirty changes.
2. Move remote preflight work out of the Order write transaction while retaining
   quote re-check/consume fencing in the local transaction.
3. Add typed retryable dependency failures and bounded concurrency around active
   outbound calls.
4. Replace relay-held row locks with claim/send/CAS lease flow. Preserve event
   IDs, aggregate ordering and operator recovery semantics.
5. Compile affected Maven modules and run only narrow validation where needed.

## Risks And Recovery

- Lease schema or query mistakes could strand outbox rows. Keep lease recovery
  bounded, preserve the old status fields, and verify compile/query contracts
  before rollout.
- Moving preflight changes transaction timing. Keep canonical fingerprint and
  quote ownership checks authoritative in the final write transaction.
- Any public status-code change must be deployed behind the existing client
  compatibility path and observed before enforcement changes.

## Progress

- [x] Inspect source/config and record dirty-worktree boundary.
- [x] Harden Order create path.
- [x] Harden core outbox relays.
- [x] Add safe Match/Restaurant guardrails where bounded.
- [x] Lease idempotency keys before remote create preflight and release them on
  deterministic failure.
- [x] Compile and report validation limits.

## Decisions

- 2026-08-22: Preserve `ORDER_QUOTE_ENFORCEMENT_ENABLED=false` for this pass;
  changing it is an externally observable client rollout decision.
- 2026-08-22: Prefer durable DB-backed recovery and bounded backpressure over
  increasing connection pools as the first mitigation.
- 2026-08-22: Production outbox relays claim one event per lease budget. This
  keeps sequential batch processing from allowing a shared lease to expire;
  bounded scheduler pools and per-instance create admission provide the first
  backpressure layer.
- 2026-08-22: Quote lookup/preview and reservation HTTP calls must not run
  inside a long-lived Order DB transaction. Reservation capabilities remain
  default-off until their remote compensation path is independently durable.
- 2026-08-22: Order WebClient uses bounded connection/pending-acquire pools
  and explicit connect/response timeouts; create admission rejects excess
  work with retryable 503 instead of allowing local DB/HTTP queues to grow
  without bound.
- 2026-08-23: Create-order idempotency is claimed before remote preflight with
  a bounded processing lease. A crashed worker can be reclaimed after expiry;
  a failed preflight releases its own lease, while the final write transaction
  still fences completion by the lease token and pessimistically locks the
  receipt, preventing a reclaim race from creating a second order.

## Validation

- Focused proof: compile affected modules and inspect generated SQL/config.
- Integration or end-to-end proof: deferred by request; runtime outage/load
  rehearsal remains outstanding.
- Repository-required checks: no destructive Harness operation.

## Result

Implemented the order-path transaction split, retryable dependency failures,
per-instance create admission, leased idempotency claims, and lease-based
relays for Order, Saga, Delivery, Restaurant, and Match. Main-code compilation
passed for all affected Maven modules after the idempotency lease change.
Runtime outage/load rehearsal and full integration tests remain deferred by
request; optional reservation capabilities remain disabled.
