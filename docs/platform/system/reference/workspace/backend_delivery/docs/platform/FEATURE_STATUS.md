# ✅ Tình Trạng Triển Khai — Feature & Service

> Cập nhật current status: 2026-08-08 · **Xác minh từ code, completed plans và
> no-runtime gates**. Các sweep ngày 2026-07-21/22 bên dưới được giữ lại như
> lịch sử, không phải trạng thái hiện hành.
> Ký hiệu: ✅ đã triển khai · 🟡 có nhưng chưa hoàn thiện/chưa gắn luồng · ❌ chưa có

Liên quan: [System Overview](./product/overview.md) · [Architecture](./ARCHITECTURE.md) · [Roadmap](../../ROADMAP_MVP_TO_PRODUCTION.md)

## Current verified checkpoint — 2026-08-08

Các mảng production nền tảng sau đã có implementation và executable/static
evidence; không giao model làm lại: Actuator health/readiness, Prometheus/Grafana
metrics, correlation ID + tracing, Gateway rate limit, Resilience4j circuit
breaker, Kafka retry/DLT + deduplication, Eureka/Config Server, secret-injection
contract, CI/rollout runbook, backup/restore rehearsal, hot-query indexes,
WebSocket fan-out/backpressure, location history, password reset/email
verification, refresh-token rotation, FCM native wake-only và voucher/flash-sale
reservation. Bằng chứng chi tiết nằm trong các
[completed plans](./plans/completed/README.md), đặc biệt:

- [Actuator](./plans/completed/backend-actuator-health-readiness.md),
  [metrics](./plans/completed/production-metrics-prometheus.md) và
  [tracing](./plans/completed/distributed-order-tracing.md).
- [Resilience](../plans/completed/phase-2-resilience.md),
  [operations](./plans/completed/phase-3-operations-deployment.md) và
  [data/scale](../plans/completed/phase-4-data-scale.md).
- [Auth security](../plans/completed/task-18-password-reset-email-verification.md),
  [token rotation](./plans/completed/task-19-refresh-token-rotation.md),
  [checkout reservation boundary](./plans/completed/task-21-voucher-flashsale-checkout.md) và
  [FCM wake-up](./plans/completed/task-22-fcm-native-wakeup.md).

Proof trên không bao gồm Docker/VM, provider thật hoặc device sanity; các hạng
mục đó là operational follow-up, không được suy diễn từ unit/static tests.

---

## 1. Backend Services (17)

| Service | Port | Endpoint/feature thật (verify) | Trạng thái |
|---|---|---|---|
| **api-gateway** | 8079 | exact route/method, peer-IP rate limit, CORS, strip legacy identity headers; không verify JWT | ✅ |
| **auth** | 8081 | two-step registration identity, login/social, refresh rotation, sessions, admin block; RS256 issuer + JWKS | ✅ |
| **user** | 8082 | profile, address, registration handoff, block audit, statistics | ✅ |
| **restaurant** | 8083 | catalog/menu/rating, owner action, confirm/reject order, internal validation/cache | ✅ |
| **order** | 8084 | COD checkout preview/create/read/cancel, immutable snapshot and outbox | ✅ |
| **delivery** | 8085 | Saga assignment, durable offer/current-offer recovery, accept/cancel/status lifecycle | ✅ contract; external canonical runtime proof tracked separately |
| **shipper** | 8089 | profile, online status, self ratings and admin read | ✅ |
| **notification** | 8091 | durable inbox/dedup, FCM token ownership and wake-up; legacy STOMP removed | ✅ |
| **tracking** | 8093 | Redis GEO, JWKS-authenticated raw WebSocket, location history and Match replica event | ✅ contract; external canonical runtime proof tracked separately |
| **match** | 8092 | Saga command consumer, Redis GEO candidate reservation, retry/DLT, found/not-found events | ✅ contract; no public HTTP controller |
| **livestream** | 8094 | Agora experimental capability | 🟡 hidden from Gateway in MVP |
| **settlement** | 8090 | internal COD eligibility, idempotent receipt/ledger/balance posting; read-only admin/refund projection | ✅ COD; payment/self-service mutation hidden |
| **search** | 8088 | anonymous restaurant/dish Elasticsearch search, entity-sync consumer | ✅ |
| **flashsale** | 8092 container-private | public/admin reads and durable reservation boundary | 🟡 merchant/checkout/relay default-off |
| **promotion** | 8096 | wallet/campaign and durable voucher reservation boundary | 🟡 checkout/relay default-off |
| **analytics** | 8097 | projection/dashboard/reconciliation code; admin read route now wired behind processing flag | 🟡 rollout-gated; backfill/ownership/financial reconciliation remain open |
| **saga-orchestrator** | 8095 | durable order→delivery→matching orchestration, rematch, timeout, outbox and inbound dedup | ✅ contract; external canonical runtime proof tracked separately |

---

## 2. Frontend Apps

### 📱 delivery_app — Khách (Flutter)
Module trong `lib/features/`:

| Feature | Trạng thái |
|---|---|
| auth (+ social login), splash, home | ✅ |
| restaurants, search, cart, orders | ✅ |
| flash_sale, promotion (voucher) | 🟡 module/UI và reservation boundary có; user-facing checkout mặc định tắt |
| notification, location (Mapbox), user_address | ✅ |
| livestream (xem stream) | ✅; concurrent viewer/chat extras deferred |
| support (chat CSKH) | 🟡 Firebase Auth/custom-token/Firestore rules chưa có proof; không thuộc MVP |
| profile, settings, admin | ✅ |
| iap (in-app purchase) | 🟡 có module, cần kiểm tra tích hợp |

### 💻 delivery_web — Admin & Nhà hàng (React + Vite + Firebase)
Trang trong `src/pages/`:

| Nhóm | Trang | Trạng thái |
|---|---|---|
| Admin | Dashboard, Orders, Shippers, ShipperTracking (realtime map), Ratings, Withdrawals, Coupons, FlashSale | ✅; Chat/Firebase graph removed from MVP |
| Nhà hàng | Dashboard, Orders, MenuManagement, Profile, Reviews, FlashSale, CouponManagement | ✅; Chat/Firebase graph removed from MVP |
| Chung | Login/Register V2, Livestream (quản lý + viewer), Settlement | ✅; concurrent livestream chat/viewer extras deferred |

### 🛵 shipper_app2 — Shipper (React Native, Clean Architecture)
Screen trong `src/presentation/screens/`:

| Feature | Trạng thái |
|---|---|
| auth (Login/Register + social), splash | ✅ |
| map (MainMap realtime), delivery, order (detail/history) | ✅ |
| earnings (thu nhập), notification, rating, documents, setting | ✅ |

---

## 3. Ma trận feature xuyên hệ thống (end-to-end)

| Feature nghiệp vụ | Backend | App khách | Web | App shipper |
|---|---|---|---|---|
| Đăng nhập + social login | ✅ | ✅ | ✅ | ✅ |
| Đặt hàng + checkout | ✅ | ✅ | — | — |
| Flash sale | ✅ reservation boundary; checkout hidden/default-off | 🟡 capability mặc định tắt | ✅ admin+shop; checkout hidden/default-off | — |
| Voucher / promotion | ✅ reservation boundary; checkout hidden/default-off | 🟡 capability mặc định tắt | ✅ admin+shop; checkout hidden/default-off | — |
| Match & nhận đơn shipper | ✅ | — | — | ✅ |
| Tracking realtime | ✅ | ✅ | ✅ (admin) | ✅ |
| Thanh toán COD | ✅ | ✅ | — | ✅ |
| Thanh toán online (VNPay) | backend graph tồn tại nhưng hidden/default-off | chưa mở UI | — | — |
| Rating & review | ✅ | ✅ | ✅ (xem) | ✅ |
| Livestream bán hàng | ✅ | ✅ (xem) | ✅ (quản lý) | — |
| Notification (FCM + WS) | ✅ | ✅ | ✅ | ✅ |
| Đối soát & rút tiền | ✅ | — | ✅ | ✅ (earnings) |
| Chat CSKH | 🟡 Firebase graph chưa có Auth/rules proof | 🟡 chưa thuộc MVP | 🟡 graph đã hide khỏi MVP | — |
| Search (Elasticsearch) | ✅ | ✅ | — | — |
| Dashboard/analytics | ✅ | — | ✅ | — |

---

## 4. Đính chính so với `SYSTEM_REVIEW.md` (25/04 — đã lỗi thời)

Nhiều thứ review cũ ghi "thiếu / tạm hoãn" **thực ra đã triển khai**:

- ✅ **Social login** (auth `/social-login`, cả 3 client).
- 🟡 **Thanh toán online VNPay** có provider/controller contract, nhưng callback,
  credential, reconciliation và production provider chưa được phê duyệt để mở.
- ✅ **Rating & review** cho cả nhà hàng và shipper.
- ✅ **Operating hours** nhà hàng, **order validation**, **menu availability**.
- ✅ **COD** đầy đủ (eligibility, hold/release, deposit) + idempotency settlement.
- ✅ **Search, flashsale, promotion, analytics, livestream** — đủ backend + UI.

---

## 5. Remaining backlog — 2026-08-01

- 🟡 **Automatic refund:** policy MVP bảo thủ đã được duyệt và boundary đã triển
  khai: settlement refund-case schema, immutable monetary snapshot, typed
  cancellation source/reason, cancellation idempotency, dedicated
  `order.refund-eligible` no-shipper trigger, reservation compensation và
  default-off refund outbox/listener. COD pre-pickup được đánh dấu không cần
  refund, còn online/provider và sau-pickup cases fail-closed vào manual review.
  Admin refund queue và customer-owned status API/UI đã có read-only projection:
  Flutter có thẻ theo đơn và lịch sử refund riêng. Provider execution, admin
  mutation và ledger reverse vẫn chưa mở.
- 🟡 **Online payment:** VNPay/provider code giữ hidden/default-off; cần provider,
  callback origin, credential store, reconciliation và rollback authority.
- 🟡 **Shipper availability:** `IDLE/ON_DELIVERY/OFFLINE` và rule filter khi match
  chưa phải MVP contract; chỉ làm sau khi product chốt state machine.
- 🟡 **Analytics per-item:** `topMenuItems` placeholder đã bỏ vì chưa có
  order-item source-of-truth; cần pipeline riêng nếu product yêu cầu.
- 🟡 **Livestream/notification extras:** concurrent viewer/chat và notification
  preferences chưa thuộc MVP contract.
- 🟡 **External proof:** cloud secret manager, PITR/KMS backup, provider smoke và
  device sanity cần môi trường/operator thật; không dùng làm local acceptance.

Các mục delivery/tracking/match/Saga còn thiếu chỉ là external runtime rehearsal
ở trên; no-runtime contract/static gates đã pass theo completed plans.

---

## 6. Historical sweep — 2026-07-22

Phần này giữ lại để truy vết quyết định và bug đã phát hiện. Không dùng các dòng
“còn thiếu” bên dưới làm current status; xem mục 5 và current checkpoint ở trên.

Sweep lần 2 sau khi đã fix nhiều thứ. Xác nhận **các chức năng cần thiết cơ bản đã code đủ**;
gap còn lại chủ yếu là hoàn thiện, không phải thiếu lõi.

### ✅ Chức năng cần thiết — ĐÃ CÓ (verify)
Auth + social login · Đặt hàng (cart/checkout preview/tính phí ship) · Reserve
flashsale + voucher khi đặt · Matching shipper + retry + rematch (Saga) · Shipper
accept/reject + **huỷ sau accept** (mới) · Tracking realtime (WebSocket) · Trạng thái
giao đầy đủ tới DELIVERED · Settlement (COD + earnings + hoa hồng 20% + withdrawal +
idempotency) · Thanh toán online VNPay (settlement, FE khởi tạo) · Rating nhà hàng +
shipper · Notification (FCM + WebSocket) · Search (Elasticsearch) · Livestream · Analytics dashboard.

### ❌/🟡 Còn THIẾU hoặc chưa khép kín (verify lần 2)
| Mức | Chức năng | Chi tiết |
|---|---|---|
| ✅ Đã làm (2026-07-22) | **Nhà hàng xác nhận/từ chối đơn** | Thêm endpoint `POST /api/restaurants/orders/{id}/confirm\|reject` + publisher; sửa Kafka converter order-service để listener POJO chạy được. **Reject nay dừng luôn delivery** (publish `order.cancelled` → Saga → cancel-delivery). **Confirm nay GATE việc tìm shipper** (Saga chỉ tìm shipper sau khi nhà hàng confirm; xử lý cả race). **Có timeout 10' chờ confirm** (không confirm → auto huỷ delivery + order FAILED); tiện thể vá bug compensation cũ (timeout không huỷ delivery). Xem learning [006](./learning/006-restaurant-confirm-reject-order.md), [008](./learning/008-reject-stops-delivery-via-saga.md), [009](./learning/009-gate-shipper-on-restaurant-confirm.md), [010](./learning/010-confirm-timeout-and-compensation-bug.md). |
| ✅ Đã làm (2026-07-22) | **user-service phân quyền** | Thực tế `/admin/*` đã có check ADMIN sẵn; lỗ hổng thật là `PUT`/`DELETE /api/users/{id}` **không check** (IDOR) → đã vá thành owner-or-ADMIN. ⏳ Follow-up: `GET /by-auth/{authId}` + `POST /users` (createUser) nên bảo vệ bằng Internal-Token. Xem [learning/007](./learning/007-user-authorization-idor.md). |
| 🟡 Một phần | **Refund tự động khi huỷ đơn** | Building block có sẵn (Transaction reasons `REFUND_*`, `COD_REFUND` + admin `reverse`), nhưng **không có trigger tự động** khi order/COD huỷ sau pickup — hiện phải admin thao tác tay. |
| ✅ Đã làm (2026-07-22) | **Shipper field `name`** | Thêm `fullName` vào shipper (entity/request/response) + dùng trong search (fallback "Shipper #id" nếu trống). Xem [learning/011](./learning/011-shipper-fullname.md). |
| 🟠 KHÔNG phải minor | **Analytics `topMenuItems`** | Analytics chỉ có stats tổng hợp (DailyOrderStats/RevenueStats), **không có dữ liệu theo món**. `topMenuItems` placeholder đã bị bỏ; nếu cần per-item thì phải dựng pipeline mới (ingest order-item events + entity + aggregate). |
| ✅/🟡 (2026-07-22) | **Livestream view count** | Đã thêm **tổng lượt xem** (`viewCount`, tăng mỗi join, hiển thị ở `LivestreamResponse`). Số viewer **đồng thời** (concurrent) vẫn chưa — cần heartbeat/Agora RTM. Xem [learning/012](./learning/012-livestream-view-count.md). |
| 🟡 Minor | **Restaurant featured items** dùng mock data ở path production. |
| 🟡 Minor | **Checkout preview** áp coupon còn placeholder (đặt hàng thật thì `reserveVouchers` chạy đúng). |

### Kết luận
Không thiếu chức năng lõi để vận hành MVP. **Đáng làm nhất**: (1) luồng nhà hàng
xác nhận/từ chối đơn — hiện là lỗ hổng nghiệp vụ thật; (2) check quyền ADMIN ở
user-service — lỗ hổng bảo mật; (3) trigger refund tự động. Phần production-grade
(observability/resilience) vẫn như Roadmap §2.

> Chi tiết việc cần làm và thứ tự ưu tiên: [ROADMAP_MVP_TO_PRODUCTION.md](../../ROADMAP_MVP_TO_PRODUCTION.md).
</content>
