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
- [x] Web BFF: enumerate all session/proxy flows and migration history; retire
  duplicated legacy session implementations only after controller/adapter
  integration and packaged JAR proofs cover login, refresh races, logout,
  token protection, origins/CSRF, proxy policy and response compatibility.
- [x] Shipper: complete profile/rating/availability/identity runtime paths,
  then relocate and retire legacy implementations with database/event proof.
- [x] User: complete runtime profile/provisioning/address/block/lifecycle
  decisions and transactional adapters, preserving identity and outbox/inbox.
- [ ] Auth: complete runtime use cases and adapters, preserving principal
  identity, session/token, lifecycle and security rules.
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

## Web BFF implementation and proof

- Root-level `web-bff/{domain,application-api,application,infrastructure,boot}`
  replaces both `modules/web-bff` and the old host. Boot contains only the
  entrypoint/config; controllers, composition, Auth/Gateway HTTP and PostgreSQL
  adapters belong to infrastructure. Stable artifact/DNS remains
  `web-bff-service`. Compose, baseline and inventory discovery use the new paths.
- Runtime baseline exposed two duplicate V1 migrations, duplicate ORM models,
  an ambiguous token-protection bean, missing controller property binding and
  a refresh-claim query lacking a transaction. Preserve original SQL V1 bytes
  and checksum; remove the duplicate Java migration and legacy entity/repository.
  The full production context now starts against PostgreSQL with ddl validation.
- PostgreSQL regression first failed: logout between refresh read/save was
  overwritten by detached refresh state. Introduced a framework-free atomic
  transition port and a transactional adapter holding a pessimistic row lock.
  Completion/rejection and logout commit locally before upstream or exception.
  The regression now passes, and a two-thread test observes actual PostgreSQL
  blocking and serial generations. No network call runs inside the row lock.
- Retargeted all legacy session/auth/proxy/factory tests to actual use cases and
  adapters. Replaced the legacy constructor-only Spring test with full production
  context assertions in PostgreSQL runtime tests. Removed dead production
  implementations only after their corresponding tests passed. Optional device
  name no longer causes Map.of's null failure; no default is invented.
- Fresh `mvn -B -pl :web-bff-service -am clean verify -q` exited 0: domain 3,
  application 6, infrastructure 22, boot 4 tests, no failures/errors/skips.
  Domain line/branch 98.25/98.53%; application 100/86%, existing gates retained.
  Logs: `/tmp/bff-final-verify.log`; red proof `/tmp/bff-race-red.log`.
- Packaged JAR proof exited 0 with actual Auth/Gateway HTTP adapters, isolated
  PostgreSQL, readiness, safe credential rejection, login/session/encryption,
  proxy server bearer and browser header stripping, origin/CSRF, refresh/logout,
  and second startup against existing Flyway history. No live provider or real
  credential is used. `/tmp/bff-packaged-runtime.log`; CI runs this proof.
- Four resolved core/boot dependency graphs pass. Boundary fixtures 13, coverage
  fixtures 10, runtime graph fixtures 8 and JS fixtures 13 pass. HTTP inventory
  remains 244 operations/231 schemas, deep wire comparison unchanged excluding
  source metadata; public-edge manifest and Compose rendering pass.
- Packaging uses the stable service selector and new boot path. Docker
  freshness/non-root/read-only proof exited 0; `/tmp/bff-docker-proof.log`.
  Inline review checked contract identity, test discovery, transaction commit
  ordering, adapter ownership, SQL checksum and removal of all legacy code.


## Shipper implementation and proof

- Baseline `mvn -B -pl :shipper-service -am verify -q` exited 0.
  Relocated layers into `shipper/`, moved all host adapters into infrastructure,
  retained only production entrypoint/config in boot. All six duplicate Java
  migrations matched byte-for-byte; keep one canonical infrastructure copy.
- Application now owns identity version-gap rejection and BLOCKED/offline
  convergence. New focused tests first failed, then passed. Kafka listener
  retains transport validation, exact event-ID/fingerprint inbox and its existing
  transaction; it delegates business decisions. Removed unused synthetic-receipt
  adapter/port that derived different IDs from principal/version and was not the
  production inbox. Listener is non-final so Spring can apply its transaction.
- Broad unvalidated combined rewrite was rejected by automatic approval review.
  That command did not execute. Continued through bounded changes with separate
  failing/passing proofs, preserving the actual Kafka event and receipt contract.
- PostgreSQL regressions exposed missing live-create identity outbox insertion,
  ignored excludingShipperId in document uniqueness, dropped document images
  and audit timestamps. Restored original pre-refactor behavior from
  `5fd2e08^` implementation, without adding DTO fields, endpoints or policy.
  Each regression first failed and then passed; failed outbox insertion rolls
  back profile. Logs `/tmp/shipper-{outbox,update,documents}-{red,green}.log`.
- Real Kafka/PostgreSQL integration loads production application properties with
  identity consumer and relay enabled. Raw JSON exposed the old JsonDeserializer
  requiring missing type headers; listener accepts String and parses the envelope
  itself, so corrected production to StringDeserializer. Wire/replay/version,
  DLT conflict/gap, committed offsets and actual outbox publication passed.
  `/tmp/shipper-kafka-red.log` retained only beginning/end after 545 MB repeated
  deserialization errors; original issue captured in test report. Green proof
  `/tmp/shipper-kafka-wire.log`. Added Tracking-failure rollback proof to final
  run. Test context closes before fixture containers stop.
- Packaged JAR proof prepared for real RS256/JWKS, PostgreSQL migrations/restart,
  ownership/admin/ratings, profile images/timestamps, Tracking success/failure
  and identity outbox. It uses only temporary fixtures. Exited 0;
  `/tmp/shipper-packaged-runtime.log`, including restart against existing schema.
- Final architecture check rejected compatibility constructors in application-api
  records; removed behavior and updated 19 local call sites. Boundary audit
  now passes. Updated test-context isolation discovery for root-level boot.
- Final escalated clean verify exited 0: domain 9, application 9,
  infrastructure 21, boot 7 tests; no failures/errors/skips. Domain line/branch
  98.39/90%; application 98.85/88.75%. Tracking-failure Kafka proof confirms
  no receipt, unchanged version and online projection after retry/DLT.
  `/tmp/shipper-final-verify.log`. The sandbox-only preliminary run lacked
  Docker/Mockito attach permissions and failed/skipped; it is not success proof.
- Four resolved dependency graphs, architecture boundary and test isolation
  audits pass. Inventory retains 244 operations/231 schemas, deep wire shape
  comparison unchanged excluding source metadata; public-edge and Compose pass.
  Stable-identity package and Docker freshness/non-root/read-only proof pass
  (`/tmp/shipper-docker-proof.log`). CI runs the packaged proof.
- Inline review checked canonical receipt handling, production feature flags,
  transaction/outbox rollback, partial updates, identity ownership, layer/test
  discovery, original migration bytes and wire compatibility. Updated accepted
  architecture decision and AGENTS to describe the approved root-level layout.
  No push/deployment or mutation of shared runtime data occurs.

## User completed

- Baseline verify exited 0 (`/tmp/user-baseline-verify.log`). Root-level layout
  moved into the root-level User group; subsequent evidence below establishes completion.
- Audit: application mostly delegates to JPA adapters. Registration still lives
  in a Spring service; legacy profile/address services remain registered but
  controllers use the new interfaces. Complete registration, provisioning rules,
  default-address transitions, block/lifecycle policy and authorization before
  deleting duplicates. Retarget legacy tests rather than dropping validation.
- Preserve signed provisioning identity, ON CONFLICT winner, profile/outbox
  atomicity, principal/email ownership, address owner locks/single-default index,
  replacement after deleting default, lifecycle replay/gaps and HTTP error shape.
  Add PostgreSQL/Kafka and actual packaged RS256/JWKS/HTTP/restart proof.

- Registration now runs through framework-free DefaultUserRegistrationUseCase;
  Nimbus verifier implements a technical identity port. Removed the old Spring
  registration service after core/wiring tests passed (logs
  `/tmp/user-registration-{core-verify,wiring}.log`).
- Provisioning command validity and immutable identity binding now live in
  domain/application. Adapter invokes core checks within its original transaction,
  including ON CONFLICT winner, then writes the existing outbox. Added domain
  exception HTTP mapping preserving 400/409 envelope. Retargeted provisioning
  tests to actual core/adapter; fixed a fixture that accidentally overwrote its
  empty first lookup and never exercised insertion. Red/green/core verify logs
  `/tmp/user-provisioning-core-{red,green,verify}.log`; clean stack verify exited 0
  `/tmp/user-provisioning-clean-verify.log` before address changes.
- Address workflow now owns default clearing, nullable update preservation and
  promotion after deleting default in application. Infrastructure exposes an
  owner-row-locked transaction callback, raw persistence and bounded lookups.
  Reads are repeated after acquiring the lock. Core red/green/verify passed;
  `/tmp/user-address-core-{red,green,verify}.log`. Retargeted list/default lock
  tests; legacy ownership test still pending migration. Added isolated PostgreSQL
  proof for concurrent provisioning/default selection and both rollback paths;
  currently validating it, not yet claiming User complete or integrating it.

- Block/unblock decisions now run in application through a locked projection
  mutation; replay preserves original metadata. Domain/application own lifecycle
  version and blocked/active projection decisions. Transport retains the exact
  raw fingerprint/event-ID receipt and transaction. Moved address ownership into
  a framework-free access use case; controllers preserve response shapes.
- All legacy registration/profile/address facades and interfaces removed only
  after retargeted actual-stack tests passed. No current source consumes them.
  Boot now contains entrypoint/config and runtime integration tests, with
  infrastructure dependencies rather than duplicated HTTP/JPA/business wiring.
- Final clean verify exited 0 (`/tmp/user-final-verify.log`): domain 5,
  application 24, infrastructure 41, boot 9 tests; no failures/errors/skips.
  Domain/application line and branch coverage all 100%. Includes six actual
  PostgreSQL concurrency/rollback/default-transition tests and real Kafka/raw
  JSON/replay/version baseline/gap/conflict/DLT/offset/relay/block/unblock proof.
- Actual packaged JAR proof exited 0 (`/tmp/user-packaged-runtime.log`): signed
  Auth provisioning/access RS256/JWKS, immutable identity/replay/outbox, profile
  full-replacement semantics, address ownership/default transitions, admin read,
  idempotent internal block/unblock and restart against existing PostgreSQL.
  CI runs this proof. Stable-identity package and Docker non-root/read-only/
  freshness proof pass (`/tmp/user-docker-proof.log`).
- Five resolved runtime graphs, static boundaries, test isolation, Compose,
  HTTP inventory and deep wire-shape comparison pass (244 operations/231 schemas).
  Explicit-claims gate now discovers canonical root infrastructure as well as
  legacy hosts and checks all 16 resource services. Auth repository lookup updated
  to its already-existing infrastructure path; missing discovery now fails closed.
- Full build-baseline gate still fails on the unchanged Auth
  PrincipalInternalController's raw IdentityPrincipal response versus the existing
  public-envelope checker. Kept that checker intact; recorded
  `/tmp/user-build-baseline.log` for the Auth tranche. This is not a claim of a
  passing global reactor/CI; the earlier Order Kafka baseline issue also remains.
- Inline review checked immutable provisioning winner, transactional callbacks,
  owner locks, default rollback/concurrency, nullable/full replacement semantics,
  lifecycle replay/gaps and metadata, exact receipt fingerprints, original
  migrations, production feature flags, real cryptographic auth, source discovery
  and unchanged wire shapes. No shared database or runtime deployment is touched.

## Result

Pending. This plan remains active until all service tranches and final system
validation are complete. A completed Routing tranche does not imply other
services or the earlier Saga-to-Dispatch migration are complete.
