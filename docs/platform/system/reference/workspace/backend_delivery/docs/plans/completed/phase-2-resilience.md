# Execution Plan: Phase 2 Resilience

Date: 2026-07-30

## Status

Completed

## Outcome

The Gateway rejects abusive HTTP requests with an observable, configurable Redis-backed policy. Core synchronous calls are bounded by circuit breakers. Core Kafka consumers have consistent non-blocking retry/DLT behavior and replay-safe side effects.

## Context

- `ROADMAP_MVP_TO_PRODUCTION.md` §2 lists rate limiting, circuit breakers, retry/DLQ and idempotency as production gaps.
- `docs/system-contract-inventory.md` is the Kafka producer/consumer and replay source of truth.
- Gateway routes and authentication are defined in `api-gateway/.../GatewayRouteConfig.java` and `JwtAuthenticationFilter.java`.
- Existing service Kafka configurations provide the migration baseline, but differ by service.

## Scope

In scope:

- Redis-backed Gateway rate limits, standard 429 envelope, metrics and operational runbook.
- Circuit breaking for audited synchronous internal calls, without fallback success semantics.
- Standard retry/DLT metadata, metrics, retention/provisioning and replay procedure for core topics.
- Idempotency audit, durable dedup where missing, and updated Kafka contract inventory.

Out of scope:

- Per-message throttling of raw shipper-location WebSocket traffic.
- New public request headers or client-contract changes.
- Product features outside the active COD MVP surface.

## Approach

1. Add Gateway route-group filters after authentication where needed, using IP keys for public routes and JWT subject keys for protected routes.
2. Add a focused error writer and counter for rejected requests; keep Redis failure behavior explicit by route group.
3. Audit each synchronous client, configure timeouts and Resilience4j around the call boundary only, and propagate unavailable dependencies through the established error path.
4. Make retry/DLT classification and headers consistent across Order, Restaurant decisions, Delivery, Saga, Notification and Settlement; provision topics and document replay.
5. Add or extend transactional idempotency receipts for any core consumer lacking a stable identity/fingerprint proof, then run focused and runtime evidence.

## Risks And Recovery

- An overly strict quota can reject legitimate traffic. All values are environment properties and can be raised without a code rollback.
- Redis outage behavior is intentionally asymmetric: auth/mutation/admin fail closed; catalog/authenticated reads fail open. Metrics distinguish rejected from bypassed requests.
- DLT replay can repeat side effects. Replay only after inspecting metadata and only with the consumer's durable dedup proof; pause the consumer and use a fresh, documented operator group where required.
- Circuit breakers must not wrap database transactions. Place them at outbound HTTP client boundaries and propagate failure.

## Progress

- [x] Audited existing Gateway routes, auth identity propagation, synchronous clients, and Kafka consumer configurations.
- [x] Product/operational baseline approved by user on 2026-07-30.
- [x] Implement and test Gateway rate limiting (unit/context proof and real-Redis boundary/concurrency/TTL-reset proof complete; public-auth runtime quota smoke passed with ten 400 downstream validation responses followed by standard 429 envelopes. Redis-outage smoke exposed that the client call needed a bounded timeout; a configurable 500 ms timeout plus focused fail-open/fail-closed timeout proof was implemented. Rebuilt Gateway runtime smoke then returned catalog 200 in 592 ms while Redis was down and public auth returned the standard 503 envelope in 519 ms; Redis and Gateway were healthy after recovery).
- [x] Implement and test circuit breakers (Auth→User plus all audited Order→Restaurant, Restaurant→Order, Tracking→Delivery and Match→Settlement boundaries have 2s configurable timeouts, Resilience4j circuit state metrics and closed/open/half-open focused proof. A Gateway runtime Auth→User outage smoke opened the circuit after its 20-call window, rejected a further call without an outbound attempt, then recovered through half-open after User restart; the exact registration retry created one account/profile only).
- [x] Standardize retry/DLT, replay runbook and metrics (all scoped Notification, Saga, Order restaurant-decision/Saga-command, Delivery Saga-command, and Settlement consumers use provisioned non-blocking retry topics and preserve poison exceptions; recoverers emit retry/DLT metrics and classify `IllegalArgumentException` no-retry; Delivery rethrows infrastructure errors instead of ACKing a fabricated failure; the 14-day provisioning script, runbook and alert exist. Raw-JSON listeners direct retry/DLT publication through `StringSerializer`, preventing per-hop JSON quoting. Unit DLT metadata proof and live retention, transient traversal, consumer-restart and replay-safe Notification rehearsals pass).
- [x] Close idempotency gaps and update the contract inventory (Notification Order/Delivery consumers use producer stable event ID as their durable dedup key, with live retry/replay and restart proof. Saga has a durable inbound `eventId` receipt/fingerprint in the same transaction as state/outbox; PostgreSQL/Kafka exact replay created one receipt/Saga/outbox and conflicting replay reached DLT without changing any count. Delivery exact Saga create-command replay was also rehearsed against PostgreSQL/Kafka, yielding one `create_event_id` delivery and one created-result outbox row. A PostgreSQL two-writer receipt race committed once and rejected the concurrent duplicate key. The contract inventory now contains a per-core-consumer identity/dedup/proof audit.)
- [x] Run Compose, contract and runtime replay evidence.

## Decisions

- 2026-07-30: Approved quota baseline: public auth 10/min/IP; public catalog 120/min/IP; authenticated read 300/min/subject; mutation 30/min/subject; admin 120/min/subject.
- 2026-07-30: Approved Redis behavior: fail closed for auth, mutation and admin; fail open for catalog and authenticated reads.
- 2026-07-30: Raw location WebSocket messages are not rate limited. New connections are capped at 10/min/subject.
- 2026-07-30: DLT retention is 14 days; replay is operator-only through a runbook.
- 2026-07-30: Circuit defaults are 2s timeout, 50% failures in a 20-call window, 30s open duration and 5 half-open calls.
- 2026-07-30: Synchronous-call inventory: Order→Restaurant (`OrderValidationService`, `CheckoutPreviewService`); Restaurant→Order (`OrderEligibilityClient`, `OrderDecisionEligibilityClient`); Tracking→Delivery (`DeliveryTrackingAccessClient`); Match→Settlement (`SettlementEligibilityClientImpl`). Delivery and Saga core paths currently rely on Kafka/internal state and have no direct HTTP client in this audit. Implement circuits at those client boundaries only.

## Validation

- Focused proof: Gateway boundary/key/Redis-failure tests PASS on 2026-07-30; context and route-security tests PASS. `RedisFixedWindowRateLimitStoreIntegrationTest` ran against Testcontainers Redis on 2026-07-30: exactly 10 of 40 concurrent requests were allowed and the fixed-window counter reset after TTL. Authenticated/admin key selection is covered by focused filter tests; runtime public-auth and Redis outage smoke are recorded below.
- Runtime Gateway proof: on 2026-07-30, twelve public-login requests through local Gateway yielded ten downstream 400 validation responses followed by two standard 429 rate-limit envelopes. The initial Redis-down smoke exposed unbounded Redis wait; after `RATE_LIMIT_REDIS_TIMEOUT_MS` was added (500 ms default), Redis-down runtime smoke returned public catalog 200 in 592 ms (fail-open) and public auth standard 503 in 519 ms (fail-closed). The Gateway was run with the required secrets overlay and Gateway/Redis were healthy after recovery.
- Contract gates: `bash scripts/verify-compose-config.sh` and `bash scripts/verify-build-baseline.sh` PASS on 2026-07-30 after the resilience changes.
- Focused proof: Auth→User circuit closed/open/half-open and existing auth failure semantics tests PASS on 2026-07-30. On 2026-07-30, focused client regression plus circuit state tests passed for Order→Restaurant, Restaurant→Order, Tracking→Delivery, and Match→Settlement; failure test inputs cover both timeout and 5xx error classes. All circuits use the approved 2s/50%-of-20/30s-open/5-half-open defaults and emit `delivery.circuit.state_transition`.
- Runtime circuit proof: on 2026-07-30, with a temporary local public-auth quota override, User service was stopped and 21 `POST /api/auth/register` requests were sent through Gateway for the same test identity. Auth logs show the outbound `ResourceAccessException` path through `AuthUserCircuitBreaker` and then `CallNotPermittedException` after the 20-call window opened; no false success was returned. After User restart and the 30-second open duration, the retry succeeded through half-open and database reads showed exactly one auth account (`id=74`, `user_id=74`) and one User profile (`auth_id=74`). The Gateway quota override was removed and Gateway returned healthy.
- Operations: `docs/runbooks/resilience-operations.md` records approved Gateway adjustment and controlled DLT replay procedures. `scripts/provision-kafka-resilience-topics.sh` explicitly provisions core DLT topics with 14-day retention (RF3 default); retry/DLT counters use `delivery.kafka.events{event=retry|dlt}`. Prometheus `DeliveryKafkaDltIncreasing` alerts on counter growth in five minutes. Kafka retry-topic migration and runtime rehearsals are recorded below.
- Focused proof: `delivery-service` Kafka configuration and shipper-offer/idempotent command tests PASS on 2026-07-30 after transient Delivery command failures were changed to rethrow for retry/DLT ownership.
- Focused proof: Notification consumer contract, ACK safety, vocabulary, and durable dedup tests PASS on 2026-07-30 after Order/Delivery notification identities moved from aggregate/status keys to stable producer `eventId` keys.
- Focused proof: Notification poison-payload listener tests PASS on 2026-07-30 and now prove malformed records remain unacknowledged as `IllegalArgumentException`, enabling the configured no-retry DLT classification.
- Focused proof: Notification retry-topic annotation/configuration proof PASS on 2026-07-30; the operator provisioning script includes the three bounded retry topics for each of its core source topics.
- Focused proof: Saga Kafka config, listener topic, acknowledgment, poison handling, and retry-topic class configuration tests PASS on 2026-07-30; the operator script provisions retry topics for all Saga source topics.
- Focused proof: Order restaurant decision/Saga-command listener, Kafka config, acknowledgment, and retry-topic tests PASS on 2026-07-30; malformed Saga commands remain unacknowledged as poison records.
- Focused proof: Delivery command validation/Kafka configuration tests PASS on 2026-07-30 after Saga commands gained retry-topic configuration; malformed source commands are poison while correlated business refusals retain their durable failure-outbox + ACK behavior.
- Focused proof: Settlement delivery-completed idempotency listener tests PASS on 2026-07-30 after financial poison payloads were preserved as `IllegalArgumentException` and the consumer gained retry-topic configuration; financial receipt/ledger transaction and after-commit ACK logic remain unchanged.
- Focused proof: Order standard DLT recoverer contract test PASS on 2026-07-30: original topic/partition/offset, exception FQCN, eventId and correlationId headers are present on the published DLT record.
- Runtime broker proof: on 2026-07-30, Compose Kafka accepted `order.created.DLT` provisioning through its internal advertised endpoint. Reapplying `retention.ms=1209600000` and `cleanup.policy=delete` showed as dynamic topic configuration, proving the operator script's new reconciliation behavior for already auto-created topics.
- Retry serializer and replay proof: on 2026-07-30, focused Notification, Saga, Order, Delivery and Settlement Kafka tests passed after raw-JSON listeners were bound to a dedicated `retryKafkaTemplate` using `StringSerializer`; Notification configuration tests assert this serializer and its retry annotation. In a live broker rehearsal, Notification ran with a temporary 1-second JDBC acquisition timeout while PostgreSQL was stopped. Event `66666666-6666-6666-6666-666666666666` appeared as the same JSON object in `order.created.rehearsal`, all three retry topics, and its DLT; listener logs show the expected transient JDBC failures at every hop and no JSON-string deserialization failure. After PostgreSQL recovery, replaying that source event twice created exactly one `notifications` row with deduplication key `order-created:66666666-6666-6666-6666-666666666666`. The temporary timeout override was removed and Notification/PostgreSQL were healthy afterward.
- Consumer-restart proof: on 2026-07-30, Notification was stopped, event `77777777-7777-7777-7777-777777777777` was published to the isolated source topic, then Notification restarted and created one durable row. After a second Notification restart, replaying that exact event logged `Skipping completed duplicate notification event` and the PostgreSQL count stayed one. Notification was healthy afterward.
- Saga receipt proof: on 2026-07-30, rebuilt Saga applied Flyway V4 to PostgreSQL. With its relay temporarily disabled only for an isolated source topic, the same `order.created` event `99999999-9999-9999-9999-999999999999` was published twice and PostgreSQL showed exactly `1` inbound receipt, `1` Saga instance and `1` command outbox row. A same-ID payload conflict left those counts unchanged and arrived in the source DLT. Saga was then restarted with normal topic/relay configuration and healthy status.
- Delivery command proof: on 2026-07-30, Delivery consumed a valid create-delivery command with stable event ID `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` twice from an isolated source topic while its relay was temporarily disabled. PostgreSQL showed exactly one delivery with that `create_event_id` and exactly one `DELIVERY_CREATED_RESULT` outbox row for that delivery. Delivery was restored to normal topic/relay configuration and healthy status.
- Integration proof: PostgreSQL dedup/concurrent replay and Kafka restart/replay rehearsal.
- Repository-required checks: module test suites, Compose configuration and contract gates.

## Result

Complete. PostgreSQL two-writer receipt race proof on 2026-07-30 held the first
receipt transaction open, then attempted the same key concurrently: the first
writer committed, the second received the primary-key violation, and the final
row count was exactly one. Kafka retries a concurrent loser, which then observes
the committed exact receipt as a no-op.
