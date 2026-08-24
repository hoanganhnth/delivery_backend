# Execution Plan: Customer Web Ordering

Date: 2026-08-21

## Status

Active

## Outcome

`delivery_web` supports a customer storefront and COD ordering flow while
preserving the existing Admin and Shop Owner portals. Customers can browse
public restaurants and menus, maintain a one-restaurant cart, manage addresses,
preview server-calculated pricing, create an idempotent order, view order
history/detail and cancel before pickup.

## Context

- Web shell and existing portal routes: `delivery_web/src/App.tsx`.
- Canonical Gateway contracts: `backend_delivery/docs/http-api-inventory.md`
  and `docs/system/api/README.md`.
- Order quote/idempotency policy: `docs/product/features/order-lifecycle.md`.
- Customer reference implementation: `delivery_app/lib/features/cart/`,
  `delivery_app/lib/features/orders/`, `delivery_app/lib/features/restaurants/`
  and `delivery_app/lib/features/user_address/`.
- Auth registration dependency: `docs/plans/active/identity-principal-event-mtls-migration.md`.

## Scope

In scope:

- Public customer catalog/search/menu pages.
- USER registration/login/email verification and role guards.
- Local one-restaurant cart and address CRUD.
- COD checkout preview/create with quote and UUID idempotency key.
- Customer order history/detail/status polling/cancel.
- Customer-focused tests and contract documentation.

Out of scope:

- Browser realtime shipper map, rating, reorder, notifications.
- Voucher/flash-sale checkout and online payments.
- Direct service calls, internal headers or new backend APIs.

## Approach

1. Add customer API types/services and preserve the existing dependency-injected
   Web architecture.
2. Add customer routes under `/customer/*`; keep `/login`, `/restaurant/*`,
   and `/admin/*` compatible with the current portals.
3. Keep catalog public. Require `USER` only for address, checkout and orders.
4. Treat preview totals as server authority. Retain the exact create command and
   UUID idempotency key across safe retries and typed quote conflicts.
5. Use Browser Geolocation plus manual coordinate fallback for addresses; no
   third-party map/geocoder dependency in v1.

## Risks And Recovery

- Existing dirty Web changes are user-owned; preserve them and resolve overlap
  manually if a touched file has changed since this plan began.
- Registration is controlled by the backend admission/canary gate and email
  infrastructure. The Web must show a recoverable unavailable state when the
  backend returns `503`.
- Static Web rollback is the release recovery. Cart storage is versioned and
  invalid data is discarded without touching server orders.

## Progress

- [x] Record and run the Web baseline.
- [x] Add customer API/auth/cart/address/order foundations.
- [x] Add customer routes and pages.
- [x] Add focused tests and update action-contract documentation.
- [x] Run repository verification and record result.

## Decisions

- 2026-08-21: v1 is COD core with REST order status polling; realtime map and
  app parity extras are deferred.
- 2026-08-21: catalog is public; customer private screens are under
  `/customer/*`; existing Admin/Shop Owner routes remain in place.
- 2026-08-21: registration is supported through the canonical Auth → User
  two-request handoff and must not persist provisioning secrets.

## Validation

- Focused Vitest/Testing Library tests for customer API parsing, cart invariants,
  checkout quote/idempotency handling, and route guards.
- `npm run verify` in `delivery_web`.
- Gateway smoke with a USER account: catalog → cart → address → preview → COD
  create/replay → order detail/history/cancel.

## Result

Web implementation and repository validation are complete. Gateway smoke is
still pending because no backend Gateway/USER environment was running during
this task; move this file to `docs/plans/completed/` after that smoke passes.

Validation record:

- `npm run verify`: PASS — lint, typecheck, 12 Vitest files / 60 tests,
  action-contract verification and Vite production build.
- `git diff --check`: PASS.
- Local Browser smoke: PASS for storefront/error-retry state, customer login,
  registration, email-verification empty-token state and anonymous checkout
  redirect; browser tabs were closed after validation.
- Known non-blocking build warning: Browserslist data is stale and the main
  bundle is above Vite's 500 kB advisory threshold.
