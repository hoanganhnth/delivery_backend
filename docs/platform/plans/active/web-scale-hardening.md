# Execution Plan: Web scale hardening

Date: 2026-08-22

## Status

Active

## Outcome

Harden `delivery_web` for higher storefront and admin/restaurant traffic while
preserving the current customer MVP and existing array API compatibility:

- split route code so public entry does not load every portal;
- add bounded in-memory read caching, in-flight deduplication and cancellation;
- make customer order status polling adaptive and single-flight;
- migrate large list surfaces to additive PageData endpoints and server filters;
- keep legacy array endpoints available during client migration.

## Context

- System boundary: `docs/product/overview.md`, `docs/ARCHITECTURE.md`.
- API conventions: `docs/system/api/README.md`,
  `backend_delivery/docs/http-api-inventory.md`.
- Existing customer implementation: `docs/plans/active/customer-web-ordering.md`.
- Web baseline: production JS 541.83 kB minified / 146.17 kB gzip; Vite warns
  above 500 kB. Current working tree contains uncommitted customer MVP changes;
  preserve them and do not reset or overwrite unrelated edits.

## Scope

In scope:

- `delivery_web` route chunking, shared read resource cache, cancellation,
  image loading hints and adaptive REST order polling.
- Additive backend/Gateway PageData routes using `/page`, bounded page/filter
  inputs and the existing `{items,page,size,totalItems,totalPages,hasNext}`
  envelope.
- Web migration to server-side pagination/filtering and targeted invalidation.
- Focused tests, bundle measurement and repository verification.

Out of scope:

- SSR, CDN/RUM/vendor monitoring, new query dependency, WebSocket/SSE order
  status, online payment, voucher/flash-sale checkout activation or other hidden
  capabilities.
- Removing legacy array endpoints.

## Decisions

- Memory cache is per browser tab; no business data is persisted to storage.
- TTL is 30 seconds for public catalog/menu reads and 10 seconds for
  admin/restaurant lists. Orders, checkout preview and mutations use no stale
  cache, only in-flight deduplication.
- Cache invalidation is targeted by resource/query key.
- Public catalog/menu use load-more with accessible button fallback; admin and
  restaurant lists use previous/next pagination.
- Existing dirty Web changes must be checkpointed by the user before a release;
  this task does not reset, rebase or discard them.

## Progress

- [x] Record baseline and finish runtime foundation.
- [x] Add route-level code splitting and error fallback.
- [x] Add resource cache/cancellation and migrate safe public catalog reads.
- [x] Harden adaptive order polling and image loading.
- [/] Additive backend/Gateway pagination/filter routes: restaurant catalog,
  menu, available menu and ratings routes are implemented and gateway-allowed.
  Promotion/flash-sale PageData handlers remain deferred.
- [/] Migrate web list callers and update contract tests/docs: endpoint registry
  is extended, but callers still use legacy arrays until the remaining backend
  routes and UI pagination are available.
- [/] Run full validation and record bundle/result: typecheck, lint, build and
  backend compile pass; full web suite still contains six pre-existing MVP
  expectation failures and restaurant runtime tests require INTERNAL_SECRET.

## Risks And Recovery

- Legacy clients depend on array responses; additive routes remain available and
  old routes are not removed.
- Dirty Web changes overlap the planned files; preserve existing behavior and
  separate hardening changes into focused edits.
- If a new route fails, disable only the new web caller and fall back to the
  legacy endpoint; backend rollback is additive route removal after callers are
  reverted.

## Validation

- Web lint, typecheck, Vitest, action-contract verification and production build.
- Cache race/abort/TTL/invalidation tests, polling visibility/backoff tests,
  route guard/lazy chunk tests and pagination/filter UI tests.
- Backend focused controller/service/repository and Gateway route tests,
  contract generator check and cross-client compatibility scan.
- Bundle report must show no minified chunk above 500 kB and public eager JS at
  or below 325.10 kB minified / 87.70 kB gzip.

## Result

This execution is intentionally still active. Runtime hardening and the first
additive pagination slice are deployed in the working tree. The remaining
promotion/flash-sale pagination, server-side filter migration and compatibility
proof must land before this plan can move to `completed/`.
