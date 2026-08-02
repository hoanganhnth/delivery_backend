# Refund and cancellation compensation runbook

## Scope

This runbook covers the conservative MVP boundary inspired by GrabFood and
ShopeeFood. It creates a durable refund case and releases checkout reservations;
it does not call a payment provider, mutate the ledger, or approve a case.

All three flags remain `false` by default:

```text
REFUND_PROCESSING_ENABLED=false
REFUND_OUTBOX_RELAY_ENABLED=false
REFUND_PROVIDER_PROCESSING_ENABLED=false
```

## Trigger matrix

| Trigger | Order/Delivery state | COD | Online while provider is off |
|---|---|---|---|
| Customer cancellation before restaurant preparation (`PENDING`) | `CANCELLED` | `NO_REFUND_REQUIRED` | `MANUAL_REVIEW` |
| Restaurant rejection/system payment failure before pickup | `CANCELLED` | `NO_REFUND_REQUIRED` | `MANUAL_REVIEW` |
| No eligible shipper after matching retries | `SHIPPER_NOT_FOUND` | `NO_REFUND_REQUIRED` | `MANUAL_REVIEW` |
| Customer/admin exception after preparation or pickup | unchanged cancellation state | `MANUAL_REVIEW` if money was captured | `MANUAL_REVIEW` |

The no-shipper path emits `order.refund-eligible`; it must not rewrite
`SHIPPER_NOT_FOUND` to `CANCELLED`. The event carries the immutable monetary
snapshot and reservation IDs. Promotion and Flash-sale release the reservation
idempotently from either compensation topic.

## Enablement rehearsal (staging/operator only)

1. Apply Flyway V4 in Settlement and confirm the `refund_cases` and
   `refund_outbox_events` constraints are present.
2. Confirm Gateway exposes only the two `GET` admin refund paths and the service
   requires `ADMIN`; there is no POST/PUT/DELETE mutation route.
3. Start Settlement with `REFUND_PROCESSING_ENABLED=true` only after the
   `order.cancelled` and `order.refund-eligible` consumers have been deployed.
4. Observe case creation and reservation release. Keep
   `REFUND_OUTBOX_RELAY_ENABLED=false` until an approved provider worker exists.
5. Provider activation is a separate T7 change and requires provider,
   signature, callback, reconciliation, secret-store and rollback authority.

## Read-only checks

Use the Gateway admin reads to inspect the bounded queue:

```text
GET /api/settlement/admin/refunds?status=MANUAL_REVIEW&limit=100
GET /api/settlement/admin/refunds/{refundId}
```

For database reconciliation, compare the event/order identity and immutable
components (`subtotal_amount`, `discount_amount`, `shipping_fee`,
`total_amount`, `captured_amount`, `refund_amount`) with the retained Order
snapshot. Review `payload_fingerprint`, `event_id`, trigger, actor source,
reason code, outbox status and attempts. Do not repair by editing these rows.

## Rollback and recovery

- Stop new triggers by setting `REFUND_PROCESSING_ENABLED=false`.
- Keep existing `refund_cases` and pending/dead outbox rows for investigation;
  do not delete rows or alter immutable monetary fields.
- Reservation release remains idempotent and may be replayed with the same event
  identity after a consumer restart.
- `MANUAL_REVIEW`, amount mismatch, unknown provider status and exhausted retry
  require an operator decision. No blind provider retry is allowed.

This document is an operational contract, not proof that a provider refund has
occurred. Provider and real-environment proof remain T7/T8 work.
