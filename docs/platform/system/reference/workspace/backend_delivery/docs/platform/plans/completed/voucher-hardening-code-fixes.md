# Voucher hardening — code fixes

Date: 2026-08-23

## Outcome

Harden the implemented Shopee-style voucher stacking flow in backend, Web and
Flutter without changing Docker/Kubernetes/runtime infrastructure. Preserve all
pre-existing user changes in the backend worktree.

## Scope

- Fix expired reservation commit semantics and internal stacking gates.
- Make preview/create voucher payloads canonical and keep attribution correct.
- Enforce the existing downstream invariant that a payable order retains a
  positive food amount (`totalPrice > gross shipping`).
- Fix platform-voucher cap defaults and client `layerCode` contract handling.
- Add focused regression tests and run no-Docker validation.

## Out of scope

- Docker, PostgreSQL/Kafka runtime, Kubernetes apply or infrastructure rollout.
- Resetting, staging or rewriting unrelated user changes already present in
  `backend_delivery`.

## Risks and recovery

- Changes are limited to voucher/checkout surfaces and can be reverted by the
  individual patch commits/diffs; no migration or destructive data operation is
  planned.
- Existing dirty Delivery/matching/shipper files are not to be altered except
  where a voucher-total invariant requires a minimal, line-scoped change.

## Progress

- [x] Inspect current dirty diffs and affected voucher tests. Preserved the
  pre-existing Delivery/matching/shipper changes in `backend_delivery`.
- [x] Implement backend reservation, gate, pricing and contract fixes.
- [x] Implement Web/Flutter contract and refresh fixes.
- [x] Add/run focused regression tests.
- [x] Run no-Docker validation and record unresolved risks.

## Delivered surfaces

- Promotion service now hardens reservation ownership and terminal-state
  transitions, gates stacking/bulk operations, quarantines malformed or
  unsupported vouchers, and emits deterministic attribution and pricing.
- Order service canonicalizes legacy versus stacking payloads, validates the
  positive-food payable invariant, preserves quote attribution, and compensates
  reservations with the authenticated principal.
- Gateway, Web and Flutter clients use the canonical `layerCode`, selection
  mode and selected-ID contracts; stale wallet selections are removed and
  voucher collection refreshes the checkout preview.
- Web test configuration separates Vitest from Playwright and ignores generated
  report assets; Flutter checkout tests provide a capability mock for both
  rollout states.

## Validation

- Backend Promotion focused Maven suite: passed (all selected tests; no
  Testcontainers/integration profiles).
- Backend Order voucher/checkout focused Maven suite: passed.
- Gateway `GatewayRouteSecurityTest`: passed (12 tests).
- Flutter voucher/checkout focused suite: 20 tests passed; `flutter analyze`
  reported no issues.
- Flutter builder contracts passed with
  `VOUCHER_CHECKOUT_ENABLED=true` and with
  `VOUCHER_STACKING_ENABLED=true`; checkout view tests also pass with the
  stacking flag enabled.
- Web `npm run verify`: lint, typecheck, 13 Vitest files/70 tests, action
  contracts, handbook snapshot and production build all passed.
- Web Playwright: 28/28 tests passed; E2E action coverage is 56/56 (100%).
- `git diff --check` passed for backend, Web, Flutter and shipper worktrees.
- Follow-up hardening: malformed wallet vouchers without `endTime` now fail
  closed; the Promotion focused suite still passes.

## Unresolved risks / intentionally unattempted

- Docker/Kubernetes and live PostgreSQL/Kafka/Redis rehearsal were intentionally
  not run per scope. Production rollout still needs environment-specific
  feature-flag/canary verification and infrastructure approval.
- The full Order test suite retains unrelated pre-existing failures around
  `OrderValidationService` context construction and
  `OrderRestaurantCircuitBreakerTest`; voucher/checkout focused tests pass.
