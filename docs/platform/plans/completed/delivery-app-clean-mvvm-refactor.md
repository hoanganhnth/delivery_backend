# Execution Plan: Delivery App Clean MVVM Refactor

Date: 2026-08-09

## Status

Completed

## Outcome

Migrate the customer Flutter app to a feature/bounded-context architecture in
which ViewModels own typed user intents and business orchestration, views are
presentational, and the Amber Hearth design system is the single UI authority.
Keep the existing Gateway/API, token persistence, cart persistence and reachable
customer behavior compatible while migrating one vertical slice at a time.

## Context

- System workflow: `docs/WORKFLOW.md`
- Client architecture/testability authority:
  `docs/plans/completed/client-testability-refactor.md`
- Customer product/API boundary: `docs/ARCHITECTURE.md` and
  `docs/plans/completed/mvp-client-alignment.md`
- Current app: `delivery_app/`
- Baseline on 2026-08-09: analyzer PASS, 231 tests PASS, filtered handwritten
  coverage 30.17%.

## Scope

In scope:

- App composition/lifecycle/navigation, design system, MVVM/effect contracts,
  reachable customer features and test/architecture guardrails.
- Bounded-context reorganization: catalog, cart, checkout, addresses, orders,
  notifications, identity and settings.
- Removal of hidden support/dead/demo/duplicate runtime graph after reachability
  proof.

Out of scope:

- Backend, Gateway, Kafka, REST/WebSocket payload or product capability changes.
- Re-enabling support chat, IAP, livestream, promotion or flash-sale UI.
- Emulator/device as a required CI acceptance gate.

## Approach

1. Establish architecture verifier, MVVM effect queue, design-system foundations
   and characterization coverage.
2. Migrate Settings as the pilot, then app shell and identity/session.
3. Migrate catalog, cart/address/checkout, then orders/tracking/notifications.
4. Remove compatibility seams only after the last caller and focused proof are
   green; run repository-wide gates after every wave.

## Risks And Recovery

- Preserve the existing uncommitted Orders changes; characterize them before
  moving the feature.
- Keep old route/page/provider adapters during each slice so a route can be
  pointed back without duplicating state ownership.
- Preserve token/Hive keys and canonical Gateway paths; stop and open a system
  plan if a backend contract gap is discovered.

## Progress

- [x] Foundation guardrails and MVVM contracts.
- [x] Design system and Settings pilot.
- [x] App shell, auth/session and profile — shell, login, registration and
  profile all terminate in page adapters, typed ViewModels and pure views.
- [x] Catalog, cart, addresses and checkout — catalog home/search/index/detail,
  cart, confirmation, delivery-address list/form and checkout are migrated.
- [x] Orders, tracking and notifications — notification inbox, order list,
  detail, tracking and refund history are migrated. `SHIPPER_NOT_FOUND` stays
  distinct and the socket/map lifecycle proof is retained.
- [x] Hidden/dead cleanup and final gates — unreachable Support Chat and legacy
  stateful widgets are removed; Dart, test, generation, Android and iOS
  no-codesign gates pass.

## Execution Record

- 2026-08-09: Added the MVVM effect contract, migration architecture guard and
  Amber Hearth design-system foundations. Settings is the first fully migrated
  slice; login, registration, profile and main shell now use page adapters with
  typed intents/effects.
- 2026-08-09: Migrated the notification inbox into
  `application/ → presentation/pages/ → presentation/views/components/`.
  ViewModel owns durable inbox loads, refresh deduplication, read/read-all and
  delete operations, while the page owns push-wake listening and SnackBar
  effects. Notification REST wiring now lives in `features/notification/di/`;
  the previous presentation provider path is a temporary export for callers.
- 2026-08-09: Notification-focused tests, the full Flutter suite (240 tests),
  migration architecture guard and `flutter analyze` all pass after the
  notification slice.
- 2026-08-09: Migrated the Home tab into the new `catalog` bounded context.
  A catalog ViewModel maps featured restaurant data and emits navigation
  effects; the legacy Home page is now a compatibility route wrapper. The
  ViewModel currently adapts the restaurants notifier, to be replaced by a
  catalog repository port in the restaurant-detail/search slice. New pure-view
  and ViewModel tests pass; the pure-view test also fixed a phone-width section
  header overflow.
- 2026-08-09: Full Flutter suite passes again after the catalog-home slice
  (242 tests), along with `flutter analyze` and the migration guard.
- 2026-08-09: Migrated Search behind a catalog-owned search port and typed
  ViewModel. The adapter preserves the existing Gateway `/search/*` calls,
  both dish and restaurant result streams, 300ms debounce and navigation
  behavior; stale query responses are now ignored. Search/Catalog focused tests
  plus analyzer and the migration guard pass.
- 2026-08-09: Added the public `CartCommands` write port and moved restaurant
  menu mutation calls through it. Cart still owns its storage and
  one-restaurant invariant; the next Restaurant Detail ViewModel can consume
  this port without reaching into CartNotifier. Cart provider tests, analyzer
  and the migration guard pass.
- 2026-08-09: Full Flutter suite passes after the Catalog Search and CartCommands
  slices (245 tests). No Gateway, token or cart storage contract changed.
- 2026-08-09: Migrated Restaurant Detail into a catalog ViewModel family, page
  adapter and pure view/components. Menu quantity, flash-sale price mapping,
  cross-restaurant confirmation, cart navigation and errors now use typed
  intents/effects. Existing restaurant/cart feature tests and new ViewModel/UI
  tests pass; narrow phone-width cart CTA overflow was fixed in the process.
- 2026-08-09: Full Flutter suite passes after Restaurant Detail migration
  (248 tests). Migration guard and analyzer pass; no Gateway, token or cart
  storage contract changed.
- 2026-08-09: Migrated Cart screen to typed ViewModel/page/pure view. Existing
  Cart persistence, server price-sync and checkout navigation behavior are
  retained through the Cart command port and adapter; Cart regression suite,
  analyzer and migration guard pass. Address and Checkout remain.
- 2026-08-09: Migrated delivery-address list and add/edit form into typed
  ViewModels, page adapters and Riverpod-free views/components. List selection,
  default/delete confirmation, form validation, create/update/delete, location
  lookup, route transitions and toast feedback are now typed intents/effects.
  The legacy address-list notifier remains an application-only compatibility
  projection for the unmigrated Checkout/Cart selected-address consumers;
  successful form mutations reload that projection. Focused address VM/view/API
  tests, analyzer and the migration guard pass.
- 2026-08-09: Migrated Checkout to a single typed ViewModel, page adapter and
  pure view. The application layer now owns preview/order request construction,
  canonical preview validation, stale-input invalidation, voucher wallet,
  selected-address transitions and create-order/cart-clear orchestration.
  Placing an order remains disabled until the Gateway confirms current cart,
  address and voucher totals; unavailable server items are surfaced as a page
  effect. Focused Cart + Address tests (39), analyzer and the migration guard
  pass. Legacy preview/voucher widgets/providers remain only as compatibility
  seams until their final caller/test cleanup wave.
- 2026-08-09: Migrated Orders List behind a typed ViewModel/page/pure view.
  Filtering, retry/pagination, cancellation confirmation, reorder cart writes
  and route feedback are typed intents/effects; reorder request validation now
  lives in the Orders application layer. The existing `SHIPPER_NOT_FOUND`
  worktree modifications were left intact and pass the Orders regression suite.
  Order detail, periodic delivery refresh, authenticated shipper socket and map
  lifecycle are intentionally still legacy until their tested adapter is built.
- 2026-08-09: Migrated Order Detail and delivery tracking. The typed detail
  ViewModel owns refresh, refund-status retry, cancellation, reorder cart
  writes, restaurant-rating submission and navigation/dialog effects; the view
  and rating sheet are provider-free UI adapters. Tracking is now an
  auto-disposed family ViewModel whose disposal releases REST polling and the
  shipper socket lease without reading a widget ref during unmount. The legacy
  `SHIPPER_NOT_FOUND` behavior remains distinct. Orders regression (53 tests),
  analyzer and the migration architecture guard pass; the narrow-screen detail
  test also fixed payment/customer-card overflows.
- 2026-08-09: Migrated customer refund history, the complete Catalog restaurant
  index and the order-confirmation route to typed ViewModel/page/pure-view
  slices. The Catalog index keeps the existing restaurant-list provider as a
  temporary adapter but owns all loading, refresh and route intents.
- 2026-08-09: Replaced Splash's controller-owned `GoRouter` navigation with an
  effect-based Splash ViewModel and page adapter. Startup still waits two
  seconds, initializes the app, checks the same auth state and routes failures
  to login; the Splash animation is now a pure view and startup tests inject
  delay/initializer ports without leaking timers.
- 2026-08-09: Completed reachability cleanup. `rg` found no route or external
  import for Support Chat or the remaining Riverpod-backed Cart, Restaurant,
  Address and Orders widgets, so that graph and its tests were removed rather
  than left as a second state owner. Modal sheets now live with page adapters.
  The imperative Mapbox observers are explicit `presentation/platform`
  adapters; all `views`, `components` and `widgets` are pure UI.
- 2026-08-09: Strengthened `tool/verify_architecture.dart --strict` to inspect
  every presentation view, component and widget for Riverpod, data-layer and
  navigation imports. Final proof on Flutter 3.32.8: `flutter analyze`, 246
  tests with coverage, `build_runner`, the strict guard, `git diff --check` and
  Android debug build pass. Filtered handwritten coverage is 47.91%.
- 2026-08-11: Re-ran the final proof from the current worktree on Flutter
  3.32.8. Code generation wrote zero outputs; analyzer, strict architecture
  guard, coverage policy and `git diff --check` passed. The complete coverage
  suite passed 246 tests; handwritten reachable coverage is 47.94%. Android
  debug APK built successfully. The iOS no-codesign build also completed after
  compiling cold native Pods: `build/ios/iphoneos/Runner.app` (718.1 seconds).

## Decisions

- 2026-08-09: Keep the existing notification error SnackBar rather than
  changing the visible feedback pattern during migration. Its trigger is now a
  acknowledged ViewModel effect, so presentation remains the only layer that
  touches `BuildContext`.
- 2026-08-09: `presentation/platform` is the only exception to pure UI rules:
  it hosts imperative Mapbox rendering and observes map-specific providers.
  It cannot start or stop tracking; lifecycle, retry and socket lease actions
  remain in `OrderTrackingViewModel`.

## Validation

- Focused ViewModel, domain, adapter, widget, golden and journey tests per
  migrated slice.
- Architecture import/effect/navigation verifier.
- `flutter analyze`, full `flutter test --coverage`, generated-code check,
  Android debug build, iOS no-codesign build and root contract gate.

Final proof (Flutter 3.32.8): `flutter test --coverage` (246 passed),
`flutter analyze`, `dart run build_runner build --delete-conflicting-outputs`
(zero outputs), strict architecture guard, coverage policy, `git diff --check`,
Android debug APK and iOS `--no-codesign` build all pass. Handwritten reachable
coverage is 47.94% across 376 files.
