# Phase 8–9 dependency and regression audit

The executable gate is `scripts/verify-phase8-9-regression.py`. It checks the
high-change services' production Java sources for direct repository hard-delete
calls and verifies the required tombstone, replay, simulator, and livestream
artifacts exist. It also executes `verify-module-boundaries.py`, which checks
core framework imports (including static imports), declared production
dependencies, contract runtime dependencies, and foreign service imports.
Simulator's `com.delivery.simulator` package is included despite not using the
usual `_service` suffix. Direct and profile Maven dependencies on another
deployable service are rejected; test dependencies and dependency-management
entries do not constitute production links.

Test fixtures and migration constraint assertions are excluded
from the hard-delete scan because they intentionally reset or validate data.

Run from the repository root:

```bash
python3 -B scripts/test-module-boundaries.py
python3 -B scripts/verify-module-boundaries.py --self-test
python3 -B scripts/verify-phase8-9-regression.py
```

The audit is a static gate, not a replacement for the full Maven reactor,
Docker-backed integration tests, coverage report, or production dependency
review. The POM scan covers declared dependencies, not the effective/transitive
Maven graph, reflection, or fully qualified implementation references. Resolved
runtime graphs now have a separate executable gate below; reflection and source
review still require human/agent review before Phase 9 exit approval.

## Resolved runtime dependency graph

After the fresh reactor build, generate runtime graphs with the pinned plugin:

```bash
mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree -Dscope=runtime -DoutputType=tgf -DoutputFile=target/phase8-runtime-dependencies.tgf
python3 -B scripts/test-runtime-dependency-boundaries.py
python3 -B scripts/verify-runtime-dependency-boundaries.py
```

The gate requires matching, well-formed, connected graphs for every discovered
service, contract and domain/application/API module. It rejects indirect service
implementation links, framework dependencies in core/contracts, and cross-domain
links in core. Provided dependencies remain covered by static POM checks; this
graph is compile/runtime scope, not test scope. Missing graphs fail closed.
On 2026-10-01 generation exited 0 and all 54 audited graphs passed. Seven fixture
tests cover hidden transitive links, invalid/missing graphs, matching roots and
valid contract/framework boundaries. CI runs generation and verification after
the fresh build. Remote CI has not yet run for this change.

## Mandatory integration evidence

After a fresh Maven `clean verify`, run:

```bash
python3 -B scripts/test-phase8-integration-evidence.py
python3 -B scripts/verify-phase8-integration-evidence.py
```

The second command checks ten mandatory Search, Analytics, Promotion, Flash Sale
and Livestream Docker suites. Missing/empty/malformed reports, failures, errors and skips fail
the gate. It reads testcase outcomes as well as suite counters, so an omitted
skip counter cannot turn a skipped test into passing proof. It does not itself
prove report freshness; CI executes `clean verify` immediately before this gate.
CI also runs coverage-report checks without weakening the existing core gates.

On 2026-10-01 the affected reactor exited 0 with 565 tests discovered, 540
executed and 25 skipped across six services. All eight required Docker suites
were skipped locally because the Docker daemon was unavailable. The integration
evidence gate therefore exited 1; this remains missing release evidence.
Ten boundary fixtures, eight evidence fixtures, existing boundary self-tests,
coverage reporter tests and static regression checks passed. The modified CI
workflow has not yet run remotely.

The Docker blocker was subsequently resolved by launching the installed Docker
Desktop executable. The six-service affected reactor then exited 0 with Docker
29.4.2: 589 tests executed, zero failures/errors/skips. Search 32, Analytics 118,
Promotion 91, Flash Sale 100, Livestream 89, Simulator 159. All eight required
integration suites passed the evidence gate; this supersedes the skipped local
checkpoint above. Later source changes require fresh affected-suite verification.

The inventory now also requires Analytics PostgreSQL receipt/upsert/rollback/
V3→V4 migration tests and Livestream PostgreSQL checkout receipt tests; they
cannot satisfy the gate via a Docker-dependent skip. Analytics' first PostgreSQL
run exposed unsupported `ADD CONSTRAINT IF NOT EXISTS` syntax in V4; the fixed
SQL uses Flyway's one-time versioned execution. Any environment that previously
applied V4 must check its stored checksum before rollout. No automatic Flyway
repair or external database modification was performed.
