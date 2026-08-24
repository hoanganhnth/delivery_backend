# Execution Plan: Checkout Quote And Create-Order Idempotency

Date: 2026-08-15

## Status

Completed

## Outcome

COD checkout only creates an order after the customer has accepted a valid
server-issued quote, and transport retries with the same idempotency key return
the original order without another order/outbox event.

## Scope

In scope:

- `backend_delivery/order-service`: quote and idempotency persistence, public
  contract, typed conflicts, rollout flag and cleanup.
- `delivery_app`: quote-aware submission, retry identity and price-change UX.
- Cross-repository API/workflow documentation and focused proof.

Out of scope:

- Voucher/flash-sale checkout, Kafka event shape changes, payment and other
  client applications.

## Decisions

- Quote validity is 5 minutes; quote and completed idempotency receipts are
  retained for 24 hours.
- Quotes bind price-affecting checkout input. The idempotency fingerprint binds
  the full effective create command and the quote ID.
- `PRICE_CHANGED` is HTTP 409 with a fresh quote under `error.details.quote`.
- A price change or expired quote requires explicit customer confirmation and a
  new idempotency key.
- Backend ships compatibility mode first; enforcement becomes mandatory through
  a centrally configured flag after clients migrate.

## Progress

- [x] Backend migration, contract and services.
- [x] Flutter submit/retry and price-change confirmation.
- [x] Documentation and generated contract.
- [x] Focused backend and Flutter proof; PostgreSQL concurrency was attempted
  and is deferred to a Docker-enabled release environment.

## Risks And Recovery

- Existing Order identity migration is already in the worktree. New records use
  the stable principal ID and must not revert its compatibility behavior.
- If enforcement causes legacy checkout failures, turn the enforcement flag off;
  persisted quotes/receipts remain backward-compatible data.

## Validation

- Order unit/migration/controller tests plus an attempted PostgreSQL concurrent
  claim proof.
- Flutter DTO, network and checkout ViewModel/widget tests.
- HTTP contract generation/check after public DTO/controller changes.

## Result

- Backend `mvn test` passed with 129 tests, including quote, idempotency,
  controller, migration and Saga coverage. The generated HTTP contract check,
  Compose config verification and Kubernetes manifest verification also passed.
- Flutter `fvm dart analyze` and the full `fvm flutter test` suite passed; the
  final full run completed with 252 tests.
- The PostgreSQL concurrency test was executed but skipped because this
  environment has no Docker runtime/socket. The H2 repository claim test and
  service-level idempotency tests passed; PostgreSQL concurrency remains a
  release-environment validation item.
- The client keeps the idempotency key in the in-memory `CheckoutViewModel`.
  A process kill/restart immediately after a timeout can lose that key; durable
  key storage or reconciliation is intentionally deferred until product policy
  defines its lifecycle and privacy requirements.
