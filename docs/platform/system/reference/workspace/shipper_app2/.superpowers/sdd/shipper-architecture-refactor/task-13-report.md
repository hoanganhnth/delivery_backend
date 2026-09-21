# Task 13 report — Separate tracking policy from React controllers

## Implementation

- Added `src/features/tracking/domain/routeProjectionPolicy.ts` with the exact structural `RouteProjectionDelivery` input and `routeDestinationForStatus` policy.
- Preserved `[lng, lat]` coordinate ordering and existing `ASSIGNED`, `PICKED_UP`, and `DELIVERING` destinations; both `DELIVERED` and `CANCELLED` return `null`.
- Exported the policy from `src/features/tracking/domain/index.ts`.
- Removed the duplicate policy/type from `useRouteProjectionController.ts`; retained compatibility re-exports for existing callers.
- Changed `DeliveryTrackingIntegration.ts` to import the policy only from the tracking domain barrel.
- Added boundary tests and architecture-script guards for framework-free policy imports and composition integration imports of tracking `use*Controller` modules. Existing cycle, feature-direction, and route/view checks were left intact.

## TDD evidence

RED was captured before adding the domain module/export:

```text
$ npx jest __tests__/deliveryRoutePolicy.test.ts --runInBand
FAIL __tests__/deliveryRoutePolicy.test.ts
  delivery route refresh policy
    ✕ projects pickup and drop-off destinations from the delivery status
    ✓ refreshes on first sample, status change, timeout or meaningful movement
    ✓ does not refresh for a fresh stationary sample

TypeError: (0 , _domain.routeDestinationForStatus) is not a function
Test Suites: 1 failed, 1 total
Tests:       1 failed, 2 passed, 3 total
```

GREEN was then captured with the implementation:

```text
$ npx jest __tests__/deliveryRoutePolicy.test.ts __tests__/trackingApplicationBoundaries.test.ts __tests__/verifyArchitecture.test.ts __tests__/deliveryTrackingIntegration.test.ts __tests__/batchRoutePolicy.test.ts __tests__/routeSlice.test.ts __tests__/mainMapProjection.test.ts __tests__/MainMapScreen.test.tsx --runInBand
Test Suites: 8 passed, 8 total
Tests:       45 passed, 45 total
Snapshots:   0 total
```

## Verification

- `npm run typecheck` — passed.
- `npm run lint` — passed with 0 errors and 0 warnings after cleanup.
- `npm run verify:architecture` — passed: `Architecture boundary check passed.`
- `npm run verify` — passed: 71 suites / 264 tests, 0 failures.
- No emulator, simulator, native smoke, or device test was run, per brief.

## Self-review

I independently reviewed the final diff for scope, compatibility exports, coordinate ordering, status mapping, import direction, and preservation of route refresh/payload behavior. The focused structural fixtures exercise both new architecture failures, and the full architecture checker plus full suite pass. No backend, API, navigation, threshold, or unrelated behavior changes were found.

## Concerns

- Authenticated Mapbox/Gateway/native tracking behavior remains outside this task’s non-device validation scope.
- The compatibility re-export is intentionally temporary and can be removed after downstream callers migrate.
- The architecture guard targets direct local imports of tracking application controller files; it does not attempt to infer policy usage through arbitrary indirect dynamic imports.

## Commit

Implementation commit: `refactor: isolate tracking route policy` (the final commit SHA is reported in the task handoff).

## Fix round 1 — tracking application barrel bypass

### Changed files

- `scripts/verify-architecture.mjs`: composition integrations now reject both direct tracking `use*Controller` targets and the resolved `features/tracking/application/index.ts` barrel.
- `__tests__/verifyArchitecture.test.ts`: added a fixture where a composition integration imports `../../../features/tracking/application`, whose barrel re-exports the controller.

### TDD evidence

RED, before changing the production guard:

```text
$ npx jest __tests__/verifyArchitecture.test.ts --runInBand
FAIL __tests__/verifyArchitecture.test.ts
  22 passed, 1 failed
  rejects a composition integration importing the tracking application barrel
  Expected: 1
  Received: 0
Test Suites: 1 failed, 1 total
Tests:       1 failed, 22 passed, 23 total
```

GREEN, after the narrow resolved-barrel guard change:

```text
$ npx jest __tests__/verifyArchitecture.test.ts --runInBand
PASS __tests__/verifyArchitecture.test.ts
Test Suites: 1 passed, 1 total
Tests:       23 passed, 23 total
```

### Focused validation

- Focused preservation/boundary run: 5 suites / 36 tests passed (`verifyArchitecture`, tracking application boundaries, delivery tracking integration, tracking ViewModels, and MainMapScreen).
- `npm run typecheck` — passed.
- `npm run lint` — passed.
- `npm run verify:architecture` — passed.
- `git diff --check` — passed.
- `npm run verify` — passed: 71 suites / 265 tests.
- No emulator, simulator, native smoke, or device test was run.
