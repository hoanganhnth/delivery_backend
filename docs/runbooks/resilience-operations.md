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

Core consumer failures are retried with bounded backoff and then written to a
same-partition owner-specific DLT. Single-owner/blocking listeners use
`<source-topic>.DLT`; shared non-blocking sources use the named destination in
the topology table below (for example, `<source-topic>.saga.DLT`). Spring Kafka
recovery headers preserve the original topic, partition, offset and exception.
Consumers must also preserve the event's `eventId` and `correlationId` from the
payload/headers when present; these identifiers are the replay/dedup key.

Every DLT record must contain Spring Kafka's `KafkaHeaders.DLT_ORIGINAL_TOPIC`,
`DLT_ORIGINAL_PARTITION`, `DLT_ORIGINAL_OFFSET`, and `DLT_EXCEPTION_FQCN`, plus
the propagated `eventId` and `correlationId` headers when the source record has
them. Do not replay a record whose identity headers are absent: repair or
quarantine it as a poison-message incident instead.

Retry and DLT topics require explicit production provisioning with 14-day
retention and consumer/producer ACLs. Run
[`scripts/provision-kafka-resilience-topics.sh`](../../scripts/provision-kafka-resilience-topics.sh)
from an approved Kafka admin toolbox with `KAFKA_BOOTSTRAP_SERVERS` and the
source topics already present with their canonical partition counts. The
provisioner derives each retry/DLT partition count from its source and increases
an accidentally smaller target before reconciling `retention.ms` and
`cleanup.policy=delete`; this is required because Spring's retry/DLT publisher
keeps the source partition. The script defaults to replication factor 3; set
`DLT_REPLICATION_FACTOR=1` (or `RESILIENCE_REPLICATION_FACTOR=1`) only in a
single-broker rehearsal. It fails closed if an existing target has too few
replicas, because increasing replication requires an operator-controlled Kafka
partition reassignment rather than a blind script mutation. Local Compose
auto-creation is a development-only convenience and is not production evidence.

The canonical Match command topology is explicit and must be provisioned before
Match starts with `autoCreateTopics=false`:

| Source | Retry topics | DLT |
| --- | --- | --- |
| `saga.command.find-shipper` | `.retry-1000`, `.retry-2000`, `.retry-4000` | `.DLT` |
| `saga.command.stop-matching` | blocking retry in the listener factory | `.DLT` |
| `shipper.location-updated` | blocking retry in the listener factory | `.DLT` |
| `shipper.status-change` | blocking retry in the listener factory | `.DLT` |

The shared-source non-blocking topology is isolated by listener owner. The
three retry topics use the normal configured delays (`1000`, `2000`, `4000` ms
by default); the provisioning script is the canonical full manifest.

| Source | Consumer owner | Retry topics | DLT |
| --- | --- | --- | --- |
| `order.created` | Saga | `order.created-retry-saga-*` | `order.created.saga.DLT` |
| `order.created` | Notification | `order.created-retry-notification-*` | `order.created.notification.DLT` |
| `order.created` | Promotion reservation (flagged off) | `order.created-retry-promotion-*` | `order.created.promotion.DLT` |
| `order.cancelled` | Promotion reservation (flagged off) | `order.cancelled-retry-promotion-*` | `order.cancelled.promotion.DLT` |
| `order.refund-eligible` | Promotion reservation (flagged off) | `order.refund-eligible-retry-promotion-*` | `order.refund-eligible.promotion.DLT` |
| `order.created` | Flash-sale reservation (flagged off) | `order.created-retry-flashsale-*` | `order.created.flashsale.DLT` |
| `order.cancelled` | Flash-sale reservation (flagged off) | `order.cancelled-retry-flashsale-*` | `order.cancelled.flashsale.DLT` |
| `order.refund-eligible` | Flash-sale reservation (flagged off) | `order.refund-eligible-retry-flashsale-*` | `order.refund-eligible.flashsale.DLT` |
| `order.created` | Analytics (flagged off) | `order.created-retry-analytics-*` | `order.created.analytics.DLT` |
| `order.status-updated` | Analytics (flagged off; no active producer) | `order.status-updated-retry-analytics-*` | `order.status-updated.analytics.DLT` |
| `order.cancelled` | Analytics (flagged off) | `order.cancelled-retry-analytics-*` | `order.cancelled.analytics.DLT` |
| `restaurant.order-confirmed` | Saga | `restaurant.order-confirmed-retry-saga-*` | `restaurant.order-confirmed.saga.DLT` |
| `restaurant.order-confirmed` | Order | `restaurant.order-confirmed-retry-order-*` | `restaurant.order-confirmed.order.DLT` |
| `delivery.status-updated` | Saga | `delivery.status-updated-retry-saga-*` | `delivery.status-updated.saga.DLT` |
| `delivery.status-updated` | Notification | `delivery.status-updated-retry-notification-*` | `delivery.status-updated.notification.DLT` |
| `shipper.location-updated` | Tracking support history | `shipper.location-updated-retry-tracking-*` | `shipper.location-updated.tracking.DLT` |
| `shipper.status-change` | Tracking delivery-room routing | blocking retry in listener factory | `shipper.status-change.tracking.DLT` |
| `identity.profile.created` | Auth profile linkage | `identity.profile.created-retry-auth-identity-*` | `identity.profile.created.auth-identity.DLT` |
| `identity.status.changed` | User lifecycle projection | `identity.status.changed-retry-user-identity-*` | `identity.status.changed.user-identity.DLT` |
| `identity.status.changed` | Shipper lifecycle projection | `identity.status.changed-retry-shipper-identity-*` | `identity.status.changed.shipper-identity.DLT` |
| `shipper.identity.upserted` | Delivery shipper mapping | `shipper.identity.upserted-retry-delivery-shipper-identity-*` | `shipper.identity.upserted.delivery-shipper-identity.DLT` |
| `shipper.identity.upserted` | Tracking shipper mapping | `shipper.identity.upserted-retry-tracking-shipper-identity-*` | `shipper.identity.upserted.tracking-shipper-identity.DLT` |

Saga's remaining non-blocking inputs and common-error-handler fallback use
`-retry-saga-*` / `.saga.DLT`; all Order non-blocking inputs and fallback use
`-retry-order-*` / `.order.DLT`; and Notification uses
`-retry-notification-*` / `.notification.DLT`. Non-shared legacy listeners
keep the generic `-retry-*` / `.DLT` pattern. The default-off Promotion and
Flash-sale reservation consumers use `-retry-promotion-*` / `.promotion.DLT`
and `-retry-flashsale-*` / `.flashsale.DLT`; do not enable either checkout
capability until these source-matched topics are provisioned.
Tracking's support-history consumer uses `-retry-tracking-*` /
`.tracking.DLT`. This is separate from Match's rebuildable live-location
projection, which retains its blocking-retry `.DLT` topology on the same source.
Tracking delivery-room routing has its own blocking-retry
`shipper.status-change.tracking.DLT`; Match retains
`shipper.status-change.DLT` because the two consumers own separate projections.
Analytics remains disabled by default. Before an approved activation, create
its source topics and run the provisioner with
`PROVISION_ANALYTICS_RETRY_TOPICS=true`; payment-source Analytics targets also
require `PROVISION_ORDER_PAYMENT_DLTS=true`. The default core manifest does not
require the currently orphaned `order.status-updated` source.

Order payment-event processing is disabled in the COD-first default. If an
operator enables `app.order.payment-event-processing-enabled`, provision
`payment.completed.order.DLT` and `payment.failed.order.DLT` from their source
partition counts in the same change by setting
`PROVISION_ORDER_PAYMENT_DLTS=true` on the provisioner. During the shared-topic
migration it also retains the old generic payment DLTs until their normal
retention/replay review is complete.
If a production profile changes `KAFKA_RETRY_ATTEMPTS`,
`KAFKA_RETRY_INITIAL_DELAY_MS`, `KAFKA_RETRY_MULTIPLIER`, or
`KAFKA_RETRY_MAX_DELAY_MS`, pass the same values as `RETRY_*` to the provisioner
and complete a topology review before rollout. `MATCH_RETRY_DELAYS_MS` is tied
to the fixed Match annotation and must only change with that code contract.

### Shared-source retry migration

The old generic retry/DLT topics must be drained before the last old replica is
removed. The provisioner keeps them in the manifest by default with
`PROVISION_LEGACY_SHARED_RETRY_TOPICS=true`; this does not route new-version
records to them.

1. Provision the new owner-specific targets before deploying any new replica.
2. Roll each owner normally while at least one old replica remains to consume
   its existing `-retry-*` records. This is the temporary dual-read period:
   old replicas consume old topology and new replicas consume new topology.
3. For `saga-orchestrator`, `order-service-group`, and
   `notification-service-group`, run the read-only gate below from the approved
   Kafka admin toolbox. These listeners do not set `@KafkaListener.groupId`, so
   Spring Kafka retry containers inherit the configured `ConsumerFactory`
   group (`saga-orchestrator`, `order-service-group` or
   `notification-service-group`); the gate checks those base groups, verifies
   every affected legacy retry partition has zero lag, rejects an active old
   consumer assignment, then snapshots end offsets and requires one full
   maximum retry delay without a new legacy write. The tool does not create,
   delete, reset or replay anything:

   ```bash
   KAFKA_BOOTSTRAP_SERVERS='broker-1:9093,broker-2:9093' \
   KAFKA_COMMAND_CONFIG=/run/secrets/kafka-admin.properties \
   bash scripts/verify-kafka-legacy-retry-drain.sh
   ```

   A retired group is acceptable when `kafka-consumer-groups --describe` still
   succeeds but returns no row for an empty retry topic. The verifier rejects a
   non-empty topic without a committed group offset or any write during the
   quiet window. The quiet window defaults to `KAFKA_RETRY_MAX_DELAY_MS` (10
   seconds); it may only be increased with
   `KAFKA_LEGACY_DRAIN_QUIET_WINDOW_SECONDS`. If the retiring replicas used a
   non-default retry policy, pass their exact `KAFKA_RETRY_ATTEMPTS`,
   `KAFKA_RETRY_INITIAL_DELAY_MS`, `KAFKA_RETRY_MULTIPLIER`, and
   `KAFKA_RETRY_MAX_DELAY_MS`; the gate derives the generic retry topic names
   from those values.
4. Preserve old DLT records through the normal 14-day retention and handle them
   only with the controlled replay procedure below. Do not bulk copy or delete
   them as part of this migration.
5. Record the zero-lag evidence. Only then may a later manifest run set
   `PROVISION_LEGACY_SHARED_RETRY_TOPICS=false`; it stops provisioning unused
   targets but intentionally does not delete existing evidence.

Run the disposable broker proof before promoting a new manifest:

```bash
bash scripts/verify-kafka-resilience-topics.sh
```

It creates source topics with three partitions, deliberately seeds a one-
partition/stale-retention DLT, runs provisioning twice, and verifies every
canonical retry/DLT target; it also proves that an insufficient replication
factor fails closed. It is evidence for topic metadata only; ACL grants,
consumer replay and cross-service crash windows still require the production
operator rehearsal.

Replay procedure, operator-only:

1. Page the owning service and identify the source topic, partition, offset,
   event ID, correlation ID and exception class from the DLT record.
2. Classify the error: transient infrastructure errors may be replayed after the
   dependency recovers; validation/poison or contradictory business payloads
   require correction or an incident decision and must not be blindly replayed.
3. Verify the consumer's durable idempotency receipt/inbox and payload
   fingerprint contract in `docs/system-contract-inventory.md`. In particular,
   `saga.command.update-order-status` is fenced by Order
   `saga_command_receipts`, and all Saga-to-Delivery commands are fenced by
   Delivery `delivery_inbound_receipts`. On PostgreSQL each inbox uses
   `INSERT .. ON CONFLICT DO NOTHING` to converge concurrent consumers to one
   committed receipt; both exact replays are no-ops only when the full raw
   payload fingerprint matches.
4. Use the repository-owned single-record command below. It reads exactly the
   named DLT coordinate, verifies the Spring original-topic/partition/offset,
   exception, `eventId`, and correlation identity headers, then preserves the
   application key/value/headers while publishing to the recorded original
   topic and partition. If Spring's recorded topic is one of this repository's
   standard final retry suffixes (`-retry-<owner>-<delay>`, generic
   `-retry-<delay>`, or Match `.retry-<delay>`), the command maps it back to its
   canonical source topic before publishing; unknown suffixes are never
   guessed. It defaults to dry-run and has no bulk mode. First run
   the dry-run, review its stable identifiers against the incident, then repeat
   with `DLT_REPLAY_DRY_RUN=false` only after the incident commander approves:

   ```bash
   mvn -q -pl kafka-operations-tool -am package

   export KAFKA_BOOTSTRAP_SERVERS='broker-1:9093,broker-2:9093'
   export KAFKA_COMMAND_CONFIG=/run/secrets/kafka-dlt-replay.properties
   export DLT_REPLAY_TOPIC='order.created.saga.DLT'
   export DLT_REPLAY_PARTITION=2
   export DLT_REPLAY_OFFSET=481
   export DLT_REPLAY_INCIDENT_ID='INC-2026-0813-42'
   export DLT_REPLAY_CONFIRMATION='REPLAY:order.created.saga.DLT:2:481'
   bash scripts/replay-kafka-dlt-record.sh # dry-run (default)

   export DLT_REPLAY_DRY_RUN=false
   bash scripts/replay-kafka-dlt-record.sh # one approved replay
   ```

   The Kafka principal requires read ACL on the DLT and write ACL on the
   recorded original topic; the command neither grants ACLs nor changes consumer
   offsets. It removes Spring DLT/retry diagnostic headers before publishing so
   a later failure has one new recovery history, but it keeps `eventId`,
   correlation, tracing and other application headers. It refuses a missing or
   contradictory identity header, an unsafe/looping target, a missing exact
   coordinate, or a confirmation that does not name that exact coordinate.
   Monitor the side effect, consumer lag and DLT count. Do not replay any other
   record before this single-record proof succeeds.
5. Keep an incident record containing the original and replay offsets. Exact
   replay must be a no-op; a changed fingerprint must return to the error/DLT
   path rather than overwrite a committed side effect.

Match has an additional local result-outbox boundary. If
`match_outbox_events.status=DEAD`, do not replay the original find command:
that could run GEO/reservation work again. Recover Kafka/ACL capacity, inspect
the stable `event_id` and payload, then replay the single durable outbox row
by an approved operator moving that same row from `DEAD` to `PENDING`; do
not create a new ID or payload. A `match_commands` row with `RESULT_STAGED`
is the evidence that the matching side effect must not be recomputed.

Prometheus rule `DeliveryKafkaDltIncreasing` alerts on a non-zero increase of
`delivery_kafka_events_total{event="dlt"}` over five minutes. Separately alert
when any core DLT topic's oldest record exceeds one hour without an assigned
owner; broker-age alerting is deployed with the Kafka exporter/operator because
the application does not expose topic-retention age.

The repository validates the mounted Prometheus syntax and this approved
application-level rule with:

```bash
bash scripts/verify-prometheus-resilience-rules.sh
```

It runs both syntax and a synthetic DLT-increase rule test. It deliberately
does not certify external Alertmanager routing, Kafka-exporter age metrics, or
a production SLO: those require the approved observability overlay and live
telemetry.
