# Task 25 Implementer Report

## Outcome

Task 25 closes delivery domain/Redux-state leakage across delivery Route contracts. Delivery-specific immutable route input DTOs now live under the delivery presentation contracts, and app composition maps domain records and Redux state into fresh DTO values before crossing the Route boundary. Existing UI behavior, backend payloads, GPS injection, COD policy, navigation parameters, Redux action types, and user-visible labels remain unchanged.

Commit subject: `refactor(task-25): isolate delivery route inputs from domain state`

## Implementation summary

- Added `DeliveryRouteInputContract.ts` with the exact status, route input, command input, read-model, and completion-result interfaces required by Task 25.
- Added pure `deliveryRouteInputAdapter.ts` mapping active delivery, offer active-delivery state, order detail, delivery history, and successful command payloads. Outputs are fresh shallow-copied frozen values; history items and the history collection are frozen as well.
- Updated the four delivery Route contracts to consume DTOs and command/result contracts rather than `Delivery`, `AcceptDeliveryRequest`, `ShipperDeliveryTransition`, or `HistoryDeliveryState`.
- Preserved `DeliveryHistoryDeliverySnapshot` as a type re-export alias to the minimal history DTO.
- Updated active/detail/history presenters to consume route DTOs while keeping delivery policies in `features/delivery/domain`.
- Updated the four app-composition ViewModels to map selector values and successful Redux payloads at the composition boundary. GPS coordinates remain injected only in the offer command adapter, and the dispatched accept/status payloads remain unchanged.
- Extended the architecture checker and fixtures for direct and barrel delivery domain/state imports from delivery presentation contracts, while allowing local route DTOs, core ports, and application ports.

## Exact files changed

Created:

- `src/features/delivery/presentation/contracts/DeliveryRouteInputContract.ts`
- `src/app/composition/adapters/deliveryRouteInputAdapter.ts`
- `__tests__/deliveryRouteInputBoundaries.test.ts`

Modified:

- `src/features/delivery/presentation/contracts/ActiveDeliveryRouteContract.ts`
- `src/features/delivery/presentation/contracts/MatchFoundRouteContract.ts`
- `src/features/delivery/presentation/contracts/OrderDetailRouteContract.ts`
- `src/features/delivery/presentation/contracts/DeliveryHistoryContract.ts`
- `src/features/delivery/presentation/contracts/DeliveryHistoryRouteContract.ts`
- `src/features/delivery/presentation/index.ts`
- `src/features/delivery/presentation/activeDeliveryPresenter.ts`
- `src/features/delivery/presentation/detailPresenter.ts`
- `src/features/delivery/presentation/historyPresenter.ts`
- `src/features/delivery/presentation/viewmodels/useActiveDeliveryViewModel.ts`
- `src/app/composition/viewmodels/useActiveDeliverySheetScreenViewModel.ts`
- `src/app/composition/viewmodels/useMatchFoundOverlayViewModel.ts`
- `src/app/composition/viewmodels/useOrderDetailScreenViewModel.ts`
- `src/app/composition/viewmodels/useDeliveryHistoryScreenViewModel.ts`
- `scripts/verify-architecture.mjs`
- `__tests__/verifyArchitecture.test.ts`
- `__tests__/SecondaryViewModels.test.tsx`
- `__tests__/navigationContracts.test.ts`

## TDD evidence

### RED

The boundary and mapper tests were added before the production DTO/adapter implementation. After correcting test-harness syntax and fixture placement, the focused RED run failed for the intended missing behavior:

```text
FAIL __tests__/verifyArchitecture.test.ts
  4 delivery route-contract fixture cases expected status 1, received 0

FAIL __tests__/deliveryRouteInputBoundaries.test.ts
  Test suite failed to run
  Cannot find module '../src/app/composition/adapters/deliveryRouteInputAdapter'
```

The architecture failures demonstrated that direct and barrel delivery domain/state imports were not yet rejected; the module failure demonstrated that the mapper contract had no implementation.

### GREEN

After the minimal implementation and boundary wiring:

```text
PASS __tests__/deliveryRouteInputBoundaries.test.ts
PASS __tests__/verifyArchitecture.test.ts
PASS __tests__/task6Presenters.test.ts
PASS __tests__/SecondaryViewModels.test.tsx
PASS __tests__/OrderDetailViewModel.test.tsx

Test Suites: 5 passed, 5 total
Tests:       74 passed, 74 total
```

## Validation output

All validation was host-only; no emulator, simulator, native smoke, or device test was run.

```text
npm run typecheck
tsc --noEmit                         PASS

npm run lint
eslint .                             PASS

npm run verify:architecture
Architecture boundary check passed. PASS

npm run verify
82 test suites passed, 339 tests passed

npx jest --runInBand --detectOpenHandles
82 test suites passed, 339 tests passed

npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles
1 test suite passed, 5 tests passed

git diff --cached --check
PASS (no whitespace errors)
```

## Self-review

- Confirmed all four delivery Route contracts no longer import a delivery `domain` or `state` module.
- Confirmed DTO adapter outputs are fresh values, omit non-contract fields, and are frozen; history collection values are independently frozen.
- Confirmed successful status and accept command results expose `tripEarnings`/`orderId` only at the Route boundary.
- Confirmed domain status/COD/offer policies remain in `features/delivery/domain`.
- Confirmed no Gateway, WebSocket, GPS, Bearer, FCM, COD, Redux action type/payload, navigation contract, label, or cancellation behavior changes.
- The architecture guard is intentionally scoped to delivery presentation contracts for this task; shipper/notifications contract migrations were explicitly out of scope.

## Concerns and unrun checks

No unresolved implementation concerns. Native/device behavior was intentionally not exercised because Task 25 prohibits emulator, simulator, native smoke, and device tests; the existing host-level tests cover the changed contracts and composition behavior.

## Fix report — round 1 review

### Review finding addressed

Generalized `scripts/verify-architecture.mjs` so every resolved import from
`features/<feature>/presentation/contracts/**` to the same feature's
`domain/**` or `state/**` is rejected. The check applies to type-only imports,
direct modules, and resolved local barrels. Delivery keeps its existing error
message; other features use the generic equivalent. Imports to local route
inputs, core ports, and application ports remain allowed.

### TDD evidence

#### RED

Added four auth fixtures covering direct and barrel imports for both domain and
state before changing the guard, plus an allowed-dependency fixture. The
focused regression command failed because the current delivery-only guard
returned status 0 for all four auth violations:

```text
npx jest __tests__/verifyArchitecture.test.ts --runInBand

FAIL __tests__/verifyArchitecture.test.ts
4 non-delivery route-contract tests failed
Expected: 1
Received: 0
Test Suites: 1 failed, 56 passed, 57 total
Tests:       4 failed, 56 passed, 60 total
```

#### GREEN

After replacing the delivery-only predicates with same-feature generic
predicates:

```text
npx jest __tests__/verifyArchitecture.test.ts --runInBand

PASS __tests__/verifyArchitecture.test.ts
Test Suites: 1 passed, 1 total
Tests:       60 passed, 60 total
```

### Files changed

- `scripts/verify-architecture.mjs` — generic contract/domain-state guard,
  preserving the delivery message.
- `__tests__/verifyArchitecture.test.ts` — auth direct/barrel domain/state
  rejection fixtures and valid local/core/application import fixture.
- `.superpowers/sdd/shipper-architecture-refactor/task-25-report.md` — this
  fix report section.

### Additional host validation

```text
npm run typecheck                         PASS
npm run lint                              PASS
npx prettier --check ...                  PASS
git diff --check                          PASS
```

`npm run verify:architecture` now correctly detects four existing non-delivery
contract leaks that were outside the authorized delivery DTO migration:

```text
src/features/notifications/presentation/contracts/NotificationsRouteContract.ts
src/features/shipper/presentation/contracts/DocumentsRouteContract.ts
src/features/shipper/presentation/contracts/ShipperProfileRouteContract.ts
src/features/shipper/presentation/contracts/ShipperRatingRouteContract.ts
```

Those existing contracts still import their own state/domain modules. Migrating
them would require a separate notifications/shipper route DTO refactor, so no
runtime or unrelated feature behavior was changed in this review fix. No
emulator, simulator, native smoke, or device test was run.

## Fix report — round 2

### Outcome

Applied the progress ruling that the four existing leaks were load-bearing for
Task 25 completion. Shipper documents, profile, and ratings Route contracts now
use feature-local readonly route inputs and command inputs. Notifications now
uses a minimal readonly notification input/read model. App composition maps
domain and Redux records into fresh frozen values before passing them to Routes.
The generic guard remains unchanged and the mandatory architecture check is
green.

### TDD evidence

#### RED

Added the focused boundary/mapper suite before creating the new DTOs or
composition adapter:

```text
npx jest __tests__/shipperNotificationRouteInputBoundaries.test.ts --runInBand

FAIL __tests__/shipperNotificationRouteInputBoundaries.test.ts
Test suite failed to run
Cannot find module '../src/app/composition/adapters/shipperNotificationRouteInputAdapter'
Tests:       0 total
```

#### GREEN

After adding the feature-local contracts, pure adapters, and route/presenter/
ViewModel wiring:

```text
npx jest __tests__/shipperNotificationRouteInputBoundaries.test.ts \
  __tests__/task6Presenters.test.ts \
  __tests__/ShipperViewModels.test.tsx \
  __tests__/DocumentsViewModel.test.tsx \
  __tests__/SecondaryViewModels.test.tsx --runInBand

Test Suites: 5 passed, 5 total
Tests:       15 passed, 15 total

npm run verify:architecture
Architecture boundary check passed.
```

### Files changed

Created:

- `src/features/shipper/presentation/contracts/ShipperRouteInputContract.ts`
- `src/features/notifications/presentation/contracts/NotificationsRouteInputContract.ts`
- `src/app/composition/adapters/shipperNotificationRouteInputAdapter.ts`
- `__tests__/shipperNotificationRouteInputBoundaries.test.ts`

Modified:

- `src/features/shipper/presentation/contracts/DocumentsRouteContract.ts`
- `src/features/shipper/presentation/contracts/ShipperProfileRouteContract.ts`
- `src/features/shipper/presentation/contracts/ShipperRatingRouteContract.ts`
- `src/features/notifications/presentation/contracts/NotificationsRouteContract.ts`
- `src/features/shipper/presentation/profilePresenter.ts`
- `src/features/notifications/presentation/notificationPresenter.ts`
- `src/features/shipper/presentation/viewmodels/useDocumentsViewModel.ts`
- `src/app/composition/viewmodels/useDocumentsScreenViewModel.ts`
- `src/app/composition/viewmodels/useShipperProfileScreenViewModel.ts`
- `src/app/composition/viewmodels/useShipperRatingScreenViewModel.ts`
- `src/app/composition/viewmodels/useNotificationsScreenViewModel.ts`
- `src/features/shipper/presentation/index.ts`
- `src/features/notifications/presentation/index.ts`
- `.superpowers/sdd/shipper-architecture-refactor/task-25-report.md`

The existing profile, ratings, and notifications feature ViewModels now consume
the DTOs through their Route props and presenter signatures; no domain/state
imports were added to presentation contracts, presenters, or feature
ViewModels.

### Validation

All validation was host-only:

```text
npm run typecheck                         PASS
npm run lint                              PASS
npx prettier --check <new files>          PASS
git diff --check                          PASS
npm run verify                            PASS — 83 suites, 346 tests
npx jest --runInBand --detectOpenHandles  PASS — 83 suites, 346 tests
npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles
                                           PASS — 1 suite, 5 tests
```

The adapters copy only the fields consumed by the route contracts, freeze each
DTO and each collection, preserve the existing document update payload shape,
and leave labels, feedback calls, notification filtering/read/delete flows,
navigation ports, and backend contracts unchanged. No emulator, simulator,
native smoke, or device test was run by instruction.

### Concerns

No unresolved implementation concerns. Native/device behavior remains
intentionally unrun because this fix round is host-only.

## Final whole-branch review fix wave

### Outcome

Closed all three Important findings from the final whole-branch review in one
coherent fix wave, starting from commit `4d52390`:

1. Restored the merge-base architecture layer matrix in
   `scripts/verify-architecture.mjs` while retaining the newer resolved-graph,
   feature-edge, cycle, composition-surface, route-contract, command,
   Mapbox, tracking-policy, and coordinator rules.
2. Made shipper-status recovery require the fulfilled action to match the
   currently applied `statusRequestId`, while preserving the online and
   canonical shipper-id guards and existing recovery payloads.
3. Invalidated delivery/shipper/history request IDs at logout pending, added
   session-generation/request-ID checks to delivery listener continuations,
   and prevented delayed completion cleanup from dispatching into a later
   session.

No Gateway REST/WebSocket, Bearer, FCM wake-only, COD, delivery-transition,
batch recovery, navigation, or hidden MVP contract was changed.

### Files changed

Modified:

- `scripts/verify-architecture.mjs`
- `__tests__/verifyArchitecture.test.ts`
- `__tests__/deliveryTrackingIntegration.test.ts`
- `src/app/composition/integrations/OfferOverlayIntegration.ts`
- `src/app/composition/integrations/deliveryRecoveryIntegration.ts`
- `src/app/composition/integrations/deliveryRouteIntegration.ts`
- `src/app/composition/integrations/deliveryStatusIntegration.ts`
- `src/features/delivery/state/currentDeliverySlice.ts`
- `src/features/delivery/state/historyDeliverySlice.ts`
- `src/features/shipper/state/shipperSlice.ts`

The architecture fixtures cover legacy roots, core, shared, domain, data,
state, feature platform, pure View, ViewModel, and Route rule groups, with
allowed dependency cases. Delivery integration fixtures cover stale online
status recovery, stale recovery route refresh, accept/status/recovery
completions during logout pending, delayed cleanup after a new session, and
the existing valid same-session paths.

### TDD evidence

#### Architecture RED

The new matrix fixtures were added before restoring the production guard. The
focused command produced the intended failures after fixture harness issues
were corrected:

```text
npx jest __tests__/verifyArchitecture.test.ts --runInBand --silent

FAIL __tests__/verifyArchitecture.test.ts (34.995 s)
Tests:       59 failed, 62 passed, 121 total
```

Representative failure:

```text
expect(received).toBe(expected)
Expected: 1
Received: 0
```

This showed the new core/shared/domain/data/state/platform/presentation
fixtures were not rejected before the guard restoration.

#### Architecture GREEN

```text
npx jest __tests__/verifyArchitecture.test.ts --runInBand --silent

PASS __tests__/verifyArchitecture.test.ts (34.381 s)
Test Suites: 1 passed, 1 total
Tests:       121 passed, 121 total
```

#### Delivery/session RED

The focused integration regression was run before the production request and
session guards:

```text
npx jest __tests__/deliveryTrackingIntegration.test.ts --runInBand --detectOpenHandles

FAIL __tests__/deliveryTrackingIntegration.test.ts
Tests:       5 failed, 10 passed, 15 total
```

The failures were the stale online status recovery, accept completion during
logout pending, status completion during logout pending, recovery completion
during logout pending, and delayed cleanup removing a same-ID delivery in the
new session. The observed stale-side-effect failure was:

```text
Expected number of calls: 0
Received number of calls: 1
1: 7
```

After adding the extra stale recovery-route fixture, the exact request-ID
guard was also proven RED:

```text
npx jest __tests__/deliveryTrackingIntegration.test.ts --runInBand --detectOpenHandles

FAIL __tests__/deliveryTrackingIntegration.test.ts
Tests:       1 failed, 15 passed, 16 total
```

That failure observed one unexpected directions call from the stale recovery
completion.

#### Delivery/session GREEN

```text
npx jest __tests__/deliveryTrackingIntegration.test.ts --runInBand --detectOpenHandles

PASS __tests__/deliveryTrackingIntegration.test.ts
Test Suites: 1 passed, 1 total
Tests:       16 passed, 16 total
```

The related five-suite regression run also passed:

```text
npx jest __tests__/verifyArchitecture.test.ts \
  __tests__/deliveryTrackingIntegration.test.ts \
  __tests__/deliveryRecoveryCoordinator.test.tsx \
  __tests__/sessionBoundary.test.ts \
  __tests__/currentDeliverySlice.test.ts --runInBand --detectOpenHandles

Test Suites: 5 passed, 5 total
Tests:       158 passed, 158 total
```

### Full validation evidence

All validation was host-only. The required `npm run verify` command passed:

```text
npm run verify

> appshipper@0.0.1 verify
> npm run typecheck && npm run lint && npm run verify:architecture && npm test -- --runInBand

> appshipper@0.0.1 typecheck
> tsc --noEmit

> appshipper@0.0.1 lint
> eslint .

> appshipper@0.0.1 verify:architecture
> node scripts/verify-architecture.mjs

Architecture boundary check passed.

Test Suites: 83 passed, 83 total
Tests:       413 passed, 413 total
```

The explicit full Jest handle-leak run, popup gate, and required checks also
passed:

```text
npm run typecheck && npm run lint && npm run verify:architecture && \
  npm test -- --runInBand --detectOpenHandles && \
  npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles && \
  git diff --check

Architecture boundary check passed.
Test Suites: 83 passed, 83 total
Tests:       413 passed, 413 total

PASS __tests__/MatchFoundPopup.test.tsx
Test Suites: 1 passed, 1 total
Tests:       5 passed, 5 total
```

The complete diff from `4d52390` was inspected before this report was
appended. It contained only the ten intended production/test files listed
above; no files outside the requested worktree were changed. No emulator,
simulator, native smoke, or device test was run.

### Decisions and concerns

- Local import checks use the current AST/resolved graph, including resolved
  barrels and type-only imports. External package rules inspect import
  specifiers. No broad allowlist was added.
- Logout pending advances the delivery assignment generation and clears
  account-scoped request IDs without changing the public Redux or API
  contracts. Listener effects compare the pre-action request ID and session
  generation; delayed completion cleanup performs a second generation check.
- The full host validation is green. Native, simulator, emulator, smoke, and
  device validation remains intentionally unrun and is the only validation
  limitation because the task explicitly prohibited it.
