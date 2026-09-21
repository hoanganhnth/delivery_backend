# Task 31 Report: Keep Active Delivery UI intent-only

## Outcome

`ActiveDeliverySheetView` now emits the existing `{ type: 'advancePressed' }`
intent after a swipe crosses the existing threshold or after accessibility
activation, regardless of `state.canAdvance`. The View still uses
`canAdvance` only for presentation affordances: progress color,
`accessibilityState.disabled`, and disabled knob styling.

`useActiveDeliveryViewModel` remains unchanged and remains the sole command
admission point. Its existing delivery-presence, `canUpdate`, loading, next
status, canonical COD, command result, status mutation, and success-navigation
guards remain intact.

## TDD evidence

### RED

Added the focused host regression tests before changing the View:

```text
npx jest __tests__/BottomSheetDelivery.test.tsx --runInBand --runTestsByPath -t "disabled view"
```

Result before the implementation change:

```text
FAIL __tests__/BottomSheetDelivery.test.tsx
Tests:       2 failed, 3 skipped, 5 total
Expected: { type: 'advancePressed' }
Number of calls: 0
```

The failures covered both accessibility activation and swipe-threshold
completion while `canAdvance` was false.

### GREEN

After removing only the two View event-admission checks:

```text
npx jest __tests__/BottomSheetDelivery.test.tsx __tests__/deliveryViewModelBoundaries.test.ts --runInBand --runTestsByPath
```

```text
PASS __tests__/BottomSheetDelivery.test.tsx
PASS __tests__/deliveryViewModelBoundaries.test.ts
Test Suites: 2 passed, 2 total
Tests:       7 passed, 7 total
```

The ViewModel regression verifies the raw intent is still rejected for an
invalid COD payment and does not call the update command.

## Files changed

- `src/features/delivery/presentation/views/ActiveDeliverySheetView.tsx`
  - Removed `state.canAdvance` from swipe and accessibility event admission.
  - Preserved gesture threshold, spring reset, accessibility metadata, and
    all disabled/visual affordances.
- `__tests__/BottomSheetDelivery.test.tsx`
  - Added host-only accessibility and swipe regressions for raw intent
    emission from a disabled view.
- `__tests__/deliveryViewModelBoundaries.test.ts`
  - Added host-only ViewModel admission regression for invalid COD data.
- `useActiveDeliveryViewModel.ts` was inspected but intentionally unchanged;
  its existing authoritative guards already satisfy the brief.

## Validation

```text
npm run typecheck
PASS (tsc --noEmit)

npm run lint
PASS (eslint .)

npm run verify:architecture
Architecture boundary check passed.

npm test -- --runInBand
Test Suites: 86 passed, 86 total
Tests:       452 passed, 452 total
Snapshots:   0 total

npm test -- --runInBand --detectOpenHandles
Test Suites: 86 passed, 86 total
Tests:       452 passed, 452 total
Snapshots:   0 total

npx jest __tests__/MatchFoundPopup.test.tsx __tests__/TopSheetPopup.test.tsx --runInBand
Test Suites: 2 passed, 2 total
Tests:       6 passed, 6 total

git diff --check
PASS (no whitespace errors)
```

## Review and concerns

Self-review covered correctness, scope, architecture, security, and
performance. No required findings remain. No new dependency, event bus,
shared component, domain policy, navigation dependency, backend contract, or
native surface was introduced. The initial focused GREEN run printed Jest's
usual open-handle warning; the full detector run exited 0 without that
warning.

No independent reviewer subagent was dispatched because the task explicitly
prohibited subagent dispatch; the requested self-review was performed in this
task.

No emulator, simulator, native smoke, device, or other native test was run.
