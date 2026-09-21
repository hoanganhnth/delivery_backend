# Testing Strategy

Use fresh dependencies for every test. Do not use production singletons, real
timers, native GPS, FCM, Mapbox or network calls in unit/integration tests.

## Test seams

- `createAppStore` accepts a `ServiceRegistry` with repository ports, clock,
  push session and location socket port.
- `AppRuntimeProvider` accepts scheduler, lifecycle, GPS, feedback, media picker,
  social identity, push runtime and tracking configuration.
- `test-support/testDependencies.ts` creates the standard fakes and fixtures.
- Feature ViewModels accept feature-local contracts and route props from app
  composition; tests should prefer those contracts over Redux or runtime singletons.

## What to prove

- Views emit the documented discriminated event and render only supplied state.
- ViewModels own validation, loading/retry/error feedback, navigation, timers
  and subscription teardown.
- Reducer/thunk tests prove transition and convergence rules.
- Repository/platform tests prove payload mapping, parsing, permission, token,
  socket heartbeat/reconnect and FCM wake semantics.
- `npm run verify:architecture` proves the import boundaries. Do not weaken it
  to make a migration pass.
- For timer/open-handle regressions, prefer fake scheduler dependencies or
  explicit unmount/cleanup in the test harness. `npx jest
  __tests__/MatchFoundPopup.test.tsx --runInBand --detectOpenHandles` is the
  targeted proof used for Task 7.

Device Mapbox, background GPS and FCM delivery are smoke checks; fake adapters
cannot prove native-provider behaviour. On 2026-08-30 the iOS simulator smoke
covered a successful native build/install/relaunch, Metro bundle loading, login
screen rendering, CoreLocation initialization, permission grant and simulated
coordinate injection. Authenticated Mapbox, background GPS, native WebSocket,
FCM delivery and Android still require a configured release/device environment;
record those as unrun rather than implying pass/fail coverage.

## Commands

```sh
npm run typecheck
npm run lint
npm run verify:architecture
npm test -- --runInBand
npm run test:coverage

# Full repository proof
npm run verify
npm run verify:coverage
```
