# Task 19 Report

## Outcome

Moved `useGpsPositionHandler` from `features/tracking/presentation/viewmodels` to `features/tracking/application`, updated `TrackingSessionCoordinator` to consume the application hook, and tightened the architecture checker so `TrackingSessionCoordinator` cannot import tracking presentation modules directly or through a local presentation barrel.

## What Changed

- Added `src/features/tracking/application/useGpsPositionHandler.ts`.
- Removed `src/features/tracking/presentation/viewmodels/useGpsPositionHandler.ts`.
- Updated `src/app/composition/coordinators/TrackingSessionCoordinator.tsx` to import the hook from tracking application.
- Hardened `scripts/verify-architecture.mjs` with a coordinator-specific rule that rejects tracking presentation imports from `TrackingSessionCoordinator`.
- Updated the focused boundary and architecture tests to cover the new application location and the coordinator presentation-import regression.
- Updated `__tests__/trackingViewModels.test.tsx` to import the hook from the new application path and added a callback-identity regression test.

## TDD / Verification

Red before implementation:

- `npm test -- --runInBand __tests__/trackingViewModels.test.tsx`
- `npm test -- --runInBand __tests__/trackingApplicationBoundaries.test.ts`
- `npm test -- --runInBand __tests__/verifyArchitecture.test.ts`

Green after implementation:

- `npm test -- --runInBand __tests__/trackingViewModels.test.tsx`
- `npm test -- --runInBand __tests__/trackingApplicationBoundaries.test.ts`
- `npm test -- --runInBand __tests__/verifyArchitecture.test.ts`
- `npm run typecheck`
- `npm run lint`
- `npm run verify`
- `npm test -- --runInBand --detectOpenHandles`
- `npm test -- --runInBand __tests__/TopSheetPopup.test.tsx --detectOpenHandles`

## Notes

- The hook behavior stayed unchanged: it still returns a stable callback via `useCallback` and only forwards the neutral `LocationSnapshot` to `publish`.
- No emulator, simulator, native smoke, or device tests were run.
- I did not find a separate popup-specific npm script in this repo, so I used the existing popup test file with `--detectOpenHandles` in addition to the full Jest open-handle pass.

## Concerns

- The architecture guard is intentionally targeted at `TrackingSessionCoordinator`. If another coordinator later picks up the same presentation dependency pattern, the guard will need a similar rule.
