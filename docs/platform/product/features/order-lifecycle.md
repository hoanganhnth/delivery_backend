# Feature: Order lifecycle

Status: verified from Order, Delivery, Promotion, Flash-sale, and Settlement
code on 2026-08-21. Voucher/flash rollout flags remain off by default.

## Checkout authority

- `POST /api/orders/checkout-preview` and `POST /api/orders` are authenticated
  `USER` routes through Gateway.
- Restaurant/menu identity and regular price come from restaurant-service.
- Menu inventory authority also comes from restaurant-service. When the
  capability is enabled, Order reserves and commits a durable menu-inventory
  hold and carries `inventoryReservationId`; missing inventory or insufficient
  capacity fails closed and never backorders.
- Voucher discount comes from promotion-service.
- Flash-sale price and stock identity come from flashsale-service.
- The client submits selections, never monetary truth. Order snapshots
  `subtotalPrice`, `shippingFee`, `discountAmount`, `totalPrice`, item prices,
  and the applicable reservation identity.
- Stacking checkout allows at most one voucher per layer (`SHOP_DISCOUNT`,
  `PLATFORM_DISCOUNT`, `FREESHIP`); voucher and flash-sale items do not stack.

## Quote and create-order retry boundary

- A successful `POST /api/orders/checkout-preview` returns a server-issued
  `quoteId`, canonical totals, and `expiresAt`. The default quote TTL is five
  minutes.
- The customer submits that `quoteId` with a UUID `Idempotency-Key` to
  `POST /api/orders`. In compatibility mode the legacy request without both
  fields is still accepted; when `ORDER_QUOTE_ENFORCEMENT_ENABLED=true`, both
  fields are mandatory and either field alone is rejected.
- Quote validation is not a replacement for current-price validation. Order
  re-prices canonically at create time. An expired quote returns
  `409 QUOTE_EXPIRED`; a changed price returns `409 PRICE_CHANGED` plus a fresh
  quote in `error.details.quote` for an explicit customer confirmation.
- The idempotency receipt is scoped to the authenticated customer principal.
  Same key plus the same effective create command returns the original order;
  reuse of the key with different data returns `409 IDEMPOTENCY_KEY_REUSED`.
  An incomplete existing receipt fails closed with `409
  IDEMPOTENCY_IN_PROGRESS`; the client retries the same key instead of creating
  a second order. Quotes and completed receipts are retained for 24 hours to
  cover transport retries without treating an identical cart as a permanent
  duplicate.

## Create sequence

1. Claim the scoped idempotency receipt, returning an already-completed order
   for an exact transport retry.
2. Validate the quote owner/input/expiry and re-price against current canonical
   facts before accepting the customer's prior price confirmation.
3. Validate actor, request, restaurant, menu ownership/status, and regular price.
4. Persist/flush a zero-valued Order shell to obtain stable `orderId`.
5. Reserve flash stock, if selected, using a generated stable reservation ID.
6. Compute subtotal from regular or returned canonical flash prices.
7. Compute shipping from canonical pickup and submitted delivery coordinates.
8. Reserve the selected voucher combination, if any, atomically by layer and
   validate the returned line breakdown/total discount.
9. Consume the quote, complete the idempotency receipt, snapshot items and all
   monetary components, and enqueue `order.created` in the same transaction.
10. If any step after a remote reserve fails or times out ambiguously, call
   idempotent release with the same `reservationId + orderId`; TTL is the final
   recovery fence.

No service-price fallback or fake reserve success exists.

## Reservation and cancellation events

`order.created` and `order.cancelled` carry nullable
`voucherReservationId`/`flashSaleReservationId`. A valid first-rollout order has
at most one of them. `order.cancelled` also carries the immutable monetary
snapshot (`subtotalPrice`, `discountAmount`, `shippingFee`, `totalPrice`,
`paymentMethod`) so compensation consumers do not reconstruct money from a
mutable catalog. Promotion/Flash-sale commit on `order.created` and release
on `order.cancelled`. A terminal no-shipper outcome keeps the distinct
`SHIPPER_NOT_FOUND` order/delivery state and emits `order.refund-eligible` with
the same reservation and monetary snapshot; Promotion/Flash-sale release from
either compensation topic, while Settlement consumes both behind its
default-off boundary.

Both order events also carry an additive `items` snapshot from the persisted
`order_items` rows (`orderItemId`, `menuItemId`, `menuItemName`, `quantity`,
`unitPrice`, `lineTotal`, and optional `flashSaleItemId`). Analytics uses this
snapshot for its default-off per-item projection; it never reads mutable menu
prices to reconstruct historical sales. Consumers that do not need item
analytics may ignore the additive field.

Cancellation events are produced for:

- exact authorized customer/admin/restaurant cancellation;
- restaurant rejection;
- non-COD payment failure before fulfilment;
- system no-shipper exhaustion (`order.refund-eligible`), without rewriting the
  fulfilment state to `CANCELLED`.

Exact customer cancellation replay with the same actor/reason is a no-op;
conflicting replay is rejected. Cancellation events carry a typed
`cancelledBySource` and `cancelReasonCode`; event enqueue is transactional with
Order state.

## Monetary reconciliation

Delivery copies immutable Order `totalPrice` into `delivery.completed`.
Settlement fails closed unless:

```text
totalPrice = restaurantEarnings + restaurantCommission + shippingFee
shippingFee = shipperEarnings + shippingCommission
```

The COD deposit debit uses the same event `totalPrice`. Receipt, ledger entries,
and balance mutations remain one idempotent settlement transaction.

## Rollout status

Backend, outbox relays, and Flutter are separate switches. Keep all false until
the environment has migrations, matching Gateway routes, PostgreSQL race proof,
event replay proof, compensation proof, and Order-to-Settlement reconciliation.
See the voucher/flash-sale runbook before enabling or rolling back.
