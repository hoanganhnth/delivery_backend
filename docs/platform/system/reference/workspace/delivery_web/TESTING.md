# Testing strategy

The web console uses `AppDependenciesProvider` as its composition boundary.
Pages and providers receive auth, restaurant, menu, order, admin, session,
notification, clock and delay ports from that registry. Production adapters are
assembled once in `src/app/dependencies.tsx`; tests use
`src/test/createTestDependencies.ts` and must not module-mock feature internals.

Test routed behavior with `src/test/renderApp.tsx` and canonical builders. Every
reachable mutation should prove validation, loading/single-submit, success,
backend failure and retry where the action remains available. Read-only routes
must distinguish loading, empty and failure states. Timers and current time use
injected ports so tests never wait on wall-clock time.

Validation commands:

```sh
npm run verify
npm run verify:ci
npm run test:coverage
npm run test:e2e:coverage
```

`npm run verify` includes the polyrepo-level handbook source check and is the
authoritative local gate when the shared workspace is present. `npm run
verify:ci` is the standalone `delivery_web` gate used by GitHub Actions; it
typechecks and bundles the checked-in handbook snapshot without requiring
parent-repository documentation files.

Coverage is a regression guard. Completion is measured against the reachable
use-case/action matrix in the polyrepo execution plan, not a global line target.

## Browser E2E

The default Playwright suite uses a stateful Gateway mock and is safe for pull
requests. It covers the current 56 reachable action IDs across customer,
restaurant-owner and admin portals; `npm run test:e2e:coverage` fails below 90%
and currently reports 100% (56/56).

Install the local Chromium binary once with `npx playwright install chromium`.
Use `npm run test:e2e:all` when Firefox and WebKit are installed as well.

The real-browser smoke is intentionally separate and must target only the
disposable backend sandbox:

```sh
E2E_BACKEND_DIR=../backend_delivery npm run test:e2e:live
```

It starts `backend_delivery/scripts/sandbox-up.sh`, operator-provisions a
run-scoped admin, checks customer → owner → admin authentication/read surfaces,
and purges the sandbox on exit. It does not accept a production or shared
staging URL and does not retain browser traces, videos or storage state.
