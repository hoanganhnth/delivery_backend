# Execution Plan: Delivery Web E2E coverage

Date: 2026-08-23

## Status

Active

## Outcome

`delivery_web` has repeatable browser proof for all currently reachable
customer, restaurant-owner and admin workflows: a deterministic mocked Gateway
suite on pull requests plus a disposable real-Compose smoke suite nightly.

## Context

- Product and portal scope: `docs/product/overview.md`, `delivery_web/APP_FEATURES.md`.
- Visible HTTP action authority: `delivery_web/docs/action-contract-matrix.md`.
- Existing focused tests: `delivery_web/TESTING.md`.
- Real runtime harness: `backend_delivery/scripts/sandbox-up.sh`.

## Scope

In scope:

- Playwright browser tests, coverage report, CI and disposable backend smoke.
- Action-matrix reconciliation for visible customer logout and voucher actions.
- Safe operator ADMIN fixture invocation in an isolated Compose project.
- Difficult-case expansion: deterministic browser failures, races and contract
  faults that are cheap to reproduce in the mock Gateway.

Out of scope:

- Production/staging data, online payment, hidden checkout capabilities,
  realtime maps, performance or pixel-diff testing.

## Approach

1. Add deterministic Playwright mock fixtures that implement canonical Gateway
   envelopes and validate outgoing browser requests.
2. Cover every current action-matrix row plus route/navigation smoke and the
   required read/mutation failure states.
3. Generate a pass-only use-case report and require at least 90% coverage in
   CI; complete this initial delivery at 100% of the reconciled matrix.
4. Reuse the backend disposable sandbox for a Chromium customer → owner → admin
   smoke; tear down all run-scoped state on success and failure.
5. Expand difficult-case coverage in waves. Keep P0/P1 mock cases PR-blocking;
   run backend-backed critical paths nightly/manual against an isolated sandbox.
   Never use production or shared staging data as a test fixture.

## Risks And Recovery

- Existing dirty Web/backend changes are user-owned and must not be reset or
  overwritten. New test files and CI configuration remain isolated from them.
- Live runtime is slow and has token-bearing state. It runs nightly only,
  uploads no browser storage/network traces, and purges its sandbox by trap.
- Optional promotion/flash-sale/analytics capabilities remain mock-covered
  until their runtime gates are intentionally enabled.

## Progress

- [x] Inspect action/route authority and existing test baseline.
- [x] Add Playwright mocked browser harness and broad workflow suite.
- [x] Reconcile action matrix and enforce use-case coverage report.
- [x] Add isolated backend smoke wrapper and safe ADMIN provisioning support.
- [x] Wire PR/nightly CI and run focused validation.
- [x] Add Wave 1 hard-case matrix and deterministic failure controls.
- [x] Cover Wave 1 with 52/52 difficult cases and retain request invariants.
- [x] Wave 2: expand to 120–150 scenarios across concurrency, malformed
  contracts, retry/idempotency and authorization boundaries.
- [x] Wave 3A: add refresh/reload, terminal-state, mobile viewport and
  malformed mutation/read cases.
- [x] Wave 3B: expand the mocked Chromium suite to 200 scenarios with
  cross-portal failure/status permutations, request invariants and guarded
  recovery states.
- [x] Wave 4: add seven distinct state/local-boundary risks (corrupt cart,
  empty search, coordinate boundary, menu edit conflict/filtering and rating
  moderation/filtering), bringing the suite to 207 scenarios.
- [x] Wave 5: add twelve distinct cart/address conflict, catalog import
  partial-failure/update, duplicate-submit and admin moderation/Flash Sale/
  coupon boundary cases, bringing the suite to 219 scenarios and 191 difficult
  cases. E2E exposed real same-tick duplicate mutations in menu create and
  Flash Sale status; both now have synchronous ref guards.
- [x] Diagnose the live-runtime bootstrap through Gateway and simulator
  readiness; capture and fix the seed hand-off that prevented browser proof.
- [x] Make failed live startup diagnostics survive the wrapper's sandbox
  cleanup as an ignored, owner-readable local test artifact.
- [x] Diagnose the current saga-orchestrator startup failure: the apparent
  Kafka bootstrap error is downstream of Docker OOM kills, not a Kafka URL
  contract defect.
- [ ] Integrate and validate the sandbox-profile changes on a stable backend
  worktree: live smoke may select an allowlisted minimal graph (Postgres,
  Redis, Kafka, Auth/User/Restaurant/Order) and skip shipper/simulator/
  observability only for the three read/auth smoke cases; the default full
  sandbox remains unchanged.
- [x] Strengthen the live browser assertions so each role proves a successful
  real `POST /api/auth/login`; owner also proves the dashboard heading, while
  customer still proves the seeded menu and admin proves the portal heading.
- [ ] Optional Wave 4 expansion: add more scenarios only when they cover a
  distinct user-visible risk, then run the isolated live critical-flow smoke
  when Docker is available.

## Decisions

- 2026-08-23: Coverage is measured by reachable action IDs, not a global line
  coverage target. The current 50-row matrix expands to 56 rows.
- 2026-08-23: Chromium runs on PR; Chromium/Firefox/WebKit run nightly; real
  Compose smoke is Chromium-only.
- 2026-08-23: Nightly checks out `delivery_backend` with a read-only repository
  secret and never targets shared infrastructure.
- 2026-08-23: Wave 1 hard cases are mock-first; critical live flows are
  nightly/manual only because Docker is unavailable locally and shared
  environments are out of scope.
- 2026-08-23: The 200–250 target is scenario count, not a claim of line or
  branch coverage; every scenario must assert an observable recovery/guard and
  an API/request invariant.
- 2026-08-23: Wave 2 uses a ref-based client-side idempotency guard for COD
  double-click events; the browser case dispatches two same-tick clicks and
  proves exactly one order POST.
- 2026-08-23: Wave 3A adds an owner ratings fixture route to keep strict mock
  mode honest for the owner review portal.
- 2026-08-23: Wave 3B reaches the agreed lower bound (200 scenarios) by adding
  status-code permutations across admin login/catalog, customer address and
  checkout, order history/detail, owner menu, and admin shipper flows. The
  additional cases are retained because each asserts a distinct UI recovery
  and request invariant; no assertion was weakened merely to increase count.
- 2026-08-23: Wave 4 adds only distinct user-visible state risks; local
  persistence corruption, filtering and boundary validation are not counted
  as status-code permutations.
- 2026-08-23: Wave 5 retains only user-visible state/consistency risks that
  were not status-code permutations: invalid-but-parseable cart state,
  address conflicts, import partial success and ID/method invariants,
  same-tick mutation deduplication, and moderation state recovery.
- 2026-08-23: The disposable runtime must preserve the run ID inherited by
  `seed.sh`; a command-scoped reassignment fails under Bash because the parent
  sandbox deliberately declares that value readonly. The seed process now
  inherits it unchanged.

## Next-wave backlog

Wave 2 (120–150 total scenarios)

- Auth/session (20): concurrent 401s with one refresh, refresh timeout, malformed
  user/profile fields, role drift, logout during in-flight request, expired
  token during mutation.
- Customer/order (35): double-submit, slow quote, quote expiry during submit,
  stale cart, duplicate restaurant items, address edit/delete races, empty or
  malformed order pages, cancel conflict by terminal status.
- Owner/admin (35): duplicate mutation clicks, stale selected restaurant,
  list refresh during mutation, empty/malformed rows, permission mismatch,
  import partial failure and retry, approval/status conflict.
- Cross-cutting (30): abort/timeout, 429/5xx retry policy, non-JSON and schema
  drift, unexpected API calls, reload during pending mutation, mobile viewport.

Wave 3 (200–250 total scenarios)

- Add browser permutations for Chromium/Firefox/WebKit and mobile Chromium only
  where behavior is user-visible and stable.
- Add isolated live smoke for customer checkout, owner decision and admin read;
  assert correlation/idempotency headers and teardown state.
- Add cross-portal contract scenarios: order status transitions, voucher
  approval visibility, restaurant/menu projection freshness and unauthorized
  access across all portals.
- Add deterministic fault combinations (one at a time first, then bounded
  pairs): latency + retry, 401 + refresh, 409 + reload, abort + resubmit.

Exit gates for each wave:

1. `npm run test:e2e:coverage` passes with action and difficult-case coverage
   >=90%; target 100% for all P0/P1 rows.
2. No unexpected API requests in strict mock mode; idempotency and auth
   headers are asserted for every relevant mutation.
3. `npm run lint`, `npm run typecheck`, unit tests and build pass.
4. Any newly discovered product bug gets a focused regression test and is
   recorded in the plan before the wave is marked complete.

Recovery: if a case is flaky, first reproduce with one worker and strict mock
mode, then add explicit request synchronization. Do not weaken assertions or
increase retries to hide a product defect. If live sandbox startup is blocked,
keep mock proof and report live proof as unattempted.

## Validation

- Passed: `npm run lint`, `npm run typecheck`, 70 Vitest tests,
  `npm run test:actions`, `npm run build`, mocked Playwright suite and
  action-coverage report.
- Passed: 80 mocked Chromium Playwright tests, including 52 difficult cases;
  action coverage 56/56 (100.0%) and difficult-case coverage 52/52 (100.0%).
- Passed: 124 mocked Chromium Playwright tests, including 96 difficult cases;
  action coverage 56/56 (100.0%) and difficult-case coverage 96/96 (100.0%).
- Passed: 156 mocked Chromium Playwright tests, including 128 difficult cases;
  action coverage 56/56 (100.0%) and difficult-case coverage 128/128 (100.0%).
- Passed: 200 mocked Chromium Playwright tests, including 172 difficult cases;
  action coverage 56/56 (100.0%) and difficult-case coverage 172/172 (100.0%).
- Passed after Wave 3B: `npm run lint -- --quiet`, `npm run typecheck`, 70
  Vitest tests, `npm run build`, and `git diff --check`.
- Passed: 70 Vitest tests, lint, typecheck and production build after the Wave
  2 checkout guard.
- Passed: workflow YAML parse, shell syntax and `git diff --check` in each
  touched repository.
- Attempted: isolated live sandbox on 2026-08-23. Compose boundary, fresh
  package build, control plane and data plane started, but `auth-service`
  remained unhealthy while Flyway started V8 on a fresh database; the runner
  purged the run-scoped project and volumes. No browser live case executed.
- Fixed during live validation: Auth now uses Flyway's session-scoped
  PostgreSQL advisory lock, preserving migration exclusion without holding the
  virtual transaction that blocked V8's concurrent canonical-email index.
  A subsequent sandbox passed the Auth phase and exposed a distinct
  `restaurant-service` bootstrap defect: the durable outbox relay had no
  `KafkaTemplate<String, Object>`. The restaurant Kafka configuration now
  provides its structured JSON outbox producer/template; its focused
  `KafkaProducerConfigTest` passes.
- Passed: a later clean sandbox reached full runtime proof: isolated
  project/volumes, healthy infrastructure and observability, 14 application
  services, simulator readiness and Gateway public reads. This includes a
  successful `order-service` startup after the constructor-injection fix.
- Blocked before browser: the synthetic seed hand-off attempted to reassign
  `RUN_ID`, which is readonly in `sandbox-up.sh`; Bash stopped at that point
  and the trap purged the whole run. Removed the redundant assignment so
  `seed.sh` inherits the validated run ID. `bash -n` and `git diff --check`
  pass; the complete live browser smoke must be rerun after this fix.
- Attempted after the seed fix: the next clean sandbox again built all 21
  modules, but Docker ran out of available memory while starting the large
  application set (`elasticsearch` exited `137`, Kafka became unhealthy).
  Routing and settlement then lost Docker DNS access to `config-server` and
  failed their fail-fast config load. The run was interrupted rather than
  waiting for its 900-second readiness timeout; its trap purged every
  run-scoped container and volume. This is an environment-capacity blocker,
  not evidence of a browser or seed regression.
- Pending: repeat the complete live customer → owner → admin browser smoke
  against a stable backend worktree. The latest retry was correctly rejected
  by the compose artifact-freshness guard because `routing-service` sources
  changed after package creation. Do not bypass that guard or test a stale
  image.
- Attempted after the sequential-memory retry: all services through
  `tracking-service` reached their health/Eureka gates, but the run failed as
  `saga-orchestrator-service` started. Its visible Hikari failure was
  `SQLTransientConnectionException` (connection unavailable/closed); the
  sandbox cleanup removed the initial transcript before it could be fully
  inspected. `sandbox-up.sh` now writes a run-scoped startup transcript and
  `run-live-e2e.sh` retains it on failure in ignored, owner-readable
  `delivery_web/test-results/live-runtime-<run-id>.log` before teardown. The
  next retry must use that artifact to distinguish PostgreSQL capacity from a
  service bootstrap fault; do not guess or weaken the health gate.
- Confirmed with retained Compose state on a subsequent retry: at the
  previous sandbox footprint, `elasticsearch`, `kafka`, and
  `notification-service` were `Exited (137)` while saga failed with the
  downstream `No resolvable bootstrap urls given` error. Search was unhealthy.
  The sandbox-only footprint was reduced again (Kafka 128 MB heap,
  Elasticsearch 128 MB heap, application JVMs 160 MB/48 MB); production
  Compose remains unchanged. A final retry still saw Elasticsearch and Kafka
  `Exited (137)` before saga startup, so the local Docker Desktop 8 GB cap is
  the current live-proof blocker. No browser live case has executed.
- Not run: disposable sandbox customer → owner → admin browser smoke; Docker
  validation has not yet reached Playwright because the backend worktree must
  first remain stable for the full sandbox startup. The CI wrapper is ready
  for the nightly runner.
- Passed again after staging the live-profile work: `npm run
  test:e2e:coverage` completed 200/200 Chromium tests, action coverage 56/56
  and difficult-case coverage 172/172. The live command was intentionally
  stopped before backend runtime startup when another backend task requested
  exclusive access; no live result is claimed from that attempt.
- Passed static backend-harness checks while waiting for the shared runtime:
  `bash -n` for the sandbox/seed/startup scripts and
  `bash scripts/verify-sandbox-config.sh`; this proves syntax and Compose
  boundary only, not live browser execution.
- Passed: `npm run test:e2e:all` executed all 600 cross-browser test slots
  with the explicit all-browser worker limit and finished **600/600** in
  12.5 minutes. `CASE-CUSTOMER-011` was fixed to queue its empty-address
  response before first navigation, eliminating a cross-browser fixture race;
  its Chromium, Firefox and WebKit focused retry passed 3/3.
- Passed after the runner hardening: `node scripts/check-e2e-coverage.mjs`
  reports action coverage 56/56 (100.0%) and difficult-case coverage 179/179
  (100.0%).
- Passed: `npm run verify` after regenerating the handbook source snapshot;
  lint, typecheck, 70 Vitest tests, action contracts, handbook check (218
  operations) and production build are all green.
- Passed: standalone `npm run verify:ci`, which is now the Web GitHub Actions
  gate and does not assume polyrepo-level `../docs` exists on a fresh checkout.
- Passed: the exact CI command `npm run test:e2e:coverage` with the coverage
  worker limit: 207/207 Chromium tests, action coverage 56/56 (100.0%) and
  difficult-case coverage 179/179 (100.0%).
- Passed: the seven Wave 4 cases on Chromium, Firefox and WebKit: 21/21.
- Passed: Wave 5 focused cross-browser run: 36/36 Chromium, Firefox and
  WebKit slots. The exact CI coverage command now runs 219/219 Chromium
  tests, action coverage 56/56 (100.0%) and difficult-case coverage 191/191
  (100.0%).
- Passed after Wave 5 race guards: lint, typecheck, 70 Vitest tests,
  production build and `git diff --check`.
- Attempted full `npm run test:e2e:all`: 655/657 slots passed under the
  concurrent two-worker cross-browser run. Two pre-existing owner-menu modal
  open steps timed out only under that full-run load (Firefox and WebKit);
  each passed when rerun individually with one worker. No Wave 5 case failed
  in the focused 36-slot cross-browser run. Keep the full-matrix flake visible
  rather than masking it with retries or weakened assertions.
- Nightly live workflow now uploads the wrapper's retained
  `live-runtime-*.log` diagnostics alongside Playwright reports when sandbox
  startup fails.

## Result

Implemented in `delivery_web`: Playwright mock Gateway fixtures, 219 semantic
browser tests, a 56-action and difficult-case coverage checker, a real sandbox
smoke wrapper and PR/nightly workflow definitions. Mocked Chromium validation
is 219/219 passing with 56/56 action IDs and 191/191 difficult cases (100.0%).
The focused Wave 5 set passes all 36 desktop-engine slots; the full matrix
attempt passed 655/657 under concurrent load, with the two owner-menu modal
timeouts passing individually at one worker. The seven Wave 4 and twelve
Wave 5 cases therefore have direct cross-browser proof. The
all-browser worker limit keeps Firefox/WebKit stable on the local runner. The
generated handbook source snapshot is current and the repository verify gate
is green; standalone CI uses `verify:ci` while the full local gate retains the
polyrepo handbook source check.
Wave 4 extends beyond the agreed 200-scenario lower bound with distinct risks.
The real Compose smoke
is wired but has not yet reached browser execution in this workspace. Its
first two backend bootstrap defects have focused fixes; the next run requires
a stable backend snapshot so the fresh-artifact guard can remain enabled.
Scheduled CI requires the repository variable `BACKEND_E2E_ENABLED=true` and
the read-only `BACKEND_REPO_TOKEN` secret.

Known limitation: the current product coupon page keeps its create modal open
after a successful response, so the E2E proves the HTTP success and does not
assert modal dismissal. Online payment, realtime maps, performance and visual
diffs remain explicitly out of scope.
