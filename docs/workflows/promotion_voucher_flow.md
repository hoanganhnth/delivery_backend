# Promotion and voucher checkout

## Runtime status

The implementation is rollout-ready but disabled by default. Public wallet
collection/listing remains available; Order is the only checkout caller.

- `PROMOTION_CHECKOUT_ENABLED=false`
- `PROMOTION_OUTBOX_RELAY_ENABLED=false`
- `ORDER_VOUCHER_CHECKOUT_ENABLED=false`
- Flutter `VOUCHER_CHECKOUT_ENABLED=false`

Gateway routes `POST /api/promotions/collect/{code}` and
`GET /api/promotions/my-vouchers` for authenticated `USER`. It does not route
`calculate`, `reserve`, `commit`, or `release`.

## Authority and price contract

- Platform vouchers/freeship are created by `ADMIN`; shop vouchers are created
  by `SHOP_OWNER` and require `ADMIN` approval. Legacy `MERCHANT` ownership is
  not checkout-eligible.
- Scope is exactly `ALL` or `SHOP`. A `SHOP` voucher carries a positive
  canonical restaurant ID. Legacy `CATEGORY` scope is not checkout-eligible.
- One order may select at most one voucher per layer (`SHOP_DISCOUNT`,
  `PLATFORM_DISCOUNT`, `FREESHIP`), and voucher does not stack with a
  flash-sale line. Clients send `AUTO` or wallet IDs; Promotion computes the
  combination and Order snapshots subtotal, shipping, discount, and total.

## Durable lifecycle

```text
RESERVED -> COMMITTED | RELEASED | EXPIRED
COMMITTED -> RELEASED   (compensation before fulfilment only)
```

`voucher_reservations.reservation_id` is the stable request identity and
`user_principal_id` is the additive stable Auth identity for reservations;
`user_id` remains the legacy voucher-wallet compatibility key during migration.
`user_vouchers.user_principal_id` is also dual-written for new collection rows.
Wallet reads/locks are principal-first and include only a matching legacy row
whose principal is still null; a locked legacy row is lazily backfilled after
the request proves both principal and legacy profile identity. No cross-service
bulk backfill guesses principal IDs.
`order_id` is unique. Reserve locks both wallet and voucher rows, validates the
whole fingerprint, increments capacity once, marks the wallet `RESERVED`, and
writes the reservation plus deterministic outbox event in one transaction.
Exact replay returns the stored state; a changed fingerprint fails closed.

`order.created` commits the reservation. `order.cancelled`, including payment
failure and restaurant/customer cancellation, releases it. A 15-minute expiry
sweep releases an uncommitted hold. Consumers ACK only after the state
transition succeeds; repeated commit/release/expiry is a no-op.

Each consumed Order event also has a durable
`promotion_order_reservation_receipts` primary-key receipt carrying source
topic, `COMMIT`/`RELEASE` action, order/reservation identity and a SHA-256 of
the raw payload. PostgreSQL atomically claims it with the local transition;
exact replay is an ACK/no-op and a reused event ID with different source,
identity or payload fails closed to the owner `.promotion.DLT`. This hardens
the disabled capability only—none of the checkout or outbox flags are enabled.

```mermaid
sequenceDiagram
    participant A as Flutter
    participant G as Gateway
    participant O as Order
    participant P as Promotion
    participant D as Promotion DB
    participant K as Kafka
    A->>G: POST /orders/checkout-preview (voucherId)
    G->>O: trusted USER identity
    O->>P: internal calculate
    P-->>O: canonical discount
    O-->>A: server-owned price breakdown
    A->>G: POST /orders (voucherIds=[id])
    O->>P: reserve(reservationId, orderId, canonical money)
    P->>D: lock wallet+voucher; reservation+outbox
    O->>O: Order+items+order.created outbox
    K->>P: order.created / order.cancelled
    P->>D: idempotent commit / compensating release
```

## Proof

- PostgreSQL 16 simultaneous duplicate use: one reservation, one capacity
  increment, one `RESERVED` outbox event.
- Exact terminal replay, expiry, compensating `COMMITTED -> RELEASED`, and
  changed-payload rejection.
- Order timeout/later-failure release with the same stable identity.
- Preview/create totals and no-stacking contract tests.
- Kafka + PostgreSQL 16 two-replica `order.created`, `order.cancelled` and
  `order.refund-eligible` duplicate partitions, same/fresh-group replay and
  contradictory identity reuse: one receipt and the expected committed/released
  reservation/outbox transition; contradiction reaches same-partition
  `.promotion.DLT`.
