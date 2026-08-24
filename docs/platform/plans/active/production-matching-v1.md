# Execution Plan: Production Dispatch v1 — Rolling Batch Assignment

Date: 2026-08-22

## Status

Active

## Outcome

Nâng matching từ per-order single-offer thành hệ thống có rolling dispatch round:
gom order và shipper trong cùng H3 zone tối đa 5 giây, tạo bundle tối đa 3 order,
tối ưu bounded min-cost flow + deterministic repair, sau đó gửi atomic batch
offer. Single-order `nearest-cod-v1` vẫn là fallback trong toàn bộ rollout.

## Locked decisions

- Batch window 5 giây; tối đa 50 order và 100 shipper mỗi round.
- Batch tối đa 3 order, pickup cùng H3 cluster/k-ring 1, pickup distance tối đa 2 km.
- Mỗi order phải pickup trước drop-off; ETA tăng thêm tối đa 10 phút.
- Shipper accept nguyên batch; không partial accept.
- Wave tối đa 3 shipper, timeout 20 giây, matching deadline 5 phút.
- ETA ranking dùng Matrix `driving` top 10 rồi `driving-traffic` top 3; live geometry dùng Directions qua `routing-service`.
- COD hold độc lập theo từng order trong batch; commit/release phải atomic ở cấp batch.
- First-offer p95 mục tiêu dưới 10 giây do batch window 5 giây.
- Không bật batch cho client không có capability `BATCH_OFFER_ENABLED`.

## Approach

1. Additive schema và event/feature flags, giữ behavior cũ mặc định.
2. Durable dispatch pool/round trong Match; H3 projection dùng chung location index.
3. Bundle generation, bounded optimizer và route feasibility.
4. Delivery batch aggregate, atomic accept, COD hold và compensation.
5. ETA batch, Tracking room nhiều delivery và client contract.
6. Shadow → canary 10% → 25% → 50% → 100%; rollback chỉ bằng flags.

## Progress

- [x] Execution plan và decision record được ghi nhận.
- [x] Feature flags và additive event contract.
- [x] Match dispatch pool/round migration và domain.
- [x] Delivery batch migration/domain.
- [x] Bundle candidate generation, bounded optimizer và durable batch proposal.
- [x] Atomic Redis batch offer reservation và Delivery batch persistence/accept endpoint.
- [x] COD hold creation, durable commit/release intent, expiry cleanup và compensation.
- [x] ETA route client với Routing service và geodesic fallback; backend capability contract.
- [x] Shipper additive batch contract, current-batch offer route, runtime flag và multi-stop state/map foundation; backend route ordering is now propagated to pickup/drop-off sequences.
- [x] Focused migration/context proof cho foundation slice.
- [x] Focused optimizer, listener, delivery migration/context và controller proof.
- [x] Batch invariant proof cho optimizer/legacy tracking projection và migration/context slices.
- [ ] Redis/PostgreSQL batch invariant proof và staging smoke.

## Implementation update (2026-08-22)

- `DispatchPoolItem` now stores canonical pickup/drop-off coordinates and COD
  amount so a round can be rebuilt without replaying Kafka.
- Due rounds query Match Redis GEO, filter COD eligibility, generate singleton /
  pair / triple candidates under the 2 km pickup constraint, and run the
  bounded optimizer. Selected batches are atomically reserved in Redis and
  published through the durable Match outbox.
- Delivery accepts the additive `batchOffer` contract, persists
  `delivery_batches` and `delivery_batch_items`, and exposes an atomic
  `POST /api/deliveries/batch/accept` path. Legacy single-offer behavior is
  unchanged while flags remain off.
- The round executor now calls `routing-service` Directions for bounded leg ETA
  ranking and falls back to geodesic when routing is unavailable. Matrix
  pre-ranking/cache tuning is still a staging optimization.
- COD holds are created in Settlement before Redis reservation; Delivery emits
  durable commit/release intents and Settlement expires orphaned HELD rows.
- Tracking keeps the legacy single-delivery projection and adds a batch set
  projection keyed by `batchId`, so batch BUSY/AVAILABLE events do not overwrite
  each other.
- Batch waves are bounded to 3 offers with 20-second offer expiry and a maximum
  of 3 requeue waves; matching deadline remains the final fence.
- Requeue only retires pool items after a hold/reservation/outbox attempt really
  succeeds; failed candidates return to WAITING instead of remaining CLAIMED.
- Delivery V21 replaces the legacy one-row shipper guard with a PostgreSQL
  partial-index policy: one active legacy delivery or one active batch aggregate
  per shipper. H2 uses a non-unique projection index only for schema validation.
- Delivery batch progress updates item and aggregate status on PICKED_UP,
  DELIVERING and DELIVERED; the last delivered item completes the aggregate and
  only then emits the shipper AVAILABLE fence.
- Saga rematch preserves `batchWave`; batch expiry/rejection emits release and
  generation-scoped rematch intents. Match retires the released generation.
- Tracking maintains both the legacy single-delivery projection and a Redis
  multi-delivery set keyed by shipper/batch, preventing one item from clearing
  BUSY for the remaining items.
- A legacy cancel request for an accepted batch before pickup is promoted to a
  whole-batch cancellation: all items return to `FINDING_SHIPPER`, each item
  emits a rematch rejection event, and COD capacity is released atomically.
- Completion of the final batch item emits a deterministic
  `delivery.batch.completed` fence so Match retires the old dispatch generation
  and stale reservations cannot remain assigned forever.
- Shipper recovery reloads all active deliveries after startup/reconnect;
  completing one stop removes only that stop and keeps the remaining batch
  visible on the map and offer UI.
- Match evaluates every permutation for a bundle of up to three orders,
  preserves pickup-before-dropoff for each order, and persists the selected
  sequence through `batchItems` into Delivery's batch item projection.
- Candidate generation remains bounded per shipper: every eligible order keeps
  a singleton fallback, while pair/triple bundles use only the nearest seed
  orders and a hard candidate cap. Routing leg estimates use a short-lived
  local cache to avoid repeating the same provider call inside one round.
- Kubernetes runtime config exposes every H3/batch capability flag with an
  explicit `false` default. Kafka operator provision/verify scripts now include
  `delivery.batch.accepted`, `delivery.batch.released`, and
  `delivery.batch.completed` source/DLT topics.

## Validation observed

- `mvn -pl match-service -am -DskipTests compile` passed.
- `mvn -pl match-service -am -Dtest=FindShipperEventListenerTest,BoundedDispatchOptimizerTest,MatchRedisGeoRepositoryOfferTest -Dsurefire.failIfNoSpecifiedTests=false test` passed (34 tests).
- `mvn -pl delivery-service -am -Dtest=DeliveryMigrationSchemaValidationTest,DeliverySagaCommandProcessorTest -Dsurefire.failIfNoSpecifiedTests=false test` passed (5 tests).
- `mvn -pl delivery-service -am -Dtest=DeliveryControllerOfferAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed (2 tests).
- `mvn -pl routing-service -am -DskipTests compile` passed.
- `mvn -pl tracking-service -am -Dtest=ShipperDeliveryAssignmentStoreTest,LocationFanoutPublisherTest,ShipperDeliveryRoomListenerTest -Dsurefire.failIfNoSpecifiedTests=false test` passed (5 tests).
- `mvn -pl delivery-service -am -Dtest=DeliveryControllerOfferAuthorizationTest,DeliverySagaCommandProcessorTest,DeliveryShipperOfferTest,DeliveryMigrationSchemaValidationTest -Dsurefire.failIfNoSpecifiedTests=false test` passed (45 tests).
- `mvn -pl match-service,routing-service,settlement-service -am compile` passed after clean compile fixes.
- Settlement focused application/listener run passed (8 tests); Saga focused convergence/generation/timeout run passed (38 tests).
- `node deploy/kubernetes/generate.mjs --write && node deploy/kubernetes/generate.mjs --check` passed (61 manifests).
- `mvn -pl match-service,delivery-service,settlement-service,saga-orchestrator-service,tracking-service,routing-service -am clean compile` passed after batch lifecycle changes.
- Delivery focused offer/migration/saga/controller suite passed (45 tests).
- Match focused listener/optimizer/Redis offer suite passed (34 tests).
- `npm run typecheck` in `shipper_app2` passed after multi-stop state/map changes.
- `mvn -pl match-service,delivery-service,settlement-service,saga-orchestrator-service,tracking-service,routing-service -am clean compile` passed after route sequence propagation changes.
- Kubernetes generator check and Kafka provision/verify script syntax checks passed.
- Match clean compile passed after bounded candidate generation and routing-leg cache changes.
- Shipper app `npm run typecheck` and batch COD contract test passed (9 tests), including aggregate COD/earning calculation.

## Risks and recovery

- Constraint hiện tại `one active delivery per shipper` phải được thay bằng `one active batch per shipper`; migration forward-only, rollback bằng code/flags.
- Batch offer đang mở khi rollback phải retire/release; batch đã accepted tiếp tục lifecycle bình thường.
- Optimizer không được làm mất order; bundle singleton luôn tồn tại làm fallback.
- Redis/H3/Mapbox đều là projection/provider có fallback; PostgreSQL Delivery/Settlement là authority cho assignment và COD.
- H2 cannot prove PostgreSQL partial-index uniqueness; production rollout must
  verify V21 on PostgreSQL with concurrent batch acceptance and legacy assignment.
- Shipper UI now exposes the additive batch offer, accept/reject paths and
  multi-stop markers; route optimization/navigation sequencing and mobile E2E
  remain open.

## Validation

- Compile các module bị thay đổi.
- Focused tests cho pool claim, bundle disjointness, route precedence, atomic accept và COD hold.
- Một staging smoke với 3 order, một batch accept và một order cancel/recalculate.
- Full load/chaos/mobile E2E ghi nhận deferred, phải chạy trước khi bật batch 100%.

## Remaining before canary

1. Run PostgreSQL/Redis invariant tests with concurrent reservation, duplicate
   Kafka delivery, hold expiry and release/accept races.
2. Execute staging smoke: three COD orders in one H3 cluster, one batch offer,
   atomic accept, three stop updates, one cancellation/rematch, and final COD
   consume.
3. Complete customer tracking regression and mobile E2E for multi-stop flows.
4. Enable flags in order: schema/contracts → routing/hold infrastructure →
   client capability → shadow → canary; keep all batch flags false by default.
