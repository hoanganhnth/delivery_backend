# ShopeeFood mobile preview

Scope: development-only `/preview/shopeefood/*`, customer screens, deterministic interactive fixtures, iPhone first. No production API/session/database changes.

## Reference ledger

- https://shopee.vn/m/dat-do-an-online-shopeefood — integrated Shopee entry/ordering reference. No complete current screen set verified.
- https://www.thegioididong.com/tin-tuc/cach-dat-do-an-freeship-1436012 — July 2022 standalone ShopeeFood home, restaurant, checkout and voucher screenshots. Supplemental only; image endpoint timed out on September 7, 2026.
- https://www.sapo.vn/blog/tra-cuu-don-hang-shopee-food — public order history/detail reference; not verified as current integrated Shopee iPhone UI.
- https://now-foody-corp.vn.aptoide.com/app — standalone archive reference from earlier inspection.
- Food photography: selected URLs from workspace `data/catalog/hanoi-catalog.json`, Grab public imagery. Menu labels/prices, distances, promotions and identities are illustrative fixtures.

All screens are reference-inspired reconstructions, not verified pixel-exact copies of the current integrated app. Auth, address forms and verification are inferred. Do not label unverified screenshots as exact references.

## Sequence

1. Isolated route, scoped tokens, fixed data and preview controls.
2. Home/search, restaurant/menu and item dialog.
3. Cart, three address contexts, checkout and duplicate-safe mock order.
4. Orders, mock lifecycle, authentication/verification, scenarios.
5. Focused state tests, typecheck/build/lint, mobile screenshots and short browser checks (no full E2E).

## Acceptance

390×844 primary; 375×812 and 430×932 secondary. No unintended horizontal overflow; sticky actions leave content accessible. Deep links and reset work without backend. Preview never reads/writes real cart or session. Home address stays separate from checkout override; next checkout follows Home. Existing dirty portal files preserved.

## Progress

- Baseline: existing restaurant portal lint failure; customer web typecheck/build previously passed.
- Implemented all ten screen entries plus item/voucher/address/cancel dialogs and mock order detail.
- Added normal/loading/empty/error catalog controls, order lifecycle control, reset, viewport selector and `?view=phone` clean display.
- September 7, 2026: 16 focused preview/customer tests pass; typecheck and production build pass. Production build omits preview chunks. Scoped ESLint passes; whole-repo lint still fails on the pre-existing RestaurantOrders memoization issue (and two pre-existing warnings).
- Chromium and WebKit short browser checks: item → cart → checkout → address override → voucher → mock order → Home; home address preserved, no JS errors, no business API requests. Widths 375/390/430 have no horizontal overflow in the checked flow. Ten screen entries additionally rendered in WebKit at 390px.
- Inspected home, item modal, checkout, order detail and desktop modal screenshots. Latest artifacts: `/tmp/shopee-ready-home.png`, `/tmp/shopee-webkit-item.png`, `/tmp/shopee-webkit-checkout.png`, `/tmp/shopee-desktop-sheet.png`; these temporary files are not durable reference sources.
- Functional preview delivered for review. Pixel-exact reference comparison remains unverified: available public references do not establish a complete current embedded Shopee iPhone screen set. Keep this plan active for visual fidelity follow-up rather than claiming exact-clone acceptance.
- September 8, 2026: “Tôi” is now a row-based account menu. Personal data moved to `account/profile`; `account/settings` adds light/dark/system theme and a debug-preview switch with in-memory diagnostics. Nine preview tests pass; WebKit 390×844 verified no personal phone/email on the menu, profile navigation, dark theme and debug toggle. Build passes.

## Recovery

### Mock-order follow-up — September 7, 2026

Added six initial orders covering pending/preparing/driver pickup/delivering/delivered/cancelled. Order detail now has an SVG schematic, moving marker, illustrative ETA/driver, timeline, auto-play/pause/manual-next/replay controls. Timers stop on terminal state or unmount; no GPS/contact integration. Seven preview tests pass (including fake-timer lifecycle), typecheck and scoped ESLint pass. WebKit 390×844 verified four active/two historical orders and automatic completion without JS errors; inspected `/tmp/mock-order-tracking.png`.

Preview state resets on page reload. To remove the preview integration, revert only its four added lines in App.tsx and remove the newly introduced customer-preview module; do not touch existing portal edits. No database recovery needed.
