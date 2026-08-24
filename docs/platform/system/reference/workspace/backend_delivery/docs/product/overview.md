# Product Overview — Delivery Backend

> Cập nhật: 2026-08-24. Đối chiếu với source, migration, test và generated
> HTTP contract sau checkpoint `28c47eb`.

Nền tảng đặt & giao đồ ăn theo kiến trúc microservices. Khách đặt món từ nhà
hàng, hệ thống tự tìm shipper gần nhất, theo dõi realtime, và đối soát tài chính
sau khi giao xong.

## Service Map

| Service | Port | Vai trò |
|---|---|---|
| api-gateway | 8079 | Cửa vào duy nhất: route, rate limit theo IP và strip legacy identity headers; không verify JWT |
| auth-service | 8081 | Đăng ký/đăng nhập, issuer JWT RS256 + JWKS, refresh, multi-device session, block |
| user-service | 8082 | Hồ sơ user, địa chỉ, block/unblock có audit |
| restaurant-service | 8083 | CRUD nhà hàng + menu, validate order, cache Redis, polygon/serviceability và menu inventory (đều gated) |
| order-service | 8084 | Vòng đời đơn, quote/idempotency checkout, immutable item/money snapshot và listener event |
| delivery-service | 8085 | Single-order + additive batch offer/snapshot, canonical shipper identity, POD/exception boundary và transactional outbox; legacy STOMP hidden |
| search-service | 8088 | Elasticsearch projection từ `entity-sync`; shipper search ẩn |
| shipper-service | 8089 | Profile/fleet; legacy location/rating-write/delete ẩn |
| settlement-service | 8090 | COD ledger; payment/self-service mutation ẩn trong MVP |
| notification-service | 8091 | FCM + inbox/read state + Redis token ownership; marketing preference boundary gated |
| match-service | 8092 | Durable Saga command/result boundary plus one live offer from Redis GEO replica |
| tracking-service | 8093 | Redis GEO + raw WebSocket location; internal REST mặc định tắt |
| livestream-service | 8094 | Agora experimental; toàn bộ HTTP API ẩn trong MVP |
| saga-orchestrator-service | 8095 | Điều phối restaurant-confirm → delivery → matching |
| promotion-service | 8096 | Voucher read/collect mapping; nằm trong Compose `optional-capabilities`, checkout reservation tắt trong COD MVP |
| analytics-service | 8097 | Projection/dashboard và per-item daily sales projection experimental, processing tắt mặc định |
| flashsale-service | 8092 (container riêng) | Public/admin read mapping; nằm trong Compose `optional-capabilities`, merchant/checkout reservation tắt |
| routing-service | private | Driving/route matrix và ETA window adapter; provider-backed ETA/serviceability gated |

## Luồng đặt hàng (rút gọn)

`Order (POST /api/orders)` → Kafka `order.created` → Saga command
`saga.command.create-delivery` → `Delivery`; nhà hàng phải confirm trước khi Saga
phát `saga.command.find-shipper` → `Match` truy vấn Redis GEO replica → một
`shipper.found` offer chỉ nhắm đúng một shipper → Delivery persist offer và
`Notification` persist inbox event → shipper accept → `Delivery` chuyển
`ASSIGNED → PICKED_UP → DELIVERING → DELIVERED` → Kafka `delivery.completed`
→ `Settlement` credit shipper + nhà hàng.

Batch delivery là additive: Match phát route có pickup-before-dropoff sequence,
Delivery giữ batch snapshot/expiry và shipper app hydrate qua protected snapshot
API. POD, post-pickup exception/retry/return, menu inventory reservation,
polygon serviceability/ETA và notification marketing preference đều tồn tại ở
contract/code boundary nhưng mặc định tắt cho đến khi có runtime/provider/UI
evidence tương ứng.

Shipper nhận wake-up bằng FCM tùy chọn và luôn recover nguồn sự thật qua self-only
`GET /api/deliveries/offers/current`; canonical harness bắt buộc quan sát durable
inbox rồi recover exact offer trước khi accept. Legacy Notification STOMP đã bị
xóa sau zero-call-site proof. Native device E2E vẫn được theo dõi ở client gate.

Chi tiết: `docs/workflows/order_lifecycle_flow.md`,
`docs/workflows/delivery_matching_tracking.md`,
`docs/workflows/settlement_finance_flow.md`.

## Ràng buộc & bất biến quan trọng

- Gateway **phải** strip `X-User-Id`/`X-Role` từ client và không inject lại chúng.
  Mỗi resource service xác thực Bearer access token qua JWKS của Auth, kiểm tra
  RS256, `kid`, issuer, audience và `token_type=access`, rồi tự dựng actor/roles.
- Consumer tài chính (settlement) **phải** idempotent theo `orderId`.
- Event delivery **phải** mang đủ `restaurantId` để settlement credit đúng.
- `shipper.id` là identity canonical cho Delivery/Tracking/History/Batch; legacy
  `userId` chỉ còn ở các notification/FCM compatibility surface được ghi rõ.
- Capability gated/default-off không được xem là public MVP chỉ vì controller
  hoặc migration đã tồn tại.
- Không service nào được `findAll()` không phân trang trên bảng lớn (OOM).
- Secret luôn qua env var, không có giá trị mặc định là secret thật trong repo.

## Trạng thái & việc còn thiếu

Xem `ROADMAP_MVP_TO_PRODUCTION.md` và
[`platform-gap-closure`](../platform/plans/active/platform-gap-closure.md).
Focused/static proof đã được mở rộng cho batch, POD/exception, inventory,
serviceability, payment/refund contracts, analytics projection và preference
boundary; các mục runtime Kafka/PostgreSQL/Redis concurrency, provider thật,
staging/device E2E và các capability T8/T10/T11/T13 vẫn phải giữ trạng thái
open/gated. gRPC không thuộc location transport hiện tại.
</content>
