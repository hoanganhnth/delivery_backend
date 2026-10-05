# Promotion tranche: inventory and first domain slice

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

1. **Completed here:** standalone `promotion/domain` reactor before host; plain
   voucher view/immutable snapshot, wallet eligibility, layer resolution,
   three-layer selection/pricing and separate legacy pricing; host mapping-only
   facades. No application-api/application is justified by this policy-only slice.
2. Extract campaign creation and lifecycle decisions with explicit mutation
   results; leave ownership HTTP, principal resolution and locked persistence
   in the host. Do not reinterpret approval policy.
3. Extract legacy and bulk reservation transition/counter/replay policies with
   concurrency regressions before introducing transaction/use-case ports.
4. Introduce application-api/application for collection, pricing, reservations
   and consumed Order events; preserve transaction/ACK/receipt/outbox boundaries.
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
