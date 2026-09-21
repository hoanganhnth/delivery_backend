# Task 23: Seal app-composition presentation boundaries

## Outcome

Task 23 is implemented in the shipper app worktree. App composition now consumes feature route contracts and stable Route components, feature ViewModel implementation files are not app-composition dependencies, map canvas projection is framework-free, and the obsolete app navigation-port barrel is removed.

## TDD evidence

- RED: Added `__tests__/compositionPresentationBoundaries.test.ts` before production boundary changes. The focused run failed as expected: 3 tests failed because the existing architecture guard returned status 0 for composition ViewModel, overlay, and shell imports of feature ViewModels; 2 allowed-contract tests passed.
- GREEN: Strengthened `scripts/verify-architecture.mjs` to detect direct, type-only, aliased, and presentation-barrel ViewModel imports from app composition ViewModels, overlays, and shell. The focused boundary suite then passed 5/5, and the additional `verifyArchitecture.test.ts` case passed.

## Implementation surfaces

- Added route contracts for auth, debug, delivery, notifications, shipper, and tracking under `src/features/*/presentation/contracts/*RouteContract.ts`.
- Moved feature navigation, command, and read-model port declarations out of ViewModel implementation files and made Routes/ViewModels consume the contracts.
- Added `src/features/tracking/presentation/mapCanvasPresenter.ts` and moved `createMapCanvasViewState` there; removed `useMainMapViewModel.ts` after migrating all production/test consumers.
- Narrowed feature presentation barrels to Route components and contracts; added missing stable delivery/auth Route exports.
- Added neutral `src/core/navigation/DeliveryNavigation.ts` for `DeliverySuccessParams`; migrated app navigation types and removed `src/app/navigation/navigationPorts.ts` and its barrel export.
- Updated affected architecture, route, navigation, map, presentation-contract, and ViewModel tests.

## Validation

- Focused architecture/presentation/navigation/map tests: 5 suites, 63 tests passed.
- `npm run typecheck`: passed.
- `npm run lint`: passed.
- `npm run verify:architecture`: passed.
- `npm run verify`: 80 suites, 321 tests passed.
- `npm test -- --runInBand --detectOpenHandles`: 80 suites, 321 tests passed; no open-handle diagnostics.
- Popup gate (`MatchFoundPopup.test.tsx` and `TopSheetPopup.test.tsx` with `--detectOpenHandles`): 2 suites, 6 tests passed.
- `git diff --check`: passed.
- No emulator, simulator, native smoke, device, or other native/device tests were run.

## Concerns

- The existing presentation-contract test initially failed because it assumed every contract file was import-free; it was updated to apply that rule only to pure view contracts, while RouteContract files intentionally import typed ports/read models.
- No independent reviewer/subagent was dispatched, per the explicit Task 23 instruction.

## Fix round 1/5 evidence

- Finding 1 fixed: `DeliveryHistoryRouteContract.ts` now imports `DeliverySuccessParams` from `src/core/navigation/DeliveryNavigation.ts` and uses it for `openSuccess(params: DeliverySuccessParams)`, removing the duplicate inline shape.
- Finding 2 fixed: the allowed-contract fixture in `__tests__/compositionPresentationBoundaries.test.ts` now imports `LoginRouteProps` from `features/auth/presentation/contracts/LoginRouteContract` while preserving pure view contract coverage.
- TDD RED: the navigation contract assertion failed against the prior inline `openSuccess` shape.
- TDD GREEN: focused navigation and composition-boundary tests passed after both fixes.
- Focused composition boundary/navigation/route/map tests: 5 suites, 16 tests passed.
- `npm run typecheck`: passed.
- `npm run lint`: passed.
- `npm run verify:architecture`: passed.
- `git diff --check`: passed.
- No emulator, simulator, native, device, or agent tests/actions were run.
