# Backend Phases 1–6 Integration and Quality Gates

## Goal

Create one reviewable integration branch containing the completed Phase 1–6
backend refactors, with the documented ports-and-adapters boundaries wired at
runtime, every domain/application module enforcing at least 85% line and branch
coverage, and executable evidence that distinguishes unit, adapter, host, and
environment-dependent tests.

Phase 7 is explicitly out of scope. Its running worker, worktree, branch, and
orchestrator ledger entries must remain untouched.

## Current evidence and constraints

- `main` contains the Phase 6 delivery/match merge but does not contain the
  Phase 1, 4, or 5 branches.
- The orchestrator marked some tasks `DONE` even when the latest attempt was
  `NEEDS_CONTEXT` or `FAILED`; task status is evidence to reconcile, not proof
  of implementation.
- The architecture decision requires every `domain` and `application` module
  to enforce 85% line and branch coverage. The current build parent defaults to
  `0.0`, so the verifier and JaCoCo checks must be aligned.
- Socket binding and Mockito dynamic-agent attachment may be unavailable in the
  current sandbox. Those failures must be recorded separately from production
  assertions and retried with the supported JVM flag where possible.

## Architecture

The integration branch is created from the current backend `main` in an
isolated worktree. Phase branches are integrated in dependency order:

1. Phase 1 Restaurant/platform modules.
2. Phase 2 routing contracts/client adoption.
3. Phase 3 event contracts, Kafka starter, and service adoption.
4. Phase 4 User/Auth extraction and host migration.
5. Phase 5 Web BFF/Shipper extraction and host migration.
6. Phase 6 Delivery/Match extraction and host migration.

Existing HTTP routes, response envelopes, Kafka topics/payloads, database
ownership, transaction boundaries, retry/DLT policy, and defaults remain
unchanged. New application APIs expose commands/results/ports; infrastructure
owns Spring/JPA/HTTP/Kafka adapters; executable services own controllers,
listeners, schedulers, and configuration wiring.

Where a service has no persistence (for example Routing), the infrastructure
module contains the provider adapter and configuration but does not invent JPA
entities or migrations. Where a host migration is not representable by the
current application API, the API is expanded from the existing controller
behavior and protected by compatibility tests before the controller is
rewired.

## Workstreams

### Integration and source hygiene

- Merge or cherry-pick only committed Phase 1–6 changes into the integration
  branch, preserving the original branches and Phase 7 worktree.
- Bring any accepted but uncommitted Phase 4/5 changes into explicit commits
  after compiling and testing them.
- Resolve conflicts by preserving the main-branch public contract and the
  phase-specific tests; no broad unrelated cleanup is allowed.

### Boundary and runtime wiring

- Add missing 85/85 properties to every domain/application POM and keep
  application-api modules threshold-free when they contain interfaces/records
  only.
- Move behavior out of application-api types that the boundary verifier rejects;
  use top-level interfaces and empty-body records for commands/results/ports.
- Create a real application module for Web BFF behavior or document and test a
  narrow compatibility exception; the preferred result is framework-free
  application services in `modules/web-bff/web-bff-application`.
- Finish Delivery host migration by defining controller-facing application
  commands/results and adapters for each existing route, then replace direct
  dependencies on legacy service classes with inbound ports.
- Keep Match's existing port boundary and add missing coverage/tests rather than
  changing its public route.
- Complete Notification's delivery-status contract migration only after the
  shared event contract carries every required identity field; add producer,
  consumer, golden serialization, replay, and DLT compatibility tests together.

### Test and coverage gates

- Use TDD for every uncovered branch or boundary: add a failing focused test,
  verify RED, implement the smallest change, verify GREEN, then run the module
  gate.
- Enforce 85% line and branch in all domain/application modules, including
  User, Auth, Web BFF, Shipper, Delivery, Match, and Restaurant.
- Add tests for null/invalid input, authorization, idempotent/replay paths,
  provider failures, compatibility mapping, and each newly wired controller
  branch. Do not count generated DTO code as proof of business coverage.
- Run focused module `clean verify`, host tests with
  `-Djdk.attach.allowAttachSelf=true` when Mockito requires it, the architecture
  verifier, HTTP inventory, and a reactor compile/test command.
- Run socket-dependent integration tests only when the environment permits
  binding; otherwise report them as unverified rather than converting them to
  false passes.

### Orchestrator status reconciliation

No Phase 7 ledger mutation is allowed. For old phases, record a separate review
matrix mapping each task to commit/worktree evidence, test result, coverage
result, and remaining concern. A task is considered ready only when its code is
committed, its focused proof passes, its architecture boundary passes, and its
parent review reason matches the evidence.

## Acceptance criteria

1. The integration branch contains the intended Phase 1–6 source and test
   changes; `git status` is clean after commits.
2. No Phase 7 branch, worktree, process, or ledger row is changed.
3. `scripts/verify-module-boundaries.py` passes.
4. Every domain/application module either passes 85% line and branch coverage
   or is explicitly interface-only and excluded by the verifier with a reason.
5. Delivery, Match, User, Auth, Web BFF, Shipper, Restaurant, routing-client,
   contracts, and Kafka focused tests pass with zero failures/errors; remaining
   environment-blocked tests are named with their cause.
6. Delivery controllers no longer depend directly on legacy application
   services, and Notification's delivery-status listener uses the shared
   contract without losing required identity fields.
7. Public route/event/schema compatibility tests pass, including serialization,
   authorization, replay/idempotency, and retry/DLT behavior relevant to the
   moved code.
8. The final report separates verified facts, unresolved external-environment
   proof, and any intentionally deferred product decision.

## Non-goals

- Implementing or reviewing Phase 7.
- Deploying, pushing, or changing production infrastructure.
- Redesigning unrelated business behavior, database schemas, pricing, retry
  policies, or public API contracts.
- Claiming aggregate backend coverage from per-module reports without a fresh
  executable measurement.

