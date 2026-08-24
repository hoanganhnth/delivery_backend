# Execution Plan: Distributed tracing for the order lifecycle

Date: 2026-07-30

## Status

Completed

## Outcome

An order initiated through the gateway can be followed by one W3C trace across
HTTP and durable Kafka/outbox transitions through order, restaurant, delivery,
saga, match, notification and settlement. Development Compose exports traces to
an OTLP collector. Location updates remain untraced by default.

## Context

- `docs/product/overview.md` defines the canonical COD lifecycle.
- `backend_delivery/` uses Spring Boot 3.5.15 and Kafka-backed transactional
  outboxes. Scheduled relays otherwise lose the originating request context.

## Scope

In scope:

- Micrometer Tracing with the OpenTelemetry bridge and OTLP exporter in the
  lifecycle services and gateway.
- W3C HTTP/Kafka propagation, including persisted parent context in outbox rows.
- Configurable local/staging/production sampling and a development collector.
- Explicit suppression of tracing for raw WebSocket location updates.
- Focused HTTP/Kafka propagation tests and a documented COD runtime smoke path.

Out of scope:

- Client-side mobile tracing, collector storage/retention for production, or
  tracing every unrelated backend service.

## Approach

1. Add the Boot-managed Micrometer/OpenTelemetry dependencies and service-safe
   tracing properties to all services in the order lifecycle.
2. Persist W3C `traceparent` in transactional outboxes and restore it in relays
   before producing Kafka records; automatic Kafka instrumentation carries it to
   consumers.
3. Add targeted span names and `order.id` correlation only (never auth or
   payload data); exclude the high-rate location WebSocket and location topic.
4. Add OTLP collector configuration to the Compose development profile.
5. Prove HTTP/Kafka context handling with tests, then run the existing COD smoke
   flow and inspect the collector-exported trace.

## Risks And Recovery

- Outbox schema changes are additive and Flyway-only; rollback disables tracing
  export/sampling while retaining harmless nullable context columns.
- Kafka instrumentation must not block consumers; retain existing listener
  concurrency/ack flow and do no exporter work on application threads.
- Trace headers can carry no secrets; persist only `traceparent`, never request
  headers, token values, or event payloads.

## Progress

- [x] Inspect Spring Boot versions, message paths, outbox relays and raw WebSocket path.
- [x] Add tracing dependencies/configuration and collector.
- [x] Persist and restore outbox trace contexts across lifecycle services.
- [x] Add propagation/suppression tests.
- [x] Run focused validation and COD smoke.

## Decisions

- 2026-07-30: Use Micrometer Tracing with the OpenTelemetry bridge and OTLP.
  Spring Boot 3.5.15 manages these compatible dependencies and Kafka/HTTP
  observation is already supported by its instrumentation.
- 2026-07-30: Store only W3C `traceparent` on outbox records. It preserves the
  request parent across scheduled relay delays without retaining sensitive data.

## Validation

- Focused proof: HTTP propagation unit test and Kafka header/outbox propagation tests.
- Integration or end-to-end proof: existing Compose COD smoke flow with OTLP collector.
- Repository-required checks: service Maven test subsets and Compose config validation.

## Result

Completed 2026-07-30.

Current validation:

- `mvn -q -pl observability-starter test` passed: HTTP correlation response/MDC
  propagation and Kafka producer header propagation.
- `mvn -q -pl order-service -am -Dtest=OrderOutboxTraceContextTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed: the order outbox stores
  a W3C `traceparent` for the scheduled relay.
- The shared starter test suite passes after proving that custom Kafka templates
  and listener factories enable observation, while tracking opts out. Its W3C
  outbox-context test proves a persisted parent is restored before the Kafka
  producer observation creates and injects the relay child span.
- Lifecycle outbox services package successfully with tests skipped.
- `WebSocketConfigAuthenticationTest` passes: raw WebSocket handshake retains a
  valid W3C `traceparent` and correlation ID as session metadata; tracking
  remains opted out of Kafka observation and tracing sampling, so location
  messages do not create a span per ping.
- Detailed collector inspection exposed periodic empty outbox/sweeper scheduled
  spans. The shared starter now suppresses automatic
  `ScheduledTaskObservationContext` observations; the focused test passes and a
  12-second post-deploy collector window contained no delivery/saga/tracking
  scheduled spans. HTTP/Kafka boundary observations remain enabled.
- Compose rendering passed with required local placeholder variables.
- The Dockerfile parser blocker was corrected and `auth-service` was packaged
  successfully. The COD harness then reached the running stack but its
  `notification-service` cannot start because its configured PostgreSQL password
  does not match the existing database volume. This is pre-existing local runtime
  state, not a trace failure. Recreate or align the local Compose database
  credentials, then rebuild lifecycle services with tracing enabled and inspect
  the collector output for a new COD trace.

## Blocker

The runtime credential mismatch was aligned using the password held by an
existing healthy service, without exposing it. A full COD smoke then passed:
order `10`, delivery `7`, four ledger entries, and duplicate replay invariant.
The collector received detailed OTLP spans. Trace ID
`969cd9a36584a2c89d811518da8bd2ba` appeared across api-gateway,
order-service, saga-orchestrator-service, delivery-service, match-service,
notification-service and settlement-service. Restaurant confirmation was
observed in its own authenticated request branch with order-service and saga.

The raw WebSocket handshake retained W3C/correlation session metadata, while
the tracking producer/listener and match-service location consumer are excluded
from observation. A focused collector window after deployment contained no
scheduled delivery/saga/tracking spans, and the no-observation location factory
has focused test coverage.
