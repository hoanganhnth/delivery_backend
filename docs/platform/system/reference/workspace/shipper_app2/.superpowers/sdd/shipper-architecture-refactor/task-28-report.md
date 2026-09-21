# Task 28 — State-authority and logout-race fix wave report

## Outcome

Implemented the coordinated shipper-app fix wave on branch
`refactor/shipper-architecture`, based on
`2d894e67260af9912354ac014af93d3b4e12db0d`.
The host-only regressions now pass, normalized delivery state remains authoritative
after delayed completion cleanup, logout pending is an admission barrier, and
notification request completions are invalidated at the boundary.

No Gateway REST/WebSocket, Bearer, FCM wake-only, COD, delivery-transition,
batch, navigation, or hidden-capability contract was changed.

## Exact files changed

Application/runtime:

- `src/app/store/index.ts`
- `src/app/store/sessionBoundarySlice.ts`
- `src/features/delivery/application/assignmentCommands.ts`
- `src/features/delivery/application/batchCommands.ts`
- `src/features/delivery/application/deliveryCommandTypes.ts`
- `src/features/delivery/application/historyCommands.ts`
- `src/features/delivery/application/offerCommands.ts`
- `src/features/delivery/application/recoveryCommands.ts`
- `src/features/delivery/state/currentDeliverySlice.ts`
- `src/features/delivery/state/deliveryStateProjection.ts`
- `src/features/notifications/application/index.ts`
- `src/features/notifications/application/notificationCommandTypes.ts`
- `src/features/notifications/application/notificationCommands.ts`
- `src/features/notifications/state/notificationSlice.ts`
- `src/features/shipper/application/index.ts`
- `src/features/shipper/application/shipperCommandTypes.ts`
- `src/features/shipper/application/shipperCommands.ts`

Tests:

- `__tests__/currentDeliverySlice.test.ts`
- `__tests__/deliveryRecoveryCoordinator.test.tsx`
- `__tests__/deliveryStateProjection.test.ts`
- `__tests__/secondaryStore.test.ts`
- `__tests__/sessionBoundary.test.ts`

## RED

Command:

```sh
npm test -- --runInBand __tests__/deliveryStateProjection.test.ts __tests__/currentDeliverySlice.test.ts __tests__/sessionBoundary.test.ts __tests__/secondaryStore.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx
```

Observed output before the production fix:

```text
FAIL __tests__/deliveryRecoveryCoordinator.test.tsx
FAIL __tests__/secondaryStore.test.ts
FAIL __tests__/sessionBoundary.test.ts
FAIL __tests__/deliveryStateProjection.test.ts
PASS __tests__/currentDeliverySlice.test.ts

Test Suites: 4 failed, 1 passed, 5 total
Tests:       5 failed, 30 passed, 35 total
```

The failures were the intended regressions: recovery was admitted after
logout pending, deferred notification results repopulated state, the session
flag was absent, and stale hydrated delivery 8 remained in the projected route.

## Implementation decisions

1. Added neutral `session.logoutPending` with initial value `false`.
   `rootReducer` sets it to `true` on `logoutUser.pending` and resets it to
   `false` on fulfilled/rejected/session-expired reset paths. Monotonic
   generation behavior remains unchanged.
2. Added `condition` admission guards to all delivery, delivery-history,
   notification, and shipper async commands. A command dispatched while
   logout is pending is condition-rejected before its repository is called.
   Existing action type strings and repository payloads are unchanged.
3. Notification logout-pending handling clears all request ids/maps while
   preserving the existing account-state reset on logout settle.
4. `clearCompletedDelivery` removes the completed id from both active rows and
   hydrated batch items. Projection now rebuilds hydrated batch items only from
   currently normalized entities, never from a missing embedded delivery.
   Valid hydrated items and route ordering remain intact.
5. The existing shared delivery `assignmentGeneration` was exercised with
   deferred recovery/status/batch completions in both pending-order cases:
   recovery-status-batch and batch-status-recovery. Both orderings are
   invalidated by the existing generation guard after the session boundary.
   No runtime guard semantics or workflow bus was added.

## GREEN and focused validation

Initial GREEN command:

```sh
npm test -- --runInBand __tests__/deliveryStateProjection.test.ts __tests__/currentDeliverySlice.test.ts __tests__/sessionBoundary.test.ts __tests__/secondaryStore.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx
```

Observed output:

```text
Test Suites: 5 passed, 5 total
Tests:       35 passed, 35 total
```

Final focused command after adding the full hydrate → DELIVERED → delayed
`clearCompletedDelivery(8)` regression:

```text
Test Suites: 5 passed, 5 total
Tests:       36 passed, 36 total
```

Additional required checks:

```sh
npm run typecheck                         # exit 0
npm run lint                              # exit 0
npm run verify:architecture               # exit 0; Architecture boundary check passed.
git diff --check                          # exit 0
```

Full verification:

```sh
npm run verify
```

```text
Architecture boundary check passed.
Test Suites: 85 passed, 85 total
Tests:       428 passed, 428 total
```

Full open-handle validation:

```sh
npx jest --runInBand --detectOpenHandles
```

```text
Test Suites: 85 passed, 85 total
Tests:       428 passed, 428 total
```

Popup gate:

```sh
npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles
```

```text
Test Suites: 1 passed, 1 total
Tests:       5 passed, 5 total
```

## Fix-wave iteration

The first GREEN run exposed a notification-slice parser delimiter mistake;
that was corrected before validation continued. Self-review then identified
that the exact delayed `clearCompletedDelivery` reproduction deserved a
slice-level regression in addition to the pure projection regression. The
regression was added, focused tests were rerun, and the full validation suite
was rerun on the final diff.

## Unresolved concerns

- Native, emulator, simulator, smoke, and device tests were intentionally not
  run because the task explicitly authorizes host-only validation. Native
  provider behavior therefore remains outside this proof boundary.
- No other unresolved host-side concern was observed. The final commit SHA is
  recorded below after commit.

## Commit

Final commit SHA: `ea51177b952d6973be5b0bad6d1746a330c95509`.

Commit message: `fix(shipper): harden session and delivery state races`.
