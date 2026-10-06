# Search inventory and ordered extraction

Scope: equivalence-only Search tranche. Authority: `docs/services/search_service.md`,
`docs/plans/active/service-architecture-consolidation.md`, and the existing host
implementation/tests. No new policy, flags, topics, storage or consumer configuration.
Paths below are relative to `search-service/src/main/java/com/delivery/search_service/`
unless prefixed otherwise. Historical line references describe the post-slice-1 source;
pre-extraction decision locations are recorded explicitly.

## Entrypoints and observable contract

| Entrypoint | Location | Behavior |
| --- | --- | --- |
| HTTP GET `/api/search/restaurants` | `controller/SearchController.java:29` | q required/nonblank/max 100, page >= 0, size 1..100; defaults 0/20; trims q before query |
| HTTP GET `/api/search/dishes` | `controller/SearchController.java:41` | Same admission; stable DTO and page envelope |
| Kafka `entity-sync` | `consumer/ElasticsearchSyncConsumer.java:48` | Configured consumer group; validation → fingerprint → checkpoint claim → stale no-op or projection write |
| HTTP error translation | `controller/SearchExceptionHandler.java:15`, `:21` | Unavailable → 503/status 0/null/`Search is temporarily unavailable`; bad request → 400 with original exception message |
| Private management health | `config/SearchBackendHealthIndicator.java:29`, `src/main/resources/application.yml:65` | HEAD Elasticsearch, 2xx/3xx UP; failure DOWN; separate management port, health-only exposure |
| Boot | `SearchServiceApplication.java:9` | Spring composition, artifact and application name remain `search-service` |

Public restaurant/dish GETs are permitted without authentication by
`security/SecurityConfig.java:24`; other application requests require JWT.
Search owns no merchant/user authorization or catalog mutation: Restaurant/Menu
own source data and transactional outbox (per service specification). Search
owns only rebuildable restaurant/dish projection and per-entity checkpoint.
SHIPPER events are rejected. No Redis, SQL/JPA, Search outbox or event publisher;
KafkaTemplate is used by the DLT recoverer only.

## Decisions and boundaries

- Event metadata admission formerly `consumer/ElasticsearchSyncConsumer.java:94`:
  required envelope → allowed action → allowed entity → create/update payload →
  positive optional aggregateVersion → DELETE deletedAt <= occurredAt. Slice 1
  moves this ordered policy to
  `search/domain/src/main/java/com/delivery/search/domain/EntitySyncRules.java:16`.
  Case-insensitive labels are accepted; whitespace is not stripped. No payload
  schema/identity normalization is introduced.
- Fingerprint stays in `consumer/ElasticsearchSyncConsumer.java:72`: sorted JSON,
  ROOT uppercase action/entity, occurredAt text, aggregateVersion, deletedAt,
  deletionReason and payload; SHA-256. Serialization and mapper configuration
  remain transport concerns.
- Slice 2 moves noop replay classification to
  `search/domain/src/main/java/com/delivery/search/domain/CheckpointReplayPolicy.java`.
  Host storage retains created/updated APPLY and missing source handling; pure policy
  owns EXACT_REPLAY, STALE, legacy-upgrade eligibility, contradictory metadata/payload,
  equal-time conflicts, corrupt timestamps and regressed claim.
- Slice 2 moves legacy timestamp parsing into `CheckpointReplayPolicy`: parse local
  time, then offset time retaining local fields; same messages, causes and suppressed
  exceptions. Legacy fingerprint upgrade remains in the checkpoint adapter and uses
  seq_no/primary_term CAS; four-attempt retry and HTTP error behavior are unchanged.
- Document index selection and payload conversion stay in
  `consumer/ElasticsearchSearchProjectionWriter.java:36` and `:87`: restaurant/dish
  indices, overwrite payload ID with envelope ID, DELETE versus PUT.
  Version calculation formerly `:77` moves to
  `search/domain/src/main/java/com/delivery/search/domain/EntitySyncRules.java:45`: aggregate version
  takes precedence; otherwise UTC epoch seconds * 1e9 + nanos, checked arithmetic.
  The adapter retains the nonpositive-version check and its existing ordering.
- Slice 4 moves query availability to `DefaultSearchQueryUseCase`: absent reader or
  runtime read failure throws application SearchUnavailableException; host translates
  to its existing exception with the original message/cause. Provider resolution
  stays outside the read catch boundary. Host repositories search name OR description
  with the original pageable/sort and return the original page, without fallback.
- Page/DTO conversion stays in `payload/PageResponse.java:15` and `dto/`;
  BaseResponse success messages remain `Thành công`/`Thất bại`.

## Idempotency, locks and projection guarantees

- Checkpoint index `entity_sync_checkpoint`, key uppercased entityType + `:` +
  entityId. Painless scripted upsert is atomic; no JVM or SQL lock. Four conflict
  attempts; unexpected responses/IO errors fail closed. Equal ID is classified in
  Java; fingerprint-less exact replay upgrades via optimistic CAS.
- Exact replay reapplies the document to recover a crash after claim and before
  write. Stale records skip write and increment stale telemetry. DELETE successes
  increment tombstone telemetry; writer failure increments replay-failure telemetry
  and wraps with `IllegalStateException("Failed to synchronize search entity")`.
- Writer uses `external_gte` version and treats Elasticsearch 404/409 as reached
  projection. Checkpoint and projection write are separate operations, not one
  transaction. Data mutations, request paths, indices and retry ordering stay put.
- `config/SearchKafkaConsumerConfig.java:37` sets earliest, auto-commit false,
  fixed contracts DTO and ignores type headers. `:52` installs common error handler.
  `config/KafkaErrorConfig.java:17` retries at 1000ms twice, publishes original
  partition to `entity-sync.DLT`, and fails on unsuccessful DLT send. These files
  are read-only for this tranche; no new ACK/delivery guarantee is claimed.

## Flags and ownership

`src/main/resources/application.yml:35` and `:54`: repositories and projection
consumer/writer/checkpoint are gated by `APP_ELASTICSEARCH_ENABLED:false`.
`:39`: discovery defaults off (`SERVICE_DISCOVERY_ENABLED:false`).
`:26`: Kafka listener startup defaults **true**, independently of backend gate.
`:11`: Config Server fail-fast defaults false. `:59`: built-in typed Elasticsearch
health defaults off; custom HEAD health remains active even with backend disabled.
No Search business feature flag or identity ownership filter is present.

## Ordered slices

1. **Completed (slice 1):** create framework-free `search/domain` before host in reactor,
   host dependency; move envelope admission and version arithmetic with exhaustive
   rule/boundary tests and host delegation regressions. 85% line/branch gates.
2. **Completed (slice 2):** extract checkpoint replay classification/time normalization into pure policy;
   preserve scripted ordering, legacy CAS, retry counts and exact exception contract.
3. **Completed (slice 3):** introduce `search/application-api` with framework-free query/projection inputs,
   outputs and ports. Keep Kafka contract and HTTP DTO mappings in adapters.
4. **Completed (slice 4):** introduce `search/application` for admission/claim/write and query orchestration;
   preserve fingerprint inputs, metrics, error wrapping and call ordering.
5. **Remaining:** move HTTP/Kafka/Jackson/Elasticsearch/security/health adapters and storage models
   into `search/infrastructure`; maintain topics, flags, indices and DB behavior.
6. **Remaining:** relocate host to `search/boot` (entrypoint/config only), ending in
   `search/{domain,application-api,application,infrastructure,boot}`. Keep artifact,
   DNS, runtime configuration and deployment contract `search-service`; rerun host
   verify and packaged/runtime recovery proof when environment supports it.

## Application boundary (slices 3–4)

- Framework-free `ProjectionInput` retains all wire values and opaque payload;
  `ProjectionPorts` binds fingerprint, atomic claim, projection write and telemetry.
  `DefaultProjectEntityUseCase` admits before fingerprint/claim, stops on STALE,
  reapplies EXACT_REPLAY, then writes and counts successful DELETE. Only the original
  write/tombstone block wraps exceptions and counts replay failure.
- `ElasticsearchSyncConsumer.HostProjectionPorts` implements these ports over the
  original Kafka event. Jackson canonicalization, event identity passed to existing
  storage adapters, logs and metrics remain in the host. No consumer config changed.
- `SearchQueryPort<R>` resolves an optional reader; `R` is an opaque output owned by
  the adapter. This deliberately preserves Spring Page identity and metadata without
  importing Spring into application-api/application or introducing new paging policy.
  HTTP still owns trimming/admission/DTO conversion; pageable stays bound to the host
  reader. Projection output is void, matching the existing listener contract.
- Reactor order: domain → application-api → application → search-service. All three
  new modules inherit explicit 85% line/branch gates. The host retains its existing
  JaCoCo report configuration; no gate is removed or weakened. No transactions are
  introduced; checkpoint and writer are still separate Elasticsearch operations.

## Pre-existing defects / proof limits (not fixed)

- Checkpoint script (`ElasticsearchEntitySyncCheckpointStore.java:54`) orders raw
  LocalDateTime strings, while Java noop classification parses them. Optional
  seconds/fraction representations can compare differently lexicographically from
  chronological time (e.g. stored `2026-09-30T10:00:00Z` compares greater
  than a later incoming `2026-09-30T10:00:00.000000001`). Legacy normalization
  only participates after a noop.
- Checkpoint admission orders occurredAt, whereas writer prefers aggregateVersion.
  Their two clocks can disagree; neither this extraction nor unit proof resolves it.
- Newly recorded during slice 2 review: writer accepts every Elasticsearch 404
  response, including PUT `index_not_found_exception`, as a reached projection.
  `SearchProjectionVersionTest.preExistingPut404IsStillTreatedAsReachedProjection`
  preserves this behavior; fixing it requires a separate authorized behavior change.
- DELETE uses Elasticsearch physical deletion, not a retained projection document.
  Long-delayed writer fencing depends on Elasticsearch delete-version retention;
  cluster/index recreation and outage/replay recovery remain unproved operationally.
- `docs/services/search_service.md` describes timestamp-only versioning, while code
  already prefers aggregateVersion. Documentation outside scope is not changed.
- Requested Promotion module pattern is not present in this checkout (only
  `promotion-service` exists). Existing Restaurant domain/build-parent conventions
  provide the equivalent new-module layout and inherited 85% JaCoCo gates.

## Slice 1 validation (historical)

`mvn -B -pl :search-service -am clean verify` exited 0, BUILD SUCCESS
(43.539 s). Final log: `/tmp/search-slice1-final-verify.log`.

- Domain: 27 tests, 0 failures/errors/skips; JaCoCo LINE 27/27 and BRANCH 38/38
  (100% each); inherited 0.85 line/branch checks passed.
- Host: 38 discovered tests, 33 executed, 0 failures/errors, 5 Docker-only skips.
- Skipped because Docker was unavailable:
  - `ElasticsearchProjectionConcurrencyIntegrationTest.exactReplayUpgradesLegacyCheckpointButChangedActionStillFailsClosed`
  - `ElasticsearchProjectionConcurrencyIntegrationTest.newerProjectionWinsWhenTwoReplicaClaimsAndWritesRaceAcrossPartitions`
  - `ElasticsearchProjectionConcurrencyIntegrationTest.exactReplayCanRepairAfterProjectionFailureButContradictoryReuseFailsClosed`
  - `ElasticsearchProjectionConcurrencyIntegrationTest.deleteTombstonePreventsAnAlreadyClaimedOlderUpsertFromResurrectingTheDocument`
  - `SearchKafkaElasticsearchIntegrationTest.kafkaReplayReorderAndContradictoryReuseConvergeAcrossTwoSearchReplicas`

Existing HTTP/query/checkpoint/config tests passed. New tests cover every required
metadata field, null/blank/unsupported labels, case/locale, error precedence,
payload and soft-delete boundaries, aggregate-version precedence, epoch/nanosecond
arithmetic and multiply/add overflow. Host regressions verify delegation errors
before side effects and unchanged external-version request parameters. Source
comparison confirmed only pure rule delegation changed in production adapters.
No Kafka consumer configuration or docs/plans files were edited. No git command
was run; branch creation/verification remains with the parent under the requested
filesystem-only workflow.


## Slices 2–4 validation (current)

`mvn -B -pl :search-service -am clean verify` exited 0, BUILD SUCCESS
(01:26 min), finished 2026-10-06T18:20:17+07:00.
Final log: `/tmp/search-slice2-final-verify.log`.

| Module | Tests discovered | Failures / errors | Skips | JaCoCo LINE | JaCoCo BRANCH |
| --- | ---: | --- | ---: | --- | --- |
| search-domain | 32 | 0 / 0 | 0 | 60/60 (100%) | 54/54 (100%) |
| search-application-api | 1 | 0 / 0 | 0 | 6/6 (100%) | 2/2 (100%) |
| search-application | 10 | 0 / 0 | 0 | 27/27 (100%) | 8/8 (100%) |
| search-service | 53 | 0 / 0 | 5 | Existing host report generated | Existing host report generated |

All three extracted modules passed inherited 0.85 line/branch checks. Host executed
48 tests. The five Docker-only skips are the same named methods listed in slice 1
validation; current Surefire XML records `disabledWithoutDocker is true and Docker
is not available` for each. No live Kafka/Elasticsearch recovery proof is claimed.

Coverage includes noop decision/error precedence; legacy local/offset parsing and
cause/suppressed errors; bounded claim/CAS conflict retries; missing/corrupt storage
responses; projection admission before side effects; APPLY/EXACT_REPLAY/STALE and
legacy null fixture claims; original input identity; successful/failed DELETE metrics;
write/telemetry exception boundaries and fatal errors; query resolution/read failure
boundaries, original result identity, pageable/sort and message/cause preservation.
Host fingerprint proof includes a fixed SHA-256 golden value, event-ID exclusion,
case equivalence, every projection field and sorted nested payloads. Existing HTTP,
health and consumer-config tests remain green. Defect regressions preserve raw-string
checkpoint ordering, disagreement with aggregateVersion, physical DELETE request,
and PUT 404 acceptance.

An initial verify exposed five unfinished Mockito stubs in the inherited partial
slice tests. Responses/exceptions are now constructed before opening their stubs;
final clean verification has no failures/errors. Final source/build review confirmed
framework-free extracted modules and host-only Jackson/Elasticsearch/Spring adapters.
No git commands, Kafka consumer configuration edits, docs/plans edits or relocation
were performed. Remaining work is slices 5–6 and environment-supported runtime proof.
