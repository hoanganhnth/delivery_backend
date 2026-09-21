# Task 30 — Auth and lifecycle race fix report

## Outcome

Closed the four whole-branch review findings with host-only regressions and
minimal owning-boundary changes. Logout now synchronously invalidates the API
client session epoch before asynchronous auth work can continue; stale refresh
results cannot write rotated tokens or replay their original request. Explicit
login still reactivates the epoch guard and preserves normal rotated-token
persistence.

Logout pending now stops GPS/socket tracking at its composition owner, rejects
foreground reactivation and GPS publishing, and suppresses location send and
route side effects. Batch map projection accepts hydrated route metadata only
when its delivery is present in the current normalized active collection.
Terminal delivery policy is centralized for `DELIVERED` and `CANCELLED`; a
cancelled recovered row no longer blocks offer polling, while the existing
short-lived delivered completion authority remains available for the success
transition and delayed cleanup contract.

Gateway REST/WebSocket/Bearer, FCM-wake-only, COD, delivery-transition,
batch-recovery, and navigation contracts are unchanged.

## Files changed

Auth/session: `src/config/api.ts`, `src/features/auth/data/HttpAuthRepository.ts`,
`src/features/auth/domain/AuthRepository.ts`,
`src/features/auth/application/authCommands.ts`,
`src/app/productionRepositories.ts`, `src/app/store/index.ts`, and
`src/app/store/sessionBoundarySlice.ts`.

Tracking/map/offer policy: `src/features/tracking/application/useTrackingSessionController.ts`,
`src/app/composition/coordinators/TrackingSessionCoordinator.tsx`,
`src/app/composition/integrations/deliveryLocationIntegration.ts`,
`src/app/composition/viewmodels/useMainMapScreenViewModel.ts`,
`src/features/delivery/domain/entities.ts`,
`src/features/delivery/domain/batchRoutePolicy.ts`,
`src/features/delivery/state/deliveryStateProjection.ts`,
`src/features/delivery/presentation/viewmodels/useOfferViewModel.ts`,
`src/app/composition/coordinators/useCurrentOfferPolling.ts`, and
`src/features/delivery/application/assignmentCommands.ts`.

Focused host regressions: `__tests__/apiRefreshPolicy.test.ts`,
`__tests__/authService.contract.test.ts`, `__tests__/trackingViewModels.test.tsx`,
`__tests__/deliveryTrackingIntegration.test.ts`,
`__tests__/mainMapProjection.test.ts`, `__tests__/deliveryStateProjection.test.ts`,
and `__tests__/deliveryRecoveryCoordinator.test.tsx`.

## RED

Command, run before production edits:

```sh
npm test -- --runInBand __tests__/apiRefreshPolicy.test.ts __tests__/trackingViewModels.test.tsx __tests__/deliveryTrackingIntegration.test.ts __tests__/mainMapProjection.test.ts __tests__/deliveryStateProjection.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx
```

Observed intended RED after correcting two test-fixture issues (a missing
`fetchActiveDeliveries` import and pre-logout route setup):

```text
FAIL __tests__/apiRefreshPolicy.test.ts
  client.invalidateSession is not a function
FAIL __tests__/trackingViewModels.test.tsx
  expected GPS stopTracking to have been called
FAIL __tests__/deliveryTrackingIntegration.test.ts
  expected no route refresh during logout pending
FAIL __tests__/mainMapProjection.test.ts
  received stale embedded delivery 8 instead of null
FAIL __tests__/deliveryStateProjection.test.ts
  received CANCELLED delivery instead of null
FAIL __tests__/deliveryRecoveryCoordinator.test.tsx
  expected polling interval to be scheduled for a cancelled-only recovery

Test Suites: 6 failed, 6 total
Tests:       6 failed, 43 passed, 49 total
```

## GREEN and validation

Focused GREEN after production changes:

```sh
npm test -- --runInBand __tests__/apiRefreshPolicy.test.ts __tests__/trackingViewModels.test.tsx __tests__/deliveryTrackingIntegration.test.ts __tests__/mainMapProjection.test.ts __tests__/deliveryStateProjection.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx
```

```text
Test Suites: 6 passed, 6 total
Tests:       49 passed, 49 total
```

Additional focused auth/session proof after review cleanup:

```sh
npm test -- --runInBand __tests__/apiRefreshPolicy.test.ts __tests__/authService.contract.test.ts __tests__/sessionBoundary.test.ts __tests__/trackingViewModels.test.tsx
```

```text
Test Suites: 4 passed, 4 total
Tests:       28 passed, 28 total
```

Final repository validation on the final tree:

```sh
npm run verify
# typecheck: exit 0
# lint: exit 0
# Architecture boundary check passed.
# Jest: 86 suites / 445 tests passed

npm test -- --runInBand --detectOpenHandles
# 86 suites / 445 tests passed

npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles
# 1 suite / 5 tests passed

git diff --check
# exit 0
```

## Review, risks, and external gates

One scoped self-review was completed against the brief, auth race ordering,
tracking ownership, map authority, terminal policy, contracts, and test proof.
It found and corrected two internal issues before final validation: `DELIVERED`
must remain transient completion authority even though `CANCELLED` is excluded
from active selection, and logout invalidation must not be redundantly invoked
by both the thunk and store middleware. The final implementation uses the
synchronous store boundary with idempotent repository protection for direct
logout calls.

No unresolved host-side regression is known. Risk remains at native-runtime
boundaries only: GPS/socket adapters, actual background lifecycle delivery,
and provider behavior were not exercised here. Per task scope, **no emulator,
simulator, native smoke, or device tests were run**.

## Fix round 1 — delayed auth write and synchronous tracking boundary

### Re-review findings addressed

1. A stale refresh can enter an asynchronous `setTokens` write after the epoch
   check and then reject only after the write completes, leaving rotated
   credentials persisted after logout.
2. Store logout-pending dispatch invalidated auth but relied on React effect
   cleanup for GPS/socket teardown, so the stop was not synchronous and a late
   GPS callback could still reach composition.

### RED

```sh
npm test -- --runInBand __tests__/apiRefreshPolicy.test.ts __tests__/trackingViewModels.test.tsx __tests__/deliveryTrackingIntegration.test.ts
```

```text
FAIL __tests__/apiRefreshPolicy.test.ts
  expected delayed refresh credentials to be cleared; received "rotated-access"
FAIL __tests__/trackingViewModels.test.tsx
  tracking test could not observe a synchronous owner stop boundary
PASS __tests__/deliveryTrackingIntegration.test.ts

Test Suites: 2 failed, 1 passed, 3 total
Tests:       2 failed, 31 passed, 33 total
```

The first run also exposed and then corrected a test-only missing `send` mock;
the second RED above is the regression run after that fixture repair.

### Implementation

- `src/config/api.ts` now awaits the storage write and, when the epoch is stale
  afterward, awaits `clearSession()` before rejecting. No fire-and-forget is
  used.
- Added the app-owned `trackingSessionBoundary` registration bridge. The
  tracking controller registers its synchronous stop/invalidate callback;
  store logout-pending middleware invokes it before forwarding the action.
  Hook cleanup remains in place, and lifecycle/GPS callbacks are gated after
  invalidation.
- Added delayed-storage and immediate teardown/late-GPS host regressions.
  Projection and terminal-policy files were not changed in this fix round.

### GREEN and final validation

```sh
npm test -- --runInBand __tests__/apiRefreshPolicy.test.ts __tests__/trackingViewModels.test.tsx __tests__/deliveryTrackingIntegration.test.ts
# Test Suites: 3 passed, 3 total
# Tests:       33 passed, 33 total

npm run typecheck                         # exit 0
npm run lint                              # exit 0
npm run verify:architecture               # Architecture boundary check passed.
npm test -- --runInBand                    # 86 suites / 446 tests passed
npm test -- --runInBand --detectOpenHandles # 86 suites / 446 tests passed
npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles # 5/5 passed
git diff --check                           # exit 0
```

Scoped self-review confirmed awaited stale-write cleanup, epoch-guarded replay,
normal login rotation, and no feature-to-feature dependency in the tracking
bridge. No emulator, simulator, native smoke, or device tests were run.

## Fix round 2 — owned stale auth cleanup and deferred GPS start

### Re-review findings addressed

1. A stale refresh could finish its delayed `setTokens` call after a newer
   login/reactivation, then call broad `clearSession()` and erase the
   replacement credentials.
2. A GPS `startTracking()` promise could resolve after synchronous logout
   teardown and re-enable native tracking.

### RED

Tests were added before the round-2 production changes. The first focused run
was:

```sh
npm test -- --runInBand __tests__/apiRefreshPolicy.test.ts __tests__/trackingViewModels.test.tsx
```

Observed RED:

```text
FAIL __tests__/trackingViewModels.test.tsx
  compensates when a GPS start resolves after logout invalidates tracking
  Expected native tracking false, received true
FAIL __tests__/apiRefreshPolicy.test.ts
  does not clear replacement credentials when a stale refresh write settles after reactivation
  Expected clearSessionIfTokens to be called; Number of calls: 0

Test Suites: 2 failed, 2 total
Tests:       2 failed, 16 passed, 18 total
```

The auth regression was then strengthened to exercise the real
`AsyncStorageSessionStore` queue with a deferred native `multiSet`; the
tracking assertion was narrowed to the observable invariant that late start
resolution adds a compensating stop, rather than relying on incidental
composition cleanup call counts.

### Implementation

- `src/config/api.ts` now hands stale refresh results to
  `clearSessionIfTokens(accessToken, refreshToken)` instead of broad session
  clearing. This rejects the stale request while preserving a newer session.
- `src/core/session/AsyncStorageSessionStore.ts` serializes token mutations and
  session clearing through one queue. Its conditional cleanup compares both
  token values inside that queue, so a replacement `setTokens` queued before a
  stale write settles is never removed. Token reads wait for queued token
  writes, preserving pair consistency.
- `src/core/session/SessionStore.ts` and API test fixtures carry the
  conditional-cleanup contract.
- `src/features/tracking/application/useTrackingSessionController.ts` attaches
  a completion guard to `gps.startTracking()` and calls `stopTracking()` when
  the session was invalidated while the native start was pending.

### GREEN and validation

Focused GREEN:

```sh
npm test -- --runInBand __tests__/apiRefreshPolicy.test.ts __tests__/trackingViewModels.test.tsx
# Test Suites: 2 passed, 2 total
# Tests:       18 passed, 18 total
```

Additional affected-boundary proof:

```sh
npm test -- --runInBand __tests__/apiRefreshPolicy.test.ts __tests__/corePlatformAdapters.test.ts
# Test Suites: 2 passed, 2 total
# Tests:       16 passed, 16 total

npm test -- --runInBand __tests__/corePlatformAdapters.test.ts __tests__/authService.contract.test.ts __tests__/sessionBoundary.test.ts __tests__/apiRefreshPolicy.test.ts __tests__/trackingViewModels.test.tsx __tests__/deliveryTrackingIntegration.test.ts __tests__/mainMapProjection.test.ts __tests__/deliveryStateProjection.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx
# Test Suites: 9 passed, 9 total
# Tests:       69 passed, 69 total
```

Repository and host-only gates:

```sh
npm run verify
# typecheck: exit 0
# lint: exit 0
# Architecture boundary check passed.
# Jest: 86 suites / 448 tests passed

npm test -- --runInBand --detectOpenHandles
# 86 suites / 448 tests passed

npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles
# 1 suite / 5 tests passed

git diff --check
# exit 0
```

No emulator, simulator, native smoke, or device tests were run. Remaining risk
is limited to native GPS/provider behavior outside the host-testable controller
and storage ordering exercised here.

## Fix round 3 — stale GPS completion cannot stop a replacement owner

### Re-review finding addressed

The round-2 controller fix correctly compensated when a pending GPS start
resolved after logout invalidated its session. However, its completion callback
only checked the old effect's `sessionInvalidated` flag. If logout/reactivation
or another effect rebind installed a replacement tracking session before that
promise settled, the old callback called the shared `gps.stopTracking()` and
stopped the replacement GPS resource.

### RED

Added a host-only regression before changing production code in
`__tests__/trackingViewModels.test.tsx`. The test uses one deferred old
`startTracking()` promise, invokes the old synchronous session boundary,
unmounts/rebinds the controller, starts a replacement session, and resolves
the old promise only after the replacement is active.

```sh
npm test -- --runInBand __tests__/trackingViewModels.test.tsx -t 'stale GPS start stop a replacement'
```

Observed RED:

```text
FAIL __tests__/trackingViewModels.test.tsx
  tracking ViewModels
    ✕ does not let a stale GPS start stop a replacement tracking session (11 ms)

  Expected: 2
  Received: null
    at __tests__/trackingViewModels.test.tsx:420:33

Test Suites: 1 failed, 1 total
Tests:       6 skipped, 1 failed, 7 total
```

### Implementation

- `useTrackingSessionController.ts` now keeps a component-lifetime
  `gpsOwnerGeneration` ref.
- Each foreground `startTracking()` claims a new generation. Its completion
  may compensate with `stopTracking()` only when the session is invalidated
  and that captured generation is still the current GPS owner.
- A replacement start therefore supersedes the old generation before the old
  promise settles. Synchronous logout stop, late GPS callback gating,
  foreground admission, transport behavior, hook cleanup, and all auth fixes
  remain unchanged.

### GREEN and validation

Focused regression after implementation:

```sh
npm test -- --runInBand __tests__/trackingViewModels.test.tsx -t 'stale GPS start stop a replacement'
# Test Suites: 1 passed, 1 total
# Tests:       6 skipped, 1 passed, 7 total
```

Focused tracking/platform coverage:

```sh
npm test -- --runInBand __tests__/trackingViewModels.test.tsx __tests__/deliveryTrackingIntegration.test.ts __tests__/trackingApplicationBoundaries.test.ts __tests__/corePlatformAdapters.test.ts
# Test Suites: 4 passed, 4 total
# Tests:       31 passed, 31 total
```

Repository host-only gates:

```sh
npm run typecheck
# exit 0
npm run lint
# exit 0
npm run verify:architecture
# Architecture boundary check passed.
npm test -- --runInBand --detectOpenHandles
# 86 suites / 449 tests passed
npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles
# 1 suite / 5 tests passed
git diff --check
# exit 0
```

No emulator, simulator, native smoke, or device tests were run. Remaining risk
is limited to the native GPS/provider implementation, which is outside the
host-only scope; the controller's existing GPS lifecycle behavior is covered by
the focused tests above.
