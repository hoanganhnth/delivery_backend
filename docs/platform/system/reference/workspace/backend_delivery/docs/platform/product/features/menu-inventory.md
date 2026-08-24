# Feature: Menu inventory reservation

Status: backend code-gated, default-off (2026-08-23). Restaurant-service is the
authority for menu inventory; Order only carries the reservation identity and
never derives stock from a cache or client payload.

## Authority and state

- `menu_item_inventory` stores `on_hand_quantity`, `reserved_quantity` and a
  monotonic revision. A missing row, invalid ledger or non-`AVAILABLE` menu
  item fails closed; there is no unlimited-stock or backorder path.
- Checkout creates one UUID reservation per order. All lines are validated and
  locked in ascending menu-item order before any counter changes.
- `RESERVED` holds capacity for 15 minutes. `COMMITTED` consumes on-hand stock;
  `RELEASED` returns held capacity or compensates a committed sale;
  `EXPIRED` returns held capacity. Replaying a terminal transition is a no-op.

## HTTP boundaries

- Restaurant owner/admin manages stock with
  `PUT /api/menu-items/{menuItemId}/inventory` and an expected revision.
- Order calls the hidden internal reserve/commit/release endpoints with the
  shared internal credential. The Gateway does not route those internal paths.
- `order.created`, `order.cancelled` and `order.refund-eligible` carry the
  additive `inventoryReservationId`. Restaurant's event consumer claims a
  durable event receipt before applying commit/release.

## Rollout and proof

`RESTAURANT_INVENTORY_ENABLED` and `ORDER_INVENTORY_RESERVATION_ENABLED` are
`false` by default. Focused service, migration, client-contract and raw-event
tests pass. PostgreSQL concurrent reservation, Kafka replay/DLT, expiry
recovery, staging/provider and web/customer UX proof remain release gates.
