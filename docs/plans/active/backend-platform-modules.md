# Execution Plan: Backend platform and business modules

## Status

Active — baseline and phase 0 implementation. Not a completed backend migration.

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

- [ ] Phase 0: baseline tests, coverage inventory, build parent/BOM, architecture
  gates and transitive Docker artifact freshness proof.
- [ ] Phase 1: Restaurant pilot — ownership, menu reads/writes, Restaurant CRUD,
  then decision/rating/serviceability/inventory. Preserve mapper, lock/flush,
  transaction and outbox behavior. Add only testkit helpers actually needed.
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

- Worktree created from current committed backend; original checkout remains
  untouched. Full baseline completed exit 1 in 4m32s; log
  `/tmp/backend-platform-baseline.log`. Modules through Delivery passed.
- Notification baseline: 68 tests, 7 failures, 0 errors/skips, before any
  production/build edits. Failures in NotificationControllerAuthorizationTest
  (2), DeliveryEventListenerContractTest (3), and
  NotificationListenerAcknowledgmentTest (2). Later modules were not executed.
  Do not skip or weaken these tests. Bounded build-foundation canary work can
  be verified independently; business migration and whole-reactor completion
  remain gated by this pre-existing baseline gap.
- Initial slice will establish build/coverage support before moving business
  behavior. Parent migration must preserve effective dependency versions.
- Build-foundation canary implemented: `delivery-platform-bom` and the opt-in
  `delivery-build-parent` are registered; only `identity-contracts` migrated.
  `scripts/verify-build-foundation.sh` compares pre/post effective dependencies
  and managed plugin versions, validates inherited JaCoCo executions, proves
  unexecuted classes remain in XML, checks a library JAR is not repackaged, and
  confirms an unmigrated service has no JaCoCo plugin. RED before implementation
  was the missing JaCoCo plugin; GREEN log is
  `/tmp/backend-build-foundation.log`. A clean temporary Maven repository also
  passed `mvn -B -pl identity-contracts -am validate`.
- Current `identity-contracts` measurement is 43.48% line and 36.36% branch.
  This is evidence that reporting works, not an 85% claim. The 85% checks remain
  pending for each extracted domain/application module.
- Known build risk: Docker freshness currently hashes only host source/POM and
  root POM; shared module inputs must be covered before declaring phase 0 done.
- `bash scripts/verify-test-context-isolation.sh`: exit 0, all 17 application
  test contexts pass. `bash scripts/verify-build-baseline.sh`: exit 1 before
  build edits, on the canonical BaseResponse grep (including BFF proxy/session,
  Routing and Simulator responses); log `/tmp/backend-platform-build-baseline.log`.
  Do not change public response shapes just to satisfy this legacy gate.

## Result

Pending. No platform, coverage or production completion claim yet.
