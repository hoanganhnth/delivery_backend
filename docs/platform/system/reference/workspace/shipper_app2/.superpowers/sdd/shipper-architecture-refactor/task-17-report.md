# Task 17 Report: Delivery Tracking Integration Decomposition

## Outcome

Decomposed `DeliveryTrackingIntegration.ts` into a composition root plus one
route-refresh coordinator and four listener-registration modules. The public
`installDeliveryTrackingIntegration` signature is unchanged. The coordinator
is instantiated exactly once and is passed to route, location, status, and
recovery registration functions.

## Implementation

- Added `deliveryRouteRefreshCoordinator.ts`. It owns the single
  `lastRouteUpdate` snapshot, existing origin selection, status-based
  destination mapping, 30-second/100-meter refresh policy, clock timestamp,
  `fetchRoute` payload, and reset operation.
- Added `deliveryRouteIntegration.ts` for accept, active-deliveries,
  active-delivery, and batch-hydration route listeners.
- Added `deliveryLocationIntegration.ts` for online GPS publishing, route
  refresh, proximity eligibility, and `setCanUpdateStatus`.
- Added `deliveryStatusIntegration.ts` for applied-status route refresh,
  delivered overlay, 2-second delay, route reset, and completed-delivery
  cleanup.
- Added `deliveryRecoveryIntegration.ts` for online recovery and the existing
  logout/session-expired predicate and route reset.
- Reduced `DeliveryTrackingIntegration.ts` to wiring only.
- Added `deliveryTrackingIntegrationBoundaries.test.ts` covering module
  presence, forbidden View/UI/native-adapter imports, exactly one coordinator
  factory call, shared coordinator wiring, and the single route snapshot.

## TDD RED/GREEN Evidence

RED was run before creating any production modules:

```text
npm test -- --runInBand __tests__/deliveryTrackingIntegrationBoundaries.test.ts
exit=1
FAIL .../deliveryTrackingIntegrationBoundaries.test.ts
ENOENT: no such file or directory, open .../src/app/composition/integrations/deliveryRouteRefreshCoordinator.ts
```

GREEN after extraction:

```text
npm test -- --runInBand __tests__/deliveryTrackingIntegrationBoundaries.test.ts
exit=0; 1 suite passed, 1 test passed

npm test -- --runInBand __tests__/deliveryTrackingIntegration.test.ts
exit=0; 1 suite passed, 6 tests passed
```

## Validation

- `npm run typecheck` — passed.
- `npm run lint` — passed.
- `npm run verify:architecture` — passed: `Architecture boundary check passed.`
- `npm run verify` — passed: 75 suites, 279 tests.
- `npm test -- --runInBand --detectOpenHandles` — passed: 75 suites, 279 tests,
  with no open-handle failure.
- `git diff --check` — passed.
- No emulator, simulator, native smoke, or device test was run.

## Files Changed

- `src/app/composition/integrations/DeliveryTrackingIntegration.ts`
- `src/app/composition/integrations/deliveryRouteRefreshCoordinator.ts`
- `src/app/composition/integrations/deliveryRouteIntegration.ts`
- `src/app/composition/integrations/deliveryLocationIntegration.ts`
- `src/app/composition/integrations/deliveryStatusIntegration.ts`
- `src/app/composition/integrations/deliveryRecoveryIntegration.ts`
- `__tests__/deliveryTrackingIntegrationBoundaries.test.ts`
- `.superpowers/sdd/shipper-architecture-refactor/task-17-report.md`

## Self-Review and Concerns

The diff is limited to the requested app-composition boundary. Listener
predicates, dispatch ordering, route origins/destinations/threshold/timestamps
and payloads, GPS/proximity behavior, completion overlay/delay/cleanup, online
recovery, logout/session reset, and backend/navigation/FCM/COD contracts remain
unchanged. No unresolved concerns identified. The new boundary test is
intentionally source-structural; existing behavioral tests provide the runtime
coverage for the preserved workflows.

## Fix Round 1 Update

### Files Changed

- `__tests__/deliveryTrackingIntegration.test.ts`
- `__tests__/deliveryTrackingIntegrationBoundaries.test.ts`
- `.superpowers/sdd/shipper-architecture-refactor/task-17-report.md`

### RED / GREEN

RED:

```text
npm test -- --runInBand __tests__/deliveryTrackingIntegration.test.ts
FAIL ... clears the route throttle after logout
Expected: true
Received: false
```

That failure came from the old dispatch-spy proof and confirmed the reset
observability gap.

GREEN:

```text
npm test -- --runInBand __tests__/deliveryTrackingIntegration.test.ts
PASS 9 tests

npm test -- --runInBand __tests__/deliveryTrackingIntegrationBoundaries.test.ts
PASS 1 test

npm run typecheck
PASS
```

### Behavior Change

No production behavior changed. The fix only strengthened test proof for exact
listener counts, exact coordinator wiring, proximity eligibility, logout route
reset, and session-expiry throttle reset.

### Concerns

None beyond the existing reliance on Jest timing and the real store listener
chain.
