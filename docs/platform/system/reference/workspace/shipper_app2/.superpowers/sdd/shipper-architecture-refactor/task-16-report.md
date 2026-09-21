# Task 16 report: Decompose delivery application commands by workflow

## Outcome

Implemented the delivery application command decomposition from base `c7784ac`.
`deliveryCommands.ts` is now a compatibility barrel, while command authority is
split into offer, assignment, recovery, batch, and history modules. Shared thunk
contracts live in `deliveryCommandTypes.ts`.

All command objects remain direct re-exports, preserving thunk identity through
both `deliveryCommands.ts` and `application/index.ts`. State reducer sections now
import their workflow command modules directly. No app, React, navigation,
native, or other-feature dependency was added to the application modules.

## Implementation

- Added `deliveryCommandTypes.ts` with `DeliveryCommandState`,
  `DeliveryCommandClock`, `DeliveryCommandServices`,
  `DeliveryCommandDispatch`, `DeliveryCommandThunkConfig`, and
  `FetchHistoryParams`.
- Added `offerCommands.ts` for `fetchCurrentOffer` and `rejectCurrentOffer`.
- Added `assignmentCommands.ts` for `acceptOrder`,
  `cancelCurrentAssignment`, and `updateDeliveryStatus`.
- Added `recoveryCommands.ts` for `fetchActiveDelivery`,
  `fetchActiveDeliveries`, and `getUniqueBatchIds`.
- Added `batchCommands.ts` for `hydrateBatchSnapshot`.
- Added `historyCommands.ts` for `fetchDeliveryHistory`.
- Reduced `deliveryCommands.ts` to direct compatibility re-exports.
- Updated the canonical application barrel and all delivery state reducer
  sections/slices to use the workflow modules.
- Added structural boundary regression coverage and explicit thunk identity
  coverage.

The extracted implementations preserve the original action type strings,
request validation, COD offer parsing/expiry checks, transition policy,
repository arguments, Vietnamese error fallbacks, conditional batch hydration
dispatch, and history status parameter forwarding.

## TDD evidence

### RED

Before creating any workflow production module, ran:

```text
npm test -- --runInBand __tests__/deliveryApplicationSections.test.ts
```

Observed the expected failure:

```text
FAIL __tests__/deliveryApplicationSections.test.ts
Tests:       2 failed, 2 total
Expected: true
Received: false
```

The first failure was the missing workflow module; the second reported that the
compatibility barrel did not re-export `fetchCurrentOffer` from
`./offerCommands`.

### GREEN

After creating the five workflow modules and shared type module, the same
focused command passed:

```text
PASS __tests__/deliveryApplicationSections.test.ts
Test Suites: 1 passed, 1 total
Tests:       2 passed, 2 total
```

The later identity assertions also pass:

```text
PASS __tests__/deliveryCommands.test.ts
Test Suites: 2 passed, 2 total
Tests:       4 passed, 4 total
```

## Validation

Focused delivery/application/state/store run:

```text
Test Suites: 10 passed, 10 total
Tests:       46 passed, 46 total
```

The focused run covered application sections, commands, current delivery
reducer, state sections/slices, secondary store, fulfilment store, app
initializer, tracking integration, and tracking view models.

Repository checks:

```text
npm run typecheck             # exit 0
npm run lint                  # exit 0
npm run verify:architecture   # Architecture boundary check passed.
npm run verify                # 74 suites passed, 276 tests passed
npm test -- --runInBand --detectOpenHandles
                              # 74 suites passed, 276 tests passed; no handle warning
git diff --check              # exit 0
```

No emulator, simulator, native smoke, or device test was run.

## Changed files

- `.superpowers/sdd/shipper-architecture-refactor/task-16-report.md`
- `__tests__/deliveryApplicationSections.test.ts`
- `__tests__/deliveryCommands.test.ts`
- `src/features/delivery/application/assignmentCommands.ts`
- `src/features/delivery/application/batchCommands.ts`
- `src/features/delivery/application/deliveryCommandTypes.ts`
- `src/features/delivery/application/deliveryCommands.ts`
- `src/features/delivery/application/historyCommands.ts`
- `src/features/delivery/application/index.ts`
- `src/features/delivery/application/offerCommands.ts`
- `src/features/delivery/application/recoveryCommands.ts`
- `src/features/delivery/state/assignmentExtraReducers.ts`
- `src/features/delivery/state/batchExtraReducers.ts`
- `src/features/delivery/state/currentDeliverySlice.ts`
- `src/features/delivery/state/historyDeliverySlice.ts`
- `src/features/delivery/state/offerExtraReducers.ts`
- `src/features/delivery/state/recoveryExtraReducers.ts`

## Self-review and concerns

Performed a second-pass diff review against the original 226-line command module
and verified each extracted command's action prefix, input/state guards,
policy calls, repository invocation, fallback text, and dispatch behavior. The
identity test compares workflow exports with both application barrels. The
architecture test checks all six new application files for forbidden imports.

No implementation concerns remain from the available checks. A separate
reviewer subagent was not dispatched because the task explicitly prohibited
subagents; the review above is therefore an in-session independent second pass.

## Fix round 1: structural proof hardening

### Review findings addressed

This fix round addresses the independent review's Important finding that the
structural test used a regex parser and a hard-coded feature-name allowlist, and
its Minor finding that shared command type compatibility exports were not
explicitly checked. Production command modules and production behavior were not
changed.

### TDD RED evidence

Before changing either detector helper, added a synthetic-import regression to
`__tests__/deliveryApplicationSections.test.ts`:

```text
import debug from '../../debug';
import native from '../../native';
```

Ran:

```text
npm test -- --runInBand __tests__/deliveryApplicationSections.test.ts
```

The existing regex/allowlist detector failed as required:

```text
FAIL __tests__/deliveryApplicationSections.test.ts
  delivery application command sections
    ✕ rejects debug and native relative imports (3 ms)
    ✓ keeps workflow modules present and outside app, native, and other-feature layers (2 ms)
    ✓ keeps every command available from the compatibility barrel (1 ms)

  ● delivery application command sections › rejects debug and native relative imports

    - Expected  - 4
    + Received  + 1

    - Array [
    -   '../../debug',
    -   '../../native',
    - ]
    + Array []

    Test Suites: 1 failed, 1 total
    Tests:       1 failed, 2 passed, 3 total
    Ran all test suites matching /__tests__\/deliveryApplicationSections.test.ts/i.
```

### GREEN implementation

- Replaced regex import extraction with `typescript` AST
  `ImportDeclaration` parsing, so comments, strings, and non-import text are
  not treated as imports.
- The detector resolves relative imports from each actual application module
  path and computes the target relative to `src`.
- It rejects targets outside `src`, under `app`, under any
  `features/<other-feature>` path without a feature-name allowlist, and any
  `platform` or `native` path segment.
- It explicitly allows delivery domain/application, `core`, `shared`, and the
  approved `@reduxjs/toolkit` external package.
- Added explicit AST checks that `deliveryCommands.ts` type-re-exports all six
  shared command contracts: `DeliveryCommandState`, `DeliveryCommandClock`,
  `DeliveryCommandServices`, `DeliveryCommandDispatch`,
  `DeliveryCommandThunkConfig`, and `FetchHistoryParams`.
- Existing runtime command re-export checks remain in place.

Focused GREEN run:

```text
npm test -- --runInBand __tests__/deliveryApplicationSections.test.ts
Test Suites: 1 passed, 1 total
Tests:       3 passed, 3 total
```

The post-fix focused delivery group also passed:

```text
Test Suites: 9 passed, 9 total
Tests:       45 passed, 45 total
```

### Fix-round validation

```text
npm run verify
  typecheck: exit 0
  lint: exit 0
  verify:architecture: Architecture boundary check passed.
  Jest: 74 suites passed, 277 tests passed

npm test -- --runInBand --detectOpenHandles
  Test Suites: 74 passed, 74 total
  Tests:       277 passed, 277 total
  No open-handle warning.

git diff --check
  exit 0
```

No emulator, simulator, native smoke, or device test was run.

### Fix-round changed files and self-review

The implementation diff after the Task 16 commit contains only
`__tests__/deliveryApplicationSections.test.ts`; the report is the accompanying
documentation change. `git diff --name-only` was checked before validation and
showed no production file. The final self-review verified the AST import parser,
generic feature boundary, app/native/platform rejection, allowed imports,
runtime re-export checks, and all six compatibility type exports. No unresolved
concerns remain for this fix round.

## Fix round 2: shared type export source proof

### Review finding addressed

This scoped re-review identified that the six shared type names were checked as
type-only exports but their source module was not asserted. This round keeps the
runtime export checks and production behavior unchanged, and strengthens only
`__tests__/deliveryApplicationSections.test.ts`.

### TDD RED evidence

Before changing the type export collector, added this synthetic source and
assertion:

```text
export type { DeliveryCommandState } from './wrongTypes';
```

The test requested the collector filtered for `./deliveryCommandTypes` to return
no names. The existing unfiltered collector returned the type name and failed:

```text
npm test -- --runInBand __tests__/deliveryApplicationSections.test.ts

FAIL __tests__/deliveryApplicationSections.test.ts
  delivery application command sections
    ✕ does not collect shared types from the wrong module (5 ms)
    ✓ rejects debug and native relative imports (1 ms)
    ✓ keeps workflow modules present and outside app, native, and other-feature layers (10 ms)
    ✓ keeps every command available from the compatibility barrel (2 ms)

  ● delivery application command sections › does not collect shared types from the wrong module

    - Expected  - 1
    + Received  + 3

    - Array []
    + Array [
    +   "DeliveryCommandState",
    + ]

    Test Suites: 1 failed, 1 total
    Tests:       1 failed, 3 passed, 4 total
    Ran all test suites matching /__tests__\/deliveryApplicationSections.test.ts/i.
```

### GREEN implementation

- Updated the AST type export collector to accept an expected module specifier.
- It now collects names only from type-only `ExportDeclaration` nodes whose
  string module specifier exactly equals `./deliveryCommandTypes`.
- The six compatibility type assertions use that exact filter, covering
  `DeliveryCommandState`, `DeliveryCommandClock`, `DeliveryCommandServices`,
  `DeliveryCommandDispatch`, `DeliveryCommandThunkConfig`, and
  `FetchHistoryParams`.
- Existing runtime command re-export checks were left intact.

Focused GREEN run:

```text
npm test -- --runInBand __tests__/deliveryApplicationSections.test.ts
Test Suites: 1 passed, 1 total
Tests:       4 passed, 4 total
```

Relevant delivery focused run:

```text
Test Suites: 9 passed, 9 total
Tests:       46 passed, 46 total
```

### Round-2 validation

```text
npm run verify
  typecheck: exit 0
  lint: exit 0
  verify:architecture: Architecture boundary check passed.
  Jest: 74 suites passed, 278 tests passed

npm test -- --runInBand --detectOpenHandles
  Test Suites: 74 passed, 74 total
  Tests:       278 passed, 278 total
  No open-handle warning.
```

No emulator, simulator, native smoke, or device test was run.

### Round-2 changed files and self-review

Only `__tests__/deliveryApplicationSections.test.ts` and this report were
changed after the previous fix commit. The final review confirms the synthetic
`./wrongTypes` regression fails before the fix and passes after it, while the
six type names are now tied to `./deliveryCommandTypes` and runtime re-export
coverage remains present. No production file was modified and no unresolved
concerns remain for round 2.
