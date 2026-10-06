# Notification slice 3: remaining decisions and application use cases

Scope: `notification/`, `notification-service/`, root `pom.xml` (unchanged).
Filesystem edits only; no git commands, plan edits, neighboring production
changes, schema/API/topic changes, new defaults or new product policies.
Authority: existing implementation/tests, `docs/services/notification_service.md`,
`docs/WORKFLOW.md`, the notification consolidation tranche and SLICE1/SLICE2 reports.
The supplied branch/HEAD was not independently queried because git commands were
excluded by this task.

## Result

- Domain `EventIdentity` owns persisted single-shipper selection, stable event/
  aggregate/customer identity, canonical status vocabulary and name normalization.
  Validation order, messages, null-element failure and ingress/direct-call
  differences are preserved. `NotificationMapping` and `ReplayPayload` continue
  owning event key construction and exact immutable replay comparison, including
  raw serialized data; no replacement identity scheme was added.
- Domain `PushEligibility` owns exact push-request eligibility and the existing
  positive user / nonblank title, body and token validation. Preferences continue
  owning capability/default decisions in `NotificationPreferences`; transactional
  wake-ups do not consult marketing preferences.
- Application `EventNotifications` maps order/status/offer decisions and invokes
  `EventNotificationPort`. `IncomingStatus` and `IncomingOffer` order domain
  validation, shared simulation-context adapter validation and dispatch through
  `StatusEventPort` / `OfferEventPort`. Only offers suppress valid simulations.
  Kafka ACK remains in the listeners after the use case completes.
- Application `DispatchPush` validates before checking provider configuration,
  reads token membership only when configured, iterates tokens, and removes
  UNREGISTERED tokens through `PushPort`. Redis/provider/cleanup failures still
  propagate, stopping subsequent tokens. `CompleteDelivery` consults the domain
  for nullable sendPush; the locked PENDING/SENT transaction remains unchanged.
- Application `PreferenceAccess` orders capability checks before preference
  service access through `PreferenceAccessPort`. The controller still authorizes
  the actor first and maps unavailable results to the exact existing HTTP 503
  body. Update request evaluation remains lazy: disabled/absent capability still
  returns 503 without dereferencing the request or touching persistence.
- Host retains adapters: wire/actor/DTO mapping, Gson byte serialization, shared
  SimulationContext validation, Spring transactions, repository SQL/dialect
  selection, Firebase message construction/provider exception classification,
  Redis operations, logging, Kafka retry/DLT configuration, ACK and public
  exception translation. These extractions leave policy/orchestration in the
  domain/application without relocating adapters in this slice.

## Equivalence evidence

New domain tests cover all canonical statuses including the unmapped return
statuses; null/zero/negative delivery/order/user/shipper IDs; offer cardinality;
validation precedence/messages; each null/empty/blank canonical text field;
finite/nonnegative distance (including negative zero, infinities and NaN);
null selected element; blank/nonblank shipper names; all nullable push choices;
and push/token validation order and exact messages.

Application tests exercise all preference capability flag/port-presence
combinations, disabled update laziness, response/exception pass-through,
all three direct event flows and their routing/keys, permissive direct offer
calls, locale-dependent replay messages, ingress identity/context/dispatch order,
simulation suppression, return-status ingress, failure propagation, provider
absence, empty tokens, object-to-token conversion, UNREGISTERED cleanup,
Redis/provider/cleanup failures, and partial-send retries repeating earlier
successful tokens. Explicit regressions also prove the 101st principal unread
row remains after mark-all and PENDING replay can change sendPush without a
payload conflict.

Host regressions prove real offer replay retains one selected shipper and raw
canonical whitespace; valid simulation offer is ACKed without dispatch; valid
simulation status still dispatches with principal identity and blank name null;
null selected shipper remains a retryable wrapped NPE; conflict remains poison;
invalid simulation never dispatches; unavailable preference update still ignores
a malformed request after actor authorization. Existing golden mapping/Gson-byte,
all-field replay mismatch, ownership, listener ACK, FCM failure, Redis ownership,
preferences, schema and authorization tests remain in the full verification.

## Preserved defects and newly identified observations

The six listed slice 1/2 defects/limitations remain deliberately unchanged:

1. RETURNING/RETURNED are accepted at status ingress but rejected by message
   mapping as poison. Domain vocabulary and existing mapping equivalence tests
   preserve both halves; application ingress also proves they reach dispatch.
2. Principal mark-all-read is capped at 100, whereas count is unbounded. The new
   101-row regression proves one unread remains. Legacy bulk SQL stays unbounded.
3. Offer distance formatting uses the process default locale; identical event
   keys may have different replay message identity across locales. Domain and
   application locale regressions preserve this.
4. Direct offer calls remain more permissive than ingress, including null
   event/text/distance. They still form `shipper-offer:null:<shipperId>`.
5. sendPush is not persisted or compared by replay identity; a PENDING retry can
   change delivery choice. The new two-attempt regression proves this behavior.
6. Marketing preferences persist without a marketing dispatch producer/gate.
   Transactional delivery remains independent; no new pipeline or opt-out exists.

Additional pre-existing observations found while inspecting the extracted paths:

- A singleton offer list containing null passes cardinality checks, then raises
  NPE and is wrapped as `Failed to process persisted shipper offer`, making it
  retryable rather than poison. Domain and host regressions retain this behavior.
- Valid simulation status notifications dispatch, although valid simulation
  offers suppress dispatch. Host regression proves the asymmetry; whether it is
  intended is a separate product decision, not guessed in this extraction.
- A null token membership element raises NPE before provider send. Application
  regression preserves it; normal Redis membership is expected to contain tokens.

No newly introduced behavioral defect was observed in focused or full proof.
FCM multi-token delivery remains at-least-once; the fake-port test proves
orchestration/retry semantics, not live provider delivery.

## Validation

Final required command (with local Docker access):
`mvn -B -pl :notification-service -am clean verify`.
**BUILD SUCCESS**, all 12 reactor modules successful, 01:00 min, finished
2026-10-06T00:04:59+07:00. Log: `/tmp/notification-slice3-final-verify.log`.

Surefire XML totals across the actual reactor modules: **244 tests, zero
failures, zero errors, zero skips**. Domain: **42** tests; application: **37**;
notification-service: **107**; other reactor modules: **58**.

Docker Desktop server 29.4.2 was available after the sandboxed socket access
failed and the verification was run with elevated access. All three methods of
`NotificationKafkaPostgresIntegrationTest` executed: **3 tests, 0 failures,
0 errors, 0 skips**, 25.550 seconds:

- `orderCreatedKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`
- `deliveryStatusKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`
- `shipperOfferKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`

These tests exercise real Kafka/PostgreSQL two-replica convergence, exact replay,
fresh-group replay and contradictory reuse to notification DLT. They do not
constitute a killed packaged-process recovery rehearsal or live Firebase proof.

JaCoCo final domain: **175/178 lines = 98.31%**, **140/142 branches = 98.59%**.
Application: **117/117 lines = 100%**, **46/46 branches = 100%**. Both existing
line/branch minimum **0.85** checks passed unchanged. The application API remains
port/data declarations under its existing coverage configuration. No gate was
lowered or exclusion added. Domain's missed lines are the two defensive mapping defaults rejected first by
the title switch and the Status record constructor (exercised by application/host
tests rather than the domain suite); the two missed branches are those switch
defaults. Public unsupported statuses are covered.

Focused pre-final command `mvn -B -pl :notification-application -am clean verify`
also passed. Final `jdeps -s` shows domain depends only on java.base; application
only on java.base, notification-domain and notification-application-api. The
packaged host contains all three notification jars. Source diff against a
pre-edit filesystem snapshot was inspected; changed-file trailing-whitespace
checks passed. No git operation was used for diff or verification.

## Remaining ordered steps to relocation

1. Move host HTTP/Kafka/JPA/Redis/FCM adapters, mapper/entities/migrations and
   composition to `notification/infrastructure`. Keep adapter-owned transactions,
   committed claim/reread before push, locked completion, dialect SQL, feature
   flags, raw Gson identity, shared contracts and retry/DLT/ACK boundaries.
2. Relocate bootstrap/configuration to `notification/boot`, preserving artifact/
   DNS `notification-service`, then remove the old host directory. Parent must
   coordinate root/runtime/Compose/CI discovery changes outside this task scope.
3. Run packaged startup and actual crash/restart/recovery with PostgreSQL/Kafka;
   retain today's two-replica/replay/DLT proof after relocation. Extend runtime
   proof for Redis Lua ownership and FCM partial-delivery retry plus shipper
   foreground/background current-offer recovery. Keep live-provider claims
   separate from deterministic fake-port tests.
4. Coordinate rollout/rollback and the parent consolidation plan. Deliberate
   product repairs (return-status mapping, bulk-read cap, locale identity,
   malformed offers, simulation asymmetry, marketing rollout) require separate
   authority and must not be mixed into equivalence relocation.

## Edited and created files

- `notification/SLICE3_REPORT.md`
- `notification/application/src/main/java/com/delivery/notification/application/CompleteDelivery.java`
- `notification/application/src/main/java/com/delivery/notification/application/DispatchPush.java`
- `notification/application/src/main/java/com/delivery/notification/application/EventNotifications.java`
- `notification/application/src/main/java/com/delivery/notification/application/IncomingOffer.java`
- `notification/application/src/main/java/com/delivery/notification/application/IncomingStatus.java`
- `notification/application/src/main/java/com/delivery/notification/application/PreferenceAccess.java`
- `notification/application/src/test/java/com/delivery/notification/application/DispatchPushTest.java`
- `notification/application/src/test/java/com/delivery/notification/application/DurableSendTest.java`
- `notification/application/src/test/java/com/delivery/notification/application/EventNotificationsTest.java`
- `notification/application/src/test/java/com/delivery/notification/application/InboxTest.java`
- `notification/application/src/test/java/com/delivery/notification/application/IncomingEventsTest.java`
- `notification/application/src/test/java/com/delivery/notification/application/PreferenceAccessTest.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/EventNotificationPort.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/OfferEventPort.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/PreferenceAccessPort.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/PushPort.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/StatusEventPort.java`
- `notification/domain/src/main/java/com/delivery/notification/domain/EventIdentity.java`
- `notification/domain/src/main/java/com/delivery/notification/domain/PushEligibility.java`
- `notification/domain/src/test/java/com/delivery/notification/domain/EventIdentityTest.java`
- `notification/domain/src/test/java/com/delivery/notification/domain/PushEligibilityTest.java`
- `notification-service/src/main/java/com/delivery/notification_service/controller/NotificationController.java`
- `notification-service/src/main/java/com/delivery/notification_service/listener/DeliveryEventListener.java`
- `notification-service/src/main/java/com/delivery/notification_service/listener/MatchEventListener.java`
- `notification-service/src/main/java/com/delivery/notification_service/service/FirebaseService.java`
- `notification-service/src/main/java/com/delivery/notification_service/service/impl/NotificationServiceImpl.java`
- `notification-service/src/test/java/com/delivery/notification_service/controller/NotificationControllerAuthorizationTest.java`
- `notification-service/src/test/java/com/delivery/notification_service/listener/NotificationIngressEquivalenceTest.java`
