# Notification inventory and first domain slice

Scope: `notification/`, `notification-service/`, root `pom.xml`. Existing branch
was `refactor/notification-core`; no git writes or plan edits were performed.
Authority: `docs/services/notification_service.md`, the Notification tranche in
`docs/plans/active/service-architecture-consolidation.md`, and the existing host
implementation/tests. This is an equivalence extraction, with no new policy.

All file:line references below refer to the resulting tree. Java host references
use the prefix `notification-service/src/main/java/com/delivery/notification_service/`.

## Inventory inspected before extraction

| Input / entrypoint | Existing mapping and recipient | Delivery / identity |
| --- | --- | --- |
| `order.created`, `listener/OrderEventListener.java:38` | Customer `userId`, optional `userPrincipalId`; title `Đơn hàng đã được tạo`; message `Đơn hàng #<orderId> từ <restaurantName> đã được tạo thành công`; ORDER_CREATED, MEDIUM, related ORDER/orderId | `order-created:<eventId>`; push defaults true. Listener rejects missing stable event, nonpositive order/user IDs or blank canonical restaurant. Restaurant text is preserved without trimming. |
| `delivery.status-updated`, `listener/DeliveryEventListener.java:56` | Customer `userId`, optional `userPrincipalId`; nine status-specific title/message/type mappings (below), HIGH, related DELIVERY/deliveryId | `delivery-status:<eventId>`; push defaults true. Listener validates event, delivery/order/user IDs, canonical status and simulation context. Blank shipper names become null; no synthesized name. |
| `delivery.shipper-offered`, `listener/MatchEventListener.java:43` | Exactly the persisted single selected shipper `shipperId`, no principal field; `🎯 Đơn hàng phù hợp!`; `Đơn hàng #<orderId> từ <restaurantName> - cách điểm lấy khoảng <distance formatted %.1f>km. Mở ứng dụng để xem offer hiện tại.`; MATCH_FOUND, HIGH, related ORDER/orderId | `shipper-offer:<offerEventId>:<shipperId>`; explicit push true. JSON contains pickupAddress, deliveryAddress, distance, orderId and recoveryEndpoint `/api/deliveries/offers/current`. No financial estimate. Listener validates exactly one result, IDs, finite nonnegative distance and nonblank restaurant/addresses. A valid simulation offer is ACKed without inbox creation or external delivery (`MatchEventListener.java:76`). |

The former mapping bodies were in `service/impl/NotificationServiceImpl.java`
(pre-change lines 359, 386 and 408). They now call the domain via the same public
methods at lines 361, 372 and 378. Wire validation, topic configuration, retry
annotations, simulation suppression and exception wrapping remain in the host.

| Delivery status | Title | Message without name |
| --- | --- | --- |
| PENDING | Đơn đang chờ xử lý giao hàng | Đơn hàng đang chờ bắt đầu quy trình giao |
| FINDING_SHIPPER | Đang tìm shipper | Hệ thống đang tìm shipper cho đơn hàng của bạn |
| WAIT_SHIPPER_CONFIRM | Đang chờ shipper xác nhận | Đang chờ shipper xác nhận nhận đơn |
| SHIPPER_NOT_FOUND | Chưa tìm được shipper | Hiện chưa tìm được shipper phù hợp cho đơn hàng |
| ASSIGNED | Đã phân công shipper | Đơn hàng của bạn đã được phân công cho shipper |
| PICKED_UP | Shipper đã lấy hàng | Đơn hàng của bạn đã được lấy và chuẩn bị giao |
| DELIVERING | Đơn hàng đang được giao | Đơn hàng của bạn đang được giao |
| DELIVERED | Giao hàng hoàn thành | Đơn hàng đã được giao thành công |
| CANCELLED | Giao hàng đã bị hủy | Quy trình giao hàng đã bị hủy |

Type is `DELIVERY_<status>`. A nonblank shipper name is preserved verbatim and
substitutes these four messages: `<name> đã được phân công giao đơn hàng của bạn`,
`<name> đã lấy đơn hàng và chuẩn bị giao`, `<name> đang trên đường giao hàng`,
`Đơn hàng đã được <name> giao thành công`. Other statuses ignore the name.

Preferences: `controller/NotificationController.java:134` and `:144` expose GET
preferences / PUT marketing for the canonical authenticated principal.
`preferencesAvailable()` at `:155` requires the capability flag and service
presence, after actor validation and before persistence. Disabled returns HTTP
503 with the existing message. `NOTIFICATION_PREFERENCES_ENABLED=false` remains
the default (`src/main/resources/application.properties:35`).
`service/impl/NotificationPreferenceServiceImpl.java:31` reads a principal row;
`:39` performs the existing atomic H2/PostgreSQL upsert. Missing row means
configured=false, marketing=false, transactional=true, updatedAt=null. Stored
rows reflect only marketing; transactional is always true. There is **no
marketing dispatch gate/producer today** and no transactional opt-out. This
slice extracts existing capability/default decisions, without adding dispatch
enforcement or wiring preferences into transactional event sends.

Inbox and ownership: `controller/NotificationController.java:66` lists self;
`:77` unread, `:86` unread count, `:95` mark one, `:105` mark all, `:114` get one,
`:124` delete. JWT actor checks are at `:164` and path self check at `:171`.
`service/impl/NotificationServiceImpl.java:188`, `:241`, `:264`, `:300`, `:321`
keep repository calls scoped to the principal; `:332` selects owned rows.
`NOTIFICATION_PRINCIPAL_OWNERSHIP_ENFORCED=false` preserves fallback only for
unmigrated rows whose principal is null and legacy user matches. Principal-owned
rows cannot be claimed through matching legacy ID alone. Lists are capped at
100; unread count counts all. Repeated mark-one preserves readAt. Delete/lookup
of an unowned row raises the existing NotificationNotFoundException. Internal
send at `controller/NotificationController.java:51` requires Internal-Token;
there is no public Gateway route for sending.

Dedup/idempotency: `service/impl/NotificationServiceImpl.java:77` validates send,
looks up nonblank keys, verifies exact immutable fields at `:163`, retries
PENDING and skips other already stored states. Comparison includes userId,
userPrincipalId, title, message, type, priority, relatedEntityId,
relatedEntityType and the raw serialized data string. It deliberately excludes
sendPush, read/status/timestamps and dedup key itself. Mismatch raises the same
NotificationConflictException with the same message before disclosure/delivery.
Blank/no key still uses saveAndFlush. Keyed insert remains at `:146`;
`repository/NotificationRepository.java:94` uses PostgreSQL ON CONFLICT DO
NOTHING in REQUIRES_NEW, followed by reread and replay validation. Unique-key
claim plus the locked coordinator converge on one stable ID, with the production
race evidence still requiring Docker. H2 fallback at repository `:124` is
unchanged and is not equivalent production concurrency proof.

FCM and ACK ownership: `service/impl/NotificationDeliveryCoordinator.java:29`
locks the stored row, skips SENT, rejects invalid delivery state, conditionally
sends push and then saves SENT/sentAt in its transaction. The PENDING insert
commits before external I/O. `service/FirebaseService.java:41` returns without
external work when Firebase is unconfigured or no tokens exist; configured
Redis/provider failures propagate and leave PENDING for retry. UNREGISTERED
removes the token; other Firebase errors retain their sanitized failure.
`service/FirebaseWakeMessageFactory.java:21` builds alert/data wake messages;
REST inbox and Delivery current-offer recovery remain authoritative. Redis is
only token membership/reverse ownership, atomic via Lua (`service/RedisService.java`).
FCM multi-token delivery is at-least-once, and partial failure may repeat a push.
Kafka ACK is owned by the three listeners after service completion (`:59`,
`:89`, `:105` respectively); poison IllegalArgumentException and
NotificationConflictException bypass retries, with unchanged notification retry
suffix / DLT suffix and default four attempts. There is no WebSocket/STOMP inbox
transport, inbox Redis cache or domain-owned acknowledgment.

## Implemented slice and proposed order

1. **This slice:** add the framework-free `notification-domain` reactor module
   before the existing host, using delivery-build-parent's JaCoCo check with
   line and branch minimum 0.85. NotificationMapping owns recipient routing,
   message/type/priority/entity selection and key construction. NotificationIntent
   carries immutable decisions/data, ReplayPayload owns exact immutable replay
   matching, and NotificationPreferences owns existing preference capability and
   default rules. Host DTO adaptation, Gson, DB operations, transport, exception
   translation, locks and FCM remain in place.
2. Extract application-api ports and application use cases for durable send /
   replay / delivery completion, maintaining commit-before-I/O, lock and
   exception boundaries; then inbox ownership/read operations and preference
   reads/upserts. No application module is justified for this pure decision
   slice: moving orchestration now would add unnecessary transaction/port scope.
3. Move HTTP/Kafka/JPA/Redis/FCM adapters and composition into infrastructure,
   keeping retry groups/topics, ownership flags and artifact/DNS identity.
4. Relocate bootstrap into notification/boot and prove packaged startup,
   PostgreSQL concurrent dedup, Kafka retry/DLT/crash recovery, Redis token
   ownership and FCM partial-delivery recovery. Integrate rollout/rollback and
   root/runtime discovery changes through the parent tranche's wider scope.

## Equivalence proof and known defects preserved

Domain tests use exact golden strings for all nine supported statuses, all four
named variants, null/empty/blank names, recipient/principal identity, priorities,
entity fields, push defaults, key separation, null/zero/negative identifiers,
validation order, unsupported statuses and null-status exception type. Preference
tests cover the complete capability truth table and absent/opt-out/opt-in states.
Replay tests vary every immutable field, including null transitions.

Host NotificationMappingEquivalenceTest checks all output request fields, all
nine statuses and the original HashMap/Gson serialization bytes including null
omission and HTML escaping. Keeping default-capacity HashMap assembly preserves
iteration/serialization behavior; raw JSON remains part of replay identity.
Host replay mismatch tests confirm every compared field retains the original
conflict type/message before delivery. Existing listener ACK/replay, ownership,
FCM failure/token, migration and preferences tests remain relevant regression
proof. No schema, topic, request/API, feature flag or persistence SQL was changed.

Observed pre-existing defects/limitations, intentionally not repaired:

- DeliveryEventListener.java:29 accepts RETURNING/RETURNED, but mapping has no
  corresponding messages/types and raises IllegalArgumentException. Such records
  are poison rather than customer notifications.
- NotificationServiceImpl.java:264 principal-based mark-all-read handles only the
  first 100 unread rows; unread count can still be positive after "mark all".
- Offer text uses String.format with the process default locale, while message
  text is replay identity. The same event across different locale settings can
  conflict (decimal dot versus comma). Locale behavior is retained and tested.
- Direct sendShipperMatchFoundNotification calls are more permissive than the
  listener: null event/text/distance can form `shipper-offer:null:<shipperId>` and
  messages containing null. Existing validation is retained at the wire boundary.
- sendPush is excluded from replay identity and is not stored on the inbox row;
  a PENDING retry can change the delivery choice without a payload conflict.
- Marketing preferences are persisted but not enforced by a marketing pipeline;
  that is the documented deferred capability, not a policy added by this slice.

## Validation

- `mvn -B -pl :notification-domain -am clean verify`: initial focused verification
  passed, 32 tests, zero failures/errors/skips.
- Final `mvn -B -pl :notification-service -am clean verify`: **BUILD SUCCESS**,
  all ten reactor modules successful, 45.385 seconds, finished
  2026-10-05T21:09:20+07:00. Notification domain: 32 tests, zero failures/errors/
  skips. Notification host: 89 tests, zero failures/errors, three skips; 24 tests
  are the new host equivalence suite. Reactor-wide Surefire XML totals:
  179 tests, zero failures/errors, 3 skips.
- Final JaCoCo domain report: lines 111 covered / 2 missed = **98.23%**;
  branches 58 covered / 2 missed = **96.67%**. Both 85% gates passed. The two
  missed defaults are defensive message/type unknown-status branches that the
  title switch rejects first; unknown-status behavior is tested via the public
  API without weakening validation or exposing implementation helpers.
- Docker-only skips: all three methods of
  `NotificationKafkaPostgresIntegrationTest`:
  `orderCreatedKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`,
  `deliveryStatusKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`,
  `shipperOfferKafkaPostgresReplayAndContradictoryReuseConvergeAcrossTwoNotificationReplicas`.
  Docker was unavailable; `@Testcontainers(disabledWithoutDocker = true)` caused
  the skips. No PostgreSQL/Kafka concurrency or restart rehearsal is claimed.
- `jdeps -s notification/domain/target/notification-domain-1.0.0-SNAPSHOT.jar`
  reports only `java.base`. Production domain has no framework dependencies;
  its only declared dependency is test-scoped JUnit.
- Packaged host contains
  `BOOT-INF/lib/notification-domain-1.0.0-SNAPSHOT.jar`.
- Final diff inspection and `git diff --check` passed. Plan files, schema,
  configuration and neighboring services were not edited.

## Edited and created files

- `pom.xml`
- `notification-service/pom.xml`
- `notification-service/src/main/java/com/delivery/notification_service/controller/NotificationController.java`
- `notification-service/src/main/java/com/delivery/notification_service/service/impl/NotificationServiceImpl.java`
- `notification-service/src/main/java/com/delivery/notification_service/service/impl/NotificationPreferenceServiceImpl.java`
- `notification-service/src/test/java/com/delivery/notification_service/service/impl/NotificationMappingEquivalenceTest.java`
- `notification/domain/pom.xml`
- `notification/domain/src/main/java/com/delivery/notification/domain/NotificationIntent.java`
- `notification/domain/src/main/java/com/delivery/notification/domain/NotificationMapping.java`
- `notification/domain/src/main/java/com/delivery/notification/domain/NotificationPreferences.java`
- `notification/domain/src/main/java/com/delivery/notification/domain/ReplayPayload.java`
- `notification/domain/src/main/java/com/delivery/notification/domain/Types.java`
- `notification/domain/src/test/java/com/delivery/notification/domain/NotificationMappingTest.java`
- `notification/domain/src/test/java/com/delivery/notification/domain/NotificationPreferencesTest.java`
- `notification/domain/src/test/java/com/delivery/notification/domain/ReplayPayloadTest.java`
- `notification/SLICE1_REPORT.md`
