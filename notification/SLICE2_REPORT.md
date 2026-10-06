# Notification application slice and raw Kafka fixture repair

Scope: `notification/`, `notification-service/`, root `pom.xml`. Filesystem edits
only; no git writes or `docs/plans/` edits. Authority is the existing implementation,
`docs/services/notification_service.md`, and slice 2 of `notification/SLICE1_REPORT.md`.
No new product policy, schema, API, topic, retry rule or production default.

## Missing retryKafkaTemplate root cause

The original test injected `KafkaTemplate<String,String>` qualified as
`retryKafkaTemplate` at
`notification-service/src/test/java/com/delivery/notification_service/listener/NotificationKafkaPostgresIntegrationTest.java:98`
(line in the input tree). That bean is not part of the shared starter contract.
`platform/kafka-starter/src/main/java/com/delivery/platform/kafka/CommonKafkaProducerConfig.java:55`
exposes `commonKafkaTemplate` / `kafkaTemplate`, typed `KafkaTemplate<String,Object>`;
its producer uses `JsonSerializer` at line 47. Notification listeners already
correctly reference `commonKafkaTemplate` (OrderEventListener.java:33,
DeliveryEventListener.java:49, MatchEventListener.java:38).

This is a stale test dependency, not evidence of broken production wiring.
Simply changing the qualifier would also be wrong for this test: its payloads
are already serialized JSON, so JsonSerializer would publish a quoted JSON
string instead of the event object. The fixture now constructs its own
StringSerializer producer against the disposable Kafka broker at
NotificationKafkaPostgresIntegrationTest.java:110 and destroys it at line 125.
It adds no Spring bean, so shared auto-configuration does not back off or change.
All production Kafka configuration and defaults remain untouched. The parent
reported the analogous Order failure on main; this worker did not reproduce
Order, which is outside scope.

## Slice 2

- Domain `NotificationLifecycle` owns exact send validation/order/messages,
  nonblank-key detection, PENDING replay / locked delivery-state decisions and
  wake payload fields. `InboxActor` owns identifier validation, read idempotency,
  fallback capability and the existing list cap of 100.
- New `notification-application-api` supplies framework-independent commands,
  immutable stored views and ports for durable send, locked delivery, inbox and
  preferences. Opaque generic row/response handles allow the existing host
  mapper/entities to stay in the host without domain/application imports of
  Spring, JPA, DTOs or Firebase.
- New `notification-application` supplies `DurableSend`, `CompleteDelivery`,
  `Inbox` and `Preferences`. Application orchestration is justified here by
  durable claim/reread/replay/delivery ordering and inbox mutation/upsert flows;
  slice 1's mapping-only decisions did not need it.
- Host NotificationServiceImpl delegates send and inbox methods; the coordinator
  delegates locked completion; the preference service delegates reads/upserts.
  NotificationInboxAdapter retains the exact actor-scoped repository queries,
  mapper and legacy-fallback metric tags/counts. Internal ReplayConflictException
  is translated into the original NotificationConflictException/message before
  reaching listeners or HTTP handlers.
- The host retains Spring transaction boundaries and SQL. Send has no new outer
  transaction; REQUIRES_NEW insert claims and ordinary saveAndFlush finish before
  the existing coordinator lock transaction / push call. Firebase failure
  propagates before SENT save, the locked SENT row is a no-op, and a successful
  response retains its original snapshot timestamps with only status set SENT.
  Even a concurrent SENT insert/reread still enters the locked coordinator.
- Legacy bulk-read SQL remains unbounded, while principal bulk-read still uses
  the first 100 unread rows. Repeated single principal reads preserve readAt;
  bulk reads share one timestamp. Preference capability checks stay in the
  controller; missing rows remain marketing-off / transactional-on. H2 and
  PostgreSQL upsert selection remains in the host.

## Validation

Final command: `mvn -B -pl :notification-service -am clean verify`.
**BUILD SUCCESS**, all 12 modules successful, total time 01:35 min, finished
2026-10-05T22:30:58+07:00. Log: `/tmp/notification-final-verify.log`.
Surefire XML total: **206 tests, zero failures, zero errors, three skips**.
Notification domain: 36 tests; application: 20 tests; host: 92 tests (three skips).

Only skipped tests (all in NotificationKafkaPostgresIntegrationTest) were:

- `orderCreatedKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`
- `deliveryStatusKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`
- `shipperOfferKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`

Each XML skip reason was `disabledWithoutDocker is true and Docker is not available`.
No wiring errors occurred; Docker runtime execution is not claimed.

JaCoCo domain: 142/144 lines = 98.61%, 86/88 branches = 97.73%.
Application: 79/79 lines and 32/32 branches = 100%. Both modules passed
line/branch minimum 0.85. Application API is data/port declarations with no
business decisions, and has no coverage minimum.
`jdeps` reports domain depends only on java.base and application depends only
on java.base, notification-domain and notification-application-api. The packaged
host contains all three notification module jars. Final diff inspection and
`git diff --check` passed.

New domain tests cover all null/zero/negative IDs, blank text fields, validation
order/messages, unconstrained fields, keys, status decisions, nullable read
flags, actor/fallback truth table, and optional wake fields. Application tests
cover initial/existing/concurrent claims, contradiction on both claim paths,
all legacy stored states, push true/false/null, failure propagation, clock/save
ordering, read idempotency, scoped actor handling, list/read/count/delete/bulk
flows, preference defaults/upsert/reread failures and timestamps. Host delegation
tests exercise both principal enforcement modes, exact query scopes/caps,
fallback counters, repeated timestamps, empty ownership and unchanged errors.
The pre-existing mapping, listener ACK, failure, schema and authorization tests
remain in the full reactor check.

## Pre-existing defects and remaining proof

Preserved deliberately; no policy repair is included:

- DeliveryEventListener.java:31 accepts RETURNING/RETURNED, which mapping still
  rejects as poison (no corresponding title/message/type).
- Principal mark-all-read still processes at most 100 rows, so unread count may
  remain positive. Extracted path: notification/application/src/main/java/com/delivery/notification/application/Inbox.java:50.
- Offer distance formatting still uses the process default locale; identical
  events can have different raw replay message identity across locales.
- Direct shipper-offer service calls remain more permissive than Kafka ingress.
- sendPush remains outside replay identity and is not persisted; PENDING retries
  can change push choice. Non-PENDING existing states still skip delivery.
- Marketing preferences are persisted with no marketing dispatch pipeline/gate.
- H2 fallback is not PostgreSQL concurrent-claim proof; FCM multi-token delivery
  remains at-least-once. Docker-backed Kafka/PostgreSQL replay/DLT/replica proof
  is unavailable in this environment. Infrastructure/boot relocation remains
  the next architecture slice, outside this request.

## Edited and created files

- `notification-service/pom.xml`
- `notification-service/src/main/java/com/delivery/notification_service/service/impl/NotificationDeliveryCoordinator.java`
- `notification-service/src/main/java/com/delivery/notification_service/service/impl/NotificationInboxAdapter.java`
- `notification-service/src/main/java/com/delivery/notification_service/service/impl/NotificationPreferenceServiceImpl.java`
- `notification-service/src/main/java/com/delivery/notification_service/service/impl/NotificationServiceImpl.java`
- `notification-service/src/test/java/com/delivery/notification_service/listener/NotificationKafkaPostgresIntegrationTest.java`
- `notification-service/src/test/java/com/delivery/notification_service/service/impl/NotificationInboxDelegationTest.java`
- `notification/SLICE2_REPORT.md`
- `notification/application-api/pom.xml`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/DeliveryPort.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/DurableSendPort.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/InboxPort.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/PreferencePort.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/SendCommand.java`
- `notification/application-api/src/main/java/com/delivery/notification/application/api/StoredNotification.java`
- `notification/application/pom.xml`
- `notification/application/src/main/java/com/delivery/notification/application/CompleteDelivery.java`
- `notification/application/src/main/java/com/delivery/notification/application/DurableSend.java`
- `notification/application/src/main/java/com/delivery/notification/application/Inbox.java`
- `notification/application/src/main/java/com/delivery/notification/application/Preferences.java`
- `notification/application/src/main/java/com/delivery/notification/application/ReplayConflictException.java`
- `notification/application/src/test/java/com/delivery/notification/application/CompleteDeliveryTest.java`
- `notification/application/src/test/java/com/delivery/notification/application/DurableSendTest.java`
- `notification/application/src/test/java/com/delivery/notification/application/InboxTest.java`
- `notification/application/src/test/java/com/delivery/notification/application/PreferencesTest.java`
- `notification/domain/src/main/java/com/delivery/notification/domain/InboxActor.java`
- `notification/domain/src/main/java/com/delivery/notification/domain/NotificationLifecycle.java`
- `notification/domain/src/test/java/com/delivery/notification/domain/NotificationLifecycleTest.java`
- `pom.xml`
