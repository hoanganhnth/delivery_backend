# Promotion tranche: domain policies and application orchestration

Inventory inspected before extraction on `refactor/promotion`, 2026-10-05.
Paths below are repository-root-relative and refer to the extracted tree unless
explicitly identified as the pre-extraction location. Existing behavior, not new
product policy, is the authority for this equivalence-only slice. The rollout
and ownership authority remains `docs/workflows/promotion_voucher_flow.md:3`.

## Inventory

| Surface | Entrypoints and decisions | Existing guarantees retained |
| --- | --- | --- |
| Campaign creation/validation | `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:65` (ADMIN platform), `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:76` (SHOP_OWNER shop); `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:93`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:140`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:854` | Code normalization/duplicate conflict, positive IDs/quantities/user limits, nonnegative money, ordered time window, supported creator/reward/scope/layer, required SHOP identity. Shop creation verifies ownership via `promotion-service/src/main/java/com/delivery/promotion_service/service/RestaurantOwnershipClient.java:36`, auto-approves and records ownership. No new campaign capability. |
| Admin lifecycle | `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:133` (admin list), `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:147` (approve), `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:157` (reject), `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:168` (pause), `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:177` (resume), `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:186` (delete); `promotion-service/src/main/java/com/delivery/promotion_service/service/VoucherLifecyclePolicy.java:11` | Locked approval/rejection only for pending shop vouchers; retired vouchers cannot be activated/approved; repeated retirement preserves original time/actor/reason. ADMIN deletion requires stable principal. Read lists exclude retired rows before the 100-row limit. |
| Customer wallet eligibility | `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:92` (collect), `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:105` (wallet); `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:166`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:758`; formerly host `WalletVoucherPolicy.java:15`, now `promotion/domain/src/main/java/com/delivery/promotion/domain/WalletVoucherPolicy.java:15` | USER-owned wallet, principal-first dual-write/read with matching unbackfilled legacy fallback. Collection rejects malformed, retired, unapproved, inactive, future-start, expired and exhausted rows; ALL/SHOP only, PLATFORM/SHOP only. Collection and reservation retain their distinct validation order/messages/end-time boundary. |
| Checkout eligibility and layer selection | `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:196`; `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:222`; formerly host `VoucherStackingCalculator.java:238`, now `promotion/domain/src/main/java/com/delivery/promotion/domain/VoucherStackingCalculator.java:237`; layer resolution `promotion/domain/src/main/java/com/delivery/promotion/domain/VoucherLayerResolver.java:13` | Wallet membership, approval, active/time/capacity/minimum/shop checks; legacy MERCHANT/CATEGORY quarantine, explicit-layer validation and compatible legacy derivation. AUTO maximizes discount, ties by earliest expiry then layer-ordered IDs; MANUAL rejects unavailable/missing/duplicate IDs and multiple vouchers in one layer. Null mode remains AUTO. |
| Pricing, rounding and caps | Formerly host `PromotionService.java:739` and `VoucherStackingCalculator.java:307`; now `promotion/domain/src/main/java/com/delivery/promotion/domain/LegacyDiscountCalculator.java:9` and `promotion/domain/src/main/java/com/delivery/promotion/domain/VoucherStackingCalculator.java:305` | FIXED capped by subtotal; percentage capped by its item base; FREESHIP capped by gross shipping; optional max cap and zero floor. Legacy percentage divides by 100 exactly then rounds at the end; stacking divides at scale 2 HALF_UP before caps and rounds final values. SHOP first, PLATFORM on remaining food base, FREESHIP separately. Canonical funding by layer, immutable result lists, strict final-total > gross-shipping payable guard remain unchanged. |
| Legacy reserve/redemption | `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:212`, `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:239`, `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:249`; `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:593`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:650`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:672` | Stable UUID/order identity and exact fingerprint replay; wallet and voucher pessimistic locks; capacity increment once, SAVED -> RESERVED -> USED, release restores capacity/wallet; conflicting replay fails closed. Fifteen-minute hold. Commit rejects expired/released holds; compensation can restore committed usage. |
| Multi-layer reserve/redemption | `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:231`, `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:259`, `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:270`; `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:297`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:402`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:437`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:481` | Sorted voucher locks, one parent/order and unique reservation/voucher lines, immutable price/funding snapshots, per-user reserved/used counters, replay fingerprint and principal checks, local transaction includes outbox. No repository/transaction code extracted in slice 1. |
| Order events/refund compensation | `promotion-service/src/main/java/com/delivery/promotion_service/listener/OrderReservationEventListener.java:30`; `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionOrderReservationEventProcessor.java:60`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionOrderReservationEventProcessor.java:132` | `order.created` -> COMMIT; `order.cancelled`/`order.refund-eligible` -> RELEASE. After PICKED_UP/DELIVERING/DELIVERED/COMPLETED, compensation retains usage. ACK after successful transactional processor return; SHA-256 raw-payload receipt and atomic event-ID claim prevent contradictory replay. Retry topic canonicalization, retry suffix `-retry-promotion` and owner DLT `.promotion.DLT` unchanged. |
| Locks/idempotency/storage | `promotion-service/src/main/java/com/delivery/promotion_service/repository/UserVoucherRepository.java:44`, `promotion-service/src/main/java/com/delivery/promotion_service/repository/VoucherRepository.java:22`, `promotion-service/src/main/java/com/delivery/promotion_service/repository/VoucherReservationRepository.java:16`, `promotion-service/src/main/java/com/delivery/promotion_service/repository/PromotionReservationRepository.java:20`, `promotion-service/src/main/java/com/delivery/promotion_service/repository/PromotionOrderReservationReceiptRepository.java:24`; migrations `promotion-service/src/main/resources/db/migration/V2__voucher_reservations.sql:1`, `promotion-service/src/main/resources/db/migration/V4__promotion_order_reservation_receipts.sql:1`, `promotion-service/src/main/resources/db/migration/V7__voucher_stacking_foundation.sql:22` | PostgreSQL unique reservation/order/receipt identities, pessimistic row locks and ON CONFLICT receipt insertion; H2 compatibility receipt path retained. Locked legacy wallet lazy-backfills principal only after matching both identities. All SQL/JPA entities/repositories/migrations untouched. |
| Outbox and expiry | `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionOutboxService.java:37`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionOutboxRelay.java:27`, `promotion-service/src/main/java/com/delivery/promotion_service/service/VoucherReservationExpiryJob.java:17`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:461`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:691` | Mandatory local transaction; deterministic reservation/state event IDs; `voucher.reservation.events` keyed by order ID. Relay locks bounded due rows, waits for broker ACK, retries with capped backoff, marks DEAD after 12 attempts. At-least-once publication, not exactly-once. Expiry locks/rechecks RESERVED holds before restoring capacity and writing outbox. |
| Capability and ownership gates | `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:52`, `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:285`, `promotion-service/src/main/java/com/delivery/promotion_service/controller/PromotionController.java:296`, `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionService.java:991`, `promotion-service/src/main/resources/application.yml:12` | Internal checkout requires token and checkout flag; stacking additionally requires stacking flag and principal canary membership. Checkout, stacking, merchant-create API, principal enforcement and outbox relay remain default-off. Gateway/public versus internal HTTP, role checks, topics, timers, exception mapping and clocks unchanged. |

## Ordered slices

1. **Completed slice 1:** standalone `promotion/domain` reactor before host; plain
   voucher view/immutable snapshot, wallet eligibility, layer resolution,
   three-layer selection/pricing and separate legacy pricing; host mapping-only
   facades. No application-api/application is justified by this policy-only slice.
2. **Completed slice 2:** campaign creation validation/defaults, lifecycle
   outcomes, collection availability/duplicate decisions and claim status/per-user
   limits now live in domain. Ownership HTTP, principal resolution, clocks,
   audit mutation and locked persistence remain in the host. Approval policy is
   unchanged.
3. **Completed slice 3:** legacy and bulk reservation transition/counter/replay
   policies, Order receipt/event routing and refund compensation outcomes now
   live in domain; host retains locks, transactions and persistence.
4. **Completed slice 4:** application-api/application for collection, pricing,
   reservations and consumed Order events; transaction/ACK/receipt/outbox
   boundaries stay in host adapters. See slice 4 evidence below.
5. Move transport, persistence, scheduling, ownership client and composition
   into infrastructure/boot, keeping `promotion-service` artifact/DNS and flags.
6. Rehearse packaged restart/replay, competing consumers, expiry and outbox
   publish-before-mark crashes before declaring the full tranche complete.

## Equivalence and validation

- New runtime module has only JDK production imports; its dependencies are
  test-scoped JUnit/AssertJ. Host keeps entities, DTOs, exception handling,
  flags, SQL, locks, transaction demarcation, Kafka and clocks. Host snapshots
  copy money and timestamps without normalization or defaults.
- Original host tests remain unchanged; their policy cases also run directly
  against domain. Added malformed-row/availability matrices, manual-selection
  validation, deterministic ties, boundary/immutability tests and 15 exact-money
  cases for scale, HALF_UP, caps, over-100 percentages and payable rejection.
  Host mapper tests cover all enum values/nulls, every copied field and every
  quote response field in AUTO/MANUAL/default modes.
- `mvn -B -pl :promotion-domain -am clean verify`: 63 tests, zero failures,
  errors or skips; 273/284 lines (96.13%) and 305/326 branches (93.56%). Both
  inherited JaCoCo gates are 85%, without coverage exclusions.
- `mvn -B -pl :promotion-service -am clean verify`: BUILD SUCCESS; 209 tests
  across the reactor (domain 63, host 115, upstream starters/contracts 31),
  zero failures/errors/skips. Docker-enabled run passed PostgreSQL reservation
  races, receipt concurrency, and Kafka/PostgreSQL recovery integration.
  `/tmp/promotion-service-clean-verify-docker.log` records the complete run.
  The initial sandbox run passed but skipped 10 Docker tests because socket
  access was denied; the approved rerun resolved all skips: 3 in
  `PromotionReservationKafkaPostgresIntegrationTest`, 3 in
  `PromotionOrderReservationReceiptPostgresConcurrencyTest`, and 4 in
  `VoucherReservationPostgresConcurrencyTest`.
- Temporary baseline oracle compiled pre-extraction HEAD policies alongside
  current code: deterministic seed 20261005, 10,000 three-voucher row sets,
  200,504 identical eligibility/layer/pricing/quote outcomes, including exception
  classes/messages. Legacy price comparisons require nonnull reward/value,
  as enforced by host eligibility before that private path. Original and current
  quote record field strings matched, including AUTO/MANUAL/default mode.
  Evidence: `/tmp/promotion-equivalence.log`; temporary oracle sources are in
  `/tmp/promotion-equivalence/`, not production or the repository.
- `git diff --check` passes. No git write commands or plan edits performed.

## Observed pre-existing defects and compatibility risks (not fixed)

- Collection accepts exactly `endTime`, but reservation and stacking reject
  that same instant: `promotion/domain/src/main/java/com/delivery/promotion/domain/WalletVoucherPolicy.java:50`
  versus `promotion/domain/src/main/java/com/delivery/promotion/domain/WalletVoucherPolicy.java:67`.
  Boundary inconsistency is preserved and explicitly regression-tested.
- Stacking payable guard compares final total to **gross**, not discounted,
  shipping: `promotion/domain/src/main/java/com/delivery/promotion/domain/VoucherStackingCalculator.java:212`.
  This can reject positive food remainder when freeship exceeds it. This is a
  policy question, not authorization to change the guard or its existing message.
- Reservation eligibility contains defensive CATEGORY/layer branches already
  ruled out by the initial checkout-shape guard:
  `promotion/domain/src/main/java/com/delivery/promotion/domain/WalletVoucherPolicy.java:75`.
  These unreachable branches/messages are retained rather than changing
  validation precedence solely to obtain 100% coverage.
- `VoucherGroup` and customer-segment data still exist in persistence, but
  fixed-layer checkout does not enforce arbitrary group exclusions or segment
  targeting: `promotion-service/src/main/java/com/delivery/promotion_service/entity/VoucherGroup.java:19`,
  `promotion-service/src/main/java/com/delivery/promotion_service/entity/Voucher.java:76`.
  This is inventory of dormant legacy data, not an enabled/new eligibility policy.
- At-least-once relay can publish twice after broker ACK/local transaction
  failure, and holds locks while waiting on Kafka. This existing recovery concern
  belongs to later runtime work, not the pricing extraction:
  `promotion-service/src/main/java/com/delivery/promotion_service/service/PromotionOutboxRelay.java:35`.

## Slice 2 equivalence evidence

Authority is the pre-extraction host behavior at `8774caa`,
`docs/workflows/promotion_voucher_flow.md` and accepted decision 0004. No new
commercial rules are introduced. `CampaignPolicy`, `VoucherLifecyclePolicy`
and `WalletClaimPolicy` use only JDK production imports and return outcomes.
Existing collection eligibility now also exposes an unavailable-reason outcome;
its throwing domain compatibility method is retained. Host facades map outcomes
to the existing exceptions and apply mutations.

| Moved decision | Equivalence proof |
| --- | --- |
| Campaign request validation and defaults | `CampaignPolicyTest` covers every validation branch/message, compound-invalid check order, platform/shop/freeship defaults and untrimmed uppercase layer storage. Host test proves validation precedes code lookup, then normalized-code lookup precedes `saveAndFlush`; invalid-layer exception cause retains the host enum class name. |
| Approval/rejection, activation and retirement | Domain tests cover every outcome branch, exact guard order (deleted, creator, pending), legacy null approval, reason normalization and retirement no-op. Existing host lifecycle tests retain audit/mutation assertions; added host tests prove locked lookup before save and no save on rejected outcomes. Activation deliberately adds no time/quota guard. Ownership-verified creation still saves PENDING first, then auto-approves and flushes again, without an admin actor. |
| Collection availability and duplicate claims | Outcome tests preserve shape, approval/active/expiry, start, global-stock check order and inclusive collection end boundary. Both principal/legacy query rails, fallback metrics and unique-violation handling remain in host. Host regression proves voucher lookup, wallet lookup, flush order and duplicate no-save with identical conflict message. |
| Claim status and per-user limit | Domain tests cover SAVED-only claim, null/negative counter normalization, null limit default of one, exact limit message and integer arithmetic. Host regression proves wallet status check before voucher lock, per-user capacity before global eligibility, replay lookups before wallet/voucher locks, and reservation flush before outbox. Legacy rail still uses status/global stock without the bulk per-user counters. |

Validation for slice 2:

- `mvn -B -pl :promotion-service -am clean verify`: BUILD SUCCESS; 275 tests
  across the reactor (domain 123, host 121, upstream 31), zero failures/errors.
  Log: `/tmp/promotion-slice2-final-verify.log`.
- Ten Docker-only tests skipped because Testcontainers could not access a valid
  Docker environment: `VoucherReservationPostgresConcurrencyTest` (4),
  `PromotionOrderReservationReceiptPostgresConcurrencyTest` (3),
  `PromotionReservationKafkaPostgresIntegrationTest` (3). No Docker proof is
  claimed for this slice.
- Domain JaCoCo: 383/394 lines (97.21%), 460/480 branches (95.83%); both 85%
  gates pass unchanged. All three new policies have 100% line/branch coverage.
- Temporary baseline oracle compiled pre-extraction host/domain sources beside
  current classes: seed 20261005, 22,672 matching creation, lifecycle
  result/mutation, capacity and collection outcomes, including exception
  classes/messages/causes. Sources and result are at
  `/tmp/promotion-slice2-oracle/`; no oracle code is shipped in production.
- `git diff --check` passes. No git writes or plan edits.

Slice 3 is recorded below. Slices 4–6 remain as listed above.

Additional pre-existing observations, preserved by regression tests:

- The legacy reservation rail does not enforce bulk used/reserved counters or
  the bulk per-user capacity limit. A SAVED row with exhausted bulk counters can
  still reserve on that rail. This extraction does not align the two contracts.
- Per-user capacity uses Java `int` addition; extreme counters can overflow the
  sum and bypass the limit. Null/negative normalization and overflow behavior
  are preserved rather than introducing a new data-repair policy.
- Creation validates a trimmed layer but stores its untrimmed uppercase text;
  this normalization asymmetry is retained. The existing end-time and stacking
  payable-guard inconsistencies remain unchanged and regression-tested.

## Slice 3 equivalence evidence

Authority is the saved pre-extraction host implementation and
`docs/workflows/promotion_voucher_flow.md`. `ReservationPolicy`,
`ReservationReplayPolicy` and `OrderReservationEventPolicy` use only JDK imports.
They return transition, counter, wallet, routing or failure outcomes; host code
maps them to the existing mutations and exception classes/messages. JSON parsing,
SHA-256 computation, clocks, transactions, repository calls, locks, receipt claim,
outbox, Kafka/ACK, topics, flags and database schema remain in the host.

| Moved decision | Executable equivalence evidence |
| --- | --- |
| Commit/release/expiry transitions | `ReservationPolicyTest` covers both rails, all persisted states plus null, before/equal/after expiry, terminal replay and malformed-expiry short circuit. Host regressions prove late commit leaves quota unchanged; expiry rechecks locked candidates and restores once. Bulk line locks still precede the commit guard. |
| Global/per-wallet counters and compatibility state | Domain matrix covers null, negative, zero, positive and MAX_VALUE counters; reserve, commit, uncommitted release/expiry and committed compensation; default/negative/zero/positive limits, untouched raw fields and overflow. Host tests assert intermediate COMMITTED counters/state, terminal replay, compensation and exact wallet/voucher/outbox lock order. Legacy release deliberately leaves bulk counters intact. |
| Reservation fingerprint/order/principal binding | `ReservationReplayPolicyTest` varies every fingerprint field, money scale, sorted-list order, null principals and conflicting payload short circuit; both binding rails retain equality direction. Host tests prove replay precedes wallet/voucher locks, conflicting payload prevents save/outbox and bulk order replay accepts a different reservation ID. Public principal checks retain their position before line locks; trusted Kafka calls retain their two-argument rail. |
| Order topic/action/routing/refund fence | Domain tests cover configured topics, numeric retry suffixes, invalid topics, overlapping-topic precedence, both/neither reservation IDs, bulk precedence, case-folded fulfilment states and untrimmed previous status. Host tests prove receipt claim precedes compensation, after-pickup receipts do not touch usage and JSON identity validation still precedes topic validation and receipt claim. |
| Receipt/event replay and commit confirmation | Domain tests vary all receipt fields and nullable reservation identity, preserve short circuit and every commit confirmation message. Host tests prove exact replay is a no-op, contradictory identity/raw whitespace fails with the same exception/message, and bulk identity remains protected by raw fingerprint while the receipt identity column remains legacy-only. |

Validation:

- `mvn -B -pl :promotion-service -am clean verify`: BUILD SUCCESS, 294 tests
  (133 domain, 130 host, 31 upstream), zero failures/errors, 10 Docker-only skips.
  Full log: `/tmp/promotion-slice3-final-verify.log`.
- Docker-only skips: `VoucherReservationPostgresConcurrencyTest` (4),
  `PromotionOrderReservationReceiptPostgresConcurrencyTest` (3),
  `PromotionReservationKafkaPostgresIntegrationTest` (3). Testcontainers could
  not find a valid Docker environment; this slice does not claim Docker proof.
- Domain JaCoCo: 441/452 lines (97.57%), 578/598 branches (96.66%). Each of the
  three extracted policies has 100% line/branch coverage. Both 85% gates remain
  unchanged, with no new exclusions or build configuration changes.
- The same 24 host reservation/event regressions passed against both saved
  pre-extraction sources and extracted classes: zero failures/skips in each run.
  Temporary baseline compilation and JUnit launcher live in
  `/tmp/promotion-slice3-baseline/`, with `baseline.log` and `extracted.log`.
  Pre-extraction sources are in `/tmp/promotion-slice3-before/`. No baseline
  implementation is shipped in the repository.

Additional pre-existing inconsistencies retained:

- Bulk order replay ignores the requested reservation UUID while legacy replay
  rejects a changed UUID. This is covered directly and through host replay.
- Legacy counters unbox null and do not normalize negatives, whereas bulk
  counters normalize null/negative values. Integer increment overflow remains.
- Bulk compatibility transitions refresh `usedAt` whenever any usage remains,
  including expiry of another hold; compensation does not preserve the original
  usage timestamp. Wallet order binding is cleared when no reserved count remains.
- The bulk reservation ID is absent from the receipt identity column; the raw
  payload hash detects its changes. JSON whitespace alone also makes an event
  contradictory. No receipt schema or fingerprint normalization is introduced.
- Refund/cancellation previous status is case-insensitive but untrimmed;
  whitespace around `PICKED_UP` bypasses the fulfilment fence. The domain matrix
  preserves this behavior instead of adding a new refund policy.

Remaining work: slice 4 application contracts/use cases, slice 5
infrastructure/boot consolidation, and slice 6 packaged recovery rehearsals.

## Slice 4: application contracts and use cases

Authority remains the existing host behavior at `3a8e195` and
`docs/workflows/promotion_voucher_flow.md`; this slice introduces no commercial
policy or rollout flag changes.

- `promotion/application-api` defines operation-specific collection, pricing,
  reservation, transition/expiry and Order receipt ports, command records and
  reservation-state views. Pricing returns the domain's immutable calculation
  result; host adapters project it to the existing HTTP DTO. Generic reservation,
  wallet and voucher handles are opaque to application code: there are no host,
  JPA, Spring, Kafka or Jackson imports in either application module.
- `promotion/application` owns collection eligibility/duplicate orchestration,
  wallet-read/pricing/projection order, ID-then-order replay checks, ordered
  wallet/voucher lock pairs, claim fences, quote-before-persist, reservation
  commit/release/expiry decisions, receipt replay and consumed-event routing.
  Every effect goes through an operation-scoped port inside the caller's existing
  transaction. Both modules inherit executable 85% line and branch gates from
  `delivery-build-parent`, with no coverage exclusions, and are registered
  immediately after domain and before `promotion-service` in the root reactor.
- `PromotionService`, `PromotionOrderReservationEventProcessor` and the pricing
  mapping facade implement the ports and delegate to these use cases. Request
  validation/principal configuration, DTO/entity mapping, mutation application,
  audit timestamps, counter application, repository calls, duplicate-race
  translation and outbox enqueue remain host adapter operations. Campaign/admin
  lifecycle facades remain the slice 2 implementation. All existing transaction
  annotations, SQL/JPA entities/migrations, Kafka listeners/topics/ACK boundaries,
  outbox implementation, locks and rollout flags are retained. Host physical
  relocation is deliberately deferred to slice 5.

### Equivalence evidence

- Application fake-port tests assert the complete reserve effect sequence:
  validation -> ID replay -> order replay -> each wallet lock/claim fence ->
  voucher lock/capacity check -> quote -> persistence. Exact replay and failing
  claim/capacity/quote paths never invoke later effects; persistence failures
  propagate without retry. Collection verifies identity -> lookup -> eligibility
  -> duplicate lookup -> flush, including concurrent duplicate failure.
- Commit/release tests retain the two rails' exact failure labels, terminal
  no-ops and expired-hold boundary. Expiry re-locks candidates and rejects missing,
  terminal and extended holds before transition. Host
  `VoucherReservationServiceTest` additionally proves actual repository lock,
  counter mutation and outbox ordering, including bulk line locks and legacy
  counter preservation. These existing tests were not weakened or replaced.
- Order event tests cover receipt-first routing, exact/contradictory replay,
  bulk precedence, all four commit/release operations, null/uncommitted commit
  responses and post-fulfilment compensation suppression. Host receipt claim SQL,
  SHA-256 raw-payload fingerprinting, H2/PostgreSQL selection, parsing and
  exception classes/messages remain intact; listener tests prove ACK only after
  successful processor return.
- Pricing tests compare the use-case result to the domain calculator with exact
  money assertions. Existing host mapper/pricing/HTTP/security/migration tests
  continue to exercise the same wire projection and persistence schema.
- Source-boundary tests in both application modules reject imports outside the
  JDK and Promotion domain/application packages.

### Slice 4 validation

`mvn -B -pl :promotion-service -am clean verify` completed with **BUILD SUCCESS**
on 2026-10-06: 312 tests discovered, 302 executed, zero failures/errors and 10
Docker skips. Breakdown: domain 133, application-api 2, application 16, host 130,
upstream starters/contracts 31. Both application module coverage checks passed:

| Module | Lines | Branches | Configured gates |
| --- | --- | --- | --- |
| promotion-application-api | 5/5 (100%) | No executable branches | 85% line + branch |
| promotion-application | 81/81 (100%) | 37/38 (97.37%) | 85% line + branch |

The skipped cases are four in `VoucherReservationPostgresConcurrencyTest`,
three in `PromotionOrderReservationReceiptPostgresConcurrencyTest`, and three
in `PromotionReservationKafkaPostgresIntegrationTest`. Testcontainers reported
that it could not find a valid Docker environment; this slice does not claim
PostgreSQL/Kafka runtime race proof from the passing in-process tests. Complete
log: `/tmp/promotion-slice4-clean-verify.log`; JaCoCo XML and Surefire XML are in
each module's `target/` directory. Source diffs were reviewed against temporary
pre-edit copies; changed-source whitespace/conflict-marker checks passed. No git
commands or `docs/plans/` edits were performed.

### Slice 5 plan

Move HTTP/security, Kafka listeners, ownership HTTP, JPA repositories/entities,
Flyway migrations, outbox, expiry scheduling and these port implementations to
`promotion/infrastructure`. Move the Spring entrypoint/config and composition to
`promotion/boot`, preserving artifact/DNS `promotion-service`, existing bean
wiring, transaction proxy boundaries and default-off flags. Keep domain and
application contracts independent of framework types. Re-run this reactor proof
and Docker reservation/receipt/Kafka races after relocation; packaged
restart/replay and outbox publish-before-mark crash rehearsal remain slice 6.
