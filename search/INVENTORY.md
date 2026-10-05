# Search inventory and ordered extraction

Scope: equivalence-only Search tranche. Authority: `docs/services/search_service.md`,
`docs/plans/active/service-architecture-consolidation.md`, and the existing host
implementation/tests. No new policy, flags, topics, storage or consumer configuration.
Paths below are relative to `search-service/src/main/java/com/delivery/search_service/`
unless prefixed otherwise. Line references describe the post-slice-1 source;
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
- Claim decisions stay in `consumer/ElasticsearchEntitySyncCheckpointStore.java:71`
  and `:123`: APPLY, EXACT_REPLAY, STALE, contradictory metadata/payload, equal-time
  conflicts, missing/corrupt checkpoint and regressed claim.
- Legacy timestamp parsing stays at checkpoint store `:173`: parse local time,
  then offset time retaining local fields; same messages, causes and suppressed
  exceptions. Legacy fingerprint upgrade at `:191` uses seq_no/primary_term CAS.
- Document index selection and payload conversion stay in
  `consumer/ElasticsearchSearchProjectionWriter.java:36` and `:87`: restaurant/dish
  indices, overwrite payload ID with envelope ID, DELETE versus PUT.
  Version calculation formerly `:77` moves to
  `search/domain/src/main/java/com/delivery/search/domain/EntitySyncRules.java:45`: aggregate version
  takes precedence; otherwise UTC epoch seconds * 1e9 + nanos, checked arithmetic.
  The adapter retains the nonpositive-version check and its existing ordering.
- Query availability stays in `service/SearchService.java:22` and `:35`: absent
  repository or runtime failure throws SearchUnavailableException; no empty-result
  fallback. Repositories search name OR description with pageable, without new sort.
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

1. **Current slice:** create framework-free `search/domain` before host in reactor,
   host dependency; move envelope admission and version arithmetic with exhaustive
   rule/boundary tests and host delegation regressions. 85% line/branch gates.
2. Extract checkpoint replay classification/time normalization into pure policy;
   preserve scripted ordering, legacy CAS, retry counts and exact exception contract.
3. Introduce `search/application-api` with framework-free query/projection inputs,
   outputs and ports. Keep Kafka contract and HTTP DTO mappings in adapters.
4. Introduce `search/application` for admission/claim/write and query orchestration;
   preserve fingerprint inputs, metrics, error wrapping and call ordering.
5. Move HTTP/Kafka/Jackson/Elasticsearch/security/health adapters and storage models
   into `search/infrastructure`; maintain topics, flags, indices and DB behavior.
6. Relocate host to `search/boot` (entrypoint/config only), ending in
   `search/{domain,application-api,application,infrastructure,boot}`. Keep artifact,
   DNS, runtime configuration and deployment contract `search-service`; rerun host
   verify and packaged/runtime recovery proof when environment supports it.

## Pre-existing defects / proof limits (not fixed)

- Checkpoint script (`ElasticsearchEntitySyncCheckpointStore.java:54`) orders raw
  LocalDateTime strings, while Java noop classification parses them. Optional
  seconds/fraction representations can compare differently lexicographically from
  chronological time (e.g. stored `2026-09-30T10:00:00Z` compares greater
  than a later incoming `2026-09-30T10:00:00.000000001`). Legacy normalization
  only participates after a noop.
- Checkpoint admission orders occurredAt, whereas writer prefers aggregateVersion.
  Their two clocks can disagree; neither this extraction nor unit proof resolves it.
- DELETE uses Elasticsearch physical deletion, not a retained projection document.
  Long-delayed writer fencing depends on Elasticsearch delete-version retention;
  cluster/index recreation and outage/replay recovery remain unproved operationally.
- `docs/services/search_service.md` describes timestamp-only versioning, while code
  already prefers aggregateVersion. Documentation outside scope is not changed.
- Requested Promotion module pattern is not present in this checkout (only
  `promotion-service` exists). Existing Restaurant domain/build-parent conventions
  provide the equivalent new-module layout and inherited 85% JaCoCo gates.

## Validation

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
