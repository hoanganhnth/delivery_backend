# Packaged rehearsal blocked by Order cancellation envelope loss

Observed on 2026-10-06 after `mvn -q -DskipTests clean package` exited 0.
No service files were edited.

## Executable evidence

Run: `SAGA_MATCH_CRASH_RUN_ID=rehearsal-1791248509 SAGA_MATCH_CRASH_TIMEOUT_SECONDS=900 bash scripts/verify-saga-match-crash-replay.sh`.
Owned project: `saga-match-crash-1791248509-79809-4382`.
Full local log: `/tmp/saga-match-rehearsal2.log`.

The run passed fixture construction/startup, two Match replicas with Find
paused, real Saga Find publication, and the global Redis outage's fail-closed
cancellation check. It reached “make Redis unavailable only to Match while it
consumes Stop”, but Saga could not publish Stop.

Read-only observations from the owned PostgreSQL/Kafka containers:

- Order 1 was `CANCELLED`; its `order.cancelled` outbox was `SENT`.
- Stored outbox payload contained
  `"eventId":"f61b123c-a831-48c2-b5a5-b5f1d4b7331a"`,
  `"eventType":"ORDER_CANCELLED"`, and `"occurredAt"`.
- `kafka-console-consumer --bootstrap-server kafka:9092 --topic order.cancelled
  --from-beginning --max-messages 1 --timeout-ms 10000` returned the cancellation
  body for order 1 with **none of these three envelope fields**. Its timestamps
  were also serialized as arrays, unlike the stored ISO strings.
- Saga logged: `Error processing order.cancelled: eventId is required and must
  be a UUID`. Its outbox had only create-delivery, find-shipper and
  update-order-status commands for order 1; no stop-matching command existed.
- The run was stopped with SIGTERM after confirmation, allowing its owned
  resource cleanup trap to execute; this is not a passing rehearsal.

## Production root cause

`order-service/src/main/java/com/delivery/order_service/service/OrderOutboxRelay.java`
rehydrates `ORDER_CANCELLED`/`REFUND_ELIGIBLE` JSON into
`OrderCancelledEvent` in `typedPayload` (lines 239–246). That DTO,
`order-service/src/main/java/com/delivery/order_service/dto/event/OrderCancelledEvent.java`,
has no `eventId`, `eventType` or `occurredAt` fields. Serialization therefore
drops the persisted envelope. Kafka headers carry identity, but
`dispatch/infrastructure/src/main/java/com/delivery/saga_orchestrator_service/listener/KafkaEventListener.java`
lines 70–74 require the UUID in the JSON body before calling SagaManager.
The producer/consumer contract is incompatible; scripts cannot repair it
without bypassing the real event path.

## COD diagnosis limits and fixture checks

This fresh build fails earlier than the supplied `.rehearsal5-failure.log`, so
the original COD eligibility failure is not yet reproduced or resolved.
Before stopping, runtime checks established:

- Both Match and Settlement retain the same Compose internal-secret mount.
- Settlement returned HTTP 200 to a request from Match using that mounted
  credential. After seed, eligibility for shipper 1 and amount 65000 returned
  `{"status":1,"message":"Thành công","data":true}`.
- Settlement balance for canonical shipper 1 was deposit `500000.00`, reserved
  deposit `0.00`; order 1's canonical total was `57000.00`.

An initial run (`/tmp/saga-match-rehearsal.log`, exit 22) encountered a separate
cold-start fixture race: seed restaurant creation got HTTP 503 and Gateway
logged `No servers available for service: restaurant-service`. The harness now
observes HTTP 200 on the public restaurant GET route before non-idempotent seed
creation. Its Docker-free regression rejects 503 and 401 and accepts 200.
The second run seeded successfully. Crash assertions and timeouts are unchanged.

Validation: `python3 scripts/test-saga-match-crash-harness.py` passed all 19 tests;
`bash -n scripts/verify-saga-match-crash-replay.sh` passed.

Parent takeover requires production scope to preserve the cancellation envelope
on the Kafka wire, then rerun the packaged proof and diagnose the original COD
step if it still fails. Do not inject a synthetic Stop or weaken its assertions.
