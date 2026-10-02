# 0002 Problem-First Modular Business Architecture

Date: 2026-09-21

Layout amendment approved: 2026-10-02

## Status

Accepted

## Context

Backend services currently mix HTTP/Kafka adapters, orchestration, business
rules and persistence in executable modules. Shared concerns are copied between
services, while broad refactoring without first stating the business problem
would make code look uniform without proving that ownership, state transitions
and failure behavior remain correct.

## Decision

- Keep business and database ownership inside each service. Communication
  between services remains through explicit HTTP or event contracts.
- Reusable code is split by responsibility: platform modules for technical
  capabilities, contract modules for stable wire types, client SDK modules for
  one service API, and testkit modules for test-only support. There is no
  universal `common` business library.
- A migrated business service owns a root-level `<service>/` directory with
  `domain`, `application-api`, `application`, `infrastructure` and executable
  `boot` subprojects. Its Maven artifact and runtime/DNS identity remain stable.
  Complete and verify one service before removing its legacy host/libraries;
  do not retain parallel production implementations or a `modules/` umbrella.
  Technical services use only the layers justified by their actual behavior.
- Domain and application remain framework-independent. Infrastructure owns
  HTTP/Kafka/JPA adapters, technical schedulers and composition, and implements
  application-owned ports. Boot contains the production entrypoint and runtime
  configuration. A facade delegating business decisions to a legacy service
  does not constitute migration. Application-api records remain behavior-free.
- Removal requires core/adapter tests, database/event evidence appropriate to
  the service, actual packaged startup/HTTP proof, stable wire contracts and
  deployment/artifact freshness checks. Existing broad platform failures remain
  visible; a successful service slice is not a full-system passing claim.
- Every domain and application module enforces at least 85% line and branch
  coverage. Coverage is necessary evidence for rules, not evidence of database
  concurrency, event recovery or production performance.
- Before each business refactor slice, its problem contract must be written and
  reviewed. It identifies actors and ownership, commands and queries, state
  transitions, invariants, validation, money/time/null semantics, authorization,
  duplicate/reordered input, concurrency, transaction failure, external
  boundaries and compatibility. Each item maps to a unit or boundary test.
  Technology and abstractions are selected only after this problem contract.
- Structural movement must preserve public APIs, event payloads, persistence,
  transaction boundaries and defaults unless a separate authorized change says
  otherwise.

## Alternatives Considered

1. Put DTOs, entities, clients and business helpers into one large common module.
2. Apply the same five-layer template to every technical and business service.
3. Refactor by file size and add tests after the movement is complete.
4. Set a single 85% threshold over the whole backend reactor.

## Consequences

Positive:

- Business rules can be tested without Spring, Kafka or a database.
- Shared technical behavior becomes reusable without coupling service data
  models or policies.
- Tests are derived from explicit business cases and exceptions rather than the
  current implementation shape.
- Coverage cannot be inflated by generated code or easy adapter lines while the
  core remains untested.

Tradeoffs:

- Migration is incremental and temporarily contains old and new module shapes.
- Each slice needs problem analysis and characterization before code movement.
- Adapter, PostgreSQL, Kafka and recovery behavior require separate integration
  evidence beyond the unit-test coverage threshold.

## Follow-Up

- Keep the completed Phase 0 build, architecture and artifact-freshness gates
  mandatory in CI as modules are added.
- Before the Restaurant/Menu pilot, write its complete basic-problem and
  exception matrix, then choose the first ownership vertical slice.
- Optimize reactor invalidation and CI duration only after measuring them.
