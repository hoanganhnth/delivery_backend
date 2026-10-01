# Phase 8–9 Coverage and Regression Closure Design

## Goal

Produce trustworthy coverage and regression evidence for the backend refactor
without lowering existing quality gates or claiming that a passing `test` build
proves an unmeasured coverage target.

## Scope

The strict 85% line and branch gates remain mandatory for extracted domain and
application modules. Search, Analytics, Promotion, Flash Sale, Livestream, and
Simulator receive measured JaCoCo baselines plus targeted tests for business
critical paths. Framework wiring, generated code, DTO boilerplate, and migration
classes are reported separately rather than used to dilute business coverage.

## Approach

1. Run Maven `verify`, because JaCoCo checks are bound to verification rather
   than the `test` lifecycle.
2. Add a deterministic report/audit command that reads module JaCoCo XML files,
   verifies every configured 85% gate, and reports Phase 8 service coverage.
3. Add tests only where the report identifies uncovered business decisions:
   tombstone handling, stale event rejection, idempotent retry, conflict paths,
   reservation fencing, and recovery behavior.
4. Keep existing 85% thresholds unchanged. Do not introduce a repository-wide
   85% gate until every monolithic service has a reviewed exclusion policy and a
   measured baseline.
5. Run dependency, hard-delete, contract, migration, and full reactor checks.
   Docker-dependent tests are classified explicitly as pass, skip, or fail.

## Outputs

- Machine-readable per-module coverage evidence and a concise review document.
- Tests closing material business-logic gaps found by the coverage report.
- Updated Phase 8–9 regression verifier and checklist evidence.
- A clean branch ready for code review, but no merge until all required checks
  pass and unresolved Docker-dependent evidence is documented.

## Success criteria

- Every extracted module configured for 85% passes both line and branch checks.
- Phase 8 service coverage is measured and recorded; no unverified “over 85%”
  claim remains.
- No threshold is lowered and no production code is excluded merely to improve
  the percentage.
- Full Maven `verify` and static regression audit pass, with skipped integration
  tests listed separately.
- Critical or important review findings are resolved before merge.
