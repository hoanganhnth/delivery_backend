# Backend Phases 1–6 Integration and Quality Gates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the committed Phase 1–6 backend refactors into one clean branch, complete the missing runtime boundaries, and make every domain/application module prove at least 85% line and branch coverage without touching Phase 7.

**Architecture:** Start from `main` in `/Users/a/Documents/private/delivery/.worktrees/backend-phase1-6-integration`. Integrate Phase 1–5 source in dependency order while retaining the already-present Phase 6 source, then move host behavior behind framework-free inbound ports and keep Spring/JPA/Kafka code in infrastructure or service adapters. Shared delivery-status events are defined once in `delivery-contracts` and consumed by both Saga and Notification.

**Tech Stack:** Java 17, Spring Boot 3.5.15, Maven reactor, JUnit 5, Mockito, AssertJ, JaCoCo 0.8.13, Kafka contracts, Python architecture verifier.

---

## File map

Integration and build files:

- `pom.xml` — reactor module list; add every accepted Phase 4/5 module in dependency order.
- `platform/delivery-build-parent/pom.xml` — shared JaCoCo execution; retain `prepare-agent`, `report`, and `check`.
- `scripts/verify-module-boundaries.py` — architecture proof; run it unchanged unless a narrowly scoped false-positive test demonstrates a verifier defect.
- `docs/reviews/backend-phases-1-6-review-matrix.md` — evidence matrix for old orchestrator tasks; contains no Phase 7 rows.

Core module POMs and code:

- `modules/user/{user-domain,user-application-api,user-application,user-infrastructure}`.
- `modules/auth/{auth-domain,auth-application-api,auth-application,auth-infrastructure}`.
- `modules/shipper/{shipper-domain,shipper-application-api,shipper-application,shipper-infrastructure}`.
- `modules/web-bff/{web-bff-domain,web-bff-application-api,web-bff-application,web-bff-infrastructure}`.
- `modules/delivery/{delivery-domain,delivery-application-api,delivery-application,delivery-infrastructure}`.
- `modules/match/{match-domain,match-application-api,match-application,match-infrastructure}`.
- `modules/restaurant/{restaurant-domain,restaurant-application-api,restaurant-application,restaurant-infrastructure}`.

Host and event compatibility files:

- `user-service/src/main/java/**`, `auth-service/src/main/java/**`, `shipper-service/src/main/java/**`, and `web-bff-service/src/main/java/**` — controllers and Spring configuration only adapt inbound requests to application ports.
- `delivery-service/src/main/java/com/delivery/delivery_service/controller/DeliveryController.java` — remove the seven legacy service fields and call one inbound delivery facade.
- `delivery-service/src/main/java/com/delivery/delivery_service/service/DeliveryEventPublisher.java` — serialize the shared delivery-status contract without dropping identity fields.
- `delivery-contracts/src/main/java/com/delivery/delivery/contracts/DeliveryStatusUpdatedEvent.java` — canonical status event record.
- `notification-service/src/main/java/com/delivery/notification_service/listener/DeliveryEventListener.java` — consume the shared event type, validate identity, acknowledge only after successful notification.

---

### Task 1: Snapshot the integration branch and import committed Phase 1–5 source

**Files:**
- Modify: `pom.xml`, module trees imported from `refactor/platform-modules`, `refactor/phase4-user-auth`, and `refactor/phase5-bff-shipper`.
- Create: one commit containing the accepted uncommitted Phase 4/5 files listed below.
- Do not modify: `/Users/a/Documents/private/delivery/.worktrees/backend-phase7`, its branch, processes, or ledger rows.

- [ ] **Step 1: Verify the isolated branch and Phase 7 exclusion**

Run:

```bash
git status --short --branch
git rev-parse HEAD
git diff --name-only main...HEAD
git -C /Users/a/Documents/private/delivery/.worktrees/backend-phase7 status --short --branch
```

Expected: branch is `refactor/backend-phase1-6-integration`, HEAD is `02c79d1` or the design-spec commit, and no path under `backend-phase7` is staged or modified.

- [ ] **Step 2: Merge the committed Phase 1–3 branch**

Run:

```bash
git merge --no-ff refactor/platform-modules -m "merge: integrate backend phases 1-3"
mvn -B -DskipTests validate
```

Expected: merge succeeds; the platform, restaurant, routing-client, event-contract, Kafka-starter, and event-test-support modules validate.

- [ ] **Step 3: Merge the committed Phase 4 branch**

Run:

```bash
git merge --no-ff refactor/phase4-user-auth -m "merge: integrate backend phase 4 user auth"
```

Resolve conflicts by keeping the current `main` public route/configuration and the Phase 4 application ports. Do not delete an existing controller route to resolve a conflict.

- [ ] **Step 4: Merge the committed Phase 5 branch**

Run:

```bash
git merge --no-ff refactor/phase5-bff-shipper -m "merge: integrate backend phase 5 bff shipper"
```

Keep `tmp_test_output.txt` and `update_domain.patch` out of the integration branch; they are worktree artifacts, not source.

- [ ] **Step 5: Import accepted uncommitted Phase 4 files as a reviewable commit**

Copy these exact files from `/Users/a/Documents/private/delivery/.worktrees/backend-phase4-user-auth` into the integration worktree, then stage them:

```text
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/CreateUserAddressCommand.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/RegisterUserCommand.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/UpdateUserAddressCommand.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/UserAddressPort.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/UserAddressResult.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/UserAddressUseCase.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/UserProfileReadPort.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/UserProfileReadUseCase.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/UserRegistrationUseCase.java
modules/user/user-application-api/src/main/java/com/delivery/user/application/api/UserStatisticsResult.java
modules/user/user-application/src/main/java/com/delivery/user/application/DefaultUserAddressUseCase.java
modules/user/user-application/src/main/java/com/delivery/user/application/DefaultUserProfileReadUseCase.java
modules/user/user-application/src/test/java/com/delivery/user/application/DefaultUserAddressUseCaseTest.java
modules/user/user-application/src/test/java/com/delivery/user/application/DefaultUserProfileReadUseCaseTest.java
modules/user/user-infrastructure/src/main/java/com/delivery/user_service/service/JpaUserAddressAdapter.java
modules/user/user-infrastructure/src/main/java/com/delivery/user_service/service/JpaUserProfileReadAdapter.java
```

Run:

```bash
git add modules/user
git commit -m "feat(user): import address and profile application ports"
```

- [ ] **Step 6: Import accepted uncommitted Phase 5 files and remove artifacts**

Apply the tracked diff from `/Users/a/Documents/private/delivery/.worktrees/backend-phase5-bff-shipper` and copy only these untracked source/config files:

```text
modules/shipper/shipper-infrastructure/src/main/java/com/delivery/shipper/infrastructure/adapter/JpaIdentityReceiptAdapter.java
shipper-service/src/main/java/com/delivery/shipper_service/config/JpaConfig.java
shipper-service/src/main/java/com/delivery/shipper_service/config/ShipperUseCasesConfig.java
```

The tracked diff includes the Shipper adapters/controllers and Web BFF API/infrastructure changes shown by `git status --short` on the source worktree. Do not copy `tmp_test_output.txt` or `update_domain.patch`.

Run:

```bash
git add modules/shipper modules/web-bff shipper-service
git commit -m "feat: import phase 5 host wiring hardening"
```

- [ ] **Step 7: Verify merge/source hygiene**

Run:

```bash
git status --short
git diff --check HEAD~3..HEAD
git log --oneline --decorate -8
```

Expected: only intentional source, test, and build files are present; no Phase 7 path appears; no whitespace errors are reported.

---

### Task 2: Enforce the architecture and coverage policy before filling gaps

**Files:**
- Modify: every `*-domain/pom.xml` and `*-application/pom.xml` under `modules/restaurant`, `modules/user`, `modules/auth`, `modules/shipper`, `modules/web-bff`, `modules/delivery`, and `modules/match`.
- Modify: `modules/shipper/shipper-application-api/src/main/java/com/delivery/shipper/application/api/{ShipperCommands,ShipperResults,ShipperUseCases,ShipperPorts}.java`.
- Modify: `modules/web-bff/web-bff-application-api/src/main/java/com/delivery/web_bff/application/api/{Ports,UseCases}.java`.

- [ ] **Step 1: Add explicit 85% JaCoCo properties to all domain/application POMs**

Every domain/application POM must contain this exact properties block directly under `<artifactId>` or the module metadata:

```xml
<properties>
    <delivery.coverage.line.minimum>0.85</delivery.coverage.line.minimum>
    <delivery.coverage.branch.minimum>0.85</delivery.coverage.branch.minimum>
</properties>
```

Do not add these properties to `*-application-api` modules that contain only interfaces/records.

- [ ] **Step 2: Make application-api declarations behavior-free**

Replace nested classes/records with top-level interfaces and empty-body records. For example, `ShipperCommands.java` must expose declarations of this form and no executable method body:

```java
package com.delivery.shipper.application.api;

public interface ShipperCommands {
    void register(RegisterShipperCommand command);
    void update(UpdateShipperCommand command);
}
```

Records such as `RegisterShipperCommand` contain only components and `{}`. Move normalization, validation, and mapping into `shipper-application` or `shipper-domain`. Apply the same rule to `Ports.java` and `UseCases.java` in Web BFF: interfaces and data records remain in API; implementations move to `web-bff-application`.

- [ ] **Step 3: Run the boundary verifier and capture the baseline failures**

Run:

```bash
python3 scripts/verify-module-boundaries.py
```

Expected at this checkpoint: failures list only modules/files still being repaired in later tasks; no forbidden dependency is silently accepted.

- [ ] **Step 4: Commit the gate and API cleanup**

```bash
git add modules
git commit -m "build: enforce core coverage and pure application contracts"
```

---

### Task 3: Complete User and Auth application behavior and host wiring

**Files:**
- Modify: `modules/user/user-domain/pom.xml`, `modules/user/user-application/pom.xml`, `modules/user/user-application/src/main/java/com/delivery/user/application/*.java`.
- Modify: `modules/auth/auth-domain/pom.xml`, `modules/auth/auth-application/pom.xml`, `modules/auth/auth-application/src/main/java/com/delivery/auth/application/*.java`.
- Test: `modules/user/user-application/src/test/java/com/delivery/user/application/*.java` and `modules/auth/auth-application/src/test/java/com/delivery/auth/application/*.java`.
- Modify: `user-service/src/main/java/**` and `auth-service/src/main/java/**` only where constructors/configuration adapt to the ports.

- [ ] **Step 1: Add failing User tests for the imported address/profile use cases**

Add tests for missing user, unauthorized principal, successful address creation/update, and profile read. The unauthorized case must assert no adapter interaction:

```java
@Test
void rejectsAddressMutationForAnotherPrincipal() {
    var port = mock(UserAddressPort.class);
    var useCase = new DefaultUserAddressUseCase(port);

    assertThatThrownBy(() -> useCase.update(new UpdateUserAddressCommand(7L, 99L, "Hanoi")))
            .isInstanceOf(UserAccessDeniedException.class);
    verifyNoInteractions(port);
}
```

- [ ] **Step 2: Run the focused User tests and verify RED**

```bash
mvn -B -pl modules/user/user-application -am -Dtest='*UserAddressUseCaseTest,*UserProfileReadUseCaseTest' test
```

Expected: the new authorization or missing-branch assertions fail before implementation changes.

- [ ] **Step 3: Implement the smallest User behavior**

Keep the use cases framework-free. Delegate persistence to `UserAddressPort` and `UserProfileReadPort`; use `UserRegistrationUseCase` for registration and preserve existing DTO/response values in the host adapters.

- [ ] **Step 4: Add failing Auth application tests for invalid credentials and successful token issuance**

Cover invalid email/password, disabled account, successful login, refresh replay, and logout idempotency using mocked ports. Assert that invalid input fails before any persistence or token port call.

- [ ] **Step 5: Implement Auth behavior and wire host beans**

Update `auth-service` configuration so Spring creates the application use cases with infrastructure adapters; controllers depend on inbound interfaces, not repositories or entities.

- [ ] **Step 6: Run User/Auth module gates**

```bash
mvn -B -pl modules/user/user-domain,modules/user/user-application,modules/auth/auth-domain,modules/auth/auth-application -am clean verify
```

Expected: all four modules pass compile, tests, JaCoCo line and branch checks at `>= 0.85`.

- [ ] **Step 7: Run selected host tests with Mockito attachment enabled**

```bash
MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true' mvn -B -pl :user-service,:auth-service -am test -Dtest='*ControllerTest,*AuthorizationTest,*ApplicationTests'
```

Expected: production assertions pass; if the environment blocks socket binding, record the exact test and `java.net.SocketException` in the review matrix instead of changing behavior.

- [ ] **Step 8: Commit User/Auth completion**

```bash
git add modules/user modules/auth user-service auth-service
git commit -m "feat: complete user and auth application boundaries"
```

---

### Task 4: Finish Shipper and Web BFF application layers

**Files:**
- Create: `modules/web-bff/web-bff-application/pom.xml`.
- Create: `modules/web-bff/web-bff-application/src/main/java/com/delivery/web_bff/application/{LoginApplicationService,CurrentSessionApplicationService,LogoutApplicationService,RefreshApplicationService,AccessTokenApplicationService,ApiProxyPolicyApplicationService,ProxyForwardingApplicationService}.java`.
- Test: `modules/web-bff/web-bff-application/src/test/java/com/delivery/web_bff/application/*Test.java`.
- Modify: `modules/shipper/shipper-domain/pom.xml`, `modules/shipper/shipper-application/pom.xml`, `modules/shipper/shipper-application/src/main/java/**` and their tests.
- Modify: `web-bff-service/src/main/java/**` and `shipper-service/src/main/java/**` configuration/controllers.

- [ ] **Step 1: Add the Web BFF application module to the reactor**

Insert this module before `web-bff-application-api` consumers in `pom.xml`:

```xml
<module>modules/web-bff/web-bff-application</module>
```

Its POM must inherit `delivery-build-parent`, depend only on `web-bff-application-api`, `web-bff-domain`, and test libraries, and contain the same 0.85/0.85 properties block.

- [ ] **Step 2: Move existing framework-free service logic into the application module**

Move the behavior currently in `web-bff-service` classes `LoginApplicationService`, `CurrentSessionApplicationService`, `LogoutApplicationService`, `RefreshApplicationService`, `AccessTokenApplicationService`, `ApiProxyPolicyApplicationService`, and `ProxyForwardingApplicationService` into the new module without changing their public `UseCases.*` interfaces. Replace direct `WebSessionRepository`, `RestClient`, `HttpMethod`, and Spring annotations with `Ports.*` dependencies.

- [ ] **Step 3: Add failing BFF tests for security branches**

Cover missing session, expired session, CSRF mismatch on mutation, refresh lease contention, denied route, allowed route, and upstream error mapping. For a denied mutation, assert the forwarding port is never called.

- [ ] **Step 4: Implement the BFF application services and wire infrastructure adapters**

`web-bff-infrastructure` supplies `Ports.Authentication`, `Ports.Sessions`, `Ports.TokenProtection`, `Ports.Clock`, and `Ports.ProxyForwarding`; `web-bff-service` supplies controllers and bean configuration only.

- [ ] **Step 5: Add failing Shipper tests for provider failure and idempotent replay**

Test rating update authorization, missing profile, identity receipt replay, tracking provider failure, and page bounds. Keep persistence and HTTP calls behind `ShipperPorts`.

- [ ] **Step 6: Implement Shipper branches and remove legacy host ownership**

Use `JpaIdentityReceiptAdapter`, `JpaShipperProfileAdapter`, `JpaShipperRatingAdapter`, and `TrackingAvailabilityAdapter`; remove deleted legacy entities/repositories/services from host configuration and keep the existing controller response envelopes.

- [ ] **Step 7: Run Shipper/Web BFF gates and host tests**

```bash
mvn -B -pl modules/shipper/shipper-domain,modules/shipper/shipper-application,modules/web-bff/web-bff-domain,modules/web-bff/web-bff-application -am clean verify
MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true' mvn -B -pl :shipper-service,:web-bff-service -am test -Dtest='*ControllerTest,*AuthorizationTest,*ApplicationTests'
```

Expected: all core modules pass >=85% line and branch; host tests pass or are explicitly classified as environment-blocked.

- [ ] **Step 8: Commit Shipper/Web BFF completion**

```bash
git add modules/shipper modules/web-bff shipper-service web-bff-service pom.xml
git commit -m "feat: complete shipper and web bff application layers"
```

---

### Task 5: Complete Delivery host migration without changing routes

**Files:**
- Create/modify: `modules/delivery/delivery-application-api/src/main/java/com/delivery/delivery/application/api/DeliveryCommandPort.java` and `DeliveryQueryPort.java`.
- Create/modify: `modules/delivery/delivery-application/src/main/java/com/delivery/delivery/application/DefaultDeliveryCommandService.java` and `DefaultDeliveryQueryService.java`.
- Modify: `modules/delivery/delivery-infrastructure/src/main/java/com/delivery/delivery_service/**` adapters/configuration.
- Modify: `delivery-service/src/main/java/com/delivery/delivery_service/controller/DeliveryController.java`.
- Test: `delivery-service/src/test/java/com/delivery/delivery_service/controller/**` and `modules/delivery/delivery-application/src/test/java/**`.

- [ ] **Step 1: Freeze the existing HTTP inventory**

Record these exact mappings in a compatibility test before rewiring: `POST /deliveries/batch/accept`, `POST /deliveries/batch/reject`, `POST /deliveries/accept`, `POST /deliveries/cancel-assignment`, `GET /deliveries/offers/current`, `GET /deliveries/offers/current-batch`, `GET /deliveries/batches/{batchId}`, the four proof routes, the four exception routes, `GET /deliveries/{id}`, `PUT /deliveries/{id}/status`, `GET /deliveries/shipper/{shipperId}`, `GET /deliveries/shipper/{shipperId}/active`, and `GET /deliveries/order/{orderId}`.

- [ ] **Step 2: Add failing controller tests for the new inbound facade**

Mock `DeliveryCommandPort` and `DeliveryQueryPort`. Assert that each route maps actor principal ID, legacy user ID, role, simulation context, path ID, and request body to the correct command; assert null actor is rejected before port interaction.

- [ ] **Step 3: Define behavior-free inbound commands/results**

`DeliveryCommandPort` must expose commands for accept delivery, cancel assignment, accept/reject batch, update status, create/confirm/read proof, report/retry/confirm return exception, and queries for current offer, current batch, batch snapshot, delivery by ID, delivery by shipper, active deliveries by shipper, and delivery by order. Commands/results are records only; no Spring or legacy DTO imports are allowed in `delivery-application-api`.

- [ ] **Step 4: Implement application services by delegating to outbound ports**

Move orchestration and authorization decisions into `delivery-application`. Infrastructure adapters translate the existing `DeliveryService`, batch services, proof service, exception service, repositories, and `ShipperIdentityResolver` into the new outbound ports. Preserve transaction boundaries and response values.

- [ ] **Step 5: Rewire `DeliveryController`**

Replace the fields at lines 39–46 (`DeliveryService`, `DeliveryBatchAcceptanceService`, `DeliveryBatchLifecycleService`, `ShipperIdentityResolver`, `DeliveryBatchSnapshotService`, `DeliveryProofOfDeliveryService`, `DeliveryExceptionService`) with inbound port fields. Keep the same mappings, response messages, validation annotations, and `requireActor` semantics.

- [ ] **Step 6: Add tests for every newly wired branch**

Cover authorization failures, null optional batch/proof/exception adapters, invalid IDs/status, idempotent status replay, provider failure, and successful response envelope mapping. Add a test that reflects over `DeliveryController` and fails if any field type is in `com.delivery.delivery_service.service`.

- [ ] **Step 7: Run the Delivery core and host gates**

```bash
mvn -B -pl modules/delivery/delivery-domain,modules/delivery/delivery-application -am clean verify
MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true' mvn -B -pl :delivery-service -am test -Dtest='*DeliveryControllerTest,*AuthorizationTest,*StatusUpdateIntegrationTest'
```

Expected: delivery-domain and delivery-application each pass line and branch >=85%; all selected controller/compatibility tests pass.

- [ ] **Step 8: Commit Delivery migration**

```bash
git add modules/delivery delivery-service
git commit -m "feat: migrate delivery host to application ports"
```

---

### Task 6: Close Match coverage and preserve the existing public boundary

**Files:**
- Modify: `modules/match/match-domain/pom.xml`, `modules/match/match-application/pom.xml`.
- Test: `modules/match/match-domain/src/test/java/com/delivery/match/domain/**` and `modules/match/match-application/src/test/java/com/delivery/match/application/**`.
- Modify only as needed: `modules/match/match-application/src/main/java/com/delivery/match/application/DefaultDispatchMatchingService.java` and `modules/match/match-infrastructure/**`.

- [ ] **Step 1: Add failing domain tests for candidate and snapshot invariants**

Cover null candidate lists, negative assignment limits, duplicate shipper IDs, invalid coordinates, and immutable result lists. Assert exact exception types/messages used by the current domain API.

- [ ] **Step 2: Add failing application tests for dispatch and provider failures**

Cover empty candidates, max assignments of zero, deterministic ordering, routing-client failure, and idempotent command replay. Mock only `FindNearbyShippersPort`/`DispatchMatchingPort` boundaries.

- [ ] **Step 3: Implement only the smallest domain/application fixes**

Do not alter Match HTTP routes, Kafka topic names, persistence schema, or retry policy. Keep `DispatchMatchingPort` and `FindNearbyShippersPort` as framework-free interfaces.

- [ ] **Step 4: Run Match gates**

```bash
mvn -B -pl modules/match/match-domain,modules/match/match-application -am clean verify
```

Expected: both modules pass line and branch >=85% with no production framework dependency.

- [ ] **Step 5: Commit Match completion**

```bash
git add modules/match
git commit -m "test: close match domain and application coverage"
```

---

### Task 7: Migrate delivery-status notification to the shared contract

**Files:**
- Create: `delivery-contracts/src/main/java/com/delivery/delivery/contracts/DeliveryStatusUpdatedEvent.java`.
- Modify: `delivery-service/src/main/java/com/delivery/delivery_service/service/DeliveryEventPublisher.java`.
- Modify: `notification-service/src/main/java/com/delivery/notification_service/listener/DeliveryEventListener.java`.
- Delete from production use: `notification-service/src/main/java/com/delivery/notification_service/dto/event/DeliveryEvent.java` after all references are removed.
- Test: `delivery-service/src/test/java/com/delivery/delivery_service/service/DeliveryEventPublisherContractTest.java`, `notification-service/src/test/java/com/delivery/notification_service/listener/DeliveryEventListenerContractTest.java`, and `notification-service/src/test/java/com/delivery/notification_service/listener/DeliveryEventListenerReplayTest.java`.

- [ ] **Step 1: Add the canonical event record and a failing golden serialization test**

The record must contain `UUID eventId`, `String eventType`, `LocalDateTime eventTimestamp`, `Long deliveryId`, `Long orderId`, `Long userId`, `Long userPrincipalId`, `Long shipperId`, `String status`, `String previousStatus`, `String shipperName`, and `SimulationContext simulationContext`. Serialize a fixed instance and assert all identity fields and status names are present.

- [ ] **Step 2: Change the producer to publish the shared record**

`DeliveryEventPublisher.DeliveryStatusUpdateEvent` becomes an adapter or is removed; all status-updated outbox payloads use `DeliveryStatusUpdatedEvent`, preserve `userId` and `userPrincipalId`, and validate non-null event/delivery/order identity before saving.

- [ ] **Step 3: Change Notification listener deserialization to the shared record**

Use the platform `ObjectMapper` configuration already provided by the service. Validate positive `deliveryId`, `orderId`, `userId`, non-null `eventId`, and canonical statuses. Pass both `userPrincipalId` and legacy `userId` to `NotificationService`; acknowledge only after the call returns.

- [ ] **Step 4: Add replay and DLT behavior tests**

Replay the same JSON twice and assert the same stable event ID reaches notification deduplication. Invalid identity/status must throw `IllegalArgumentException` without acknowledgement. A transient notification failure must throw a retryable exception; `@RetryableTopic` must retain `delivery.status-updated-retry-notification` and `.notification.DLT` suffixes.

- [ ] **Step 5: Run contract and consumer tests**

```bash
mvn -B -pl :delivery-service,:notification-service -am test -Dtest='*DeliveryEventPublisherContractTest,*DeliveryEventListenerContractTest,*DeliveryEventListenerReplayTest,*KafkaListenerTopicConfigurationTest'
```

Expected: producer JSON, consumer mapping, acknowledgement, replay, retry, and DLT assertions all pass.

- [ ] **Step 6: Commit the shared-contract migration**

```bash
git add delivery-contracts delivery-service notification-service
git commit -m "feat: standardize delivery status notification contract"
```

---

### Task 8: Execute the full verification suite and reconcile old-phase evidence

**Files:**
- Create: `docs/reviews/backend-phases-1-6-review-matrix.md`.
- Modify: no Phase 7 ledger or source files.

- [ ] **Step 1: Run the architecture verifier**

```bash
python3 scripts/verify-module-boundaries.py
```

Expected: `Module boundary verification passed.`

- [ ] **Step 2: Run all core module gates**

```bash
mvn -B -pl \
modules/restaurant/restaurant-domain,modules/restaurant/restaurant-application,\
modules/user/user-domain,modules/user/user-application,\
modules/auth/auth-domain,modules/auth/auth-application,\
modules/shipper/shipper-domain,modules/shipper/shipper-application,\
modules/web-bff/web-bff-domain,modules/web-bff/web-bff-application,\
modules/delivery/delivery-domain,modules/delivery/delivery-application,\
modules/match/match-domain,modules/match/match-application -am clean verify
```

Expected: zero test failures and zero JaCoCo check failures; record each module's exact LINE and BRANCH ratio from `target/site/jacoco/jacoco.xml`.

- [ ] **Step 3: Run focused contracts, hosts, and reactor verification**

```bash
MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true' mvn -B -DskipTests compile
MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true' mvn -B -DskipTests=false test
```

Run socket-dependent integration tests only when the environment permits binding. If a test fails with `java.net.SocketException: Operation not permitted`, list that test and the environment cause in the matrix; do not mark it as a production failure or weaken the test.

- [ ] **Step 4: Write the old-phase review matrix**

For every Phase 1–6 task represented in the orchestrator history, record: task identifier, source commit/worktree, focused test command/result, coverage result, architecture-verifier result, host migration status, and remaining concern. Mark a task `ready` only when code is committed and all applicable proof passes. Include no Phase 7 task or ledger mutation.

- [ ] **Step 5: Verify clean integration state and Phase 7 isolation**

```bash
git status --short
git diff --check
git log --oneline --decorate --max-count=20
git -C /Users/a/Documents/private/delivery/.worktrees/backend-phase7 status --short --branch
```

Expected: integration worktree is clean, Phase 7 worktree is unchanged, and the final report distinguishes verified results from environment-blocked tests.

- [ ] **Step 6: Commit the evidence matrix**

```bash
git add docs/reviews/backend-phases-1-6-review-matrix.md
git commit -m "docs: record phase 1-6 integration evidence"
```

