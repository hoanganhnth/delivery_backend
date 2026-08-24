# Execution Plan: Shipper App Feature-First MVVM Refactor

Date: 2026-08-09

## Status

Completed

## Outcome

`shipper_app2` has a feature-first MVVM architecture: production Views are pure
renderers receiving typed state and a typed event sink; ViewModels own user
actions, orchestration, navigation/feedback ports, timers and lifecycle effects;
data/native boundaries are injected ports. Existing Gateway, auth, delivery,
tracking and push contracts remain unchanged.

## Context

- Product/system authority: `docs/product/overview.md`, `docs/ARCHITECTURE.md`,
  `docs/product/features/delivery-matching.md`.
- Workflow: `AGENTS.md`, `docs/WORKFLOW.md`.
- App contract/test authority: `shipper_app2/README.md`,
  `shipper_app2/TESTING.md`, `shipper_app2/__tests__/`.
- Baseline 2026-08-09: `npm run verify` passes 33 suites / 124 tests; coverage
  is statements 81.70%, branches 70.66%, functions 77.67%, lines 84.06%.
- Current 2026-08-09: legacy layers have been deleted; `npm run verify` passes
  43 suites / 146 tests. `npm run test:coverage` is statements 82.03%,
  branches 71.79%, functions 82.29%, lines 85.86%.

## Scope

In scope:

- All production-reachable shipper surfaces: auth/bootstrap, drawer,
  availability, tracking/map, offer, delivery lifecycle, history/detail,
  notifications/push, profile/documents/ratings.
- Feature-first folder ownership, typed ports, pure views, ViewModel event
  contracts, state ownership, UI tokens/primitives, test harness and
  architecture import verification.
- Migration of existing services/types/redux/utilities into their owning
  feature/core boundary, followed by legacy cleanup.

Out of scope:

- Backend, Gateway, Kafka, public endpoint, payload, delivery-state-machine or
  threshold changes.
- Enabling hidden registration, settlement, withdrawal, payout, fake GPS,
  settings or earnings capabilities.
- Product UX redesign, native dependency upgrades and generated iOS/Android
  output changes.

## Approach

1. Record the architectural decision and add characterization/architecture
   tests before changing behavior.
2. Build a composition root, typed repository/platform ports and shared UI
   foundation without changing observable behavior.
3. Migrate one vertical feature slice at a time through
   `Route -> ViewModel -> View`, keeping temporary re-export facades only while
   callers migrate.
4. Treat ViewModels as the sole owner of user-event handling and side effects;
   Views may retain renderer-only animation state but only emit semantic events.
5. Delete legacy layers only after all callers and tests use the new ownership
   structure.

## Implementation Handoff

### Starting points

An engineer changing the app should begin at the relevant screen's `View` and
follow this fixed direction:

```text
View event/type -> ViewModel -> feature state command or domain policy
                              -> injected repository/platform port
                              -> production adapter under app/core/feature data
```

The reverse dependency direction is forbidden. In particular, do not import a
Redux action into a native adapter, access a repository from a View, or access
Redux/navigation from a View. `scripts/verify-architecture.mjs` enforces the
important import boundaries.

### Runtime and composition ownership

| Concern | Authoritative files | Change rule |
| --- | --- | --- |
| App boot, provider and FCM background registration | `shipper_app2/App.tsx`, `shipper_app2/index.js`, `shipper_app2/src/app/bootstrap/` | Keep boot as composition only; background and foreground push must use the same `pushPersistence` singleton. |
| Store, listeners and service injection | `shipper_app2/src/app/store/`, `shipper_app2/src/app/productionRepositories.ts` | Add a new repository/port to `ServiceRegistry`, then update fakes in `test-support/testDependencies.ts`. |
| Runtime/native injection | `shipper_app2/src/app/AppRuntimeContext.tsx`, `shipper_app2/src/app/productionRuntimeDependencies.ts` | Expose narrow typed ports only; production adapters are constructed here, never inside a ViewModel. |
| Navigation and global overlay | `shipper_app2/src/app/navigation/`, `shipper_app2/src/app/overlay/` | Routes adapt typed navigation ports; the overlay host remains the only global popup coordinator. |
| Generic HTTP/session/feedback/media | `shipper_app2/src/core/` | Keep these feature-agnostic. A core module cannot import feature, app composition or runtime config code. |
| Shared visual/pure helpers | `shipper_app2/src/shared/` | Share tokens, primitives, formatters and pure geometry only; it cannot import a feature or store. |

### Screen ownership map

| Surface | Route → ViewModel → View | State/data owner | Important behavior to preserve |
| --- | --- | --- | --- |
| Bootstrap and login | `auth/presentation/routes/LoginRoute.tsx` → `useLoginViewModel.ts` → `LoginView.tsx`; `app/bootstrap/useAppBootstrapViewModel.ts` | `auth/state`, `AuthRepository`, session port | Password/Google login and post-login hydration; do not reveal registration or password-reset UI. |
| Main map, availability and GPS | `tracking/presentation/routes/MainMapRoute.tsx` → `useMainMapViewModel.ts` → `MapCanvasView.tsx` | `tracking/state`, GPS/socket/directions ports | `useMainMapViewModel` is the sole owner of GPS, socket and offer-polling lifecycle; teardown must happen on unavailable/background/unmount. |
| Current offer | `delivery/presentation/routes/MatchFoundRoute.tsx` → `useOfferViewModel.ts` → `MatchFoundView.tsx` | `delivery/state/currentDeliverySlice`, delivery repository | Push only wakes the app; canonical offer is recovered through the documented Gateway endpoint. Preserve expiration, active-delivery guard and COD policy. |
| Active delivery | `delivery/presentation/routes/ActiveDeliverySheetRoute.tsx` → `useActiveDeliveryViewModel.ts` → `ActiveDeliverySheetView.tsx` | delivery/tracking state | Preserve the authorized sequence `ASSIGNED → PICKED_UP → DELIVERING → DELIVERED`; route refresh is a ViewModel consequence, not gesture code. |
| Detail, cancel, success and history | `OrderDetailRoute.tsx`, `DeliverySuccessRoute.tsx`, `DeliveryHistoryRoute.tsx` with their matching ViewModels/Views | delivery state/repository | Keep cancellation confirmation, delivery payloads and history bounds unchanged. |
| Drawer, profile, documents and ratings | `shipper/presentation/routes/` with the matching ViewModels/Views | `shipper/state`, `ShipperRepository`, media port | Status toggle, logout, document upload and rating refresh stay in ViewModels; do not add settlement/payout/settings capability. |
| Notifications and push | `notifications/presentation/routes/NotificationsRoute.tsx` → `useNotificationsViewModel.ts` → `NotificationsView.tsx`; `notifications/platform/` | notification state/repository and push runtime ports | Preserve durable notification actions and session-bound token lifecycle; FCM is not a delivery source of truth. |

### View event contract

Every interactive View exports its `ViewState` and a discriminated `Event` type
next to its component. Existing event unions are the contract for the screen:

- Login: input changes, submit and Google sign-in.
- Offer/active delivery: accept, reject, close and advance delivery state.
- Detail/cancel/history/success: navigation, cancellation modal sub-events,
  history refresh/detail selection and return destinations.
- Drawer/profile/documents/ratings: navigation, availability, logout, document
  text/image/save and refresh.
- Notifications: tab selection, refresh, read/read-all and delete.

When adding a user action, first add the union case and immutable state needed
by the View. Then implement the event in the ViewModel using a domain rule,
feature command or injected port. Do not pass callbacks that expose raw Redux
actions, React Navigation objects, `Alert`, native SDK instances or HTTP
clients into a View.

### Safe change recipe

1. Locate the screen in the table above and read its View, ViewModel, Route,
   focused test and the owning feature's `domain`, `state` and `data` folders.
2. Add/adjust the typed `ViewState` and discriminated event in `views/`; retain
   renderer-only animation state locally only when it has no business effect.
3. Put validation, timing, confirmation, navigation and asynchronous behavior
   in the ViewModel. Add a narrow port in `core/platform/ports.ts` or the
   feature domain when an external dependency is needed.
4. Keep a Gateway mapping in the feature's `data/*Contract.ts` and repository;
   test malformed/missing fields fail closed before changing a payload.
5. Add or update a ViewModel test, repository/adapter contract test and any
   reducer/policy test affected by the change. Add cleanup proof for every new
   timer, subscription or GPS/socket watch.
6. Run `npm run verify:architecture` before the broader checks. If it fails,
   repair the ownership boundary rather than weakening the checker.
7. Run the full validation sequence below. For an externally observable policy
   change, obtain product/backend authority and create a new cross-project plan
   before editing this app.

### Target ownership

- `src/app`: composition, store factory, bootstrap, typed navigation and global
  overlay host.
- `src/core`: API/session/platform ports and production adapters.
- `src/shared`: pure common domain helpers, formatters, theme and UI primitives.
- `src/features/auth`, `shipper`, `delivery`, `tracking`, `notifications`:
  domain, data, application/state and presentation layers.
- A feature View owns `ViewState + ViewEvent`; it does not import Redux,
  navigation, native ports, services or `Alert`.

### Wave order

1. Durable plan, ADR, characterization tests and architecture checker.
2. Composition root, typed ports, feature-state/store foundation and UI tokens.
3. Auth, bootstrap and typed navigation.
4. Shipper availability, GPS/WebSocket tracking and Main Map.
5. Current offer, active delivery lifecycle, detail/cancel/success and overlay.
6. History, notifications and push lifecycle.
7. Profile, documents, ratings and drawer.
8. Legacy deletion, documentation convergence and final proof.

## Risks And Recovery

- Large moves can break routing or state ownership: each wave remains runnable
  and preserves the old file only as a thin re-export until all callers move.
- GPS/socket/push can leak resources: one coordinator owns each subscription;
  every timer/listener/watch has an explicit teardown test.
- A failed mutation must not fabricate convergence: reducers retain canonical
  state until a validated backend response arrives.
- If a product-policy choice appears (for example a different tracking lifetime
  or threshold), stop at that boundary and request an explicit decision.
- No persistence/schema migration is involved; recovery is a narrow git revert
  of the affected wave after restoring its route facade.

## Progress

- [x] Read system/app contracts and record baseline validation.
- [x] Agree feature-first MVVM, typed `onEvent`, typed side-effect ports,
  incremental migration and preserved UX.
- [x] Add ADR, characterization harness and architecture import checker.
- [x] Build foundation/composition root and shared UI contracts.
- [x] Migrate auth/bootstrap/navigation.
- [x] Migrate tracking/availability and fulfilment spine.
- [x] Migrate secondary surfaces and remove legacy layers.
- [x] Run final full validation and move this plan to completed.

## Decisions

- 2026-08-09: Keep Redux Toolkit as the app state container; ViewModels are
  hooks/controllers above selectors and feature commands, not a replacement
  state library.
- 2026-08-09: Views use one typed discriminated-union event sink per screen;
  Route adapters provide typed navigation ports and ViewModels use typed
  feedback/media/timing/native ports.
- 2026-08-09: Preserve all documented MVP behavior and contracts; do not
  reinterpret refactor work as authority to reveal hidden capability.
- 2026-08-09: `MainMapViewModel` is the single owner of GPS/socket/polling
  lifecycle. Delivery ViewModels only commit feature state; the native GPS
  adapter never owns Redux or AppState policy.
- 2026-08-09: FCM adapters and persistence are owned by notifications. The
  production push runtime and session bridge are composed under `src/app`; the
  foreground runtime and Firebase background handler share the same persistence
  singleton.
- 2026-08-09: The architecture checker rejects source files under legacy roots
  and prohibits Views, Routes and ViewModels from crossing their defined
  dependency boundaries.

## Validation

- Focused proof: domain/contract/reducer/ViewModel/View tests for each migrated
  feature; architecture import checker; timer/subscription cleanup tests.
- 2026-08-09 repository proof passed: `npm run verify` (typecheck, lint,
  architecture check, 43 suites / 146 tests) and `npm run verify:coverage`.
  Coverage: statements 82.03% (1648/2009), branches 71.79% (942/1312),
  functions 82.29% (530/644), lines 85.86% (1373/1599).
- 2026-08-09 final build proof passed after `npm ci`:
  `npx react-native build-android --mode=debug` (`BUILD SUCCESSFUL`, 349
  Gradle tasks) and `git diff --check`. A final
  `npm run verify:architecture` also passed; legacy source roots
  `src/navigation`, `src/presentation`, `src/services`, `src/types` and
  `src/utils` are absent.
- Device Mapbox/GPS/background/FCM behavior remains a final optional sanity
  check, not a claim supported by fake adapters alone. The Android build emitted
  third-party deprecation/Mapbox C++ warnings but no application build error.

## Result

Completed. Production-reachable shipper surfaces now conform to
`Route → ViewModel → View`, feature boundaries own contracts/state/adapters,
and legacy source roots are removed. The durable ADR is
`docs/decisions/0004-shipper-feature-first-mvvm-architecture.md`; this plan is
the migration and recovery record for future maintainers.

Verified limits: automated tests and a debug Android bundle prove compilation,
contract/behavior seams and dependency boundaries. A real-device smoke test of
Mapbox rendering, background GPS and FCM delivery remains advisable before a
production release; it was not performed as part of this refactor.
