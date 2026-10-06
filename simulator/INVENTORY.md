# Simulator inventory and ordered extraction

## Authority and boundary

This is an equivalence-only inventory of `simulator-service`, at the slice 1 baseline; later extraction evidence is recorded below.
References in the baseline tables below are repository-relative pre-extraction
file:line locations; current owners are described in the slice progress sections.
Source and executable tests take precedence over the stale in-memory-only
claims in simulator-service/README.md and docs/platform/system/simulator/README.md.
Relevant authority: AGENTS.md, docs/WORKFLOW.md, ROADMAP_MVP_TO_PRODUCTION.md,
docs/plans/active/service-architecture-consolidation.md, the simulator design
README and existing fence/recovery/assertion tests. No new product policy is
introduced. Runtime artifact/DNS remains `simulator-service`. Transport, JSON,
JPA, scheduling, security, metrics and composition stay in the host through slice 5.
No Kafka consumer configuration or docs/plans files are changed.

For the tables, `C` means
`simulator-service/src/main/java/com/delivery/simulator/controller/SimulatorController.java`,
`S` means `simulator-service/src/main/java/com/delivery/simulator/service/`,
and `R` means `simulator-service/src/main/resources/application.properties`.
Each abbreviated reference expands to that repository-relative file:line.

## Inbound entrypoints

All HTTP paths have prefix `/api/simulator` (C:30); authorization runs before
application work (C:143). Error handlers preserve disabled 404, unauthorized
401, invalid 400, not-ready 409 and Gateway failure 502 (C:156–179).

| Transport | Entrypoint | File:line |
| --- | --- | --- |
| HTTP POST | /validate | C:44 |
| HTTP POST | /runs (202) | C:52 |
| HTTP GET | /runs/{runId} | C:61 |
| HTTP GET | /runs | C:68 |
| HTTP GET | /runs/{runId}/algorithm-traces | C:75 |
| HTTP GET | /runs/{runId}/journal | C:83 |
| HTTP GET / SSE | /runs/{runId}/stream; header token only | C:91 |
| HTTP POST | /runs/{runId}/pause | C:101 |
| HTTP POST | /runs/{runId}/resume | C:109 |
| HTTP POST | /runs/{runId}/abort | C:117 |
| HTTP POST | /runs/{runId}/reconcile | C:126 |
| HTTP DELETE | /runs/{runId} | C:135 |
| Kafka | matching.decision-trace (configurable topic), dedicated simulator-algorithm-observer group | S/AlgorithmDecisionTraceObserver.java:28; observe :31 |
| Kafka | delivery.completed (configurable simulator.delivery-completed-topic), inherited consumer group | S/SimulationLedgerObserver.java:23; observe :24 |
| Lifecycle | restart orphan reconciliation; shutdown | S/SimulationService.java:116, :284 |
| Scheduler | lease heartbeat; run TTL abort; expired lease reclamation | S/SimulationService.java:534, :539; S/SimulationLeaseService.java:62 |

Trace observer requires versioned IDs/algorithm/stages/candidates and wraps
malformed payloads in IllegalStateException for Kafka retry. It has no business
mutation. Ledger observer validates SimulationContext, ignores non-simulation
contexts and persists observed simulation completion. Neither publishes Kafka.

## Decision locations and dependencies

| Decision / behavior | File:line | Dependencies / proof |
| --- | --- | --- |
| Terminal assertion, REJECTED alias, shipper mismatch, ledger skip, outcome precedence | S/SimulationAssertionPolicy.java:9, :26; S/SimulationService.java:1060 | Pure strings/booleans; SimulationAssertionPolicyTest, SimulationAssertionOutcomeTest |
| Straight-line movement, distance, heading | S/DeterministicPolyline.java:12, :17 | Pure numeric rules; DeterministicPolylineTest; seed currently has no effect |
| Gateway target allowlist, scenario validation and error order | S/SimulationService.java:1084, :1107 | Properties + Jackson; SimulationServiceValidationTest |
| Actor bind/unbind and cohort/run ownership validation | S/SimulationService.java:399, :430, :452 | Auth port + JSON; SimulationRunControlTest, recovery tests |
| Action/control fences, per-order sequencing, triggers | S/SimulationService.java:367, :689, :775, :842, :898, :1004, :1251 | Mutable state + Gateway; BusinessWrite/SingleOffer/BatchOffer/Trigger/Location fence tests |
| Terminal projection convergence | S/SimulationService.java:1293 | Pure strings; poll/transient-poll recovery tests |
| Poll rate-limit classification and location backoff | S/SimulationService.java:970, :995, :999 | Gateway exception; SimulationPollFailureTest, SimulationTransientPollRecoveryTest |
| Canonical delivery response / shipper aliases | S/SimulationDeliverySnapshot.java:8 | Jackson; SimulationDeliverySnapshotTest |
| Candidate oracle / online / COD / ranking | S/SimulationRunState.java:512 | JSON + snapshot view; not authoritative Match output |
| Shadow ETA and fairness comparison; ties retain first candidate | S/ShadowAlgorithmComparator.java:24, :59, :63, :142 | Jackson; ShadowAlgorithmComparatorTest; never feeds Match |
| Run terminality, actor release safety, trigger de-duplication, SSE snapshots | S/SimulationRunState.java:96, :270, :448 | JSON + SSE + synchronized mutation; RunControl/RunStateAlgorithmTrace tests |
| Lease claim/renew/release/quarantine and heartbeat abort | S/SimulationLeaseService.java:31, :48, :74, :86, :101; S/SimulationLeaseCoordinator.java:22, :31 | JPA + wall clock; LeaseService/LeaseCoordinator/HeartbeatSafety tests |
| Restart recovery, terminal proof before actor release, ID discovery | S/SimulationRecoveryService.java:70, :126, :153 | Auth/Gateway/internal Delivery + journal; SimulationRecoveryServiceTest |
| Credential key redaction and journal ordering | S/SimulationCredentialRedactor.java:10; S/SimulationRunJournalService.java:23, :29 | Jackson/JPA; ScenarioRedaction/RunJournalService tests |
| One transient GET order-poll fault per correlation | S/GatewayFaultInjection.java:16, :20 | Concurrent set; GatewayFaultInjectionTest |
| Catalog and operational gauges | S/Phase8ScenarioCatalog.java:11; S/SimulationOperationalMetrics.java:16 | Catalog constants / repositories + Micrometer; corresponding tests |

## Guarantees and limits to preserve

- Gateway HTTP uses actor Bearer tokens and correlation IDs; no direct writes to
  neighboring databases. Gateway transport is S/GatewayClient.java:52–111.
  Auth bind/unbind uses private HTTP + X-Internal-Secret and binding version
  (S/AuthSimulationActorPoolClient.java:26, :44, :54). Recovery lookup uses
  Internal-Token (S/InternalSimulationDeliveryRecoveryClient.java:27).
- COD order creation gets checkout-preview first. Only a nonblank quote adds
  quoteId and a new UUID Idempotency-Key (S/SimulationService.java:551–597).
  There is no durable simulator command receipt or outbox; journal entries are
  audit events, not transactional command delivery. Location 429 retries are
  bounded snapshot retries; business writes retain control checks.
- SimulationRunState uses synchronized view mutations, volatile controls,
  concurrent identity/trigger sets and atomic timeline sequence. These are
  process-local locks, not distributed locks. Traces correlate on both order
  and delivery identity; eventId replaces duplicate traces without a duplicate
  trace timeline entry (S/SimulationRunState.java:216). Shadow rows replace by
  sourceEventId/algorithm/version but still append a timeline event (:236).
- Pending traces are memory-only: 20-minute retention, 2048-entry cap,
  eventId key (S/SimulationService.java:54, :472–506). Orphan runs are ABORTED,
  leases quarantined, Auth bindings retained; credentials cannot resume after
  restart (:116). Durable statuses and redacted scenario are stored separately
  from append-only journal writes (:144, :464). No transaction spans these
  writes and remote Auth/Gateway actions.
- Lease service transactions read latest principal fence and save the next;
  renew/release require ACTIVE and matching fence. The repository has no
  explicit row lock or optimistic @Version. V1 adds only a nonunique principal
  index; no database constraint makes claim exclusive. Auth's external binding
  fence is a separate ownership boundary, not a local lease lock.
- V1 migration (`simulator-service/src/main/java/db/migration/V1__simulation_runs.java:9`)
  creates runs, leases and ledger; ledger has eventId PK and unique deliveryId.
  Ledger observer checks eventId before insert, without an atomic receipt claim;
  concurrent duplicate inserts / different event IDs for one delivery can fail.
  V2 (:9 in V2__simulation_run_journal.java) creates journal with generated ID;
  repository reads ascending ID, without event deduplication.
- Terminal success waits for Order DELIVERED and Delivery DELIVERED. Cancellation
  / no-shipper permits absent Delivery or terminal compensation (:1293).
  Failure/abort finalization attempts cancellation only for unaccepted states;
  unsafe delivery retains Auth binding and quarantines leases (:324). Cleanup
  is terminal-only and durable repeat cleanup does not remove recovery fences
  (:254). Recovery releases actors only after all discovered deliveries are
  terminal (S/SimulationRecoveryService.java:70); journal IDs are preferred,
  internal Delivery lookup is used only when the journal has none.
- Ledger-count assertions still SKIP solely because the assertion has
  expectedLedgerCount; the observer flag does not enable count verification
  (S/SimulationService.java:1072). Failure wins over skip; skip yields PARTIAL.
  The moved policy preserves exact Vietnamese/English messages and NPE behavior
  for null expected strings, with skip/terminal mismatch short-circuiting.

## Ownership, default-off flags and safety

C:143 checks enabled, optional X-Simulator-Token, and admin-only role. It does
not record a creator or restrict run reads/controls by creator: authorized
operators share run access. SecurityConfig at
`simulator-service/src/main/java/com/delivery/simulator_service/security/SecurityConfig.java:17`
declares JWT authentication; SimulatorServiceApplication.java:9 explicitly
scans both simulator packages. SimulatorService additionally checks enabled and safe Gateway
host on validate/start (:1239, :1084). Auth is the owner of managed actor binding,
run/cohort and bindingVersion; simulator validates returned run/cohort (:430).
Credentials remain in memory; redaction strips token/accessToken/ownerToken
recursively before snapshots and persistence.

Default-off flags at R:5, :13, :23–24 and SimulatorProperties.java:11, :19,
:32–33 (under simulator-service/src/main/java/com/delivery/simulator/config/):
SIMULATOR_ENABLED=false, SIMULATOR_ALLOW_NON_LOCAL_TARGETS=false,
SIMULATOR_KAFKA_OBSERVER_ENABLED=false, SIMULATOR_LEDGER_OBSERVER_ENABLED=false.
Default-on guards: ADMIN_ONLY=true and MANAGED_ACTOR_POOL_REQUIRED=true.
API token/internal secret default empty; no secret is introduced. Gateway
hosts default localhost/127.0.0.1/api-gateway; production/staging names rejected
even with non-local allowance. Kafka defaults at R:28–33 remain unchanged:
latest offset reset, auto-commit false, String deserializers, observer group.
Lease reclaim, orphan reconciliation and run expiry are not conditional on
the HTTP enabled flag. Do not infer global shutdown from default-off HTTP.

## Observed pre-existing defects / gaps (report only)

1. Lease claim has read-then-insert race without exclusive principal constraint,
   row lock or @Version; concurrent claim/renew safety is not proven by mocks.
2. Ledger eventId lookup followed by insert is not atomic; unique deliveryId
   can reject a differently identified replay without comparison/reconciliation.
3. README/design text says durable storage and ledger observer are future work,
   despite current code/migrations. Ledger assertions always SKIP even when
   observer is enabled; this slice preserves that behavior.
4. DeterministicPolyline seed expression uses seed % 1 and contributes zero,
   so every seed yields the same route. No algorithm change is authorized.
5. maxConcurrentRuns sizes a fixed executor with an unbounded task queue; start
   has no admission-cap check. This limits active threads, not admitted runs.

These are source observations, not new runtime reproductions. No defect fixes
or claims of packaged-runtime/concurrency proof accompany the extraction.

## Proposed ordered slices

1. **This slice:** create simulator/domain before simulator-service in the root
   reactor, with Promotion's build parent and 85% JaCoCo line/branch gates.
   Move SimulationAssertionPolicy unchanged into com.delivery.simulator.domain;
   expose it for the host caller, add exhaustive truth-table/message/null tests,
   retain host assertion outcome/order tests. Host owns JSON and observations.
2. Extract pure movement and terminal/control/retry decisions with explicit
   inputs; characterize all fence and numeric edge cases before moving them.
3. Extract candidate/shadow ranking and alias decisions behind immutable domain
   inputs; keep Jackson response parsing and existing tie/order semantics in
   adapters. Retain oracle versus actual Match distinction.
4. Create simulator/application-api commands/results and ports for Gateway,
   Auth binding, lease store, journal, run store, observation and clock. Keep
   existing exceptions, redaction and HTTP/Kafka contracts at host adapters.
5. Create simulator/application orchestration slices: validation/start/control,
   per-order actor flow, observation/assertions, then recovery/cleanup. Preserve
   side-effect ordering, partial failures, binding fences and transaction scopes.
6. Move persistence entities/repositories/migrations, HTTP clients/controllers,
   SSE, Kafka observers and metrics into simulator/infrastructure; retain
   consumer configuration, topics, flags, schema and runtime dependencies.
7. Relocate entrypoint/composition/resources/tests into simulator/boot; finish
   simulator/{domain,application-api,application,infrastructure,boot}, remove
   simulator-service directory only after reactor, packaging, migration and
   runtime/recovery proof. Artifact/DNS remains simulator-service.

Validation for slice 1: `mvn -B -pl :simulator-service -am clean verify`.
Docker-dependent upstream skips must be reported separately from passing tests.

## Slice 1 implementation and proof

Completed module: `simulator/domain/pom.xml`; implementation:
`simulator/domain/src/main/java/com/delivery/simulator/domain/SimulationAssertionPolicy.java:9`.
Only package and public visibility changed in the policy. SimulationService
imports it directly; the host policy and its old test file were removed.
Root reactor registers simulator/domain immediately before simulator-service;
host adds the simulator-domain dependency. No production framework dependencies
are declared by the domain. The domain test checks 2,000 combinations, exact
messages, all four outcome combinations and null exception/precedence behavior.
Existing host SimulationAssertionOutcomeTest retains assertion ordering and
Gateway-no-interaction coverage.

Executed `mvn -B -pl :simulator-service -am clean verify`: exit 0, BUILD SUCCESS,
28.904 seconds. Surefire: identity-contracts 5, auth-resource-server-starter 8,
simulator-domain 9, simulator-service 153; total 175 tests, 0 failures, 0 errors,
0 skips. Docker-only skips: none in this reactor. JaCoCo domain LINE 13/13 and
BRANCH 18/18 (both 100%); inherited 85% gates passed. Evidence is in the modules'
target/surefire-reports and simulator/domain/target/site/jacoco/jacoco.xml;
console log for this execution: /tmp/simulator-verify.log.

Filesystem-only work: no Git commands were run; branch creation/verification
is the parent's responsibility under the assigned constraint. Later slices and
real database concurrency/packaged recovery validation remain future work.


## Slices 2–5 implementation

Completed the ordered decision and application extractions without relocation:

- **Slice 2:** domain `DeterministicPolyline` and `SimulationDecisions` own movement,
  terminal convergence, run terminality, actor release safety, coordinate bounds,
  confirmation and retry decisions. The host route class is a compatibility
  delegate; host state delegates terminal/release decisions. Seed behavior is
  deliberately unchanged, including NaN/infinity and zero-distance behavior.
- **Slice 3:** domain `CandidateOracle` and `ShadowRanking` use immutable record
  inputs/results. Host state and shadow comparator retain Jackson parsing and
  response rendering. Rounded candidate ordering, first-match actor aliases,
  first-candidate ties, duplicate actor overrides and fairness scoring remain
  equivalent. Neither oracle nor shadow recommendation feeds Match.
- **Slice 4:** `simulator/application-api` defines Gateway, Auth actor binding,
  Delivery recovery, settings, run/state/store, lease, journal, observation,
  fault, state factory and time ports. Existing JsonNode scenario/response and
  Map snapshot shapes serve as command/result contracts; this slice deliberately
  introduces no transport DTO conversion. Binding and delivery result records
  and the Gateway failure contract now belong to that module. Host interfaces
  and exception subclasses retain caller compatibility.
- **Slice 5:** `simulator/application` owns `SimulationUseCases`,
  `SimulationLeaseCoordinator` and `SimulationRecoveryUseCase`. This includes
  validation/start/control, per-order execution, human order discovery, offers,
  movement, triggers, polling, observations/assertions, orphan reconciliation,
  heartbeat/expiry, cleanup and recovery. Host `SimulationService` and
  `SimulationRecoveryService` compose and delegate to those use cases. Host
  repositories, entities, lease transactions, JSON/redaction, HTTP/Kafka/SSE,
  scheduler/lifecycle annotations, thread-pool construction and system-time
  implementation stay in `simulator-service`.

Reactor adds application-api/application after domain, before the host. Domain,
application-api and application keep inherited 85% LINE/BRANCH gates. Application
has its own executable port-only tests rather than relying on host coverage;
all original host regression tests remain. Production application/domain have
no Spring/JPA/Kafka/HTTP-client dependencies. Jackson is retained in the API and
application to preserve the existing scenario and observation contract during
this bounded extraction. Spring test utilities are test-only.

### Regression and newly observed gaps

Domain truth tables cover projection/control/coordinate/retry branches, aliases,
shadow eligibility and reason precedence, numeric movement and seed equivalence.
Application tests cover side-effect order, persistence failure rollback, binding
ownership and partial rollback, durable/memory controls, retained recovery
fences, observation retention/cap, offers and action de-duplication, trigger
policies, transient poll classification, bounded location retry, movement,
confirmation, checkout quote forwarding, human-order discovery/timeout and
assertion precedence. Recovery tests cover nested journal identities, malformed
rows, fallback lookup, every terminal status, partial binding rollback and
terminal proof before release. Coordinator tests cover lost fence, outage abort,
terminal/no-state exclusions and release retry.

Recorded defects remain: seed has no effect; ledger assertions skip; commands
have no admission-cap check; stale lease reads can produce the same fence;
ledger receipt reads precede inserts and differently identified delivery replay
propagates a storage conflict. `SimulationKnownPersistenceGapsTest` characterizes
stale repository responses and replay failure, **not actual database concurrency**.
The stale README observation also remains; it is documented in this inventory.

Two additional gaps were found while characterizing the moved logic and are
preserved, with explicit tests rather than fixed in this equivalence task:

6. Recovery accepts an Auth binding with a foreign run/cohort when the returned
   principal still matches the customer. Normal start checks run/cohort;
   recovery does not. `SimulationRecoveryUseCaseTest.
   recoveryPreservesExistingMissingReturnedContextOwnershipCheck` proves that
   behavior and the use of the original requested run when unbinding.
7. `checkControl` checks abort before/inside the pause loop, but not after the
   sleep exits the loop. Abort clears pause; an abort during pause sleep can
   therefore allow one subsequent Gateway action. `SimulationUseCasesTest.
   pauseAndInterruptFenceSubsequentWrites` characterizes that one action and
   verifies the following control check rejects the aborted state. This does
   not weaken the existing host fence tests; the gap is a distinct interleaving.

### Remaining slices

Slices 6–7 remain: infrastructure and boot relocation, final composition/module
boundary proof, packaged runtime, migration and real persistence/recovery proof.
No docs/plans or Kafka consumer configuration edits are part of this work.
No Git commands were run. Parent owns branch/history/diff review under the
filesystem-only constraint; source surfaces and executable behavior were
inspected here.


### Fresh slice 2–5 validation evidence

Executed `mvn -B -pl :simulator-service -am clean verify`: exit **0**, **BUILD
SUCCESS**, 1:08 minutes, finished 2026-10-06T21:28:34+07:00. Surefire reports:
identity-contracts 5, auth-resource-server-starter 8, simulator-domain 17,
simulator-application-api 2, simulator-application 103, simulator-service 156
(including all original 153 host tests). **291 tests, 0 failures, 0 errors,
0 skips. Docker-dependent skips: none in this reactor.** No Docker-backed
persistence or packaged runtime exercise was added by this slice.

JaCoCo executable gates all passed:

| Module | LINE | BRANCH | Minimum |
| --- | --- | --- | --- |
| simulator-domain | 102/102 (100%) | 162/166 (97.59%) | 85% / 85% |
| simulator-application-api | 12/12 (100%) | 10/10 (100%) | 85% / 85% |
| simulator-application | 957/969 (98.76%) | 473/538 (87.92%) | 85% / 85% |

Evidence: `/tmp/simulator-slice2-verify.log`, module `target/surefire-reports/`
and `target/site/jacoco/jacoco.xml`. The host repackaged JAR also built
successfully. Final source inspection confirms ports have host implementations,
application has no production framework imports, and scheduler/lifecycle/SSE
composition remains in the host. Kafka observers/configuration, migrations,
HTTP security and resources were not relocated.
