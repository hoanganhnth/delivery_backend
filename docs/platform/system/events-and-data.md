# Events, Data Ownership and Recovery

> Status: current as-built data/event model, checked 2026-08-21. The exact
> producer/consumer and state-machine record is
> [`../../backend_delivery/docs/system-contract-inventory.md`](../../system-contract-inventory.md).

## Data ownership

Each service owns the schema it writes. In local Compose, databases share one
PostgreSQL server for developer convenience; this is not permission for a
service to query another database.

| Database/store | Owner | Durable responsibility | Recovery role |
| --- | --- | --- | --- |
| `auth_db` | Auth | Accounts, password/security tokens, sessions/refresh families, identity/profile link | Restore with user identity linkage in a consistent recovery plan |
| `user_db` | User | Profiles, addresses and block-state projection | Durable identity/product support state |
| `restaurant_db` | Restaurant | Restaurants, menus, decisions, rating state and outbox | Current catalog/decision authority |
| `order_db` | Order | Orders, immutable monetary/location snapshot, decision receipts and outbox | Critical lifecycle state; recover consistently with Delivery/Settlement/Saga |
| `delivery_db` | Delivery | Delivery state, offers, assignment, batch snapshots, POD metadata, delivery exceptions and outbox | Critical fulfillment/evidence state; private proof objects live in explicitly configured object storage |
| `match_db` | Match | Find command receipts/fingerprints, staged candidates/results and result outbox | Critical coordination/replay state; restore with Saga/Delivery lifecycle data |
| `saga_db` | Saga | Workflow transitions, inbound receipts, early-event staging and command outbox | Critical coordination/replay state; restore together with lifecycle data |
| `settlement_db` | Settlement | COD receipts, immutable ledger and balances | Critical financial state; never “repair” by deleting receipts |
| `shipper_db` | Shipper | Shipper profiles/fleet state | Durable operational data |
| `notification_service_db` | Notification | Inbox and durable event deduplication | Durable communication state |
| `tracking_db` | Tracking | Asynchronous sampled support location history | Support data only; not current location truth |
| `promotion_db` | Promotion | Voucher wallet/reservations/outbox | Gated capability data |
| `flashsale_db` | Flash Sale | Campaigns, reservation lines/outbox | Gated capability data |
| `analytics_db` | Analytics | Experimental projection/reconciliation state | Rebuildable/experimental; default processing is off |
| `livestream_db` | Livestream | Experimental livestream metadata | Experimental/disabled capability |
| Elasticsearch | Search | Restaurant/dish search documents/projection checkpoints | Rebuild from durable producer/event source; not independent business truth |
| Redis | Gateway/Restaurant/Match/Tracking/Notification | Cache, counters, GEO, presence/freshness, leases, rooms and Pub/Sub | Volatile; flush/rebuild/reconnect, never restore stale live offers or locations |

`search_db` remains a legacy/unused init entry and is not a protected business
store. Match now owns `match_db` for command/result replay state; its Flyway
migration and backup inventory are part of the same deployment boundary.

## Relational schema and migrations

JPA services use Flyway migrations under each service's
`src/main/resources/db/migration/`. A clean-room rebuild must treat migrations
as schema authority rather than deriving tables from entities alone. In
particular, durable outbox/receipt/unique-key tables encode replay correctness;
omitting them may yield a system that works once but corrupts on retry.

Deployment order for a migration:

1. Make a backward-compatible schema change.
2. Deploy code that tolerates both old/new shape.
3. Backfill or enable the new behavior only when proven safe.
4. Remove old shape only in a later, explicit recovery-tested release.

Never deploy code that requires a migration before that migration has safely
completed. See [operations/release-and-recovery.md](./operations/release-and-recovery.md).

## Event transport rules

Kafka gives at-least-once delivery. The platform obtains safe effects through:

1. A producer writes its aggregate change and a stable outbox row in one local
   transaction.
2. The relay publishes after commit with stable event identity and an aggregate
   ordering key.
3. The consumer persists business change plus a receipt/dedup key/fingerprint in
   its own transaction.
4. The listener ACKs only after commit; temporary errors retry and then use a
   same-partition DLT route.
5. An exact replay is a no-op. A changed payload with the same identity is a
   poison/conflict record, not an opportunity to overwrite durable truth.

Saga scheduler observations follow the same rule. A timeout is a typed internal
command with a deterministic `eventId`, expected status/version, observed
`updatedAt` and an absolute deadline. The manager re-locks the Saga and treats a
stale, early or already-claimed observation as an acknowledged no-op. It does
not manufacture an anonymous event just to enter the inbox.

New or frozen contracts should use an explicit envelope:

```json
{
  "eventId": "UUID",
  "eventType": "delivery.completed",
  "eventVersion": 1,
  "occurredAt": "2026-08-08T00:00:00Z",
  "aggregateType": "delivery",
  "aggregateId": "123",
  "correlationId": "opaque-request-id",
  "causationId": "optional-parent-event-id",
  "payload": {}
}
```

Not every legacy event is fully normalized to that envelope yet; the current
inventory records a mix of typed DTO, JSON/string and legacy forms. A rebuild
should standardize only with a compatibility/versioning plan rather than silently
change active consumer payloads.

## Active topic map

| Topic family | Producer | Consumer(s) | Core rule |
| --- | --- | --- | --- |
| `order.created` | Order | Saga, Notification; gated Promotion/Flash Sale | Immutable money/reservation snapshot launches workflow and projections; Saga drains any staged early facts before `create-delivery` |
| `order.cancelled` | Order | Saga; gated Promotion/Flash Sale/Settlement refund boundary | Typed actor/reason plus snapshot; if Saga is not created yet, Saga durably stages the fact and promotes it later |
| `order.refund-eligible` | Order | Gated compensation consumers | `SHIPPER_NOT_FOUND` stays distinct from cancellation |
| `restaurant.order-confirmed` / `.rejected` | Restaurant | Order, Saga | Decision/outbox fingerprint prevents contradictory replay; Saga stages only an early `.confirmed` fact until its aggregate exists |
| `saga.command.create-delivery` | Saga | Delivery | Stable command validates full COD/location/price facts |
| `delivery.created.result` / `.failed` | Delivery | Saga | Workflow convergence; no invented delivery fee fallback |
| `saga.command.find-shipper` | Saga | Match | Canonical COD/location payload plus Saga-owned absolute `matchingDeadlineAt`; rematches keep the original cutoff |
| `shipper.found` / `.not-found` | Match | Saga | Exactly one candidate outcome or explicit deterministic terminal outcome; deadline expiry releases a late reservation before `.not-found` |
| `matching.decision-trace` | Match | Simulator Service (read-only observer) | Versioned, best-effort explanation of the active `nearest-cod-v1` path (`GEO_QUERY → COD_ELIGIBILITY → RESERVE → OUTCOME`); published only after the durable business result, keyed by `orderId`; never participates in reservation, assignment, Saga convergence or retries |
| offer/terminal commands | Saga | Delivery/Match/Order | Cache offer, expiry, cancel/stop matching, mark no-shipper, update order state |
| `delivery.shipper-offered` | Delivery | Notification | Offer persisted before durable inbox/FCM wake-up |
| `delivery.shipper-accepted` / `.rejected` | Delivery | Saga | Advance/re-match only through canonical state machine |
| `delivery.status-updated` | Delivery | Saga, Notification | Shipper lifecycle, no-shipper terminal notification and durable `CANCELLED` confirmation for Saga compensation |
| `delivery.cancel.failed` | Delivery | Saga | Correlated refusal is recorded as a visible failed compensation, never silently discarded |
| `delivery.completed` | Delivery | Settlement | COD completion only; receipt/ledger is idempotent |
| `delivery.exception.reported` | Delivery | Settlement review bridge, operations/audit | Dedicated post-pickup exception stream; immutable money snapshot; only `DELIVERY_EXCEPTION_REPORTED/RETRY_AVAILABLE` creates a manual-review case; legacy `delivery.status-updated` does not carry RETURNING/RETURNED |
| `shipper.status-change` | Delivery | Match, Tracking | Availability/room projection with stale-event fence |
| `delivery.batch.accepted` | Delivery | Settlement | Batch COD holds commit intent; additive and feature-gated |
| `delivery.batch.released` | Delivery | Settlement, Match | Atomic hold release plus old Match-generation retirement |
| `delivery.batch.completed` | Delivery | Match | Retire completed pool generation; no financial transition |
| `shipper.location-updated` | Tracking | Match, tracking history | Live location plus sampled durable history; offline is a tombstone |
| `entity-sync` | Restaurant | Search | Search projection is rebuildable and accepts only valid ordering/fingerprint semantics |
| reservation event families | Promotion/Flash Sale | Operations/audit when relay enabled | Capability is gated; do not infer active checkout from schema/events |

Removed topics such as `delivery.find-shipper`, `delivery.picked-up`,
`delivery.cancelled`, `shipper.matched` and `no.shipper.available` are not
compatibility contracts to recreate.

## Saga ordering and recovery fences

The order lifecycle has two independent Kafka orderings, so topic arrival order
cannot be used as an aggregate lock. The Saga owns the following convergence
rules:

| Situation | Durable rule | Safe outcome |
| --- | --- | --- |
| `order.cancelled` or `restaurant.order-confirmed` before `order.created` | Store the validated fact in `saga_early_events`; promote it into `saga_inbound_receipts` after the Saga row exists | No lost cancellation/confirmation and no premature delivery creation |
| Scheduler sees a stuck Saga | Re-lock and compare status, optimistic version, observed timestamp and deadline before claiming the deterministic timeout receipt | Stale/early/duplicate timeout is a no-op |
| `delivery.created.result` after a terminal Saga | Persist the one Delivery identity and enqueue cancellation again | Late work cannot leave an orphan Delivery |
| Cancellation after a Delivery exists | Keep Saga `COMPENSATING` until `delivery.status-updated(CANCELLED)` is committed by Delivery | Compensation has a durable downstream acknowledgement |
| Delivery refuses cancellation | Consume `delivery.cancel.failed` with the original identity and mark the Saga `FAILED` | Drift is visible for reconciliation; no false terminal success |

Early staging is intentionally narrow. Match owns only command/result replay
rows in `match_db`; Redis reservations, GEO and cancellation projections remain
volatile and are rebuilt from fresh events.

`matching.decision-trace` is an observability contract, not a business state
store. Match writes it as a second outbox row after `shipper.found` or
`shipper.not-found` is durable. The simulator uses its own consumer group and
keeps the trace in the in-memory run snapshot/SSE stream. A missing or malformed
trace must therefore never cause Match to retry, release a valid offer, or
change the order/delivery result. The candidate list is explicitly the result
after Match's online/fresh/busy/offer GEO filters; it is not a raw Redis GEO
dump. A future algorithm may publish `SHADOW` traces on the same schema only
after a separate rollout/comparison policy is approved.

## Consumer receipt patterns

| Consumer boundary | Durable replay fence |
| --- | --- |
| Restaurant decision → Order | `restaurant_decision_receipts` plus payload fingerprint in Order transaction |
| Saga early fact → Saga | `saga_early_events` keyed by source `eventId`, then promotion into `saga_inbound_receipts` before state/outbox mutation |
| Saga inbound | `saga_inbound_receipts` with source topic/order/fingerprint before state/outbox mutation; timeout commands use deterministic identity plus state/version/deadline fence |
| Match Saga command | `match_commands` stores source identity/fingerprint; candidate staging and `match_outbox_events` commit the deterministic result exactly once |
| Saga command → Delivery | Unique command/create identity plus state lock and delivery outbox |
| Order event → Notification | Unique inbox/deduplication key; exact semantic payload repeat is no-op |
| Delivery completion → Settlement | Unique receipt/event/order identity and ledger business-key constraints |
| Tracking location history | Durable receipt/sampling identity; current Redis location has a separate freshness fence |

## Redis state is intentionally non-durable

| Redis use | Owner | Rebuild behavior |
| --- | --- | --- |
| Gateway rate-limit window | Gateway | Expire naturally; never share a user-ID key as a rate-limit identity |
| Restaurant cache | Restaurant | Refill from owned relational data |
| Match candidate GEO/reservations/tombstones | Match | Rebuild from fresh tracking/availability events; preserve cancellation semantics during normal operation |
| Tracking GEO, publisher lease, room fan-out | Tracking | Authenticated clients reconnect/publish a new generation; old state must not be restored |
| Notification FCM token ownership | Notification | Recover through registered device/token lifecycle; token handling remains idempotent |

## Backup and disaster recovery

Critical lifecycle/finance data (`order_db`, `delivery_db`, `match_db`,
`settlement_db` and related Saga/outboxes/receipts/early-event staging) must be restored to one consistent recovery
point. A mix of newer financial rows and older orders/deliveries is not valid.

The documented MVP target is daily encrypted logical backups with 14-day daily
and eight-week weekly retention. The stated production target adds physical base
backup/WAL PITR, encrypted object storage and a 35-day recovery-point window.
Those production facilities are requirements, not deployed cloud evidence.

Recovery sequence:

1. Stop ingress, writers, outbox relays and consumers.
2. Verify encrypted artifact and inner checksums.
3. Restore first into isolated databases and run migration/reconciliation.
4. Rebuild Elasticsearch; flush/recreate Redis instead of restoring live state.
5. Compare receipts/outboxes/consumer offsets before controlled Kafka replay.
6. Promote only after reconciliation, security approval and recorded RTO.

See the executable scripts and safeguards in
[the data backup/restore runbook](../../runbooks/data-backup-restore.md).

## Sources

- [System contract inventory — event matrix and state machines](../../system-contract-inventory.md)
- [Contract conventions ADR](../decisions/0001-backend-contract-conventions.md)
- [Backup and restore runbook](../../runbooks/data-backup-restore.md)
- [Resilience/DLT runbook](../../runbooks/resilience-operations.md)
