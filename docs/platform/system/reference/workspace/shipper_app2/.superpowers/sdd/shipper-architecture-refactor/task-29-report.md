# Task 29 — Presentation View decomposition report

## Outcome

Decomposed the debug and shipper presentation Views into the exact feature-local
components requested by Task 29. `DebugToolsView` and `DrawerView` remain the
Route → ViewModel → View composition boundaries and continue to map raw child
intents to the unchanged `DebugToolsEvent` and `DrawerEvent` unions.

No runtime, backend, navigation, debug, shipper command, or shared UI contract
was changed.

## Files changed

Debug presentation:

- `src/features/debug/presentation/views/DebugToolsView.tsx`
- `src/features/debug/presentation/views/components/DebugDisabledState.tsx`
- `src/features/debug/presentation/views/components/GatewayOriginCard.tsx`
- `src/features/debug/presentation/views/components/DebugLogList.tsx`
- `src/features/debug/presentation/views/components/DebugLogDetails.tsx`

Shipper presentation:

- `src/features/shipper/presentation/views/DrawerView.tsx`
- `src/features/shipper/presentation/views/components/DrawerProfileHeader.tsx`
- `src/features/shipper/presentation/views/components/OnlineStatusCard.tsx`
- `src/features/shipper/presentation/views/components/DrawerMenu.tsx`
- `src/features/shipper/presentation/views/components/DrawerLogoutAction.tsx`

Tests and architecture proof:

- `__tests__/presentationViewComposition.test.tsx`
- `__tests__/verifyArchitecture.test.ts`

## RED

Focused test command run before production edits:

```sh
npm test -- __tests__/presentationViewComposition.test.tsx --runInBand
```

Observed intended RED output:

```text
FAIL __tests__/presentationViewComposition.test.tsx
● Test suite failed to run
Cannot find module '../src/features/debug/presentation/views/components/DebugDisabledState'
Tests: 0 total
```

The failure was caused by the requested child component boundary being absent;
no production extraction had been written at that point.

## GREEN and validation

Focused GREEN command:

```sh
npm test -- __tests__/presentationViewComposition.test.tsx --runInBand
```

Observed output:

```text
PASS __tests__/presentationViewComposition.test.tsx
Test Suites: 1 passed, 1 total
Tests:       8 passed, 8 total
```

The focused tests cover the disabled debug branch, gateway event forwarding,
saving/error/notice and empty-log branches, log expand/collapse and clear
intent, profile/status/menu/logout intents, and drawer conditional branches.

Architecture proof:

```sh
npm run typecheck
# exit 0

npm run lint
# exit 0

npm run verify:architecture
# exit 0
Architecture boundary check passed.

npm test -- __tests__/verifyArchitecture.test.ts --runInBand
# exit 0
Test Suites: 1 passed, 1 total
Tests:       124 passed, 124 total
```

The added architecture test checks all eight child files for Redux,
navigation, runtime, domain, state, data, application, platform, repository,
and native-adapter imports. The existing AST architecture matrix remains
unchanged.

Full host-only validation:

```sh
npx jest --runInBand --detectOpenHandles
```

```text
Test Suites: 86 passed, 86 total
Tests:       437 passed, 437 total
```

Popup gate:

```sh
npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles
```

```text
Test Suites: 1 passed, 1 total
Tests:       5 passed, 5 total
```

Diff whitespace check:

```sh
git diff --check
# exit 0
```

## Review and concerns

Self-review confirmed that `DebugLogList` is the only extracted component
holding UI state; all persistence, clearing behavior, navigation, eligibility,
logout sequencing, and asynchronous behavior remain event/ViewModel concerns.
Children only receive readonly presentation data and raw callbacks. No shared
UI component was added.

An independent subagent review was not run because the task explicitly says not
to dispatch subagents. The implementation was reviewed locally against the
brief, contracts, import graph, labels, and focused host tests.

No unresolved host-side concerns.

Native, emulator, simulator, smoke, and device tests were intentionally not run.
This is an explicit host-only presentation slice, so native-provider behavior is
outside the validation performed here.

## Fix round 1 — direct native-service package guard

### Review finding and root cause

The first-round child-boundary matcher did not name direct native-service
packages, and `scripts/verify-architecture.mjs` only rejected native Mapbox
imports from feature presentation Views. The current children were clean, but a
future child could import `react-native-geolocation-service` or
`react-native-image-picker` without the canonical architecture command failing.

### Changed files

- `scripts/verify-architecture.mjs` — added the canonical
  `nativeServicePackages` list and `isNativeServiceModule` package/subpath
  matcher, applied to every feature presentation View path, including child
  components. The existing Mapbox and AST dependency rules are unchanged.
- `__tests__/verifyArchitecture.test.ts` — added temporary fixture regressions
  for `react-native-geolocation-service` and `react-native-image-picker`, and
  extended the current-child proof to recognize the same direct packages.
- `.superpowers/sdd/shipper-architecture-refactor/task-29-report.md` — this
  fix-round evidence.

### Fix-round RED

Regression command run after adding the fixture tests and before changing the
canonical script:

```sh
npm test -- __tests__/verifyArchitecture.test.ts --runInBand -t "direct .* imports from feature presentation View children"
```

Exact result:

```text
FAIL __tests__/verifyArchitecture.test.ts
✕ rejects direct react-native-geolocation-service imports from feature presentation View children
✕ rejects direct react-native-image-picker imports from feature presentation View children
Expected: 1
Received: 0
Tests:       2 failed, 124 skipped, 126 total
```

Both temporary fixtures passed through the old canonical checker with status 0,
confirming the missing native-service rule rather than a fixture or test typo.

### Fix-round GREEN and host validation

Focused regression command after the canonical script change:

```sh
npm test -- __tests__/verifyArchitecture.test.ts --runInBand -t "direct .* imports from feature presentation View children"
```

Exact result summary:

```text
PASS __tests__/verifyArchitecture.test.ts
✓ rejects direct react-native-geolocation-service imports from feature presentation View children
✓ rejects direct react-native-image-picker imports from feature presentation View children
Test Suites: 1 passed, 1 total
Tests:       124 skipped, 2 passed, 126 total
```

Covering architecture and View tests:

```sh
npm test -- __tests__/verifyArchitecture.test.ts __tests__/presentationViewComposition.test.tsx --runInBand
```

```text
Test Suites: 2 passed, 2 total
Tests:       134 passed, 134 total
```

Additional required host checks:

```sh
npm run typecheck
# exit 0

npm run lint
# exit 0

npm run verify:architecture
# exit 0
Architecture boundary check passed.

npx jest --runInBand --detectOpenHandles
```

```text
Test Suites: 86 passed, 86 total
Tests:       439 passed, 439 total
```

Popup gate:

```sh
npx jest __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles
```

```text
Test Suites: 1 passed, 1 total
Tests:       5 passed, 5 total
```

```sh
git diff --check
# exit 0
```

### Fix-round concerns

No runtime behavior, event contracts, ViewModel policy, or existing AST rule was
changed. The direct native-service package list is intentionally explicit and
currently covers the native service dependencies declared by this app; UI
framework packages such as `react-native`, `react-native-reanimated`, and
`react-native-gesture-handler` remain valid imports for presentation code.

No emulator, simulator, native smoke, or device tests were run. No subagent was
dispatched, per the task instruction; the fix was self-reviewed against the
review finding, canonical checker, temporary fixtures, and host-only validation.
