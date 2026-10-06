# Analytics inventory and ordered extraction

Authority: `docs/services/analytics_dashboard.md`, `docs/product/overview.md`,
`ROADMAP_MVP_TO_PRODUCTION.md`, and the existing implementation/tests. This
tranche is equivalence-only: no new product policy, ownership enforcement,
consumer configuration, message, topic, flag, migration or database behavior.
Full entrypoint paths below are relative to `analytics-service/src/main/java/`.
Short package paths in the remaining sections are relative to
`analytics-service/src/main/java/com/delivery/analytics_service/`; `db/migration/`
Java paths are relative to `analytics-service/src/main/java/`.
Line references identify the inspected host baseline (the scheduler gains one
import in slice 1). Promotion's proposed domain module is not present in this
checkout; use the existing `settlement/domain/pom.xml` build-parent pattern with
85% line and branch gates instead. No git operations or plan-file edits belong
in this task; branch creation/base verification stays with the parent.

## Entrypoints

All business HTTP endpoints, both Kafka listener beans and the reconciliation
job require `app.analytics.processing-enabled=true`. The default in
`analytics-service/src/main/resources/application.properties:27` is
`${ANALYTICS_PROCESSING_ENABLED:false}`. The application entrypoint is
`com/delivery/analytics_service/AnalyticsServiceApplication.java:10`; scheduling
is enabled there. Services/repositories/security/configuration are still wired
when the capability is off.

| Transport | Contract | Entrypoint |
| --- | --- | --- |
| HTTP GET | `/api/analytics/dashboard/admin` | `com/delivery/analytics_service/controller/DashboardController.java:28` |
| HTTP GET | `/api/analytics/dashboard/restaurant/{restaurantId}` | `com/delivery/analytics_service/controller/DashboardController.java:53` |
| HTTP GET | `/api/analytics/dashboard/my-restaurant` | `com/delivery/analytics_service/controller/DashboardController.java:70` |
| HTTP POST | `/api/analytics/reconcile?date=...` | `com/delivery/analytics_service/controller/DashboardController.java:92` |
| Kafka | `order.created` | `com/delivery/analytics_service/listener/OrderEventListener.java:44` |
| Kafka | `order.status-updated` | `com/delivery/analytics_service/listener/OrderEventListener.java:77` |
| Kafka | `order.cancelled` | `com/delivery/analytics_service/listener/OrderEventListener.java:111` |
| Kafka | `payment.completed` | `com/delivery/analytics_service/listener/PaymentEventListener.java:41` |
| Kafka | `payment.failed` | `com/delivery/analytics_service/listener/PaymentEventListener.java:69` |
| Scheduled | local clock, `0 5 0 * * *`, previous day | `com/delivery/analytics_service/scheduler/StatsReconciliationJob.java:54` |

`common/KafkaTopicConstants.java:18` also declares `delivery.status-updated`,
which has no Analytics listener. Kafka topic values are declared at lines 9–15.

## Decisions and ownership

- `controller/DashboardController.java:33`: admin dashboard requires ADMIN;
  normalizes period with trim/lowercase, accepts month/quarter/year, validates
  year 2000–2100, and retains exact response messages. Lines 59 and 77 allow
  ADMIN or SHOP_OWNER for restaurant dashboards. The latter requires an explicit
  restaurant ID (line 83), rather than resolving it from the actor. Reconcile
  requires ADMIN (line 95), then parses ISO LocalDate (line 99).
- `security/SecurityConfig.java:18`: stateless RS256/JWT resource-server chain;
  actuator permitted, other requests authenticated; controller applies roles.
  **No restaurant-to-actor ownership lookup exists.** Order owns item snapshots;
  Analytics owns receipts and derived daily statistics, not order state.
- `listener/AnalyticsEventPayload.java:11`: object JSON only; required positive
  integral long IDs at line 23; optional IDs remain null at line 31; payment
  amount absent/null becomes zero, otherwise finite JSON number at line 36.
  Order created requires userId; payment userId is optional. Order listeners
  parse totalPrice as BigDecimal with absent/null zero (OrderEventListener:53).
  Status uses `status` then `newStatus`, and only case-insensitive DELIVERED
  projects (lines 82–92); other statuses ACK without projection.
- `service/EventProcessingService.java:76,117,165,209,236`: five transactional
  processing operations. Claim receipt first, then platform followed by restaurant
  writes. Created/cancelled apply items after order counters. Payment aggregates
  are platform-only. PostgreSQL URL detection (line 359) chooses atomic native
  upsert; other databases use read-modify-save. Pending decrements clamp at zero;
  delivered revenue uses null-as-zero and average uses scale 0 HALF_UP (line 444).
- `service/EventProcessingService.java:381`: dedup key prefers nonblank textual
  eventId, namespaced by event type; legacy fallback is type + positive orderId.
  Line 363 hashes exact raw UTF-8 payload with SHA-256. Line 372 validates optional
  positive integral aggregateVersion; it is identity, not an ordering fence.
- `service/AnalyticsReplayPolicy.java:11`: exact receipt comparison includes
  type/order/user/restaurant/name/status/payment method/version, scale-insensitive
  amount, and fingerprint (legacy null fingerprint falls back to exact raw text).
  Contradiction throws IllegalArgumentException with the existing exact message.
- `service/AnalyticsItemSnapshotParser.java:15`: absent/null items are legacy
  no-op; array <=100 lines; positive integral menuItemId/quantity; positive price
  with <=2 decimals, unitPrice before price fallback; lineTotal must equal
  quantity * unitPrice; blank/missing name becomes UNKNOWN. Entire list validated
  before the first item write. No restaurant ID means no item validation/write
  (`EventProcessingService.java:264`).
- `service/EventProcessingService.java:311`: item stat date selects occurredAt,
  eventTimestamp, createdAt in that order, parsed as LocalDateTime; otherwise
  today. Order/payment daily counters use processing day, receipts use ingest
  time. No timezone normalization or monotonic event-version policy.
- `scheduler/OrderReconciliationAccumulator.java:13,27` (baseline): exact uppercase
  order event counting; unrelated events ignored; only delivered amounts summed;
  null delivered amount still contributes to the average denominator; pending
  max(0,created-delivered-cancelled); average scale 0 HALF_UP or zero if empty.
  Slice 1 relocates these rules to
  `analytics/domain/src/main/java/com/delivery/analytics/domain/OrderReconciliationAccumulator.java`.
- `scheduler/StatsReconciliationJob.java:72`: ingest-time day window, 500-row
  pages ascending ID; platform plus per-restaurant reducers. Line 130 resets
  unobserved existing scopes; empty day creates no new row. Line 151 overwrites
  counts and revenue and zeros shipping fees, discounts, new customers.
- `service/DashboardQueryService.java:38,74`: null year means current local year;
  quarter aggregates months, year uses all-year data, other periods use month.
  Lines 103/129 calculate average HALF_UP and delivery rate rounded to one decimal;
  processing = total - delivered - cancelled - pending, without clamping.
  Line 155 emits T1–T12 with missing-month zeros; line 172 emits Q1–Q4;
  line 198 status order is DELIVERED/CANCELLED/PENDING; line 218 requests top 10
  restaurants from delivered receipts, not payment/item rows.

## Receipt, locking, projection and recovery guarantees

- `repository/AnalyticsEventRepository.java:23`: PostgreSQL INSERT ON CONFLICT
  DO NOTHING claims the unique deduplication key. Losing concurrent claim reads
  committed winner and checks exact replay (`EventProcessingService.java:345`).
  Exact replay writes nothing; contradictory replay rejects before projection.
  Non-PostgreSQL saveAndFlush uses uniqueness but has no equivalent conflict
  recovery. No explicit pessimistic or optimistic JPA locks are configured.
- `db/migration/V1__analytics_schema.java:57,59,61`: unique receipt key and
  PostgreSQL UNIQUE NULLS NOT DISTINCT date/restaurant scope constraints (including
  platform null). Existing duplicate groups fail migration rather than silently
  reconcile. V2 adds payload fingerprint; V3 adds unique item scope; V4 adds
  positive aggregateVersion check/index. V4's filename says tombstone but it
  adds no tombstone state or retention/deletion mechanism.
- Native order/revenue/item SQL increments in
  `repository/DailyOrderStatsRepository.java:20,34,53`,
  `repository/DailyRevenueStatsRepository.java:19,32`,
  `repository/DailyItemSalesRepository.java:23` use ON CONFLICT updates to avoid
  lost increments. Receipt and all projection writes share each processing
  transaction; malformed item snapshot rolls the whole transaction back.
- Listener ACK happens only after transactional service return. Validation
  IllegalArgumentException propagates unchanged; other failures become existing
  IllegalStateException messages and remain retryable. Retry annotations use
  4 attempts, exponential 1000ms/2x/max10000ms defaults, `-retry-analytics` suffix,
  `.analytics.DLT`, no auto-create, excluding IllegalArgumentException.
  `config/KafkaConsumerConfig.java:66` uses MANUAL_IMMEDIATE ACK; recovery uses
  same partition owner DLT, send-result failure propagation and commitRecovered
  (`:92,93,101`). Recovery producer has all ACKs and idempotence (`:77,78`). Consumer
  configuration is outside the editable behavioral scope of this tranche.
- No business outbox or outbound business publisher exists. The KafkaTemplate
  is retry/DLT infrastructure, not an analytics output stream.
- Reconciliation is transactional for both direct and scheduled paths. It
  rebuilds only order aggregates from accepted receipts, leaves receipts intact,
  and propagates failures. It cannot recover never-ingested events or rebuild
  payment/item projections. There is no scheduler/distributed lock or serialization
  against concurrent ingestion; PostgreSQL race/recovery proof remains separate.
- Default-off switches also include SERVICE_DISCOVERY_ENABLED, CONFIG_SERVER_FAIL_FAST,
  JPA_SHOW_SQL, HIBERNATE_FORMAT_SQL in application.properties. Kafka listener
  auto-startup defaults true in config:40, but capability-off removes listeners.

## Ordered slices

1. **Complete (slice 1):** new framework-free analytics-domain reactor module before the
   host, 85% JaCoCo line/branch gates; relocate the pure reconciliation reducer,
   preserving input order, exception behavior and numeric results; exhaustive
   domain tests plus existing host paging/transaction integration proof.
2. **Complete (slice 2):** Extract immutable receipt identity/replay decisions into domain values; host
   maps JPA receipts. Preserve fingerprint legacy fallback, exceptions and claim
   winner handling. Add decision truth tables before changing orchestration.
3. **Complete (slice 2):** Separate JSON parsing from framework-free item/version/date decisions; keep
   Jackson, timestamps and exact malformed-payload errors at the adapter boundary.
4. **Complete (slice 2):** Extract dashboard aggregation values and mapping policies; preserve labels,
   series/status ordering, rounding and current authorization behavior.
5. **Complete (slice 2):** Introduce `analytics/application-api` ports/use-case contracts and
   `analytics/application` orchestration for ingestion, querying and reconciliation.
   Retain transaction/ACK ordering, scope reset, SQL behavior and default-off gates.
6. Move HTTP/Kafka/JPA/Jackson/security/scheduling adapters and composition into
   `analytics/infrastructure`; migrations/resources remain infrastructure-owned.
   Prove schema, runtime wiring and rollback/replay parity without altering Kafka
   consumer configuration as part of structural extraction.
7. Relocate the host entrypoint/configuration to `analytics/boot`, keeping artifact,
   DNS, port and deployment contracts `analytics-service`. Final layout:
   `analytics/{domain,application-api,application,infrastructure,boot}`; remove
   the old host path only with packaged runtime and focused recovery evidence.

## Pre-existing defects and limitations (preserved)

- Restaurant endpoints permit any SHOP_OWNER to supply any restaurantId without
  ownership verification (DashboardController:59,77–87). The default-off flag
  explicitly names ownership gates as outstanding; extraction does not authorize
  a new access policy.
- Restaurant period/year parameters bypass admin endpoint validation/normalization
  (DashboardController:65,87); unsupported periods silently select monthly data.
- Ingestion and reconciliation may race: no distributed reconciliation lock and
  ingest/reconcile use different date sources for item vs order counters. These
  are recovery limitations, not fixed by this slice.
- Payment projection converts JSON amounts through double (AnalyticsEventPayload:36,
  EventProcessingService:212), preserving existing precision characteristics.
- No long-counter overflow guard exists in the reducer. Null eventType throws
  NullPointerException, unknown/case-mismatched types are ignored, and negative
  delivered amounts are summed; preserve these inputs rather than invent policy.

## Slice 1 validation

`mvn -B -pl :analytics-service -am clean verify` completed with BUILD SUCCESS,
exit 0, on 2026-10-06 (55.504 seconds). Reactor: 200 tests reported, 0 failures,
0 errors, 5 skips. Domain: 24 cases, 0 skips; host: 145 cases, 5 skips. Domain
JaCoCo: 14/14 lines and 8/8 branches covered (100% each); inherited verify-phase
85% line/branch checks passed. Host paging (3 cases) and transaction (5 cases)
proof passed, including scheduled/direct failure propagation, rollback,
repeat overwrite, stale scope reset and empty-day preservation.

Docker was unavailable to Testcontainers. The five Docker-only skips are all in
`AnalyticsReceiptPostgresIntegrationTest`:

- `v3UpgradePreservesRawReceiptAndEnforcesPositiveVersion`
- `rejectedItemRollsBackReceiptAndCountersThenCorrectedRetryAggregatesOnce`
- `contradictoryReplayPreservesCommittedReceiptAndProjection`
- `distinctConcurrentReceiptsDoNotLosePlatformOrRestaurantIncrements`
- `concurrentExactReplayClaimsOneReceiptAndAggregatesOnce`

Proof artifacts: `analytics/domain/target/site/jacoco/{jacoco.xml,jacoco.csv}`,
module `target/surefire-reports/` directories, and
`/tmp/analytics-slice1-verify.log`. Final filesystem diff inspection confirms
root registration and host dependency plus a single scheduler import; reducer
source is byte-identical after normalizing package/public visibility. The old
host reducer/test are removed; the domain test retains their cases and expands
boundary coverage. Kafka configuration, listeners, persistence and flags have
no source edits. No git commands were run; branch/base remains parent-owned.

## Slice 2 extraction (ordered slices 2–5)

- `analytics/domain`: immutable `ReceiptIdentity` owns exact replay comparison;
  `ReceiptKey` owns namespaced identity/fallback. `SnapshotDecisions` owns item
  size, positivity, price scale, line reconciliation, name normalization, item
  deltas and first-present timestamp selection. `OrderProjection` and
  `PaymentProjection` own existing read/modify/save arithmetic. `DashboardValues`
  owns typed totals, overview, monthly/quarterly/yearly mapping and statuses.
- `analytics/application-api`: ingestion command/receipt/payload/projection ports,
  typed dashboard query/read contracts, paged reconciliation receipt/scope ports
  and use-case results. No JPA entities, raw SQL rows, JSON nodes, Spring, Kafka
  or HTTP types cross the application boundary. The scope overwrite handle is
  host-owned so reconciliation preserves the existing row identity.
- `analytics/application`: `IngestionService` sequences claim, date resolution,
  platform/restaurant writes and complete item parsing; exact replay returns
  before clock/payload/projections. `DashboardService` preserves two separate
  overview reads, query order, local default year, top-ten delivered receipts and
  period fallback. `ReconciliationService` pages 500 receipts and scopes, resets
  unobserved rows, then overwrites platform and restaurant reductions.
- Host services and scheduler delegate to these contracts. The host retains
  Spring transactions (both direct and scheduled), JPA/native upsert adapters,
  claim conflict recovery, SHA-256, Jackson shape/number parsing and timestamp
  syntax/errors, payment double conversion, response DTO mapping and logging.
  Controllers, listeners, Kafka consumer configuration, flags, migrations and
  deployment contracts retain their existing behavior and location.
- Both new modules inherit 85% line/branch verify gates, matching domain. The
  pre-existing host POM reports coverage without a check; it is not weakened.

Additional baseline quirks characterized during this slice (preserved, not new
regressions): any accepted receipt, including an unrelated/payment receipt,
marks its platform/restaurant scope observed during reconciliation and can
create zero order rows. A negative pending counter is retained by ingestion's
`pending > 0` decrement rule. Null restaurant IDs passed directly to the query
service retain restaurant query semantics rather than becoming platform reads.
These have explicit domain/application/host regression cases. The absence of a
reconciliation lock remains a concurrency limitation; date divergence and lack
of a version ordering fence have deterministic regression evidence.

Remaining work is ordered slices **6 and 7** only: adapter/composition relocation
into infrastructure, then boot/artifact relocation with packaged runtime/schema
and PostgreSQL recovery proof. Docker concurrency proof remains outstanding
when Testcontainers cannot start. No git commands or plan files were used.

### Slice 2 final validation

`mvn -B -pl :analytics-service -am clean verify` completed with **BUILD SUCCESS**,
exit **0**, on 2026-10-06 at 19:17:09 +07:00 (47.955 seconds). Reactor reports:
**279 tests, 0 failures, 0 errors, 5 skips**. Domain: 60; application-api: 1;
application: 32; host: 155 (5 skips); dependency modules: 31.

JaCoCo verify gates passed without exclusions or threshold changes:

| Module | Lines covered/total | Branches covered/total |
| --- | --- | --- |
| analytics-domain | 91/91 (100%) | 100/100 (100%) |
| analytics-application-api | 6/6 (100%) | 0/0 (no executable branches) |
| analytics-application | 68/68 (100%) | 39/39 (100%) |

The five skips remain exactly the Docker-only
`AnalyticsReceiptPostgresIntegrationTest` cases listed under slice 1.
Testcontainers reported no valid Docker environment. This does not establish
PostgreSQL concurrency or native SQL rollback proof. Existing mock claim-winner
and native-path tests passed; all 3 new H2 `AnalyticsIngestionTransactionTest`
cases passed (invalid second item rolls back receipt/counters; corrected same-key
retry and exact replay apply once; restaurant write failure rolls back platform
and receipt; payment write failure rolls back and permits corrected retry).
Existing host paging (3 cases) and reconciliation transaction (5 cases) passed.

Domain replay truth tables cover every identity field, null/present amounts,
scale equivalence, fingerprint precedence and legacy raw-text fallback.
Application tests cover all five ingestion operations, claim-first ordering,
platform-before-restaurant writes, replay short circuit, complete item parsing,
operation failures, every query period/fallback, two-read behavior, both paged
reconciliation sources, empty days, stale scopes and failure propagation.
Host regression tests preserve ownership/parameter-validation gaps, explicit
my-restaurant ID, source payment precision loss, event/processing date divergence,
and absence of aggregate-version ordering enforcement. Existing reducer tests
continue to preserve unknown/case-mismatched event types, null type, negative
amounts and overflow behavior. No introduced behavioral defect was found.

Evidence: `/tmp/analytics-slice2-verify.log`, each module's
`target/surefire-reports/`, and the three extracted modules'
`target/site/jacoco/{jacoco.xml,jacoco.csv}`. Final host filesystem comparison
against the pre-edit snapshots is at `/tmp/analytics-slice2-host.diff`.
Filesystem inspection confirms only the assigned source/POM/inventory surfaces
were edited; Kafka consumer configuration, listener/controller production code,
repository SQL, migrations, flags and plan files have no edits.
