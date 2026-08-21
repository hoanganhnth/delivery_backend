# Flash-sale checkout and stock reservation

## Runtime status

The durable implementation is rollout-ready but disabled by default.

- `FLASHSALE_CHECKOUT_ENABLED=false`
- `FLASHSALE_OUTBOX_RELAY_ENABLED=false`
- `ORDER_FLASHSALE_CHECKOUT_ENABLED=false`
- `FLASHSALE_MERCHANT_REGISTRATION_ENABLED=false`
- Flutter `FLASHSALE_CHECKOUT_ENABLED=false`

Public active-campaign/item reads and admin campaign/approval routes remain
available. Gateway never routes internal quote/reserve/commit/release. Merchant
item registration remains hidden until its separate rollout is approved.

## Authority and price contract

- Campaigns are owned by `ADMIN`.
- A flash-sale item belongs to a canonical restaurant. Registration verifies
  the authenticated shop owner against `restaurantId`; the internal Restaurant
  ownership contract is principal-first and only checks `creator_id` when the
  row has no `owner_principal_id` yet. Activation requires admin approval.
- The client obtains `flashSaleItemId` only from the public approved catalog.
  It never submits a flash price.
- Order verifies the returned `flashSaleItemId`, menu item, quantity, and price,
  then snapshots that server price. Voucher and flash sale do not stack.

## Durable atomic lifecycle

PostgreSQL is the stock authority. Redis is not used for reservation truth.
Every requested item row is locked in sorted order; ownership, approval,
campaign window, and remaining stock for every line are validated before any
counter changes. The cart then updates counters, persists reservation lines,
and enqueues the deterministic outbox event in one transaction.

```text
RESERVED -> COMMITTED | RELEASED | EXPIRED
COMMITTED -> RELEASED   (compensation before fulfilment only)
```

Reservation identity is stable `reservationId + orderId`; `orderId` is unique.
Reservation records and emitted events also carry optional `userPrincipalId`
from Order while retaining legacy `userId` for compatibility.
Exact replay returns the stored line fingerprint and terminal state. A changed
line, quantity, restaurant, user, order, or reservation identity fails closed.
`order.created` commits; `order.cancelled` releases; a 15-minute sweep expires.
Each consumed Order event also claims a durable
`flash_sale_order_reservation_receipts` receipt carrying source topic,
`COMMIT`/`RELEASE` action, order/reservation identity and SHA-256 raw payload.
PostgreSQL atomically claims the receipt with the stock transition; an exact
replay ACKs as a no-op, while a reused event ID with a changed source, identity
or payload fails closed to the owner `.flashsale.DLT`. This hardens the disabled
capability only—the checkout and outbox-relay flags remain false.

```mermaid
sequenceDiagram
    participant A as Flutter
    participant O as Order
    participant F as Flash sale
    participant D as Flash-sale DB
    participant K as Kafka
    A->>F: GET approved campaign items
    A->>O: preview(menuItemId, flashSaleItemId, quantity)
    O->>F: internal quote (IDs + quantities only)
    F-->>O: canonical menu IDs and unit prices
    A->>O: create order with same selection
    O->>F: reserve(reservationId, orderId, restaurantId, lines)
    F->>D: sorted row locks; validate all; counters+reservation+outbox
    O->>O: immutable monetary snapshot + order.created outbox
    K->>F: order.created / order.cancelled
    F->>D: idempotent commit / release
```

## Proof

- PostgreSQL 16 last-stock race: exactly one checkout succeeds.
- Multi-item request with an exhausted second line changes no counter and
  creates no reservation/outbox row.
- Exact terminal replay, expiry, compensating release, outbox cardinality, and
  malformed-event no-ACK tests.
- Kafka + PostgreSQL 16 two Flash-sale replicas consume duplicate
  `order.created`, `order.cancelled` and `order.refund-eligible` identities on
  separate partitions; same/fresh-group replay converges to one receipt and the
  expected `COMMITTED` or `RELEASED` reservation/outbox transition, while changed
  reuse reaches the same-partition `.flashsale.DLT`. PostgreSQL concurrency
  additionally proves a failed local transition rolls its receipt back for Kafka
  replay.
- Flutter catalog contract rejects malformed or duplicate active selections and
  persists the authoritative `flashSaleItemId` through preview/create.
