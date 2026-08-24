# Execution Plan: Production Metrics With Micrometer And Prometheus

Date: 2026-07-30

## Status

Completed

## Outcome

Every backend service exposes safe, standardized Micrometer metrics for an
internal Prometheus server; the operational dashboard and runbook make slow
requests, Kafka failures, and COD settlement failures observable.

## Context

- `docs/product/overview.md` describes the order → delivery → settlement flow.
- `backend_delivery/ROADMAP_MVP_TO_PRODUCTION.md` identifies Actuator,
  Prometheus, and Grafana as missing production foundations.
- `backend_delivery/docker-compose.yml` is the local composition authority.

## Scope

In scope:

- Add Actuator and Prometheus registry dependencies/configuration to all backend
  services.
- Add low-cardinality business counters at the canonical success transitions.
- Add dashboards, Prometheus scrape configuration, and an operational runbook.
- Verify custom metrics, registry startup, scrape output, Compose parsing, and
  a Maven baseline.

Out of scope:

- Distributed tracing, alert delivery integrations, or external Grafana hosting.
- Changing public APIs or Kafka event contracts.

## Approach

1. Define a `delivery.*` metric contract with only bounded tags and make the
   Actuator Prometheus endpoint internal-only.
2. Apply the Actuator/registry dependency and common meter configuration across
   all services, relying on Boot instrumentation for HTTP, Hikari, JDBC,
   Lettuce, and Kafka client/listener metrics.
3. Instrument successful business transitions in their owning services.
4. Add internal Prometheus/Grafana Compose services, provisioning, dashboard,
   and runbook.
5. Run focused tests and runtime/configuration validation.

## Risks And Recovery

- Metrics can create high cardinality: custom tags are fixed vocabularies only;
  never include aggregate/user/token/request identifiers. Remove a custom meter
  or tag before rollout if cardinality grows unexpectedly.
- Prometheus must not become public: neither Prometheus nor service management
  ports are published by Compose. Remove the monitoring services to roll back
  local monitoring without affecting business traffic.

## Progress

- [x] Inspect current services, runtime composition, and production roadmap.
- [x] Add common metrics foundation and safe endpoint configuration.
- [x] Instrument business and Kafka error/retry/DLT counters.
- [x] Add monitoring stack, dashboard, and runbook.
- [x] Validate focused builds, custom metric/registry tests, and Compose configuration.

## Decisions

- 2026-07-30: `delivery.business.events` is the canonical custom counter with
  bounded `event` and `outcome` tags. This avoids a separate, unbounded metric
  family per aggregate or actor.
- 2026-07-30: Use Spring Boot's built-in `http.server.requests`,
  `spring.kafka.listener`, Kafka client, Hikari, JDBC, and Lettuce binders;
  configure percentiles only for HTTP server timers to bound histogram cost.

## Validation

- Focused proof: unit tests for the metric recorder and Spring Boot registry.
- Integration/runtime proof: scrape a running service's Prometheus endpoint.
- Repository-required checks: Maven baseline and `docker compose config`.

## Result

Implemented shared Actuator/Prometheus configuration for all services with an
internal management port, standardized business/Kafka counters for the core
order-to-COD path, and internal Prometheus/Grafana provisioning.

Verified on 2026-07-30:

- `BusinessMetricsTest` records the bounded custom counter tags.
- `OrderServiceApplicationTests` confirms a `PrometheusMeterRegistry` starts.
- `PrometheusEndpointIntegrationTest` starts a separate H2-backed management
  server and verifies `/actuator/prometheus` returns Prometheus text containing
  `jvm_memory_used_bytes`.
- Focused Maven suites for observability-starter, order, delivery, match,
  settlement, and API gateway passed.
- `scripts/verify-build-baseline.sh` and
  `scripts/verify-compose-config.sh` passed; the former checks that all service
  management ports are private and use readiness probes.
