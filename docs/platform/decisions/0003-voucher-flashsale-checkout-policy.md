# 0003 Voucher And Flash-Sale Checkout Policy

Date: 2026-07-30

## Status

Accepted for the legacy single-voucher rail; stacking rules are superseded by
decision 0004.

## Context

Promotion and flash-sale catalog/admin capabilities exist, but checkout is
intentionally disabled because ownership, stacking, pricing authority, durable
reservation, compensation, and coordinated rollout semantics were not complete.
The old voucher path can double-use quota, the flash-sale path can partially
reserve a multi-item request, and local Order rollback cannot undo a remote
reservation. A lasting cross-project policy is required before implementation.

## Decision

- Campaigns are owned and administered by `ADMIN`.
- Flash-sale items are restaurant-owned. The server derives and verifies the
  canonical `restaurantId`; activation requires admin approval.
- A voucher has `PLATFORM` or `RESTAURANT` scope. Restaurant vouchers apply only
  to an order for that canonical restaurant.
- An order accepts at most one voucher. Voucher and flash-sale discounts do not
  stack in the first rollout.
- Pricing is server-owned. Order snapshots canonical regular/flash item prices,
  discount, shipping fee, and final total; clients only submit selections and
  display the returned breakdown.
- Every voucher/stock hold has a 15-minute TTL and the state machine
  `RESERVED -> COMMITTED | RELEASED | EXPIRED`. Terminal transitions are
  idempotent. `COMMITTED -> RELEASED` is the only compensating transition and
  is allowed for order cancellation or payment failure before fulfillment.
- Checkout failure, Order cancellation, and payment failure release reservations.
  Reserve, commit, and release use stable `reservationId + orderId`, a
  transactional outbox, and replay-safe consumers.
- Gateway routes and Flutter/Web capability remain disabled until backend
  contracts and validation pass. Backend, Gateway, and client rollout flags are
  enabled together; no client price fallback or fabricated reservation success
  is permitted.

## Alternatives Considered

1. Let restaurants own campaigns as well as items. Rejected because campaign
   governance and approval are platform policy, while item eligibility and
   inventory remain restaurant data.
2. Allow multiple vouchers or voucher/flash stacking immediately. Rejected
   because ordering, caps, allocation, and settlement rules have no approved
   authority and substantially enlarge the race/reconciliation surface.
3. Trust client totals or fall back to menu prices when a promotion dependency
   fails. Rejected because this permits inconsistent charges and settlement.
4. Use only Redis TTL/decrements without durable reservation records. Rejected
   because recovery, audit, replay fingerprinting, and compensation cannot be
   proved after a crash or Redis loss.
5. Expose clients before the full backend rollout. Rejected because visible UI
   would advertise a checkout path that still fails closed by design.

## Consequences

Positive:

- Ownership and discount eligibility are deterministic and enforceable server-side.
- Atomic reservations prevent oversell and voucher double-use while durable
  identities make expiry, compensation, and replay auditable.
- Order and settlement share one immutable monetary source of truth.
- Coordinated gating prevents contract drift from becoming user-visible.

Tradeoffs:

- The first rollout supports fewer stacking scenarios and requires additional
  reservation/outbox storage and expiry operations.
- Checkout fails closed when authoritative pricing or reservation dependencies
  are unavailable.
- Restaurant-created flash-sale items require an approval workflow.

## Superseded stacking addendum

The single-voucher limitation in this decision is superseded for the stacked
voucher capability by decision 0004. The legacy request/reservation rail and
stored data remain readable during the expand/contract rollout.

## Follow-Up

- Implement and validate the contract in the Task 21 execution plan.
- Document operator reconciliation, expiry, replay, and rollback procedures.
- Any future multi-voucher or stacking behavior requires a new accepted decision
  covering ordering, caps, tax/shipping allocation, refund, and settlement rules.
