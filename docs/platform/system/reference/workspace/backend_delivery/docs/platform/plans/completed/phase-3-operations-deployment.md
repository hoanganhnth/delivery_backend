# Execution Plan: Phase 3 Operations And Deployment

Date: 2026-07-30

## Status

Completed

## Outcome

The backend resolves internal services by canonical logical name through a
registry, configuration is versioned and centrally supplied without secrets,
all four repositories have enforceable CI checks, and operators can roll out or
roll back safely using genuine readiness signals.

## Context

- The roadmap requires completing Eureka and centralized configuration:
  `backend_delivery/ROADMAP_MVP_TO_PRODUCTION.md` §2.3.
- Gateway has a commented Eureka dependency but currently receives 15 static
  `APP_*_SERVICE_URI` values from Compose. Synchronous service-to-service calls
  also use static Docker-DNS URLs.
- The platform contract freezes the Gateway as the sole public application
  boundary; management ports and service ports remain internal.
- Existing worktree changes are unrelated Phase 2 work and are preserved.

## Scope

In scope:

- Canonical local, staging, and production discovery/configuration/secrets
  topology; service naming, registration metadata, migration and rollback.
- Discovery and config-server implementation for active MVP services only.
- Compose validation, fail-fast and operational runbooks.
- CI workflows for backend, web, Flutter, and shipper repositories.
- Rolling/canary health-check and COD smoke/rollback operational contracts.

Out of scope:

- Enabling hidden payment, promotion, flash-sale, analytics, or livestream
  capabilities.
- Selecting a cloud vendor or provisioning external cloud/Kubernetes accounts.
- Publishing service or management ports other than the Gateway public port.

## Approach

1. Establish the architecture decision, central service-name registry and
   operator topology. Use Eureka in local Compose and production only behind
   private networking; use Kubernetes-native discovery only as a future
   replacement with an explicit migration decision.
2. Add the config/discovery services and migrate active service bootstrap and
   Gateway routing to discovery. Keep a static-routes Compose overlay as an
   explicit, tested rollback path.
3. Keep secrets out of the config repository. Compose injects local Docker
   secrets; staging/production use platform-managed secret injection. Document
   rotation, auditing, revocation and JWT key overlap.
4. Add repository-local CI gates and scripts that reject direct public service
   ports, static production Gateway routes, required-config omissions, and
   contract drift.
5. Prove rendered topology, focused route/registration checks, restart and
   static rollback; run runtime Compose and COD smoke when Docker is available.

## Risks And Recovery

- A registry outage must not turn into a public-boundary bypass. Gateway routes
  only through discovered healthy instances; rollback is the versioned static
  route overlay while retaining the same Gateway-only ingress.
- A bad config must not be hot-applied during a transaction. Config is
  immutable per process; rollout a new config label and restart/redeploy a
  service. Revert the label/image for rollback.
- Secret exposure is irreversible. Do not print or commit real secret values;
  rotate any value found in source control before deployment.
- Docker/runtime validation may be unavailable locally. Static verifier and
  focused tests remain mandatory; runtime proof is recorded explicitly if the
  daemon is unavailable.

## Progress

- [x] Inspect the current static URL, secret, Compose and CI state.
- [x] Record canonical topology and security decision.
- [x] Implement discovery and centralized configuration foundations.
- [x] Move Gateway and active internal HTTP calls to logical service names.
- [x] Add secret-injection, validation, rotation and rollback artifacts.
- [x] Add/enforce all-polyrepo CI pipelines.
- [x] Run final static checks and the complete discovered-topology COD smoke.

## Decisions

- 2026-07-30: Use Eureka for the requested discovery rollout. It is the
  roadmap-selected mechanism and gateway already uses Spring Cloud; do not
  introduce Consul or Kubernetes discovery into the same migration.
- 2026-07-30: Use Spring Cloud Config with a versioned configuration repository
  and immutable process configuration. Config refresh is deliberately not
  enabled; a controlled restart gives transaction safety and deterministic
  rollback.
- 2026-07-30: Use Docker secrets for local Compose and an external secret
  manager injected through Kubernetes/managed-platform identity for
  staging/production. This avoids encoding an unchosen cloud vendor or secrets
  in the config repository.
- 2026-07-30: Spring Config Data must receive `SPRING_CONFIG_IMPORT` directly
  from Compose. Indirect interpolation through `CONFIG_SERVER_IMPORT` is not
  available early enough during Config Data bootstrap in all service images.

## Validation

- Focused proof: `runtime-platform-starter` required-secret test passed;
  `scripts/verify-build-baseline.sh`, `scripts/verify-compose-config.sh`, and
  `scripts/verify-secrets.sh` passed. The full 22-module Maven reactor package
  passed with tests skipped. An earlier aggregate test run was blocked by the
  pre-existing Mockito inline self-attach limitation in this JDK/sandbox,
  outside this Phase 3 surface.
- Client gates passed: `delivery_web` `npm run verify` (35 tests),
  `shipper_app2` `npm run verify` (97 tests), and `delivery_app` `fvm flutter
  analyze` plus `fvm flutter test` (192 tests).
- Integration proof: Config Server and Discovery Server were healthy; Config
  Server served Gateway logical-route config; Gateway and all active MVP
  services were `UP` in Eureka with readiness metadata. Auth restart/re-register
  was observed. Gateway -> Auth through discovery and the static-routes recovery
  overlay both returned Auth's expected validation `400` response.
- End-to-end proof: on 2026-07-30, `SEED_SKIP_OFFLINE_PREVIOUS_SHIPPERS=true
  bash scripts/verify-mvp-cod-flow.sh` passed against the discovered topology:
  order `11`, delivery `9`, raw WebSocket participant authorization and location
  propagation, four canonical settlement ledger entries, and an idempotent
  `delivery.completed` replay. The skip flag was necessary only because the
  retained local test shipper profiles had already been verified offline; it
  avoids consuming the public-auth per-IP test quota while preserving the
  Gateway-only route and all production defaults.

## Result

Completed 2026-07-30. Active MVP services now discover each other using canonical
Eureka names, receive immutable centralized configuration without secrets, and
retain a private static-routes recovery overlay that keeps the Gateway-only
public boundary intact. Docker secret files provide local injection, while the
runbooks define workload-identity secret management, rotation, and JWT key
overlap for staged production rollout. All four repositories have CI gates, and
the operator rollout/rollback runbook has readiness, migration, drain, smoke,
and rollback conditions. The originally observed concurrent BuildKit
unknown-blob export was an infrastructure failure during a bulk build, but did
not prevent the subsequently healthy discovered stack and completed COD smoke.
