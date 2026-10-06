# Livestream inventory and ordered extraction

Authority: `docs/services/livestream_management.md`, default-off checkpoint in
`ROADMAP_MVP_TO_PRODUCTION.md`, and existing code/tests. This is an equivalence
tranche: retain messages, exception classes, validation order, topics, flags,
transactions, migrations and persistence. Paths below are relative to
`livestream/infrastructure/src/main/java/com/delivery/livestream_service/` unless stated.
Line references describe the pre-extraction baseline (checkout service line
numbers change in slice 1). No git operation or branch manipulation is part of
this filesystem-only task; the parent owns branch provenance.

## HTTP entrypoints

All controllers are conditional on `app.livestream.api-enabled=true`.
The namespace is `/api/livestreams`.

| Method and suffix | Entrypoint file:line | Authority / effect |
|---|---|---|
| POST (root) | controller/LivestreamController.java:36 | Actor + host/restaurant ownership, create AGORA room |
| POST /{id}/start | controller/LivestreamController.java:46 | Host/restaurant authority then room seller permission, CREATED → LIVE |
| POST /{id}/join | controller/LivestreamController.java:57 | Authenticated actor, LIVE viewer token; ADMIN does not count view |
| POST /{id}/end | controller/LivestreamController.java:67 | Owning host only; cross-owner ADMIN must moderate |
| GET /active | controller/LivestreamController.java:81 | Authenticated, newest LIVE rooms, bounded 100 |
| GET /{id} | controller/LivestreamController.java:89 | Authenticated room inspection |
| GET /seller/{sellerId} | controller/LivestreamController.java:97 | Authenticated seller history, bounded 100 |
| GET /restaurant/{restaurantId} | controller/LivestreamController.java:105 | Host/restaurant authority, bounded 100 |
| POST /{id}/products/pin | controller/LivestreamProductController.java:39 | Owning host + canonical product authority |
| DELETE /{id}/products/{productId}/pin | controller/LivestreamProductController.java:50 | Owning host, unpin |
| DELETE /{id}/products/{productId} | controller/LivestreamProductController.java:61 | Owning host, tombstone |
| GET /{id}/products | controller/LivestreamProductController.java:72 | Authenticated, visible products, bounded 100 |
| GET /{id}/products/pinned | controller/LivestreamProductController.java:80 | Authenticated, pinned visible products, bounded 100 |
| POST /{id}/token/renew | controller/LivestreamTokenRenewalController.java:45 | LIVE, server-derived HOST/VIEWER and fixed 3600s TTL |
| POST /{id}/token | controller/StreamTokenController.java:30 | Always denied; legacy caller-controlled token boundary |
| GET /admin | controller/LivestreamAdminController.java:32 | ADMIN, page ≥ 0, size 1–100, createdAt DESC then id DESC |
| POST /{id}/moderation | controller/LivestreamModerationController.java:26 | ADMIN, validated WARN/UNPIN/FORCE_END with reason |
| POST /internal/checkout-quote | controller/InternalLivestreamCheckoutController.java:34 | Internal-Token, scoped LIVE prices; missing pins omitted |
| POST /internal/order-context | controller/InternalLivestreamCheckoutController.java:45 | Internal-Token + actor/correlation/key, immutable persisted context |

## Kafka and other boundaries

There are **no Kafka consumer entrypoints**, retry listeners, scheduled jobs or
Kafka consumer configuration in this host's source. Do not add/change consumer
configuration during extraction. `service/LivestreamEventPublisher.java:22,27,32,38`
contains the started/ended/pinned/unpinned publisher entrypoints, but every send
is commented out. `common/constants/KafkaTopicConstants.java:6-9` retains
`livestream.started`, `livestream.ended`, `livestream.product.pinned`, and
`livestream.product.unpinned`; intended keys are room UUID strings. No active
Kafka projection, outbox, broker acknowledgement or realtime delivery guarantee
exists. The legacy `livestream_events` table is not an active outbox.

Outgoing restaurant authority: `client/RestaurantOwnershipClient.java:31`
checks principal + legacy owner IDs and internal secret, fails closed on errors;
`client/LivestreamProductAuthorityClient.java:36` checks availability/scope and
canonical metadata with 3s connect/read timeouts and correlation propagation.
Agora SDK calls remain in `service/StreamTokenService.java:68` and `config/agora/`.

## Decision locations and invariants

- `service/LivestreamHostAuthorization.java:16`: ADMIN or SHOP_OWNER with user
  identity; ADMIN bypasses restaurant lookup, shop owner uses restaurant service.
- `service/LivestreamService.java:51,73,123,184,246`: AGORA only, CREATED start,
  LIVE end/join, ADMIN seller-permission bypass at service level, host/viewer token
  TTL 3600s, cumulative view increment (null → zero) only when countView=true.
  Reads at :159,169,227,237 keep existing sorting and 100-item bounds.
- `controller/LivestreamController.java:67` and
  `controller/LivestreamProductController.java:88`: ordinary end/product writes
  require room ownership even for ADMIN; moderation is the cross-owner path.
- `controller/LivestreamTokenRenewalController.java:46`: actor/status checks,
  owning host gets HOST; another ADMIN/customer gets VIEWER. A shop owner host
  is reauthorized against restaurant ownership.
- `service/StreamTokenService.java:41`: VIEWER requires LIVE; token UID remains
  legacy user ID narrowed to int, TTL supplied internally. Invalid credentials
  wrap as IllegalStateException with the existing Vietnamese message.
- `service/LivestreamProductService.java:58,119,152,193`: permission before
  scope/status/product lookup; CREATED/LIVE only, duplicate pin is 409, canonical
  metadata replaces copied fields. Repin restores existing tombstoned row and
  keeps its ID. Unpin keeps row; remove sets isPinned=false and deletion fields.
- `dto/request/ModerateLivestreamRequest.java:15` and
  `service/LivestreamModerationService.java:26,33`: ADMIN, reason trimmed only
  after request validation, target only for UNPIN; WARN audit only, UNPIN may
  repeat, FORCE_END requires LIVE. No moderation idempotency key.
- `service/LivestreamCheckoutQuoteService.java:59,89,167,177` baseline: request
  scope first (context headers precede scope), then room/status/restaurant,
  requested pins in request order. Quote omits unavailable ordinary items;
  context requires every requested pin and valid snapshot ID/price/scope.
  Quote validates scope before price with distinct IllegalStateException
  messages; context combines snapshot failures into one message.
- `service/LivestreamCheckoutQuoteService.java:143,150`: SHA-256 fingerprint of
  room:restaurant:ordered product list:actor; matching key restores complete
  original payload before reading current room/pins. Correlation is stored but
  excluded from fingerprint. Conflicting fingerprint is IllegalArgumentException.
- `mapper/LivestreamMapper.java:14`: maps entity fields and pinned products;
  entity association filtering and response shape remain host concerns.

## Persistence, locks, receipts and guarantees

- `entity/Livestream.java:19` uses generated UUID identity; :70 supplies
  room/channel/timestamps in JPA callbacks. Lifecycle/product mutation methods are transactional. No
  pessimistic lock or optimistic @Version exists on rooms or products; view
  increments are read-modify-write, so concurrent joins can lose increments.
- `repository/LivestreamProductRepository.java:17-34`: ordinary queries exclude
  deletedAt rows; explicit including-deleted lookup supports repin. V1 migration
  (`../db/migration/V1__livestream_schema.java:49`) fences duplicate room/channel
  and (room, product) identities. This uniqueness does not serialize lifecycle
  decisions or turn duplicate concurrent pin failures into a stable 409.
- `service/LivestreamModerationService.java:32-41`: action and saveAndFlush audit
  share one transaction; audit failure rolls back state. Repeated WARN/UNPIN
  creates another audit. No token revocation, viewer eject or Kafka delivery.
- `entity/LivestreamCheckoutReceipt.java:19` and V4 migration enforce unique
  (actor_principal_id, idempotency_key). `service/LivestreamCheckoutReceiptWriter.java:18`
  uses REQUIRES_NEW/saveAndFlush; concurrent duplicate insert rolls back its own
  transaction, then the reader restores first committed snapshot. Payload is
  immutable by application mapping; no purge/retention job is introduced.
- quote uses readOnly transaction; orderContext is not one enclosing
  transaction. Room/pin reads are not a locked atomic snapshot with concurrent
  pin/end mutations. Receipt retry durability is not proof of such isolation.
- Migrations V1–V4, H2 schema/integration tests and Docker PostgreSQL receipt
  test remain host-owned. No DDL, DB behavior or projection changes in slice 1.

## Default-off flags and deferred product choices

`src/main/resources/application.properties:28` uses
`LIVESTREAM_API_ENABLED:false`; all HTTP controllers use that independent gate.
Gateway `app.livestream.client-api-enabled` and Order
`ORDER_LIVESTREAM_CHECKOUT_ENABLED` remain false by contract (outside this scope).
Legacy token route remains denied even when the service gate is enabled.
Discovery defaults false; API flag does not gate all service bean creation.
Agora credentials and internal secret are environment inputs with empty defaults.
Cooldown properties 60s/10s in application.properties have no enforcement caller
in this host. Concurrent viewer/chat/heartbeat policy remains decision-gated;
join count is cumulative and currentViewers is not measured.

## Ordered slices

1. **Complete (slice 1):** create framework-free `livestream/domain` before host in
   reactor, apply 85% line/branch JaCoCo gates, move checkout scope, context
   metadata and stored quote/context snapshot validation with exhaustive tests.
   Keep nullable host request handling, DTO mapping and persistence in host.
2. **Complete (this tranche):** Extract lifecycle/provider/product eligibility and permission rules with
   enum/exception compatibility adapters; prove all transition/error ordering.
3. **Complete (this tranche):** Extract token role/TTL/UID decisions and moderation decisions; retain Agora,
   actor conversion, audit transaction and request validation at adapters.
4. **Complete (this tranche):** Extract receipt/replay and price-context decisions without changing ordered
   fingerprint bytes, JSON payload, unique constraint or REQUIRES_NEW recovery.
5. **Complete (this tranche):** Introduce `application-api` commands/results/ports, then `application` use
   cases for lifecycle, products, moderation and checkout; preserve transaction
   boundaries and bounded reads with host adapters and tests.
6. **Remaining:** Relocate transport, auth, clients, Kafka DTO/publisher, JPA, migrations and
   adapters to `livestream/infrastructure`; retain inert publisher behavior.
7. **Remaining:** Relocate composition/config/entrypoint to `livestream/boot`, preserving
   artifact/DNS `livestream-service`, runtime resources and package compatibility.
   Final tree: `livestream/{domain,application-api,application,infrastructure,boot}`.

## Pre-existing defects / limitations observed (not repaired)

- Kafka success publishers are inert; there is no outbox/delivery guarantee.
- Room transitions/product mutation and cumulative count have no lock/version;
  concurrent lost updates/stale state are possible. Context reads lack an atomic
  room/pin snapshot. No concurrency guarantee is added by this extraction.
- Service-level ADMIN bypass differs from ordinary HTTP owner checks. Direct
  service calls must retain that difference until an authorized later slice.
- Product removal assigns legacy sellerId to deletedByPrincipalId
  (`service/LivestreamProductService.java:168`), despite the principal field name.
- RestaurantOwnershipClient constructs default RestClient without explicit
  connect/read bounds (:22), unlike the product authority client.
- Channel names use restaurant ID + epoch seconds (`entity/Livestream.java:79`),
  so two rooms created for one restaurant in the same second can collide with
  the unique channel constraint. No naming change is made.
- Snapshot validation requires only non-null row ID, not positivity; zero and
  negative IDs remain accepted in pure rules (normal generated DB IDs are positive).
- Configured cooldown values are unused; concurrent viewer count is unimplemented.

## Slices 2–5 implementation

- `domain/LivestreamPolicy` owns provider, seller/host permission, lifecycle,
  product scope/status/duplicate, cumulative-count, token UID/role/TTL, moderator,
  checkout-room/completeness and replay decisions. `CheckoutFingerprint` retains
  exact UTF-8 SHA-256 input bytes, including ordered `List.toString()` formatting.
- `application-api` exposes checkout commands, room/product snapshot results and
  lifecycle, product, moderation, checkout and restaurant-ownership ports. Generic
  host handles/commands/mapped responses keep JPA entities and HTTP DTO types out
  of the application module's imports and dependencies.
- `application` orchestrates lifecycle, products, host authorization, moderation
  and checkout behind those ports. Lists request the original fixed 100 bound;
  quote omissions, requested order, replay-before-live-read and receipt recovery
  results remain unchanged.
- Host services implement ports and delegate. `LivestreamCompatibility` maps
  pure domain rejections to the existing exact exception classes and messages;
  nullable enum conversion stays at the boundary. Token renewal and Agora token
  adapters delegate role/status/TTL/UID decisions to domain.
- Transactions, JPA save/saveAndFlush, canonical restaurant HTTP lookup, response
  mapping, timestamps, inert publishers, JSON receipt serialization and the
  REQUIRES_NEW writer/duplicate-insert recovery stay in the host. Migrations,
  feature flags, secrets and Kafka consumer configuration are unchanged.
- Pure policy tests cover all statuses and permission combinations, validation
  ordering, nullable compatibility, UID narrowing, count overflow, and ordered
  fingerprint bytes. Application tests exercise ordered port interactions,
  rejection-before-write behavior, missing pins, malformed snapshots, duplicate
  rows, original-payload replay, conflict rejection and recovery-result mapping.
  Host H2 tests prove rollback and the preserved default-join limitation below;
  publisher tests explicitly prove that all four success publishers remain inert.

### Newly observed pre-existing limitations (preserved)

- The two-argument `joinLivestream(id, viewer)` overload is not transactional and
  calls the transactional three-argument overload on `this`. Spring's proxy is
  bypassed: a view increment can commit even when viewer token generation fails.
  `LivestreamLifecycleTransactionTest` preserves this behavior and separately
  proves rollback for explicit counted join and failed start. The HTTP controller
  uses the explicit counted/monitoring entrypoint, retaining its transaction.
- Legacy long user IDs are narrowed with `intValue()` without range checks;
  cumulative view-count addition can wrap at `Long.MAX_VALUE`. Pure regression
  tests retain these behaviors; this extraction adds no new identity/count policy.

## Slice 1 validation (historical)

`mvn -B -pl :livestream-service -am clean verify` passed (exit 0,
BUILD SUCCESS, 41.186s; log `/tmp/livestream-slice-verify.log`). Reactor reports
175 tests, zero failures/errors, three skips: host 108 (105 executed), domain
41, observability 16, auth resource server 8, runtime starter 2. Domain JaCoCo
covers 19/19 lines and 46/46 branches (100% each), passing both 85% gates.
Host validation regressions prove scope rejection before any repository access
for quote/context and metadata rejection before scope. Existing receipt,
ordering, HTTP, migration and authority tests remain green.

Docker-only skips in `LivestreamCheckoutReceiptPostgresIntegrationTest`
(`disabledWithoutDocker is true and Docker is not available`):
- `retryReturnsTheOriginalPriceSnapshotAfterThePinnedProductIsRetired`
- `concurrentConflictingPayloadsLeaveOneReceiptAndRejectTheLoser`
- `exactConcurrentHandoffsConvergeAndFreshServiceRestoresStoredSnapshot`

Slice 1 domain has no production dependencies; host retains all
transport/persistence classes. PostgreSQL race/recovery proof remains deferred
until Docker is available. No Kafka consumer configuration was edited.


## Slices 2–5 final validation

`mvn -B -pl :livestream-service -am clean verify` passed, exit 0,
**BUILD SUCCESS**, 1 minute 37 seconds, completed 2026-10-06 21:25:57 +07:00.
Full output: `/tmp/livestream-slice2-final-verify.log`.
Reactor: **224 tests, zero failures, zero errors, three Docker-only skips**:

| Module | Tests | Skipped | Line coverage | Branch coverage |
|---|---:|---:|---:|---:|
| livestream-domain | 50 | 0 | 64/65 (98.46%) | 104/104 (100%) |
| livestream-application-api | 1 | 0 | 3/3 (100%) | No executable branches |
| livestream-application | 29 | 0 | 99/99 (100%) | 12/12 (100%) |
| livestream-service | 113 | 3 | Existing host reporting retained | Existing host reporting retained |
| observability-starter | 16 | 0 | Existing checks | Existing checks |
| runtime-platform-starter | 2 | 0 | Existing checks | Existing checks |
| identity-contracts | 5 | 0 | Existing checks | Existing checks |
| auth-resource-server-starter | 8 | 0 | Existing checks | Existing checks |

All extracted modules retain and pass both **85% line and branch gates**.
The sole missed domain line is the impossible missing-SHA-256 provider catch;
no coverage exclusion or gate reduction was introduced. Counters come from
`target/site/jacoco/jacoco.xml`; test totals come from Surefire XML reports.
The three PostgreSQL test methods listed in the historical validation section
remain skipped because Docker is unavailable. H2 receipt, migration, moderation
rollback, lifecycle rollback, HTTP authorization/error, product authority/repin,
ordering and bounded-list tests all pass. In particular,
`LivestreamLifecycleTransactionTest` executes both rollback checks and the
preserved default-overload self-invocation failure case.

Filesystem-only source review confirms zero Spring/JPA/Kafka/host-package imports
in the three extracted modules. No Kafka consumer configuration, migration,
feature flag, `docs/plans/` file or Git state was edited. Slices **1–5 complete**;
remaining ordered work is **6 (infrastructure relocation)** and **7 (boot
relocation/runtime proof)**. Docker PostgreSQL concurrency/recovery proof remains
unexecuted in this environment; no stronger persistence guarantee is claimed.
