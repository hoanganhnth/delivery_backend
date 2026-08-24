# Execution Plan: Production-like synthetic delivery sandbox

Date: 2026-08-22

## Status

Active — local orchestration, static safety gates and disposable Docker smoke
are implemented; multi-order/HA/device evidence remains deferred.

## Outcome

Cho phép người phát triển chạy một stack disposable có dữ liệu/actor mock nhưng
đường đi nghiệp vụ giống production: Gateway, microservices, Kafka/Saga,
H3/rolling matching, batch offer, Delivery, Tracking, Settlement và Scenario
Lab observer. Sandbox không thể vô tình dùng project, volume hoặc target
production/canonical.

## Context

- `docs/WORKFLOW.md` — workflow và completion evidence.
- `docs/product/overview.md` — service map và order lifecycle.
- `docs/system/simulator/README.md` — simulator safety boundary.
- `backend_delivery/docs/runbooks/production-like-sandbox.md` — operator
  runbook.
- `backend_delivery/docker-compose.isolated-e2e.yml` — existing isolation
  boundary.
- `backend_delivery/simulator-service/` — real Gateway-driven runner.

## Scope

In scope:

- Sandbox Compose overlay with project-scoped names, dynamic loopback ports and
  H3/batch flags.
- Run-scoped secrets, synthetic fixture seed and scenario generation.
- Safe up/run/status/down commands and static Compose contract gate.
- Gateway routes and runner support for the additive batch recovery/accept
  contract.

Out of scope:

- Production deployment, real credentials, real customer data or third-party
  payment providers.
- Claiming HA/load/chaos/mobile-device proof from a single Docker host.
- Full multi-order batch fixture provisioning; current ready scenario is a
  one-item batch, while the core optimizer remains testable separately.

## Approach

1. Reuse the canonical Compose service graph and existing isolated overlay.
2. Add a sandbox overlay that removes fixed identities, binds only Gateway and
   simulator to loopback dynamic ports, and enables matching/batch flags.
3. Extend the runtime startup verifier with explicit extra overlays and a
   post-Gateway simulator health gate.
4. Generate run-scoped secrets/state, package artifacts, start the stack and
   seed actors through public Gateway contracts plus the documented local COD
   fixture seam.
5. Generate redacted-on-screen scenario files, run them through the Scenario
   Lab API, and provide status/cleanup guardrails.
6. Validate static shell/Compose contracts and run focused JVM tests; execute
   disposable runtime smoke when Docker is available.

## Risks And Recovery

- **Canonical stack collision:** fixed names/ports are removed and project,
  network and data volumes must carry `delivery_sandbox_`; down refuses anything
  else.
- **Target leakage:** simulator allows only `api-gateway` inside the isolated
  network and rejects non-local/prod-like hosts.
- **Batch client mismatch:** the runner probes `current-batch` first and falls
  back to the legacy offer endpoint; set the two batch capability flags false
  for a single-offer comparison.
- **Cold start/resource pressure:** startup has an explicit timeout; use
  `SANDBOX_SKIP_BUILD=true` only with already-verified artifacts and purge the
  run with `SANDBOX_PURGE=true`.
- **Runtime failure:** `sandbox-up.sh` captures focused logs and tears down only
  the disposable project; state remains available unless explicitly deleted.

## Progress

- [x] Add sandbox Compose overlay and static safety contract.
- [x] Add extra-overlay/simulator support to runtime startup verifier.
- [x] Include the routing service in the application startup wave so batch ETA
  planning does not silently fall back to an absent Eureka registration.
- [x] Add run-scoped secret generation, synthetic seed integration and scenario
  generator (simulated happy/reject/no-shipper plus HUMAN_ORDER mode).
- [x] Add safe run/status/down scripts and operator runbook.
- [x] Route additive batch endpoints through Gateway and teach runner to accept
  a one-item batch offer.
- [x] Execute full Docker startup + happy/reject/no-shipper runtime smoke.
- [ ] Add multi-order fixture generator and durable ledger observer in a later
  phase.

## Decisions

- 2026-08-22: Keep canonical Compose defaults unchanged; sandbox flags are
  overlay-only so a normal local or production-like deployment cannot
  accidentally enable experimental batch behavior.
- 2026-08-22: Use dynamic loopback host ports and run-scoped volumes instead of
  fixed port/container identities to allow concurrent developer sandboxes.
- 2026-08-22: Store actor tokens only in mode-0600 run state/scenario files;
  Scenario Lab snapshots continue to redact them.
- 2026-08-22: Enable the batch capability in the sandbox and support a one-item
  batch first; multi-order grouping remains a separate fixture concern.
- 2026-08-23: The sandbox Saga matching cutoff is explicitly overridden to one
  minute for synthetic no-shipper runs; canonical Compose keeps the five-minute
  default. A clean Gateway-driven run reached `SHIPPER_NOT_FOUND` in both Order
  and Delivery, and the Match PostgreSQL row/outbox converged exactly once.

## Validation

- Focused proof: `bash -n` for all sandbox/startup/seed scripts; scenario
  generator exercised for all three templates.
- Static Compose proof: `bash scripts/verify-sandbox-config.sh` passed; rendered
  overlay verified dynamic loopback ports, isolated names and H3/batch flags.
- JVM proof: `GatewayRouteSecurityTest` passed; `mvn -q -pl simulator-service
  -am test` passed (11 tests); Match, Delivery, Saga and Settlement focused
  modules compile with tests skipped.
- Integration or end-to-end proof: full isolated startup passed on
  `web_e2e_20260823194848_61552`; happy and restaurant-reject scenarios passed,
  and no-shipper run `sim-a75d1582-6546-4cec-9814-10cc7c36b6e0` passed with
  Order/Delivery `SHIPPER_NOT_FOUND`. The database evidence was one sent
  `shipper.not-found` outbox row and one `EXPIRED` dispatch pool item.
- Repository-wide HTTP/docs gates remain pre-existingly stale in this dirty
  workspace (`docs/http-api-inventory.md` and the offline reference bundle); the
  sandbox-specific Compose and JVM gates above are green.

## Result

Keep active for multi-order fixture work and stronger replay/HA evidence. The
single-host one-item smoke is complete, but it does not prove provider,
multi-replica, load/chaos or mobile-device behavior.
