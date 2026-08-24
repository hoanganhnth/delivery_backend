# 0004 Shopee-Style Voucher Stacking Policy

Date: 2026-08-22

## Status

Accepted

## Decision

- Checkout contains exactly one restaurant and supports at most three vouchers:
  one `SHOP_DISCOUNT`, one `PLATFORM_DISCOUNT`, and one `FREESHIP`.
- `SHOP_OWNER` creates shop-funded vouchers; `ADMIN` approves, pauses, resumes,
  or rejects them. `ADMIN` creates platform-funded item discounts and freeship.
- Discounts apply in the order shop, platform, freeship. Minimum-order value is
  checked against the pre-discount item subtotal. All money rounds to two
  decimals using `HALF_UP`.
- `AUTO` chooses the eligible combination with maximum customer savings; ties
  use earliest expiry and then lower stable voucher ID. `MANUAL` is validated
  server-side and accepts one voucher per layer.
- Voucher and flash-sale discounts do not stack. Checkout is COD-only.
- Shop discount reduces the restaurant commission base. Platform item discount
  and freeship are platform subsidies. Shipper earnings and commission retain
  gross shipping.
- A committed reservation is compensable only before `PICKED_UP`; cancellation
  or refund does not support partial-item allocation. Quotas are restored only
  when compensation is allowed.
- Legacy vouchers, reservations, and wallet rows are retained without automatic
  backfill or deletion. Stacking is canary-gated by stable principal allowlist.

## Consequences

The server owns selection, pricing, reservation, attribution, and settlement.
Clients receive a capability contract and a line-level breakdown; they never
submit trusted totals or bypass promotion reservation state.
