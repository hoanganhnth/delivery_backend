# Service architecture consolidation

Date: 2026-10-02

## Status

Active. User approved the root-level service layout and completing one service
before starting the next. Worktree: `.worktrees/backend-service-architecture`
at workspace root, branch `refactor/service-architecture`, base `71218d7`.
Auth tranche is integrated on `main` at `047ccb2`; Restaurant is next. The
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
