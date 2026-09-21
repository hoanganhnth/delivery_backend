Task 20 Report

Outcome: split `DeliveryRecoveryCoordinator` into `useDeliveryForegroundRecovery` and `useCurrentOfferPolling`; coordinator is now composition-only.

Changed files:
- `src/app/composition/coordinators/DeliveryRecoveryCoordinator.tsx`
- `src/app/composition/coordinators/useDeliveryForegroundRecovery.ts`
- `src/app/composition/coordinators/useCurrentOfferPolling.ts`
- `scripts/verify-architecture.mjs`
- `__tests__/deliveryRecoveryCoordinator.test.tsx`
- `__tests__/deliveryRecoveryCompositionBoundaries.test.ts`
- `__tests__/verifyArchitecture.test.ts`

RED / GREEN:
- RED: `__tests__/deliveryRecoveryCompositionBoundaries.test.ts` failed before the split.
- GREEN: `__tests__/deliveryRecoveryCompositionBoundaries.test.ts` passes after extraction.
- GREEN: `__tests__/deliveryRecoveryCoordinator.test.tsx` passes with recovery, retry, session-boundary, and cleanup coverage.
- GREEN: `__tests__/verifyArchitecture.test.ts -t 'direct delivery recovery coordinator dependencies outside the two local workflow hooks'` passes.

Validation:
- Focused: `deliveryRecoveryCompositionBoundaries`, `deliveryRecoveryCoordinator`, `verifyArchitecture` fixture.
- Static: `npm run typecheck`, `npm run lint`, `npm run verify:architecture`.
- Full repo: `npm run verify` passed with 77 suites / 291 tests.
- Open handles: `npm test -- --runInBand --detectOpenHandles` passed with 77 suites / 291 tests.
- Open handles: `npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles` passed.

Behavior:
- Preserved one lifecycle subscription, one polling interval, session-generation guards, recovery dispatch order, batch hydration retry behavior, polling eligibility, and cleanup.
- No API/Gateway/WebSocket/Bearer/FCM/COD/navigation/native contract changes.

Concerns:
- None known yet; remaining open-handle checks will confirm timer cleanup at repo level.

---

Fix round 1 (2026-08-30)

Root cause:
- `useDeliveryForegroundRecovery` sampled `session.generation` from a ref updated in a passive effect. A foreground event fired immediately after `logoutUser.pending` could start recovery with a stale generation and abort the new session's recovery work.

RED:
- `npm test -- --runInBand __tests__/deliveryRecoveryCoordinator.test.tsx -t 'uses the current session generation when foreground recovery starts immediately after logout pending'`
- Failure: `expect(currentOffer).toHaveBeenCalledTimes(1)` received `0` after `resolveActive?.([])`, proving the stale-generation path aborted current-offer recovery.

GREEN:
- Updated `src/app/composition/coordinators/useDeliveryForegroundRecovery.ts` to read `const generation = store.getState().session.generation` inside `recover` and removed the selector/ref/effect sync path.
- The same focused regression now passes.

Files:
- `src/app/composition/coordinators/useDeliveryForegroundRecovery.ts`
- `__tests__/deliveryRecoveryCoordinator.test.tsx`

Validation:
- `npm test -- --runInBand __tests__/deliveryRecoveryCoordinator.test.tsx`
- `npm run typecheck`
- `npm run lint`
- `npm run verify:architecture`

Concerns:
- None known for this round.

---

Correction round (2026-08-30)

Issue:
- `__tests__/deliveryRecoveryCompositionBoundaries.test.ts` still asserted `selectSessionGeneration` in the foreground recovery hook boundary fixture after the approved production fix removed that selector/ref path.

RED:
- `npm test -- --runInBand __tests__/deliveryRecoveryCompositionBoundaries.test.ts`
- Failure: `expect(hook).toContain('selectSessionGeneration')` failed because `useDeliveryForegroundRecovery.ts` now contains `store.getState().session.generation` instead.

GREEN:
- Updated the boundary fixture to assert `store.getState().session.generation` and the absence of `sessionGenerationRef`.
- No production code changed.

Files:
- `__tests__/deliveryRecoveryCompositionBoundaries.test.ts`
- `.superpowers/sdd/shipper-architecture-refactor/task-20-report.md`

Validation:
- `npm test -- --runInBand __tests__/deliveryRecoveryCompositionBoundaries.test.ts __tests__/deliveryRecoveryCoordinator.test.tsx __tests__/verifyArchitecture.test.ts`
- `npm run typecheck`
- `npm run lint`
- `npm run verify:architecture`

Concerns:
- None known.
