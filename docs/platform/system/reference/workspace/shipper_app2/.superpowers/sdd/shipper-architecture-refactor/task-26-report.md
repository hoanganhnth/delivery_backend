# Task 26 Implementation Report

Date: 2026-08-31
Worktree: `/Users/a/Documents/private/delivery/.worktrees/shipper-architecture-refactor`
Base commit: `72c15e2`

## Outcome

Hardened the remaining architecture and test-boundary proof gaps without
changing product behavior or externally observable contracts. The architecture
verifier now checks the delivery recovery coordinator allowlist before local
target resolution, including unresolved external packages. The delivery
projection synchronizer now accepts only the state fields it owns and uses
typed Immer draft adapters instead of the previous double `unknown` cast.
The boundary suites now use TypeScript AST import/export/call extraction and
resolved local targets, and the delivery command proof derives its async
command surface from the canonical application barrel.

No REST, WebSocket, Bearer, FCM, COD, delivery-transition, batch-recovery,
navigation, or hidden-capability contract was changed.

## Implemented scope

- Added a resolved presentation-barrel cycle fixture while retaining the
  direct-cycle fixture in `__tests__/verifyArchitecture.test.ts`.
- Added an unresolved external-import regression for the delivery recovery
  coordinator and moved that allowlist check before local-target short-circuit.
- Replaced the regex-only import scan in
  `__tests__/deliveryStateSections.test.ts` with TypeScript AST import/export
  extraction and extension/index-aware local resolution.
- Replaced source-string listener assertions in
  `__tests__/storeIntegrationBoundaries.test.ts` with AST call/import checks,
  resolved canonical application targets, nested dispatch-call proof, and
  resolved View/native-adapter filtering.
- Changed `syncNormalizedAggregate` to use a narrow
  `Draft<DeliveryProjectionState>` boundary and typed `castImmutable`/
  `castDraft` adapters in `src/features/delivery/state/deliveryProjectionReducer.ts`.
- Added `__tests__/deliveryProjectionReducer.test.ts` to prove the narrow
  projection input and read-model convergence.
- Removed the duplicated delivery command-name list from
  `__tests__/deliveryStateCommandBoundaries.test.ts`; value exports are
  derived from `src/features/delivery/application/index.ts` through its AST
  export graph.

## RED evidence

The focused proof was added before the verifier/reducer production changes.

Focused command:

```text
npx jest __tests__/verifyArchitecture.test.ts __tests__/deliveryStateSections.test.ts __tests__/storeIntegrationBoundaries.test.ts __tests__/deliveryStateCommandBoundaries.test.ts __tests__/deliveryProjectionReducer.test.ts --runInBand --silent
```

Observed RED:

- `__tests__/verifyArchitecture.test.ts` failed its new unresolved external
  recovery-coordinator case: expected process status `1`, received `0`.
- The other four focused suites passed, including the new barrel-cycle and
  AST-boundary proofs.
- Result: 1 failed suite, 4 passed; 1 failed test, 127 passed; 128 total.

Type-boundary RED:

```text
npm run typecheck
```

Observed `TS2740` at
`__tests__/deliveryProjectionReducer.test.ts(21,11)`: the old
`syncNormalizedAggregate` signature required the full `CurrentDeliveryState`,
so the focused projection-only fixture was missing `loading`, `error`,
request-generation fields, and other unrelated state.

## GREEN evidence

After the minimal verifier ordering, typed projection-boundary, and canonical
export-surface changes:

```text
npx jest __tests__/verifyArchitecture.test.ts __tests__/deliveryStateSections.test.ts __tests__/storeIntegrationBoundaries.test.ts __tests__/deliveryStateCommandBoundaries.test.ts __tests__/deliveryProjectionReducer.test.ts --runInBand --silent
```

```text
Test Suites: 5 passed, 5 total
Tests:       128 passed, 128 total
```

```text
npm run typecheck
tsc --noEmit                         PASS
```

An intermediate lint run identified the stale source-string variable left
behind by the AST conversion. Removing that unused test-only read restored a
clean lint result without changing behavior.

## Full host-only validation

- `npm run lint` — PASS (`eslint .`).
- `npm run verify:architecture` — PASS (`Architecture boundary check passed.`).
- `npm run verify` — PASS: 84 suites / 416 tests.
- `npm test -- --runInBand --detectOpenHandles` — PASS: 84 suites / 416 tests.
- `npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles`
  — PASS: 1 suite / 5 tests.
- `git diff --check` — PASS with no whitespace errors.

All validation was host-only. No emulator, simulator, native smoke, or device
tests were run.

## Self-review

The complete Task 26 diff was inspected before this report was written. It is
limited to the named verifier, projection reducer, boundary tests, focused
projection regression, and this report. Existing direct-cycle coverage,
local recovery hooks, React allowance, state-facade negative assertions, and
listener/count behavior assertions remain present. No unrelated worktree
changes were found.

No unresolved implementation concerns were identified within the authorized
host-only scope.

## Fix round 1 — review findings

Date: 2026-08-31

### Findings addressed

1. Installer wiring in `__tests__/storeIntegrationBoundaries.test.ts` now
   requires each installer call to be an identifier call whose imported
   binding resolves to its canonical integration module:
   `OfferOverlayIntegration.ts`, `PushSessionIntegration.ts`, or
   `DeliveryTrackingIntegration.ts`.
2. Native filtering now rejects `@rnmapbox/maps` subpaths and performs
   case-normalized substring checks after resolution for `View`,
   `nativeAdapter`, and `native-adapter` path segments, including
   `webSocketNativeAdapter.ts`.
3. The store proof now recursively traverses resolved local import and export
   references with cycle protection before applying forbidden View/native
   adapter checks. The direct `currentDeliverySlice` assertion remains
   direct-target based because the canonical application command graph
   legitimately reaches that reducer through app store types/reducers.
4. The command export walker now preserves local re-export names and records
   `ExportSpecifier.name` as the public alias, so
   `export { fetchCurrentOffer as loadOffer }` contributes `loadOffer`.

### RED evidence

The review regression fixtures and assertions were added before the helper
implementations were corrected.

```text
npx jest __tests__/storeIntegrationBoundaries.test.ts __tests__/deliveryStateCommandBoundaries.test.ts --runInBand --silent
```

Observed intended failures:

- The local re-export/alias fixture returned only `fetchCurrentOffer` instead
  of `fetchCurrentOffer, loadOffer`.
- `@rnmapbox/maps/subpath` was not recognized as forbidden.
- The resolved `webSocketNativeAdapter.ts` filename was not recognized as
  forbidden by the original exact-case matcher.
- The barrel fixture returned no forbidden View reference because traversal
  stopped at the intermediate barrel.

Result: 2 failed suites, 3 failed tests, 2 passed tests, 5 total.

### GREEN evidence

After the minimal AST/resolution helper changes:

```text
npx jest __tests__/storeIntegrationBoundaries.test.ts __tests__/deliveryStateCommandBoundaries.test.ts --runInBand
```

```text
Test Suites: 2 passed, 2 total
Tests:       5 passed, 5 total
```

The first GREEN attempt exposed two test-proof corrections: the adapter
fixture uses `NativeAdapter` capitalization, and recursive traversal reaches
`currentDeliverySlice` through the legitimate application/store type graph.
The matcher was normalized case-insensitively, and the reducer assertion was
kept resolved but direct-only; the recursive traversal remains applied to the
forbidden View/native-adapter rule.

### Fix-round validation

- `npm run typecheck` — PASS (`tsc --noEmit`).
- `npm run lint` — PASS (`eslint .`).
- `npm run verify:architecture` — PASS (`Architecture boundary check passed.`).
- `npm run verify` — PASS: 84 suites / 419 tests.
- `npm test -- --runInBand --detectOpenHandles` — PASS: 84 suites / 419 tests.
- `npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles`
  — PASS: 1 suite / 5 tests.
- `git diff --check` — PASS with no whitespace errors.

### Fix-round files and scope review

Modified:

- `__tests__/storeIntegrationBoundaries.test.ts`
- `__tests__/deliveryStateCommandBoundaries.test.ts`

Added test-only fixtures outside Jest discovery:

- `test-fixtures/task-26/deliveryCommandExports.ts`
- `test-fixtures/task-26/storeIntegration/barrel-entry.ts`
- `test-fixtures/task-26/storeIntegration/barrel.ts`
- `test-fixtures/task-26/storeIntegration/HiddenView.tsx`

The initial fixture placement under `__tests__` was corrected after
`npm run verify` reported Jest's “must contain at least one test” discovery
error; no production behavior was involved. The complete fix-round diff was
inspected after relocation and contains no unrelated changes. No subagents
were dispatched.

All validation was host-only. No emulator, simulator, native smoke, or device
tests were run.

No unresolved concerns were identified for the four review findings within
the authorized scope.

## Fix round 1 completion audit

The affected proof suites were rerun on the committed fix-round tree before
this audit was appended:

```text
npx jest __tests__/storeIntegrationBoundaries.test.ts __tests__/deliveryStateCommandBoundaries.test.ts --runInBand --detectOpenHandles

Test Suites: 2 passed, 2 total
Tests:       5 passed, 5 total
```

The RED evidence immediately preceding the helper fixes remains recorded in
the Fix round 1 section above: 2 suites failed with 3 intended assertion
failures for the missing public alias, native package/adapter matching, and
barrel traversal. The corrected run is GREEN with no further concrete gap.

The full host-only validation recorded for this fix round also remains valid:
`npm run typecheck`, `npm run lint`, `npm run verify:architecture`,
`npm run verify` (84 suites / 419 tests), full Jest with
`--runInBand --detectOpenHandles` (84 suites / 419 tests), the MatchFound
popup gate (1 suite / 5 tests), and `git diff --check` all passed.

Final self-review found only the two affected boundary tests, four static
test fixtures outside Jest discovery, and this report change in the fix-round
diff. No product/runtime/API files changed, no subagents were dispatched, and
no emulator, simulator, native smoke, or device tests were run.
