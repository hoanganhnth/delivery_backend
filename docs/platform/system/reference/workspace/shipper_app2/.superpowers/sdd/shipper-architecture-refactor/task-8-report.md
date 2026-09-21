# Task 8 post-review fix wave report

## Outcome

Implemented the Task 8 fix wave in `/Users/a/Documents/private/delivery/.worktrees/shipper-architecture-refactor` from the required starting HEAD `d9fb36c8c9cf007e92292fdf47234111e03c4a05`.

The changes preserve the Gateway REST/WebSocket routes and payload shape, Bearer authentication, FCM wake-only recovery, COD validation, delivery transitions, batch contracts, and the hidden MVP capability policy. No backend/API endpoint or contract was changed.

## Files changed

Application/composition:

- `src/app/bootstrap/AppInitializer.ts`: awaits active-delivery recovery and hydrates each unique non-empty batch ID.
- `src/app/composition/coordinators/DeliveryRecoveryCoordinator.tsx`: performs the same batch hydration after foreground recovery, including retry on a later recovery event.
- `src/app/composition/coordinators/TrackingSessionCoordinator.tsx`: subscribes to debug Gateway changes and drives socket URL state through the existing tracking lifecycle so URL changes disconnect/reconnect cleanly.
- `src/app/composition/overlays/MatchFoundOverlay.tsx`: reads the current composition GPS snapshot and supplies `currentLat`/`currentLng` to accept.
- `src/app/composition/viewmodels/useMainMapScreenViewModel.ts`: selects the next batch delivery from hydrated route items or `pickupSequence`/`dropoffSequence`, and orders map stops by the active authoritative route position.
- `src/app/composition/screens/DeliverySuccessScreen.tsx`: new composition-owned navigation adapter for the success route.
- `src/app/composition/screens/OrderDetailScreen.tsx`: renders an explicit not-found state for unknown historical deliveries.
- `src/app/navigation/AppNavigator.tsx` and `navigationTypes.ts`: mount/type the composition success screen.

Delivery feature/state:

- `src/features/delivery/application/deliveryCommands.ts`: adds unique batch ID extraction and avoids dispatching hydration after the accept session has been invalidated.
- `src/features/delivery/state/currentDeliverySlice.ts`: adds request tombstones for accept/status/reject/cancel, invalidates competing work at cancellation, ignores stale completions, and keeps batch hydration failures in per-batch state without changing a committed accept into a rejected accept.
- `src/features/delivery/state/deliveryRecoverySlice.ts` and `batchSlice.ts`: require an active matching request before applying recovery/snapshot completions.
- `src/features/delivery/state/deliveryStateProjection.ts`: removes `batchSequence` from active-route authority and uses route positions when available.
- `src/features/delivery/presentation/contracts/OrderDetailContract.ts`: restores canonical IDs/labels: distance, vehicle, restaurant-wait, customer-unreachable, and other.
- `src/features/delivery/presentation/viewmodels/useOrderDetailViewModel.ts`: preserves string rejection messages using `extractErrorMessage`.
- `DeliverySuccessContract.ts`, `DeliverySuccessRoute.tsx`, and `useDeliverySuccessViewModel.ts`: move navigation knowledge out of the feature route/ViewModel.

Presentation immutability:

- `MapCanvasContract.ts`: makes nested route, coordinate, leg, step, waypoint, and batch-stop fields readonly.
- `MapCanvasView.tsx`: copies readonly coordinates at the native Mapbox boundary.
- `NotificationsContract.ts`, `notificationPresenter.ts`, and `useNotificationsViewModel.ts`: make notification state readonly and freeze projected items/collections.

Tests were updated/added in `__tests__/MatchFoundPopup.test.tsx`, `appInitializer.test.ts`, `deliveryRecoveryCoordinator.test.tsx`, `deliveryService.contract.test.ts`, `currentDeliverySlice.test.ts`, `deliveryStateSlices.test.ts`, `deliveryStateProjection.test.ts`, `mainMapProjection.test.ts`, `deliveryTrackingIntegration.test.ts`, `trackingViewModels.test.tsx`, `navigationContracts.test.ts`, `OrderDetailViewModel.test.tsx`, `debugRuntime.test.ts`, `task6Presenters.test.ts`, and related fixtures.

## Root causes addressed

1. Accept composition only forwarded offer fields; it never selected the current location state.
2. Bootstrap/foreground recovery fetched active rows but did not fan out snapshot hydration. Hydration also wrote its error to the shared delivery error, conflating recoverable additive hydration with accept failure.
3. Map selection and batch stop sorting treated `batchSequence` as route authority instead of the backend route stop positions.
4. Reducers accepted fulfilled/rejected async actions when the corresponding pending request had already been reset or cancelled. Nullable request IDs made a reset state permissive.
5. `DeliverySuccessRoute` imported React Navigation and app navigation types directly; `AppNavigator` mounted the feature route.
6. Cancellation options had drifted from the historical canonical implementation.
7. Order-detail feedback handled only `Error` objects and replaced string reject payloads with a generic fallback.
8. `DebugRuntime` already exposed a gateway subscription, but composition did not consume it, so tracking lifecycle retained the old URL.
9. Nested map and notification presentation contracts were mutable even where presenters copied some values.

## TDD RED evidence

Focused tests were written before each production fix and run to confirm the expected failure:

- `npx jest __tests__/MatchFoundPopup.test.tsx __tests__/appInitializer.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx --runInBand`
  - RED: accept received `{ orderId, action, batchId }` without GPS; initializer dispatched 7 actions instead of the hydration-aware expectation; recovery made 0 snapshot calls.
- `npx jest __tests__/mainMapProjection.test.ts __tests__/currentDeliverySlice.test.ts __tests__/deliveryStateSlices.test.ts --runInBand`
  - RED: `selectAuthoritativeMapDelivery` was absent; stale accept/status/batch completions repopulated reset state; nullable reset request IDs allowed stale recovery/snapshot helpers.
- `npx jest __tests__/navigationContracts.test.ts __tests__/OrderDetailViewModel.test.tsx __tests__/trackingViewModels.test.tsx --runInBand`
  - RED: canonical cancellation IDs/options were missing; string rejection showed the generic message; no reconnect to the new Gateway URL; success composition screen was absent.
- `npx jest __tests__/task6Presenters.test.ts --runInBand`
  - RED: notification projection item was not frozen.
- `npx jest __tests__/currentDeliverySlice.test.ts --runInBand`
  - RED: an older status completion re-added a delivery after cancellation.

## TDD GREEN evidence

- `npx jest __tests__/MatchFoundPopup.test.tsx __tests__/appInitializer.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx --runInBand`
  - GREEN: 3 suites, 9 tests passed.
- `npx jest __tests__/mainMapProjection.test.ts __tests__/currentDeliverySlice.test.ts __tests__/deliveryStateSlices.test.ts --runInBand && npx tsc --noEmit`
  - GREEN: 3 suites, 16 tests passed; typecheck passed.
- `npx jest __tests__/navigationContracts.test.ts __tests__/OrderDetailViewModel.test.tsx __tests__/trackingViewModels.test.tsx --runInBand`
  - GREEN after boundary/cancellation/debug fixes: 3 suites, 7 tests passed.
- `npx jest __tests__/task6Presenters.test.ts __tests__/currentDeliverySlice.test.ts --runInBand && npx tsc --noEmit`
  - GREEN: 2 suites, 16 tests passed; typecheck passed.

## Focused validation

Final focused validation before the whole-repository run:

`npx jest __tests__/deliveryService.contract.test.ts __tests__/task6Presenters.test.ts __tests__/presentationContracts.test.ts __tests__/navigationContracts.test.ts __tests__/OrderDetailViewModel.test.tsx __tests__/trackingViewModels.test.tsx __tests__/mainMapProjection.test.ts __tests__/currentDeliverySlice.test.ts __tests__/deliveryStateSlices.test.ts __tests__/appInitializer.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx __tests__/MatchFoundPopup.test.tsx --runInBand`

Result: 12 suites passed, 55 tests passed.

`npm run verify:architecture` passed with `Architecture boundary check passed.`

`npm run lint` passed with no errors or warnings.

## Full verification

Command:

`npm run verify`

Result:

- `tsc --noEmit`: passed.
- `eslint .`: passed with no errors or warnings.
- `verify:architecture`: passed.
- Jest: **68 suites passed, 224 tests passed, 0 failed**.

Required final command:

`npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles`

Result: **1 suite passed, 5 tests passed**, with no open-handle failure.

## Self-review and concerns

- `git diff --check` passed.
- The architecture checker passed, and the final source scan found no feature-to-feature import or DeliverySuccess feature route/ViewModel app-navigation import.
- Map/state/composition code no longer uses `batchSequence` as route authority; the legacy delivery-history presentation DTO still exposes its historical display field, but it is not used for active map routing.
- Batch snapshot hydration is additive and retryable. If a snapshot remains unavailable and active rows do not contain route positions, the projection fails closed rather than inventing route order.
- Native device smoke checks were not run because this checkout had no running emulator/device session; native-only behavior remains covered by the existing adapter/contract tests and the full verification suite.

Commit: `fix: close Task 8 shipper architecture gaps` (final commit hash is reported by the task handoff).

## Round 1 reviewer-fix report

### Scope and root causes

Round 1 started from `2c1e3dc fix: close Task 8 shipper architecture gaps`.

- Cancellation invalidation was a one-time clear of request IDs at cancellation start. Status, recovery, offer, or batch work that entered afterward could install a fresh ID and complete after a successful cancellation.
- Active-batch projection fell through to `lastAcceptedDelivery`/the first active row when no hydrated route items or row route positions existed, which invented a map target.
- `applyBatchSnapshot` sorted active items by pickup position without applying the delivery-status stop policy.
- Map contract readonly coverage omitted nested scalar fields even though coordinates and collections were copied.
- Batch accept and debug tests did not assert the complete batch payload or exercise the real persisted Gateway subscription lifecycle.

### Changed files

- `src/features/delivery/state/currentDeliverySlice.ts`: added monotonic `assignmentGeneration`, cancellation tombstone state, generation-bound request IDs for accept/status/reject/cancel/recovery/offer/batch, and invalidation at cancellation success. Cancellation failure leaves the assignment usable and permits new requests.
- `src/features/delivery/state/batchSlice.ts`: records request generation and derives `activeStopId` through `deriveNextStop`, selecting pickup for `ASSIGNED`, drop-off for `PICKED_UP`/`DELIVERING`, and null for no valid stop.
- `src/features/delivery/state/deliveryStateProjection.ts`: preserves only authoritative route-based batch targets; active batches without route metadata now return null, and legacy fallback is limited to non-batch deliveries.
- `src/app/composition/viewmodels/useMainMapScreenViewModel.ts`: returns null for an active batch with no authoritative hydrated/row route metadata instead of falling back.
- `src/features/tracking/presentation/contracts/MapCanvasContract.ts`: marks all nested geometry, coordinate, leg, step, waypoint, route, and batch-stop fields readonly; existing deep copying remains intact.
- `__tests__/currentDeliverySlice.test.ts`: covers requests started after cancellation, successful cancellation tombstones, failed-cancellation recovery, and post-reset races.
- `__tests__/deliveryStateProjection.test.ts` and `__tests__/mainMapProjection.test.ts`: cover fail-closed active-batch projection/map selection.
- `__tests__/deliveryStateSlices.test.ts`: covers status-aware interleaved batch stop selection and terminal nulling.
- `__tests__/presentationContracts.test.ts`: asserts nested MapCanvas scalar readonly declarations.
- `__tests__/deliveryService.contract.test.ts`: covers canonical batch accept endpoint/payload with batch ID, notes, and GPS unchanged.
- `__tests__/trackingViewModels.test.tsx`: uses a real `DebugRuntime`, changes and resets the persisted Gateway origin, asserts exactly one disconnect and one reconnect per change/reset, verifies the actual URL sequence, and verifies subscription cleanup on unmount.

### TDD RED evidence

Tests were written before each production change and run against the unfixed code:

`npx jest __tests__/currentDeliverySlice.test.ts __tests__/deliveryStateProjection.test.ts __tests__/mainMapProjection.test.ts __tests__/deliveryStateSlices.test.ts __tests__/presentationContracts.test.ts __tests__/deliveryService.contract.test.ts __tests__/trackingViewModels.test.tsx --runInBand`

Observed RED output: 5 suites failed. The cancellation race re-added the delivery after post-cancel status/recovery/batch work; both projection tests returned the batch delivery instead of null; `applyBatchSnapshot` returned delivery 8 instead of authoritative delivery 9; and readonly assertions failed at `readonly type: string`.

### TDD GREEN evidence

`npx jest __tests__/currentDeliverySlice.test.ts __tests__/deliveryStateProjection.test.ts __tests__/mainMapProjection.test.ts __tests__/deliveryStateSlices.test.ts __tests__/presentationContracts.test.ts __tests__/deliveryService.contract.test.ts __tests__/trackingViewModels.test.tsx --runInBand && npx tsc --noEmit`

Output: **7 suites passed, 44 tests passed; typecheck passed**.

The additional failure-path regressions were then verified by the same focused current-delivery suite: **1 suite passed, 13 tests passed** after the cancellation generation/tombstone implementation.

### Focused tests

`npx jest __tests__/currentDeliverySlice.test.ts __tests__/deliveryStateProjection.test.ts __tests__/mainMapProjection.test.ts __tests__/deliveryStateSlices.test.ts __tests__/presentationContracts.test.ts __tests__/deliveryService.contract.test.ts __tests__/trackingViewModels.test.tsx --runInBand`

Output: **7 suites passed, 44 tests passed**.

### Full verification

`npm run verify`

Output summary: `tsc --noEmit` passed; `eslint .` passed with no errors/warnings; `verify:architecture` passed with `Architecture boundary check passed.`; Jest **68 suites passed, 231 tests passed, 0 failed**.

`npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles`

Output summary: **1 suite passed, 5 tests passed**, with no open-handle failure.

### Self-review and concerns

- `git diff --check` passed before commit; no unrelated source areas were changed.
- The cancellation gate is generation-bound and clears all competing request IDs/batches on successful cancellation; a rejected cancellation leaves the current assignment and permits genuinely new requests.
- A batch with no authoritative route metadata now fails closed. This intentionally means `lastAcceptedDelivery` and the map target can be null while the committed accept row remains in `activeDeliveries`; hydration failure cannot reject the committed accept.
- Batch accept uses the existing canonical `/api/deliveries/batch/accept` payload shape; no backend/API contract was changed.
- The real debug lifecycle test confirms change/reset reconnect behavior and unsubscribe cleanup. Native device smoke checks were not available in this checkout; existing native adapter/contract tests and full verify pass.

## Round 2 reviewer-fix report

### Scope and root cause

The round-2 re-review identified a stale fallback in `src/features/delivery/state/deliveryStateProjection.ts`: when route derivation returned no valid non-terminal stop, `next?.id ?? current.activeStopId` could preserve an obsolete active-stop authority. The projection now assigns `next?.id ?? null`, so terminal or invalid current hydrated routes always clear the stop while valid route-derived stops remain unchanged.

### Changed files

- `src/features/delivery/state/deliveryStateProjection.ts`: clear `activeStopId` when no authoritative next delivery exists.
- `__tests__/deliveryStateProjection.test.ts`: add a regression covering a prior active-stop value followed by a terminal hydrated batch projection.

### TDD evidence

Regression test was added before the production edit and run with:

`npx jest __tests__/deliveryStateProjection.test.ts --runInBand`

Output: **4 tests passed**. This checkout rebuilds each projected batch from `createBatchState`, so the supplied prior `activeStopId` is not copied into the rebuilt object; consequently the regression is already fail-closed before the one-line defensive fix. The test still covers the required terminal hydrated projection, and the production change closes the reported stale fallback without changing state-generation or cancellation behavior.

After the minimal fix, the focused GREEN command was:

`npx jest __tests__/deliveryStateProjection.test.ts --runInBand && npx tsc --noEmit && git diff --check`

Output: projection suite **1 suite / 4 tests passed**; TypeScript passed; `git diff --check` passed.

### Focused and full validation

- `npx jest __tests__/deliveryStateProjection.test.ts --runInBand`: **1 suite, 4 tests passed**.
- `npx tsc --noEmit`: passed.
- `npm run verify`: typecheck, lint, architecture boundary check, and **68 suites / 232 tests passed**.
- `npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles`: **1 suite / 5 tests passed**, with no open-handle failure.

### Self-review and concerns

- `git diff --check` passed and the round contains only the projection implementation, its focused regression test, and this report append.
- Valid current route stops continue to win; terminal/invalid current routes now produce `activeStopId: null`.
- No backend/API contracts, generation guards, cancellation behavior, or unrelated round-1 changes were modified.
- Concern: the isolated projection path does not currently copy a prior `activeStopId`, so the old fallback is not observable through this public helper alone; the requested null-coalescing fix is nevertheless applied exactly at the reviewed merge point.

## Round 3 reviewer-fix report

### Scope and root cause

The round-2 regression was weak because projected batches were rebuilt with `activeStopId: null`, meaning the old fallback could not observe stale metadata. The projection merge now copies `previous.activeStopId` into the current intermediate batch as metadata before deriving the authoritative route stop. The assignment remains `next?.id ?? null`, so a terminal/invalid or route-less current batch clears stale authority, while a valid route-derived stop replaces it.

### Changed files

- `src/features/delivery/state/deliveryStateProjection.ts`: preserve prior `activeStopId` in the intermediate batch metadata before route recomputation.
- `__tests__/deliveryStateProjection.test.ts`: seed `activeStopId: 1`, assert a route-less active batch clears it, and assert a valid route-derived stop wins with ID `2`.

### TDD evidence

Because the one-line production fix was already present at the start of this round, the strengthened test was first run against the corrected baseline:

`npx jest __tests__/deliveryStateProjection.test.ts --runInBand`

Output: **1 suite, 4 tests passed**. As expected, before copying prior metadata the test could not distinguish the old and corrected fallback.

After adding the metadata copy, a temporary mutant restored the old expression `next?.id ?? current.activeStopId`. The mutant was not retained. RED command:

`npx jest __tests__/deliveryStateProjection.test.ts --runInBand`

Output: **1 suite failed, 3 tests passed, 1 failed**; the regression expected `null` but received stale `1` at `__tests__/deliveryStateProjection.test.ts:95`.

The corrected expression `next?.id ?? null` was restored, then GREEN validation ran:

`npx jest __tests__/deliveryStateProjection.test.ts __tests__/mainMapProjection.test.ts __tests__/currentDeliverySlice.test.ts __tests__/deliveryStateSlices.test.ts --runInBand && npx tsc --noEmit && git diff --check`

Output: **4 suites, 27 tests passed**; typecheck passed; `git diff --check` passed.

### Full validation

`npm run verify`

Output: typecheck, lint, and architecture boundary check passed; Jest **68 suites passed, 232 tests passed, 0 failed**.

### Self-review and concerns

- The temporary mutant was restored; final production behavior is `next?.id ?? null`.
- Valid route-derived stops continue to take precedence over copied metadata; no-route and terminal/invalid routes clear `activeStopId`.
- Only the projection, its focused test, and this report were changed in round 3. No backend/API contracts or generation/cancellation behavior changed.
- The initial corrected-baseline run was necessarily green because the fix predated this round; the temporary mutant provided the required meaningful RED proof after seeding prior metadata.
