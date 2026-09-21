# Task 4 Fix Round 1 Report

## Finding fixed

The `OrderDetail` navigation entrypoint previously resolved `deliveryId` only
against `currentDelivery.entities`. Delivery history items are authoritative in
`deliveryHistory.history`, so selecting a delivered historical item produced a
missing delivery and rendered the null fallback.

The app-composition detail screen now resolves the requested id from both
existing read models, preferring the active current-delivery entity and then
falling back to the matching history item. Navigation continues to carry only
`{ deliveryId: number }`; no backend endpoint, delivery/auth API behavior, or
serialized `Delivery` navigation payload was added.

## Files changed

- `src/app/composition/screens/OrderDetailScreen.tsx`
  - Added the app-composition entrypoint and cross-feature resolver.
  - Looks up active `currentDelivery.entities` first, then
    `deliveryHistory.history`.
  - Creates the app navigation port and passes the resolved delivery into the
    feature route.
- `src/app/navigation/AppNavigator.tsx`
  - Wires the composition detail screen to the root `OrderDetail` route.
- `src/features/delivery/presentation/routes/OrderDetailRoute.tsx`
  - Converted the feature route to a prop-driven renderer receiving
    `{ delivery, navigation }`.
  - Removed direct React Navigation and app-store access from the feature
    route.
- `__tests__/OrderDetailNavigation.test.tsx`
  - Added a render-level regression test with a history-only delivery id.
- `__tests__/navigationContracts.test.ts`
  - Updated the contract checks to enforce composition-owned resolution,
    composition screen wiring, delivery-id-only params, and the feature route
    boundary.

## Boundary decisions

- Cross-feature state lookup belongs to app composition, where the active and
  historical delivery read models can be coordinated.
- The delivery feature route remains reusable and feature-local: it accepts a
  `Delivery` and an `OrderDetailNavigationPort` and does not know about route
  params, app navigation, or Redux store selection.
- The existing current-delivery lookup remains first in precedence; history is
  a fallback for delivered orders that are no longer in the active read model.
- Navigation remains identifier-only and no delivery data is fetched or
  serialized as a route parameter.

## Tests and commands

### Test-first evidence

- RED: `npm test -- --runInBand __tests__/OrderDetailNavigation.test.tsx`
  - Failed as expected with `Unable to find an element with text: #173` because
    the history-only delivery was not resolved by the pre-fix route.
- GREEN: `npm test -- --runInBand __tests__/OrderDetailNavigation.test.tsx __tests__/navigationContracts.test.ts`
  - Passed: 2 suites, 2 tests.

### Focused validation

- `npm test -- --runInBand __tests__/OrderDetailNavigation.test.tsx __tests__/navigationContracts.test.ts __tests__/OrderDetailViewModel.test.tsx __tests__/task4Boundaries.test.ts`
  - Passed: 4 suites, 5 tests.
- `npm run typecheck`
  - Passed (`tsc --noEmit`).
- `npm run lint`
  - Passed with no ESLint output.
- `npm run verify:architecture`
  - Passed: `Architecture boundary check passed.`
- `git diff --check`
  - Passed with no whitespace errors.

### Regression validation

- `npm test -- --runInBand`
  - Passed: 60 suites, 183 tests.

## Unresolved concerns

None identified within the scoped finding. The route continues to render its
existing null fallback when a requested id is absent from both read models;
handling an unknown id was outside this fix.
