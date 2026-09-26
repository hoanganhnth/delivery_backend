# Execution Plan: Backend platform and business modules

## Status

Active — phase 0 complete; the Restaurant/Menu problem contract is approved and
phase 1 implementation is in progress. This is not a completed backend
migration.

## Outcome and authority

Implement the user-approved platform/contracts/SDK/testkit architecture in
incremental Maven modules inside this repository. Each business service keeps
its executable artifact and database ownership. The conversation-approved plan
is captured here so subsequent sessions can continue without restarting work.

Read `AGENTS.md`, `docs/WORKFLOW.md`, existing service contracts and regression
tests before changing a slice. Preserve unrelated checkout changes.

## Binding decisions

- Worktree: `../.worktrees/backend-platform-modules`, branch
  `refactor/platform-modules`, starting commit `52be51c`.
- Java 17, Spring Boot 3.5.15 and existing Cloud/dependency versions stay fixed.
- Core: domain, application-api, application are framework-independent Java.
  Infrastructure implements application ports. The existing service host wires
  adapters and exposes HTTP/listener/scheduler entrypoints.
- New business libraries live under `modules/<service>/`; existing executable
  `<service>-service` paths and artifact names remain stable.
- No cross-service domain/repository/application imports; inter-service traffic
  stays HTTP/events. No shared JPA entities or universal common module.
- No API/event/schema, feature-flag, ownership, pricing, retry, transaction or
  default behavior changes hidden in a structural refactor.
- Domain and application modules must each reach at least 85% LINE and BRANCH
  coverage with unit tests before their migration is complete. Interface-only
  modules report N/A. Report adapter/host coverage separately without hiding
  handwritten code. Coverage is not concurrency or production evidence.
- Existing stable code is retained; no empty five-layer scaffold for technical
  services. Bugs require separately identified regression tests and changes.
- No registry publishing, deploy, merge or push in this implementation request.

## Module catalogue

| Group | Modules and first consumers |
| --- | --- |
| Build | delivery-build-parent, delivery-platform-bom; migrated modules first |
| Existing platform | observability-starter, auth-resource-server-starter, runtime-platform-starter; retain artifact names |
| HTTP | platform-http-blocking: Restaurant/Auth; platform-http-reactive: Order/Match |
| Messaging | platform-kafka-support: Order/Notification, preserving local consumer/retry policies |
| Contracts | existing identity-contracts; routing-contracts; order-contracts (created/cancelled); delivery-contracts (completed/status-updated) |
| SDK | routing-client for Order and Match, with caller-owned fallback/retry/deadline policy |
| Testkit | platform-testkit-core, platform-testkit-http, platform-testkit-integration; test scope only |
| Business | <name>-domain, <name>-application-api, <name>-application, <name>-infrastructure, existing <name>-service host |

Contract modules contain only published wire types, not application commands.
Do not change raw-payload fingerprints or Kafka type headers when moving types.
SDKs never import service internals. Credentials are target-specific and never
forwarded automatically. Kafka helpers do not choose group/topic/ACK/DLT policy.
Outbox/inbox schemas, atomicity, ordering and business receipts remain local.

## Ordered implementation

- [x] Phase 0: baseline tests, coverage inventory, build parent/BOM, architecture
  gates and transitive Docker artifact freshness proof.
- [ ] Phase 1: Restaurant pilot. Before implementation, approve a problem
  contract and test matrix for actors/ownership, Restaurant CRUD, menu
  reads/writes, state transitions, invariants, validation, authorization,
  duplicate/reordered input, concurrency, transaction failure, external
  boundaries and compatibility. Only then extract the agreed slice; decision,
  rating, serviceability and inventory remain separate follow-up problems.
  Preserve mapper, lock/flush, transaction and outbox behavior. Add only
  testkit helpers actually needed.
- [ ] Phase 2: HTTP support, routing-contracts and routing-client; adopt Order
  then Match, retaining different caller failure/fallback policies.
- [ ] Phase 3: Order/Delivery event contracts and Kafka support in Order then
  Notification; golden payloads, raw retry, owner DLT, replay/integration proof.
- [ ] Phase 4: User, Auth, Web BFF, Shipper core migrations.
- [ ] Phase 5: Notification, Order core migrations.
- [ ] Phase 6: Saga, Delivery, Match core migrations.
- [ ] Phase 7: Routing, Tracking, Settlement core migrations.
- [ ] Phase 8: Search, Analytics, Promotion, Flashsale, Livestream migrations.
- [ ] Phase 9: Simulator/tools, full dependency audit and regression closure.

## Phase 1 approved Restaurant/Menu problem contract

This contract is the authority for the pilot. It separates existing behavior
that must be preserved from independently authorized bug fixes and additive
contracts.

### Actors, ownership and compatibility

- `principalId` is the ownership identity. `SHOP_OWNER` creates a Restaurant
  only for itself. `ADMIN` must supply `ownerPrincipalId`; Restaurant verifies
  that target through a typed Auth boundary as an existing, `ACTIVE`
  `SHOP_OWNER` principal.
- The typed Auth lookup is `GET /api/auth/internal/principals/{principalId}`
  with `Internal-Token`. It returns the raw `IdentityPrincipal` wire record on
  200 so consumers do not import Auth's service-local response envelope. A
  malformed/non-positive ID returns 400, an invalid or absent internal token
  returns 403 before lookup, and an unknown positive principal returns 404.
  The endpoint is not routed through the public Gateway.
- Menu ownership is inherited from its Restaurant. Public catalogue and
  checkout never trust a client-supplied owner or price.
- Existing executable `restaurant-service`, URLs and artifact path stay stable.
  Lifecycle/version/timezone response fields and lifecycle endpoints are
  additive. Existing DELETE endpoints become archive aliases.
- Optimistic concurrency is introduced additively: mutation responses expose
  `version`; PUT/PATCH bodies accept `expectedVersion`; DELETE accepts an
  optional `expectedVersion` query parameter. Missing versions increment
  `delivery.catalog.expected_version.missing` while
  `CATALOG_EXPECTED_VERSION_REQUIRED=false`; stale versions fail with 409
  `STALE_VERSION`. Required enforcement is deferred until Web sends versions.

### Lifecycle rules

- Restaurant states are `ACTIVE`, `PAUSED`, `ARCHIVED`. New owner-created
  Restaurants start `ACTIVE`. Restaurant archive does not mutate persisted Menu
  state. Public catalogue/search/checkout hide `ARCHIVED`; `PAUSED` remains
  visible but rejects new checkout. Owner/admin management lists include
  archived rows, and detail-by-ID remains resolvable for history.
- Menu states are `AVAILABLE`, `SOLD_OUT`, `DISCONTINUED`, `ARCHIVED`. Public
  catalogue/search/checkout expose only `AVAILABLE` items whose Restaurant is
  not archived and can accept checkout where required.
- Owners may pause/archive their Restaurant and change ordinary Menu selling
  state. Only `ADMIN` may restore a Restaurant from `ARCHIVED` to `PAUSED` or a
  Menu item from `ARCHIVED` to `DISCONTINUED`. Reapplying the current target
  state is idempotent.
- Normal business flows never physically delete Restaurant or Menu rows.
  Confirm/reject, delivery, settlement, rating and compensation for existing
  orders remain resolvable after pause/archive. Physical purge policy is TODO.
- Lifecycle endpoints are `PATCH /api/restaurants/{id}/lifecycle` and
  `PATCH /api/menu-items/{id}/lifecycle`, with target state and expected version
  in the request body.

### Operating-hours and checkout rules

- Each Restaurant stores an IANA timezone; existing rows default to
  `Asia/Ho_Chi_Minh`. Both operating times null means 24/7, exactly one null is
  invalid, and equal open/close times mean 24/7.
- Availability uses `[open, close)`: opening is included and closing excluded.
  `open < close` is a same-day window; `open > close` spans midnight. Domain
  decisions receive `Clock`/`Instant` and `ZoneId`; they never read JVM local
  time directly.
- Checkout authority is PostgreSQL and fails closed for missing, archived,
  paused, unavailable or invalid records. Existing Restaurant Redis catalogue
  keys are removed only after DB-backed checkout is proven; Search remains on
  its existing transactional outbox.

### Integrity, audit and proof boundaries

- Lifecycle mutation, optimistic version check, immutable local audit row and
  outbox write are one PostgreSQL transaction. Audit stores actor/role/action,
  aggregate identity, whitelisted before/after state, versions, timestamp and
  correlation ID; it has no update/delete API. Retention/purge is TODO.
- Domain unit tests cover every transition, authorization decision, idempotent
  target, time/null boundary and timezone conversion. Parent/child persistence
  independence is proved at the application/adapter boundary in Slice 3, where
  Restaurant archive orchestration and stored Menu state coexist. Adapter tests
  prove PostgreSQL locking/version conflicts, rollback and audit/outbox
  atomicity. HTTP tests prove response/error compatibility. Search and client
  tests prove archived filtering and additive parsing.
- Coverage 85/85 is required for domain and application modules, but does not
  prove PostgreSQL concurrency, Kafka recovery or performance.

### Explicitly deferred problems

- Weekly/holiday/multiple operating windows; owner transfer, staff and multiple
  owners; audit retention and physical purge; required-version enforcement;
  inventory deadlock/load proof; rating high-contention benchmark; ETA provider
  runtime proof; Search bulk rebuild; cache/index optimization without
  before/after measurements.

### Current Restaurant/Menu use-case test proof (2026-09-26)

Tests now describe behavior at the HTTP, host/use-case, and persistence-query
boundaries. This closes the characterization gate for the core catalog paths;
it does not mean every behavior is already implemented in the new application
module.

| Business use case | Actors and exception cases exercised | Executable proof | State |
| --- | --- | --- | --- |
| Create Restaurant | SHOP_OWNER self; ADMIN assigning an active SHOP_OWNER; missing/unsupported actor; invalid fields; missing owner/Auth failure; incomplete opening-hours pair; transactional Search outbox and after-commit cache | `DefaultCreateRestaurantUseCaseTest`, `DefaultRestaurantOwnerAssignmentUseCaseTest`, `RestaurantControllerTest`, `RestaurantControllerIntegrationTest`, `RestaurantCreationIntegrationTest`, `RestaurantServiceTest`, `CatalogMutationValidationTest` | Extracted through application/port; verified in unit, MockMvc and H2 integration tests |
| Update Restaurant | Owner, ADMIN, foreign owner, missing row; partial update preserving the other hour; resulting incomplete schedule rejected before mutation | `RestaurantServiceTest`, `RestaurantControllerIntegrationTest`, `CatalogMutationValidationTest` | Verified in unit and H2 integration tests |
| Read Restaurant | Missing ID; archived detail remains resolvable; public list/search/page hide archived; management can still see archived; public pagination envelope | `RestaurantServiceTest`, `RestaurantControllerTest`, `CatalogArchiveIntegrationTest` | Verified in unit, MockMvc and H2 query tests |
| Create Menu item | Owner access; ADMIN cross-owner access; foreign owner and missing Restaurant rejected; `principalId` wins over matching legacy ID | `MenuItemServiceTest`, `MenuOwnershipIntegrationTest` | Verified in unit and H2 integration tests |
| Update Menu item | Owner, ADMIN, foreign owner, missing item; request cannot reassign the parent Restaurant; invalid field validation | `MenuItemServiceTest`, `MenuItemMapperTest`, `MenuOwnershipIntegrationTest`, `MenuItemControllerTest` | Verified in unit and H2 integration tests |
| Read Menu | Public reads return only AVAILABLE items under non-archived Restaurants; management reads include unavailable/history rows and enforce owner/Admin scope; pagination bounds and envelope | `MenuItemServiceTest`, `MenuItemControllerTest`, `MenuCatalogBoundaryTest`, `CatalogArchiveIntegrationTest`, `MenuOwnershipIntegrationTest` | Verified in unit, MockMvc and H2 query tests |
| Archive and restore | Idempotency, stale version, owner/Admin role matrix, immutable audit facts, parent archive preserving Menu status/rows | `CatalogLifecycleServiceTest`, `CatalogArchiveIntegrationTest`, `CatalogLifecycleDecisionUseCaseTest` | Verified in unit and H2 persistence tests |
| Checkout validation | PostgreSQL-canonical name/price/status/parent lifecycle, missing/foreign items and invalid operating schedule fail closed | `OrderValidationPersistenceIntegrationTest`, `OrderCacheValidationServiceImplTest`, domain `OperatingScheduleTest` | Verified on H2; PostgreSQL runtime proof remains TODO |
| Cache/Search side effects | Cache work runs after commit; rollback skips cache; Search mutation persists an outbox record instead of publishing directly | `CatalogCacheSynchronizerTest`, `SearchSyncPublisherTest`, `CatalogLifecycleServiceTest` | Unit proof exists; real PostgreSQL atomicity proof remains TODO |

The latest full Restaurant reactor run reports 318 tests (237 in the host) with
no failures, errors, or skips. `restaurant-domain` and `restaurant-application` pass their
independent JaCoCo 85% line and branch gates: domain is 124/124 lines and 77/78
branches; application is 57/57 lines and 40/44 branches. These percentages
cover only the currently extracted core classes. Restaurant creation now runs
through the application use case and infrastructure persistence port; remaining
CRUD workflows still run in the executable host and are characterized there.

Remaining proof and extraction work:

- Move Restaurant update/read and Menu create/update/read orchestration
  behind framework-free `restaurant-application-api` use cases and ports, with
  unit tests in `restaurant-application`; then retain the host tests as adapter
  and HTTP contract tests.
- Prove optimistic write conflicts, transaction rollback with no audit/outbox
  residue, and audit/outbox atomicity against PostgreSQL. H2 coverage is not
  PostgreSQL concurrency evidence.
- Inventory legacy rows where only one operating time is set. Reads now report
  `isOpen=false` for an invalid stored schedule, and new create/update flows
  reject incomplete schedules. A repair policy and safe database constraint
  need production-data evidence before migration.
- `UpdateRestaurantRequest` still treats null as “not supplied”, so it cannot
  explicitly clear both hours to restore 24/7. Define an additive clear/reset
  contract before implementing that operation.
- General Restaurant/Menu PUT and DELETE do not yet accept the optional
  `expectedVersion` promised by the approved additive contract. Missing-version
  metrics/version enforcement remain deferred until clients are migrated.
- Search projection state for archived parents and SOLD_OUT/DISCONTINUED items
  needs a focused consumer/projection test; the current wire extraction and
  outbox version/tombstone tests are not that proof.

### Phase 1 implementation sequence

- [x] Slice 1: pure `restaurant-domain` lifecycle and operating-hours rules,
  with independent 85/85 coverage and no runtime wiring.
- [x] Slice 2: typed Auth principal lookup and platform HTTP boundary.
- [x] Slice 3: Restaurant/Menu persistence, parent/child archive independence,
  lifecycle endpoints, versioning, immutable audit and soft-delete aliases.
- [x] Slice 3 test contract: characterize core Restaurant/Menu create, update,
  read, ownership, validation, pagination, lifecycle and public visibility
  across host, HTTP and H2 persistence tests.
- [x] Slice 3A: move Restaurant creation orchestration behind application API
  and ports; keep owner assignment and persistence effects covered independently.
- [ ] Slice 3B: move Restaurant update, public reads and management reads behind
  application use cases; preserve DTO and pagination adapters in the host.
- [ ] Slice 3C: move Menu create/update/public/management read use cases behind
  application ports; preserve inherited ownership and immutable parent ID.
- [ ] Slice 4: PostgreSQL-canonical checkout; then remove obsolete Redis graph.
- [x] Slice 5: Search wire contract extraction and archived projection behavior
  (wire type extracted; archived projection behavior remains covered by the
  existing tombstone/version tests).
- [ ] Slice 3 hardening: complete `restaurant-infrastructure` extraction for
  persistence, cache, outbox and internal-client adapters; the first identity
  adapter has now moved out of the host.
- [ ] Slice 6: extract existing order decision/outbox behavior unchanged.
- [ ] Slice 7: rating concurrency fix and extraction.
- [ ] Slice 8: serviceability and inventory extraction with separate proofs.
- [ ] Slice 9: client SDK adoption, compatibility cleanup and canonical docs.

Within each service: lock existing behavior with characterization tests, define
ports, implement core tests, move one vertical slice, prove adapters/runtime,
review, commit. Remove transitional facades only when no caller remains.
Gateway/Discovery/Config/CLI use only layers that have actual responsibility.

## Validation and recovery

- Baseline: `mvn -B clean test`; failures predating edits are recorded, not skipped.
- Core: JUnit without Spring; architecture and Maven dependency checks.
- Adapters: PostgreSQL mapping/constraint/rollback/outbox tests; HTTP contract
  tests; Kafka/Redis duplicate/reorder/crash proof where the slice crosses them.
- Host: existing security/HTTP/listener contracts and packaged startup.
- Build: `mvn -pl <service> -am verify`, service packaging, inventory/Compose
  gates; changes in transitive source/resources/POM must invalidate old JARs.
- Per slice: `git diff --check`, independent review and focused regression.
- Never treat fake adapters or H2 as PostgreSQL concurrency proof.
- Roll back code artifacts per slice; database schema and wire contract stay
  unchanged. Keep existing runnable service while incrementally extracting.

## Execution record

- 2026-09-26, Task 1 / Slice 3A: added framework-free immutable creation
  command/result records, use-case and persistence-port contracts, and an
  application implementation reusing the existing owner-assignment use case.
  The host maps actor/request/result only; the infrastructure adapter owns the
  creation transaction, existing entity defaults, log-only initial balance,
  Search outbox write and after-commit cache scheduling. No schema, HTTP DTO,
  endpoint, error-handler or event-payload changes. New unit tests prove all
  actor/owner/schedule rejections avoid persistence. New H2 HTTP integration
  tests prove identity/default/response compatibility, owner lookup outside
  the transaction, outbox rollback and commit/rollback cache behavior. Focused
  proof passes 78 tests; full Restaurant `clean verify` passes 318 tests,
  including 237 host tests, packaging and both 85/85 core gates. Module-boundary
  self-test and 242-handler HTTP inventory pass. The boundary verifier change
  only permits empty-body records in application-api, with negative fixtures
  for handwritten behavior and framework imports; dependency rules and coverage
  thresholds are unchanged. PostgreSQL concurrency/atomicity and real Redis/Kafka
  runtime evidence remain deferred; H2 results do not claim those guarantees.

- 2026-09-26: added use-case characterization for Restaurant/Menu CRUD and
  ownership, archived/history reads, pagination envelopes, public projection
  filters, Menu parent immutability, ADMIN access, incomplete operating-hours
  validation and legacy identity rows. The matrix above records what each test
  proves. A clean run also replaced an invalid IDE-generated
  `IdentityPrincipalClient` class in `target/`; this was a stale build artifact,
  not a source or contract failure.
- The same test pass found and fixed two contract mismatches: restaurant
  response `open` now uses the restaurant IANA timezone and shared domain
  schedule rules (including overnight windows); create/update reject a resulting
  one-sided operating-hours pair before persistence, and incomplete legacy
  schedules read as closed. Partial update of one hour remains allowed when the
  merged stored pair is valid. `UpdateMenuItemRequest.restaurantId` remains
  ignored so a menu item cannot be moved across Restaurant ownership.
- Final `mvn -B -pl :restaurant-service -am clean verify` passes with 226 tests,
  no failures/errors/skips, successful packaging, and independent 85/85
  restaurant-domain and restaurant-application JaCoCo checks. The module
  boundary verifier passes and HTTP inventory reports 242 handlers. These are
  unit, MockMvc and H2 proofs; PostgreSQL concurrency/atomicity and CRUD
  application-module extraction remain open.

- 2026-09-23: moved `CatalogLifecycleAudit` and its Spring Data repository
  into the infrastructure module. A first clean build exposed and then fixed
  the module-local Lombok dependency. Final `clean verify` passes. Lifecycle
  orchestration and DTO mapping remain in the host pending application ports.

- 2026-09-23: moved Restaurant/Menu JPA entities and repositories into
  `restaurant-infrastructure`, retaining Java packages for existing scanning
  and callers. Queries, mappings, locks and schema are unchanged. This is a
  physical persistence extraction, not completion of application ports: host
  services still call these repositories, and DTO mappers remain in the host.
  `mvn -B -pl :restaurant-service -am clean verify` passes, including 180 host
  tests and independent domain/application coverage gates. Domain coverage is
  98/109 lines and 74/74 branches; application is 26/26 lines and 18/18 branches
  for the currently extracted owner-assignment use case only. PostgreSQL race
  proof and remaining use-case extraction stay pending.

- Worktree was created from commit `52be51c`; the original checkout remains
  untouched. Build foundation was committed separately as `04dd84c`.
- The first baseline exposed stale identity-overload tests in Notification,
  Shipper, Settlement and Flashsale. They were updated to assert both trusted
  `principalId` and legacy business IDs; production behavior and public wire
  contracts were not changed. Match integration tests were also stale because
  they counted all outbox rows after decision-trace observability was added;
  they now assert exactly one business-result event while retaining the trace.
- The completed segmented reactor run reports 1,403 tests, 0 failures, 0 errors
  and 6 skips. PostgreSQL/Kafka/Redis integration tests were retained. Initial
  failures and repair runs are recorded in `/tmp/backend-platform-phase0-*.log`.
  A subsequent `mvn -B -DskipTests verify` completed the full reactor and proved
  packaging plus JaCoCo lifecycle wiring; log
  `/tmp/backend-platform-phase0-skiptests-verify.log`.
- `delivery-platform-bom` and the opt-in `delivery-build-parent` are registered;
  `identity-contracts` is the canary consumer. The foundation verifier compares
  effective dependencies/plugin management, verifies JaCoCo prepare/report/check,
  includes unexecuted classes, checks non-repackaged library JARs and proves a
  negative fixture below 85% fails. Final log:
  `/tmp/backend-build-foundation-phase0-final.log`.
- Current `identity-contracts` measurement is 43.48% line and 36.36% branch.
  This is a reporting canary, not an 85% claim. No extracted domain/application
  module exists yet. Each future domain/application module must independently
  pass 85% line and 85% branch coverage.
- `scripts/verify-module-boundaries.py` rejects framework dependencies/imports,
  invalid dependency direction, cross-service internal imports and behavior in
  future application-api interfaces. Its self-test passes; log
  `/tmp/backend-module-boundaries-phase0-final.log`.
- Docker artifact freshness now hashes all reactor POM and `src/**` inputs and
  rejects a manifest after shared-module source changes. This intentionally uses
  broad invalidation for correctness. Dependency-closure optimization is
  deferred until build-time measurements justify it. Final log:
  `/tmp/backend-docker-freshness-phase0-final.log`.
- Legacy build policy and all 17 isolated application contexts pass without
  changing public response shapes or runtime defaults. Final logs:
  `/tmp/backend-build-baseline-phase0-final.log` and
  `/tmp/backend-test-context-phase0-final.log`.
- Slice 1 added the framework-independent `restaurant-domain` module without
  wiring it into runtime behavior. A RED compile proved the lifecycle,
  availability and operating-hours APIs did not exist before implementation.
  The final clean verify ran 18 tests with no failures/errors/skips and measured
  100% line (90/90) and branch (72/72) coverage. Module-boundary self-test and
  repository verification passed. Independent re-review found no remaining
  Critical, Important or Minor findings. Parent/child archive independence is
  intentionally proved in Slice 3 where persistence orchestration exists.
- ADR `0002-problem-first-modular-business-architecture.md` makes the problem
  contract and problem-to-test mapping a binding entry gate for every business
  extraction. Technology/module choices follow the approved business model.
- Slice 2's producer boundary now exposes a typed, internal Auth principal
  lookup with fail-closed `Internal-Token` authorization and stable 400/403/404
  semantics. The Auth reactor passes 102 tests and the generated HTTP catalog
  remains aligned at 240 handlers. Generated source IDs stay canonical as
  `backend_delivery/...`, while the generator accepts explicit workspace and
  backend roots so links resolve from an isolated worktree.
- Slice 3's catalog boundary now owns Restaurant/Menu lifecycle authorization,
  transitions, optimistic version checks, immutable audit and transactional
  Search outbox writes. DELETE remains a compatibility alias for archive;
  Redis mutation is registered after commit. The Restaurant reactor passes 175
  tests with no failures/errors, HTTP inventory maps 242 handlers, and module
  boundary plus diff gates pass. PostgreSQL-specific version-conflict and
  rollback proof remain follow-up adapter tests in this slice's hardening list.
- Slice 4 is in progress: the internal order-validation path now reads
  Restaurant/Menu from PostgreSQL under `REPEATABLE_READ`; client price/name and
  Redis are excluded from the decision. Focused proof covers canonical price,
  foreign/missing menu rows, paused/archived restaurants and invalid quantity;
  the Restaurant reactor currently passes 177 tests. Redis remains available
  for catalogue read-side only until PostgreSQL-specific concurrency and
  end-to-end Order rollout proof are complete.
- Slice 5 is complete for the wire boundary: `search-contracts` is consumed by
  Restaurant's outbox producer and Search's Kafka consumer/projection writer.
  JSON fields, `entity-sync` topic, type-header handling and projection
  deduplication/version semantics remain unchanged. A deprecated Search-local
  facade remains only for source-compatible test/integration callers and is not
  used by runtime production code; its removal is a compatibility-cleanup task.
- Restaurant infrastructure extraction has started: the typed Auth directory
  adapter now lives in `restaurant-infrastructure` and the service host only
  wires it. JPA/Redis/outbox adapters remain in the host until each move has a
  focused regression and runtime proof; the Restaurant pilot is not complete
  until those moves are finished.
- The infrastructure hardening increment then moved the remaining Restaurant
  persistence entities/repositories, Redis catalogue adapters, Search/outbox
  relay adapters and the Java Flyway core migration into
  `restaurant-infrastructure`. The module declares its own JPA, Redis, Kafka,
  Jackson and Flyway dependencies. SQL migration resources and lifecycle
  orchestration/DTO mapping still remain in the executable host, so this is a
  completed physical adapter move but not yet a completed application-boundary
  extraction. `mvn -B -pl :restaurant-service -am clean verify` passes with 180
  Restaurant tests; module-boundary verification and the 242-handler HTTP
  inventory pass.
- The lifecycle decision increment now exposes
  `CatalogLifecycleDecisionUseCase` from `restaurant-application-api`, with a
  framework-free implementation and tests in `restaurant-application`.
  Restaurant's runtime lifecycle service delegates transition and restore
  authorization to that use case while retaining the existing host transaction,
  persistence, mapper and side-effect behavior. Application verification passes
  with 6 tests and the 85/85 JaCoCo gate. Full lifecycle orchestration through
  persistence ports, DTO-free application results, PostgreSQL conflict/rollback
  proof and remaining catalog use cases are still TODO.
- The ownership decision increment moves Restaurant management access into the
  framework-free application layer. It covers ADMIN direct access, principal
  owner access, the explicitly temporary legacy creator fallback, enforcement
  cut-over, missing principals and unsupported roles. The host policy is now a
  thin Spring/configuration adapter that maps domain denial to the existing 403
  response and retains the legacy-fallback metric. Core verification passes its
  85/85 gates; host characterization tests cover Restaurant, Menu and lifecycle
  callers. PostgreSQL transaction and conflict proof remains a separate adapter
  task rather than an application-coverage claim.
- The identity infrastructure increment is now complete: Auth client
  properties, timeout/redirect construction, lazy typed client creation and
  principal-directory wiring are owned by `restaurant-infrastructure`.
  `restaurant-service` imports that configuration and retains only use-case and
  domain-policy wiring; the public HTTP and fail-closed ownership behavior is
  unchanged.
- Slice 2 then added the policy-free `platform-http-blocking` transport and the
  first service-specific `identity-client`. The platform requires explicit
  headers and a caller-configured `RestTemplate`; it adds no credential,
  timeout, retry or fallback policy. The SDK owns only Auth URI/status mapping:
  404 becomes an absent principal and other failures are classified but remain
  visible. Review hardened the transport against credential forwarding by
  disabling redirects, and hardened the SDK against non-2xx, decode errors,
  empty bodies and malformed/mismatched principal payloads. Restaurant's
  `ACTIVE`/`SHOP_OWNER` acceptance rule and fail-closed creation behavior remain
  in Slice 3, where the application boundary can own them rather than the SDK.
- Slice 3 started with the framework-independent owner-assignment decision.
  `SHOP_OWNER` may assign only its own principal without an Auth round trip;
  `ADMIN` must provide an owner that the directory reports as existing, active,
  and a shop owner. Unsupported actors, missing/invalid targets and directory
  failures fail closed. The application depends only on a typed port and domain
  facts; mapping from `identity-client` and HTTP error semantics remain adapter
  work before this rule is wired into Restaurant creation.
- The first Slice 3 vertical path is now wired: `POST /api/restaurants` resolves
  ownership before entering `RestaurantServiceImpl`'s transaction. SHOP_OWNER
  creation remains network-free; ADMIN supplies additive `ownerPrincipalId` and
  uses the lazy typed Auth adapter. Invalid actor assignment maps to 403,
  invalid/non-active/non-owner targets map to 400 without exposing which Auth
  predicate failed, and transport/protocol failures remain fail-closed. The
  full Restaurant reactor passes 159 tests, including an HTTP-to-persistence
  integration test for ADMIN assignment and proof that SHOP_OWNER creation does
  not call Auth. The remaining lifecycle work is tracked by the increment below.
- Slice 3's archive regression increment is complete. Flyway V10 adds and
  backfills Restaurant lifecycle/version/timezone and Menu version. Existing
  DELETE routes now archive idempotently, never physically delete catalog rows,
  and Restaurant archive preserves Menu rows and selling state. Public
  Restaurant projections hide archived rows; public Menu projections require
  both `AVAILABLE` Menu and a non-archived parent, while detail and management
  reads preserve history. Responses expose additive lifecycle/version/timezone
  fields. Redis mutation is registered only after PostgreSQL commit; rollback
  performs no cache mutation. Search calls remain inside the transaction
  intentionally because they only write the existing transactional outbox.
  The full Restaurant reactor passes 170 tests. The next lifecycle increment
  adds PATCH state transitions, additive expectedVersion handling with
  `409 STALE_VERSION`, the missing-version metric, V11 audit rows, and shared
  lifecycle cache/outbox behavior; existing DELETE aliases use the same
  boundary. PostgreSQL concurrency/rollback proof remains pending.

## Result

Phase 0 is complete. The build can now enforce module boundaries and future
85/85 core coverage without forcing all existing services into the new parent.
The Restaurant/Menu problem contract is approved above. Slice 1 is complete and
adds pure, tested rules without changing runtime behavior. Slice 2 is complete:
the typed Auth producer, policy-free blocking transport and typed identity SDK
are independently tested. Slice 3 has extracted owner assignment, ownership
and lifecycle decisions plus Restaurant infrastructure adapters. Create,
update, read and transactional orchestration for the rest of Restaurant/Menu
still remain in the service host, so phase 1 remains active.

### Task 1 — Extract Restaurant creation use case

Move the `POST /api/restaurants` creation workflow through the framework-free
Restaurant application module while preserving the existing host endpoint,
response, validation errors, owner assignment, persistence defaults, and
read-side effects.

- Add `CreateRestaurantCommand`, `CreateRestaurantResult`, and the
  `CreateRestaurantUseCase` plus `RestaurantCreationPort` contracts in
  `restaurant-application-api`. Keep these contracts free of Spring, JPA,
  `Restaurant` entities, and service HTTP DTOs.
- Implement the use case in `restaurant-application`. Reuse
  `RestaurantOwnerAssignmentUseCase` for ADMIN/SHOP_OWNER owner resolution;
  validate the final operating-hours pair through the domain schedule; call the
  persistence port only after actor/owner/schedule decisions succeed.
- Implement the persistence port in `restaurant-infrastructure`. The adapter
  owns the database transaction, persists creator/owner and existing defaults,
  writes Search outbox in that transaction, and schedules cache work after
  commit. It returns a framework-free result sufficient for the host to retain
  the current response shape without an extra database read.
- Keep `RestaurantController` and `RestaurantService` as HTTP/service-host
  adapters. They map `AuthenticatedActor` and request DTOs to the command and
  map the result to `RestaurantResponse`; they do not resolve owner policy or
  persist an entity themselves.
- Unit-test SHOP_OWNER self-ownership, ADMIN active SHOP_OWNER assignment,
  unsupported/missing actor, missing/invalid/inactive/non-owner target,
  directory failure, incomplete hours, and successful result mapping. Rejected
  cases must not call the persistence port. Keep H2 integration proof for
  controller-to-database compatibility and Search outbox/cache behavior.
- No endpoint, payload, status/error mapping, schema, lifecycle, owner identity,
  transaction, cache, Search event, or default behavior changes are in scope.
