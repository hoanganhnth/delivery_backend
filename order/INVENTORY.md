# Order tranche inventory (2026-10-05)

Scope: first equivalence slice on `refactor/order-core`. References below are
pre-extraction host line numbers. Authority: `docs/platform/product/features/order-lifecycle.md`,
`docs/workflows/order_lifecycle_flow.md`, consolidation plan, and executable host behavior.
No plan, topic, schema, flag or business-policy changes are authorized.

| Workflow / entrypoint (under order-service/src/main/java/com/delivery/order_service/) | Decisions and guarantees before extraction |
| --- | --- |
| Preview: controller/OrderController.java:54; service/CheckoutPreviewService.java:107 | USER admission; canonical Restaurant/menu facts, livestream/flash/voucher gating and incompatibilities, duplicates, prices, shipping and ETA. Read/remote calls; CheckoutQuoteIssuer persists a principal/input/pricing-bound quote with expiry. |
| Create: controller/OrderController.java:67; service/impl/OrderServiceImpl.java:149,165,216,277 | Customer admission before bounded concurrency permit; scoped idempotency lease before preflight, exact completed replay read with ownership; quote owner/input/expiry/reprice, canonical Restaurant/menu validation in OrderValidationService.java:76; no client monetary authority. Preflight precedes TransactionTemplate write. Receipt re-claim, zero-price shell flush, inventory/flash reserves, subtotal/shipping/vouchers, item/money snapshots, inventory commit, quote lock/consume, receipt completion and order.created outbox. Remote reservation failure releases identical IDs; TTL is recovery fence. Optional rails/default flags remain host configuration. |
| Quote: service/CheckoutQuoteService.java:47,83 | Read/reprice without write lock across HTTP; changed price issues replacement quote and PRICE_CHANGED; final consume pessimistically locks owner/expiry/unused quote inside create transaction. |
| Create retry: service/OrderCreateIdempotencyService.java:43,74,99,124,140 | Unique principal+key, SHA-256 effective command, H2/Postgres atomic insert, processing token/lease expiry and in-progress conflict; final claim requires current live owner. Completed receipt binds order; failure releases only owned incomplete lease. |
| Saga status: listener/SagaCommandListener.java:57; service/SagaOrderCommandProcessor.java:36,52,68,105 | JSON/UUID/order identity admission, exact sagaStatus routing (SHIPPER_FOUND maps WAIT_SHIPPER_CONFIRM); raw-payload SHA-256 inbox claim BEFORE pessimistic order lock. Exact event replay returns false without order mutation; contradictory identity/payload IllegalArgumentException. Nonpositive sequence accepted only at cursor 0; <=cursor sequenced command commits receipt without mutation; next sequence updates cursor before status handler; gap throws retryable host exception and rolls back receipt/cursor/state. ACK after transactional processor returns; poison IllegalArgumentException excluded from retry, other errors wrapped. |
| Delivery progression: service/impl/OrderEventServiceImpl.java:51; entity/OrderStatus.java:36 | Locked order, simulation-context equality before status mapping; strict canonical state table, same status no save/notes/timestamp; PENDING→CONFIRMED→FINDING_SHIPPER bridges cross-topic confirmation race; finding clears shipper. Host saves/timestamps/notes, no new event here. |
| Assignment / no shipper: service/impl/OrderEventServiceImpl.java:228; service/impl/OrderServiceImpl.java:854 | Assignment validates context and positive shipper; ASSIGNED/PICKED_UP/DELIVERING/DELIVERED exact shipper replay no-op, other shipper conflicts. No-shipper mutates only CONFIRMED/FINDING_SHIPPER/WAIT_SHIPPER_CONFIRM, otherwise no-op; overwrite retry note, retain distinct terminal state, enqueue refund-eligible in same transaction. Host wraps all no-shipper failures in IllegalStateException. |
| Restaurant decision: listener/RestaurantEventListener.java:44,71; service/impl/OrderEventServiceImpl.java:149,185 | Lock order before decision receipt; validate event/actor/restaurant, serialized-event fingerprint, unique event/order, exact replay no-op and contradictory replay fail-closed. Late confirmation records receipt without regressing advanced state; reject requires PENDING, publishes cancellation snapshot/outbox. ACK follows transaction return. |
| Payment compatibility: listener/PaymentEventListener.java:34,60; service/impl/OrderEventServiceImpl.java:102,121 | Locked order; COD completion does nothing/failure ignored; non-COD pending completion confirms, failure transitions CANCELLED and enqueues typed system cancellation. Not active online checkout; preserve compatibility rail. |
| Cancellation/refund boundary: controller/OrderController.java:174; service/impl/OrderServiceImpl.java:748,790,821,834; service/OrderEventPublisher.java:75,98 | Pessimistic order lock, principal ownership or default-off enforcement legacy fallback; ADMIN bypasses eligibility guard but still must pass transition table. Customer/owner pre-pickup only. Exact cancelled actor+reason replay no save/event; conflicting replay fails. Persist cancellation and typed source/reason immutable money/reservation snapshot in mandatory transactional outbox. No provider refund here; Settlement owns downstream gated eligibility/provider decisions. |
| Outbox: service/OrderOutboxService.java:32,39; service/OrderOutboxRelay.java:112,145; service/OrderOutboxLeaseService.java:26,46,61 | MANDATORY enqueue with stable event ID/trace/correlation; short row-lock lease claim, Kafka outside DB transaction, token-fenced SENT/failure update, expiry reclaim, bounded retries/DEAD. Publish-before-mark can duplicate: at-least-once, consumers must use receipts. Payload rehydration preserves contract type headers. |
| Reads: controller/OrderController.java:103,113,124,151,163; service/impl/OrderServiceImpl.java:594,615,631,669,680,710 | Read-only paged queries; ADMIN unrestricted detail/global status/all; USER own principal; restaurant owner creator principal; unmigrated legacy fallback only while enforcement off; SHIPPER detail compares legacy userId to stored shipperId. No external identity hot-path lookup. Restaurant list service helper itself has no authorization (not public controller route). |
| Internal eligibility: controller/InternalOrderController.java:29,51 | Shared Internal-Token fails closed; rating requires legacy user+restaurant+DELIVERED; restaurant decision requires restaurant+PENDING under pessimistic lock, released when request transaction ends. |

## Ordered remaining slices

1. Complete Saga status application orchestration through transaction/store/receipt ports; preserve receipt-before-order locking, stale receipt commit, gap rollback and ACK proof.
2. Extract restaurant/payment lifecycle use cases and restaurant receipt decisions with cross-topic convergence/replay integration proof.
3. Extract ownership/read and cancellation/refund-intent policies/use cases, preserving principal fallback and exception precedence.
4. Extract canonical checkout admission/pricing, preview, shipping/ETA and quote policies through fact/client ports.
5. Extract create/quote/idempotency/reservation orchestration and compensation/lease recovery with DB race and remote ambiguous-failure proof.
6. Extract outbox lease/retry application orchestration; relocate adapters/composition to infrastructure and entrypoint/config to boot; remove host only after packaged HTTP/Kafka/Postgres recovery proof and inventories/packaging updates (outside this slice).

## Observed pre-existing discrepancies (preserved)

- Cancellation comment promises ADMIN any-state cancellation, but the shared transition table rejects PICKED_UP, DELIVERING and terminal states. Do not change either contract in this extraction.
- SHIPPER read ownership compares authenticated legacy user ID with shipperId, while product overview names shipper.id canonical. Identity migration needs separate authority/proof.
- No-shipper handler does not validate simulation context or set updatedAt; refund snapshot uses existing updatedAt if present. Delivery/assignment handlers do validate context. Preserved, not repaired here.
- The consolidation plan records an earlier Docker-enabled SagaOrderKafkaPostgresIntegrationTest baseline failure (missing retryKafkaTemplate); current execution result must distinguish available Docker proof from skipped classes.

## Slice 1 implementation and equivalence evidence

- `order/domain` inherits the build-parent JaCoCo check with 85% LINE and BRANCH
  minima; root reactor lists it before the unchanged `order-service` host and
  host directly depends on `order-domain:1.0.0-SNAPSHOT`.
- Domain owns canonical enum/aliases/transition table, delivery status mapping,
  confirmation bridge, assignment/no-shipper decisions, sequence admission and
  command admission/exact receipt replay. No framework/host/contracts dependency.
  No application-api/application module is created: transaction/store/receipt
  orchestration remains a future coherent use-case extraction, not an empty layer.
- Host enum remains a persistence/DTO mapping facade. Invalid external enum text
  is translated to preserve the original fully qualified host enum error message.
  Sequence GAP translates back to the original SagaOrderSequenceGapException.
- Receipt lookup/insert/fingerprint, pessimistic query, transaction annotations,
  cursor assignment before handler, timestamps, notes, shipper writes, save calls,
  refund/cancellation events, outbox enqueue, ACK/retry routing, schema, wire types,
  messages and feature flags remain host-owned and unchanged.
- Exhaustive domain tests cover all 100 source/target state pairs, self/null,
  terminal states, canonical/legacy aliases, case-sensitive delivery mappings,
  bridge/no-op paths, all assignment/replay/no-shipper states, every receipt
  identity field/validation precedence, and sequence legacy/stale/next/gap bounds.
- Added H2 integration proof exercises actual proxied processor/domain/JPA:
  receipt+cursor+bridge commit, stale receipt commit without effects, gap and
  invalid-transition rollback, identical gap retry after predecessor, and legacy
  acceptance before fencing/rejection after fencing. Original listener, event,
  receipt, converter and PostgreSQL concurrency assertions are retained.

## Validation result / parent handoff

Branch `refactor/order-core` already existed; HEAD and main both pointed to
`3bcf2c7e7f6407a222e1cc820a6819a8f88e57cd`. No git write commands or docs/plans edits.

- `mvn -B -pl :order-domain -am clean verify`: exit 0; 8 tests,
  zero failures/errors/skips. Domain JaCoCo: LINE 80/80 (100%), BRANCH 99/99 (100%);
  both 85% gates passed. Log: `/tmp/order-domain-verify.log`.
- Required `mvn -B -pl :order-service -am clean verify`, with sandbox escalation:
  exit 1; Order host 161 tests, zero failures, **one error**, zero skips.
  Sole error: existing `SagaOrderKafkaPostgresIntegrationTest` cannot inject
  `KafkaTemplate<String,String>` qualified `retryKafkaTemplate`. This reproduces
  the baseline defect recorded in consolidation plan lines 137–142. Prometheus
  and both PostgreSQL concurrency classes passed. Log:
  `/tmp/order-reactor-verify-unsandboxed.log`.
- First sandboxed invocation hit local-server `Operation not permitted` and
  skipped `OrderCreateIdempotencyPostgresConcurrencyTest`,
  `SagaOrderKafkaPostgresIntegrationTest`, and
  `SagaOrderCommandPostgresConcurrencyTest` (one test each). Escalated invocation
  resolved the environment restriction and ran all three: **no Docker-only skips
  in the definitive full run**. Log: `/tmp/order-reactor-verify.log`.
- Focused `mvn -B -pl :order-service -am verify
  -Dtest=OrderStatusTest,SagaStatusPolicyTest,SagaCommandIdentityTest,SagaCommandReceiptServiceTest,SagaCommandReceiptTransactionIntegrationTest,SagaCommandListenerTest,OrderEventServiceTest,SagaOrderCommandPostgresConcurrencyTest
  -Dsurefire.failIfNoSpecifiedTests=false`: exit 0; domain 8 + host 43 tests,
  zero failures/errors/skips; host packaged successfully. Log:
  `/tmp/order-focused-verify.log`. This is focused equivalence evidence, not a
  substitute for the failing full acceptance check.
- Final transaction test clarifies that a gap retries the identical status/event
  after its predecessor; final focused non-Docker verification is recorded in
  `/tmp/order-focused-final-verify.log`: exit 0, domain 8 + host 42 tests,
  zero failures/errors/skips; both coverage checks and host packaging passed.
- `git diff --check` passes; source inspection finds no Spring, Jakarta, Jackson
  or Lombok imports in domain production code.

**NEEDS_CONTEXT:** implementation and focused equivalence proof are complete,
but full zero-error acceptance cannot be met while preserving known defects.
Parent must authorize a separate repair of Kafka test/composition wiring, or
handle the baseline acceptance decision. This task does not fix that defect.
