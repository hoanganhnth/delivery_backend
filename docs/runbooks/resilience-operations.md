# Resilience Operations Runbook

## Gateway rate limits

The API Gateway stores fixed-window counters in Redis under
`delivery:gateway:rate-limit:<group>:<key>`. Every key is the direct peer IP.
Gateway uses the first `X-Forwarded-For` value only when
`RATE_LIMIT_TRUSTED_PROXY=true` and the direct peer matches an explicit
comma-separated `RATE_LIMIT_TRUSTED_PROXY_CIDRS` CIDR allow-list; it never uses
JWT or identity headers for rate-limit keys.

Each Redis counter call is bounded by `RATE_LIMIT_REDIS_TIMEOUT_MS` (500 ms by
default). On timeout or an unavailable Redis connection, public catalog and
authenticated reads fail open; auth, mutation and WebSocket handshakes return
the standard 503 envelope and do not wait on the Redis client indefinitely.

Default policy (per 60 seconds):

| Group | Limit | Redis outage behavior |
| --- | ---: | --- |
| public auth | 10/IP | fail closed (503) |
| public catalog | 120/IP | fail open |
| authenticated read | 300/IP | fail open |
| mutation | 30/IP | fail closed (503) |
| raw location WebSocket handshake | 10/IP | fail closed (503) |

Location messages after a successful raw WebSocket handshake are not rate
limited by Gateway. To tune limits, set the corresponding `RATE_LIMIT_*`
environment variables on `api-gateway` and roll the Gateway deployment. Do not
delete counters to respond to a single client: wait for the maximum 60-second
TTL unless incident command explicitly authorizes an emergency flush.

Observe `gateway.rate_limit.rejected`, `gateway.rate_limit.redis_failure`, and
`gateway.rate_limit.redis_fail_closed`, tagged by group. A sustained rise in
fail-open Redis failures is an infrastructure incident even if customers remain
able to browse.

## Kafka DLT triage and replay

Core consumer failures are retried with bounded backoff and then written to the
same-partition `<source-topic>.DLT`. Spring Kafka recovery headers preserve the
original topic, partition, offset and exception. Consumers must also preserve
the event's `eventId` and `correlationId` from the payload/headers when present;
these identifiers are the replay/dedup key.

Every DLT record must contain Spring Kafka's `KafkaHeaders.DLT_ORIGINAL_TOPIC`,
`DLT_ORIGINAL_PARTITION`, `DLT_ORIGINAL_OFFSET`, and `DLT_EXCEPTION_FQCN`, plus
the propagated `eventId` and `correlationId` headers when the source record has
them. Do not replay a record whose identity headers are absent: repair or
quarantine it as a poison-message incident instead.

DLT topics require explicit production provisioning with 14-day retention and
consumer/producer ACLs. Run
[`scripts/provision-kafka-resilience-topics.sh`](../../scripts/provision-kafka-resilience-topics.sh)
from an approved Kafka admin toolbox with `KAFKA_BOOTSTRAP_SERVERS` and the
source topics' partition counts. The script defaults to replication factor 3;
set `DLT_REPLICATION_FACTOR=1` only in a single-broker rehearsal. Local Compose
auto-creation is a development-only convenience and is not production evidence.

Replay procedure, operator-only:

1. Page the owning service and identify the source topic, partition, offset,
   event ID, correlation ID and exception class from the DLT record.
2. Classify the error: transient infrastructure errors may be replayed after the
   dependency recovers; validation/poison or contradictory business payloads
   require correction or an incident decision and must not be blindly replayed.
3. Verify the consumer's durable idempotency receipt/inbox and payload
   fingerprint contract in `docs/system-contract-inventory.md`.
4. Replay one record to the original topic and original key/partition. Monitor
   the side effect, consumer lag and DLT count. Do not bulk replay before this
   single-record proof succeeds.
5. Keep an incident record containing the original and replay offsets. Exact
   replay must be a no-op; a changed fingerprint must return to the error/DLT
   path rather than overwrite a committed side effect.

Prometheus rule `DeliveryKafkaDltIncreasing` alerts on a non-zero increase of
`delivery_kafka_events_total{event="dlt"}` over five minutes. Separately alert
when any core DLT topic's oldest record exceeds one hour without an assigned
owner; broker-age alerting is deployed with the Kafka exporter/operator because
the application does not expose topic-retention age.
