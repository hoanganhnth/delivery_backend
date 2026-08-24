# Feature: Voucher and flash-sale checkout

Status: implementation and focused proof complete; environment rollout disabled
by default.

| Concern | Voucher | Flash sale |
|---|---|---|
| Ownership | Platform vouchers/freeship are ADMIN-owned; shop vouchers are SHOP_OWNER-created and ADMIN-approved | ADMIN campaign; restaurant-owned item; admin approval |
| Client input | `AUTO` or up to three wallet `selectedVoucherIds`, one per layer | approved catalog `flashSaleItemId` + quantity |
| Price owner | Promotion | Flash-sale service |
| Durable hold | `voucher_reservations` (legacy) or `promotion_reservations` + lines (stacking) | reservation + line tables |
| Capacity authority | locked wallet/voucher rows | sorted PostgreSQL item row locks |
| TTL | 15 minutes | 15 minutes |
| Success | `order.created` commits | `order.created` commits |
| Compensation | `order.cancelled` releases | `order.cancelled` releases |

Voucher and flash sale do not stack. Voucher layers apply in the order shop,
platform, freeship; AUTO maximizes savings and MANUAL accepts at most one per
layer. Exact replay is a no-op; replay with a
different fingerprint fails closed. Flutter hides the wallet/catalog selection
until its compile-time flag is enabled and always displays the Order preview
breakdown. The admin/restaurant Web portal has no customer checkout surface and
does not expose internal reservation endpoints.

Operational procedure: see
`backend_delivery/docs/runbooks/voucher-flashsale-checkout.md`.
