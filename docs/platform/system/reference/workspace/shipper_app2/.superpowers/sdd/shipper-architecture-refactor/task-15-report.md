# Task 15 implementation report

## Outcome

Separated delivery business commands from the state compatibility facade. The
canonical production entry point is now
`src/features/delivery/application/index.ts`; the state facade continues to
re-export the existing commands for compatibility.

## Implementation

- Added the application barrel exporting exactly `acceptOrder`,
  `cancelCurrentAssignment`, `fetchActiveDelivery`, `fetchActiveDeliveries`,
  `fetchCurrentOffer`, `hydrateBatchSnapshot`, `getUniqueBatchIds`,
  `rejectCurrentOffer`, and `updateDeliveryStatus` from `deliveryCommands.ts`.
- Migrated the seven production app consumers in bootstrap, store,
  integration, recovery, and view-model composition code to that barrel.
- Kept state-only actions, selectors, and reducer imports on the state facade.
- Added the architecture guard for named delivery-command imports from the
  resolved state barrel or `currentDeliverySlice` in `src/app/**`.
- Added direct-state rejection and application-barrel allowance fixtures, plus
  a state-only allowance fixture.

## Exact TDD RED/GREEN evidence

RED was run before creating the application barrel or architecture guard:

```text
npm test -- --runInBand __tests__/deliveryApplicationBoundaries.test.ts
FAIL .../deliveryApplicationBoundaries.test.ts
  1 failed, 1 passed
  Expected: 1
  Received: 0
```

The failure was the intended missing-boundary behavior: the direct state
fixture was accepted by the pre-change architecture checker.

GREEN was run after adding the barrel, migrations, guard, and fixtures:

```text
npm test -- --runInBand __tests__/deliveryApplicationBoundaries.test.ts __tests__/verifyArchitecture.test.ts
PASS .../deliveryApplicationBoundaries.test.ts
PASS .../verifyArchitecture.test.ts
Test Suites: 2 passed, 2 total
Tests: 26 passed, 26 total
```

## Validation

- Focused delivery suite: 7 suites passed, 23 tests passed.
- Popup open-handle gate: `npx jest __tests__/MatchFoundPopup.test.tsx
  --runInBand --detectOpenHandles`; 1 suite and 5 tests passed.
- `npm run typecheck`: passed.
- `npm run lint`: passed.
- `npm run verify:architecture`: passed.
- `npm run verify`: passed; 73 suites and 270 tests passed.
- No emulator, simulator, native smoke, or device test was run.

## Files changed

- `src/features/delivery/application/index.ts`
- `src/app/bootstrap/AppInitializer.ts`
- `src/app/store/index.ts`
- `src/app/composition/integrations/DeliveryTrackingIntegration.ts`
- `src/app/composition/coordinators/DeliveryRecoveryCoordinator.tsx`
- `src/app/composition/viewmodels/useActiveDeliverySheetScreenViewModel.ts`
- `src/app/composition/viewmodels/useMatchFoundOverlayViewModel.ts`
- `src/app/composition/viewmodels/useOrderDetailScreenViewModel.ts`
- `scripts/verify-architecture.mjs`
- `__tests__/deliveryApplicationBoundaries.test.ts`
- `__tests__/verifyArchitecture.test.ts`

## Self-review and concerns

The application barrel re-exports the original thunk/function objects, so thunk
identity, type prefixes, request behavior, backend payloads, and state-facade
compatibility remain unchanged. The guard checks resolved module paths and
named bindings, including aliases, while allowing state-only bindings.

Implementation commit: `00818aa` (`refactor: separate delivery application commands`).
No unresolved code concerns were found. Per the request, no subagent or native
device review was used; the diff was independently checked locally against the
brief, import scan, focused tests, architecture tests, and full verification.

## Fix round 1

### Finding and implementation

The review finding was reproduced: `useDeliveryHistoryScreenViewModel.ts`
imported `fetchDeliveryHistory` from the delivery state barrel, while the thunk
was defined in `state/historyDeliverySlice.ts` and absent from the application
barrel. The fix moved the exact thunk authority into
`application/deliveryCommands.ts` using `DeliveryCommandThunkConfig`, preserving
`FetchHistoryParams`, action type `historyDelivery/fetchDeliveryHistory`, the
`historyForShipper(shipperId, params)` repository call, and the fallback
`Không tải được lịch sử đơn hàng`. `historyDeliverySlice.ts` now consumes that
thunk for its extra reducers and re-exports it and its parameter type for
compatibility. The history ViewModel imports the command from the application
barrel and keeps `selectDeliveryHistoryState` in state.

The architecture command set now includes `fetchDeliveryHistory`. Regression
fixtures cover direct `currentDeliverySlice` imports and aliased state-barrel
imports, while state-only and application-barrel imports remain allowed.

### Fix-round TDD RED/GREEN evidence

RED was run after adding the two new fixtures and before production changes:

```text
npm test -- --runInBand __tests__/deliveryApplicationBoundaries.test.ts
FAIL .../deliveryApplicationBoundaries.test.ts
  1 failed, 3 passed
  Expected: 1
  Received: 0
```

The failing case was the direct `currentDeliverySlice` command fixture; the
aliased state-barrel rejection, original state-barrel rejection, and
application-barrel allowance already passed.

GREEN after moving the thunk, migrating the ViewModel, and extending the
command set:

```text
npm test -- --runInBand __tests__/deliveryApplicationBoundaries.test.ts __tests__/deliveryCommands.test.ts __tests__/secondaryStore.test.ts __tests__/CompositionLoadBindings.test.tsx __tests__/OrderDetailNavigation.test.tsx __tests__/deliveryViewModelBoundaries.test.ts
Test Suites: 6 passed, 6 total
Tests: 13 passed, 13 total
```

### Fix-round files

- `src/features/delivery/application/deliveryCommands.ts`
- `src/features/delivery/application/index.ts`
- `src/features/delivery/state/historyDeliverySlice.ts`
- `src/app/composition/viewmodels/useDeliveryHistoryScreenViewModel.ts`
- `scripts/verify-architecture.mjs`
- `__tests__/deliveryApplicationBoundaries.test.ts`

### Fix-round validation and concerns

- `npm run typecheck`: passed.
- `npm run lint`: passed after removing the export-only local type import.
- `npm run verify:architecture`: passed.
- `npm run verify`: passed; 73 suites and 272 tests passed.
- No emulator, simulator, native smoke, or device test was run.

The compatibility exports remain available through the state facade, and the
application and state exports reference the same thunk object. No unresolved
fix-round concerns remain. Fix-round implementation commit: recorded in the
following fix commit.

## Boundary hardening round 2

### Finding and implementation

Re-review identified a future bypass: an app module importing a delivery
command directly from `features/delivery/state/historyDeliverySlice.ts` was not
covered by the prior exact-target guard. The regression fixture now imports
`fetchDeliveryHistory as loadDeliveryHistory` from that direct history slice.
The guard condition is generalized to reject any named binding in the delivery
command set when the resolved target matches
`features/delivery/state/*.ts`. Default reducer imports and state-only named
bindings remain allowed; no command or runtime behavior changed.

### Round-2 TDD RED/GREEN evidence

RED was run after adding the direct history-slice fixture and before changing
the production guard:

```text
npm test -- --runInBand __tests__/deliveryApplicationBoundaries.test.ts
FAIL .../deliveryApplicationBoundaries.test.ts
  1 failed, 4 passed
  Expected: 1
  Received: 0
```

The failing case was the aliased direct `historyDeliverySlice.ts` command
import; the existing state-barrel, direct `currentDeliverySlice`, aliased
state-barrel, and application-barrel cases passed.

GREEN after generalizing the resolved-target predicate:

```text
npm test -- --runInBand __tests__/deliveryApplicationBoundaries.test.ts
PASS .../deliveryApplicationBoundaries.test.ts
Test Suites: 1 passed, 1 total
Tests: 5 passed, 5 total
```

### Round-2 focused validation

- Focused boundary and architecture tests: 2 suites passed, 29 tests passed.
- `npm run typecheck`: passed.
- `npm run lint`: passed.
- `npm run verify:architecture`: passed.
- `npm run verify`: passed; 73 suites and 273 tests passed.
- No emulator, simulator, native smoke, or device test was run.

Round-2 files changed: `scripts/verify-architecture.mjs` and
`__tests__/deliveryApplicationBoundaries.test.ts`, plus this report. No
unresolved concerns remain.
