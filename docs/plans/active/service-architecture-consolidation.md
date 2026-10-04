# Service architecture consolidation

Date: 2026-10-02

## Status

Active. User approved the root-level service layout and completing one service
before starting the next. Worktree: `.worktrees/backend-service-architecture`
at workspace root, branch `refactor/service-architecture`, base `71218d7`.
Auth tranche is integrated on `main` at `047ccb2`; Restaurant tranche is integrated on `main` at `2c0eaaa`; Tracking is integrated on `main` at `0c41101`; Settlement is next. The
retired Auth source paths have no tracked files. Ignored local build outputs
and operator PEM files may still remain in a developer checkout under the old
directory; do not delete or package those PEM files as part of source cleanup.

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
- [x] Auth: complete runtime use cases and adapters, preserving principal
  identity, session/token, lifecycle and security rules.
- [x] Restaurant: close decision/rating/serviceability/inventory use cases in
  addition to catalogue; retain transaction, concurrency and event guarantees.
- [x] Tracking: close REST/WebSocket/publication/lease/recovery use cases;
  preserve location ordering and reconnect fences.
- [x] Settlement: move actual COD ledger/refund/payment/payout workflows;
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

## Auth in progress

- Baseline verify exited 0 (`/tmp/auth-baseline-verify.log`). Relocation started
  in worktree only; no Auth completion claim. Existing domain/application login
  and registration are not production-wired and differ from actual retry,
  onboarding, session/token and transaction rules. Do not activate them blindly.
- Authority: current AuthService, AccountSecurityService, TokenService,
  IdentityRegistrationService and existing integration tests. Preserve canonical
  email, password registration resume/race, operator provisioning, lifecycle and
  linked-profile guards, social identity, device/session/refresh-family replay,
  noRollbackFor revocation, email/reset token locking and after-commit effects,
  admission, simulation bindings, principal lookup and outbox/inbox semantics.
- Move all technical HTTP/JWKS/crypto/JPA/SMTP/Google/Firebase adapters and
  composition to infrastructure; boot owns entrypoint/config. Complete real core
  policies/use cases before removing compatibility AuthUseCaseAdapter and legacy
  business services. Retarget validation; add PostgreSQL/Kafka and packaged signed
  JWT/session/provisioning/restart proof. Preserve original migrations.

- Layout clean verify passed after restoring test-only config into infrastructure
  and keeping shared JWT fixture helper in boot. Initial failures were missing
  config/import isolation and helper test classpath, not changed runtime rules.
  `/tmp/auth-layout-verify.log`.
- Public password registration is now production-wired to core. Preserves role
  admission/error messages (including rejecting role whitespace), canonical
  email, credential/role/active guards for pending retries and concurrent winner,
  identity persistence before handoff/handle, RS256 token and 15-minute opaque
  recovery handle. Added granular crypto/handle ports and complete account mapper.
  Domain exceptions preserve 401/409 envelope. Removed duplicate registration and
  handle-issue methods only after actual-stack and PostgreSQL proof passed.
- Core resume regression first failed (`/tmp/auth-registration-core-red.log`),
  then core verify passed (`/tmp/auth-registration-core-verify.log`). Retargeted
  existing registration tests to real core/JPA/password adapters, preserving
  identity-field assertions instead of legacy entity-instance identity.
- Five actual PostgreSQL registration tests passed with no skips/errors/failures:
  Bcrypt/RS256/JWKS/recovery, concurrent identity convergence, takeover/blocked
  guards, handle-failure durability/retry and production HTTP wire shape.
  `/tmp/auth-registration-postgres-test.log`. Auth overall remains incomplete;
  login/session/refresh, operator/social, lifecycle/security/admission/lookup
  and packaged runtime evidence still pending.

- Password login now production-wired to framework-free application: preserves
  eligibility order/messages, raw stored device ID versus trimmed revocation key,
  trusted principal/legacy profile and simulation facts, 7-day session, and exact
  order of old device/token revocation, token issuance, session/credential writes.
  Transaction port wraps the whole flow; crypto/JPA/hash adapters remain technical.
  Legacy login method and compatibility inbound method removed after actual-stack
  and retargeted existing refresh-rotation proof passed.
- Login regression first failed (`/tmp/auth-login-core-red.log`), then core verify
  passed (`/tmp/auth-login-core-verify.log`). New PostgreSQL proof covers real
  login/simulation JWT claims, stored SHA-256 credential, same/other device
  revocation, atomic rollback of new session and old revocation when credential
  write fails, and production login HTTP/401 envelope. Combined registration/login
  PostgreSQL suite has 9 passing tests; `/tmp/auth-login-postgres-verify.log`.
  Retargeted legacy login guards/JWT and refresh rotation fixtures to actual core;
  `/tmp/auth-login-retarget-verify.log` exited 0.
- Auth expiry test flaked because a 1-second JWT expires at integer-second
  precision before the first wall-clock assertion under load. Introduced a
  package-private Clock seam for token signing/parsing and expiry helpers; the
  production constructor still uses the system UTC clock and same TTL. Test now
  advances a fixture clock, retaining before/after expiry assertions without
  sleeping. Wiring test passed (`/tmp/auth-login-wiring-test.log`).

- Refresh/logout now production-wired to framework-free core. Locked credential
  port retains the existing pessimistic join-fetch and exact mutation order.
  Reuse/family mismatch returns a transaction outcome, commits family/session
  revocation, then raises the 401 domain rejection; successor-write failure
  rolls back rotation and session changes. Domain reuse exception remains an
  invalid-token subtype. Duplicate legacy refresh/logout methods removed.
- Core refresh tests and full Auth verify passed. PostgreSQL suite expanded to
  12 passing tests: concurrent refresh/replay durable revocation, successor-write
  rollback/retry, HTTP 200/401 envelope and logout device isolation.
  `/tmp/auth-refresh-postgres-green.log`. The first rollback fixture called an
  abstract Mockito real method; fixed to fail only successor inserts, retaining
  all rollback assertions. No production fix was needed for that fixture.
- Device-session core preserves canonical lookup, locked trimmed device key,
  transactional credential/session revocation and bounded active/unexpired
  latest-login query (100). Core and retargeted HTTP/rotation tests verified:
  `/tmp/auth-session-core-verify.log`, `/tmp/auth-session-wiring-verify.log`.
  Removed legacy session-list/revocation methods after those passed. Additional
  PostgreSQL list/revocation proof passed (see below). Module boundary checker passed:
  `/tmp/auth-core-boundaries.log`. Auth tranche remains uncommitted/incomplete.

- Device-session PostgreSQL proof passed: actual query filters/sorts 122 rows
  and returns the latest 100 usable sessions; revocation isolates the selected
  device. Strengthened the fault fixture to fail session save after token-family
  revocation and verified both token/session changes roll back. Old session
  methods and compatibility inbound entries are gone.
- Recovery status/cleanup now belongs to DefaultRegistrationRecoveryUseCase.
  JPA adapter only hashes/loads/deletes; scheduler delegates to core. Preserves
  required/unknown/expired-handle errors, strict expiry deadline, all four
  nextAction values, profile-link rule and configured retention clamp. Removed
  IdentityRegistrationService after wiring proof. Real PostgreSQL/HTTP verifies
  status headers/envelope, 404/400 and retention without deleting accounts.
- Admission now belongs to DefaultRegistrationAdmissionUseCase. Preserves startup
  percentage/key validation, master-first/allowlist-first precedence, canonical
  email and strict percentage threshold. HMAC adapter keeps unsigned big-endian
  SHA-256 cohort vectors; metrics adapter retains exactly four bounded counters
  without identity labels. Removed RegistrationAdmissionPolicy and legacy inbound
  entry after core/wiring proof. Logs: `/tmp/auth-recovery-core-green.log`,
  `/tmp/auth-recovery-wiring-verify.log`, `/tmp/auth-admission-core-green.log`,
  `/tmp/auth-admission-recovery-green.log`.
- Fresh `mvn -B -pl :auth-service -am clean verify -q` passed after legacy removal:
  157 tests (domain 13/application 24/infrastructure 90/boot 30), zero failures,
  errors or skips, including 16 PostgreSQL workflows. Domain line/branch coverage
  87.8%/88.4%; application both 100%; 85% gates unchanged.
  `/tmp/auth-registration-session-clean-verify.log`.
- Boundary verifier and self-test, HTTP inventory/contract check, explicit-claims
  (16 resource services), Actuator, secret scan and Compose checks passed. Deep
  contract comparison preserved all 244 operations/231 schemas after stripping
  source metadata. Source gates now discover the new Auth/User/Shipper paths and
  recursively inspect main source/config across service layers.
- Corrected the existing public-envelope false positive for the internal principal
  lookup wire contract. Its exact internal path, required authorization call,
  fail-closed secret checks and constant-time comparison are checked before its
  single canonical file is exempted. Existing executable controller tests retain
  200/404/403/invalid-ID behavior; five negative/positive source-gate fixtures also
  pass (`/tmp/auth-baseline-envelope-tests.log`). The public envelope gate remains
  enforced for any other raw controller.
- Full build-baseline gate is still NOT green: it now reaches the unchanged
  SettlementApplicationConfiguration `matchIfMissing=true` violation. Do not
  weaken that gate or change Settlement policy as part of this Auth slice.
  `/tmp/auth-build-baseline-check.log`. Runtime-startup harness also has pre-existing
  source-tree JWT-key paths; align it with file-backed secrets when running the
  final packaged-runtime/whole-system proof, not by copying keys into source.
- Remaining Auth sequence: shared profile/operator/social workflows; lifecycle
  transitions/status sync/outbox/inbox; reset/verification security-token workflows;
  principal/Firebase/simulation authorization; delete remaining business facades;
  simplify boot dependencies; dependency graphs/packaged signed-JWT/Kafka/restart
  proof and final review. Auth remains uncommitted in the worktree and must not be
  merged to main or called complete until the whole service is verified.

- Shared User-profile provisioning decisions now live in framework-free core:
  accept only successful/nonempty matching principal/email/role replies, retain
  exact errors and update lifecycle/version without discarding security/simulation
  facts. HTTP/Internal-Token/circuit breaker remain technical adapters. The social
  compatibility path already calls this core; its remaining orchestration is next.
  Core/domain tests, a real local HTTP exchange proof and whole Auth wiring verify
  passed (`/tmp/auth-profile-core-green.log`, `/tmp/auth-profile-wiring-verify.log`).
- Operator ADMIN/SHIPPER workflows now production-wired to core and one-shot
  runners. Preserve fixed roles, canonical email, credential/active/winner guards,
  verified operator credentials, identity commit before profile handoff and retry
  of linked/unlinked identities. Existing runner/operator assertions retargeted
  without dropping authorization/binding checks. Two PostgreSQL proofs passed:
  same linked identity on retry and signed ADMIN JWT; wrong binding leaves the
  committed SHIPPER identity unlinked and resumable.
  `/tmp/auth-operator-core-verify.log`, `/tmp/auth-operator-postgres-verify.log`.
  Legacy operator methods/helpers removed after proof. Fresh post-removal clean
  verify and final HTTP-503/logging regression verify both exited 0:
  `/tmp/auth-operator-removal-clean-verify.log`, `/tmp/auth-operator-final-verify.log`.
  167 tests (domain 14/application 30/infrastructure 91/boot 32), no failures,
  errors or skips, including 18 PostgreSQL workflows. Application coverage is
  100% line/branch; domain remains above both unchanged 85% gates.
  Module boundaries, HTTP contract (244 operations/231 schemas), strict claims
  and five envelope-gate fixtures pass. Full build-baseline still fails only at
  the known unchanged Settlement matchIfMissing violation; do not claim global
  green. Auth remains uncommitted/unmerged and incomplete. Next step: extract
  social-login orchestration, then lifecycle/security/lookup/simulation and final
  packaged-runtime proof.

- Social login now production-wired to framework-free application core, sharing
  public-role admission and profile binding. Retain Google verified-email checks,
  existing/winning identity role, nullable-active compatibility, device normalization,
  session/token claims and local transaction rollback. Legacy social orchestration,
  its helpers and facade method removed only after runtime proof; AuthService's
  unused crypto/Google/profile constructor dependencies also removed.
  Deterministic PostgreSQL concurrency proof first reproduced a poisoned outer
  Hibernate transaction after a duplicate-email insert. Persistence now inserts
  all identity facts with `ON CONFLICT DO NOTHING` inside the existing transaction,
  then reads the winner for core validation. Two initially missing social requests
  converge on one identity/winning role; credential failures roll back identity,
  verification and session changes. Remote User-profile creation retains the
  previous distributed-transaction limitation; local rollback does not undo it.
  Evidence: `/tmp/auth-social-postgres-red.log`,
  `/tmp/auth-social-postgres-green.log`, `/tmp/auth-social-removal-clean-verify.log`.
- Firebase chat claim construction, feature/identity/role guards and token lifetime
  now live in application core. SDK availability/issuance/error translation stays
  in a technical adapter; the old FirebaseChatTokenService was deleted after proof.
  Retained original claim assertions and added unavailable SDK/error-cause tests.
  HTTP proof uses real signed tokens and a local JWKS HTTP fixture with the unchanged
  production decoder and all validators; USER/ADMIN retain 503 when disabled,
  SHIPPER retains 403. No external DNS dependency or mocked decoder in this proof.
  `/tmp/auth-firebase-core-green.log`, `/tmp/auth-firebase-http-green.log`.
- Fresh post-removal `mvn -B -pl :auth-service -am clean verify -q` exited 0:
  `/tmp/auth-social-firebase-final-clean-verify.log`. 184 tests (domain 15,
  application 39, infrastructure 92, boot 38), zero failures/errors/skips,
  including 24 PostgreSQL workflows. Application line/branch coverage 100%;
  domain 86.36% line / 88.64% branch, above unchanged 85% gates.
  Module-boundary verifier plus 13 fixtures, five envelope-gate fixtures, strict
  explicit-claims and HTTP inventory checks pass. Source-location-independent
  HTTP contract remains unchanged (244 operations / 231 schemas).
  Build baseline still stops at the existing Settlement matchIfMissing violation
  (`/tmp/auth-social-firebase-baseline.log`); no global-green claim.
  Remaining Auth: lifecycle/status sync/outbox/inbox, password-reset/email-verification
  security-token flows, principal/simulation policy, final facade/dependency removal,
  packaged-runtime/Kafka/restart proof and review. Auth remains uncommitted and
  unmerged in `.worktrees/backend-service-architecture`; complete Auth before
  moving to another service. Saga-to-Dispatch is still not claimed complete.

- Account block/unblock, credential revocation, User status-sync pending/version
  decisions, after-commit projection, retry reconciliation and event-mode routing
  now run in framework-free application core. Domain owns status selection from
  active/profile/verification facts. Spring adapters retain JPA locks and versioned
  updates, HTTP Internal-Token/circuit breaker, outbox persistence, REQUIRES_NEW
  callbacks and scheduling. Old AuthService block/status implementation and
  unused constructor dependencies were removed; the legacy sync job no longer
  runs in parallel. An actual PostgreSQL test caught that the old retry-failure
  metadata was rolled back with the post-commit exception. The new workflow
  commits that diagnostic update before rethrowing; the account remains blocked
  and pending until a later retry succeeds. Red/green evidence:
  `/tmp/auth-lifecycle-failure-record-red.log`,
  `/tmp/auth-lifecycle-failure-record-green.log`. PostgreSQL proofs include
  rollback on credential or outbox failure, stale version ACK, unblock states,
  blocked-session invalidation, event-mode outbox and retry recovery.
- `identity.profile.created` now deserializes in the Kafka listener and delegates
  transaction, binding, lifecycle transition, receipt validation and BLOCKED
  snapshot replay to core. The existing retry/DLT annotation and raw-payload
  SHA-256 fingerprint remain at the listener boundary. Three real PostgreSQL
  workflows prove exact-once effect for a repeated event, conflict rejection,
  BLOCKED replay after profile creation, and atomic rollback when receipt write
  fails. Evidence: `/tmp/auth-profile-event-core-green.log`,
  `/tmp/auth-profile-event-postgres-verify.log`.
  Auth remains uncommitted/unmerged; remaining work is security-token workflows,
  principal/simulation policies, facade/dependency cleanup, packaged-runtime and
  Kafka/restart proof. Do not mark the service complete yet.

- Safe account lookup moved to core with canonical email and a projection that
  excludes password hashes. Admin and internal principal controllers now use
  this lookup; the old AuthService, PrincipalLookupService and their compatibility
  lookup port were deleted. Production HTTP behavior and four principal roles
  remain covered by MVC tests. The strict-claims gate now checks the canonical
  lookup rule in core. `/tmp/auth-lookup-legacy-removal-clean-verify.log`.
- Simulation actor bind/unbind and fencing policy now live in application core.
  A generic locked-account port retains pessimistic JPA updates; the JWT adapter
  signs the exact saved run/cohort/version context. The old
  SimulationActorBindingService was deleted. Real PostgreSQL proofs verified
  signed RS256 simulation claims, leased-run/cohort rejection, stale-fence
  rejection and transaction rollback when token signing fails.
  `/tmp/auth-simulation-core-green.log`, `/tmp/auth-simulation-postgres-verify.log`.
  Next: extract password-reset/email-verification issuance, consumption, audit
  and retention from AccountSecurityService; then remove remaining facade,
  simplify dependencies, run packaged-runtime/Kafka/restart proof and review.
  Auth remains uncommitted/unmerged until the whole service is complete.

- Password-reset and email-verification issuance, one-time consumption, uniform
  rejection, credential revocation, audit and retention now run in application
  core. JPA hashing/locks, email after-commit delivery and audit persistence stay
  in infrastructure. The old `AccountSecurityService`, `AuthUseCase` facade,
  `AuthUseCaseAdapter` and `JwksUseCase` were deleted; Auth/JWKS controllers
  now call the corresponding core or technical service directly. Fresh
  `mvn -B -pl :auth-service -am clean verify -q` exited 0 with 218 tests
  (domain 18/application 62/infrastructure 88/boot 50), no failures/errors/
  skips. Domain line/branch coverage is 87/90%; application 99.8/94.95%,
  above unchanged 85% gates. Module boundaries, 13 boundary fixtures, five
  build-contract fixtures, strict claims and HTTP inventory pass. The
  source-independent HTTP contract is unchanged (244 operations/231 schemas).
  Evidence: `/tmp/auth-security-removal-clean-verify.log`.
- `scripts/verify-auth-runtime.py` now launches the packaged Auth JAR twice
  against the same disposable PostgreSQL database with disposable file-backed
  RSA keys. It passed readiness (excluding absent Kafka/SMTP health contributors
  in this fixture), JWKS, public security requests, internal-secret denial,
  registration and recovery after restart. CI runs this packaged proof.
  Evidence: `/tmp/auth-packaged-runtime.log`. Resolved dependency review and
  final Auth integration remain; Auth is still
  uncommitted/unmerged in the isolated worktree.
- A real Kafka and PostgreSQL Testcontainers proof now sends a profile event
  through the listener, observes the committed inbox/account change, consumes
  the relayed `identity.status.changed` event, and waits for the replayed input
  offset before asserting one outbox effect. Fresh clean verify passed 219 Auth
  tests with zero failures/errors/skips. Evidence:
  `/tmp/auth-kafka-postgres-verify.log`, `/tmp/auth-final-clean-verify.log`.
  The canonical runtime-startup script now validates operator-mounted JWT key
  files instead of referencing the retired `auth-service/src/main/resources`
  location; syntax and local-runtime safety checks pass.
- Auth's resolved Maven runtime graphs pass for domain, application API,
  application and boot. `scripts/package-compose-services.sh auth-service`
  produced a fresh manifest; the production Dockerfile built the relocated
  `auth/boot` artifact and the temporary image reports non-root UID/GID
  `10001:10001`. Secret scanning passed. The full build-baseline gate still
  stops only at the pre-existing Settlement `matchIfMissing=true` violation,
  which this Auth tranche does not change. Evidence:
  `/tmp/auth-runtime-dependency-tree.log`, `/tmp/auth-compose-package.log`,
  `/tmp/auth-docker-build.log`, `/tmp/auth-secrets-check.log`,
  `/tmp/auth-final-build-baseline.log`.

## Restaurant next tranche

- Baseline `mvn -B -pl :restaurant-service -am clean verify -q` exited 0:
  319 tests (domain 25/application 61/infrastructure 20/boot 213), zero
  failures/errors/skips. Evidence: `/tmp/restaurant-baseline-clean-verify.log`.
- Existing core already owns catalogue create/update/read and management access,
  while runtime decision publishing, rating, serviceability and inventory
  reservation/event processing still make material decisions in infrastructure.
  The old host also contains catalogue lifecycle, order-cache validation and
  HTTP/Kafka adapters. Extract those workflows to core with PostgreSQL/Kafka
  concurrency and idempotency proof before removing the old host.
- Ordered work: map existing API/event/transaction contracts; relocate layers
  and boot; extract order-decision and rating policy; extract serviceability
  geometry/zone workflow; extract inventory reserve/consume/release and event
  receipts; retire duplicated host services; verify full tests, contracts,
  packaged JAR/restart, Kafka relay and Compose image; integrate one complete
  Restaurant tranche.
- Rating submission/moderation/read decisions now run in framework-free
  `DefaultRestaurantRatingUseCase`; JPA mapping, aggregate queries, PostgreSQL
  locking and Spring transactions are adapters. The old infrastructure rating
  use-case implementation and eligibility port were removed. Existing ordering,
  duplicate-conflict, HTTP and approved-read assertions remain. Four new real
  PostgreSQL tests cover approval/rejection aggregates, simultaneous duplicate
  submissions, aggregate-write rollback and identities larger than int32.
  They exposed the old `(integer,bigint)` advisory-lock call, which PostgreSQL
  does not support. The lock now folds the full identity to a deterministic
  int32 key in the existing namespace; collisions only add serialization.
  Red/green evidence: `/tmp/restaurant-rating-postgres-lock-red.log`,
  `/tmp/restaurant-rating-postgres-green.log`. Fresh post-removal clean verify
  exited 0: 329 tests (25 domain/67 application/20 infrastructure/217 boot),
  zero failures/errors/skips; module boundaries and diff checks pass.
  `/tmp/restaurant-rating-final-clean-verify.log`. Restaurant remains in the
  isolated worktree until all remaining workflows and runtime proofs are done.
- Confirmation/rejection now use framework-free
  `DefaultRestaurantOrderDecisionUseCase`. Core owns input admission, order-lock
  ordering, eligibility and authoritative/replayed/opposite decision behavior;
  the JPA adapter retains the existing canonical SHA-256, legacy outbox
  fingerprint fallback, tracing and event envelope. The old publisher business
  service was removed and controller/HTTP conflict mapping now use the core.
  Existing H2 replay and validation tests were retargeted without dropping
  assertions. Three PostgreSQL proofs verify retained fingerprints after outbox
  pruning, one winner/event for concurrent opposite decisions, and full rollback
  on outbox failure followed by successful retry. Fresh clean verify exited 0
  with 338 tests, zero failures/errors/skips; module boundaries pass.
  `/tmp/restaurant-decision-core-verify.log`,
  `/tmp/restaurant-decision-postgres-verify.log`,
  `/tmp/restaurant-decision-final-clean-verify.log`.

## Result

Pending. This plan remains active until all service tranches and final system
validation are complete. A completed Routing tranche does not imply other
services or the earlier Saga-to-Dispatch migration are complete.

### Restaurant root layout (in progress)

- Relocated all five layers to `restaurant/{domain,application-api,application,infrastructure,boot}`. Production HTTP/Kafka/JPA adapters and canonical migrations now live in infrastructure; boot keeps the entrypoint, runtime properties and Spring/database integration tests. Reactor and Compose use the root layout while retaining `restaurant-service` artifact/DNS identity.
- Clean verify after relocation passed: domain 25, application 73, infrastructure 159 and boot 81 tests (338 total), zero failures/errors/skips; `/tmp/restaurant-root-layout-clean-verify.log`. Restored the direct Flyway/H2 migration test to boot after initial test relocation exposed its runtime dependency.
- Boundary audit and HTTP inventory passed; regenerated source metadata and compared the full HTTP contract excluding source locations: all 244 operations/231 schemas unchanged.
- Restaurant remains incomplete: serviceability, inventory and catalog lifecycle still need business decisions extracted into core. Keep this tranche in the refactor worktree until the full service is verified.

- Serviceability geometry extraction: immutable polygon validity, Vietnam coordinate bounds and boundary-inclusive point containment now belong to domain; GeoJSON decoding remains in infrastructure. Retained all original adapter tests and added domain vectors for orientation, concavity, boundary points, invalid coordinates, closure/area and immutable rings. Full clean Restaurant verify passed with 343 tests (domain 30/application 73/infrastructure 159/boot 81), no failures/errors/skips; `/tmp/restaurant-geometry-clean-verify.log`. Management/evaluation orchestration still awaits extraction.

### Restaurant serviceability core and proof

- `DefaultRestaurantServiceabilityUseCase` now owns management authorization (principal identity and legacy fallback), zone revision admission, defaults/partial updates and ordered fail-closed coverage evaluation. Core depends only on fact/geometry/incident/transaction ports. `JpaServiceabilityAdapter` only maps and persists; GeoJSON decoding and logs stay in infrastructure. Removed the old `RestaurantServiceabilityService`; production composition directly wires the new core.
- Preserved existing flags/defaults, ADMIN behavior, exception messages, geometry policy, zone ordering, all original assertions and HTTP responses. Moved serviceability errors into domain and proved actual Spring MVC annotation handling of 403/404/409. Added explicit read-only transactions to the shared port and preserved write transactions in existing rating/decision workflows.
- Application core proof passed (7 additional scenarios). Five PostgreSQL tests passed with production composition: core bean selection/read-only transactions, default/priority/revision/partial update/delete, ownership and malformed stored polygons, rollback after a real JPA insert and concurrent optimistic updates with exactly one winner. Initial two PostgreSQL fixture failures were caused by attempting Mockito `callRealMethod` on a Spring Data interface; moved the barrier/fault to the concrete JPA adapter without weakening assertions.
- Full clean verify passed: domain 30/application 80/infrastructure 159/boot 86 = 355 tests, no failures/errors/skips; `/tmp/restaurant-serviceability-final-clean-verify.log`. The subsequently added HTTP mapping test passed separately (infrastructure now 160; combined observed suites 356 tests), `/tmp/restaurant-serviceability-http-errors-verify.log`. Core coverage remains above required thresholds: domain line/branch 93.92/97.73%, application 100/95.77%.
- Boundaries, test-context isolation, HTTP inventory and contract freshness checks passed. Restaurant remains in progress in the worktree: inventory/event consumption, catalog lifecycle/order cache and final packaged runtime proof remain.

### Restaurant inventory capacity extraction (in progress)

- Domain `InventoryCapacity` now owns availability, reserve/consume/release/compensate arithmetic, revision admission and overflow checks. The production inventory implementation uses immutable capacity transitions; its JPA entity delegates the former availability arithmetic to domain. Kept ascending row-lock order, complete-cart validation before writes, TTL/state transitions, messages and existing feature flags.
- Added five domain cases for stock/revision conservation, invalid/missing capacity, inconsistent commit/release, stale updates and overflow. Retained every original inventory assertion. Full clean Restaurant verify passed with 361 tests: domain 35/application 80/infrastructure 160/boot 86, zero failures/errors/skips; `/tmp/restaurant-inventory-capacity-clean-verify.log`.
- Inventory orchestration still lives in the infrastructure service and must be moved next: reserve/replay/commit/release/expiry and order-event receipt decisions. This is a verified intermediate extraction, not completion of inventory or Restaurant.

### Restaurant inventory orchestration and PostgreSQL races

- `DefaultMenuItemInventoryUseCase` now owns reserve/replay/commit/release/expiry, admission, complete-cart validation, deterministic lock ordering, management authorization and advisory reads. It uses immutable domain reservation/stock facts, a clock, transaction port and `InventoryStorePort`; no framework dependency or delegation to a legacy business service. Added `JpaInventoryAdapter` and conditional inventory composition; deleted `MenuItemInventoryReservationService`. Original inventory tests now exercise this exact core/JPA mapping and retain all assertions.
- Added 11 framework-free core cases and seven real PostgreSQL cases: whole-cart rollback, duplicate/replay binding, last-unit contention, commit/cancellation compensation, expiry, inventory editing, insert failure rollback and forced reservation-lock/cancellation races. HTTP mapping preserves inventory 403/404 responses.
- Actual PostgreSQL contention exposed the existing DISTINCT/fetch-join reservation locking query loading state before follow-on locking. Two simultaneous commits then read stale RESERVED state; `/tmp/restaurant-inventory-follow-on-lock-red.log`. Locking the single root row before lazily reading immutable lines fixes the issue; the same contention test passes. PostgreSQL restriction: https://www.postgresql.org/docs/16/sql-select.html ; follow-on locking reference: https://docs.hibernate.org/orm/6.5/userguide/html_single/#locking-follow-on .
- Actual cancellation between expiry candidate selection and locking exposed another stale entity snapshot in the same persistence context; `/tmp/restaurant-inventory-expiry-race-red.log`. The candidate query now returns only IDs (same RESERVED/expiry criteria, order and 100-row limit). Core relocks/rechecks the current state without preloading stale entities; the same cancellation test passes.
- Focused proof `/tmp/restaurant-inventory-postgres-green.log` and full clean verify `/tmp/restaurant-inventory-final-clean-verify.log` passed: domain 35/application 91/infrastructure 160/boot 93 = 379 tests, no failures/errors/skips. Business flags, TTL defaults, messages, public commands/results, transaction nesting and database migration bytes remain intact.
- Restaurant remains incomplete: inventory order-event receipt/replay decisions still need core extraction, followed by catalog lifecycle, order-cache and complete packaged/runtime verification.

### Restaurant inventory event core and receipt proof

- `DefaultInventoryOrderEventUseCase` now owns receipt admission, exact replay binding and CREATED→COMMIT / CANCELLED-or-REFUND→RELEASE decisions within one required transaction. Domain source/action enums are independent of Kafka. `JsonInventoryOrderEventAdapter` only decodes JSON, validates wire fields, canonicalizes retry topic identity and computes the unchanged raw-payload SHA-256. `JpaInventoryReceiptAdapter` only implements H2/PostgreSQL insert-if-absent and receipt mapping. Removed the former `MenuItemInventoryOrderEventProcessor` business implementation; the Kafka listener now delegates transport to the JSON adapter and ACKs after the core transaction returns.
- Retained all original replay and transition assertions. Added six framework-free event-core cases and transport tests for refund retry topic identity and malformed inputs failing before core admission. Four additional PostgreSQL cases (11 inventory PG cases overall) prove receipt/stock atomic rollback and successful retry, concurrent duplicate event one-receipt/one-transition behavior, canonical retry identity/raw-fingerprint contradiction, refund/cancellation compensation and events without reservation IDs.
- Full clean Restaurant verify passed: domain 35/application 97/infrastructure 162/boot 97 = 391 tests, no failures/errors/skips; `/tmp/restaurant-inventory-event-final-clean-verify.log`. Focused event proof: `/tmp/restaurant-inventory-event-postgres-verify.log`. Core coverage: domain line/branch 90.61/98.05%, application 100/96.92%. Boundaries, context isolation and HTTP contract freshness checks passed.
- Inventory business orchestration and receipt decisions now run in application, with domain capacity/reservation facts. Complete Restaurant still requires catalog lifecycle, order-cache extraction and final packaged HTTP/Kafka/runtime validation before integration into main.

### Restaurant catalog lifecycle orchestration

- Added `DefaultCatalogLifecycleUseCase`: management authorization (including legacy fallback), version admission/missing-version telemetry, transition/no-op decisions, whitelisted audit facts and Search DELETE/UPDATE selection now run in application. `JpaCatalogLifecycleAdapter` handles only snapshots, entity writes/conflict mapping, audit metadata, telemetry and outbox transport. Removed `CatalogLifecycleService`; HTTP facades translate DTOs/results and authorization errors while invoking the transaction-wrapped core for both change and archive.
- Moved framework-independent not-found/stale-version errors into domain, retaining messages and 404/409 mapping; domain catalog authorization maps to 403. Retargeted all nine lifecycle assertions and existing ownership/facade tests rather than dropping them. Seven new core cases verify ordered effects, versions/no-op behavior, restore restrictions, legacy ownership and missing/invalid aggregates.
- Six PostgreSQL tests verify atomic state/audit/Search outbox commit, idempotent no-op, archive via the HTTP facade (including mandatory outbox transaction), ADMIN restoration/menu independence, audit-write rollback, outbox failure rollback/retry and simultaneous commands at one version producing one audit/event.
- Full clean verify passed: domain 35/application 104/infrastructure 162/boot 103 = 404 tests, no failures/errors/skips; `/tmp/restaurant-lifecycle-final-clean-verify.log`. Focused core/PG proofs: `/tmp/restaurant-lifecycle-core-verify.log`, `/tmp/restaurant-lifecycle-postgres-verify.log`. Boundaries, test-context isolation and HTTP contract freshness passed.
- Restaurant remains in progress: canonical Order validation (formerly order-cache) still needs core extraction, then packaged HTTP/Kafka/runtime, dependency and architecture audits before main integration.


### Restaurant canonical Order validation tranche

- Replaced the old `OrderCacheValidationServiceImpl` business implementation with `DefaultOrderValidationUseCase` in framework-free application. Core owns canonical restaurant/menu eligibility, operating schedule, serviceability and optional stock admission, totals and unchanged error/result decisions. `JpaOrderValidationCatalogAdapter` only retrieves unfiltered canonical snapshots; `OrderValidationHttpAdapter` only maps the existing wire DTO. Production composition calls this core directly; existing controller/interface contracts remain.
- Extended the shared transaction port with an explicit repeatable-read operation. The Spring adapter uses a read-only `REPEATABLE_READ` template, preserving the old checkout boundary. API commands/results are separate empty-body records; the architecture verifier initially rejected nested record bodies, so records were split without weakening the gate. Existing fake transaction fixtures implement the added operation.
- Preserved canonical prices/names over client payload, duplicate item totals, missing/foreign/inactive/malformed items, lifecycle/schedule failures, null quantities, disabled inventory/serviceability behavior and the existing empty-cart behavior. The seven original adapter tests and persistence assertion paths now execute the actual new core and JPA mapping. Seven pure application cases cover these decisions.
- Three actual PostgreSQL tests prove production core/HTTP mapping (including every restaurant/item response field), current archived/inactive rejection and concurrent snapshot consistency. A committed writer pauses the restaurant and changes its menu price after the reader fetches the restaurant: that first validation still uses the original price/status, and the next call sees both updates. The test also observes `transaction_isolation = repeatable read` and `transaction_read_only = on`. Test fixture uses disabled capability flags; enabled serviceability/inventory have separate existing PG proofs and still require the final combined runtime proof.
- First PG run failed at context creation because Mockito subclass spying cannot wrap a final adapter. Made the technical adapter non-final, matching existing spy-enabled adapters, and corrected explicit fixture property names. Green three-test proof: `/tmp/restaurant-order-validation-postgres-green.log`.
- Fresh `mvn -B -pl :restaurant-service -am clean verify -q` exited 0: 414 Restaurant tests (35 domain, 111 application, 162 infrastructure, 106 boot), zero failures/errors/skips. `/tmp/restaurant-order-validation-final-clean-verify.log`. Module boundaries, test-context isolation, diff whitespace and HTTP contract freshness pass; inventory remains 244 operations/231 schemas.
- Inline review compared the old decisions and messages, canonical data lookups, null input/line mapping, transaction participation and feature-flag semantics against the replacement. No public API/event shape or new checkout policy was introduced. Restaurant remains unintegrated pending full packaged HTTP/Kafka/Docker and dependency/service audits; other service migrations and Saga-to-Dispatch remain in scope.


### Restaurant production Kafka/PostgreSQL proof

- Added a real Kafka/PostgreSQL integration test loading production `application.properties`, enabling the actual inventory consumer, retry topics and leased outbox relay. Fixture topics/groups and disposable containers isolate proof data; context closes before container shutdown. Added only the test-scoped Testcontainers Kafka dependency.
- Combined enabled serviceability/inventory checkout uses canonical price and real zone/stock data, rejecting outside-zone and insufficient-stock requests. The first run exposed a fixture expectation typo (`OUTSIDE_SERVICE_AREA` versus the existing `OUTSIDE_ACTIVE_ZONES` contract); corrected the assertion to existing authority without changing production policy.
- Raw JSON events prove commit and exact replay, contradictory same-event payload into owner DLT, refund compensation/replay and cancellation release. A forced exception after the managed reservation mutation rolls back receipt, reservation and stock together. The retry is held before mutation so the test observes the rolled-back database externally, then releases it and proves successful canonical-topic receipt, one transition and exactly two attempts.
- Actual Search lifecycle outbox publication is observed in Kafka with entity/action/key/event-ID header, while PostgreSQL marks the leased row SENT. Consumer group committed offsets are inspected through Kafka AdminClient. Four admitted receipts remain after the conflicting payload; no direct listener invocation substitutes for transport proof.
- Focused proof `/tmp/restaurant-kafka-postgres-proof-green.log` exited 0. Fresh full `clean verify` `/tmp/restaurant-kafka-final-clean-verify.log` exited 0: 415 Restaurant tests (35 domain/111 application/162 infrastructure/107 boot), zero failures/errors/skips. Boundaries, context isolation and diff whitespace pass.
- Restaurant remains in progress pending packaged HTTP/JWT/migration-restart proof, final resolved-dependency/adapter audit and Docker packaging/security validation before integration. Read-only cross-service inspection also found that Order's inventory client sends only Internal-Token while Restaurant security currently authenticates menu inventory routes; verify this concrete route in the packaged proof before proposing a fix.


### Restaurant packaged HTTP runtime and private route repairs

- Added `scripts/verify-restaurant-runtime.py` and a CI step after the fresh Maven build. It launches the actual executable JAR with disposable PostgreSQL and local public JWKS, generates temporary RS256 keys/tokens, exercises HTTP and restarts the child JVM against the existing migrated schema. No shared runtime data, operator keys or deployment is touched.
- First actual JAR startup failed with `NoClassDefFoundError: IdentityPrincipal`: the boot POM's direct test-scoped `identity-contracts` overrode infrastructure/client runtime dependencies. Removed that obsolete test-only override; the canonical infrastructure dependency now packages the contract. Red `/tmp/restaurant-packaged-runtime-red.log`; JAR entry and resolved graph explicitly confirm the restored runtime contract.
- The actual Order-style request (Internal-Token only) then reproduced HTTP 401 for inventory reservation. Authority: existing `InventoryReservationClient`, controller private credential checks and `docs/platform/system/security.md` require the separate internal credential on exact private routes. Security now allows only the three POST patterns for reserve/commit/release through to the existing credential guard. Management, other methods and unknown internal paths still require JWT; missing/wrong internal credentials remain 403. Red `/tmp/restaurant-packaged-runtime-route-red.log`.
- Added three real-filter-chain HTTP regressions for no-Bearer successful reserve/commit/compensation, missing/wrong credential without mutation, and method/path/management isolation. Focused `/tmp/restaurant-internal-route-focused.log` exited 0. Fresh full `clean verify` `/tmp/restaurant-packaged-runtime-final-clean-verify.log` exited 0: 418 Restaurant tests (35 domain/111 application/162 infrastructure/110 boot), zero failures/errors/skips.
- Runtime proof validates canonical price/name instead of client values, real zone/stock admission, principal ownership/foreign rejection, invalid/no JWT, internal credentials, reserve/commit/release stock conservation, stale lifecycle 409, archive/history/discovery/checkout behavior, ADMIN-only restore to PAUSED, four audit rows/six Search outbox rows and persistence after restart. Two test assumptions about archive were corrected against the existing domain/read contract (history detail remains available, restore target is PAUSED); production policy was preserved. Complete green proof `/tmp/restaurant-packaged-runtime-complete-green.log` exited 0.
- Fresh runtime dependency trees were generated after installing the verified reactor artifacts: `/tmp/restaurant-runtime-install.log`, `/tmp/restaurant-runtime-dependency-tree-green.log`. Four graphs passed the actual boundary checker (domain 1 node/API 2/application 3/boot 190), including no foreign service implementation. Preliminary sandbox tree failed because Maven attempted to write its local resolver cache; that failed run is not audit proof.
- Boundaries, context isolation, full HTTP contract and public-edge freshness pass (244 operations/231 schemas; 93 edge routes/157 patterns). Inline review checked scope mediation/JAR contents, exact method/path matchers, unchanged internal guard and public-edge exclusion, actual wire requests and fixture cleanup. Restaurant remains unintegrated pending Docker freshness/security and the final adapter/service audit. Entire system plan and Saga-to-Dispatch work remain active.


### Restaurant obsolete ownership wrapper cleanup

- Production callers used `RestaurantOwnershipPolicy` only for its deployment flag; its `assertCanManage` decision wrapper had no runtime caller after earlier core migrations. Replaced it with technical `RestaurantOwnershipSettings`, removed the unused wrapper and duplicate `ManagementAccess` result. Existing flag name/default and transport command values remain.
- Moved all six former ownership wrapper vectors into application against actual `DefaultRestaurantManagementAccessUseCase`: missing principal, principal mismatch despite matching legacy identity, principal success without fallback, gated unmigrated fallback and ADMIN. Domain exceptions are asserted at their owning core boundary; existing facade/HTTP tests retain Spring authorization response proof.
- Focused adapter/core/ownership fixtures passed; fresh `/tmp/restaurant-final-adapter-clean-verify.log` full clean verify exited 0 with 418 tests (35 domain/117 application/156 infrastructure/110 boot), zero failures/errors/skips. No business policy or API/event shape was changed.
- Final controller audit found two remaining ownership decisions outside core: internal principal/legacy lookup and legacy creator admission for order confirm/reject. They must be moved next while preserving their distinct existing semantics; Restaurant remains unintegrated pending this work and final runtime/Docker proof.


### Restaurant final ownership, Livestream authority and completion audit

- Moved internal ownership queries and confirm/reject authorization to `DefaultRestaurantOwnershipLookupUseCase`, backed by a read-only transaction and canonical JPA facts. Removed three obsolete ownership repository queries. Internal old-client creator lookup and gated principal-aware fallback/counter remain compatible.
- Final review found confirm/reject still authorized only legacy creator. The repository specification (`docs/services/restaurant_and_menu.md`, ownership policy) makes principal identity authoritative and restricts legacy fallback to unmigrated rows with enforcement off. Corrected this gap: assigned principal owner succeeds even when legacy creator differs; former creator fails before Order eligibility or outbox mutation. Two regression tests failed against a temporary creator-only implementation, then passed against the canonical core (`/tmp/restaurant-order-owner-legacy-red.log`). Event `actorUserId` remains the trusted legacy actor identity.
- Moved the remaining repository-backed HTTP business decision for Livestream products to `DefaultLivestreamProductUseCase`. JPA supplies unfiltered canonical metadata within core-owned read-only transaction; application checks AVAILABLE and restaurant scope. Internal token, invalid-scope 400, unavailable/missing/foreign 404, response fields and Gateway exclusion remain unchanged. Existing controller tests exercise the real replacement; four core tests cover canonical metadata, every status, missing/foreign/parentless facts and invalid scope before persistence.
- Fresh full `mvn -B -pl :restaurant-service -am clean verify -q` passed: 434 tests (domain 35/application 128/infrastructure 158/boot 113), zero failures/errors/skips; `/tmp/restaurant-final-livestream-clean-verify.log`. Domain line/branch coverage 87.73/98.05%; application 99.86/96.81%; all existing 85% gates retained.
- Final actual JAR proof passed (`/tmp/restaurant-final-livestream-runtime.log`): real PostgreSQL migrations/restart, RS256/JWKS authentication, owner/admin/foreign role checks, canonical checkout with serviceability/stock enabled, inventory compensation/private credentials, lifecycle audits, Search and decision outbox rows, actual load-balanced Order HTTP eligibility, confirm/reject/replay/conflict, rating moderation and Livestream metadata/secret/scope/status. Order outage returns the existing generic HTTP 500 and creates no decision/outbox; an earlier fixture assumption of 503 was corrected without changing production error policy. Fixtures do not prove a live Auth/Order/Search deployment.
- Final adapter review confirms HTTP controllers do not import repositories, core has no framework dependency, business writes/receipts/outbox retain core transaction boundaries, and JPA/JSON/HTTP/Kafka/lease/scheduler adapters retain technical responsibilities. Existing facade classes are wire/exception translators to actual core use cases; no legacy business implementation is delegated to.
- Docker freshness rejection and Compose configuration checks passed (`/tmp/restaurant-docker-freshness.log`, `/tmp/restaurant-compose-config-green.log`). Compose render proof supplies a placeholder Web BFF encryption-key path, without reading operator key material. Final Compose Restaurant package manifest is generated (`/tmp/restaurant-final-compose-package.log`). Final Docker security passed (`/tmp/restaurant-final-docker-security.log`): non-root 10001:10001, read-only application filesystem, writable temporary directory. Fresh final runtime dependency graphs passed (domain 1/API 2/application 3/boot 190 nodes), without framework dependencies in core or foreign service implementations (`/tmp/restaurant-final-livestream-dependencies.log`).
- HTTP wire shape remains identical (244 operations/231 schemas), with controller source metadata updated; public edge remains 93 routes/157 patterns. Static module boundaries and all 17 test-context isolation checks passed. No tracked old `restaurant-service/` or `modules/restaurant/` source remains. Whole-backend consolidation and Saga-to-Dispatch remain pending.


### Restaurant integration and Tracking baseline

- Fast-forwarded main to `2c0eaaa` after checking the complete branch diff against local tracked/untracked user changes; no overlap. Main module boundaries and HTTP contract freshness passed. Retired Restaurant host contained only ignored `target/` output and Finder `.DS_Store`; removed those after path inventory. `restaurant-service/` and `modules/restaurant/` are absent in the main checkout. User product/reference edits and Auth operator PEM files were preserved.
- Tracking baseline `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-consolidation-baseline.log`): domain 4/application 4/infrastructure 5/host 72 = 85 tests, zero failures/errors/skips. No baseline process remains live.
- Runtime mapping: REST uses `DefaultTrackingService`, but WebSocket still orchestrates Redis, timestamps, fanout and Kafka inside `ShipperLocationWebSocketHandler`. Legacy `ShipperLocationService` also remains, with a test-only `LegacyTrackingPort` constructor in the REST controller. `ShipperAvailabilityService` owns tombstone decisions; `ShipperPublisherSessionManager` and `PublisherLeaseExpirySweeper` own generation/grace/recovery decisions; `LocationHistoryService` owns receipt/replay, sampling, ordering and bounded query decisions; `ShipperIdentityResolver` owns principal/projection/fallback policy. JPA entities/migrations are already in module infrastructure, while Redis/Kafka/HTTP/WebSocket adapters and composition remain in the legacy host.
- Authority for extraction is `docs/services/tracking_service.md`, `docs/operations/location-history.md` and the existing executable behavior/tests. In particular explicit offline must emit a timestamped tombstone even without cached coordinates. Current REST core `markOffline` instead throws when cache is absent, whereas lease/grace availability emits the required tombstone. Both HTTP offline routes in fact used the incomplete REST core; the later offline tranche corrects this wiring. This is an existing documented-contract gap to close when unifying the actual core, without substituting the legacy delegating facade. Publication error propagation, exact sources/payloads, simulation context, time encoding, lease fences and receipt transaction guarantees must be verified separately.
- Next ordered work: extract principal/projection identity policy and verify production wiring first; then unify location/offline publication in real core, lease/recovery and history use cases. Only after those runtime paths and PostgreSQL/Redis/Kafka/WebSocket/JAR proofs pass should Tracking move to the root layout and its old host be retired. Tracking remains unchecked in the service completion list; Match/Delivery/Order and Saga-to-Dispatch are still pending.


### Tracking principal identity core

- Extracted principal/legacy tuple validation, canonical projection match and gated missing-projection fallback into framework-free `DefaultShipperIdentityUseCase`. `JpaShipperIdentityReadAdapter` supplies unfiltered local mapping facts; production composition wires this core. `ShipperIdentityResolver` now only translates domain denial to the existing Spring security exception and increments the existing counter when core reports actual fallback.
- Five application tests cover distinct principal/legacy/shipper identities, both enforcement settings, divergent mapping without fallback, missing mapping, invalid tuple before reads and database failure propagation. Three real PostgreSQL/Spring tests prove migrations/JPA, production REST and WebSocket identity wiring (both derive shipper 987 from principal 100/legacy 200), denied mapping before location writes and exactly one counter increment for actual pre-enforcement fallback. Redis/location publication and publisher-lease collaborators are replaced only for this bounded identity proof; their complete runtime proof remains pending.
- Focused core and PG tests passed (`/tmp/tracking-identity-core-focused.log`, `/tmp/tracking-identity-postgres.log`). Fresh complete `clean verify` exited 0 (`/tmp/tracking-identity-final-clean-verify.log`): domain 4/application 9/infrastructure 5/host 75 = 93 tests, zero failures/errors/skips. Domain line/branch 90.48/100%; application 94.59/100%, retaining all 85% coverage gates.
- Static boundaries, all 17 test-context isolation checks, HTTP inventory/contract freshness (244 operations/231 schemas), public edge freshness (93 routes/157 patterns) and diff whitespace passed. No HTTP/event shape, rollout flag, fallback metric tag or existing authorization message changed. Existing generic HTTP 500 translation of the Spring identity-denial exception is retained; authorization error contract cleanup is not silently bundled into this extraction.
- Tracking remains in the worktree until the full service is complete. Next is offline/tombstone orchestration with absent and partial cached coordinates, then location publication, lease/recovery, projection event processing and sampled history; root relocation/host retirement must follow full runtime verification.


### Tracking canonical offline/tombstone orchestration

- `DefaultShipperAvailabilityUseCase` now owns cached/missing decisions, server timestamps, Redis save/removal before Kafka, OFFLINE_TOMBSTONE source and optional authorized fanout after successful publication. Read/write/publish/fanout stay behind typed ports. Cached scalar facts retain partial coordinates, telemetry and optional distance; immutable application facts do not impose online-coordinate validation on a tombstone. Existing lease/grace/sweeper callers use the Kafka-only operation and keep their callback behavior.
- `RedisShipperAvailabilityAdapter` only maps existing Redis/DTO representations and calls the real Kafka/fanout adapters. `ShipperAvailabilityService` is now a DTO translator into the real core, without offline business logic. Required WebSocket dependency is resolved through ObjectProvider at fanout time because publisher session management itself depends on availability; no dependency becomes optional or silently skipped.
- Production audit corrected the earlier baseline description: both REST and internal offline HTTP routes used the incomplete `DefaultTrackingService.markOffline`, while the complete absent-cache behavior existed only in the legacy availability path. Both HTTP routes now invoke the canonical core with authorized broadcast. Removed duplicate core offline policy, unused location-store read/remove/timestamp parser and test-only legacy controller constructors. Core offline input validation and previous cached-fact/error vectors are retained in the actual owning use-case tests; the old missing-cache rejection test is replaced by the documented identity-only tombstone requirement.
- Real Redis/Kafka plus production Spring/HTTP composition proves missing cache on both HTTP routes, complete and partial cached coordinates, private-secret fail-closed behavior, canonical principal-derived shipper ID, actual removal from GEO/online sets, retained cached facts/timestamps/distance and four exact keyed JSON tombstones with distinct event IDs. The fixture uses H2 only for ancillary identity projection and disables consumers; producers/Redis are real, without mocked application ports. PostgreSQL projection wiring is proven separately by the earlier identity tests. This is not yet a packaged-JVM or cross-instance lease recovery proof.
- Red proof temporarily restored the retired absent-cache rejection rule: the real HTTP/Redis/Kafka test failed with one assertion failure (expected 200, observed 500), zero test errors; `/tmp/tracking-offline-missing-cache-red.log`. Python finally restored canonical source before completion. The first focused adapter run also exposed reference-identity assertions against newly mapped DTOs; replaced these with captured full recursive field comparisons and retained all side-effect/error checks. Review additionally preserved optional distance metadata in cached results.
- Focused proof passed (`/tmp/tracking-offline-redis-kafka-focused.log`). Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-offline-final-clean-verify.log`): domain 4/application 13/infrastructure 5/host 76 = 98 tests, zero failures/errors/skips. Domain line/branch 90.48/100%; application 91.30/100%; all 85% gates retained.
- Static boundaries, context isolation, whitespace, HTTP inventory and public-edge freshness passed. HTTP inventory stays at 244 operations; reachable schemas are now 229 because removal of the anonymous test constructor fixes a pre-existing source-catalog misidentification: it described an anonymous `TrackingPort.markOffline` instead of the actual internal HTTP handler, pulling Coordinate/LocationSnapshot as phantom wire schemas. Only that generated handler description changes (now correct BaseResponse<Void>, path ID and Internal-Token); other handler descriptions/schemas are identical after source offsets are ignored. Actual internal annotated method/secret guard/response code is unchanged except the core invocation. Public edge stays 93 routes/157 patterns.
- Tracking is still incomplete and stays in the isolated branch: WebSocket/location publication, generation/expiry/grace recovery, identity event/inbox processing, history and final root layout/runtime/Docker/dependency proof remain. Do not integrate or retire its host before the complete service tranche is proven.


### Tracking publisher lease/grace/recovery core

- `DefaultPublisherSessionUseCase` now owns configured grace/TTL/claim normalization, acquisition/refresh orchestration, current-disconnect scheduling, generation/active lease admission, offline callback/completion ordering, per-claim failure handling and recovery continuation. Typed lease/schedule/incident/availability ports replace Spring/Redis/DTO dependencies. `PublisherExpiryClaim` belongs to domain; Redis repository implements the lease port directly with all seven Lua scripts byte-identical to the prior implementation.
- `ShipperPublisherSessionManager` is now only the WebSocket DTO/callback translator to actual application core. `PublisherLeaseExpirySweeper` only reads the batch setting and invokes the core at the existing scheduled interval. `TaskSchedulerPublisherAdapter` supplies the qualified scheduler; `PublisherLeaseLoggingAdapter` retains existing success/grace/sweep log messages. Existing property names/defaults, relative deadline clocks, Redis keys, claim token comparison and Kafka-only lease recovery semantics remain.
- Ten pure application cases cover acquire/refresh and configured clamps, exact grace deadline/order, superseded disconnect, reconnect without claim, inactive/newer-generation skip, claim/fence/offline/callback/completion failures, independent batch recovery after a failed claim, empty batch, visible batch-store and scheduling failures and missing dependencies. All six pre-existing manager/sweeper cases now exercise the real replacement core behind technical adapters; callback output fields/timestamp are retained rather than asserting mutable DTO object identity.
- Five real Redis tests construct independent application instances against the actual lease repository and prove old refresh/disconnect fencing, late grace callback and stale deadline cleanup after reconnect, recovery when the original scheduled callback is lost, actual active-key TTL expiry without disconnect, failed offline claim retention with retry only after timeout and stale completion unable to delete a newer claim. Offline/Kafka delivery is a recording boundary in this lease proof; the earlier separate Redis/Kafka/HTTP offline proof supplies actual tombstone transport evidence. These tests do not claim an actual JVM kill/restart or atomicity between the Redis fence check and subsequent offline side effects; those broader runtime/failure guarantees remain in the final Tracking audit.
- First real Redis run exposed an incorrect fixture assumption that reconnect immediately removes a released generation's deadline. With no active key to prune, the existing repository retains it until the generation fence safely completes the stale claim at expiry. Corrected the test to exercise that real cleanup without changing production Lua/policy (`/tmp/tracking-publisher-real-redis.log`, then green `/tmp/tracking-publisher-real-redis-green.log`).
- Adversarial red proof temporarily bypassed the core generation check: the real Redis reconnect test detected offline shipper 7 while the replacement generation was healthy, one assertion failure/zero errors (`/tmp/tracking-publisher-generation-fence-red.log`). Python finally restored canonical source before full verification. Focused core and production wiring proofs passed (`/tmp/tracking-publisher-core-focused.log`, `/tmp/tracking-publisher-wiring-focused.log`).
- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-publisher-final-clean-verify.log`): domain 4/application 23/infrastructure 5/host 81 = 113 tests, zero failures/errors/skips. Domain line/branch 86.36/100%; application 92.59/100%; retained 85% gates. Static boundaries, context isolation, whitespace, HTTP inventory/contract (244 operations/229 schemas) and public edge freshness (93 routes/157 patterns) passed.
- Follow-up caller audit now finds `ShipperLocationService` and its `ShipperAvailabilityService` DTO facade have no production consumers after the session/core migrations; only their legacy fixture uses them. Retire these together with migration of their still-relevant update/offline failure assertions when unifying actual REST/WebSocket publication, including source/time/simulation metadata. WebSocket location decisions, identity event/inbox processing, sampled history, actual scheduling-disable behavior and final root layout/JVM/Redis/Kafka/Docker/dependency proof still require completion before Tracking integrates into main.


### Tracking unified location publication and obsolete service retirement

- Actual REST and WebSocket updates now invoke `DefaultTrackingService` through typed source-aware ports. Domain owns source/publication ordering; application owns immutable state, server time and Redis/event/fanout orchestration. Technical Redis/DTO/Kafka/PubSub mapping lives in `RedisLocationUpdateAdapter` and `LocationUpdateMapper`. APPLICATION preserves Redis → fanout → Kafka and Instant encoding; WEBSOCKET preserves Redis → Kafka → fanout and local-date-time encoding. Server decides source and canonical shipper identity; forged payload fields remain ignored. Producer simulation context remains real.
- Repository Tracking specification requires distributed authorized fanout and boolean-only online state. Corrected two actual REST gaps: local-only fanout now uses Redis PubSub; explicit null/string/number/collection online values fail HTTP 400 before state mutation. Omitted online flag still defaults true. Generated source catalog records the new validation annotations; its required marker describes field validation, while executable HTTP proof retains omission compatibility. No new route or event field is introduced.
- Deleted unreferenced `ShipperLocationService` and `ShipperAvailabilityService`, including their test-only delegation path. Five retained offline failure vectors now exercise the actual availability core/adapter; update failure/order/input vectors exercise actual publication core. WebSocket fixtures use the required production constructor and actual identity/publication cores, without optional dependency bypasses.
- Seven pure publication cases, domain ordering policy and two full mapping/boolean cases cover all facts, clocks, source ordering and failure boundaries. Expanded real Spring HTTP/Redis/Kafka proof retains four offline tombstones, verifies seven malformed online flags cannot mutate cache, and checks actual WebSocket and REST publication to an independent Redis subscriber/handler, authorized versus denied participants, forged identities/sources, actual GEO/online membership and six exact keyed Kafka events with real simulation metadata. This proves two handler instances in one JVM, not two deployed JVMs or authenticated network WebSocket handshakes.
- Adversarial red proof detected removed online validation (expected 400, observed 200) and removed APPLICATION PubSub (independent receiver missed the update), each one assertion failure/zero errors. Logs: `/tmp/tracking-publication-online-flag-red.log`, `/tmp/tracking-publication-rest-pubsub-red.log`; temporary source restored in finally. Initial focused WebSocket fixture exposed unused shared Mockito stubbing; corrected with a typed fanout fake without loosening strictness.
- Architecture gate initially rejected enum/constructor behavior in application-api. Moved policy to domain and validation to application, keeping API records declarative and all gate thresholds unchanged. Subsequent compile exposed a missing moved-enum import; fixed before the authoritative fresh build.
- Fresh `mvn -B -pl :tracking-service -am clean verify -q` exited 0, `/tmp/tracking-publication-import-final-clean-verify.log`: domain 5/application 28/infrastructure 5/host 80 = 118 tests, zero failures/errors/skips. Domain line/branch 88/100%; application 92.86/100%; existing core 85% gates retained. Module boundaries, context configuration isolation, whitespace, HTTP inventory/contract (244 operations/229 schemas), public-edge freshness (93 routes/157 patterns) passed. Context isolation script checks property presence; observed scheduled Redis attempts confirm actual scheduler disabling is still unresolved and requires runtime proof.
- Tracking remains only in the isolated worktree and incomplete. Identity inbox, sampled history, room/fanout policy audit, actual scheduler disabling, final root relocation and packaged JVM/JWKS/WebSocket/Redis/Kafka/PostgreSQL/recovery/Docker/dependency verification remain before integration. Whole service architecture and Saga-to-Dispatch objectives remain active.


### Tracking actual scheduling-disable wiring

- Authority is the existing test bootstrap contract: `spring.task.scheduling.enabled=false` must isolate periodic jobs. Runtime previously unconditionally enabled scheduling in `PublisherSessionConfig`; full-test logs showed scheduled Redis attempts despite the false property. Moved only annotation-driven scheduling into a conditional nested configuration, enabled by default or explicit true. The qualified disconnect-grace scheduler remains available even when periodic jobs are disabled, preserving explicit grace callback behavior. Runtime defaults and sweep/retention intervals are unchanged.
- Three actual Spring configuration context tests verify disabled periodic registration/no sweeper or history calls plus an executing explicit grace task, default-enabled registration of both real jobs, and explicit-enabled registration. The full application context smoke test also asserts no scheduled processor under the actual test resource bootstrap. Regression test failed against old wiring with one assertion failure/zero errors (`/tmp/tracking-scheduling-disable-red.log`), then passed after the fix. No static gate was relaxed.
- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-scheduling-final-clean-verify.log`): domain 5/application 28/infrastructure 5/host 83 = 121 tests, zero failures/errors/skips. No scheduled-task Redis localhost attempts appear in this run. Module/context/whitespace gates passed; HTTP/public-edge contract freshness remains unchanged. Tracking remains incomplete in worktree; publication tranche committed as `a7eed2b`, identity inbox/history and final integration/runtime audit remain.


### Tracking identity inbox core and PostgreSQL serialization

- `DefaultShipperIdentityInboxUseCase` now owns event identity validation, exact UTF-8 SHA-256 fingerprint, receipt conflict/replay decisions, stale mapping receipt-only admission, version gaps and mapping/receipt timestamps. Typed command/fact/store ports keep core independent of Kafka/JPA. The Kafka listener retains its exact retry/DLT annotations and raw JSON decoding, then invokes actual core; its old business implementation and listener-owned transaction are removed. Application requests an atomic store operation covering both projection and receipt.
- Existing identity migration authority requires at-least-once replay safety and conflict/gap fencing. The old listener used unprotected read-then-save operations. `JpaShipperIdentityInboxAdapter` now uses PostgreSQL transaction-scoped advisory locks by event first and principal second before any reads, including absent projection/receipt rows. Locks, JPA mutation and inbox receipt share the existing transaction manager; no new table, event field or property is introduced. PostgreSQL semantics were verified against https://www.postgresql.org/docs/16/explicit-locking.html#ADVISORY-LOCKS. This adapter's event-write serialization targets production PostgreSQL; H2 context fixtures do not exercise this PostgreSQL-specific SQL.
- Explicitly retain existing admission semantics: an initial snapshot can start at any positive version; a different event at the same mapping version applies its facts; lower versions record a receipt without replacing current facts; gaps above current+1 fail with the existing retryable exception. This tranche does not invent a new same-version conflict policy. Missing event ID fails before store access under the existing stable inbox identity requirement. Exact raw payload reuse, including whitespace differences, retains strict fingerprint behavior.
- Seven pure core tests cover all input validation branches, default/explicit clocks, exact known fingerprint, every receipt conflict field, exact replay without projection reads/writes, stale/equal/next/initial versions, nullable legacy fact compatibility, gap before writes and visible storage failures. Four new real PostgreSQL cases retain the three existing principal/REST/WebSocket cases: actual listener replay/stale/equal/gap, unique shipper violation rolling back projection and receipt followed by successful retry, eight concurrent exact replays through two independent core/adapter instances, and observable database blocking for absent principal/global event locks followed by successful completion. No mocked core/database port is used for inbox proof; unrelated location/lease collaborators remain mocked in the existing identity-only fixture. This is not a real Kafka delivery/two-JVM proof.
- Adversarial red proof removed both advisory locks: the database blocking test observed zero waiting locks and timed out on that assertion (one Awaitility ConditionTimeout test error with underlying assertion, zero infrastructure failure), `/tmp/tracking-identity-inbox-lock-red.log`. Python finally restored source before authoritative verification. Focused core/context and PostgreSQL tests passed (`/tmp/tracking-identity-inbox-focused.log`, `/tmp/tracking-identity-inbox-postgres.log`).
- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-identity-inbox-final-clean-verify.log`): domain 5/application 35/infrastructure 5/host 87 = 132 tests, zero failures/errors/skips. Domain line/branch 88/100%; application 91.67/100%; 85% gates retained. Static module/context/whitespace checks, HTTP inventory/contract (244 operations/229 schemas) and public edge freshness (93 routes/157 patterns) passed.
- Tracking remains incomplete and isolated. Next actual business extraction is location history sampling/query/retention/replay, followed by room/fanout policy audit, full root relocation and packaged-JVM/JWKS/WebSocket/Redis/Kafka/PostgreSQL/recovery/Docker/dependency proof before main integration. Entire system architecture and separate Saga-to-Dispatch objectives remain open.


### Tracking location history core, retention and atomic sampling

- `LocationHistoryPolicy` owns the approved ten-second/25-metre neighbour separation, five-decimal coordinates, two-decimal optional telemetry and source normalization. `DefaultLocationHistoryUseCase` owns stable identity/time admission, raw SHA-256 replay/legacy receipt fencing, atomic claim/outcome orchestration, both-neighbour sampling, positive delivery query/1..configured-max<=500 bounds and Clock-based retention cutoff with existing configured-days clamp. Typed command/point/receipt/outcome/transaction/store contracts remove Kafka/JPA/framework dependencies from core.
- Removed the old business `LocationHistoryService` completely. Kafka listener, internal support controller and retention scheduler now invoke the actual core; listener retains rolling deterministic legacy event-ID decoding and ACK/retry/DLT behavior. `LocationHistoryCommandMapper` and response mapper only translate wire facts. Retention job only triggers `cleanupExpired`; max-query/retention properties and defaults compose the core through `LocationHistoryConfiguration`. HTTP secret/ADMIN/positive support identity checks and coordinate-free audit log remain unchanged.
- `JpaLocationHistoryAdapter` owns technical entity/fact conversion, read-only/write transactions, existing PostgreSQL atomic receipt claim and completion SQL, sequential H2 fallback, ordered/limited queries and cleanup statements. Repository SQL and migrations are unchanged. Application explicitly scopes claim, point and receipt completion in one transaction; actual invalid-coordinate and invalid-telemetry PostgreSQL ingress leaves neither point nor receipt and can retry successfully under the same ID. PostgreSQL concurrency fixture now runs outside an ambient test transaction, so commit/rollback assertions observe real independent service transactions.
- Approved support-history policy is at most one point per ten seconds unless movement >=25 m and requires both neighbours. Receipt-only protection did not fence two distinct event IDs racing on an absent neighbour window. Added PostgreSQL transaction-scoped advisory sampling lock by delivery/shipper before either neighbour read. Independent core/adapter instances and two transactions prove the second writer blocks at the database, then one point is PERSISTED and the other SAMPLED_OUT with two committed receipts. H2 remains sequential-fixture-only and is not concurrency evidence. No publisher hot-path database write is introduced.
- Two pure domain and eight pure application cases cover temporal/distance boundaries in both directions, precision/source/telemetry, all admission branches, no-delivery/offline outcomes, both out-of-order neighbours, exact/existing/lost-claim/legacy replay, each contradictory identity/payload/PENDING/missing-row/completion fence, query/retention clamps and transaction ownership. Existing H2/history and PostgreSQL replay cases now run against the real replacement; full DTO-to-core-to-JPA-to-query fields are asserted. Existing real Kafka/PostgreSQL two-application-context same/fresh-group replay and owner-partition DLT proof passes against the new core. These are independent contexts in one JVM, not packaged two-JVM deployment.
- Focused initial compile found AssertJ unavailable in the lean domain test module. Used its existing JUnit assertions without adding dependencies. Focused history/scheduler proof passed (`/tmp/tracking-history-focused-green.log`, `/tmp/tracking-history-sampling-focused.log`). Adversarial red proof omitted PostgreSQL sampling lock execution: the second-writer blocking assertion failed once with zero test errors (`/tmp/tracking-history-sampling-lock-red.log`); source restored in Python finally.
- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-history-final-clean-verify.log`): domain 7/application 43/infrastructure 5/host 89 = 144 tests, zero failures/errors/skips. Domain line/branch 90.70/100%; application 93.14/99.26%; existing 85% core gates retained. Module boundaries, test context isolation, whitespace, HTTP inventory/contract (244 operations/229 schemas) and public-edge freshness (93 routes/157 patterns) passed. Source inventory needs no regeneration because route/wire schema shapes and offsets remain identical.
- Tracking is still incomplete in isolated worktree. Remaining work: actual room/routing/fanout policy ownership audit and extraction where required, final five-directory root layout, production dependency graphs, packaged-JVM/JWKS/HTTP/WebSocket/Redis/Kafka/PostgreSQL/restart/recovery and Docker/security/freshness evidence, then main integration. Entire platform consolidation and separate Saga-to-Dispatch objective remain active.


### Tracking fanout and assignment room application policy

- `DefaultLocationFanoutUseCase` now selects exact multi-delivery assignments, retains the existing empty/null set fallback to the legacy projection, ignores absent shipper facts and owns per-room best-effort publication/incident reporting so one failed JSON/PubSub attempt does not prevent another room from receiving. Typed nullable `FanoutLocation` preserves every coordinate/motion/online/time/distance fact, including identity-only offline tombstones and the established timestamp strings. Redis/JSON/logging live in `RedisLocationFanoutAdapter`; `LocationFanoutPublisher` is now only the DTO translator to actual core, with no routing/failure decision left. Assignment-read failures remain visible; publication failures retain Redis-cache recovery semantics.
- `DefaultDeliveryRoomAssignmentUseCase` owns positive identity/time, stable UUID/status admission, case normalization and BUSY/AVAILABLE single/batch orchestration. Kafka listener keeps raw decoding/field typing, batch marker mapping, existing owner factory/retry/DLT and ACK/error translation, then calls core. Existing assignment store implements its typed port; local membership registry implements the index port. All four Redis Lua scripts, TTL/key formats and their atomic freshness/conflict policies are unchanged.
- Tracking specification requires old assignment events to be no-ops. Old listener correctly let Redis reject stale facts but unconditionally activated/ended the local room afterward. Core now consults actual post-mutation projection: BUSY activates only while that delivery remains assigned; AVAILABLE ends only once it is absent. This closes the observed stale BUSY evicting a newer room and stale AVAILABLE closing the same active room despite the Redis fence. Exact current BUSY replay can still rebuild a local index after consumer restart. This does not claim atomicity between a projection read and local index update across concurrent remote changes; broader race audit remains open.
- Seven pure fanout cases cover exact batch recipients/facts, null/empty legacy fallback, no assignment/identity, checked and runtime per-room failure with continued delivery, visible routing-read failure, nullable offline facts and required dependencies. Five pure assignment cases cover all ID/UUID/status validation branches, case normalization, all four store operations/order, admitted versus stale room changes and projection/apply errors before local mutation. Existing transport tests now exercise actual replacements; added full JSON/core/DTO field roundtrip for both batch recipients and nullable offline mapping. Real Redis core test keeps the newer participant room after stale BUSY/AVAILABLE and ends it after the admitted AVAILABLE.
- Focused proof passed (`/tmp/tracking-fanout-core-focused.log`, `/tmp/tracking-room-fanout-focused.log`). Adversarial red proof restored unconditional room updates: real Redis test detected active room 100 instead of 200, one assertion failure/zero errors (`/tmp/tracking-local-room-stale-red.log`); Python finally restored source.
- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-room-fanout-final-clean-verify.log`): domain 7/application 55/infrastructure 5/host 91 = 158 tests, zero failures/errors/skips. Domain line/branch 90.70/100%; application 94.19/99.40%; 85% core gates retained. Existing real Kafka/Redis assignment replay and same-partition owner DLT through independent application contexts, and HTTP/WebSocket/PubSub publication proof, pass against actual replacements. Static module/context/whitespace checks, HTTP contract freshness (244 operations/229 schemas) and public edge (93 routes/157 patterns) pass.
- Corrected descriptive service documentation that said Tracking never consumed `shipper.status-change`: existing code, operation spec and actual Kafka proof show Delivery-owned events drive Tracking routing projection, while Match independently uses availability. No topic/consumer policy changed. Production-matching plan requires multiple batch delivery rooms; current local registry still models a single active room per shipper. Batch subscription and authoritative room admission need completion next, together with projection/index race audit, before root relocation and final packaged runtime/security/dependency/main integration. Entire platform refactor/Saga-to-Dispatch goal remains active.


### Tracking multi-delivery batch subscription

- Production matching plan requires Tracking rooms for multiple active batch deliveries. Replaced the local single-delivery pointer with an immutable delivery set and delivery/session indexes. Synchronization preserves retained rooms and removes only dropped memberships; legacy activation still replaces the old single room. Batch subscribe no longer evicts a sibling audience. Unsubscribe/disconnect clean all memberships for one session; lookup/disconnect remain indexed, including the existing 10,000-room bound test. A legacy minimum-delivery getter remains only for compatibility; production local offline broadcast now iterates all active rooms.
- `DefaultDeliveryRoomSubscriptionUseCase` owns authorized-scope admission and shared-projection routing selection. The WebSocket handler first retains the existing canonical Delivery participant check, then invokes the core. If that delivery is present in Redis assignment projection, synchronize all active rooms (including batch siblings); if absent/stale, retain the existing compatibility behavior using only that successfully authorized delivery. This core does not replace Delivery authorization or grant participant access to sibling rooms: each membership still requires its own check. A projection read failure is visible before membership mutation. Subscription confirmation, raw location wire format and cache replay remain unchanged.
- Batch BUSY/AVAILABLE core paths synchronize the complete admitted projection instead of performing single-room activate/end; legacy paths retain their existing fence. Local registry is technical membership/index management. No Redis Lua/key/TTL, event contract, topic or feature flag changes in this tranche. Atomicity of projection-read/index update remains a separate pending audit.
- Five pure subscription cases verify shared batch scope and order, empty/stale fallback, invalid identity/session before port access, visible routing failure and missing dependencies. Two registry cases verify retained sibling rooms/idempotent synchronization/item-only closure and same-session multi-room unsubscribe/disconnect cleanup. Existing assignment/WebSocket fixtures invoke actual subscription core with technical external collaborators. New real Redis PubSub proof uses an independent subscriber handler/container and bounded dispatcher: two separately authorized customers for two active batch items receive the same location, denied participant receives none, and ending one item retains the other's audience. Delivery permission is a mocked HTTP boundary in this fixture; it is not a packaged network/JWKS/two-JVM proof.
- Focused first compile caught use of a package-private executor fixture constructor; changed to the existing public bounded production dispatcher constructor, without expanding visibility or relaxing tests. Focused proof passed `/tmp/tracking-batch-room-focused-green.log`. Adversarial red restored unconditional single-room activation on subscribe: the first customer lost the actual PubSub update after the second subscribe, one assertion failure/zero errors (`/tmp/tracking-batch-room-single-subscribe-red.log`). Python finally restored source.
- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-batch-room-final-clean-verify.log`): domain 7/application 60/infrastructure 5/host 94 = 166 tests, zero failures/errors/skips. Domain line/branch 90.70/100%; application 94.53/99.45%; existing 85% core gates retained. Module/context/whitespace gates and HTTP/public-edge freshness (244 operations/229 schemas, 93 routes/157 patterns) pass.
- Follow-up source audit found AVAILABLE scripts delete the legacy assignment key or batch fence key. This may permit an older BUSY after terminal removal, because the next BUSY sees no timestamp fence. Reproduce with actual Redis and close terminal replay protection next under the documented old-event-no-op requirement. Projection/index races, reassignment/room admission audit and final root/package/runtime/security/dependency/main integration remain. Entire system refactor and separate Saga-to-Dispatch goal remain active.


### Tracking terminal assignment replay fence

- Actual Redis reproduced terminal resurrection in three cases before edits: legacy BUSY after AVAILABLE, batch item BUSY after AVAILABLE and AVAILABLE delivered before initial BUSY. All three tests failed assertions with zero errors (`/tmp/tracking-terminal-replay-red.log`). This contradicts Tracking's documented older-event-no-op requirement; the old AVAILABLE scripts deleted the only freshness key.
- Added separate terminal timestamp keys: `tracking:shipper:assignment-terminal:<shipper>` and `tracking:shipper:batch-assignment-terminal:<shipper>:<delivery>`. AVAILABLE atomically retains the admitted terminal timestamp and removes the active assignment/member in the same Lua operation, including absent initial BUSY. BUSY atomically checks terminal first and skips timestamps <= terminal. A strictly newer BUSY remains eligible; legacy AVAILABLE for a different currently active delivery remains a no-op. Batch terminal scope cannot close or fence a sibling item. Active key values/formats, existing properties/topic/event shapes and conflict rules for live same-timestamp BUSY facts remain compatible.
- Terminal keys use the existing projection TTL of 24 hours; older/exact terminal replay cannot reduce or refresh the retained fence. This proves protection inside that cache window only. Losing Redis/expiry or an older writer version that ignores terminal keys can bypass this protection; do not claim indefinite replay or safe mixed-version writers. Final recovery/rollout audit remains required. No persistent database ledger or new replay-age policy was silently introduced.
- Three new actual Redis transition cases verify late/tied BUSY remains absent, new deliveries/newer facts admitted, foreign legacy AVAILABLE cannot terminate the next assignment, partial batch closure preserves sibling and AVAILABLE-before-BUSY fences both modes. Two further cases run two independent store instances against the actual Redis server with concurrent terminal/late-BUSY writers (24 races across both modes), and validate exact terminal values/24-hour TTL, stale terminal no-op and corruption rejection before mutation in all four scripts. Existing room/Kafka replay and same-timestamp poison vectors are retained. The old fake-Lua fixture is not evidence for new terminal semantics; the actual server tests supply that proof.
- Focused suite passed `/tmp/tracking-terminal-replay-focused-green.log`. Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-terminal-replay-final-clean-verify.log`): domain 7/application 60/infrastructure 5/host 99 = 171 tests, zero failures/errors/skips. Core logic and existing coverage gates are unchanged. Module/context/whitespace checks and HTTP/public-edge freshness (244 operations/229 schemas, 93 routes/157 patterns) pass.
- Tracking remains incomplete in worktree: projection-read/local-index race and reassignment admission audit, Redis loss/expiry/replay recovery, final five-directory root layout and packaged JVM/JWKS/HTTP/WebSocket/Kafka/PostgreSQL/restart/Docker/security/dependency proof remain before main integration. Whole platform architecture and separate Saga-to-Dispatch objective remain active.


### Tracking local room serialization, reassignment and queued membership revocation

- Reproduced an authorized same-delivery/new-shipper subscription failing because registry retained the old owner: one assertion failure/zero errors (`/tmp/tracking-room-reassignment-red.log`). Registry now replaces old membership when an already-authorized subscription binds the new shipper, removes the old shipper's delivery association and keeps old-owner cleanup from removing another shipper's current room. This remains downstream of the existing canonical Delivery participant check; assignment events alone do not replace an established room's participant owner.
- Added typed `withinUpdate(shipperId, operation)` to the index port. Assignment and authorized subscription core operations serialize their local Redis mutation/read and room index updates using the same bounded 256-stripe monitor array. The short registry map/membership mutations retain their intrinsic lock; Redis I/O holds only the relevant stripe rather than the whole registry. This fixes same-instance read/index interleaving, not a distributed transaction or cross-JVM snapshot fence. Real Redis proof pauses a subscription after reading the old projection, observes the same-shipper assignment thread blocked before mutation, allows another shipper to progress and ends with both Redis/local index on the new assignment and no old audience.
- Memberships now carry opaque instance-local versions. Repeated same-membership subscribe remains idempotent; unsubscribe/rejoin and reassignment issue a new version. The only production dispatcher call captures the exact membership and canonical target shipper; each pending message has a mandatory eligibility callback evaluated immediately before sending under the session send lock. Room end, unsubscribe, disconnect or owner transfer can therefore revoke queued messages, including old messages after rejoin. This does not retract a message already admitted into an in-flight send or re-check remote Delivery authorization on every packet. No wire payload/route or timestamp shape changes.
- Three deterministic controlled-queue tests prove old-membership messages are dropped after unsubscribe/rejoin while a fresh offline update sends, ended batch-room messages are revoked while the sibling sends, and reassignment revokes the old owner's pending location. New registry case proves authorized owner transfer and late old-owner index/end/disconnect do not remove new membership. Existing coalescing/offline transition, authorization, batch and 10,000-room performance vectors remain intact.
- Initial focused compile caught an accidental validation statement inserted into the private pending-message record by a broad replacement; removed it from the record and retained validation at dispatch entry. Focused proof then passed (`/tmp/tracking-local-room-admission-focused-green.log`). Adversarial proofs removed the local monitor (one actual Redis race assertion failure/zero errors, `/tmp/tracking-local-index-lock-red.log`) and removed pending eligibility (three assertion failures/zero errors, `/tmp/tracking-queued-membership-red.log`). Python finally restored each source before full verification.
- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-local-room-admission-final-clean-verify.log`): domain 7/application 60/infrastructure 5/host 104 = 176 tests, zero failures/errors/skips. Domain line/branch 90.70/100%; application 94.62/99.45%; existing 85% gates retained. Afterwards strengthened the same reassignment case with a late old-owner activate before end; the entire registry test class passed again (`/tmp/tracking-room-late-owner-focused.log`), without changing production code. Module/context/whitespace and HTTP/public-edge freshness pass (244 operations/229 schemas, 93 routes/157 patterns).
- Source audit confirms explicit HTTP offline still calls local socket broadcast through `RedisShipperAvailabilityAdapter`, while ordinary updates/disconnect callbacks use the distributed fanout core. Unify this offline path next with actual cross-handler Redis PubSub proof. Cross-JVM projection/index timing, Redis expiry/loss recovery, final root layout, packaged runtime/JWKS/WebSocket/Kafka/PostgreSQL/restart/Docker/security/dependency and main integration remain. Entire system goal remains active.


### Tracking explicit HTTP offline distributed fanout

- Explicit authenticated and internal HTTP offline now invokes `LocationFanoutPublisher` → actual `DefaultLocationFanoutUseCase` → Redis PubSub instead of resolving a local WebSocket handler. Removed `ObjectProvider`/handler coupling from `RedisShipperAvailabilityAdapter`; safe Redis mutation, Kafka tombstone then fanout order remains core-owned. Cached and coordinate-free offline retain exact server identity, LocalDateTime timestamps and existing HTTP/event shapes. Fanout projection-read failure remains visible after Redis/Kafka complete; per-room publication failures retain the existing best-effort policy.
- Extended actual Redis/Kafka ingress proof with an independent subscriber-handler/container for authenticated cached-coordinate and internal identity-only offline. Subscriber authorization and denied-audience checks remain; Kafka now verifies exactly eight events in order, unique IDs, sources, keys and both additional tombstone payloads. This fixture uses one JVM, MockMvc/authenticated actor and mocked Delivery participant boundary; it does not prove packaged two-JVM/JWKS ingress. Old implementation timed out with no received offline update (two tests, zero assertion failures/one Awaitility assertion timeout, `/tmp/tracking-http-offline-red.log`). Updated adapter passed focused proof (`/tmp/tracking-http-offline-focused-green.log`).
- Source audit found `broadcastShipperLocation` had no production callers after this change. Removed that obsolete shipper-wide local broadcast method. WebSocket test fixture now invokes actual fanout core with test transport delivery to `broadcastDeliveryLocation`; authorization payload proof uses that production Redis subscriber endpoint. Initial fixture compilation caught nullable registry ID versus Optional return mismatch; corrected the technical conversion before authoritative verification. Added adapter failure proof for visible fanout-read failure after ordered Redis removal and Kafka publication.
- Final fresh `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-http-offline-final-clean-verify.log`): domain 7/application 60/infrastructure 5/host 105 = 177 tests, zero failures/errors/skips. Existing coverage gates retained. Fresh module/context/whitespace checks and HTTP/public-edge freshness passed (244 operations/229 schemas, 93 routes/157 patterns). Review confirms constructor dependency removes the local-handler cycle, both HTTP endpoints retain security guards, no payload/route changes and bounded per-room publication uses existing fanout failure policy. Tracking root relocation, cross-JVM timing, expiry/loss recovery, packaged runtime/security/Docker/dependency proof and main integration remain; whole-system refactor and separate Saga-to-Dispatch objective remain open.


### Tracking root layout and host retirement (in progress)

- Proceed with the authorized structural consolidation after the verified core extractions: relocate the four layers to root `tracking/{domain,application-api,application,infrastructure}` and host to `tracking/boot`. All production HTTP/WebSocket/Redis/Kafka/security/composition code moves into infrastructure; boot retains entrypoint and runtime properties. Existing package names, artifact versions, DNS, event/HTTP shapes and database migrations remain unchanged. Existing host regression/integration tests remain in boot to exercise the packaged dependency composition; infrastructure owns its existing persistence tests.
- Update reactor relative parents, infrastructure's production dependencies, Compose artifact path, source inventory/catalog and source-based identity/publisher guards. Preserve old guards' business invariants by checking publisher policy at the actual application core and scheduled trigger at its adapter. No completion/main integration yet: packaged runtime, cross-JVM/recovery/Docker proof and remaining policy audit still apply. Rollback is reverting this structural commit without database/schema changes.

- First relocated clean build compiled infrastructure but its old persistence-only fixture lacked Config Client bootstrap settings after adding production runtime dependencies. Added test-only isolation properties and fixture-only internal secret; production configuration/security defaults are unchanged. Then fresh `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-root-layout-clean-verify.log`): domain 7/application 60/infrastructure 5/boot 105 = 177 tests, zero failures/errors/skips, existing 85% domain/application gates retained. Boot dependency list now only infrastructure, Actuator/Prometheus and test dependencies; infrastructure owns all transport/persistence/composition dependencies. Final POM formatting/description change was followed by successful reactor packaging through `scripts/package-compose-services.sh tracking-service` (`/tmp/tracking-root-layout-package.log`).
- Confirmed original `tracking-service/` and `modules/tracking/` no longer exist in the worktree; pre-move ignored-file audit found only build outputs, no operator material. Packaged boot JAR contains only `TrackingServiceApplication.class` in BOOT-INF/classes and the four Tracking layer JARs; infrastructure JAR contains actual HTTP/WebSocket/offline adapters and both SQL/Java migrations. Core packages remain framework-free and dependency boundary checks pass. Source layout regression fixtures (2) and module-boundary fixtures (13) pass. Contract equivalence comparison to previous HEAD proves only Tracking source paths changed; all operation/schema facts remain identical (244/229), and public edge remains 93 routes/157 patterns.
- Compose path `SERVICE_PATH=tracking/boot` and identity/publisher guards now target actual layers; the isolated actual publisher/Lua/grace/recovery guard passes. Full build-baseline gate still exits 1 on existing matchIfMissing=true findings in Settlement and Tracking periodic scheduler configuration; no gate was weakened or blanket whitelisted. Address the scheduler-policy/verifier mismatch separately using existing default-enabled runtime authority and executable scheduling proof; Settlement belongs to its subsequent tranche. Compose contract passes with the renderer-only WEB_BFF_ENCRYPTION_KEY_FILE placeholder supplied (first invocation omitted this required rendering variable); Actuator, JWT claim and test context isolation gates pass.
- Actual image build `delivery-tracking-root-proof:local` passed the artifact freshness stage (`/tmp/tracking-root-layout-docker.log`). Ephemeral image probe with read-only filesystem and no network ran as UID10001, read packaged app.jar and reported Java17; this is an image/platform probe, not application startup/readiness or two-JVM runtime proof. Next: packaged actual JWKS/HTTP/WebSocket/PostgreSQL/Redis/Kafka instances, replacement-publisher race, kill/restart/recovery and remaining distributed timing/TTL audit before main integration. Tracking checkbox and whole-system Result remain pending.


### Tracking packaged multi-JVM runtime and identity ACK (in progress)

- Added `scripts/verify-tracking-runtime.py`: owns disposable PostgreSQL16/Redis7/Kafka7.4 containers, generated RSA/JWKS and Delivery permission HTTP fixtures, two independent packaged Tracking JVMs, real HTTP/JWT and socket frames. Intended proof covers cross-instance updates/offline, denial, principal-vs-aggregate identity, publisher supersession, clean disconnect, hard kill/expiry, restart/history and terminal/identity Kafka replay. Child processes/containers are cleaned in finally; fixture private key is removed and logs remain in the printed temporary directory. This tests Tracking against a permission fixture, not a deployed Delivery/Auth/Gateway platform.
- Initial harness errors were fixture defects: wrong uppercase identity eventType was correctly sent to DLT; fixed to canonical `shipper.identity.upserted` contract with complete metadata. Raw redis-cli member check initially ignored GenericJackson string encoding; corrected to encoded JSON string rather than altering production Redis layout.
- Actual packaged proof subsequently passed two JVM startup/readiness, JWKS HTTP/WebSocket, cross-instance REST/WS/offline, superseded publisher rejection, clean grace offline, hard-killed A lease reconciliation by surviving B, cached offline re-subscribe and restart A with a fresh history group. Strengthened committed-offset proof found a production defect: identity projection/receipt exists but consumer offset remains0 after restart/replay because listener has no manual ACK under MANUAL_IMMEDIATE container. `/tmp/tracking-packaged-identity-ack-red.log` timed out waiting for identity offset2; application logs in `tracking-runtime-proof-poovanw5` show offset0 on restart. No completion is claimed from those earlier partial passes.
- Identity listener now takes `Acknowledgment` and acknowledges only after actual inbox core returns (projection and receipt transaction committed). Decode/core failure remains unacknowledged for the existing retry/DLT handling. Existing actual PostgreSQL direct ingress tests pass a test acknowledgment; three new transport tests verify ordered success, inbox failure and malformed JSON. Full verification and packaged rerun pending.
- Build-baseline scheduler false positive is resolved with an exact annotation/exact canonical-path exception for the documented default-enabled periodic scheduling runtime trigger. The scanner now retains offending line numbers; no whole Tracking file is allowlisted. Four executable isolated gate fixtures allow only the exact scheduler annotation and reject another default-enabled feature in that file, the annotation in another class, or a changed property. All9 source-gate tests pass. Actual full build-baseline gate still correctly rejects Settlement's optional application-api default; Settlement work remains pending.

- Subsequent packaged run confirmed identity offset advances after ACK, then exposed actual support-denial ResponseStatusException(403) being caught by global generic advice and returned as500. Added explicit status-to-BaseResponse translation without changing the controller permission decision. Actual MVC composition red proof with the AuthenticationPrincipal resolver fails403-vs500 once, zero errors (`/tmp/tracking-support-status-red.log`); an initial standalone fixture omitted that resolver and was corrected before accepting the red proof. Existing unrelated identity AccessDeniedException behavior is not changed by this targeted fix.
- Later actual two-JVM run reproduced clean-disconnect offline delivery loss (`/tmp/tracking-packaged-grace-fanout-red.log`, application logs `tracking-runtime-proof-ntituxh4`): sweeper won the durable expiry claim before the in-memory grace task, and old sweep path mutated Redis/published Kafka without fanout. Unified both claim winners through `markOfflineAndBroadcast` inside publisher core before completing claim. Removed the public afterOffline callback, entire `ShipperPublisherSessionManager` facade and handler's now-unused fanout dependency/private method. Handler directly uses typed PublisherSessionUseCase; scheduler/grace adapters remain technical. Expiry incident logging occurs after claim completion, so incidental logging failure no longer retains an already-completed data transition.
- Migrated original facade tests to actual publisher core + scheduling adapter; existing acquire/reconnect/generation/stale-completion/Redis crash-recovery vectors remain. Core failure matrix now covers fanout failure before completion instead of the removed DTO callback, preserving retryable claim proof. Actual Redis fixture rejects the Kafka-only operation. Focused replacement proof passed (`/tmp/tracking-expiry-fanout-focused.log`). Packaged harness now requires proactive offline on hard-kill recovery as well as cached re-subscribe; fresh history group proof checks committed replay offset and unchanged PostgreSQL point/receipt counts. Final clean verification and complete packaged run remain pending.

- Fresh `mvn -B -pl :tracking-service -am clean verify -q` exited0 (`/tmp/tracking-publisher-fanout-final-clean-verify.log`): domain7/application60/infrastructure5/boot109 =181 tests, zero failures/errors/skips; 85% core gates retained. Module/context/JWT/source contract gates pass. Packaged run `tracking-runtime-proof-pekasbrn` proves proactive grace offline when the sweeper won (A log16:38:13.616) and proactive hard-kill offline by B (16:38:18.926), but its history replay checkpoint used a30-second wait while actual consumer logs configure session.timeout.ms45000. A owned the history partition when killed; B had not yet completed crash rebalance at the verifier deadline. Updated only this crash-rebalance observation window to90seconds, retaining committed-offset and unchanged point/receipt assertions. This is an evidenced verifier timing correction, not a consumer policy change or accepted replay failure. Full packaged rerun still pending.

- Complete packaged rerun exited0: `/tmp/tracking-packaged-runtime.log`, artifacts `tracking-runtime-proof-4j2q___4/summary.json` report PASS,10 unique location events,4 persisted support points and3 JVM lifetimes. Proved actual two concurrent JVMs with shared real Redis/Kafka/PostgreSQL, RSA JWKS auth, explicit principal100→legacy200→shipper7002 despite client spoofed999, unauthorized HTTP/WS and denied subscriber, cross-instance WS/REST/HTTP offline, replacement generation rejection without stale location event, clean disconnect offline under either claim winner, hard-killed A → B lease expiry with proactive PubSub offline, cached re-subscribe, restarted packaged A, fresh history group consuming through pre-restart committed snapshot without adding point/receipt rows, identity exact replay offset2 with one receipt, support ADMIN+secret access/403 denials and AVAILABLE-before-late-BUSY terminal protection with both instance groups committed through offset3. Kafka console records cover server-owned sources and unique IDs; Delivery permission/Auth JWKS remain HTTP fixtures, not a deployed platform/Gateway proof. Generated fixture RSA key was removed; owned processes/containers cleaned in finally.
- Final full verification remains181 tests, zero failures/errors/skips (`/tmp/tracking-publisher-fanout-final-clean-verify.log`), source/API/edge/context/JWT guards pass; exact scheduler gate red proof against old script yields one false-positive assertion failure out of4 fixtures, zero errors (`/tmp/tracking-scheduler-gate-red.log`), all9 updated source-gate tests pass. Full build-baseline still rejects only Settlement optional application-api default, not Tracking scheduler; no blanket Tracking whitelist. Latest reactor packaging succeeded (`/tmp/tracking-runtime-final-package.log`) and generated fresh manifest for tracking/boot. Actual final image `delivery-tracking-runtime-proof:local` build exited0 through the freshness stage (`/tmp/tracking-runtime-final-docker.log`); read-only/no-network image probe ran as UID10001, read app.jar and used Java17 (`/tmp/tracking-runtime-final-image-probe.log`). This remains an image probe; the complete application lifecycle proof above runs actual packaged host JVMs. Retired publisher facade is absent from packaged infrastructure JAR.
- Review: business offline publication is now core-owned once per admitted expiry attempt, callbacks/delegating publisher facade are retired, transport wire/DB schemas remain unchanged, HTTP support denial preserves controller403 and canonical error envelope, inbox ACK follows transaction completion, decode/storage/conflict failures remain retryable/unacknowledged. Claim-to-offline generation check and mutation are still separate Redis operations; do not infer an atomic healthy-replacement guarantee from this lifecycle proof. Audit/reproduce that remaining race and Redis loss/TTL boundaries next, then finish Docker runtime/security/dependency/main integration. Whole-system and separate Saga-to-Dispatch objectives remain pending.


### Tracking atomic expiry mutation fence (in progress)

- Authority: existing single-current-publisher generation and grace/crash recovery contract in `docs/services/tracking_service.md`. No timing defaults, HTTP/event shapes or security policy change. Real Redis regression paused expiry after preliminary admission/cache read, then acquired a replacement generation through an independent lease adapter and wrote fresh online coordinates. Old implementation overwrote replacement online cache: one assertion failure, zero errors/skips (`/tmp/tracking-offline-fence-red.log`).
- New application API carries the exact `PublisherExpiryClaim` to a conditional store port. Availability core owns offline facts and Redis → Kafka → fanout order; an unadmitted mutation publishes neither event nor fanout. Redis adapter atomically checks generation, active-key absence, exact claim deadline and claim validity using Redis TIME before cache/GEO/online-set mutation. Membership keys are preflighted before writes because Lua command errors do not roll back earlier writes. Uses actual GenericJackson cache/member serialization and existing 300-second TTL. Preliminary lease check remains an optimization; the mutation Lua is authoritative. Explicit HTTP offline retains its existing unconditional behavior.
- Real Redis matrix covers reconnect during the check/write gap, successful cached and identity-only offline, active lease, changed generation, expired/reclaimed claim, stale completion and corrupt membership with no partial writes. Focused tests passed (`/tmp/tracking-offline-fence-focused.log`, `/tmp/tracking-offline-fence-matrix.log`). First complete verification found two remaining sweeper mocks still targeting the retired expiry call; updated them to the claim-aware method while retaining publish-failure retry assertions and strengthening call-order proof. Final complete verification and packaged runtime pending below.
- Remaining distributed audits: online WebSocket refresh→write TOCTOU; reconnect after committed Redis offline but before Kafka/PubSub (publisher currently stamps Kafka event at publication time); duplicate retry/reordering and Redis loss/terminal TTL/mixed-writer limits. Atomic expiry mutation alone does not settle these cases or authorize declaring Tracking/whole-system goal complete. Main integration remains pending. Rollback is reverting this code tranche; no schema migration.

- Review caught a liveness gap introduced by rejecting expired claims at mutation: the old completion Lua could still remove that same expired deadline before another worker reclaimed it, permanently losing offline recovery. Strengthened the actual Redis matrix: the expired completion must return false and retain the deadline before reclaim. Red proof: six tests, one assertion failure/zero errors (`/tmp/tracking-expired-claim-completion-red.log`). Completion now also checks Redis TIME and retains expired deadlines for retry; reclaim-generation/score fencing remains. Final verification rerun is required after this production Lua change.

- Fresh final `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-offline-fence-recovery-final-verify.log`): domain 7/application 63/infrastructure 5/boot 115 = 190 tests, zero failures/errors/skips. Domain line/branch 90.70/100%; application 94.78/99.47%; existing coverage gates retained. Package manifest refreshed after final production mutation (`/tmp/tracking-offline-fence-final-package.log`, exit 0). Module boundaries, context isolation, nine build-baseline regression fixtures, HTTP inventory (244 handlers) and public-edge manifest (93 routes/157 patterns) pass. Isolated actual publisher/grace/recovery source guard passes. Full baseline exits 1 solely on existing Settlement optional application-api `matchIfMissing=true` (`/tmp/tracking-offline-fence-build-baseline.log`), with no blanket whitelist or weakened gate.

- Final packaged two-JVM harness exited 0 (`/tmp/tracking-offline-fence-packaged-runtime.log`): actual JWKS/HTTP/WebSocket/PostgreSQL/Redis/Kafka, allowed/denied subscriptions, publisher supersession, explicit offline, grace expiry, hard kill/restart and durable replay/ACK all pass. Fixture summary `/var/folders/9r/yvxp64w95y7dqkj_fx16pzwc0000gn/T/tracking-runtime-proof-5z8mmlmq/summary.json`: 10 unique events, 4 support history points, 3 JVM lifetimes, hard-kill recovery true. Generated fixture key removed. JWKS/Delivery are explicit HTTP fixtures; this does not prove live Auth/Delivery/Gateway deployment or the remaining post-mutation publication race.
- Review: typed framework-free expiry port; shared canonical Redis lease-key constants; bounded atomic script; no new dependencies/security/API shape changes. Reconnect, stale worker and corruption cannot perform the expiry mutation or publish a rejected tombstone; expired completion retains the recovery deadline. This tranche is verified, but Tracking checkbox, main integration and whole-system Result remain pending the distributed audits above.


### Tracking atomic WebSocket publisher write fence (in progress)

- Resume from verified expiry tranche `90fe54b`. Authority remains the single current publisher generation contract in `docs/services/tracking_service.md`; no new product policy or timing defaults. Forced an actual WebSocket refresh to pause after a successful Redis refresh, then independently acquired a replacement generation and cached fresh coordinates. Red proof (`/tmp/tracking-online-write-fence-red.log`): one test/one assertion failure/zero errors, expected new latitude 10.9 but old writer overwrote it with 10.7.
- Tracking application API now has a publisher update carrying the immutable lease; unfenced update accepts APPLICATION source only. Core verifies WEBSOCKET source and matching canonical shipper, builds the same snapshot, then conditionally saves before Kafka/fanout. Redis atomically checks generation and exact active lease during cache/GEO/online-set mutation. A rejected attempt skips publication/fanout. Handler carries the same captured lease through refresh→parse→write (never rereads a removed session map entry), then uses the existing PUBLISHER_SUPERSEDED/policy-close response on final-store rejection. APP order/identity behavior remains unchanged.
- Technical Lua keeps actual GenericJackson payload/member serialization and 300-second TTLs. Preflights membership types; GEOADD validates existing Redis latitude limits before any projection is written, so invalid GEO or corrupt membership cannot partially replace cached state. No new framework/core dependency or production fallback.
- Focused proof and real Redis matrix pass (`/tmp/tracking-online-write-fence-focused.log`, `/tmp/tracking-online-write-fence-matrix.log`): old writer after refresh/replacement, successful online/offline with all telemetry/TTL/membership/order, missing active lease/wrong session, invalid GEO/corrupt membership, and core source/identity/null admission. Existing WebSocket/mapper tests migrated to the actual conditional port. Full reactor/package/runtime/main proof pending. Post-mutation publication ordering and Redis-loss/terminal-TTL/mixed-writer audits remain; whole-system goal stays active. Rollback: revert this tranche, no schema change.

- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-online-write-fence-clean-verify.log`): domain 7/application 64/infrastructure 5/boot 119 = 195 tests, zero failures/errors/skips. Domain line/branch 90.70/100%; application 95.00/99.49%; existing 85% gates retained. Module boundary, test-context isolation, HTTP inventory (244 handlers) and public-edge freshness (93 routes/157 patterns) pass. Package/manifests refreshed successfully (`/tmp/tracking-online-write-fence-package.log`). Production caller audit finds only authenticated APP controller→unfenced APP method and WS handler→lease-aware method.

- Packaged two-JVM proof passed (`/tmp/tracking-online-write-fence-packaged-runtime.log`, exit 0). Fixture summary `/var/folders/9r/yvxp64w95y7dqkj_fx16pzwc0000gn/T/tracking-runtime-proof-6mxgcfe9/summary.json`: 10 unique events, 4 history points, 3 JVM lifetimes, hard-kill recovery true. Actual HTTP/JWKS/WebSocket/Redis/Kafka/PostgreSQL, allowed/denied audience, old publisher rejection, grace expiry, explicit offline, crash/restart and replay ACK checks pass. Generated fixture key removed. Auth/Delivery participant services are HTTP fixtures; actual deployed platform and remaining post-mutation event ordering are not claimed.
- Review confirms immutable lease capture survives local supersession, final Redis admission prevents replaced/expired/wrong-session writers, declined mutation skips Kafka/fanout, WS cannot use the unfenced APP path, and source-specific payload/order/telemetry stay intact. No schema/security/default/dependency change. This publisher-write tranche is verified; Tracking/main integration and the full service consolidation/Saga→Dispatch goal remain open for the remaining audit.


### Tracking occurrence time through publication (in progress)

- Resume from publisher-write `3638ce3`. Authority: `docs/services/tracking_service.md` says the offline fact timestamp fences older online records; the existing Match Lua explicitly compares location timestamp, and the history receipt/support contract derives occurredAt from that same epoch-millisecond field. Current producer stamped System.currentTimeMillis at publication instead of the immutable core fact, so an old fact delayed after a new one could appear newer. Red actual core→publisher tests cover APP/WS/OFFLINE: three assertion failures, zero errors/skips (`/tmp/tracking-occurrence-time-red.log`).
- Core offline timestamp is now an absolute Instant, matching online LocationSnapshot, with Clock.systemUTC default. Redis/WS DTO conversion retains existing server-local timestamp strings at the technical boundary. Both actual event adapters pass the core occurrence epoch millis explicitly to Kafka; removed no-time DTO/identity helper overloads (no production callers) instead of leaving an unsafe fallback. Simulation context overload still accepts explicit trusted timestamp/context. Positive timestamp admission happens before assignment lookup/Kafka. Field names, wire types, HTTP/WS shapes, source and consumer freshness/equal-time/TTL policies remain unchanged.
- Focused core/adapter/producer tests passed (`/tmp/tracking-occurrence-time-focused.log`). Strengthened broker failure fixture to use a valid shipper and assert the broker root cause plus actual send; invalid occurrence time has no assignment/Kafka effects. Added actual Match Kafka/PostgreSQL/Redis proof for newer online→late older offline and newer offline→late older online, waiting for committed offsets before asserting final membership/GEO. Full Tracking verification, Match consumer proof and packaged runtime are pending.
- This fixes the Kafka fact timestamp, not Redis PubSub arrival order. Subscriber/dispatcher currently selects by arrival and can send an older location after a newer packet; that remaining realtime ordering audit must precede Tracking main integration. Existing timestamp fence remains millisecond/clock based and expires with the documented 300-second freshness window; no global sequence, clock-skew or Redis-loss guarantee is claimed. Whole-service/whole-system goal remains active. Rollback reverts this internal time/adapter tranche, no schema or consumer event-contract migration.

- Fresh Tracking clean verify exited 0 (`/tmp/tracking-occurrence-time-clean-verify.log`): domain 7/application 64/infrastructure 5/boot 123 = 199 tests, zero failures/errors/skips, existing coverage thresholds retained. The actual Match consumer case also exited 0 (`/tmp/tracking-occurrence-time-match-consumer.log`), one test/zero failures/errors/skips; assertions wait for real committed group offsets for both reversed fact pairs before checking GEO/online state. Match production code and freshness policy are unchanged. This is consumer-wire proof plus producer/core mapping proof, not a combined deployed Tracking→Match platform rehearsal.
- Module boundary, context isolation, HTTP inventory (244 handlers), public edge (93 routes/157 patterns) and whitespace checks pass. Tracking package/manifests refreshed (`/tmp/tracking-occurrence-time-package.log`, exit 0). The full baseline's existing Settlement optional-default finding remains outside this tranche. Packaged two-JVM proof is running; no main integration/completion claim yet.

- Final packaged two-JVM Tracking harness exited 0 (`/tmp/tracking-occurrence-time-packaged-runtime.log`). Fixture summary `/var/folders/9r/yvxp64w95y7dqkj_fx16pzwc0000gn/T/tracking-runtime-proof-y95isdmn/summary.json`: PASS, 10 events, 4 history points, 3 JVM lifetimes, hard-kill recovery true. HTTP/JWKS/WebSocket/Redis/Kafka/PostgreSQL, supersession/grace, explicit offline, kill/restart, support-history replay and identity ACK checks pass; generated fixture key removed. JWKS/Delivery participant services remain HTTP fixtures.
- Review: absolute core occurrence time reaches the exact existing Kafka timestamp field; no-time publisher overloads are retired; broker failure remains visible with a verified valid send; invalid timestamp has no assignment/Kafka effects. Both APP/WS order and all nullable offline facts remain unchanged, DTO local-date-time rendering stays technical, and existing Match equal-time/freshness policy is retained. This occurrence-time tranche is verified. Realtime PubSub/WebSocket ordering, final service audit/main integration and the full consolidation/Saga→Dispatch objective remain pending.


### Tracking realtime ordering and paired cache metadata (2026-10-04, in progress)

- Resumed uncommitted tranche from occurrence-time milestone `4f0cd89`. Authority: latest-location recovery contract plus existing Match older/equal-time first-wins policy. Domain `LocationDeliveryOrder` rejects nonpositive times, older facts and equal-time replay; local delivery membership owns its watermark. Exact membership-version check protects stale queued audiences. Shipper stripe serializes admission/enqueue and cache-read/register/bootstrap enqueue, preserving increasing online-state transitions and batch-room authorization.
- Internal PubSub envelope and single Redis cache value carry mandatory absolute epoch millis. Cache pairs details and metadata atomically and retains coordinate-free offline marker. Public HTTP/WS response fields, Kafka payload, five-minute TTL and security scope stay unchanged. Legacy cache retains offline-coordinate reads but cannot bootstrap guessed metadata; old envelopes are rejected. Coordinated cutover: stop every old Tracking writer, wait old location values' 300-second TTL or refresh with new writer, then start new consumers/writers. No mixed writer rollout. Rollback: stop new writers and wait cache expiry before restarting old binary.
- Prior real-handler red proof `/tmp/tracking-realtime-order-red.log`: delayed older offline produced a second regressing packet. Realtime regressions now cover inverse old-online, equal-time replay, increasing state transitions, cache reconnect seeding, coordinate-free marker, and membership rejoin fencing. Actual Redis/Kafka matrix exercises new serializer/atomic cache paths. Replaced retired sequential-cache mock assertions with observable paired-cache/legacy-read tests; real Redis tests still prove GEO/set/TTL mutation.
- Expiry diagnostic: targeted previously failing expired-claim method passed with fresh Redis (`/tmp/tracking-clock-diagnostic.log`, exit 0). Measured host 1791078147033 vs Redis 1791078147064 (~31 ms). Earlier run had a 1071-second interruption; its cause remains unconfirmed. Existing lease deadline creation uses JVM time whereas final claim validation uses Redis TIME; clock-skew consistency remains a separate recovery audit item, not silently relaxed.
- First full reactor test `/tmp/tracking-realtime-all-tests.log` failed with two assertion failures, zero errors: test migration used verification-time `System.currentTimeMillis()` instead of core fact time. Availability fixture now uses injected fixed clock 12345 and exact deadline assertions; no production policy or coverage gate weakened. Module boundary, all 17 test-context isolation and 244 HTTP handlers gates passed. Fresh clean verify/package/runtime evidence pending.
- Do not mark Tracking complete or integrate main yet: source cache can still follow physical write order under concurrent APP requests; duplicate clock/TTL/Redis-loss limits and final architecture audit remain. Full service consolidation and Saga→Dispatch objective are also pending.

- Fresh `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-realtime-final-verify.log`): domain 8/application 64/infrastructure 5/boot 131 = 208 tests, zero failures/errors/skips. Domain line/branch 91.67/100%; application 95.00/99.49%; existing coverage gates retained. Package command exited 0 (`/tmp/tracking-realtime-package.log`).
- Initial packaged fixture reached recovery but timed out at repeated subscribe on the same socket (`/tmp/tracking-realtime-packaged-runtime.log`). Same membership correctly rejects equal-time replay; fixture claimed reconnect without opening a new socket. Corrected it to close/reconnect an authorized customer socket, retaining the exact offline payload assertion. Final packaged two-instance run exited 0 (`/tmp/tracking-realtime-packaged-runtime-final.log`); fixture `tracking-runtime-proof-sk8roqcu/summary.json`: PASS, 10 events, 4 history points, 3 JVM lifetimes, hard-kill recovery true. HTTP/JWKS/WS/Redis/Kafka/PostgreSQL, current-publisher fencing, offline/grace/crash, reconnect cache recovery and history restart/replay pass. JWKS/Delivery access remain HTTP fixtures.
- Review confirms core occurrence time passes unchanged into internal envelope/cache, wire DTO shape remains stable, paired value serialization passes actual Redis tests, and stale/missing timestamps cannot enqueue. Admission plus enqueue share the same stripe; subscription source read and seed use that stripe too; increasing state transitions remain deliverable. Source-cache concurrency and lease-clock consistency remain explicitly pending for the next tranche. Tracking is not complete/main-integrated yet.


### Tracking lease deadline clock consistency (2026-10-04, in progress)

- Authority: documented active TTL, durable grace/deadline recovery and exact claim fencing. Preserve configured durations and current one-second minimum, keys/member shape, generation guards, wire contracts and publication order. Redis already owns active-key TTL and final mutation/completion checks; creation/discovery must use that same clock. No new product timing policy.
- Red evidence: two real Redis tests with 400-ms delayed client transport, two assertion failures, zero errors/skips (`/tmp/tracking-lease-clock-red.log`, exit 1). Active TTL deadline differed by 460 ms, grace had consumed transport delay before Redis received the command. Host-generated deadlines and Redis-time validation are inconsistent even without modifying any system clock.
- Fix sequence: compute deadlines/expired discovery inside the owning Lua transaction with Redis TIME; return authoritative claim deadline with selected members (single and bulk claims); retain exact-score generation fencing; validate acquire/refresh/grace/single+bulk claim through delayed transport plus existing expiry/retry/reconnect matrix; full verify and packaged runtime before committing. Rollback is code revert; epoch-millis score/member format unchanged. Old client-generated deadlines remain claimable by Redis-time workers but require old writers stopped during existing coordinated Tracking cutover.
- Primary reference: Redis TIME https://redis.io/docs/latest/commands/time/ and PTTL https://redis.io/docs/latest/commands/pttl/ (server time and millisecond TTL). This deterministic delay proof does not establish the cause of the earlier interrupted expiry run.

- Focused Redis matrix passed (`/tmp/tracking-lease-clock-focused.log`, exit 0): delay regression plus acquire/refresh, persisted grace/lost callback, actual active TTL, failed-offline retry, stale completion, exact expired/reclaimed claims, corrupt-membership protection and reconnect mutation fences. Test expiry polling/manual expired scores now use Redis TIME instead of host time, preserving the exact before/after deadline assertions.
- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-lease-clock-final-verify.log`): domain 8/application 64/infrastructure 5/boot 133 = 210 tests, zero failures/errors/skips. Coverage unchanged: domain line/branch 91.67/100%, application 95.00/99.49%. Module boundary, nine baseline-contract regression tests, 17 application-context isolation and 244 HTTP handler inventory gates passed. Package exited 0 (`/tmp/tracking-lease-clock-package.log`).
- Final packaged runtime exited 0 (`/tmp/tracking-lease-clock-packaged-runtime.log`); fixture `tracking-runtime-proof-r87nlnk4/summary.json`: PASS, 10 unique events, 4 history points, 3 JVM lifetimes, hard-kill recovery true. Supersession, clean grace, survivor expiry, reconnect bootstrap, history restart/replay and HTTP/JWKS/WS/Redis/Kafka/PostgreSQL pass. Fixture key removed; JWKS/Delivery access remain HTTP fixtures.
- Review: no client-clock reads remain in lease Redis repository; every create/discover path uses server TIME within its Lua operation. Single claim returns its exact score; bulk returns one integer-formatted authoritative deadline plus members, with empty-list behavior preserved. Completion and offline mutation retain exact score/generation/active-key guards. Configured durations and one-second minimum remain unchanged; schema/key/member contracts unchanged. Lease-clock tranche verified; source-cache concurrency and final Tracking/main audit remain pending. Overall service consolidation and Saga→Dispatch goal remain active.


### Tracking source-cache ordering (2026-10-04, in progress)

- Lease-clock tranche committed as `83bd8c9`; source-cache audit started separately. Actual Redis regression `/tmp/tracking-source-cache-order-red.log` exited 1: three tests, three assertion failures, zero errors/skips. An older APP write replaced newer epoch metadata/cache, equal-time online replaced first offline, and old online replaced a coordinate-free offline marker. Subscriber watermark already blocks backwards delivery but a new subscription reads the regressed source value; source protection must be at atomic Redis mutation.
- New regressions live uncommitted in `PublisherWriteFenceRedisIntegrationTest` with deterministic post-commit write order and actual cache/GEO/set checks. Two-state loop now uses distinct newer timestamps (2000 then 4000) so equal-time first-wins does not prevent setup of its second case. No production change for source ordering yet. Current full-suite evidence of 210 green tests applies to committed lease-clock tranche; these three added red regressions are intentionally failing pending fix.
- Next: carry existing latest/equal-time first-wins semantics into all source-cache mutations, including APP, lease-fenced WS and claim-fenced offline; validate before any GEO/set/cache write, do not bypass lease/claim guards, retain public ACK/error and fact-publication contracts. Explicitly define stale projection no-op separately from stale publisher/claim rejection; then prove reconnect source and legacy/corrupt cache behavior with actual Redis, followed by full verification/package/runtime. Do not merge Tracking into main until this and the remaining final/TTL-loss audit are complete.

- Source-order decision: recognized legacy DTO is replaceable by a new fact because it has no absolute ordering metadata; unrecognized/corrupt JSON or paired metadata fails before projection mutation. New paired metadata comparison is inside the same Lua as cache/GEO/set writes, after publisher/claim and membership-type checks. Result 0 remains lease/claim rejection; result 2 is admitted stale/equal projection no-op. Core ACK, publisher rejection and fact publication order are unchanged; Kafka/realtime receivers already apply older/equal-time policy. No new transport errors or timestamp fabrication. All three write paths (APP/explicit offline, current WS and claim expiry) share this projection check.

- Initial source-order focused run passed (`/tmp/tracking-source-cache-order-focused.log`, exit 0). Expanded actual Redis matrix also passed (`/tmp/tracking-source-cache-order-matrix.log`, exit 0): valid old WS fact preserves source while publishing original 1000-ms time; wrong session remains rejected with no publication; valid old expiry claim publishes original tombstone but retains newer APP projection and can complete; newer coordinate-free marker survives old claim; stale writes do not refresh source TTL; recognized legacy DTO replaces correctly; corrupt JSON/type/negative occurrence metadata fails before cache/GEO/set writes. Current full verify is required after these new proofs and API contract comments.

- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-source-cache-order-final-verify.log`): domain 8/application 64/infrastructure 5/boot 142 = 219 tests, zero failures/errors/skips. Domain line/branch 91.67/100%, application 95.00/99.49%; existing coverage gates retained. Module boundary, nine baseline-contract tests, all 17 context-isolation checks and 244 HTTP-handler inventory passed; source scan found no Spring/JPA/Kafka/Jackson imports in domain/application API/application main code.
- Package exited 0 (`/tmp/tracking-source-cache-order-package.log`); final packaged runtime exited 0 (`/tmp/tracking-source-cache-order-packaged-runtime.log`). Fixture `tracking-runtime-proof-m3ry_wuy/summary.json`: PASS, 10 events, 4 history points, 3 JVM lifetimes, hard-kill recovery true. HTTP/JWKS/WS/Redis/Kafka/PostgreSQL, supersession, grace, survivor expiry, reconnect source bootstrap and history restart/replay pass. Fixture key removed; participant/JWKS services remain HTTP fixtures.
- Review: APP/explicit offline and WS use the same latest-projection Lua; expiry uses the identical shared Lua predicate after its existing claim/generation/active-key guard. Invalid current metadata/type fails before any projection write. Result 2 preserves admission/publication semantics and does not renew source TTL; result 0 still means stale publisher/claim. Actual Redis serializer verifies both paired record and recognized legacy DTO; original occurrence time reaches events/fanout even on admitted no-op. Source-order tranche is verified. Tracking final audit, documented TTL/Redis-loss limits and main integration remain pending; overall consolidation/Saga→Dispatch goal stays active.


### Tracking final TTL/state-loss audit (2026-10-04, in progress)

- Authority: existing 300-second location freshness, 24-hour per-item routing/fence retention and PostgreSQL audit-only role. No new lifetime/persistence/routing defaults are authorized. The final architecture audit must establish behavior within this retained state and explicit loss-recovery limitations, not silently promise durable fences after Redis data loss.
- Inspection found `activeDeliveries` reads every member in the batch set without checking its individual BUSY fence. Sibling BUSY refreshes shared set TTL while the older item's fence can expire, leaving a ghost room in fanout routing. New real Redis regression expires one item fence but retains shared set and live sibling; it expects only the sibling to remain routed. Fix will retain existing per-item TTL authority rather than introducing a new timeout. Red evidence pending below.
- Planned remaining proof: Redis loss fences old sessions/claims even when generation counter restarts; fresh publisher/location restores source; routing loss has empty projection until fresh current Delivery facts are applied. Late facts after terminal/freshness metadata is lost are outside the stored ordering window; restore must stop old writers/clear local sessions and use current authoritative assignments/new location observations, not blindly replay isolated old BUSY facts. Full policy/architecture/package/Compose/baseline review and main integration still pending.

- Red batch TTL proof exited 1: one assertion failure, zero errors/skips (`/tmp/tracking-batch-ttl-routing-red.log`). Routing read now loads per-item fence presence with one MGET and excludes expired/missing items; shared membership set is unchanged, legacy assignment fallback retained, and no new duration introduced. New focused real Redis/Kafka audit exited 0 (`/tmp/tracking-final-recovery-audit-focused.log`): batch expiry with live sibling, source expiry/fresh observation, flushed Redis rejects old publisher/claim, generation reset still rejects old session value, current Delivery facts restore routing without old local audience, and expired terminal plus restored current assignment rejects older BUSY. Existing assignment/fanout Kafka integration matrix passes too.
- Cold-recovery limitation and quiesce/rebuild/reconnect steps recorded in `docs/services/tracking_service.md`. No automatic Redis reconstruction or infinite replay fencing is claimed; PostgreSQL remains support-only. Existing finite-retention contract is preserved. Remaining verification: full clean reactor proof, fresh resolved Tracking dependency graphs, baseline/Compose/image package checks and updated two-JVM runtime, then integrate main without touching unrelated operator/product-reference work.

- Fresh full `mvn -B -pl :tracking-service -am clean verify -q` exited 0 (`/tmp/tracking-final-audit-clean-verify.log`): domain 8/application 64/infrastructure 5/boot 147 = 224 tests, zero failures/errors/skips. All existing coverage gates retained. Module boundary, 17 context-isolation checks, nine baseline-contract tests and 244 HTTP-handler inventory passed; legacy `tracking-service/` and `modules/tracking/` remain absent, boot main source contains only entrypoint and configuration.
- Fresh Maven resolved runtime graph generation exited 0 (`/tmp/tracking-final-resolved-dependencies.log`). An initial manual helper call incorrectly labeled infrastructure as framework-free core and reported expected Spring dependencies; corrected by using canonical `inventory()` from the unchanged dependency gate. All four canonical Tracking graphs (domain/application API/application/boot service, including infrastructure transitively) passed. No verifier policy or whitelist changed. Compose renderer with fixture secret paths passed (`/tmp/tracking-final-compose-contract.log`); Actuator gate passed. Full baseline exited 1 only on the existing Settlement default-enabled application API annotation (`/tmp/tracking-final-build-baseline.log`); not a new Tracking finding.
- Final package/image/two-JVM rerun and main integration remain pending. Automatic approval review rejected the escalation for the package command before execution because review quota was exhausted, saying to try again at 10:16 AM. This was a review failure, explicitly not a determination that the operation is unsafe. Do not bypass it by moving packaging/runtime to another execution surface or permission mode. Read-only audit and this checkpoint were completed independently; latest actual two-JVM proof still applies to `8884871`, not the new batch TTL read change. Keep Tracking checkbox and overall result pending until final artifact/runtime validation and integration complete.

- Automatic-review quota recovered on continuation; final packaging was approved and exited 0 (`/tmp/tracking-final-audit-package.log`). Final image build `delivery-tracking-final-proof:local` exited 0 with freshness checksum enforced (`/tmp/tracking-final-audit-image.log`). Owned image probe passed UID10001/read-only filesystem/no-network and exact SHA256 equality between `/app/app.jar` and the runtime-verified host JAR. This is an image/platform/artifact proof, not container application readiness.
- Final packaged two-JVM run exited 0 (`/tmp/tracking-final-audit-packaged-runtime.log`): 10 events, 4 history points, 3 JVM lifetimes and hard-kill recovery PASS. HTTP/JWKS/WS/Redis/Kafka/PostgreSQL, participant denial/identity, current-publisher supersession, grace, survivor sweep, reconnect source bootstrap, history restart/replay and identity ACK pass. Generated fixture key removed. JWKS/Delivery participant endpoints remain HTTP fixtures; not a whole deployed platform claim.
- Final review: domain/application API/application own actual location/offline, occurrence/order, identity/inbox, support history/sampling/retention, publisher lease/recovery, fanout selection and single/batch room orchestration; imports remain framework-free and resolved core dependencies point inward. Infrastructure owns transport/mapping/security/JPA/Redis/Kafka/composition; boot contains only entrypoint/config. Root layout/POM/Compose/catalog/scripts refer to canonical layers, package retains DNS/artifact/wire/schema, all older guards and 85% coverage gates retained. The old host is replaced, not concurrently supported. Finite TTL and Redis-loss recovery are explicitly bounded by existing MVP policy, with executable boundary proof and operational steps, not an unimplemented durable-rebuild promise. Ready for main integration; Tracking checkbox remains pending until actual merge and main verification.


## Tracking main integration closure

- Merged verified tranche into main at `0c41101`. Root `tracking/` owns all five layers; old host and module paths are absent. Removed only audited regenerable ignored old-host output and empty directories; operator material and unrelated product/reference changes remain untouched.
- Main full clean verify exited 0 (`/tmp/tracking-main-integration-final-verify.log`): domain 8/application 64/infrastructure 5/boot 147 = 224 tests, zero failures/errors/skips. Initial main run had one lease-refresh assertion failure; precise active value/TTL assertion context was added without changing expectations. Targeted diagnostic run and full verification then passed; root cause of the initial non-reproduced failure remains unconfirmed.
- Main package exited 0 (`/tmp/tracking-main-integration-package.log`). Recursive executable-JAR payload comparison, including nested dependencies and ignoring only ZIP timestamps, matched the final worktree runtime/image-verified artifact across 68,633 entries. Final worktree owned image runs as UID 10001 and contains the exact runtime-verified JAR; two-JVM packaged rehearsal passed including hard kill and restart. This is not a whole-platform readiness claim.
- Main module/context/baseline-contract/HTTP-inventory/Actuator checks passed. Whole-backend baseline is not green: main has stale Surefire reports for other service/retired paths (`/tmp/tracking-main-final-build-baseline.log`), while the fresh worktree baseline identifies the existing Settlement default-enabled application API annotation. Neither reports nor gate rules were altered to mask these findings.
- Changed Match Kafka/PostgreSQL/Redis integration class passed on main: seven tests, zero failures/errors/skips, Maven exit 0 (`/tmp/tracking-main-match-proof.log`). This covers location occurrence ordering without claiming all Match architecture work is finished.
- Tracking architecture checkbox is closed; overall plan remains active. Settlement is the next complete-service tranche; Saga-to-Dispatch ownership and other service work remain open.


## Settlement tranche: authority, inventory and execution order

Authority: `docs/platform/product/features/settlement.md`, `docs/workflows/settlement_finance_flow.md`, existing host behavior and executable proofs. Preserve COD-only MVP, default-off payment/payout/client gates, immutable receipt fingerprints, positive canonical amounts, promotion attribution, customer identity/read ownership, row locking, retry/DLT and ACK-after-commit. Provider UNKNOWN is not FAILED; unwired PayOS remains unwired. No new financial policy is authorized by the structural migration.

Current module `DefaultSettlementService` composes payment/payout provider-contract delegates; actual COD posting lives in the 400-line `DeliveryCompletedEventListener`, deposit/ledger in `TransactionServiceImpl`, refund decisions/receipt persistence in `RefundCaseService`, capacity holds in `CodCapacityHoldService`. Moving folders alone cannot close this tranche.

- [x] Establish fresh `mvn -B -pl :settlement-service -am clean verify -q` baseline and retain all existing tests/coverage requirements.
- [x] Completion: extract canonical identity/money reconciliation and ordered financial postings into domain/application. Receipt replay/order conflict/concurrent claim orchestration belongs in core; JSON fingerprint serialization, SQL claim variants, transaction scope, commit callback and metrics remain adapters. Preserve simulation short-circuit before financial validation and before all real ledger/receipt/hold mutations. Keep legacy-ledger-without-receipt rejection. Validate existing listener and DB integration suites, including rollback and after-commit ACK.
- [x] Ledger/balance and COD capacity: move eligibility/reserved-capacity calculation, hold/transition/consume decisions and ledger balance mutations into core ports; preserve pessimistic locks, scan bounds, wallet accounting and existing transition behavior. Do not silently fix discovered financial policy discrepancies during extraction. Add regression proof before any authorized behavior fix.
- [x] Refund: move cancellation/exception eligibility, immutable replay validation, customer/admin read policy and outbox decisions into core; retain DB uniqueness/transactional receipt+outbox, serialized fingerprint compatibility and default-off provider execution.
- [ ] Payment/payout: replace `LegacyPaymentWorkflowAdapter` delegation with actual core workflows while retaining provider selection, existing flags, default-off execution, UNKNOWN outcome, ownership rejection and callback gates.
- [ ] Relocate complete service into `settlement/{domain,application-api,application,infrastructure,boot}`; preserve Java packages, artifact/DNS, migrations/config and wire contracts. Retire old host/module only after all callers use replacement. Update reactor/Compose/package/catalog/inventory/gates.
- [ ] Verify coverage/dependency/context/contracts and full Settlement reactor; package canonical boot artifact and repeat owned PostgreSQL/Kafka recovery/reconciliation/crash-window evidence with updated artifact path. Integrate main only after executable proof; keep whole-platform and Saga-to-Dispatch work open.

Tracking closure is committed at `44b25a0`; refactor worktree fast-forwarded to it. Settlement baseline exited 0 (`/tmp/settlement-architecture-baseline.log`); no Settlement production source has changed yet.


### Settlement COD completion core checkpoint

- `CompletedCodDelivery` and immutable `CodSettlementPlan`/`LedgerPosting` now own canonical identity, fee/commission/promotion reconciliation and exact posting amounts/order/descriptions. `DefaultCodSettlementUseCase` owns receipt event/order replay decisions, concurrent claim winner validation, legacy ledger rejection, postings and hold consumption ordering. This is an actual runtime use case, not a delegate to the old completion listener.
- Listener now decodes/maps, checks the shared simulation context, preserves the simulation short-circuit before real money/receipt/ledger/hold effects, serializes the exact pre-existing full-DTO SHA-256 fingerprint, calls core and ACKs after commit. Core is wired through `CodSettlementConfiguration`, independently of the existing provider-contract flag. `JpaCodSettlementAdapter` preserves the same receipt SQL variants and delegates balance locking/posting to the existing ledger adapter pending the next slice. No wire/schema/feature-flag or financial policy changed.
- Domain and application red runs exited 1 because the requested core classes were missing (`/tmp/settlement-cod-domain-red.log`, `/tmp/settlement-cod-application-red.log`); connected green tests passed. Final full `mvn -B -pl :settlement-service -am clean verify -q` exited 0 (`/tmp/settlement-cod-composition-clean-verify.log`): domain 11/application 11/host 88 = 110 tests, zero failures/errors/skips. Coverage: domain 117/121 lines (96.69%), 120/128 branches (93.75%); application 57/57 lines and 20/20 branches (100%). Existing 85% gates unchanged.
- Full reactor includes real owned Kafka/PostgreSQL two-replica completion and refund tests, each one test with zero skip. Completion verifies one receipt/four ledger rows, exact replay across fresh groups and contradictory payload retained in same-partition DLT. Existing DB integration also verifies insufficient-deposit rollback, concurrent replay and ACK after the financial commit. This is not packaged process-crash proof; final artifact/crash-window rehearsal remains pending for the whole Settlement tranche.
- Strengthened simulation test wires the actual new core/JPA adapter with a hold collaborator and confirms no receipt/ledger/hold/metric effects; targeted listener run exited 0 after that test-only change (`/tmp/settlement-cod-simulation-authority.log`). No old test assertion was removed or relaxed.
- Fresh resolved Maven dependency graphs exited 0 (`/tmp/settlement-cod-resolved-dependencies.log`); all four canonical Settlement graphs pass the unchanged checker. Module boundaries, 17 isolated contexts, nine baseline-contract tests, 244-handler HTTP inventory and diff whitespace check passed. Review checked policy equivalence, full fingerprint preservation, original SQL/transaction and posting order, inward dependencies, bounded query count, no new secret/HTTP surface and active COD independent of default-off provider flags.
- Settlement remains incomplete: actual balance/ledger decisions, COD hold orchestration, refunds/outbox and legacy payment workflows still need extraction. Root relocation, legacy deletion, artifact/recovery proof and main integration remain pending. Existing baseline annotation finding is untouched. Source is currently in the refactor worktree; no Settlement code completion is claimed on main.


### Settlement ledger mutation core checkpoint

- `DefaultLedgerUseCase` now owns all previous transaction mutations: create, deposit top-up, COD eligibility, withdrawal request/approve/reject, reversal and hold/release. `WalletBalance` owns dual-wallet arithmetic and COD insufficient-deposit checks; `LedgerEntry` owns withdrawal/reversal admission and processing state. Full legacy reason/status enums are mapped without schema changes. `TransactionServiceImpl` now supplies API/type/exception mapping and transaction scope, retaining bounded read adapters; it no longer computes or decides financial mutations.
- `JpaLedgerAdapter` preserves pessimistic account acquisition, existing REQUIRES_NEW balance creation followed by re-lock, entry save/update ordering and managed entity identity. Core account/entry snapshots are mapped back into the already managed rows. Existing missing-resource and insufficient-funds HTTP exception types/messages are retained by the facade. Default-off mutation flags and historical reason-specific accounting behavior are unchanged. Balance creation/read fallback still lives in `BalanceServiceImpl` and is the next extraction.
- Wallet red run failed on missing domain class. An initial enum expansion introduced ambiguous static `DEPOSIT` imports; explicit wallet imports fixed it. Application red was then confirmed on missing `DefaultLedgerUseCase` (`/tmp/settlement-ledger-application-red-confirmed.log`), and green tests passed. Full connected verification passed (`/tmp/settlement-ledger-core-clean-verify.log`). The module gate identified an exception incorrectly placed in application API; moved it into application without modifying gate rules.
- Final full `mvn -B -pl :settlement-service -am clean verify -q` exited 0 (`/tmp/settlement-ledger-final-clean-verify.log`): domain 18/application 16/host 91 = 125 tests, zero failures/errors/skips. Domain coverage 163/167 lines and 147/155 branches; application 125/125 lines and 45/46 branches. All 85% gates retained. Existing real Kafka/PostgreSQL completion/refund replica/replay/DLT proof passes with the new ledger core.
- Added three real Spring/JPA transaction tests for top-up + withdrawal reject/approve persisted balances/metadata, hold/release + reversal, and reserved-capacity eligibility + rollback of an inserted insufficient-COD ledger entry. Resource/insufficient exceptions retain adapter API types. These H2 tests complement the existing Kafka/PostgreSQL active-COD proof; they do not claim enabled customer/payment/admin money APIs or packaged crash-window proof.
- Module boundary, 17 context-isolation checks, 244-handler HTTP inventory and diff whitespace check passed. No old test expectations were removed. Settlement overall remains pending: balance creation/read use case, COD holds, refunds/outbox, payment workflow, root relocation/legacy retirement, artifact/recovery proof and main integration.


### Settlement balance account core checkpoint

- `DefaultBalanceAccountUseCase` now owns existing-account reuse, zero-wallet creation intent and integrity-conflict winner re-read/missing-winner failure. `BalanceServiceImpl` retains DTO/exception mapping, bounded SQL read projections and original transaction annotations. `JpaBalanceAccountAdapter` owns saveAndFlush and Spring integrity exception translation; shared JPA account/wallet mapping is reused from `JpaLedgerAdapter`. Account creation through the ledger still calls the existing REQUIRES_NEW facade then re-locks the row; getBalance retains its outer transaction. No automatic account repair/rebuild or new policy is introduced.
- Red failed on absent use case/conflict type (`/tmp/settlement-balance-application-red.log`). Final full clean verify exited 0 (`/tmp/settlement-balance-core-clean-verify.log`): domain 18/application 18/host 92 = 128 tests, zero failures/errors/skips. Four real Spring/JPA workflow tests now include repeated creation/read preserving the existing deposited money/account identity. Existing Kafka/PostgreSQL replica/replay/DLT and financial rollback proof passes with the new balance core. Domain coverage remains 163/167 lines, 147/155 branches; application 134/134 lines, 45/46 branches. Existing 85% gates retained.
- Fresh resolved dependency generation exited 0 (`/tmp/settlement-ledger-balance-resolved-dependencies.log`); canonical domain/application API/application/service graphs all pass unchanged boundary checker. Module gate, 17 context-isolation checks, nine baseline-contract tests, HTTP inventory and whitespace check pass. Review confirms API/error compatibility, balance fields/counters, transaction propagation, managed entity mapping and existing conflict behavior. No assertions or defaults weakened.
- Ledger mutation/account decisions are now core-owned; COD capacity holds are next. Whole Settlement checkbox remains pending until holds/refunds/payment and relocation/retirement/artifact/recovery/main proof finish. Whole backend baseline and Saga-to-Dispatch objective remain open.


### Settlement COD capacity core checkpoint

- `CodHoldCommand`, `CodHold` and `WalletBalance` own existing validation, replay admission, transition/expiry and reservation arithmetic. `DefaultCodCapacityUseCase` owns sorted batch reservation, replay/create ordering, hold consumption and bounded expiry orchestration through `CodCapacityStore`. The host service is now a transaction/scheduler/DTO facade; `JpaCodCapacityAdapter` retains pessimistic row locks and managed-row persistence. Returned hold lists retain their previous mutability. No HTTP/event/schema/provider flag changed.
- Red domain/application runs failed on missing core types. Reviewed full clean verify exited 0 with Docker-accessible owned PostgreSQL/Kafka (`/tmp/settlement-cod-hold-owned-clean-verify.log`): 145 tests, zero failures/errors/skips. The earlier sandbox run skipped three Docker tests and is not used as integration proof. Domain coverage: 202/206 lines, 219/227 branches; application: 191/191 lines, 77/78 branches. Existing 85% gates unchanged.
- Three Spring/JPA hold tests prove batch replay, reservation consumption without changing deposit money, expiry, insufficient-capacity rollback and all-item rollback/no ACK when one batch hold is missing. Actual PostgreSQL simultaneous 70-unit requests against a 100-unit deposit leave exactly two holds/reserved 70; only one batch succeeds. Kafka completion consumes its persisted hold exactly once across replica/replay/DLT verification; fixture hold/order/delivery identity is consistent.
- Review preserves legacy nullable wave id key, replay without payload comparison, duplicate offer behavior and terminal transition policy; these are not claims that the old financial policy is correct. Policy corrections require explicit repository/product authority. Two separate documented-contract repairs remain next: batch listener currently acknowledges before DB commit, and COD expiry scheduling is incorrectly gated by default-off refund relay. Refund/payment extraction, root relocation, legacy retirement, packaged recovery and main integration remain pending. Settlement remains unchecked overall and source remains in the refactor worktree.
- Module boundary, all 17 isolated test contexts, nine baseline contract tests, 244 HTTP handler inventory and whitespace checks passed. Fresh canonical resolved dependency graphs are checked separately before committing this slice.


### Settlement batch financial ACK repair

- Authority: `docs/workflows/settlement_finance_flow.md` requires Kafka acknowledgment only after the financial DB commits. Two real Spring/JPA transaction tests reproduced the batch listener defect: ACK happened inside an enclosing transaction and remained acknowledged after rollback (`/tmp/settlement-batch-ack-red.log`: five tests, two assertion failures, zero errors/skips).
- Batch accept/release listener now registers acknowledgment with Spring transaction synchronization, preserving immediate acknowledgment only for direct calls without synchronization, consistent with the existing completion listener. Transaction boundaries, topic/target/hold parsing and whole-batch rollback remain unchanged. Focused green run passed all five hold integration tests (`/tmp/settlement-batch-ack-green.log`). Full owned PostgreSQL/Kafka clean verify exited 0 (`/tmp/settlement-batch-ack-owned-verify.log`): 147 tests, zero failures/errors/skips; no test or gate was weakened. COD expiry scheduler repair remains next.


### Settlement COD expiry scheduling repair

- Authority: production matching plan requires Settlement expiry of orphaned HELD rows; test-context contract requires `spring.task.scheduling.enabled=false` isolation. Three runtime context tests reproduced default/explicit-enabled COD job absence with refund relay off, and scheduling unexpectedly active when disabled with refund relay on (`/tmp/settlement-cod-scheduler-red.log`).
- Replaced the unused, refund-gated scheduling configuration with `CodCapacitySchedulingConfig`: documented periodic runtime trigger defaults on and respects the shared scheduling-disable knob. Refund relay still owns its independent default-off business flag. Expiry cadence and scan size are unchanged (1000 ms/200). Focused context proof passes all three cases (`/tmp/settlement-cod-scheduler-green.log`).
- Baseline gate allows only the exact scheduling annotation at the canonical Settlement path, like Tracking; it still rejects altered property/default-enabled capability in that file and the same annotation in foreign files. All 13 gate contract tests pass; 17 context-isolation checks, module gate and whitespace checks pass. Full owned Settlement clean verify exited 0 (`/tmp/settlement-cod-scheduler-owned-verify.log`): 150 tests, zero failures/errors/skips, including real PostgreSQL/Kafka concurrency/replay. Docker daemon inspection confirmed it remained available during startup; the same Maven process was observed to completion without restarting. HTTP inventory remains 244 handlers. Existing default-enabled application API finding remains unresolved and is not allowlisted by this repair.


### Settlement refund domain policy extraction

- Authority: canonical Settlement feature/refund runbook and existing runtime rules preserve COD pre-pickup NO_REFUND_REQUIRED, default-off ONLINE provider execution, source/reason-specific pre-pickup admission, and post-pickup DELIVERY_DISPUTE manual review without provider outbox.
- New framework-free `RefundPolicy` has immutable cancellation/delivery-exception snapshots and existing trigger/status, captured/refund arithmetic, actor normalization, identity and canonical monetary validation. Host processing now calls domain validation before full-event JSON fingerprint serialization and uses the domain decision for cancellation status/money/actor and exception money. Existing receipt SQL, exact replay, race winner recovery, read policy and outbox orchestration remain in the host pending the next application extraction; this is not closure of the full refund step.
- Seven domain tests cover missing/nonpositive identities, invalid state/type/source/reason/payment, all pre-pickup COD statuses, enabled/disabled ONLINE boundary, canonical money null/negative/zero/reconciliation errors and both post-pickup exception states. Red failed on absent RefundPolicy; domain green verify passed (`/tmp/settlement-refund-policy-red.log`, `/tmp/settlement-refund-policy-domain-green.log`). Connected owned PostgreSQL/Kafka full clean verify exited 0 (`/tmp/settlement-refund-policy-owned-verify.log`): 157 tests, zero failures/errors/skips. Domain coverage: 309/313 lines, 367/379 branches; existing 85% gates retained. Real two-replica refund/completion replay/DLT, COD capacity races and transaction rollback/ACK proof pass through the wired policy. Module gate, 17 context checks, 13 baseline contract tests, 244 HTTP handler inventory and whitespace checks pass unchanged.


### Settlement refund receipt application extraction

- `DefaultRefundCaseUseCase` now owns both cancellation and post-pickup dispute processing: domain validation before lazy full-event fingerprinting, event/key/order identity lookup precedence, exact replay rejection, immutable draft creation, insert claim race winner validation and REQUESTED-only outbox intent. `RefundDraft` and `RefundReceipt` own canonical persistence snapshots, stable business identity and replay constraints. The active host methods invoke this core directly.
- `JpaRefundCaseAdapter` retains the exact H2/PostgreSQL native inserts, nullable principal overload, ORDER_TOTAL lookup and original transaction scope. An invocation-local bounded entity map preserves returned entity identity and uses no shared mutable state or extra SQL. It delegates only the technical outbox persistence to the existing mandatory-transaction facade; deterministic outbox event construction and read ownership policy remain next extractions.
- Application red failed on missing port/core types (`/tmp/settlement-refund-usecase-red.log`); six fake-store tests passed (`/tmp/settlement-refund-usecase-green.log`), proving lookup precedence, new receipt before outbox, no duplicate outbox on replay/race, winner validation/missing-winner failures, and invalid snapshot rejection before serialization. Domain tests cover draft snapshot fidelity and contradictory receipt identity/trigger/hash. First connected clean verify exited 0 (`/tmp/settlement-refund-usecase-owned-verify.log`); Final connected clean verify exited 0 (`/tmp/settlement-refund-usecase-final-owned-verify.log`): 166 tests, zero failures/errors/skips, including real Spring/JPA one-receipt/one-outbox replay and joint rollback after injected post-insertion failure. Application coverage: 220/220 lines, 93/94 branches; domain: 326/330 lines, 381/393 branches. All 85% gates retained. Review added contradictory race-winner assertions for both workflows; complete core verify passed (`/tmp/settlement-refund-race-review-core-verify.log`). Original provider defaults, SQL signatures, event serialization and financial transaction behavior are unchanged.
- Source still lives only in refactor worktree. Full refund checkbox, whole Settlement tranche, root relocation/legacy retirement/recovery/main integration and Saga-to-Dispatch remain open.


### Settlement refund read ownership core

- `DefaultRefundQueryUseCase` owns admin status/all selection, 1–100 read limits, missing-case and customer identity admission, principal-only versus unmigrated-legacy compatibility selection, fallback metric intent, and customer-safe projection. API records contain framework-free values; customer shape excludes event/idempotency/actor/reason/provider/error fields. Enforcement is injected from the existing host flag, never selected by request data. Legacy single-user overload and its historical behavior remain source-compatible.
- `JpaRefundQueryAdapter` retains unchanged bounded repository queries and maps every admin projection field, nullable enum and timestamp. Host retains transaction/HTTP DTO and ResourceNotFoundException mapping. No ownership rollout default or wire field changed. Three core tests first failed on absent types, then passed (`/tmp/settlement-refund-query-red.log`, `/tmp/settlement-refund-query-core-green.log`).
- Full owned clean verify exited 0 (`/tmp/settlement-refund-query-final-owned-verify.log`): 170 tests, zero failures/errors/skips. Spring/JPA fixture proves compatibility sees only matching principal plus unmigrated matching legacy rows, enforcement sees only the principal, foreign migrated/unmigrated rows remain excluded, legacy overload and admin filter/limit/get remain compatible. Real PostgreSQL/Kafka replay/claim/DLT, COD races and transaction/outbox rollback proof pass. Application coverage 240/241 lines, 115/116 branches; domain remains 326/330 lines, 381/393 branches; 85% gates unchanged.
- Four fresh canonical runtime graphs pass; module boundary, 17 isolated contexts, 13 baseline contract tests, 244 HTTP handlers and whitespace checks pass. An automatic approval-review quota failure initially prevented the adapter/full-test action from executing; read-only inspection confirmed no mutation. After the permitted retry time, approval and Docker health check succeeded and the original proof ran; no gate or approval was bypassed. Outbox construction/relay and payment/root-layout/recovery/main remain pending.


### Settlement refund outbox domain/application extraction

- `RefundOutboxRequest`/`RefundOutboxIntent` own deterministic refund/status event identity and aggregate/key intent; `RefundOutboxFailure` owns the existing 2000-character error bound, exponential retry delay and DEAD-at-12 threshold. Enqueue/relay use cases own exists replay, intent creation, 100-row scan and publish-before-SENT or retry/DEAD decisions. Host services retain MANDATORY/scheduled transaction boundaries, default-off relay flag and cadence; JSON, SQL locks/managed-row updates and Kafka 10-second future wait remain explicit infrastructure adapters.
- Domain/application red runs failed on missing types; connected core green verification passed. First full owned clean verify exited 0 (`/tmp/settlement-refund-outbox-owned-verify.log`): 177 tests, zero failures/errors/skips. Existing actual PostgreSQL/Kafka replay/claim/DLT, COD races and receipt/outbox atomic rollback pass with the extracted enqueue path. Module/context/13 baseline-contract/244 HTTP handler/whitespace checks pass; no rule or financial flag changed.
- User explicitly requested multi-agent delegation. Native spawn accepted the required gpt-5.6-luna model. Independent read-only reviewer found no concrete behavior regression but identified missing relay adapter/DB proof. A separate test-only writer owns only `RefundOutboxRelayIntegrationTest.java` and focused H2 validation; parent owns production integration/full reactor and avoids concurrent Maven. Another read-only agent inventories payment/payout and relocation. Reports require parent inspection and executable proof before closure.
- Relay JPA persistence/rollback proof and final integrated verification remain pending; do not close refund or Settlement overall based on the initial green run. Payment extraction preserves the documented default-off, contract-only payout boundary; refactor does not authorize new provider/approval/reconciliation behavior.

- Final integrated owned full clean verify exited 0 (`/tmp/settlement-refund-outbox-final-owned-verify.log`): 180 tests, zero failures/errors/skips. Independent review reported no concrete equivalence defect; its identified adapter-proof gap is now covered by three reviewed Spring/JPA tests: SENT plus retained attempts/cleared error/no republish, exact topic/key/payload and 10-second wait, retry/DEAD with truncation and later success, and transaction rollback followed by replay. These are H2/transport-mock proofs, not a real process-crash or exactly-once Kafka claim. Existing real PostgreSQL/Kafka tests also pass. Application 253/254 lines, 119/120 branches; domain 340/345 lines, 387/399 branches; 85% gates retained. All four fresh canonical runtime graphs pass. Refund extraction checkbox is closed; Settlement overall remains open.
- User subsequently explicitly chose gpt-6.1-sol for child agents; this overrides the earlier workspace gpt-5.6-luna preference for new task delegation. Completed reviewed results are retained. Parent remains responsible for source review, full verification and final integration.
- Relocation inventory is prepared. Parent confirmed the existing crash-window shell payload lacks canonical totalPrice as well as using the old host JAR path; fix canonical fixture and audit owned infrastructure before final rehearsal. No crash harness was run against canonical operator services in this tranche.

### Settlement parallel work after child-model change

- New native child tasks explicitly use `gpt-6.1-sol`, as requested by the user. Payment extraction owns isolated worktree `.worktrees/backend-settlement-payment-core`, branch `refactor/settlement-payment-core`, based on `7883578`; it owns payment core/adapters/composition/tests only. It must preserve existing provider gates, transaction rollback, callback replay and the separate UNKNOWN-preserving provider seam. Parent reviews and integrates before relocating service paths.
- A read-only composition audit independently checks unused wrappers, remaining host business logic and the optional application API boundary. A second isolated writer owns `.worktrees/backend-settlement-crash-proof`, branch `refactor/settlement-crash-proof`, and only crash harness/helper surfaces: canonical event totals, owned disposable infrastructure, current/future packaged JAR selection and cleanup. Its actual packaged recovery rehearsal remains a parent integration step.
- Parent owns this plan, combined source review, full verification, root relocation and main integration. No concurrent Maven operates against the same worktree; workers may not change shared operator infrastructure. Delegation and candidate commits are not completion evidence.

### Settlement customer payment admission policy

- Canonical product authority requires explicit unsupported create/read until customer ownership and online Order support exist together. `CustomerPaymentPolicy` now owns the existing customer/principal/legacy identity admission, positive order ID, exact reference format and unsupported outcomes. The host only maps authenticated actors and domain exceptions to the existing HTTP-facing exception types. Public error codes, validation precedence and lack of payment-row lookup remain unchanged.
- Four domain tests cover identity/input boundaries and both unsupported outcomes. Red compilation failed on absent policy (`/tmp/settlement-customer-policy-red.log`); domain verify passed (`/tmp/settlement-customer-policy-domain-green.log`). Focused wired host/controller proof passed (`/tmp/settlement-customer-policy-host-green.log`). Combined full verification remains pending after the independent Payment candidate is integrated.

### Settlement payment integration, recovery proof and root relocation (2026-10-04)

- Payment candidate `fd81713` was cherry-picked without conflict as `eda1778`. Fresh owned clean verify `mvn -B -pl :settlement-service -am clean verify` exited 0 (`/tmp/settlement-payment-integrated-verify.log`): domain 42/application 43/host 113, zero failures/errors/skips; the three Docker PostgreSQL/Kafka replica/replay/concurrency tests executed. Independent gpt-6.1-sol review found no blocker/major financial regression; accepted minor differences: workflow-level payment logs removed, `application-api-enabled` no longer gates payment workflow composition, unwired placeholder PayOS provider beans removed (consistent with the contract-only seam in `docs/platform/product/features/settlement.md`). Follow-up hardening (restore workflow logs; signed IPN through MVC; concurrent PostgreSQL callbacks; real Kafka publisher; top-up/publish failure rollback) remains open.
- Crash harness unit tests (8, later 9 after relocation fixtures) pass. Actual packaged crash-window rehearsal on owned disposable PostgreSQL/Kafka passed before and after relocation: SIGKILL after DB commit and before ACK, uncommitted offset, exact redelivery, unchanged ledger, lag 0 (`/tmp/settlement-crash-window-rehearsal.log`, `/tmp/settlement-crash-window-reloc.log`).
- Relocated to `settlement/{domain,application-api,application,infrastructure,boot}` mirroring Tracking `9cecfe7`: 225 byte-identical renames; boot keeps only `SettlementServiceApplication`, runtime properties and host tests; infrastructure owns HTTP/Kafka/JPA/scheduler/security/composition and all production dependencies. Artifact `settlement-service:0.0.1-SNAPSHOT`, packages, flags, migrations and contracts unchanged; only infrastructure test properties gained config-server/discovery/Kafka/scheduling isolation. Compose `SERVICE_PATH=settlement/boot`; baseline gate rules only re-pathed; HTTP catalog 244 operations/229 schemas with only source paths changed. Old `settlement-service/` and `modules/settlement/` removed.
- Post-relocation owned clean verify exited 0 (`/tmp/settlement-relocated-verify.log`): domain 42/application 43/infrastructure 1/boot 113, zero failures/errors/skips. Independent gpt-6.1-sol review: no blocker/major; two stale doc links fixed. Generated mirror under `docs/platform/system/reference/` is left to its generator. Remaining before checking Settlement: payment hardening follow-up and main integration.

### User decisions for the next tranches (2026-10-04)

- Source: read-only Match inventory (gpt-6.1-sol, snapshot `eda1778`) and the user's answers in the coordinating chat.
- Settlement payment: if the concurrent DEPOSIT_TOPUP callback proof shows a double top-up, fix it in Settlement before main integration (red test first).
- Match: fix the documented contract defects inside the Match tranche, each as a separate red-first commit, never folded into equivalence refactors: (1) `DispatchBatchReleaseListener` ACK before commit; (2) batch reservation ignores cancellation tombstones/deadline and stop does not retire pool items (authority `delivery-matching.md`); (3) feasibility ETA route order (per-order pickup→dropoff) differs from the emitted snapshot (all pickups then dropoffs) — canonical order still needs to be pinned to `delivery-matching.md`/`production-matching-v1.md` before the fix. Runtime greedy optimizer is retained; min-cost flow is separate algorithm work, not authorized here.
- Saga → Dispatch: user chose option C — Dispatch becomes the delivery-coordination owner and the current Saga orchestrator is retired or repurposed. This changes ownership and likely Kafka topics/consumers, so it requires its own plan (cross-repo if any app/web consumer is affected) with migration, dual-run/cutover and rollback before implementation. It does not change Match slices M1–M6, which preserve current ownership.
- Ordered tranches: Settlement → Match (M1 single-dispatch domain policy, M2 batch domain policy, M3 single use cases, M4 batch use cases + COD hold orchestration, M5 adapters/composition, M6 relocation/packaged recovery; contract fixes interleaved) → Delivery → Order/Notification → Saga→Dispatch (option C) → Phase 8/9 services → remove `modules/`.

### Settlement payment hardening and concurrent top-up fix (2026-10-05)

- gpt-6.1-sol hardening restored workflow logs through observation-only `Effects` hooks in infrastructure (application stays framework-free) and added proofs: full context with processing on/application-api off, signed VNPay IPN through MVC (00/99/97 and DB state), real KafkaTemplate publisher payload/failure swallowing, top-up/publish failure rollback and fake replay.
- Real PostgreSQL concurrency proof reproduced a pre-existing double top-up (two identical callbacks: ledger=2, wallet +200 for 100, two success events). Per user decision it is fixed: callback and fake confirmation load the payment through `findByPaymentRefForUpdate` (PESSIMISTIC_WRITE) before the pending check, so a duplicate waits and replays the committed SUCCESS. Red `/tmp/payment-double-topup-red.log`; green full clean verify `/tmp/settlement-payment-lock-verify.log`: domain 42/application 43/infrastructure 1/boot 125, zero failures/errors/skips; observed ledger=1, wallet +100, events=1.
- Settlement checkbox closed after this commit is fast-forwarded to main.
