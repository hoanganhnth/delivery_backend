# Execution Plan: Backend Actuator Health and Readiness

Date: 2026-07-30

## Status

Completed

## Outcome

All 17 backend applications expose sanitized Actuator health, liveness, and
readiness probes on a non-public management port. Readiness includes every
auto-configured required infrastructure dependency; Compose uses the probe for
container health without publishing management ports or creating Gateway routes.

## Context

- `docs/product/overview.md`: canonical runtime exposes only API Gateway.
- `backend_delivery/docker-compose.yml`: service ports are internal with direct
  host ports confined to `docker-compose.debug.yml`.
- `backend_delivery/docs/http-api-inventory.md`: Gateway routes are an explicit
  allow-list and internal operational endpoints must not become public routes.
- `backend_delivery/scripts/verify-*.sh`: build, Compose and API contract gates.

## Scope

In scope:

- Actuator dependency/configuration for `api-gateway` and all 16 services.
- Separate internal management port, sanitized probe responses, Docker probes,
  Compose policy validation, and operator documentation.
- Context/build checks and runtime probe verification when local dependencies
  are available.

Out of scope:

- Publishing management endpoints beyond `/actuator/health/**`.
- Adding Gateway routes for operations endpoints.
- New authentication/authorization policy for the private Compose network.

## Approach

1. Add the Spring Boot Actuator starter in each module and configure only
   health endpoints on management port `9090`; enable probes and make readiness
   aggregate all available health contributors while never returning component
   details.
2. Keep management port off host publishing: expose it only to the Compose
   network and use the image's local HTTP client for Docker healthchecks.
3. Add verifier assertions and an operations runbook, then run module tests,
   build/config/contract gates and runtime probes where feasible.

## Risks And Recovery

- A newly included health indicator can make a service not ready when its
  declared infrastructure is down. This is intentional; restore availability
  only by restoring the dependency or roll back the Actuator configuration.
- The separate port is reachable to containers on the private Compose network.
  Do not add it to `ports` or Gateway routes; removing the `expose`/healthcheck
  changes reverts the network presentation.
- Health response details are disabled so error messages and connection data are
  not emitted. Regression coverage and the Compose verifier guard this setting.

## Progress

- [x] Establish current Compose and Gateway boundary authority.
- [x] Add Actuator dependency and probe configuration across all modules.
- [x] Add Docker healthchecks and Compose verifier coverage.
- [x] Add runbook and focused regression checks.
- [x] Run full backend/runtime validation and record observed outcome.

## Decisions

- 2026-07-30: Use management port `9090`, private to the Compose network,
  because the documented canonical Compose policy publishes only Gateway and
  `docker-compose.debug.yml` is the explicit direct-port escape hatch.
- 2026-07-30: Use Actuator's `readiness.include=*` to aggregate each service's
  auto-configured mandatory contributors (JDBC, Kafka, Redis, Elasticsearch)
  without hard-coding an invalid contributor name into services that do not use
  it. Gateway has no infrastructure contributor and therefore reports only its
  readiness state.
- 2026-07-30: Explicitly enable `livenessState`/`readinessState` contributors
  and the liveness group. The Gateway HTTP probe test demonstrated that the
  generic probes flag alone did not materialize `/health/liveness` in this
  non-Kubernetes management-port test runtime.

## Validation

- Focused proof: each module test/context and Actuator auto-configuration.
- Integration or end-to-end proof: Compose health status and local internal
  `/actuator/health`, `/liveness`, `/readiness` responses; no public Gateway
  Actuator route.
- Repository-required checks: build baseline, Compose config, MVP polyrepo
  contract, and HTTP inventory if routes are changed.

## Result

Implementation and focused checks completed:

- `bash backend_delivery/scripts/verify-actuator-config.sh` PASS.
- `bash backend_delivery/scripts/verify-compose-config.sh` PASS.
- `mvn -q -pl observability-starter clean install && mvn -q -pl api-gateway
  -Dtest=ActuatorProbeEndpointTest test` PASS. The test makes HTTP requests to
  the separate management server: health/liveness/readiness are UP, the body
  has no component details, and the Gateway application port returns 404 for
  `/actuator/health`.
- `bash scripts/verify-mvp-polyrepo-contract.sh` PASS from the delivery root.

Current runtime evidence after a clean package build:

- Docker Compose's rebuilt `api-gateway`, delivery, order, restaurant, match,
  notification, settlement, saga and tracking containers are Docker `healthy`.
  Their internal `http://localhost:9090/actuator/health/readiness` endpoints
  returned only `{"status":"UP"}`; Gateway's public `:8079/actuator/health`
  returned `404`.
- A controlled local Redis stop made the Match Docker healthcheck time out while
  readiness was waiting for its mandatory Redis indicator; Redis was restarted
  immediately and Match readiness returned `UP`.
- `mvn -q -pl match-service -Dtest=MatchReadinessDependencyTest test` PASS:
  with a deliberately unavailable Redis endpoint, the actual management
  readiness endpoint returned HTTP 503 and `{"status":"DOWN"}`, with no
  component data in the response.

`mvn -q -DskipTests package` PASSes for every service. Context/startup coverage
is PASS across all service groups, including explicit User/Match/Settlement and
the remaining Search/Tracking/Livestream/Flash Sale/Analytics/Promotion groups.
The final rerun of build baseline, Compose config, HTTP inventory and the root
polyrepo contract gate all PASS. No API route was added, so the inventory was
verified as part of the baseline/contract checks rather than changed.
