# Phase 8 coverage baseline — 2026-09-30

## Evidence and scope

Source base: `61413b0`, with the coverage instrumentation and null-request fix
in this change. Reports are bundle-wide with no new coverage exclusions.
These are test coverage measurements, not production performance measurements.

- Original full reactor: `mvn -B -fae verify`, 79 modules SUCCESS.
- Instrumented affected reactor:
  `mvn -B -fae -pl :search-service,:analytics-service,:promotion-service,:flashsale-service,:livestream-service,:simulator-service -am verify`,
  12 modules SUCCESS.
- Six services: 310 tests discovered, 285 executed successfully, 25 skipped.
  Search skipped 5, Promotion 10, Flash Sale 10; Docker-backed validation remains outstanding.
- 20 extracted domain/application modules passed their existing 85% gates:
  minimum observed line coverage 91.67%; minimum observed branch coverage 86.46%.
- `python3 -B scripts/test-report-phase8-coverage.py`: 7 tests passed.
- `python3 -B scripts/report-phase8-coverage.py`: all 26 expected current reports
  present; no configured core threshold failures.
- `python3 -B scripts/verify-module-boundaries.py`: passed.
- `python3 -B scripts/verify-phase8-9-regression.py`: passed its narrow static scan.
  It does not establish complete dependency or runtime correctness.

## Service baseline

| Service | Line % | Branch % | Tests skipped |
|---|---:|---:|---:|
| Search | 56.52 | 31.25 | 5 |
| Analytics | 52.59 | 42.35 | 0 |
| Promotion | 62.63 | 50.89 | 10 |
| Flash Sale | 44.37 | 29.30 | 10 |
| Livestream | 72.76 | 62.50 | 0 |
| Simulator | 41.50 | 30.72 | 0 |

Services use Spring Boot parent directly. Explicit JaCoCo prepare-agent/report
executions now collect their baseline without changing dependency management.
The agent overwrites its execution data for each test run (`append=false`).
Run the complete service suite before interpreting its report; targeted test
runs are not equivalent baselines. XML reports are generated during `verify`.
The reporter does not independently prove report freshness: rerun verify after
source changes. A missing report is an error, not zero or 100% coverage.

Core gates remain unchanged. No new service-wide threshold has been claimed
as achieved. Branches with zero opportunities are displayed as null, not 100%.
Run from the backend root:

```sh
mvn -B -fae verify
python3 -B scripts/test-report-phase8-coverage.py
python3 -B scripts/report-phase8-coverage.py
python3 -B scripts/verify-module-boundaries.py
python3 -B scripts/verify-phase8-9-regression.py
```

## Highest-value next tests and refactoring

Order balances correctness risk and uncovered business branches.

1. Livestream: replace separate in-memory fingerprint/result maps with durable
   atomic receipts. Prove same-key replay across restart and concurrent callers;
   reject different payloads; validate product authority when creating a receipt.
   Separate handoff orchestration from read-only quote. Keep stored context
   immutable even when a product is repinned. Current patch fixes only validation
   ordering: ten invalid-scope cases now fail before repository access, including
   null request which previously threw NullPointerException.
2. Flash Sale: `FlashSaleStockService` has 102 missed / 6 covered branches.
   Exercise quote, reserve, commit, release, expiry, and deleted-item paths with
   unit tests; retain PostgreSQL concurrency tests for actual lock/transaction
   semantics. Extract pure availability decisions only after characterization.
3. Promotion: `PromotionService` has 309 missed / 232 covered branches.
   Characterize eligibility and reservation transitions, then extract voucher
   lifecycle and reservation orchestration separately. Preserve public APIs and
   transaction boundaries; verify self-invocation does not lose transactions.
4. Search: checkpoint store has 42 missed / 0 covered branches.
   Exercise claim/replay/conflict/failure paths; run real Elasticsearch integration
   before claiming stale-after-delete safety. Test external version compatibility.
5. Analytics: `EventProcessingService` has 95 missed / 89 covered branches.
   Separate payload parsing, replay comparison, and projection updates after tests
   for malformed input, conflicting replay, out-of-order versions, and rollback.
   `DashboardQueryService` has 42 missed / 0 covered branches.
6. Simulator: `SimulationService` has 452 missed / 110 covered branches.
   Extract scenario execution from gateway effects and run journaling. Connect the
   catalog to execution and prove a production destination is rejected before any
   outbound call. Constant metadata alone does not prove production isolation.

Do not declare completion from percentage increases alone. Regression tests
must prove behavior and the old six falsely-complete checklist items remain open.
Review of the scoped instrumentation/report/null fix found no concrete blocking
issue; report freshness remains an explicit operational prerequisite.

## Recovery

This change adds build instrumentation and reporting, with no schema migration.
If instrumentation interferes with tests, isolate the interference and retain
the failing evidence; never lower core coverage gates to make verification pass.
No Phase 8/9 merge or production rollout is justified by this baseline alone.
