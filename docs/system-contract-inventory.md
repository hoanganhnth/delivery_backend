# System Contract Inventory

Ngày kiểm kê: 2026-08-21

Tài liệu này là inventory thực thi cho Phase 0 của
`../../docs/plans/active/priority-roadmap.md`. Source code, test và runtime vẫn là
nguồn sự thật cuối cùng. Contract backend MVP được freeze ngày 2026-07-26; các
dòng `OPEN` trong checkpoint lịch sử là production proof hoặc capability
hidden/disabled, không được hiểu là public contract chưa xác định.

## Runtime inventory

| Service | Port | Persistence/dependency chính | Gateway surface | Baseline issue |
|---|---:|---|---|---|
| api-gateway | 8079 | Redis rate-limit; no JWT keys | public edge | exact route/method allow-list, peer-IP quota and legacy identity-header sanitization. JWT validation and role/ownership enforcement run in each resource service via Auth JWKS. Approved Redis-outage policy is bounded: catalog/authenticated reads fail open, while auth/mutations/WebSocket handshakes fail closed with the standard 503 envelope; the two-replica Match rehearsal verifies the mutation branch leaves Order status/cancellation fields and `order.cancelled` outbox cardinality unchanged before exercising Match-local projection recovery. |
| auth-service | 8081 | PostgreSQL `auth_db`, AWS SES SMTP, RS256 keypair | exact public auth/recovery + protected session/admin routes + JWKS | Forgot/reset và verification dùng post-only exact route, uniform request response, 256-bit one-time token chỉ lưu digest, async email sau commit, security audit và account-wide refresh-family/session revocation; Auth emits `kid`/issuer/audience access tokens and publishes active/retiring public JWKs. |
| user-service | 8082 | PostgreSQL `user_db` | exact current-user/address/admin-read routes; create/by-auth/status projection internal | ownership đã audit; internal create idempotent theo authId, fail-closed khi email đã thuộc auth identity khác và migration khóa unique email case-insensitive; block/unblock projection dùng row lock transaction; Auth↔User crash-window có code/unit/H2 proof và runtime schema/startup proof; live outage retry harness `scripts/verify-auth-user-outage-retry.sh` đã có nhưng runtime execution còn OPEN |
| restaurant-service | 8083 | PostgreSQL `restaurant_db`, Kafka | `/api/restaurants/**`, `/api/menu-items/**` | confirm/reject decision+outbox atomic; direct restaurant decision publisher now fails closed on invalid order/restaurant/actor/prep-time/rejection input before touching eligibility or outbox; PostgreSQL two-instance race, broker-down/backoff, relay restart và Kafka cardinality PASS; checkout reads canonical `openingHour/closingHour`, accepts only menu status `AVAILABLE`, and requires menu ownership plus finite positive canonical price; rating duplicate sequential/constraint race maps to 409 conflict with no average rewrite after duplicate; Restaurant `112/112`, Order consumer `87/87`; downstream Order crash-window còn OPEN |
| order-service | 8084 | PostgreSQL `order_db`, Kafka; HTTP restaurant/promotion/flash sale | exact create/preview/read/cancel + admin paths | server-owned regular/flash/voucher pricing; one voucher and no stacking; immutable monetary/reservation snapshot; synchronous same-identity release on ambiguous/later create failure; cancellation/payment failure emits compensation IDs through transactional outbox. `saga.command.update-order-status` has a durable `saga_command_receipts` inbox: an atomic PostgreSQL `INSERT .. ON CONFLICT DO NOTHING` claim commits with the Order mutation before listener ACK; exact raw-payload replay ACKs without another mutation, while a reused ID with changed command/order/status/payload fails closed. The shared Kafka factory preserves raw `String` commands for the SHA-256 receipt, while DTO listeners still deserialize JSON; its recoverer also uses the raw-string template so DLT does not JSON-quote a command. Two-replica Kafka/PostgreSQL and fresh-group replay proof PASS. All Order retry/DLT and common-error-handler fallback destinations are owner-isolated as `-retry-order-*` / `.order.DLT` during the shared-source migration. Checkout flags remain false by default. |
| delivery-service | 8085 | PostgreSQL `delivery_db`, Kafka; legacy STOMP hidden | exact delivery routes | offer/accept identity + transactional outbox + authenticated self current-offer recovery đã khóa; every Saga command (create, cancel, cache/expire offer, no-shipper) now claims `delivery_inbound_receipts` with atomic PostgreSQL conflict handling, command/aggregate/SHA-256 raw payload inside a processor transaction before listener ACK, so exact replay is an ACK/no-op and contradictory ID reuse fails closed; exact REJECT/cancel-assignment replay trước rematch không phát duplicate event; PostgreSQL two-writer receipt/terminal-outbox and two-replica Kafka/fresh-group replay convergence PASS |
| search-service | 8088 | Elasticsearch, Kafka | exact anonymous GET restaurant/dish; shipper API và projection hidden | query/page bounds; unavailable trả sanitized 503 thay vì empty giả; live recovery/CAS còn OPEN |
| shipper-service | 8089 | PostgreSQL `shipper_db`, Kafka | exact self profile/online/ratings + admin list/read; legacy delete/rating-write hidden | fulfilment identity là auth `userId`, tách profile DB `id`; arbitrary profile-ID rating read đã xóa, self read cap 100; active-delivery/location convergence còn OPEN |
| settlement-service | 8090 | PostgreSQL `settlement_db`, Kafka | exact admin GET-only, including read-only refund queue; client/payment/mutation hidden | ledger unique key + balance row lock; `delivery.completed.totalPrice` must equal restaurant earnings + restaurant commission + shipping fee and is the COD debit source; mismatch fails before ledger mutation. `settlement_receipts` uses atomic PostgreSQL `ON CONFLICT DO NOTHING` before the four ledger postings, so an exact same-ID replay across replicas ACKs/no-ops while contradictory payload reuse fails closed to DLT. Feature-gated cancellation/no-shipper refund cases retain immutable monetary snapshots and use the same atomic PostgreSQL claim before any outbox handoff; exact replays no-op and contradictory reuse reaches DLT. Refund listener/outbox/provider remain default-off. |
| notification-service | 8091 | PostgreSQL `notification_service_db`, Redis, Kafka, optional FCM; no STOMP graph | exact notification/FCM client routes | REST ownership + durable event dedup; two-instance Kafka/PostgreSQL duplicate, same/fresh-group replay and contradictory-reuse-to-owner-DLT proof PASS for all customer-visible sources (`order.created`, `delivery.status-updated`, `delivery.shipper-offered`); PENDING row-lock delivery, configured-provider failure, DLT/operator replay và restart PASS; shared source retries use `-retry-notification-*` / `.notification.DLT`, preventing Saga consumption of a Notification retry record; delivery status message không bịa `shipperName` nếu producer không gửi; offer dùng FCM best-effort wake-up, multi-token partial success còn at-least-once |
| match-service | 8092 | PostgreSQL `match_db`, Redis GEO replica, Kafka | không có HTTP controller | canonical Saga command pipeline; `match_commands` inbox/fingerprint + candidate staging and `match_outbox_events` deterministic result relay; legacy/debug HTTP đã xóa. A committed cancellation tombstone owns a PostgreSQL `PENDING → PROJECTED` Redis projection relay with retry metadata and `SKIP LOCKED` claims, so a finite Kafka retry budget cannot lose it. Disposable Compose proof with two Match replicas: a real Saga cancellation reaches `PENDING` while Redis is unreachable only to Match, ACKs source with empty Stop DLT, recovers the Redis key, fences delayed Find to one `CANCELLED` command/no offer-outbox-notification, then survives paired kill/restart plus exact Find replay to one durable offer. |
| tracking-service | 8093 | Redis GEO/routing, Kafka, raw WebSocket; PostgreSQL `tracking_db` async support history; internal Delivery access check | exact update/offline + `/ws/shipper-locations`; internal admin delivery-history only | delivery rooms + Redis Pub/Sub exact audience, bounded coalescing/backpressure and last-location reconnect; one-publisher Redis generation fence + new-session supersede + Redis-backed 30s disconnect grace; sampled 90-day support history stays off hot path and replay-idempotent |
| livestream-service | 8094 | PostgreSQL `livestream_db`, Agora | hidden khỏi Gateway cho MVP | experimental: startup mapper đã sửa; compatibility lists cap 100 và dead JPA event graph đã xóa; restaurant ownership/token boundary chưa chứng minh |
| saga-orchestrator-service | 8095 | PostgreSQL `saga_db`, Kafka | không có public controller | row lock/transition + ordered transactional command outbox có focused proof; `saga_inbound_receipts` atomically claims event identity/topic/order/SHA-256 raw fingerprint with PostgreSQL `ON CONFLICT DO NOTHING`, while `saga_early_events` atomically stages early cancellation/restaurant confirmation with the same contradictory-reuse fence. Kafka/PostgreSQL two-Saga-replica proof covers duplicate cross-partition records, same/fresh-group exact replay and same-partition owner DLT for `order.created` (one `STARTED` Saga/create-delivery outbox), `delivery.created.result` (one `DELIVERY_CREATED` transition) and early `order.cancelled` (one staged fact). Typed deterministic timeout commands fence status/version/observed deadline; late Delivery results are cancelled again and known-delivery cancellation waits for `delivery.status-updated(CANCELLED)`; all non-blocking retry/DLT destinations are owner-isolated as `-retry-saga-*` / `.saga.DLT`, with legacy generic topics drained during rollout. The read-only `verify-kafka-legacy-retry-drain.sh` gate verifies zero lag, no old assignment and a quiet end-offset window in each configured base ConsumerFactory group before legacy provisioning can be disabled; approved-broker evidence remains OPEN. Disposable Compose proves Saga cancellation emits generation-scoped Stop before a delayed Match Find, then survives Match-local Redis projection loss/recovery and the paired two-replica crash/replay; broader cross-service recovery remains OPEN. |
| promotion-service | 8096 | PostgreSQL `promotion_db`; Kafka | user wallet + admin campaign; calculate/reservation internal | V2 durable voucher reservation, V3 deterministic outbox, V4 `promotion_order_reservation_receipts` atomic event-id/source/action/order/reservation/SHA-256 fence, locked wallet/capacity, 15-minute expiry, exact replay/conflict, commit/release and `COMMITTED -> RELEASED` compensation complete. Two Kafka/PostgreSQL replicas across duplicate partitions plus same/fresh group replay for commit and both release sources converge to one expected reservation/receipt/outbox transition; contradictory reuse goes directly to owner `.promotion.DLT`. Only ADMIN-owned `ALL/SHOP` vouchers are checkout-eligible; checkout/relay flags false by default. Flash-sale has its independent default-off receipt boundary. |
| analytics-service | 8097 | PostgreSQL `analytics_db`, Kafka | hidden khỏi Gateway cho MVP | REST/listener/reconciliation vẫn off mặc định. Khi được bật, raw event được atomically claim bằng unique dedup key và SHA-256 raw-payload fingerprint; exact replay no-op, contradictory reuse fail-closed, và PostgreSQL aggregate increments dùng atomic upsert thay cho read-modify-write giữa replica. Owner recovery là `-retry-analytics-*` / `.analytics.DLT`, chỉ provision qua `PROVISION_ANALYTICS_RETRY_TOPICS=true`; payment targets còn cần cờ payment riêng. Compile/unit proof có; Kafka/PostgreSQL two-replica rehearsal vẫn OPEN. |
| flashsale-service | 8092 trong container riêng | PostgreSQL, Kafka; Redis non-authoritative | exact public/admin reads; merchant/internal hidden | V2 durable reservation/lines and V3 deterministic outbox; sorted PostgreSQL row locks make multi-line reserve atomic; 15-minute expiry, exact replay, commit/release and compensation complete. V4 `flash_sale_order_reservation_receipts` atomically fences Order event ID/source/action/order/reservation/SHA-256 before stock transition; Kafka/PostgreSQL two-replica commit and both release-source duplicate-partition/same-fresh-group rehearsal converges to one receipt and expected COMMITTED/RELEASED reservation/outbox transition, while contradictory reuse reaches `.flashsale.DLT`; a failed transition rolls the receipt back for Kafka replay. Owner recovery names are `-retry-flashsale-*` / `.flashsale.DLT`. Checkout/relay/merchant flags remain false by default. |

`scripts/verify-compose-config.sh` đã qua ngày 2026-07-22: YAML render hợp lệ,
DB/URL/Kafka/Redis/JVM env, healthcheck hạ tầng và public-port boundary đúng
contract. Base Compose chỉ publish Gateway cho application traffic; debug port
override là opt-in. Runtime startup proof sau đó đã PASS qua
`scripts/verify-runtime-startup.sh`: canonical volumes được giữ, hạ tầng healthy
và Gateway public reads responded. Từ checkpoint hiện tại, script mặc định chỉ
start 13 service thuộc COD MVP; bốn capability `livestream`/`promotion`/
`analytics`/`flashsale` cần `RUNTIME_INCLUDE_DISABLED_CAPABILITIES=true` để
start cùng. Checkpoint lịch sử 17 service vẫn được giữ như evidence, không phải
default runtime policy.

Analytics context test đã được cô lập sang H2 in-memory sau khi audit phát hiện
suite cũ kết nối và chạy DDL trên `analytics_db` local. Analytics REST, listener
và reconciliation job chỉ tạo bean khi `app.analytics.processing-enabled=true`;
Compose giữ cờ `false`. Livestream dùng mapper Spring tường minh thay generated
mapper từng làm context không khởi động. Clean proof hiện tại: Search 15/15,
Livestream 1/1, Analytics 7/7; đây không phải Elasticsearch/Agora/Kafka functional
proof và không mở lại hai capability experimental.

## HTTP surface baseline

Exact method inventory hiện có tại `http-api-inventory.md`: **166 handler
mapping**. `verify-http-api-inventory.sh` khóa source mapping count, controller
và handler ownership trong build baseline; exact path/verb/actor được review từ
source, Gateway và call-site nhưng chưa được script parse cơ học.

Các surface đã xác định cần đổi classification ở wave tương ứng:

- `order-service` và `delivery-service` admin routes: `public-admin`, resource
  service bắt JWT qua JWKS và role `ADMIN`; Gateway chỉ giữ exact route/method.
- auth account lookup theo email: `internal`; baseline thấy nằm trong public
  allow-list dù controller còn kiểm `Internal-Token`. Edge wave 2026-07-22 đã
  loại khỏi public allow-list; service credential boundary vẫn cần integration proof.
- `restaurant-service` test controller và `tracking-service` WebSocket test page:
  `dev-only`, chỉ được tạo bean ở profile `dev`.
- restaurant catalog/menu read dùng exact public GET allow-list. Validation
  `/api/restaurants/validate/**` không có Gateway route và fail-closed bằng
  `Internal-Token`; order/restaurant nhận cùng `INTERNAL_SECRET` trong Compose.
- order dashboard controller/service/DTO cùng query graph: không có Gateway/client
  consumer và trùng trách nhiệm Analytics; đã xóa hoàn toàn cùng feature flag sau
  polyrepo proof, không còn dormant API để bật nhầm.
- Restaurant legacy validation interface/DTO/service graph không có controller
  hoặc consumer và đã xóa; canonical internal checkout validation vẫn là
  `OrderValidationController -> OrderCacheValidationService`.
- restaurant cache/location controllers: không có Gateway/client/ops consumer.
  Đã xóa cache warmup/arbitrary availability API, backend geocoding/Mapbox graph,
  flags và repository query chỉ phục vụ các graph này. Canonical restaurant/menu
  mutation vẫn tự cập nhật cache/search; checkout validation vẫn giữ Redis cache
  với DB fallback. Unknown route trả 404. Clean Restaurant proof 87/87.
- Dead `RestaurantCatalogService` Redis home-feed graph đã xóa sau zero-call-site
  polyrepo proof. Graph này không có controller/consumer nhưng từng chạy side
  effect trên create/update/delete và tạo random coordinates/rating/review/price/
  delivery fee cùng fake featured items. Public restaurant reads tiếp tục dùng
  canonical repository/cache; search tiếp tục thuộc search-service.
- search restaurant/dish: `public-client`; query/page/size đã bound và không yêu
  cầu JWT. Search shipper không có Gateway route.
- tracking public REST chỉ còn shipper tự update/offline; arbitrary point read,
  fleet query và busy mutation đã tách sang internal controller, off mặc định và
  fail-closed bằng `Internal-Token` nếu được bật. Raw WebSocket có participant
  check là contract vị trí canonical của MVP.
- delivery legacy `POST /api/deliveries/assign` không có consumer và có thể tạo
  delivery ngoài state machine/outbox canonical nên controller/DTO/service/mapper
  path cùng feature flag đã bị xóa; Saga Kafka command là đường tạo/offer duy nhất
  của MVP.
- notification manual-send không có Gateway route và fail-closed bằng shared
  `Internal-Token`; luồng notification nghiệp vụ chính vẫn đi từ Kafka consumer.
- match legacy event pipeline cùng DTO/orphan topics đã được xóa sau zero-call-site
  proof; canonical flow chỉ còn `shipper.found`/`shipper.not-found` qua Saga.
- analytics/livestream: `experimental/hidden` trong COD-first MVP; chỉ mở lại sau
  role/ownership, retry/idempotency và provider/integration proof.
- settlement payment/VNPay controller off mặc định bằng
  `app.payment.processing-enabled=false`; ba client còn reference contract cũ
  nhưng phải ẩn/chuyển sang COD ở client wave trước backend freeze.

Tại checkpoint 2026-07-23, wave đã thu hẹp order/promotion route theo exact method/path: generic
order update/delete và path-identity list bị ẩn; promotion calculate/reserve không
public. Handler code khi đó vẫn được giữ để migration/recovery và inventory có
197 dòng; các wave xóa/ẩn tiếp theo đã đưa inventory hiện tại về 161.

Actor, auth, ownership, consumer và disposition MVP hiện được phân loại trong
matrix. Các dòng còn ghi `OPEN` là production/recovery proof hoặc capability đang
hidden/disabled, không phải public surface ngầm được phép mở.

### Early polyrepo consumer mismatches

Đây là dependency evidence cho client alignment sau backend freeze/Gate B8.

- Flutter đặt Dio base URL là `http://...:8079/api`, nhưng search datasource lại
  gọi `/api/search/...`; URL kết quả thành `/api/api/search/...`.
- Flutter gọi `POST /api/shippers/in-area`; shipper-service không có route này.
  Nearby lookup hiện nằm ở match/tracking và không nên public cho customer nếu chỉ
  phục vụ matching nội bộ.
- Flutter `GET /api/flashsales/public/campaigns` hiện khớp backend và chỉ nhận
  campaign ACTIVE; public item list chỉ trả item APPROVED. Web admin phải chuyển
  moderation read sang `GET /api/flashsales/admin/campaigns/{id}/items` sau B8.
- Web gọi cả `/api/menu-items/search` và `/api/search/menu-items`; backend search
  dùng `/api/search/dishes`, restaurant-service không có menu search route.
- Web gọi `/api/users/profile`; user-service hiện dùng `GET /api/users` cho current
  user và không có `/profile`.
- Web restaurant confirm hiện đi qua update order status chung; endpoint canonical
  phát event là `POST /api/restaurants/orders/{orderId}/confirm`. Gateway và
  controller đã bắt `SHOP_OWNER|ADMIN`, owner restaurant; order consumer kiểm
  restaurantId khớp order và xử lý duplicate.
- Shipper app có social login `/api/auth/login/google|apple`; backend chỉ có
  `POST /api/auth/social-login`.
- Ba client còn trộn endpoint path có/không có `/api`, direct service port và
  Gateway base. Phase 8 phải map từ use case, không search/replace URL mù quáng.

## Kafka producer/consumer matrix

| Topic | Producer hiện tại | Consumer hiện tại | Tình trạng |
|---|---|---|---|
| `order.created` | order | saga, notification, promotion, flashsale; analytics disabled | active; immutable monetary snapshot plus nullable stable voucher/flash reservation identity. Notification atomically claims `notifications.deduplication_key = order-created:<eventId>` before optional FCM I/O; two Kafka consumers across duplicate partitions plus same/fresh-group replay converge to one stable row, while conflicting reuse goes directly to the owner `.notification.DLT`. The default-off Promotion reservation consumer atomically claims event ID/source/action/order/reservation/SHA-256 payload before commit, with two-replica same/fresh-group replay converging to one receipt and one committed reservation; contradictory reuse goes to `.promotion.DLT`. The default-off Flash-sale consumer atomically claims the same identity dimensions and SHA-256 before commit; its two-replica same/fresh-group Kafka/PostgreSQL rehearsal converges to one receipt/commit and directs contradictory reuse to `.flashsale.DLT`. |
| `order.cancelled` | order | saga, promotion, flashsale, settlement refund boundary (flagged off); analytics disabled | active core cancellation; carries event identity/occurredAt from the transactional outbox, nullable stable voucher/flash reservation identity, immutable monetary snapshot (`subtotalPrice`, `discountAmount`, `shippingFee`, `totalPrice`, `paymentMethod`) and typed `cancelledBySource`/`cancelReasonCode`. Customer/admin/restaurant cancellation, restaurant rejection, and payment failure enqueue the same compensation contract. Default-off Promotion and Flash-sale use atomic receipts before release; Kafka/PostgreSQL two-replica release rehearsals PASS for both. If explicitly enabled, Settlement atomically claims a refund case by event/idempotency/order-trigger before evaluating existing manual-review policy; exact replay no-ops and contradictory raw reuse goes to same-partition DLT. Provider/manual review remains fail-closed and default-off. |
| `order.refund-eligible` | order | promotion, flashsale, settlement refund boundary (flagged off) | active compensation trigger; produced when no-shipper matching exhausts before pickup; carries stable outbox event identity, `SHIPPER_NOT_FOUND` terminal status, typed `SYSTEM`/`SHIPPER_NOT_FOUND` source/reason, reservation IDs and the same immutable monetary snapshot. It never rewrites Order/Delivery to `CANCELLED`; default-off Promotion and Flash-sale atomically receipt the event before release, with two-replica Kafka/PostgreSQL release rehearsal PASS for both. Settlement case creation remains atomically replay-safe and gated. |
| `voucher.reservation.events` | promotion | operations/audit consumers only | deterministic event ID derived from reservation identity and state; transactional outbox; relay separately gated and false by default. |
| `flashsale.reservation.events` | flashsale | operations/audit consumers only | deterministic event ID derived from reservation identity and state; transactional outbox; relay separately gated and false by default. |
| `order.status-updated` | none | analytics (disabled) | không có producer trong polyrepo; orphan Notification listener/service/constants đã xóa, không thuộc MVP runtime contract |
| `restaurant.order-confirmed` | restaurant | order, saga | active; owner + order relation + producer decision/outbox guard; payload mang authenticated `actorUserId`, producer lưu SHA-256 `payload_fingerprint` ngay trên decision row để khóa actor/prep-time/notes và từ chối replay cùng decision nhưng khác payload kể cả khi outbox đã prune; legacy decision row chưa có fingerprint chỉ fallback sang retained outbox payload khi còn tồn tại để giữ compatibility; producer PostgreSQL two-instance duplicate/opposite-decision race, broker-down retry, restart và exact Kafka cardinality PASS; Order internal eligibility khóa order row trước pending check, consumer bắt buộc UUID `eventId`, positive `actorUserId`, khóa Order rồi ghi receipt unique theo event/order cùng payload SHA-256 trong transaction mutation; two-group concurrent duplicate, offset-0 replay, contradictory new-ID DLT/source-lag-0 và restart PASS |
| `restaurant.order-rejected` | restaurant | order | active; cùng decision-row fingerprint/runtime consumer proof và durable receipt boundary như confirm; rejection + receipt + `order.cancelled` outbox commit atomic, `cancelledBy` dùng authenticated `actorUserId` chứ không dùng `restaurantId`; concurrent duplicate/replay giữ đúng một receipt/outbox, relay/restart giữ output offset 1; không coi user cancellation hoặc event/payload khác là duplicate |
| `saga.command.create-delivery` | saga | delivery | active; Saga transition + stable command outbox commit atomic; Delivery bắt buộc `eventId`, positive `shippingFee`, zero discount, exact `subtotal + shippingFee = totalPrice`, COD và đủ pickup/delivery coordinate hữu hạn trong Việt Nam; `delivery_inbound_receipts` claims eventId/command/order/SHA-256 raw payload in the same transaction before validation, delivery mutation, or correlated failure outbox. Exact replay ACKs without another effect; contradictory reuse fails closed. The aggregate unique create identity remains an additional domain fence. Kafka/PostgreSQL two-replica/two-partition same/fresh-group rehearsal converges to one receipt, one `FINDING_SHIPPER` delivery and one `DELIVERY_CREATED_RESULT` outbox; contradictory reuse reaches same-partition DLT. |
| `delivery.created.result` | delivery | saga | active; mang canonical `totalPrice`, `shippingFee`, `paymentMethod`, `restaurantId` cùng delivery/location; Delivery không tự điền phí `15.000` khi command thiếu dữ liệu |
| `delivery.created.failed` | delivery | saga | active; dùng source command UUID làm stable failure/outbox ID, exact replay no-op và contradictory replay fail-closed; hội tụ order về canonical `CANCELLED`, retry/DLT có unit proof |
| `saga.command.find-shipper` | saga | match | active; canonical COD payload mang Saga-owned `matchingSessionId` cho từng initial find/rematch; `DELIVERY_CREATED` phải có persisted `delivery.created.result`, không dùng restaurant event làm fallback cho delivery/location; per-order ordered transactional outbox; Match ghi source fingerprint, unique `(deliveryId, matchingSessionId)`, stage candidate trước Redis reserve và chỉ ACK sau durable command persistence. Consumer group được cấu hình qua `spring.kafka.consumer.group-id` thay vì hard-code. Kafka/PostgreSQL/Redis rehearsal với hai Match application replica và hai partition chứng minh duplicate cross-partition, same/fresh-group exact replay hội tụ đúng một command/result/offer; reuse cùng eventId nhưng raw payload khác đi vào same-partition DLT. Find V1 thiếu session chỉ tương thích tạm thời bằng `eventId` của command. |
| `shipper.found` | match | saga | active; đúng một shipper đủ deposit; `eventId` được dẫn xuất ổn định từ UUID command + outcome và `matchingSessionId` là command generation, durable result outbox fail-closed nếu thiếu identity thay vì sinh UUID mới; key `orderId`, relay retry không chạy lại GEO; concurrent exact replay giữ Redis offer của cùng delivery/session khi result đã staged, không được release nhầm. Match chỉ sở hữu GEO/availability nên không bịa shipper name/phone/rating. |
| `shipper.not-found` | match | saga | active; `eventId` được dẫn xuất ổn định từ UUID command + terminal outcome và luôn mang `matchingSessionId`; durable result outbox key `orderId`; Saga chỉ nhận result thuộc generation hiện hành rồi phát hai command terminal riêng để Order và Delivery cùng hội tụ `SHIPPER_NOT_FOUND`. Hai-replica find ingress proof gồm cùng/fresh-group replay và contradiction-to-DLT cũng áp dụng cho durable result boundary; no-candidate happy path vẫn có Kafka/PostgreSQL/Redis integration coverage. |
| `matching.decision-trace` | match | simulator-service (read-only, dev/test only) | active observability contract; Match ghi versioned `MATCHING_DECISION_TRACE` sau khi business result đã durable, với `nearest-cod-v1`, stage/total latency, attempts, candidates và rejection reasons. Outbox key `orderId`; simulator dùng consumer group riêng và không có business retry/DLT path. Trace mất hoặc malformed không được thay đổi reservation/assignment/Saga state; candidate list là post-GEO filter, không phải raw Redis pool. |
| `saga.command.cache-shipper-found` | saga | delivery | active; `delivery_inbound_receipts` exact-replay/fingerprint fence, then persist offer + expiry trước notification. Kafka/PostgreSQL two-replica/two-partition same/fresh-group rehearsal converges to one receipt, `WAIT_SHIPPER_CONFIRM` offer and one `SHIPPER_OFFERED` outbox; contradictory reuse reaches same-partition DLT. |
| `delivery.offer-persisted` | delivery | saga | active; Delivery emits this transactional-outbox confirmation only after the offer row commits. It carries the source cache command and matching session; Saga uses it as the sole authority to advance Order to `WAIT_SHIPPER_CONFIRM`. |
| `saga.command.expire-shipper-offer` | saga | delivery | active; `delivery_inbound_receipts` exact-replay/fingerprint fence; timeout command mang exact delivery/shipper/deadline generation, Delivery lock row rồi chỉ clear matching expired offer. Kafka/PostgreSQL two-replica/two-partition same/fresh-group rehearsal converges to one receipt and cleared `FINDING_SHIPPER` offer; contradictory reuse reaches same-partition DLT. PostgreSQL/Kafka two-instance accept-vs-expired-timeout, delayed stale timeout và exact timeout replay PASS; source lag 0, DLT không tăng sau valid replay và offsets giữ nguyên qua broker OOM restart; intentionally early timeout fail-closed vào DLT; isolated Saga scheduler→outbox→Kafka→Delivery timeout/rematch, pending relay recovery và peer restart idempotency PASS |
| `saga.command.mark-shipper-not-found` | saga | delivery | active; `delivery_inbound_receipts` exact-replay/fingerprint fence; stable command `eventId` + positive order/delivery identity; Delivery lock đúng row và chỉ chuyển `FINDING_SHIPPER -> SHIPPER_NOT_FOUND`; tách khỏi cancellation để không còn Delivery=`CANCELLED` trong khi Order=`SHIPPER_NOT_FOUND`. Two Delivery replicas in one Kafka group, exact redelivery, fresh-group offset replay and contradictory reuse to same-partition DLT all converge to one receipt/status outbox. |
| `delivery.shipper-offered` | delivery | notification | active; delivery mutation + stable-event outbox commit atomic; payload must carry canonical restaurant/pickup/delivery text (no UI fallback placeholders); Notification requires producer `eventId` and atomically claims its unique dedup key before optional FCM I/O, so replay is suppressed while a later rematch to the same shipper remains deliverable. Kafka/PostgreSQL two replicas on duplicate partitions, same/fresh-group replay and contradictory identity reuse prove one `SENT` row and owner `.notification.DLT`. |
| `saga.command.stop-matching` | saga | match | active; outbox payload bắt buộc `eventId`, positive `orderId`/`deliveryId` và `matchingSessionId` hiện hành. Match ghi durable tombstone `(deliveryId, matchingSessionId)` ở `SERIALIZABLE` trước Redis; stop đến trước find làm find cùng generation persist `CANCELLED` và không chạy GEO. Stop cũ chỉ suppress command/result/offer cùng session, không thể hủy rematch mới. PostgreSQL fence failure vẫn Kafka retry/DLT; Redis failure sau fence ghi `PENDING` + attempts/next-at, ACK Stop và relay `SKIP LOCKED` retry đến `PROJECTED`. Two-replica Compose proves source offset commit, Stop DLT empty và Redis cancellation-key recovery. |
| `saga.command.update-order-status` | saga | order | active; stable command `eventId`, positive top-level `orderId`, JSON-object `originalEvent`; inner `orderId` nếu có phải khớp command; `SHIPPER_NOT_FOUND` bắt buộc positive `deliveryId`. Order transactional processor atomically claims `saga_command_receipts` (eventId, command type, order, Saga status, SHA-256 raw payload) with the Order mutation before listener ACK; exact replay is an ACK/no-op and contradictory reuse reaches retry/DLT. The shared converter preserves this raw String and raw-string DLT preserves rejected payloads. PostgreSQL two-writer and Kafka/PostgreSQL two-replica/fresh-group replay prove one `PENDING -> FINDING_SHIPPER` transition/receipt; contradictory reuse reaches `.order.DLT`. Saga enrich persisted delivery identity cho compensation; failure chỉ dùng canonical `CANCELLED`/`SHIPPER_NOT_FOUND`; per-order ordered transactional outbox; Delivery-status rehearsal tạo đúng 3 command, relay/restart giữ Kafka cardinality 3 |
| `saga.command.cancel-delivery` | saga | delivery | active; chỉ dành cho order/user/restaurant cancellation hoặc failure trước matching; matching/waiting/assigned cancel trước pickup; duplicate CANCELLED skip idempotently; PICKED_UP/DELIVERING/DELIVERED phát stable `delivery.cancel.failed`; ACK failure không bị map nhầm thành business failure. Kafka/PostgreSQL two-replica/two-partition same/fresh-group rehearsal converges to one receipt, one `CANCELLED` delivery and one status outbox; contradictory reuse reaches same-partition DLT. |
| `delivery.shipper-accepted` | delivery | saga | active; canonical payload chỉ gồm positive `orderId/deliveryId/shipperId` và optional `notes`; outbox cấp stable `eventId/occurredAt`; không tự sinh ETA hoặc match/display identity; lock theo delivery + kiểm offered shipper/expiry; transactional outbox; PostgreSQL two-instance same-shipper/two-offer race và exact HTTP replay PASS |
| `delivery.shipper-rejected` | delivery | saga | active; pre-accept reject và assigned-shipper cancellation đều validate identity, clear Saga shipper, cập nhật Order FINDING_SHIPPER rồi rematch với exclusion/limit; exact HTTP replay trước rematch trả state hiện tại và không phát duplicate rejected/status event; transactional outbox; Delivery clone reject/cancel state + outbox cardinality PASS, canonical runtime failure matrix chứng minh rematch sang shipper thứ hai và completion settlement |
| `delivery.status-updated` | delivery | saga, notification | active; self-assigned SHIPPER đi tuần tự `PICKED_UP -> DELIVERING -> DELIVERED`; terminal matching transition `FINDING_SHIPPER -> SHIPPER_NOT_FOUND` cũng ghi status event trong cùng transaction để Notification báo khách hàng, replay command không phát trùng. Saga xử lý `SHIPPER_NOT_FOUND` delivery-status như terminal echo sau step `shipper.not-found`, record idempotent và không phát duplicate `saga.command.update-order-status`; event đến trước matching terminal thì fail-closed để retry. PostgreSQL two-peer out-of-order/concurrent duplicate và exact retry của shipper lifecycle PASS với đúng 3 status outbox; relay/restart giữ offset 3; Saga consume hội tụ đúng 3 step/COMPLETED, source replay exact no-op, contradictory terminal eventId mới retry hữu hạn vào đúng 1 DLT với lag 0. Transactional outbox có canonical customer `userId`, nullable shipperId, `status`/newStatus`; Notification atomically claims its `delivery-status:<eventId>` key and only uses `shipperName` when producer thật sự có, nếu thiếu thì dùng message generic không chứa tên giả. Kafka/PostgreSQL two replicas on duplicate partitions, same/fresh-group replay and contradictory identity reuse prove one `SENT` notification and owner `.notification.DLT`. Analytics có constant nhưng không listener. Kafka runtime proof riêng cho nhánh `SHIPPER_NOT_FOUND` còn OPEN |
| `delivery.cancelled` | none | none | **removed**; Saga `saga.command.stop-matching` là canonical cancellation command |
| `delivery.picked-up` | none | none | **removed**; producer/listener/DTO/constants đã loại, COD chỉ chốt một lần tại completion |
| `delivery.completed` | delivery | settlement | active; producer copies immutable Order `totalPrice`. Consumer requires `totalPrice = restaurantEarnings + restaurantCommission + shippingFee` and `shippingFee = shipperEarnings + shippingCommission`; COD debit uses that same total. Atomic receipt/fingerprint claim and four ledger postings share one transaction; ACK follows commit. Kafka/PostgreSQL two-replica, same-group/fresh-group exact replay yields one receipt/four entries, while contradictory raw reuse reaches the same-partition DLT. |
| `delivery.find-shipper` | none | none | **removed**; publisher/DTO/constants đã loại, Saga dùng `saga.command.find-shipper` |
| `shipper.status-change` | delivery | match, tracking routing projection | active; stable eventId + positive shipper/delivery/order/timestamp; Match atomically release đúng offer, mutate BUSY/AVAILABLE và lưu version fence; exact/stale replay no-op, same-timestamp contradictory event fail-closed. Tracking uses Redis Lua compare-and-set for its active-delivery routing projection so multi-replica stale BUSY/AVAILABLE cannot regress the room; a contradictory same-time BUSY fact is poison and reaches its owner `.tracking.DLT`. Tracking does not own availability business state. |
| `shipper.location-updated` | tracking | match, tracking history | active; Match fields remain compatible; new producer adds stable eventId, nullable deliveryId, optional accuracy/speed/heading/source. Online update requires valid coordinates; explicit offline remains a tombstone. Match atomically applies the timestamp freshness fence plus GEO/online mutation in Redis Lua, so a concurrent stale online record cannot resurrect a newer offline tombstone across replicas. Async support history atomically claims `(eventId, delivery/shipper identity, occurredAt, SHA-256 raw payload)` with PostgreSQL `ON CONFLICT DO NOTHING` before one sampled point/outcome; exact same/fresh-group replay is no-op while contradictory reuse reaches same-partition `.tracking.DLT`. Kafka/PostgreSQL two-Tracking-replica/two-partition rehearsal proves one receipt/point; Redis 7 concurrent online/offline proof passes. This does not alter Redis/WebSocket realtime contract or Match selection policy. |
| `entity-sync` | restaurant | search | active for restaurant/dish; mutation + UUID/occurredAt outbox row commit atomically, existing relay retries/DEAD. Search atomically claims a per-entity checkpoint with Elasticsearch scripted upsert (`occurredAt`/eventId/action/canonical SHA-256 fingerprint); a legacy missing fingerprint is upgraded only after exact metadata normalization (including zero-second/UTC-offset date serialization), by `_seq_no`/`_primary_term` compare-and-set. It then writes the typed document with nanosecond `occurredAt` as `external_gte` version. Exact crash retry may reapply; stale/reordered records cannot overwrite a newer update or resurrect a newer DELETE, malformed payloads fail before a document write, and contradictory same-ID input fails closed. Kafka + Elasticsearch two-replica/two-partition rehearsal proves reorder, same/fresh-group replay and owner DLT convergence; cluster outage/index-rebuild and controlled production replay remain OPEN. |
| `payment.completed` | settlement payment graph (disabled) | order, analytics (disabled) | inactive trong COD MVP; producer/listeners đều feature-gated off |
| `payment.failed` | settlement payment graph (disabled) | order, analytics, flashsale (disabled) | inactive trong COD MVP; không thuộc runtime contract hiện tại |
| `shipper.matched` | none | none | **removed**; canonical result là `shipper.found` |
| `no.shipper.available` | none | none | **removed**; canonical result là `shipper.not-found` |
| livestream topics | publisher code bị comment | không có | inactive/experimental |

### Saga orchestrator hardening checkpoint (2026-08-09)

- `order.cancelled` and `restaurant.order-confirmed` are the only early facts
  currently staged by Saga. A valid event arriving before the aggregate exists
  is stored in `saga_early_events`, then promoted into the normal
  `saga_inbound_receipts` fence before it can mutate state or write commands.
  `SagaEarlyEventScheduler` covers the commit-after-drain race.
- Scheduler timeouts use a typed internal `SAGA_TIMEOUT` payload with a
  deterministic identity derived from Saga identity/status/version,
  `expectedStatus`, `expectedVersion`, `observedUpdatedAt` and `deadlineAt`.
  The manager re-locks the row and treats stale, early and exact-replay polls as
  no-ops; it claims the inbox only after the observation is still due.
- `saga.command.find-shipper` carries the first matching
  `matchingDeadlineAt`; offer/rematch commands preserve that absolute cutoff.
  Match stops before retry/reserve/publish after the cutoff, releases a late
  reservation, and emits the deterministic `shipper.not-found` outcome.
- Every `MATCHING_STARTED` step stores a deterministic Saga-owned
  `matchingSessionId`; every Find command and Match result carries that same
  generation. Saga ignores an old `shipper.found`/`shipper.not-found` after a
  rematch instead of allowing an old result to advance the new attempt.
- `saga.command.stop-matching` targets the persisted current generation. Match
  stores a `(deliveryId, matchingSessionId)` cancellation tombstone before its
  volatile Redis projection, so a stop-before-find creates a durable no-GEO
  cancellation and an old stop cannot release a newer rematch offer. After
  that fence commits, a Redis failure is a durable `PENDING` projection relay
  with attempts/backoff and PostgreSQL `SKIP LOCKED` claims, not a Kafka retry
  that can exhaust and strand the projection.
- A late `delivery.created.result` after `CANCELLED`/`FAILED` records the one
  Delivery identity and reissues `saga.command.cancel-delivery`. When a Delivery
  is known, Saga remains `COMPENSATING` until the atomic
  `delivery.status-updated(CANCELLED)` confirmation. `delivery.cancel.failed`
  is recorded as `DELIVERY_CANCEL_FAILED` and moves Saga to visible `FAILED`;
  cancellation-vs-pickup/refund policy is intentionally still open.
- Saga's receipt and early-event claims use PostgreSQL `INSERT .. ON CONFLICT
  DO NOTHING`; an exact concurrent claimant reloads and ACKs the committed
  record, while a topic/order/raw-fingerprint mismatch is poison and uses the
  owner `.saga.DLT`. Kafka + PostgreSQL 16 two-replica/two-partition rehearsal
  covers `order.created` (one `STARTED` Saga and create-delivery outbox),
  `delivery.created.result` (one `DELIVERY_CREATED` transition) and an early
  `order.cancelled` (one staged fact), including exact same/fresh-group replay
  and contradictory reuse to the source partition DLT.
- Focused Maven/H2 proof for these paths is green. Match now owns `match_db`
  command receipts, candidate staging and a deterministic result outbox; Redis
  offers/GEO remain volatile projections. Two-replica PostgreSQL/Kafka/Redis
  stop-projection recovery and crash/replay rehearsals pass; broader
  cross-service recovery remains `OPEN`.

### Rollout for generation-aware `stop-matching`

1. Quiesce/drain legacy Saga publishers that can emit a broad stop payload, then
   apply Match Flyway V2 and the Match V2 code to every Match replica before
   enabling the new Saga producer. Match V2 accepts a V1 Find without
   `matchingSessionId` only by using its command `eventId` as the temporary
   generation; it rejects a legacy broad stop rather than guessing a target.
2. Deploy Saga V2 and resume command production. A persisted legacy
   `MATCHING_STARTED` step without a session emits no `stop-matching` command
   (but still emits `cancel-delivery`), preventing a broad stop from cancelling
   a later rematch. New initial finds, reject rematches and offer-timeout
   rematches all persist an explicit generation.
3. Monitor Match stop retry/DLT, tombstone conflicts and unsent-result
   cancellations during the drain. Do not remove the V1 Find fallback until all
   pre-V2 find records have aged past the operational replay-retention window.

## Phase 2 core-consumer idempotency audit

| Consumer boundary | Stable identity | Durable dedup / conflict boundary | Current proof |
|---|---|---|---|
| Notification `order.created` | Producer `eventId` | PostgreSQL `INSERT .. ON CONFLICT (deduplication_key) DO NOTHING` commits one `PENDING` row in `REQUIRES_NEW` before FCM I/O; replay compares the complete semantic notification payload before delivery/no-op | Kafka + PostgreSQL 16 two-replica duplicate-partition, same-group and fresh-group replay proof PASS 2026-08-12; one row reaches `SENT`, contradictory reuse goes directly to same-partition `.notification.DLT` |
| Notification `delivery.status-updated` | Producer `eventId` | The same atomic `delivery-status:<eventId>` claim commits a `PENDING` row before FCM I/O; status payload conflict is rejected | Kafka + PostgreSQL 16 two-replica duplicate-partition, same-group and fresh-group replay proof PASS 2026-08-12; one row reaches `SENT`, contradictory reuse goes directly to same-partition `.notification.DLT` |
| Notification `delivery.shipper-offered` | Producer `eventId` plus shipper identity | The same atomic `shipper-offer:<eventId>:<shipperId>` claim commits a `PENDING` row before FCM I/O; offer payload conflict is rejected | Kafka + PostgreSQL 16 two-replica duplicate-partition, same-group and fresh-group replay proof PASS 2026-08-12; one row reaches `SENT`, contradictory reuse goes directly to same-partition `.notification.DLT` |
| Promotion reservation `order.created` | Order `eventId`, source topic/action/order/reservation | `promotion_order_reservation_receipts` primary-key inbox retains source topic, COMMIT/RELEASE action, order/reservation identity and SHA-256 raw payload; PostgreSQL `ON CONFLICT DO NOTHING` commits it with the reservation transition before listener ACK | Kafka + PostgreSQL 16 two-replica duplicate-partition, same-group and fresh-group replay proof PASS 2026-08-12; one receipt/COMMITTED reservation/outbox transition remains, contradictory reuse goes directly to same-partition `.promotion.DLT`. Checkout and relay remain false by default. |
| Promotion reservation `order.cancelled` / `order.refund-eligible` | Order `eventId`, source topic/action/order/reservation | The same receipt fence classifies both as RELEASE and commits/rolls back with the reservation transition | Kafka + PostgreSQL 16 two-replica duplicate-partition, same-group and fresh-group replay proof PASS 2026-08-12 for each release source; one expected RELEASED reservation/outbox transition remains, contradictory reuse goes directly to same-partition `.promotion.DLT`. |
| Order restaurant decisions | Restaurant `eventId`, `orderId` | `restaurant_decision_receipts` unique receipt + SHA-256 payload fingerprint in the order mutation transaction | Existing PostgreSQL two-group duplicate/offset-reset/restart proof recorded above |
| Order Saga update-status command | Saga command `eventId`, order aggregate | `saga_command_receipts` primary-key inbox retains command type/order/Saga status/SHA-256 raw payload; PostgreSQL `ON CONFLICT DO NOTHING` resolves concurrent claims, and the processor commits the Order mutation before listener ACK | Kafka + PostgreSQL 16 two-replica duplicate-partition, same-group and fresh-group replay proof PASS 2026-08-12; one receipt/`PENDING -> FINDING_SHIPPER` transition remains, contradictory reuse goes directly to same-partition `.order.DLT`. |
| Delivery Saga commands | Saga command `eventId`, delivery/order aggregate | `delivery_inbound_receipts` primary-key inbox retains command type/order/delivery/SHA-256 raw payload; PostgreSQL `ON CONFLICT DO NOTHING` resolves concurrent claims, and the processor commits Delivery mutation/correlated failure outbox before listener ACK; aggregate row locks/create identity remain domain fences | Focused exact/conflicting replay, H2 Flyway/JPA transaction rollback and PostgreSQL two-writer receipt/terminal-outbox proof PASS 2026-08-12. Kafka + PostgreSQL 16 two-replica duplicate-partition, same-group and fresh-group rehearsal now PASS for all five commands: create produces one `FINDING_SHIPPER`/created-result outbox, cancel one `CANCELLED`/status outbox, cache offer one `WAIT_SHIPPER_CONFIRM`/offer outbox, expire offer one cleared `FINDING_SHIPPER` state and no extra outbox, and no-shipper one terminal status outbox. Contradictory reuse reaches each source's same-partition DLT. |
| Saga inbound events | Producer `eventId`, order aggregate | `saga_inbound_receipts` primary-key inbox stores source topic, order and SHA-256 raw payload; PostgreSQL `ON CONFLICT DO NOTHING` claims atomically with Saga mutation/outbox, exact replay reloads/ACKs and contradictory reuse rejects. Before an aggregate exists, `saga_early_events` uses the same atomic identity/topic/order/fingerprint claim and is promoted through the normal inbox. | Focused receipt/Flyway proof and Kafka + PostgreSQL 16 two-Saga-replica/two-partition rehearsal PASS 2026-08-13 for `order.created` one `STARTED`/create-delivery outbox, `delivery.created.result` one `DELIVERY_CREATED` transition, and early `order.cancelled` one staged fact; same/fresh group replay is a no-op and contradictory raw reuse reaches same-partition `.saga.DLT`. |
| Settlement `delivery.completed` | Delivery `eventId`, order/delivery aggregate | `settlement_receipts` event/order unique receipt + immutable SHA-256 fingerprint in the ledger transaction; `ON CONFLICT DO NOTHING` makes a concurrent exact claim converge to a no-op before financial postings | Kafka + PostgreSQL 16 two-replica/same-group and fresh-group replay proof PASS 2026-08-12; contradictory raw reuse reaches same-partition `.DLT` |
| Settlement gated refund triggers | Order `eventId`, order/trigger aggregate | `refund_cases` event/idempotency/order-trigger constraints plus an atomic PostgreSQL `ON CONFLICT DO NOTHING` claim; provider outbox is inserted only by the successful claimant in the same transaction | Kafka + PostgreSQL 16 two-replica/same-group and fresh-group `order.cancelled` rehearsal PASS 2026-08-12; contradictory raw reuse reaches same-partition `.DLT`; flags remain false by default |

Saga receipt races are resolved by PostgreSQL `INSERT .. ON CONFLICT DO NOTHING`
before the handler performs a mutation or writes its command outbox. A concurrent
loser reloads the committed exact receipt and ACKs as a no-op; it does not depend
on a duplicate-key exception or Kafka retry for convergence. A reused event ID
with a different topic/order/payload remains a poison record and enters the
configured owner DLT path.

Cross-cutting Kafka issues:

- Local Compose khóa rõ Kafka auto-create topic cho MVP/runtime rehearsal, nên
  retry/DLT topic không phụ thuộc default ẩn của image. Production vẫn phải đổi
  sang explicit topic provisioning/retention/ACL trước khi Gate C12.
- Custom Kafka config của order, delivery, match, saga, notification và
  feature-gated Promotion đã đọc
  `spring.kafka.bootstrap-servers`; Compose override được unit/render proof.
- Saga, notification, Order và feature-gated Promotion không còn ack parse/service
  failure; retry hai lần
  rồi publish cùng partition sang `<source-topic>.DLT`, và DLT send failure tiếp
  tục fail-closed. Các projection listener Match dùng factory sync riêng để
  common error handler có thể seek/retry; chỉ listener `Mono` tìm shipper dùng
  async ACK factory. Manual-immediate DLT recovery của Order/Delivery/Saga/Match/
  Notification/Promotion commit source offset sau publish thành công, tránh poison bị DLT
  lại ở mỗi restart. Runtime fresh-group/poison/restart proof đã PASS cho năm
  service. Match lỗi hạ tầng tìm shipper đi qua retry topics rồi
  `saga.command.find-shipper.DLT`. Producer/outbox policy ở các service còn lại
  vẫn chưa thống nhất.
- Các listener raw-JSON thuộc Notification, Saga, Delivery, Order Saga-command và
  Settlement dùng `retryKafkaTemplate` với `StringSerializer`; retry/DLT không
  được dùng `JsonSerializer` để tránh JSON bị quote lồng qua mỗi hop. Runtime
  Notification rehearsal ngày 2026-07-30 đã xác nhận đúng cùng JSON object ở
  source, `-retry-1000`, `-retry-2000`, `-retry-4000` và DLT khi PostgreSQL
  unavailable; sau recovery replay source hai lần tạo đúng một durable
  `order-created:<eventId>` notification.
- Delivery, Saga và Match dùng `earliest` khi consumer group state không tồn tại;
  Match bỏ qua online-location replay cũ quá freshness 300 giây nhưng vẫn ACK,
  nên rebuild group không bỏ command durable hoặc resurrect shipper stale.
- Event dùng lẫn typed DTO, `Map`, `JsonNode`, JSON string và `Object`; chưa có
  version/schema compatibility test.
- Topic key không thống nhất giữa `orderId`, `deliveryId` và chuỗi
  `delivery_<id>`, làm ordering theo aggregate không được bảo đảm xuyên luồng.

## WebSocket inventory

| Endpoint | Protocol | Producer/consumer | Tình trạng |
|---|---|---|---|
| `/ws/shipper-locations` | raw WebSocket JSON | shipper publish; delivery participant subscribe | canonical payload/actions unchanged; Tracking validates JWT through Auth JWKS in the handshake, then checks participant access; exact delivery rooms and Redis Pub/Sub prevent old-delivery/IDOR audience; bounded per-session coalescing preserves latest state and offline/online transition, reconnect sends final Redis location; publisher generation, grace/lease tombstone and stale fences unchanged |
| `/ws/delivery-native` | STOMP | none | removed: client migration complete; Delivery config/notifier/dependency/properties/Compose flag đã xóa, lifecycle tiếp tục qua REST + Kafka/outbox |
| `/ws-native` | STOMP | none | removed: zero polyrepo caller; Notification broker/config/service/DTO/dependency và Compose flag đã xóa, không phải compatibility surface MVP |
| `/ws-test` | HTML test page | developer | phải `dev-only` |

gRPC tracking không thuộc MVP; dependency, generated-source build hook, Java
skeleton, `.proto` và guide vận hành cũ đã được loại khỏi tracking-service.
Build-baseline verifier chặn gRPC/protobuf runtime artifact quay lại.

## State-machine inventory

### Order and delivery convergence

| Business stage | Order hiện tại | Delivery hiện tại | Saga hiện tại | Canonical public state |
|---|---|---|---|---|
| vừa checkout | `PENDING` | chưa có / `PENDING` | `STARTED` | `PENDING` |
| nhà hàng đồng ý | `CONFIRMED` | `FINDING_SHIPPER` | `DELIVERY_CREATED`/`FINDING_SHIPPER` | `CONFIRMED` rồi `FINDING_SHIPPER` |
| đã gửi một offer | `WAIT_SHIPPER_CONFIRM` qua Saga command | persist offered shipper + expiry và `WAIT_SHIPPER_CONFIRM` | `SHIPPER_FOUND` | `WAIT_SHIPPER_CONFIRM` |
| shipper nhận | `ASSIGNED` | `ASSIGNED` | `SHIPPER_ASSIGNED` | `ASSIGNED` |
| lấy hàng | `PICKED_UP` | `PICKED_UP` | `PICKING_UP` | `PICKED_UP` |
| đang giao | `DELIVERING` | `DELIVERING` | `DELIVERING` | `DELIVERING` |
| hoàn tất | `DELIVERED` | `DELIVERED` | `COMPLETED` | `DELIVERED` |
| hủy | `CANCELLED` | `CANCELLED` chỉ cho một số source state | `CANCELLED`/`COMPENSATING` | `CANCELLED` |
| hết shipper | `SHIPPER_NOT_FOUND` | `SHIPPER_NOT_FOUND` | `FAILED`/dedicated terminal command | `SHIPPER_NOT_FOUND` |

Drift phải giải quyết trước freeze:

- Order persistence/public vocabulary đã dùng `OrderStatus` enum và normalize
  một số alias legacy chỉ ở boundary; transition domain đã có focused proof.
- Delivery offer persist nay chuyển `FINDING_SHIPPER -> WAIT_SHIPPER_CONFIRM`,
  accept chỉ nhận từ WAIT và exact offer replay idempotent. Transition vẫn cho đường tắt
  `PENDING -> ASSIGNED`, `SHIPPER_NOT_FOUND -> ASSIGNED` và cancel implementation
  không dùng cùng transition table; cần đóng compatibility path trước freeze.
- Saga status là orchestration state nội bộ; không được trả trực tiếp như public
  order status. Cần mapping có test thay vì dùng cùng tên một cách ngẫu nhiên.
- Restaurant decision đã có identity receipt/state guard; cross-service race và
  Kafka restart vẫn phải chứng minh ở Gate B8.

Canonical transition dự kiến cho COD MVP:

`PENDING -> CONFIRMED -> FINDING_SHIPPER -> WAIT_SHIPPER_CONFIRM -> ASSIGNED ->
PICKED_UP -> DELIVERING -> DELIVERED`.

Nhánh terminal/loop:

- nhà hàng reject hoặc actor hợp lệ cancel trước pickup: `-> CANCELLED`;
- shipper reject/offer timeout: `WAIT_SHIPPER_CONFIRM -> FINDING_SHIPPER`;
- hết retry/candidate: `FINDING_SHIPPER -> SHIPPER_NOT_FOUND`;
- cancel sau pickup không dùng cùng rule cancel đơn thông thường; cần policy tài
  chính/return flow riêng, không tự suy diễn trong MVP.

### Shipper availability

Hiện có hai nguồn state: `shipper-service.isOnline` trong PostgreSQL và busy key
trong Redis của tracking/match. Canonical eligibility để nhận offer phải là
`isOnline && locationFresh && !busy && noActiveDelivery`; accept phải atomically
đổi offer thành assignment và busy. Offline, reject, timeout, cancel và delivered
phải có owner rõ ràng cho thao tác release; hiện chưa có single source of truth.

Với COD, Match còn gọi exact internal Settlement API bằng shared secret và chỉ
offer candidate có `depositBalance >= totalPrice` canonical. Candidate được duyệt
theo khoảng cách, tối đa 20 mỗi lượt, nhưng chỉ một shipper được Redis `SETNX`
reserve/publish. Redis/Settlement lỗi là infrastructure failure và record không
được ack hoặc đổi thành terminal `shipper.not-found`. Chưa có hold tiền riêng khi
offer; invariant một active offer/assignment mỗi shipper làm giới hạn MVP, còn
rehearsal đồng thời bằng Redis/PostgreSQL thật vẫn OPEN.

### Payment and settlement

- Payment order: `PENDING -> SUCCESS | FAILED | EXPIRED`.
- Ledger transaction: `PENDING -> COMPLETED | FAILED | REVERSED`.
- COD MVP không cần payment order cho checkout. Ledger chỉ chốt ở
  `delivery.completed`: credit restaurant net, credit shipper earnings, debit
  tổng tiền mặt khỏi deposit của shipper và credit platform commission.
- COD debit fail-closed nếu deposit tại thời điểm completion không còn đủ; receipt,
  ledger và balance mutation cùng rollback để không tạo số dư ký quỹ âm.
- Pickup deduction legacy đã bị ngắt để không double-debit. Consumer completion
  rethrow lỗi để rollback toàn transaction và dùng durable receipt làm replay
  boundary. DB unique business key chặn trùng từng ledger
  entry và balance mutation dùng pessimistic row lock; migration có preflight,
  nhưng PostgreSQL concurrent replay rehearsal vẫn cần trước Gate P5.

## Baseline P0 blockers and current status

1. Restaurant web vẫn dùng API update status chung; backend canonical confirm đã
   an toàn nhưng Phase 10 phải chuyển web sang endpoint phát event.
2. Restaurant confirm/reject đã kiểm role + owner; order consumer kiểm
   restaurantId thuộc order. Kafka replay/integration proof vẫn OPEN.
3. Offer-one đã persist; Redis `SETNX` chặn concurrent offer và partial unique
   index chặn concurrent active assignment. Index được wire vào startup
   `schema.sql`, accept dùng `saveAndFlush` trước khi phát BUSY/event. PostgreSQL
   startup/migration/concurrency rehearsal và atomic compare-delete release vẫn OPEN.
4. Delivery USER view đã đối chiếu `creatorId`; tracking subscribe đã kiểm active
   delivery, đúng assigned shipper và USER/SHIPPER/ADMIN participant qua internal
   shared-secret boundary. Existing subscription chưa tự revoke ngay khi delivery
   sang terminal nếu socket vẫn mở; cần terminal event/reauthorization proof.
5. Status vocabulary giữa order, delivery, saga và ba client khác nhau; nhánh
   cancel/not-found không đi tới cùng terminal state.
6. Script E2E hiện tại gọi accept trực tiếp sau sleep, nên không chứng minh match
   hoặc notification thật sự hoạt động.

## Remaining Phase 0 proof

- HTTP method-level matrix và polyrepo consumer mapping.
- Startup baseline của Compose từ dữ liệu sạch (không xóa volume hiện có khi chưa
  có xác nhận); health/readiness và E2E hiện trạng.
- Chốt error envelope, pagination, money/time/ID semantics và event key/version
  convention trong decision record trước Phase 1.

## Executable baseline 2026-07-22

- `docker compose -f docker-compose.yml config --quiet`: **PASS**.
- Reactor `mvn -DskipTests package` với JDK 17: **PASS**, đủ 17/17 module.
- Cùng lệnh bằng Maven hệ thống tự chọn JDK 25: **FAIL** ở tracking-service do
  Lombok annotation processing không sinh getter/setter/logger. Đây là lỗi
  toolchain reproducibility; source build được bằng Java version mà các POM khai
  báo (`17`). Phase 1 phải pin/enforce JDK thay vì phụ thuộc PATH của máy.
- Reactor `mvn test` với JDK 17: **FAIL** ngay ở auth-service vì context test dùng
  profile mặc định và nối PostgreSQL thật. Maven skip 16 module còn lại.
- Chạy test từng module trong sandbox: 17/17 báo fail. Hai nhóm nguyên nhân quan
  sát được là context test nối PostgreSQL/Redis thật và Mockito inline mock maker
  không được phép self-attach trong sandbox. API Gateway context test chạy lại
  ngoài sandbox: **PASS 1/1**, xác nhận lỗi Gateway trước đó là môi trường.
- Baseline này chưa chứng minh business rule: nhiều module chỉ có một
  `contextLoads`, còn các test có nghiệp vụ vẫn chưa tách khỏi DB/Redis hoặc cấu
  hình Mockito của máy. Phase 1 cần test profile/Testcontainers và Mockito agent
  setup ổn định trước khi dùng test suite làm gate.

## Dependency baseline audit 2026-07-23

- JDK authority và runbook đều là Java 17. Tất cả 17 module đã được
  normalize sang Spring Boot `3.5.15`.
- Gateway dùng Spring Cloud `2025.0.3`, release train chính thức cho Boot 3.5.x,
  và starter mới `spring-cloud-starter-gateway-server-webflux`.
- Flashsale, analytics và promotion đã bỏ Eureka dependency/BOM `2022.0.3`;
  promotion bỏ `@EnableDiscoveryClient`. Eureka được roadmap xếp post-MVP,
  còn Compose MVP không có registry và dùng internal URL tường minh.
- Task 21 (2026-08-01) supersedes that historical exception for Promotion and
  Flash-sale: both now consume the supported `runtime-platform-starter` and
  register through Spring Cloud `2025.0.3` so their Gateway `lb://` routes are
  executable. Analytics and Livestream remain outside the active registry set.
- `scripts/verify-build-baseline.sh` fail khi JDK, Boot parent, Cloud BOM hoặc
  Gateway starter lệch baseline.
- Proof sau migration: verifier pass; reactor `-DskipTests package` pass 17/17;
  full Gateway test 12/12, focused Auth 16/16 và User 12/12 pass trên JDK 17.
- Public password registration đã tách thành hai client request: Auth persist
  identity và phát opaque digest-only handoff trước; exact public User endpoint
  resolve immutable `authId/email/role` qua internal Auth credential, create
  idempotent theo `authId`, rồi complete exact `userId` link. Lost response/callback
  được retry mà không rebind identity; login fail-closed tới khi link hoàn tất.
  Social/operator provisioning vẫn dùng Auth→User internal call. Unique-email
  race reload account thắng để resume. Public flow không tạo mới SHIPPER;
  existing operator-provisioned SHIPPER vẫn login được. Profile mutation dùng
  identity từ Gateway. Default-address mutation serialize bằng pessimistic owner
  lock. Auth clean 44/44, User clean 31/31; PostgreSQL migration/race integration
  vẫn là gate riêng.
- Phase 3 edge proof: restaurant exact route/multi-role/internal credential đạt
  9/9 focused tests; order pricing/IDOR/event/MVP policy đạt 14/14. Compose verifier
  xác nhận auth, user, order và restaurant dùng cùng internal secret non-blank.
- Restaurant validation trả canonical item name/price từ cache/DB; thiếu
  restaurant name không còn trả `Unknown Restaurant`/`N/A` mà fail-closed; order tính
  subtotal và lưu `order_items` từ dữ liệu này, không từ client. Duplicate menu ID
  fail sớm. Clean focused order test 14/14 và reactor package 17/17 tiếp tục xanh.
- Order persistence dùng canonical enum + transition table:
  `PENDING -> CONFIRMED -> FINDING_SHIPPER -> WAIT_SHIPPER_CONFIRM -> ASSIGNED ->
  PICKED_UP -> DELIVERING -> DELIVERED`, với các nhánh terminal cancel/not-found.
  Converter đọc alias legacy nhưng chỉ ghi canonical. Migration idempotent có
  unknown-value preflight nằm tại
  `docs/migrations/2026-07-23-order-status-canonical.sql`; PostgreSQL rehearsal
  vẫn OPEN.
- Saga order command hiện cập nhật cả finding/wait state, bắt buộc `orderId` dương,
  và reject `sagaStatus` không được hỗ trợ thay vì log rồi acknowledge. Command
  validation/service failure vì vậy đi tiếp vào retry/DLT; chỉ mutation thành công
  mới ACK. Rematch `ASSIGNED -> FINDING_SHIPPER` xóa shipper cũ; acceptance không
  thể ghi đè một shipper khác trên order assigned/đang giao. Restaurant rejection
  chỉ idempotent khi khớp canonical cancel reason, không còn giả làm duplicate của
  user cancellation; delayed confirmation sau khi matching đã bắt đầu được coi là
  replay. Unknown delivery status fail-closed. Dead Order-local shipper rejection
  handler không có call site đã xóa. Generic HTTP status/assign đã bị ẩn khỏi
  Gateway. Full Order suite 51/51 và Gateway 19/19 xanh; Kafka replay/crash-window
  proof vẫn thuộc Gate B8.
- Flash-sale routes tách public GET, merchant `SHOP_OWNER`, admin `ADMIN`; internal
  reserve fail-closed bằng shared secret. Promotion/flash-sale reserve đều trả
  unavailable khi feature flag mặc định false và compensation listener tương ứng
  không được tạo bean. Proof: Promotion 4/4, Flash-sale 4/4, Gateway 12/12,
  Compose verifier và reactor package 17/17 xanh.
- Reservation chưa hoàn tất: promotion đang check-then-increment; flash-sale
  decrement Redis rồi update DB từng item, không có order-scoped unique record,
  nên duplicate/partial failure/replay chưa an toàn. Merchant flash sale cũng
  chưa chứng minh `restaurantId` thuộc JWT owner.
- Promotion production schema nay do Flyway V1 sở hữu và Hibernate chỉ `validate`:
  clean/legacy/missing/incomplete/duplicate proof khóa voucher, wallet, group và
  exclusion tables cùng unique/index contract. `calculate` cũng trả 503 khi
  checkout tắt thay vì trả totalDiscount=0 gây hiểu nhầm; năm finder không caller
  đã xóa. PostgreSQL DDL/concurrent collect vẫn cần runtime proof.
- Flash Sale checkout tắt không còn tạo custom Redis config/stock-service bean;
  internal boundary vẫn 503 và fail-closed nếu graph vắng. Recurring daily reset
  dùng một bulk update thay vì load toàn bộ approved item vào JVM; một finder
  unbounded/dead đã xóa. Reservation algorithm hiện hữu vẫn không đủ authority để
  bật checkout.
- Flash-sale merchant registration gọi internal restaurant ownership endpoint
  với merchant ID từ JWT. Restaurant rating gọi internal order eligibility trước
  DB transaction, chỉ nhận đúng customer + restaurant + `DELIVERED`; DB unique
  `order_id` giữ duplicate boundary. Hai endpoint fail-closed và không public.
  Proof: Restaurant 11/11, Flash-sale 5/5, Order 25/25, Gateway 14/14,
  Compose verifier và reactor package 17/17.
- Delivery accept đã có DB-level guard cho một active delivery/shipper bằng
  partial unique index ở startup SQL; test profile tắt SQL init, service bắt
  `DataIntegrityViolationException` và không phát BUSY/event khi flush xung đột.
  Full Delivery suite 10/10 trên H2; việc index thực sự được tạo và tranh chấp
  đúng trên PostgreSQL vẫn OPEN vì chưa có runtime/Testcontainers proof.
- Match command listener phân biệt `NoShipperAvailableException` với lỗi hạ tầng:
  chỉ hết candidate sau business retry mới stage `shipper.not-found`. Inbox
  fingerprint, candidate-before-reserve staging và result outbox làm exact
  replay no-op, resume candidate sau crash và broker retry không chạy lại GEO;
  Spring Kafka vẫn quản lý Mono completion với async ack, còn lỗi hạ tầng đi qua
  retry topics/DLT. Output ID deterministic theo command generation; GEO
  projection không tạo profile/rating giả. H2 command/outbox proof xanh;
  PostgreSQL/Kafka restart/order/DLT cardinality proof vẫn OPEN.
- Notification REST list/read/mark/delete đã enforce owner từ JWT tới repository;
  inbox chỉ đọc PostgreSQL, cache Redis write-only đã bị xóa. Mark-read retry là
  idempotent và không ghi đè `readAt`. Kafka-derived notification có
  business dedup key + DB unique constraint, listener chỉ ack sau success và lỗi
  được retry rồi chuyển DLT. FCM token registration dùng Redis Lua reverse-owner
  atomic không dùng owner/membership TTL lệch nhau để ngăn cross-account reuse.
  Manual mapper thay MapStruct bean lỗi, context
  test dùng H2 độc lập. Notification STOMP không thuộc raw-location MVP và bị ẩn
  mặc định bằng rejecting handshake. Full Notification suite 14/14; PostgreSQL/Redis/Kafka replay
  và DB→WebSocket/FCM outbox vẫn OPEN.
- Saga khóa aggregate theo order bằng pessimistic row lock + optimistic version,
  unique `order_id`, chống duplicate reject, kiểm sequence pickup→delivering→done.
  Publish Kafka failure được propagate để rollback/retry; consumer lỗi vào DLT.
  Timeout/failure hội tụ Order về canonical `CANCELLED` hoặc
  `SHIPPER_NOT_FOUND`, không còn gửi status lạ bị Order bỏ qua. Focused Saga
  proof hiện tại 34/34; timeout query deterministic/cap theo batch và lỗi một aggregate không
  chặn phần còn lại. Migration/replay và atomic DB→Kafka outbox vẫn OPEN.
- Order create/cancel không còn gửi Kafka trực tiếp: cùng transaction ghi
  `outbox_events` với stable `eventId`; relay claim nhiều instance bằng
  `FOR UPDATE SKIP LOCKED`, exponential backoff và terminal `DEAD`. Transaction
  rollback/commit, Flyway-only schema và relay metadata có H2 proof, full Order 30/30; broker restart,
  crash sau send/trước mark và consumer dedup vẫn cần PostgreSQL/Kafka rehearsal.
- Restaurant confirm/reject kiểm owner ở edge và gọi internal Order eligibility
  trước decision; PostgreSQL advisory transaction lock đảm bảo quyết định đầu tiên
  theo `orderId`, record quyết định và outbox commit atomic. Duplicate cùng quyết
  định no-op, quyết định đối nghịch conflict. Full Restaurant 69/69; advisory-lock,
  relay restart và consumer replay vẫn cần proof trên PostgreSQL/Kafka thật.
- Delivery mutation và correlated command failure không còn gửi Kafka trực tiếp:
  Delivery cùng stable-event outbox commit/rollback atomic; relay dùng
  `FOR UPDATE SKIP LOCKED`, bounded backoff và terminal `DEAD`. Full suite đạt
  21/21 sau khi gộp test relay trùng; PostgreSQL/Kafka crash sau send/trước mark
  và consumer dedup vẫn thuộc Gate B8.
- Saga transition và command kế tiếp commit atomic vào `saga_outbox_events` với
  stable `eventId`; relay chỉ claim command đầu hàng trên mỗi `orderId`, bounded
  backoff và `DEAD` chặn command sau để giữ ordering. Full Saga 14/14;
  PostgreSQL multi-instance lock và Kafka crash-window vẫn cần rehearsal.
- Cross-service contract wave sửa Delivery owner từ restaurant-owner
  `Order.creatorId` sang customer `Order.userId`; nhờ đó participant auth và
  status notification dùng đúng người. Match result không còn swallow broker
  failure và Order assignment replay cùng shipper là no-op. Full Delivery 21/21,
  Match 17/17, Order 33/33, Notification 15/15, Saga 14/14.
- Gate cấu trúc mới nhất ngày 2026-07-23: build-baseline, Compose contract và
  `git diff --check` đều PASS; reactor package JDK 17 PASS đủ 17/17 service.
- Boundary proof mới nhất: Gateway 17/17, Tracking 8/8, Match 17/17,
  Notification 17/17, Order 33/33 và Settlement 11/11. Order legacy dashboard và
  settlement payment controller đều không tạo bean mặc định.
- Identity/fleet proof mới nhất trên clean JDK 17: Auth 19/19, User 15/15,
  Shipper 8/8. Admin/session/rating compatibility lists cap repository query ở
  100; shipper rating average dùng SQL aggregate; legacy
  shipper PostgreSQL-location controller không tạo bean mặc định.
- Restaurant query-safety proof: compatibility catalog/menu/rating lists cap
  100; rating count/average dùng SQL aggregate và full suite đạt 74/74 trên JDK 17.
- Notification query-safety proof: owned/unread compatibility lists cap 100,
  unread count vẫn là SQL aggregate chính xác; full suite đạt 18/18 trên JDK 17.
- Promotion/Flash Sale query-safety proof: voucher, campaign và campaign-item
  compatibility lists cap repository query ở 100. Context tests chạy độc lập với
  H2/subclass mock-maker; Promotion đạt 6/6 và Flash Sale đạt 7/7 trên JDK 17.
- Auth session list chỉ trả active + unexpired session và cap 100; Delivery
  shipper history/active compatibility lists cap 100. Full suites đạt Auth 19/19
  và Delivery 22/22 trên JDK 17.
- Auth/resource-server runtime matrix xác nhận access/refresh TTL 900/604800 giây;
  missing/malformed/tampered JWT 401, injected identity headers bị strip, USER
  không leo ADMIN và cross-user address bị 403. Refresh token dùng per-device
  family + hashed history; rotation row-lock current fingerprint, reuse commit
  revoke toàn family, logout/device revoke không ảnh hưởng session device khác.
  Access JWT cũ vẫn hợp lệ tới TTL 15 phút vì access token stateless và resource
  service không introspect session/version ở mỗi request. Product authority ngày
  2026-07-26 chấp nhận
  bounded window này cho MVP: logout/admin block revoke refresh/session ngay,
  access token đã phát còn hiệu lực tối đa 15 phút; immediate revocation không
  thuộc contract MVP.
- Static checkpoint mới nhất sau timeout/rematch/Auth runtime đạt full clean
  reactor 17/17, 580 test/174 suite, 0 failure/error/skip; package 17/17, build
  baseline, HTTP inventory 161/161, Compose contract và diff hygiene đều PASS
  trên JDK 17 ngày 2026-07-26. Gate B8 và backend freeze vẫn OPEN.
- Order checkout-preview boundary validates restaurant, Vietnam coordinates,
  bounded nested items và quantity trước downstream calls. Preview now uses the
  same internal `POST /api/restaurants/validate/order` + `Internal-Token`
  contract as create-order, and fails closed when canonical pickup coordinates
  or menu facts are missing instead of deriving an approximate total from public
  catalog reads. Cancellation metadata được mapper đánh dấu server-owned
  explicit ignore; Order đạt 85/85 trên JDK 17.
- Mutation validation proof: Restaurant/menu create bounds và Flash Sale item
  bounds chạy trước persistence; Order/Restaurant bean-validation errors trả 400,
  không rơi vào catch-all 500. Restaurant 77/77, Order 36/36, Flash Sale 8/8.
- Delivery command boundary dùng DTO riêng cho accept/reject và cancel-assignment;
  positive order, canonical action, bounded text/pickup/Vietnam coordinates và
  validation HTTP 400 có proof. Delivery đạt 25/25 trên JDK 17.
- Delivery status mutation lấy pessimistic row lock trước permission/state-machine
  check và transactional outbox write; focused ordering proof xanh, full Delivery
  đạt 26/26. PostgreSQL concurrent rehearsal vẫn OPEN.
- Order customer/admin and event-driven status mutations use pessimistic row locks
  before state transition; cancel lock→save→outbox ordering has focused proof.
  Payment/restaurant listeners chỉ ACK sau mutation thành công và rethrow lỗi để
  Kafka retry thay vì silent-drop/treo record ở manual-ack mode. Order full suite
  đạt 43/43; PostgreSQL/Kafka race rehearsal remains OPEN.
- Promotion collect enforces start/end active window and flushes the unique
  user-voucher insert inside service scope; concurrent duplicate maps to HTTP 409
  rather than commit-time 500. Promotion reaches 12/12; PostgreSQL race proof OPEN.
- Flash Sale merchant registration is absent from Gateway and its controller bean
  is off by explicit default/Compose flag while menu/original-price canonical proof
  is missing. Public reads/admin campaigns remain. Gateway 19/19, Flash Sale 10/10.
- Promotion wallet cap 100 và calculate batch-load thay vì N+1; calculate vẫn
  hidden khỏi Gateway/checkout flag off nhưng controller cũng bắt role `USER`
  trước khi override body `userId` nếu bật sau này. Merchant create bị Gateway
  ẩn do ownerId không phải restaurantId. Promotion đạt 7/7, Gateway 17/17; User
  address list cap 100 và User đạt 15/15 trên JDK 17.
- Gateway không còn auth catch-all hoặc orphan `/api/orchestrator/**`; auth,
  Firebase và Promotion routes đều được method-scope theo controller thật.
- `scripts/verify-runtime-startup.sh` là startup proof có thể lặp lại cho 4 infra,
  control plane, observability, 13 app COD MVP và Gateway smoke; đặt
  `RUNTIME_INCLUDE_DISABLED_CAPABILITIES=true` mới include đủ bốn capability
  disabled. Checkpoint lịch sử 2026-07-28/29 từng PASS đủ 17 application; default
  hiện tại không khởi động capability off để không biến resource pressure local
  thành lỗi MVP. Đây là runtime proof, không còn chỉ là fail-closed.
- Notification repository không còn dead unbounded list methods; Auth account
  blocking chỉ load active sessions nhưng vẫn xử lý full set để revoke chính xác.
- Delivery/Match không còn legacy HTTP client tới Tracking port 8090; canonical
  location/matching path vẫn là raw WebSocket + Redis GEO/Kafka lifecycle.
- Tracking dev HTML controller sai port/thiếu auth/deliveryId đã bị xóa sau
  polyrepo search không có consumer; 8/8 behavior tests còn lại xanh.
- Flash Sale không còn dual MySQL/PostgreSQL runtime; production source và Compose
  cùng PostgreSQL, test profile dùng H2. Full suite 7/7.
- Gateway strip spoofed identity headers toàn cục trước routing và nhận CORS origin
  allow-list qua env. Resource services reject JWT thiếu/sai subject, role, kid,
  RS256, issuer, audience hoặc access token type through Auth JWKS. Full suite
  passed at the recorded checkpoint.
- Delivery accept/cancel command DTO đã tách và validate, malformed body trả 400;
  Order pagination bound page >= 0, size 1..100. Full suites Delivery 24/24 và
  Order 37/37.
- Restaurant confirm/reject dùng typed validated DTO thay Map parsing; owner check
  và transactional decision outbox giữ nguyên. Full suite 77/77.
- Notification manual-send command được validate/bounded; unexpected runtime lỗi
  trả sanitized 500 thay vì leak exception message. Full suite 19/19.
- Flash Sale internal reserve DTO ràng buộc item id, quantity và price dương;
  recovery flag được kiểm tra trước domain validation để capability mặc định đóng
  vẫn trả 503. Full focused suite đạt 10/10 trên JDK 17.
- Promotion calculate ràng buộc shop id và monetary values, yêu cầu role `USER`
  ở controller trước khi override body `userId`; internal reserve ràng buộc
  user/order/voucher ids. Credential và recovery gate chạy trước domain
  validation, nên checkout mặc định đóng vẫn fail-closed 503 cho request hợp lệ.
  Full focused suite đạt 10/10 trên JDK 17.
- Order generic update/delete/status/assign không còn được đăng ký mặc định ở
  service; chỉ compatibility flag tường minh mới tạo controller. Public cancel
  reason được bound 500 ký tự. Full suite đạt 39/39 trên JDK 17.
- Shipper rating write được tách thành compatibility controller mặc định tắt;
  self-rating read vẫn giữ. Request write bound order id, score 1..5 và comment
  500, nhưng chỉ được mở sau relationship proof. Flutter submit hiện là client
  migration item. Full suite đạt 9/9 trên JDK 17.
- Shipper production-truth closeout 2026-07-26: entity mới không còn khởi tạo
  rating giả `5.0`; Flyway V2 chỉ clear rating ở shipper không có bất kỳ rating
  row sở hữu nào và giữ nguyên aggregate có owner. Self read dùng canonical
  `BaseResponse`; aggregate thiếu sau insert fail transaction thay vì fallback
  5.0. Full shipper-service đạt 21/21 trên JDK 17, gồm clean/legacy migration.
- Restaurant pickup contract closeout 2026-07-26: create bắt buộc latitude
  8..24 và longitude 102..110 vì Order/Match dùng đây làm canonical pickup;
  web owner form giữ ô trống khi backend thiếu và không còn submit `0,0`. Full
  restaurant-service đạt 94/94 trên JDK 17; web ESLint/build PASS.
- Restaurant và menu partial updates giờ chạy Bean Validation trước service:
  optional field semantics giữ nguyên nhưng blank name, text/phone overflow,
  ngoài Vietnam coordinates và invalid price/restaurantId bị reject 400. Full
  suite đạt 79/79 trên JDK 17.
- Auth/User admin block commands kiểm role trước payload, bound reason 500; User
  bắt reason và Auth dùng typed numeric gateway identity thay parse String. Full
  suites đạt Auth 20/20 và User 16/16 trên JDK 17.
- Match nearby-shippers HTTP debug controller mặc định không tạo bean và Compose
  khóa `MATCH_DEBUG_API_ENABLED=false`; canonical Kafka/Saga matching không đổi.
  Nếu bật explicit, unexpected validation errors không còn leak message. Full
  suite đạt 17/17 trên JDK 17.
- Search restaurant/dish public endpoints giữ query/page bounds; shipper discovery
  được tách thành controller mặc định tắt ở application/Compose. Flutter call là
  contract migration item. Search clean suite đạt 6/6 trên JDK 17 với subclass
  mock-maker test isolation.
- Active Restaurant→Search `entity-sync` producer now attaches UUID + occurredAt
  and stores the event in the existing Restaurant outbox inside the same transaction
  as restaurant/menu create/update/delete. Existing relay owns broker ACK/backoff/DEAD;
  Search validates metadata before Elasticsearch mutation and retains retry/DLT.
  Search also persists a per-entity event checkpoint before document mutation:
  exact retries may reapply, but an older DLT/replay cannot overwrite or resurrect
  a newer update/delete. Restaurant 83/83 and Search 6/6 clean; runtime
  relay/recovery rehearsal remains OPEN.
- Dead Shipper→Search graph đã xóa sau zero-call-site proof: không còn HTTP
  controller, Elasticsearch document/repository/consumer branch hay publisher
  fire-and-forget phía Shipper. Admin fleet dùng Shipper service, Match dùng
  Redis GEO; Search chỉ còn Restaurant/Dish projection có outbox authority.
- Livestream lifecycle/product/token HTTP controllers đều mặc định không tạo bean
  và Compose khóa `LIVESTREAM_API_ENABLED=false`; client references là migration
  items, chưa phải lý do mở capability thiếu ownership/token proof. Context suite
  đạt 1/1 trên JDK 17.
- `delivery.completed` financial contract fail-closed ở cả hai đầu: Delivery
  không còn tự bịa shipping fee/payment method khi hoàn tất và chỉ phát exact COD
  với positive IDs/totals; Settlement từ chối non-COD, identity/fee thiếu hoặc
  `totalPlatformEarnings` lệch tổng commissions trước khi chạm ledger. Clean proof:
  Delivery 26/26 và Settlement 12/12 trên JDK 17; PostgreSQL concurrent replay
  vẫn OPEN.
- Settlement arbitrary-entity self-service balance/transaction và money mutation
  controllers mặc định tắt qua `SETTLEMENT_SELF_SERVICE_API_ENABLED=false`;
  internal COD eligibility, ledger consumer và admin reads không đổi. Client calls
  được ghi migration items. Full suite đạt 11/11 trên JDK 17.
- Settlement admin controller chính chỉ còn read-only; approve/reject/reverse ở
  compatibility controller mặc định tắt, reason bound 500 và Compose verifier
  khóa flag. Full suite giữ 11/11 trên JDK 17.
- Flash Sale retained public/admin surface dùng bounded campaign name và typed
  campaign status; public active campaign/approved item lists đều cap 100 và
  campaign không tồn tại trả 404 thay vì 500. Merchant/reserve capability vẫn
  đóng. Full suite đạt 13/13 trên JDK 17.
- Promotion merchant create được tách thành compatibility controller mặc định tắt
  và Compose verifier khóa flag; creator type là server-derived nên DTO không còn
  bắt client gửi. Explicit restaurant ownership contract vẫn OPEN. Full suite đạt
  12/12 trên JDK 17.
- Shipper self-delete được tách thành controller mặc định tắt vì chưa có active
  delivery/deactivation policy; create/profile/update/online dùng typed trusted
  identity. Compose verifier khóa delete flag. Full suite giữ 9/9 trên JDK 17.
- User path-ID delete được tách thành controller mặc định tắt trong application và
  Compose cho tới khi có lifecycle authority; canonical current-user read dùng
  typed identity. Full suite giữ 16/16 trên JDK 17.
- Dead `delivery.find-shipper` producer DTO/method/constants đã bị loại khỏi
  Delivery/Match; rematch duy nhất đi qua `delivery.shipper-rejected` → Saga →
  `saga.command.find-shipper`. Delivery 26/26 và Match 17/17 clean trên JDK 17.
- Module-local Kafka constants không có call site đã được xóa khỏi
  Order/Delivery/Match/Notification; dead Restaurant `delivery-completed` typo và
  unused Shipper topic classes cũng bị loại. Reactor compile/package 17/17 chứng
  minh không có active producer/listener phụ thuộc các symbols này.
- Match location/status Redis replica consumers chỉ ACK sau mutation và chuyển lỗi
  sang retry handler. Legacy orphan-topic pipeline đã xóa sau zero-call-site proof;
  canonical matching chỉ publish `shipper.found`/`shipper.not-found`. Clean suite
  nay đạt 24/24. Find command bắt buộc stable UUID + positive delivery/order IDs.
  Cancellation tombstone theo `(deliveryId, matchingSessionId)` là monotonic:
  delayed find cùng generation persist `CANCELLED` và không resurrect matching,
  trong khi rematch với generation mới vẫn chạy. Offer ownership dùng cặp Redis
  key `shipper → delivery`/`delivery → shipper` cộng session reverse key; reserve
  kiểm tombstone atomically và stale stop/release không xóa offer session mới.
  `stop-matching` fence Match command/result outbox ở `SERIALIZABLE` trước rồi
  mới project Redis. DB fence failure vẫn fail-closed vào Kafka retry; Redis
  failure sau fence lưu projection `PENDING` để relay PostgreSQL retry, rồi mới
  đánh dấu `PROJECTED`.
  Dead `stopMatchingProcess` wrapper không call site và từng nuốt lỗi đã xóa.
  H2/Flyway command-store, stop-before-find và stale-generation proof xanh.
  Provisioner nay khai báo rõ `saga.command.find-shipper.retry-1000/2000/4000`,
  `saga.command.find-shipper.DLT`, cùng các DLT của stop/location/status; mọi
  retry/DLT target được reconcile theo số partition của source thay vì mặc định
  một partition. Disposable metadata rehearsal PASS 2026-08-09 qua
  `scripts/verify-kafka-resilience-topics.sh`: broker tắt auto-create, DLT seed
  một partition được tăng đúng ba partition, retention/cleanup policy đúng sau
  hai lần provision. Kafka/PostgreSQL replay runtime tổng vẫn OPEN.
- Match Geo location replica nay lưu per-shipper timestamp freshness key TTL 5 phút,
  bỏ qua update cũ và loại shipper hết freshness khỏi candidate set; trước đó TTL
  chỉ là constant không được dùng nên shipper mất kết nối có thể online vô hạn.
  Location/status/offer-release Redis exception đều propagate, vì vậy listener
  không ACK một replica mutation thất bại. Status event cũng bắt buộc stable UUID
  và positive shipper/delivery IDs. Redis integration ngày 2026-07-29 chứng minh
  tombstone offline xóa shipper khỏi nearby candidates, online replay cũ không
  resurrect, và online update mới hơn khôi phục eligibility. Runtime verifier
  `scripts/verify-match-location-replay.sh` bắn event thật vào Kafka topic
  `shipper.location-updated` và xác nhận Match consumer/group áp dụng cùng thứ tự
  online → offline tombstone → older online replay → newer online vào Redis
  projection; PASS 2026-07-29.
- Tracking location producer chờ broker ACK với idempotent producer settings;
  WebSocket trả sanitized retryable error khi Kafka replica update thất bại.
  Tracking write-only status consumer đã xóa; Match là owner của BUSY/AVAILABLE
  replica và bắt stable UUID cùng positive shipper/delivery/order/timestamp.
  Tracking 17/17 clean;
  live Redis/Kafka/WebSocket reconnect rehearsal vẫn OPEN.
- Notification dùng chính row PENDING + unique dedup key làm durable delivery
  record trước external WebSocket/FCM I/O. Provider failure được Kafka retry với
  cùng notification ID; SENT replay được skip và cache status được invalidate.
  Order-created/delivery notification listeners giờ bắt buộc stable UUID `eventId`, positive
  aggregate/user identities và non-blank status trước dispatch; malformed identity
  không ACK hoặc tạo notification cho sai user.
  Core private WebSocket send propagates broker failure thay vì đánh dấu SENT sai.
  Delivery notification vocabulary đã đổi từ legacy
  `STARTED/IN_PROGRESS/COMPLETED` sang đủ canonical Delivery enum; legacy/unknown
  status fail-closed thay vì gửi generic message. Clean suite đạt 27/27;
  crash-window/FCM/STOMP runtime proof vẫn OPEN.
- Settlement malformed/invalid `delivery.completed` không còn bị ACK-discard: error
  handler retry hai lần rồi chuyển record sang cùng-partition `.DLT` bằng producer
  idempotent. Durable receipt yêu cầu stable `eventId`, unique theo order và giữ
  delivery/payload fingerprint: chỉ exact replay được bỏ qua, còn event ID tái dùng
  với payload khác hoặc event mới cho order đã settle đều fail-closed. Migration
  preflight không tự hợp thức hóa ledger cũ thiếu receipt. Legacy
  `delivery.picked-up` double-COD path đã xóa theo zero-call-site proof. Settlement
  16/16 clean; PostgreSQL concurrent race/Kafka DLT rehearsal vẫn OPEN.
- Saga listener rejects non-positive identities before state mutation; only success
  ACKs, with configured retry/DLT for validation or manager failure. A repeated
  `delivery.created.result` is now idempotent only when delivery identity matches
  the stored step; a second delivery ID for the same order is rejected instead of
  ACK-discarded because Saga already advanced. Every shipper-found/not-found,
  accept/reject and delivery-status event must also match the Saga-owned delivery
  ID. A different shipper cannot overwrite an existing Saga assignment, while an
  exact same-shipper replay after progress remains idempotent. `order.created`
  additionally requires UUID metadata; an existing Saga only accepts a structurally
  equal JSON replay, not a new event/payload sharing the order ID. Tất cả 11 active
  Saga event handlers giờ bắt buộc UUID metadata trước manager mutation. Terminal
  cancellation chỉ skip exact stored replay; cancellation sau COMPLETED hoặc payload
  khác sau CANCELLED fail-closed, trong khi compensation consequence sau FAILED vẫn
  được nhận diện và bỏ qua an toàn. Saga 25/25.
- Delivery create command no longer bypasses full validation through a minimum-field
  fallback. Correlated invalid commands produce failure outbox; uncorrelated poison
  records retry/DLT. Shipper-not-found mutation now locks the delivery row before
  racing acceptance/status changes: assigned/in-flight/terminal state wins over a
  stale not-found event, while impossible PENDING/WAIT contradiction fails instead
  of being ACK-discarded. Create command identity được lưu trên delivery: cùng
  `orderId` nhưng event ID hoặc ownership/location/COD payload khác không còn được
  trả existing record như duplicate. Recoverable migration boundary: V8 adds a
  nullable column, documented backup/backfill assigns historic reconciliation UUIDs,
  then V9 preflight enforces non-null + unique. Delivery 37/37 clean; PostgreSQL race proof remains
  OPEN.
- Remaining Delivery Saga commands now also require stable metadata at the edge:
  cancel command requires UUID + positive order ID, cache-offer requires UUID +
  positive delivery/order IDs, and Order status command requires Saga outbox UUID
  before state mapping. Delivery remains 37/37; Order durable restaurant-decision
  receipt + Flyway/H2 transaction proof raises the clean suite to 57/57.
- Promotion orphan `order.events` listener was removed after producer search found
  none. Flash Sale `payment.failed` no-op listener was removed; checkout remains off.
- Match algorithm explainability vertical slice is now explicit: the active
  `nearest-cod-v1` path writes a versioned, read-only `matching.decision-trace`
  outbox row after its durable `shipper.found`/`shipper.not-found` result. The
  simulator observer uses a dedicated group and renders stage/total latency,
  candidate rank/COD decisions and durable-candidate resume notes. The trace is
  deliberately source-only (no retry/DLT business topology) and cannot gate
  reservation, assignment or Saga convergence. Shadow algorithms and
  baseline-vs-candidate comparison remain follow-up work.
- Container startup proof vẫn bị tách riêng: package/test xanh không chứng minh
  datasource, Kafka, Redis và WebSocket có thể khởi động cùng nhau.
- Runtime COD rehearsal hiện có executable harness tại
  `scripts/verify-mvp-cod-flow.sh`: seed fail-fast theo contract thật, đi qua
  restaurant-confirm → one-shipper offer → accept → delivered, kiểm bốn ledger
  entries và replay payload completion. Rerun 2026-07-29 PASS sau khi seed được
  cập nhật sang one-shot operator SHIPPER provisioning và fixture cleanup:
  order/delivery `21`, bốn ledger entries và exact completion replay bất biến.
- Backend logging/error pass đã loại console stacktrace khỏi active Java code.
  Settlement error envelope dùng đúng `message`/`data` và generic 500 của
  Settlement/Shipper không lộ exception detail. Match/Redis/geocoding/Agora dùng
  structured logger. Clean proof: Settlement 14/14, Match 20/20, Restaurant
  79/79, Livestream 1/1 và Shipper 9/9 trên JDK 17.
- Toàn bộ dead gRPC Tracking gồm Java skeleton, dependency/build hook, `.proto`
  và guide cũ đã bị xóa; verifier chặn runtime artifact quay lại. Raw WebSocket
  vẫn là transport vị trí canonical theo product decision. Redis location và
  BUSY/AVAILABLE mutations không còn nuốt infrastructure error; HTTP fail và
  Kafka listener retry thay vì báo thành công giả. Tracking clean 14/14.
- Order payment-event consumer được condition/off mặc định và Compose khóa
  `ORDER_PAYMENT_EVENT_PROCESSING_ENABLED=false`; COD order không còn nhận state
  mutation từ topic payment của capability đã ẩn. Order clean 39/39.
- Main runtime properties không còn bật mặc định SQL output, Spring Security/
  WebSocket DEBUG hoặc Hibernate bind TRACE; tất cả chuyển về `false/INFO` có env
  override. `verify-build-baseline.sh` chặn literal verbose logging tái xuất hiện.
- Full JDK 17 reactor test mới nhất PASS 17/17 service với 457 test; baseline,
  Compose contract và diff hygiene cùng xanh. Đây vẫn là static/H2 proof, không
  thay thế Gate B8 trên PostgreSQL/Kafka/Redis/WebSocket thật.
- OSIV đã tắt cho đủ 13 JPA service và được baseline verifier enforcement; full
  reactor 333 test vẫn xanh, không có controller proof hiện tại phụ thuộc lazy
  loading ngoài transaction. User-service cũng bỏ Security/JJWT dependency và
  permit-all config không có tác dụng; focused User 16/16, Shipper 9/9 và reactor
  package 17/17 PASS sau cleanup.
- Database credential không còn default `123456`: direct service dùng
  `DB_PASSWORD`, Compose bắt buộc `POSTGRES_PASSWORD` và inject cùng giá trị cho
  đủ 13 JPA service. Local generator tạo secret ignored; verifier chặn blank/
  mismatch/legacy default. Existing-volume rotation vẫn là operator recovery,
  không có destructive automation.
- Local secret generator giữ keypair hợp lệ và chỉ rotate khi explicit
  `ROTATE_JWT_KEYS=true`; bổ sung `.env` không còn vô tình vô hiệu hóa JWT đang
  dùng. Runtime startup parser chỉ đọc INTERNAL/POSTGRES key, không execute `.env`.
- Settlement payment disabled state không còn khởi tạo ngầm provider/service/
  publisher graph. Fake provider + fake-confirm cần cả processing và explicit
  `FAKE_PAYMENT_PROVIDER_ENABLED=true`, đồng thời bean bị khóa vào profile
  `dev|test` nên không thể bật ở `prod`; Compose khóa false. VNPay không còn DEMO
  credential và từ chối create/verify khi credential env trống. Settlement 36/36.
- Restaurant dev-only `/api/test/protected` không có consumer/contract đã bị xóa;
  không còn test endpoint trong active source hay HTTP inventory.
- Notification schema runtime không còn phụ thuộc Hibernate `ddl-auto=update`:
  Flyway V1 là authority cho bảng `notifications`, unique dedup key và hai index
  phục vụ user/timestamp + unread/timestamp; Hibernate chỉ `validate`. Clean schema
  và legacy upgrade giữ row có executable proof; migration fail-closed khi thiếu
  core column hoặc có duplicate dedup key, thay vì tự sửa/xóa dữ liệu. Notification
  clean 32/32; PostgreSQL transactional DDL/concurrent unique và Kafka/provider
  restart vẫn thuộc Gate B8.
- Saga state schema cũng không còn mixed Flyway/Hibernate authority: Flyway V1
  giữ outbox, V2 sở hữu `saga_instances` và `saga_steps`; production Hibernate
  chỉ `validate`. V2 tạo clean schema, giữ legacy aggregate/step, thêm optimistic
  version mặc định 0, preflight duplicate `order_id`, thiếu core column và table
  pair không đầy đủ trước unique/index changes. Recovery SQL được ghi rõ chỉ là
  operator reference và đồng bộ constraint/index với Flyway. Saga clean 31/31;
  PostgreSQL DDL/row-lock/concurrent first-insert và Kafka crash-window vẫn OPEN.
- Order core schema đã chuyển khỏi Hibernate `ddl-auto=update`: Flyway V4 sở hữu
  `orders`/`order_items` bên cạnh V2 outbox và V3 restaurant-decision receipt;
  production Hibernate chỉ `validate`. Clean schema và legacy row preservation có
  executable proof; thiếu core column hoặc chỉ có một bảng aggregate fail-closed.
  V4 tạo index theo các query user/restaurant/owner/shipper/status timeline và
  order-item lookup thay vì dựa Hibernate side effect. Order clean 62/62;
  PostgreSQL DDL/row-lock và canonical status rehearsal vẫn thuộc Gate B8.
- Restaurant catalog/rating schema đã chuyển sang Flyway V2 + Hibernate
  `validate`; V1 tiếp tục sở hữu decision/outbox. V2 tạo `restaurant`, `menu_item`
  và `restaurant_ratings`, giữ nguyên legacy rows, fail-closed nếu thiếu core
  column hoặc table set không đầy đủ, và tạo index theo owner/catalog/rating query
  đang dùng. Restaurant clean 88/88; PostgreSQL DDL/advisory-lock/outbox restart
  vẫn thuộc Gate B8.
- Settlement không còn để Hibernate sở hữu base financial schema: Flyway V1 giữ
  receipt/ledger replay preflight, V2 tạo/validate `balances`, `transactions` và
  `payment_orders`; production Hibernate chỉ `validate`. Clean/legacy/fail-closed
  và entity-schema proof đạt 26/26. Payment table được giữ cho compatibility nhưng
  processing/controller/provider vẫn tắt; V2 không mở capability. PostgreSQL DDL,
  row-lock và concurrent financial replay vẫn thuộc Gate B8.
- Build baseline verifier giờ khóa Notification/Order/Restaurant/Saga/Settlement
  ở Flyway enabled + Hibernate `validate`; đổi ngược về `ddl-auto=update` sẽ fail
  structural gate thay vì âm thầm trả schema authority cho Hibernate.
- Auth account/session schema đã chuyển sang Flyway V1/V3 + Hibernate `validate`.
  Migration giữ legacy rows, fail-closed khi thiếu table/cột, duplicate email hoặc
  duplicate non-null legacy refresh token. V3 backfill mỗi session thành một
  family, hash legacy current token vào `auth_refresh_token`, xóa raw token khỏi
  session và tạo unique hash + session/state indexes. Rotation/reuse dùng
  pessimistic history-row lock; clean/legacy schema và concurrent family proof
  chạy trong Auth suite.
- Flyway authority verifier đã mở rộng để khóa cả Auth, tổng cộng sáu service.
- User profile/address schema đã chuyển sang Flyway V1 + Hibernate `validate`;
  `auth_id` unique bảo vệ Auth→User provisioning idempotency. Migration giới hạn
  JDBC metadata trong current schema để không nhầm H2/PostgreSQL system catalogs,
  giữ legacy rows và fail-closed khi duplicate auth identity, nhiều default address
  hoặc thiếu core column. Address timeline/default indexes khớp repository. User
  clean 31/31; PostgreSQL default-address race/partial unique proof vẫn thuộc B8.
- Flyway verifier khóa thêm User, tổng cộng bảy service.
- Shipper fleet schema đã chuyển sang Flyway V1 + Hibernate `validate`. Migration
  giữ legacy rows và preflight duplicate user/license/id-card, nhiều current
  location trên một shipper, hoặc nhiều rating trên một order trước khi thêm unique
  constraints; online/rating timeline indexes khớp repository. Legacy location,
  rating-write và delete APIs vẫn off. Shipper clean 16/16; PostgreSQL concurrent
  registration/location/rating proof vẫn thuộc B8.
- Flyway verifier khóa thêm Shipper, tổng cộng tám service.
- Flash Sale campaign/item schema đã chuyển sang Flyway V1 + Hibernate `validate`;
  migration chỉ tạo/validate schema và retained query indexes, không phát minh
  campaign/menu uniqueness policy chưa có authority. Checkout và merchant
  registration vẫn off ở application/Compose. Flash Sale clean 18/18;
  PostgreSQL/Redis reservation runtime proof vẫn thuộc disabled-feature gate/B8.
- Flyway verifier khóa thêm Flash Sale, tổng cộng chín service.
- Promotion voucher/wallet/group schema đã chuyển sang Flyway V1 + Hibernate
  `validate`; verifier khóa luôn YAML config của Promotion thay vì bỏ sót service
  này khỏi schema authority gate.
- Analytics raw-event/daily-projection schema đã chuyển sang Flyway V1 + Hibernate
  `validate`. Migration tạo hoặc kiểm tra đủ ba bảng, giữ nguyên legacy rows và
  fail-closed khi thiếu core column, table set không đầy đủ, trùng event dedup key
  hoặc trùng daily scope. Hai daily table dùng PostgreSQL 16
  `UNIQUE NULLS NOT DISTINCT (stat_date, restaurant_id)`, vì unique thông thường
  cho phép nhiều platform row có `restaurant_id IS NULL` trái với repository
  `Optional` contract. Clean/legacy/fail-closed/entity-schema proof đạt Analytics
  14/14; processing/controller/reconciliation vẫn off mặc định. PostgreSQL
  migration/concurrent insert và Kafka retry/DLT vẫn thuộc Gate B8.
- Flyway verifier khóa thêm Analytics, tổng cộng mười service.
- Livestream persistence đã chuyển sang Flyway V1 + Hibernate `validate` trong
  khi toàn bộ controller tiếp tục off mặc định. Migration sở hữu ba bảng stream,
  product và event; giữ legacy rows; fail-closed với thiếu/incomplete schema,
  duplicate room/channel hoặc duplicate `(livestream_id, product_id)` vốn được
  repository đọc như một row; đồng thời tạo index theo status/seller/restaurant
  timeline, pinned product và event lookup. Livestream clean 7/7; Agora/Kafka/API
  vẫn không được mở và PostgreSQL migration/concurrency proof vẫn thuộc B8.
- Flyway verifier khóa thêm Livestream, tổng cộng mười một service.
- Delivery không còn mixed schema authority: production Hibernate chuyển từ
  `update` sang `validate`, SQL init/outbox schema trùng bị xóa, và startup
  `DatabaseFixConfig` best-effort được thay bằng Flyway V10 transactional. V10
  drop legacy status check và thêm shipper/status timeline indexes theo repository.
  H2 proof từ schema tương thích V9 xác nhận V10 + Hibernate validation; Delivery
  clean 39/39. Baseline verifier giờ thực sự khóa đủ 12 Flyway service, chặn
  `schema.sql` và Java startup DDL tái xuất hiện. Chuỗi clean V1→V10, partial
  unique V5 và create-event reconciliation V9 vẫn phải rehearsal trên PostgreSQL
  thật; H2 proof không thay Gate B8.
- HTTP surface inventory không còn dựa vào số đếm ghi tay: source hiện có 194
  mapped controller method, gồm hai Order internal eligibility endpoint và một
  Restaurant internal ownership endpoint trước đây bị thiếu trong bảng. Mười ba
  handler đã chuyển sang legacy/feature-flag controller được ghi đúng ownership.
  `verify-http-api-inventory.sh` khóa row count, controller và handler mapping qua
  build baseline; thay đổi controller mà không cập nhật inventory sẽ fail gate.
- Gateway Notification routes dùng numeric ID predicate cho exact read/read-mark/
  delete handlers; future one-segment paths không còn được route ngầm. Focused
  Gateway 19/19 xác nhận positive numeric và negative future-path matrix. Compose
  application boundary vẫn chỉ publish port 8079; hidden/internal service APIs
  chỉ nằm trên Docker internal network trong base configuration.
- Kafka cancellation contract không còn dual pipeline: zero-call-site proof cho
  thấy `delivery.cancelled` chỉ được Delivery tự ghi outbox và Match chỉ giữ DTO
  không listener. Producer method/constant và DTO ở cả Delivery/Match đã xóa;
  Saga `saga.command.stop-matching` tiếp tục là canonical command. Delivery test
  mới xác nhận cancel trước assignment không tạo orphan outbox; Delivery 40/40,
  Match 20/20. `payment.completed/failed` được sửa inventory thành inactive vì
  producer và mọi consumer đều feature-gated off trong COD MVP.
- WebSocket transport MVP đã thu về một đường canonical: raw Tracking
  `/ws/shipper-locations` qua Gateway. Delivery STOMP `/ws/delivery-native` và
  Notification STOMP `/ws-native` chưa có public auth/routing contract nên đều
  hidden mặc định và bị khóa `false` trong base Compose. Delivery disabled
  handshake trả 404, origin không còn wildcard; client URL/protocol cũ được giữ
  trong Phase 8-11 migration queue. Focused Delivery 41/41.
- Static checkpoint sau WebSocket legacy lock: full backend reactor 17/17 đạt
  458 test, không failure/error/skip; build baseline, HTTP inventory, Compose,
  diff hygiene và package đều PASS trên JDK 17 ngày 2026-07-24. Raw WebSocket
  auth/reconnect/terminal-revocation runtime proof vẫn thuộc Gate B8.
- JWT startup boundary không còn bypass Spring config và không còn implicit
  classpath fallback: Auth/Gateway dùng `jwt.*.path` bridge từ
  `JWT_*_KEY_PATH`, mặc định blank để startup fail-fast khi chưa mount key.
  Loader vẫn đọc filesystem path, `file:` URI hoặc explicit `classpath:` nếu
  operator cấu hình rõ. Auth ký/verify probe để fail-fast nếu private/public
  không cùng cặp; thiếu/sai key làm startup fail. `docker-compose.secrets.yml`
  mount ba key file read-only dưới `/run/secrets`; runtime harness dùng override
  này thay vì dựa vào PEM được bake trong JAR. Auth full, Gateway full,
  build-baseline, Compose config và polyrepo contract đều PASS ngày 2026-07-28.
- Static checkpoint sau JWT external-key/preflight wave: full backend reactor
  17/17 đạt 463 test, không failure/error/skip; build baseline, HTTP inventory,
  base + secret-mount Compose contract, diff hygiene và package đều PASS trên
  JDK 17 ngày 2026-07-24. Container startup và signed-token handshake qua Gateway
  vẫn OPEN vì Docker daemon chưa chạy.
- Runtime URL audit 17 service xác nhận các `localhost` còn lại trong main config
  là direct-run fallback, CORS local hoặc disabled payment-provider code; base
  Compose đã override datasource/Kafka/Redis/Elasticsearch và mọi cross-service
  URL bằng Docker DNS. Compose verifier giờ quét tổng quát các runtime dependency
  env và fail nếu application container trỏ `localhost`/`127.0.0.1`; Auth cũng
  bridge tường minh `USER_SERVICE_URL`. Payment callback topology vẫn OPEN và
  không được mở cùng capability payment trước contract/proof riêng.
- Identity status convergence closeout: polyrepo search xác nhận web chỉ mutate
  block/unblock qua Auth; User admin status route không có client consumer nên đã
  bị bỏ khỏi Gateway và chuyển thành internal projection command có shared secret
  + ADMIN identity. User block/unblock idempotent cho retry. Auth không còn nuốt
  lỗi block và unblock giờ cũng sync User; sync failure propagate để rollback
  Auth transaction thay vì trả success khi hai DB lệch. Focused Auth 18/18,
  User 11/11, Gateway route 13/13; distributed crash-window proof vẫn OPEN.
  Runtime harness `scripts/verify-auth-user-outage-retry.sh` đã được thêm để chạy
  bằng run-scoped Auth/User DB/container riêng, mô phỏng committed pending sync
  khi User outage rồi verify scheduler recovery sau khi User quay lại; lượt hiện
  tại chưa chạy được vì Docker daemon unavailable.
- Static checkpoint sau Auth/User status convergence: full backend reactor 17/17
  đạt 469 test, không failure/error/skip; build baseline, HTTP inventory, base +
  secret-mount Compose contract, diff hygiene và package đều PASS trên JDK 17
  ngày 2026-07-24. Đây chưa phải Auth↔User distributed commit/recovery proof.
- Restaurant→Order checkout surface closeout: Gateway và Order service giờ chỉ
  cho role USER preview/create; non-customer bị chặn trước canonical validation
  hay DB/outbox side effect. Dead duplicate `RestaurantClient` và dead distance
  helper trong Order đã xóa sau zero-call-site proof. Ba Restaurant validation
  helper HTTP (`menu-item`, `calculate-total`, `operating-hours`) không có consumer
  cũng đã xóa; canonical internal surface chỉ còn atomic `/validate/order`, item
  validation giữ private. Follow-up preview path cũng dùng surface atomic này với
  shared secret và không còn public-catalog approximation. HTTP inventory giảm có
  chủ đích 194→191. Focused Order 11/11, Restaurant decision/authorization 7/7,
  Gateway 17/17; Order follow-up 85/85.
- Static checkpoint sau checkout role/dead-surface closeout: full backend reactor
  17/17 đạt 468 test hiện hữu, không failure/error/skip; HTTP inventory 191/191,
  build baseline, base + secret-mount Compose, diff hygiene và package đều PASS
  trên JDK 17 ngày 2026-07-24. Đây là authoritative total của full run hiện tại;
  không suy diễn total phải tăng đơn điệu từ checkpoint trước.
- Fulfilment dead-surface closeout: polyrepo zero-call-site proof xác nhận
  PostgreSQL shipper-location API cũ, Match debug controller và Tracking
  diagnostics/fleet/distance/busy REST không có consumer. Code/controller/DTO/
  repository methods tương ứng đã xóa; legacy Shipper table/migration được giữ để
  không tự ý drop dữ liệu. Canonical runtime chỉ còn Tracking raw WebSocket +
  Redis GEO, Kafka location/status replica sang Match, Saga one-shipper offer và
  Delivery accept/reject/cancel/rematch. HTTP inventory giảm đúng 191→180;
  focused clean proof Shipper 17/17, Match 24/24, Tracking 13/13.
- Reproducible clean-build closeout: `mvn clean test` phát hiện bytecode MapStruct
  của Delivery/Order bị IDE/JDT ghi thành problem class dù Maven compile báo
  success; checkpoint non-clean trước đó không thấy lỗi này. Ba mapper nhỏ ở
  Delivery/Order/Shipper đã chuyển thành deterministic Spring source mapper,
  Match bỏ MapStruct dependency không dùng; mapper parity tests khóa mapping/null/
  partial-update semantics và build verifier chặn generated mapper tái xuất hiện.
  Clean focused proof Delivery 43/43, Order 63/63, Shipper 17/17.
- Static checkpoint sau fulfilment + clean-build closeout: full backend reactor
  17/17 đạt 472 test hiện hữu, không failure/error/skip; package, build baseline,
  HTTP inventory 180/180, base + secret-mount Compose contract và diff hygiene đều
  PASS trên JDK 17 ngày 2026-07-25. Đây vẫn là static/H2 proof; PostgreSQL/Kafka/
  Redis/raw-WebSocket Gate B8 chưa đạt.
- Cross-service dead-artifact closeout: xóa Restaurant menu-catalog interface/DTO
  không implementation/caller cùng implementation 350+ dòng đã comment toàn bộ;
  xóa Auth DTO rỗng/sai tên, Shipper balance DTO không caller và sáu Java file
  rỗng/duplicate ở Restaurant/Livestream. Delivery cũng bỏ hoàn toàn REST tracking
  response/service/mapper và location DTO chỉ còn trong comment; canonical vị trí
  tiếp tục là Tracking raw WebSocket, không thay HTTP inventory. Build verifier
  giờ chặn Java source thiếu package declaration để zombie/empty file không quay
  lại. Focused Auth/Delivery/Restaurant/Shipper/Livestream đều xanh.
- Static checkpoint sau dead-artifact closeout: full backend reactor 17/17 vẫn đạt
  472 test hiện hữu, không failure/error/skip; package, build baseline, HTTP
  inventory 180/180, base + secret-mount Compose contract và diff hygiene đều PASS
  trên JDK 17 ngày 2026-07-25. Gate B8 runtime vẫn OPEN.
- Flash Sale false-compensation closeout: `order.cancelled` canonical không mang
  item list, nên listener cũ khi bật flag chỉ log warning và không hoàn stock.
  Listener cùng release method/Lua chỉ có caller đó đã xóa; checkout/internal
  reserve tiếp tục disabled. Compensation tương lai phải dựa reservation record,
  stable identity và outbox/replay contract, không suy luận item từ cancellation.
- Kafka documentation convergence tại checkpoint đó có 15 listener class/35 handler;
  Flash Sale false compensation đã xóa. Saga design, root product overview và
  legacy topic rows nay dùng đúng restaurant-confirm-before-match,
  `saga.command.find-shipper`, `shipper.found/not-found`, persisted offer và raw
  Tracking WebSocket. `shipper.matched`/`no.shipper.available` được phân loại
  removed thay vì inactive producer.
- Shipper-status consumer replay/dead-graph closeout: Tracking trước đây consume
  `shipper.status-change` và ghi busy key nhưng không có read path trong toàn
  polyrepo; listener/DTO/service/repository graph đó đã xóa, Match là consumer
  duy nhất cần availability cho matching. Match nay validate stable eventId cùng
  positive shipper/delivery/order/timestamp và dùng một Redis Lua operation để
  release đúng ownership hai chiều, mutate BUSY/AVAILABLE và lưu version fence;
  exact/stale replay là no-op, cùng timestamp khác event ID fail-closed. Source còn 14
  listener class/35 handler. Clean Tracking 17/17 và Match 32/32; Redis live
  reorder/restart proof vẫn OPEN ở Gate B8; focused live Redis serialization và
  cancel-vs-stale-release proof đã PASS. Full clean reactor đạt
  17/17 service, 544 test trong 164 suite, không failure/error/skip.
- Order consumer retry/DLT closeout: ba handler active (restaurant confirm/reject
  và Saga status command) đã có row lock, transition guard, stable identity và
  restaurant decision receipt nhưng container dùng `DefaultErrorHandler()` không
  có recoverer tường minh. Order nay retry hai lần rồi publish record cùng key/
  partition sang `<source-topic>.DLT`; DLT send failure fail-closed. Config proof
  capture exact topic/partition/key/value, build verifier khóa policy. Hai payment
  handler tiếp tục không tạo bean khi COD-only flag false. Clean Order 68/68;
  full clean reactor đạt 17/17 service, 546 test trong 164 suite, không failure/
  error/skip. Kafka retry/DLT và PostgreSQL crash-window rehearsal vẫn OPEN ở
  Gate B8.
- Delivery command ACK/failure-idempotency closeout: create/cancel listener trước
  đây đặt manual ACK trong broad processing catch, nên ACK lỗi sau DB/outbox commit
  có thể phát `delivery.created.failed`/`delivery.cancel.failed` giả và kích hoạt
  compensation cho operation đã thành công. ACK nay nằm ngoài business-failure
  mapping; redelivery đi qua create/cancel state guard. Correlated failure outbox
  dùng chính source command UUID, exact replay không tạo row/event mới và cùng ID
  khác metadata/payload fail-closed. Delivery Kafka config được chuẩn hóa thành
  testable finite-retry + same-partition DLT beans, DLT send failure fail-closed và
  build verifier khóa policy. Clean Delivery 67/67; PostgreSQL/Kafka crash-window
  rehearsal vẫn OPEN ở Gate B8.
- Notification dead/unsafe surface closeout: polyrepo zero-call-site proof cho
  thấy broadcast/topic push, typing/status, arbitrary
  `/notification/connect/{userId}`/disconnect message mappings, session registry,
  cache read helper và bảy shipper/system notification variants không có caller.
  Các surface này cùng constants mồ côi đã bị xóa; REST inbox/FCM token API và ba
  Kafka-derived notification path giữ nguyên. Service README được viết lại theo
  contract MVP và không còn quảng bá `/ws`, MapStruct hay listener legacy. Clean
  Notification 32/32; STOMP `/ws-native` vẫn hidden, không được suy ra là kênh
  shipper-offer đã hoàn tất.
- Notification configured-provider failure closeout: Firebase không cấu hình vẫn
  là optional no-op để PostgreSQL inbox hoạt động; khi đã cấu hình, Redis token
  lookup và non-`UNREGISTERED` provider error không còn bị catch/log rồi báo
  success. Lỗi propagate qua NotificationService, row giữ PENDING và listener
  không ACK để cùng dedup key được retry. Clean Notification 36/36; multi-token/
  multi-channel partial success vẫn là at-least-once và cần stable ID/client dedup
  cùng Redis/FCM/Kafka runtime proof ở Gate B8.
- Authenticated shipper-offer recovery boundary: Delivery expose exact self-only
  `GET /api/deliveries/offers/current` qua Gateway route `SHIPPER`, derive identity
  từ JWT claims đã được Delivery validate qua JWKS và chỉ trả offer chưa hết hạn;
  nhiều offer đồng thời fail
  closed để lộ invariant violation. Notification không còn gửi offer vào STOMP
  đang hidden, không tự suy diễn phí/earnings từ distance, mà ghi durable inbox
  rồi dùng FCM như wake-up best-effort; giá canonical chỉ nằm trong Delivery
  current-offer response. Focused
  clean proof: Gateway 21/21, Delivery 52/52, Saga 32/32, Notification 36/36. Shipper app còn
  phải fetch contract này lúc startup/foreground/push ở Phase 11; live expiry,
  reconnect, Kafka/Redis/FCM proof vẫn thuộc Gate B8.
- Static checkpoint sau offer recovery/timeout convergence: full clean reactor
  đạt 17/17 service, 503 test trong 156 suite, không failure/error/skip; package
  17/17, HTTP inventory 178/178, build baseline, Compose contract và diff hygiene
  đều PASS trên JDK 17 ngày 2026-07-25. Đây không thay thế live B8.
- Search immutable replay closeout: per-entity checkpoint now uses Elasticsearch
  scripted atomic upsert rather than a repository read/save race, retains the
  canonical SHA-256 payload fingerprint, and upgrades a legacy missing
  fingerprint only when event ID/occurredAt/action all match. Document mutation
  uses nanosecond `occurredAt` with Elasticsearch `external_gte`, so an old
  Kafka partition write delayed after a newer update/delete cannot regress or
  resurrect the projection. Testcontainers Elasticsearch proof passes four
  cases (concurrent claim + delayed old writer, exact/contradictory reuse,
  legacy-checkpoint fingerprint upgrade, and delayed pre-delete upsert); Kafka
  + Elasticsearch two-replica rehearsal
  passes reorder, same/fresh-group replay and contradiction-to-DLT. Cluster
  outage/index recreation and controlled production replay remain OPEN at Gate
  B8.
- Delivery shipper-status boundary closeout: polyrepo chỉ có `shipper_app2` gọi
  status mutation theo ba bước fulfilment; web chỉ còn endpoint constant và
  Flutter không mutate. Gateway nay chỉ nhận SHIPPER, service bắt đúng shipper đã
  assign, bỏ generic ADMIN mutation cùng shortcut assign/cancel/rematch. Exact
  same-state retry trả state hiện tại mà không ghi lại status/completion outbox;
  cancel trước pickup tiếp tục thuộc `cancel-assignment`. Clean proof Delivery
  56/56 và Gateway 21/21 ngày 2026-07-25; PostgreSQL lock/Kafka relay runtime
  proof vẫn thuộc Gate B8.
- Static checkpoint sau shipper-status boundary: full clean reactor đạt 17/17
  service, 507 test trong 156 suite, không failure/error/skip; reactor package
  17/17, HTTP inventory 178/178, build baseline, Compose contract và diff hygiene
  đều PASS trên JDK 17 ngày 2026-07-25. Đây vẫn là static/H2 proof và không thay
  thế PostgreSQL/Kafka/Redis/raw-WebSocket/COD Gate B8.
- Delivery read/offer route convergence: Gateway trước đây gộp self current-offer
  với shipper history/active dưới role set `SHIPPER,ADMIN`, trái service contract
  chỉ SHIPPER cho offer. Current-offer nay có route SHIPPER-only riêng; history và
  active giữ SHIPPER-self/ADMIN support. Service list trước đây chỉ so numeric ID,
  nên direct USER/SHOP_OWNER có ID trùng có thể qua; nay bắt cả canonical role và
  path identity. Owned delivery/order reads tiếp tục kiểm creator, assigned
  shipper, restaurant owner hoặc ADMIN. Focused clean proof: Delivery 58/58,
  Gateway 21/21 ngày 2026-07-25. Shipper app query `page/limit/status` trên history
  vẫn là Phase 11 mismatch vì backend contract hiện cap 100, không nhận filter.
- Legacy Delivery manual-assignment removal: polyrepo zero-call-site search xác
  nhận `POST /api/deliveries/assign` không có client/internal consumer và Gateway
  đã deny. Hidden implementation còn nguy hiểm hơn classification cũ: role check
  bị comment, DTO không validation, tạo thẳng `ASSIGNED` ngoài restaurant-confirm,
  Saga, one-offer lock và transactional outbox. Controller, DTO, service/mapper
  branch, repository query và feature flag/Compose env đã xóa; Gateway regression
  tiếp tục khóa POST này không route. HTTP inventory giảm 178 -> 177; focused
  clean proof Delivery 58/58 và Gateway 21/21 ngày 2026-07-25.
- Delivery dead Redis dependency removal: source/module search xác nhận không có
  Redis caller ngoài `RedisConfig`, nhưng service vẫn kéo Redis starter, ép
  Compose dependency và best-effort sửa global `notify-keyspace-events` lúc
  startup. Canonical offer timeout/rematch đã thuộc Saga exact-generation command,
  không dùng TTL listener trong Delivery. Đã xóa config/starter/properties/test
  toggles và Compose Redis host/dependency; verifier khóa Delivery không tái nhận
  Redis env/dependency. Legacy STOMP chưa xóa trong wave này vì Flutter còn
  reference và cần contract-removal mapping ở client phase.
- Delivery hidden-STOMP runtime correction: feature flag trước đây chỉ từ chối
  handshake nhưng `@EnableWebSocketMessageBroker` vẫn khởi động simple broker và
  mọi mutation vẫn gửi vào topic không subscriber. WebSocket config nay chỉ tạo
  khi flag `true`; notifier mặc định no-op qua optional template, và context proof
  khóa không có config/messaging-template khi Compose flag false. Compatibility
  code vẫn chưa được coi là public contract và chưa bật trước auth/ownership +
  client migration decision. Delivery README cũ quảng bá `/ws`/topic sai đã được
  thay bằng contract bám source.
- Search availability/dead-cache closeout: xóa Redis cache graph không có external
  contract, tránh deserialize `Page` từ `Object` không type-safe và eviction bằng
  blocking `KEYS`; Compose/verifier xác nhận Search không còn Redis env hay
  dependency. Restaurant/dish query không còn trả `200 []` khi Elasticsearch tắt
  hoặc lỗi mà trả sanitized 503. Consumer fail-closed nếu repository vắng để
  Kafka retry/DLT thay vì ACK mất projection; shipper event bị chặn phía consumer
  trước checkpoint/document khi capability hidden. Clean Search 15/15 trên JDK
  17; full clean reactor đạt 17/17 service, 543 test trong 165 suite, không
  failure/error/skip. Elasticsearch relay recovery và multi-instance checkpoint
  CAS vẫn OPEN ở Gate B8.
- Restaurant decision/outbox runtime closeout: destination topic có isolated
  override nhưng canonical defaults giữ nguyên; Order custom Kafka factory tôn
  trọng configured group/listener auto-startup để eligibility peer không consume
  production. Hai Restaurant peer dùng chung PostgreSQL clone chứng minh duplicate
  confirm chỉ tạo một decision/outbox; confirm-vs-reject cho một winner và một
  HTTP 409. Wrong restaurant/non-PENDING trả 400, không ghi row; handler regression
  có unit proof. Relay/restart giữ topic cardinality confirmed=1/rejected=1 cho hai
  decision đầu. Trong broker-down window, outbox thứ ba giữ PENDING và tăng attempts
  với backoff; Kafka restart cùng named volume + relay peer mới hội tụ SENT, final
  topic offsets confirmed=2/rejected=1 và DB đúng 3 decision/3 outbox. Clean Order
  68/68, Restaurant 90/90. Peer, clone DB, topic B8 đã cleanup; production stack
  healthy. Kafka OOM `137` khi đủ stack + peer + hai CLI JVM song song vẫn là
  memory-sizing risk; Order consumer business crash-window được đóng ở runtime
  checkpoint Order kế tiếp.
- Notification inbox/provider runtime closeout: listener input topics có isolated
  overrides với canonical defaults giữ nguyên; constants class zero caller đã xóa.
  Hai peer/two-partition exact duplicate ban đầu lộ concurrent PENDING delivery có
  thể gọi provider hai lần. Shared delivery coordinator nay khóa notification row
  pessimistic trong transaction external-delivery + SENT update; PENDING row được
  commit trước transaction này để failure/restart còn recovery record. PostgreSQL
  simultaneous proof cho row mới đúng một insert/provider path, loser unique
  conflict retry rồi skip; PENDING row có sẵn chỉ một peer gọi provider, final một
  stable SENT row. Firebase synthetic credential + Redis DNS failure giữ row ID 9
  PENDING, finite retry tạo đúng một DLT/source lag 0; operator source replay với
  provider optional/Redis healthy chuyển cùng row 9 SENT, restart không tăng row
  hay DLT. Clean Notification 44/44; peer, credential, clone DB, group/topic B8 đã
  cleanup. Kafka recurring OOM `137` được chặn ở Compose bằng
  `KAFKA_HEAP_OPTS=-Xms256m -Xmx384m` dưới memory limit 768 MiB và verifier khóa
  giá trị; broker recreate cùng named volume hiện healthy. Multi-token partial
  provider success vẫn at-least-once và cần client dedup stable notification ID.
- Delivery status/Saga transition runtime closeout: Delivery status/completed/
  shipper-status destinations và toàn bộ 11 Saga input topics hỗ trợ isolated
  override nhưng giữ production defaults. Hai Delivery peer chung PostgreSQL clone
  chứng minh out-of-order transition fail-closed, concurrent/same-state retry
  không nhân outbox và final `DELIVERED` có đúng 3 status + 1 completed + 1
  shipper-AVAILABLE event. Relay offsets `3/1/1` giữ nguyên qua restart. Saga từ
  `SHIPPER_ASSIGNED` consume ba status thành đúng ba step và `COMPLETED`, tạo đúng
  ba update-order outbox; reset source về 0/restart là exact no-op. New-ID terminal
  replay mâu thuẫn retry hai lần rồi vào đúng một same-partition DLT, source lag 0
  và DB bất biến. Saga relay output offset 3 không đổi qua restart. Clean Delivery
  68/68, Saga 42/42; peer, clone DB, group/topic `b8.status.*` đã cleanup và
  production stack healthy. Build baseline JDK 17, HTTP inventory 161/161,
  Compose contract và diff hygiene đều PASS. Gate B8 tổng vẫn OPEN.
- Order Restaurant-decision consumer runtime closeout: Restaurant confirm/reject,
  Saga status input và Order created/cancelled output đều hỗ trợ isolated override
  với canonical default giữ nguyên; Saga listener bỏ hard-coded group để factory
  là authority. Hai Order peer ở hai group cùng consume exact confirm/reject trên
  PostgreSQL clone, nhưng chỉ tạo hai receipt, một `CONFIRMED`, một `CANCELLED` và
  đúng một cancellation outbox. Reset cả hai group về offset 0/restart chỉ exact
  no-op. New-ID confirmation cho Order đã quyết định retry hữu hạn vào đúng một
  same-partition DLT, source lag 0 và DB bất biến. Relay/restart giữ cancellation
  output offset 1; consumer restart giữ DLT offset 1. Clean Order 70/70; peer,
  clone DB, group/topic `b8.order.*` đã cleanup và production stack healthy.
  Build baseline JDK 17, HTTP inventory 161/161, Compose contract và diff hygiene
  đều PASS.
- Gate B8 harness hardening: COD flow nay bắt buộc quan sát durable shipper
  `MATCH_FOUND` notification rồi recover exact self-offer trước accept; customer
  Delivery polling fallback đã xóa. Seed thêm unrelated customer để Java 17 raw
  WebSocket probe kiểm handshake 401, participant IDOR, authorized subscription,
  location propagation và JWT-derived shipper identity. Public SHIPPER
  self-registration vẫn bị cấm; seed/failure matrix dùng auth-service one-shot
  operator runner cho fixture SHIPPER qua AuthService + User provisioning và đưa
  shipper fixture cũ offline qua API trước mỗi run. Clean runner dùng unique
  Compose project + PostgreSQL/Kafka volumes, không xóa canonical volumes và có
  recovery trap restore canonical containers sau lỗi/interruption. Bash syntax,
  Java compile `-Xlint:all` và baseline guard PASS; rerun 2026-07-29: COD harness
  PASS và failure matrix PASS. Legacy `test-order-flow.sh` giờ chỉ delegate
  canonical harness, không còn polling/hidden-settlement implementation.
- Clean Compose Gate B8 runtime ngày 2026-07-26 PASS trên dữ liệu/volume mới:
  infrastructure healthy, đủ 17 application và Gateway reads; COD journey đạt
  Notification `MATCH_FOUND` + self-offer recovery, raw WebSocket auth/participant
  enforcement/JWT-derived publisher, Delivery + Order `DELIVERED`, đúng bốn
  settlement rows và exact completed replay bất biến. Failure matrix đạt
  restaurant rejection không settlement, no-online-shipper hội tụ Order/Delivery
  `SHIPPER_NOT_FOUND`, và cancel-assignment rematch sang shipper thứ hai rồi giao
  xong; invalid auth/role/payment/voucher, out-of-order status và role mutation
  đều fail-closed.
- Runtime gate đóng các gap contract: initial Saga matching nay luôn phát Order
  `FINDING_SHIPPER`; Order Saga listener nhận JSON object qua configured Kafka
  converter; delivery-status adapter chỉ đọc identity/business fields nên không
  phụ thuộc timestamp metadata; Saga find/rematch payload là canonical allowlist,
  không deep-copy control event. Order hội tụ cross-topic confirm/matching reorder
  bằng `PENDING -> CONFIRMED -> FINDING_SHIPPER` trong một transaction và late
  restaurant confirmation ghi receipt không làm lùi state. Full backend proof
  sau fix đạt 595 test/179 suite, zero failure/error/skip; JDK 17 baseline, HTTP
  inventory 161/161, Compose contract và diff hygiene PASS.
- Runner dùng run-scoped PostgreSQL/Kafka volumes rồi xóa sau success. Canonical
  stack đã restore đủ 21 container, PostgreSQL gắn
  `backend_delivery_b8_20260725_postgres_data` qua host port `15432`, Kafka gắn
  `backend_delivery_kafka_data`. Product authority ngày 2026-07-26 đã chốt
  access-token policy MVP: revoke refresh/session ngay, access JWT đã phát còn
  valid tối đa 15 phút. API surface classification sau đó đóng các role gap ở
  Promotion wallet, Restaurant/Order self routes và User address wallet. Full
  reactor cuối đạt 602 test/179 suite, zero failure/error/skip; inventory
  161/161, JDK/build baseline, Compose contract và diff hygiene PASS. Backend
  contract MVP được freeze ngày 2026-07-26.
- Notification STOMP removal closeout ngày 2026-07-26: sau khi ba client đã bỏ
  `/ws-native` và polyrepo search chỉ còn negative contract test/tài liệu, xóa
  `@EnableWebSocketMessageBroker`, endpoint/config/service/message DTO,
  `sendWebSocket`, WebSocket starter và Compose feature flag. Notification chỉ
  còn PostgreSQL inbox + FCM optional; raw WebSocket duy nhất của MVP tiếp tục là
  Tracking location. Build-baseline và Compose verifier khóa graph này không
  quay lại.
- Delivery STOMP removal closeout ngày 2026-07-26: Flutter đã dùng bounded REST
  refresh cho delivery status và raw Tracking WebSocket cho location; không còn
  client/backend subscriber của `/ws/delivery-native`. Xóa conditional broker
  config, best-effort notifier/call-sites, WebSocket starter, properties, test và
  Compose flag. Delivery mutation vẫn chỉ ghi aggregate + Kafka/outbox; focused
  67/67 PASS và verifier khóa STOMP không quay lại.
- Compose artifact freshness boundary ngày 2026-07-27: shared Dockerfile vẫn
  tiêu thụ host-built JAR nhưng nay copy Maven metadata + module `src/` vào stage
  kiểm tra và từ chối image nếu bất kỳ input nào mới hơn artifact. Runtime stage
  chỉ nhận JAR từ stage đã kiểm tra, không mang source/Maven vào image chạy.
  `verify-docker-artifact-freshness.sh` chứng minh fixture fresh được chấp nhận và
  source đổi sau package bị từ chối với operator guidance; actual Shipper image
  build PASS. JDK 17 build baseline, HTTP inventory 161/161 và diff hygiene PASS.
- Tracking canonical convergence rerun ngày 2026-07-27: fixture shipper `75` mở
  hai raw-WebSocket publisher qua Gateway; Redis generation tăng `1→2→3`, session
  cũ nhận `PUBLISHER_SUPERSEDED` và close `1008`. Reconnect ping 35 giây giữ
  Tracking + Match online quá disconnect grace, chứng minh deadline cũ không thể
  offline generation mới. Sau final close, cả hai view còn online trong grace rồi
  Tracking reconciler phát Kafka tombstone và shipper biến mất khỏi
  `shippers:geo:locations`/`shippers:online:set` lẫn
  `match:shippers:geo`/`match:shippers:online`; log hậu kiểm không có consumer,
  DLT hoặc reconcile error. Không dùng emulator.
