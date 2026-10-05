# Notification Kafka/PostgreSQL timeout diagnosis

Status: DONE — Notification-side resolution verified with Docker.
The diagnosis and failed-run evidence below describe the original transport
defect. The authorized correction leaves producer services and the shared
Kafka starter unchanged.

## Evidence from the supplied failure log

These excerpts preserve the relevant evidence from `notification/kafka-it-failure.log`,
which was deleted as requested. Line numbers refer to that original file.

- Line 695: Flyway connected to `notification_kafka` on PostgreSQL 16.13.
- Lines 905 and 913: `notification-inbox-replicas` assigned
  `delivery.status-updated.notification-inbox-proof-1` and `-0` respectively.
- Line 932: source partition 0, offset 0 was sent directly to the Notification
  DLT; the cause was `Cannot convert from [java.util.LinkedHashMap] to [java.lang.String]`.
- Line 1212: shipper offer partition 0, offset 0 was sent directly to the DLT;
  the cause was `Cannot convert from [java.util.LinkedHashMap] to [com.delivery.delivery.contracts.ShipperFoundEvent]`.
- Line 1477: order-created partition 0, offset 0 was sent directly to the DLT;
  the cause was `Cannot convert from [java.util.LinkedHashMap] to [com.delivery.order.contracts.OrderCreatedEvent]`.
- Lines 1541–1565: all three tests failed while waiting for one committed
  notification. The conversion errors precede listener business logic and DB writes.

## Root cause and production boundary

The fixture publishes headerless JSON with `StringSerializer`.
`CommonKafkaConsumerConfig.consumerFactory()` uses `JsonDeserializer`, honors
type headers and defaults to `Object`. Without a header, a JSON object becomes
`LinkedHashMap`. The container factory supplies no message converter capable of
binding that map to the three listener signatures. Kafka assignment, listener
startup, acknowledgment availability and PostgreSQL startup are not the cause
of the observed failure.

Order's production `OrderOutboxRelay.typedPayload()` rehydrates `OrderCreatedEvent`
before serialization, so the order fixture omits a production type header.
However, Delivery's `OutboxMessageRelay` publishes `objectMapper.readTree(...)`
through its `JsonSerializer` producer for both delivery status and shipper offer.
That emits `com.fasterxml.jackson.databind.node.ObjectNode` as the type header.
The shared Notification consumer does not trust that class. Merely trusting it
would still leave an incompatible object for the listener signatures (`String`
and `ShipperFoundEvent`). There is no service-specific consumer/converter override.
The `spring.json.use-type-info-headers=false` property in Notification's main
configuration is not read by the shared factory, which explicitly sets it to true.

A standalone diagnostic using the resolved Notification test classpath and the
actual shared consumer factory properties exercised the production serializer /
deserializer round trip:

```text
Production relay rejected: The class 'com.fasterxml.jackson.databind.node.ObjectNode' is not in the trusted packages: [java.util, java.lang, java.lang.*, com.delivery].
Production relay round-trip result: not delivered
Compatible with delivery listener String: false
Raw fixture round-trip result: java.util.LinkedHashMap
```

Diagnostic source/output: `/tmp/NotificationWireDiagnostic.java` and
`/tmp/notification-wire-diagnostic.log`. Exit code: 0 (the expected rejection was
captured). A fixture-only transport override would conceal the Delivery
production incompatibility, so none was introduced.

## Validation

Required command: `mvn -B -pl :notification-service -am clean verify`.
Docker access initially failed in the sandbox with permission denied on
`unix:///Users/a/.docker/run/docker.sock`. Elevated execution reached Docker
Desktop 29.4.2. The verification run started both `apache/kafka-native:3.8.0`
and `postgres:16-alpine`; it is a real Docker-backed run, not a skipped proof.
Full run output: `/tmp/notification-kafka-verify.log`.

Final Maven result: exit code 1, `BUILD FAILURE`, finished at
2026-10-05T22:47:21+07:00 (3 minutes). Log lines 675 and 678 confirm Kafka and
PostgreSQL containers started. Line 1436 reports this integration class:
3 tests, 3 failures, 0 errors, 0 skipped. Line 1595 reports Notification:
92 tests, 3 failures, 0 errors, 0 skipped. Line 1613 records `BUILD FAILURE`.
The run reproduces the same map-to-listener conversion failures and subsequent
notification wait timeouts. All upstream reactor modules passed.

## Resolution

`NotificationKafkaConsumerConfig` owns the consumer and listener container
factories. Values use `StringDeserializer`; `StringJsonMessageConverter` binds
typed listeners using inferred parameter types, ignoring producer Java type
headers. Delivery's `String` parameter receives the original JSON for its
existing parsing/validation. Neither ObjectNode trust nor a producer change is
needed.

Notification also owns the recovery producer factory and template. Raw strings
use `StringSerializer` so retries/DLT preserve the source JSON bytes; typed
objects still use `JsonSerializer`. Explicit template wiring is necessary because
the shared producer auto-configuration directly invokes its own factory method.
The common error handler, retry policy, DLT destinations, poison exclusions,
group/topic properties, single-consumer concurrency and shared
`MANUAL_IMMEDIATE` acknowledgment mode remain unchanged. No listener business
logic changed.

`NotificationKafkaPostgresIntegrationTest` publishes through `JsonSerializer`:
Delivery status and shipper-offer records use `readTree()` (ObjectNode type
header), and order records use typed `OrderCreatedEvent`. Header-less JSON is
retained for same-group replay. Payload expectations use the producer's canonical
serialized representation, including the nullable fields of the order contract.
Notification/DLT assertions and deadlines are unchanged.

`NotificationKafkaConsumerConfigTest` checks converter binding against each
actual listener method signature with and without real serializer type headers,
unresolvable producer headers, malformed typed JSON, service bean overrides,
consumer settings, acknowledgment mode, concurrency and byte-preserving recovery.
Focused command:
`mvn -B -pl :notification-service -am -Dtest=NotificationKafkaConsumerConfigTest -Dsurefire.failIfNoSpecifiedTests=false test`.
Result: BUILD SUCCESS; 9 tests, 0 failures, 0 errors, 0 skipped.
Log: `/tmp/notification-wire-focused.log`.

### Successful Docker-backed verification

Command: `mvn -B -pl :notification-service -am clean verify` (elevated for Docker).
Result: exit code 0, BUILD SUCCESS, finished at 2026-10-05T23:04:29+07:00.
Full log: `/tmp/notification-wire-verify.log`.

- Log lines 675 and 678 confirm real `apache/kafka-native:3.8.0` and
  `postgres:16-alpine` containers started.
- Line 2100: Kafka/PostgreSQL integration class — 3 tests, 0 failures,
  0 errors, 0 skipped. All three real-wire ingress cases passed the existing
  two-replica convergence, same-group/header-less replay, fresh-group replay,
  contradictory-payload DLT and recovered-source-offset assertions.
- Line 2105: converter/configuration class — 9 tests, 0 failures,
  0 errors, 0 skipped.
- Line 2223: Notification service — 101 tests, 0 failures, 0 errors, 0 skipped.
- Entire reactor — 215 tests, 0 failures, 0 errors, 0 skipped; all 12 modules
  succeeded. Counts are the sum of the per-module Maven result summaries.

The fixture's deadlines remain 30 seconds for state/offset convergence,
20 seconds for DLT polling and 10 seconds for Kafka operations. Listener topic
and retry policy assertions and acknowledgment/poison behavior tests also passed.
No changes were made to Delivery, Order, Match, the shared Kafka starter or
`docs/plans/`. The consumer config and real-wire fixture were already present
when this continuation began; this run completed recovery-template wiring,
added focused regression proof and updated this diagnosis.
