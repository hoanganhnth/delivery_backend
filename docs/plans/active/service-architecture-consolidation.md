# Service architecture consolidation

Date: 2026-10-02

## Status

Active. User approved the root-level service layout and completing one service
before starting the next. Worktree: `.worktrees/backend-service-architecture`
at workspace root, branch `refactor/service-architecture`, base `71218d7`.

## Outcome and approved design

Each business service owns a root-level directory with `domain/`,
`application-api/`, `application/`, `infrastructure/`, and executable `boot/`.
The old `modules/<service>/` and `<service>-service/` are removed only after
their replacement is complete and verified. Domain/application must contain
the actual runtime business decisions; a facade delegating to legacy business
implementations is insufficient. Technical services need only layers justified
by their responsibilities, not empty business scaffolding.

Java 17, Spring Boot 3.5.15, service identity, public/internal HTTP paths, wire
contracts, secrets, feature flags, persistence ownership and existing business
policies remain the compatibility authority. Extracted domain/application
retain 85% line and branch coverage gates. Tests must not be weakened or moved
outside discovery. Saga-to-Dispatch ownership is a separate open design decision
from the earlier conversation, not implicitly authorized by this structural plan.

## Ordered execution

- [x] Routing: prove baseline; move core libraries into `routing/{domain,
  application-api,application,infrastructure}`; move HTTP adapter into
  infrastructure and bootstrap/config into `routing/boot`; update reactor,
  Compose, packaging, source inventories, dependency and coverage discovery;
  verify actual packaged JAR HTTP/auth/provider/fallback paths; remove old host.
- [ ] Web BFF: enumerate all session/proxy flows and migration history; retire
  duplicated legacy session implementations only after controller/adapter
  integration and packaged JAR proofs cover login, refresh races, logout,
  token protection, origins/CSRF, proxy policy and response compatibility.
- [ ] Shipper: complete profile/rating/availability/identity runtime paths,
  then relocate and retire legacy implementations with database/event proof.
- [ ] User and Auth, independently: complete runtime use cases and adapters,
  preserving provisioning, principal identity, session/token and security rules.
- [ ] Restaurant: close decision/rating/serviceability/inventory use cases in
  addition to catalogue; retain transaction, concurrency and event guarantees.
- [ ] Tracking: close REST/WebSocket/publication/lease/recovery use cases;
  preserve location ordering and reconnect fences.
- [ ] Settlement: move actual COD ledger/refund/payment/payout workflows;
  retain provider gating, receipts, locks and financial compensation guarantees.
- [ ] Match: complete single/batch dispatch runtime, expiry, cancellation,
  availability and COD holds behind core use cases.
- [ ] Delivery: complete create/offer/accept/batch/lifecycle/POD/exception and
  cancel workflows; replace legacy delegating facades with real use cases.
- [ ] Order, Notification and Saga, independently: extract complete runtime
  use cases with outbox/inbox, ordering, retry and cancellation recovery proof.
- [ ] Search, Analytics, Promotion, Flash Sale, Livestream and Simulator,
  independently: complete existing Phase 8/9 responsibilities, including
  remaining Kafka evidence, without declaring pending work complete.
- [ ] Remove empty `modules/`; validate full reactor, contract inventories,
  effective dependency boundaries, Compose and packaged runtime evidence.

## Routing implementation and proof

Affected files: `pom.xml`, `routing-service/**`, `modules/routing/**`,
`docker-compose.yml`, `scripts/package-compose-services.sh`, module/coverage/
runtime dependency discovery scripts and HTTP source inventory generators.
Use Maven artifact selectors (`-pl :routing-service -am`) so the executable
artifact identity survives relocation. Add `routing/pom.xml` as an aggregator.
Bootstrap owns composition only; HTTP mapping/authentication stays in
infrastructure; existing application owns fallback, matrix and ETA policy.

Commands:

```sh
mvn -B -pl :routing-service -am clean verify
python3 scripts/verify-module-boundaries.py --self-test
python3 scripts/test-module-boundaries.py
python3 scripts/verify-module-boundaries.py
bash scripts/verify-http-api-inventory.sh
node docs/platform/system/api/generate-http-contract.mjs --check
bash scripts/package-compose-services.sh routing-service
```

Executable proof must launch the packaged `routing/boot` JAR using isolated
local ports, a test-only internal credential and local provider fixture; verify
all three endpoints, forbidden missing/wrong credentials, provider output and
provider-failure fallback. Stop only the child process created by the proof.
Deployment proof includes Compose path resolution and artifact freshness.

## Risks and recovery

- Repository discovery currently assumes `modules/*/*` and `*-service/src`.
  Update discovery with regression fixtures; do not silently drop moved code.
- Keep artifact/service/DNS identities stable while changing source paths.
- Source-derived inventory metadata changes are expected; operation IDs and
  request/response contract shapes must remain unchanged.
- Work in isolation; preserve uncommitted main-checkout documentation changes.
  Commit each fully verified service separately. Reverting a service commit
  restores its prior code/build/deployment paths; no database changes are
  authorized as a side effect of relocation.

## Progress and evidence

- 2026-10-02: Routing baseline `mvn -B -pl :routing-service -am verify -q`
  exited 0; log `/tmp/routing-baseline.log`. Inspection confirms library modules
  have no production entrypoint and host currently owns HTTP/bootstrap.
- 2026-10-02: Routing consolidation verified. Fresh `clean verify` exited 0:
  domain 4, application 7, infrastructure 2, boot 1 tests; zero failures/errors/
  skips. Domain line/branch 93.75/96.97%; application 100/90%. Boot now contains
  only the production entrypoint; controller is in infrastructure and uses the
  real application port. Removed redundant boot dependencies. Old tracked host
  and `modules/routing` sources are replaced by root-level `routing/`.
- Packaged JAR proof passed readiness, all three endpoints, missing/wrong
  credentials, provider response mapping and 503 fallback. A cold JVM caused
  the original 450 ms provider budget to select fallback on first request;
  the contract fixture now explicitly uses 5000 ms, without changing production
  defaults. This is contract/runtime proof, not live Mapbox performance proof.
- Static boundary fixtures: 13 pass; coverage reporter fixtures: 10 pass;
  runtime graph fixtures: 8 pass; JS contract fixtures: 13 pass. All four
  relocated core/boot runtime dependency graphs pass. HTTP inventory retains
  244 operations, 231 schemas; deep comparison excludes only source locations
  and proves unchanged contract metadata. Public-edge manifest check passes.
- Stable-identity packaging, Docker freshness/non-root/read-only image proof
  and Compose topology rendering passed. Compose rendering used a temporary
  path placeholder for the existing mandatory BFF key variable, not a real
  secret or deployment. Logs: `/tmp/routing-final-verify.log`,
  `/tmp/routing-docker-proof.log`, `/tmp/routing-compose-verify.log`.
- Order consumer/client focused proof passed 18 checkout and 6 Routing client
  tests. Full Match verify exited 0: 107 discovered, 101 executed, six existing
  `MATCH_REDIS_INTEGRATION` opt-in tests skipped, no failures/errors. No test
  source or gate was disabled to obtain these results.
- Broad Order/Match reactor verification exited 1 in Order's existing
  `SagaOrderKafkaPostgresIntegrationTest`: missing `retryKafkaTemplate` bean.
  This matches the documented main baseline in the Phase 8/9 stop checkpoint;
  156 other Order tests passed. It is not a Routing regression. Keep this
  failure visible and resolve Kafka ownership/wire transport in the later
  Order/Notification tranches. No full-platform passing claim is made.
- Inline review checked layer dependencies, preserved service identity,
  credential-first HTTP handling, provider/fallback compatibility and test
  discovery at relocated paths. No new database or business policy was added.

## Result

Pending. This plan remains active until all service tranches and final system
validation are complete. A completed Routing tranche does not imply other
services or the earlier Saga-to-Dispatch migration are complete.
