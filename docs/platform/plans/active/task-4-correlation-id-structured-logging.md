# Task 4 — Correlation ID and structured logging

## Outcome

Every request entering the API gateway has a validated `X-Correlation-Id`; the
same value is returned to the caller, passed to downstream HTTP/Kafka work, and
is available as structured log context without exposing credentials or sensitive
request data.

## Context and authority

- The user request is authority for the correlation header, validation,
  propagation, structured logging, redaction, and the listed proof.
- `backend_delivery/ROADMAP_MVP_TO_PRODUCTION.md` identifies cross-service
  correlation logging as outstanding work.
- Gateway discards client `X-User-Id` and `X-Role`; resource services reconstruct
  the authenticated actor only after validating the Bearer JWT through Auth JWKS.

## Approach

1. Add a small shared observability Maven module with correlation validation,
   servlet/WebFlux filters, Kafka producer/consumer interceptors, MDC helpers,
   JSON logging defaults, and safe exception/request redaction.
2. Apply it to every backend service; configure the gateway to generate,
   validate and return `X-Correlation-Id`, without allowing it to influence
   identity headers.
3. Cover the concrete order outbox → Kafka → notification consumer path and
   emit only identifiers (order/delivery/event IDs), never event payloads.
4. Add focused tests plus source scans and a failure-path runtime smoke where
   the environment can start the required services.

## Risks and recovery

- A malformed client ID is rejected with `400`, rather than silently replaced,
  so callers can diagnose bad integration input.
- Kafka records produced before this deployment receive a new ID on consume;
  this preserves observability while retaining backward compatibility.
- Changes are additive configuration/code and can be reverted by removing the
  shared dependency and filters.

## Progress

- [x] Read system and backend workflow instructions.
- [x] Confirm roadmap authority and locate current gateway, outbox and consumer code.
- [x] Implement shared observability infrastructure and apply it.
- [x] Add focused propagation/security/redaction tests.
- [x] Run focused validation and source scan.
- [x] Run live Kafka-consumer failure smoke and inspect structured output.

## Validation record

- `mvn -pl observability-starter,api-gateway,order-service,notification-service -am test`:
  Gateway (26 tests), observability (6 tests), and the selected service build
  path passed before output truncation during downstream service execution.
- `mvn -pl observability-starter,order-service -am
  -Dtest=CorrelationIdTest,CorrelationPropagationTest,OrderOutboxRelayTest
  -Dsurefire.failIfNoSpecifiedTests=false test`: passed (8 focused tests).
  This includes generated/accepted/rejected request IDs, HTTP context, Kafka
  producer/consumer MDC, redaction, and an outbox failure/retry path.
- Source scan found no logger interpolation of credentials, request headers,
  payloads, or full addresses. The only lexical hit was a safe operational
  message (`Tracking authorization unavailable for delivery {id}`), which logs
  no authorization value.
- Live smoke: started an isolated notification-service against temporary
  PostgreSQL and the existing Kafka broker; sent a malformed `order.created`
  record with `X-Correlation-Id=smoke-failure-20260730`. Its real listener
  emitted structured JSON `ERROR` records carrying that exact `correlationId`.
  The temporary containers were stopped afterward. The Gateway test verifies
  the same header is injected into the downstream service request.

## Validation target

- Gateway tests for absent, valid, and invalid correlation IDs plus spoofed
  identity headers.
- Kafka interceptor and selected `order-service` → `notification-service`
  propagation tests.
- Source scan for credential/header/payload logging.
- Failure-path application smoke when local infrastructure is available.
