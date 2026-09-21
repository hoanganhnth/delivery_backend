# Execution Plan: Backend platform and business modules

## Status

Active — phase 0 complete; phase 1 problem analysis is the next gate. This is
not a completed backend migration.

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
- ADR `0002-problem-first-modular-business-architecture.md` makes the problem
  contract and problem-to-test mapping a binding entry gate for every business
  extraction. Technology/module choices follow the approved business model.

## Result

Phase 0 is complete. The build can now enforce module boundaries and future
85/85 core coverage without forcing all existing services into the new parent.
The next action is analysis, not extraction: produce and review the Restaurant/
Menu problem contract and its unit/boundary/integration test matrix.
