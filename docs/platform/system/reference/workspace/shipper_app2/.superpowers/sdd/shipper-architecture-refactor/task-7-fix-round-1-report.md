# Task 7 Fix Round 1 Report

## Outcome

Fixed the callback-identity regression identified in review `f2d489f..64470b0`.
`DeliveryHistoryScreen` and `ShipperRatingScreen` now expose stable async load
commands with `useCallback(..., [dispatch])`. The ViewModel effect dependency
lists and initial-load semantics are unchanged. A Redux loading-state update no
longer causes either route's loader-dependent effect to run again.

The fix is intentionally limited to the two composition bindings and focused
regression coverage. No API, network, authentication, COD, delivery,
WebSocket, FCM, lifecycle, or navigation contract was changed.

## Files changed

- `src/app/composition/screens/DeliveryHistoryScreen.tsx`
  - Imported `useCallback`.
  - Moved the existing `fetchDeliveryHistory` dispatch into a stable
    `loadHistory` callback with `[dispatch]`.
  - Passed that callback to `DeliveryHistoryRoute`.
- `src/app/composition/screens/ShipperRatingScreen.tsx`
  - Imported `useCallback`.
  - Moved the existing `fetchShipperRatings` dispatch into a stable
    `loadRatings` callback with `[dispatch]`.
  - Passed that callback to `ShipperRatingRoute`.
- `__tests__/CompositionLoadBindings.test.tsx`
  - Added focused history and ratings composition regression tests.
- `.superpowers/sdd/shipper-architecture-refactor/task-7-fix-round-1-report.md`
  - Added this report.

No other production or architecture files were modified in this fix round.

## Root cause and compatibility decision

The feature ViewModels correctly include their injected load commands in their
effect and event-callback dependency lists:

- `useDeliveryHistoryViewModel` derives `load` from `[loadHistory, shipperId]`
  and performs its initial load from `[load]`.
- `useShipperRatingViewModel` performs its initial load from `[loadRatings]`
  and uses the same command for refresh events.

The composition screens supplied new inline async functions on every render.
When a Redux loading action changed the selected state, the composition screen
rendered again, the function identity changed, and the ViewModel effect chain
could start another load. The repair stabilizes the source command identity at
the composition boundary with `[dispatch]`; it does not suppress or weaken any
effect dependency and does not remove the initial load.

The existing action identities and payloads remain unchanged:

- History still dispatches `fetchDeliveryHistory` with the canonical shipper id
  and `{ status: 'DELIVERED' }`.
- Ratings still dispatches `fetchShipperRatings()` with no changed arguments.

## Before/after import-boundary inventory

This fix round is layered on the completed Task 7 boundary cleanup. The
inventory below records the complete relevant baseline and the verified result.

### Before Task 7 cleanup (`f2d489f` baseline)

Feature-to-app/store or runtime edges were:

- `src/features/auth/state/authSlice.ts` → `../../../app/store/types`
  (`AppThunkConfig`).
- `src/features/auth/state/selectors.ts` → `../../../app/store/types`
  (`RootState`).
- `src/features/debug/presentation/viewmodels/useDebugToolsViewModel.ts` →
  `../../../../app/AppRuntimeContext` (`useAppRuntime`).
- `src/features/delivery/presentation/routes/ActiveDeliverySheetRoute.tsx` →
  `../../../../app/store` (`useAppDispatch`, `useAppSelector`).
- `src/features/delivery/presentation/routes/MatchFoundRoute.tsx` →
  `../../../../app/AppRuntimeContext` and `../../../../app/store`.
- `src/features/delivery/presentation/viewmodels/useDeliveryHistoryViewModel.ts`
  → `../../../../app/store`.
- `src/features/delivery/presentation/viewmodels/useOrderDetailViewModel.ts` →
  `../../../../app/AppRuntimeContext` and `../../../../app/store`.
- `src/features/delivery/state/historyDeliverySlice.ts` →
  `../../../app/store/types` (`AppThunkConfig`).
- `src/features/notifications/state/notificationSlice.ts` →
  `../../../app/store/types` (`AppThunkConfig`).
- `src/features/notifications/state/selectors.ts` →
  `../../../app/store/types` (`RootState`).
- `src/features/shipper/presentation/viewmodels/useDocumentsViewModel.ts` →
  `../../../../app/AppRuntimeContext` and `../../../../app/store`.
- `src/features/shipper/presentation/viewmodels/useShipperRatingViewModel.ts` →
  `../../../../app/AppRuntimeContext` and `../../../../app/store`.
- `src/features/shipper/state/selectors.ts` →
  `../../../app/store/types` (`RootState`).
- `src/features/shipper/state/shipperSlice.ts` →
  `../../../app/store/types` (`AppThunkConfig`).
- `src/features/tracking/state/routeSlice.ts` →
  `../../../app/store/types` (`AppThunkConfig`).
- `src/features/tracking/state/selectors.ts` →
  `../../../app/store/types` (`RootState`).

The historical feature-to-feature edge was:

- `src/features/delivery/presentation/viewmodels/useDeliveryHistoryViewModel.ts`
  → `../../../shipper/state` (`selectCanonicalShipperId`).

Delivery history also read shipper state directly from feature code, rather than
receiving the canonical identity through app composition.

### After Task 7 cleanup and this fix round (`HEAD`)

- `src/features/auth/**`, `src/features/delivery/**`,
  `src/features/notifications/**`, `src/features/shipper/**`,
  `src/features/tracking/**`, and `src/features/debug/**` contain zero imports
  targeting `app/store`, `app/AppRuntimeContext`, or `app/composition`.
- Feature source contains zero runtime or type-only feature-to-feature imports.
- Feature state/selectors use feature-local structural source contracts or
  feature-local thunk configuration types; they do not import app-store types.
- Feature ViewModels consume feature-local state, core ports, navigation ports,
  and command contracts; they do not import app/store, runtime context, or
  concrete adapters.
- `ViewState` and navigation contracts continue to use presentation data and
  scalar identifiers rather than domain entities. History navigation carries
  `deliveryId`; order detail resolves the delivery from app-composed state.
- App composition remains the cross-feature workflow owner. Its relevant
  bindings are:
  - `src/app/composition/screens/DeliveryHistoryScreen.tsx` → app store hooks,
    delivery history state/thunk, shipper identity selector, and the delivery
    feature route.
  - `src/app/composition/screens/ShipperRatingScreen.tsx` → app store hooks,
    runtime feedback, shipper state/thunk, and the shipper feature route.
  - `src/app/composition/screens/ActiveDeliverySheetScreen.tsx` → app store,
    runtime ports, and the delivery route.
  - `src/app/composition/overlays/MatchFoundOverlay.tsx` → app store, runtime
    ports, and the delivery offer route.
  - `src/app/composition/screens/DocumentsScreen.tsx` → app store/runtime
    bindings and the shipper documents route.
  - `src/app/composition/screens/OrderDetailScreen.tsx` → app store/runtime
    bindings and the delivery order-detail route.
  - `src/app/composition/screens/DebugToolsScreen.tsx` → development runtime
    and the debug tooling route.
- `test-support/testDependencies.ts` retains app runtime/store type imports for
  test-only fixture construction; this is outside production feature source.
- App composition → feature imports remain allowed by the architecture checker.
- Debug remains classified as app development tooling, not a production feature.

The current architecture checker also rejects type-only imports because it
parses all local import declarations, including `import type`, and rejects
feature-to-feature edges while allowing app composition to bind features.

## TDD evidence

The production change was made only after adding the focused regression test.

### RED

Added `__tests__/CompositionLoadBindings.test.tsx` with two route probes. Each
probe models the ViewModel effect dependency chain by running an effect whose
dependency is the supplied load command, then dispatching one Redux loading
state update.

Command:

```text
npm test -- --runInBand __tests__/CompositionLoadBindings.test.tsx
```

Observed failure before the production change:

```text
FAIL __tests__/CompositionLoadBindings.test.tsx
  composition load command bindings
    ✕ keeps delivery history loading stable across Redux loading updates
    ✕ keeps rating loading stable across Redux loading updates

Expected: 1
Received: 2

Test Suites: 1 failed, 1 total
Tests:       2 failed, 2 total
```

The failure was the intended callback-identity regression, not a test or
compilation error.

### GREEN

After adding `useCallback` in the two composition screens, the same command
reported:

```text
PASS __tests__/CompositionLoadBindings.test.tsx
  composition load command bindings
    ✓ keeps delivery history loading stable across Redux loading updates
    ✓ keeps rating loading stable across Redux loading updates

Test Suites: 1 passed, 1 total
Tests:       2 passed, 2 total
```

## Validation results

All commands below were run on the final modified tree and exited with status
`0`.

Focused history/ratings/composition tests:

```text
npm test -- --runInBand __tests__/SecondaryViewModels.test.tsx __tests__/ShipperViewModels.test.tsx __tests__/CompositionLoadBindings.test.tsx

Test Suites: 3 passed, 3 total
Tests:       6 passed, 6 total
```

Typecheck:

```text
npm run typecheck

tsc --noEmit completed with no diagnostics.
```

Lint:

```text
npm run lint

eslint . completed with no diagnostics.
```

Architecture verification:

```text
npm run verify:architecture

Architecture boundary check passed.
```

Full test suite:

```text
npm test -- --runInBand

Test Suites: 68 passed, 68 total
Tests:       211 passed, 211 total
```

Required open-handle regression check:

```text
npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles

Test Suites: 1 passed, 1 total
Tests:       5 passed, 5 total
```

The open-handle run emitted no Jest lifecycle warning. `git diff --check` also
completed without whitespace errors.

## Native/device smoke limitation

Native/device smoke checks were not run in this environment. In particular,
auth, online/offline, GPS/WebSocket, offer handling, delivery lifecycle,
batch/COD, background/foreground, and logout/push cleanup still require device
or native-runtime verification. They are recorded as unrun, not passed.

## Unresolved risks

- Native/device-only behavior remains unverified for the reasons above.
- The test-only `test-support/testDependencies.ts` app runtime/store type edges
  remain intentionally test-scoped.
- The new tests prove stable composition command identity across the relevant
  Redux update; they do not replace native smoke verification.
- No production boundary or behavior risk remains identified within this
  scoped callback fix.
