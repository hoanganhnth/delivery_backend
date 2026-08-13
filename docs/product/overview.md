# Product Overview — Delivery Backend

> Cập nhật: 2026-08-08

Nền tảng đặt & giao đồ ăn theo kiến trúc microservices. Khách đặt món từ nhà
hàng, hệ thống tự tìm shipper gần nhất, theo dõi realtime, và đối soát tài chính
sau khi giao xong.

## Service Map

| Service | Port | Vai trò |
|---|---|---|
| api-gateway | 8079 | Cửa vào duy nhất: route, rate limit theo IP và strip legacy identity headers; không verify JWT |
| auth-service | 8081 | Đăng ký/đăng nhập, issuer JWT RS256 + JWKS, refresh, multi-device session, block |
| user-service | 8082 | Hồ sơ user, địa chỉ, block/unblock có audit |
| restaurant-service | 8083 | CRUD nhà hàng + menu, validate order, cache Redis |
| order-service | 8084 | Vòng đời đơn, tính phí ship (Haversine), listener event |
| delivery-service | 8085 | Trạng thái giao (9 state), offer/assignment và transactional outbox; legacy STOMP hidden |
| search-service | 8088 | Elasticsearch projection từ `entity-sync`; shipper search ẩn |
| shipper-service | 8089 | Profile/fleet; legacy location/rating-write/delete ẩn |
| settlement-service | 8090 | COD ledger; payment/self-service mutation ẩn trong MVP |
| notification-service | 8091 | FCM + inbox/read state + Redis token ownership |
| match-service | 8092 | Durable Saga command/result boundary plus one live offer from Redis GEO replica |
| tracking-service | 8093 | Redis GEO + raw WebSocket location; internal REST mặc định tắt |
| livestream-service | 8094 | Agora experimental; toàn bộ HTTP API ẩn trong MVP |
| saga-orchestrator-service | 8095 | Điều phối restaurant-confirm → delivery → matching |
| promotion-service | 8096 | Voucher read/collect mapping; nằm trong Compose `optional-capabilities`, checkout reservation tắt trong COD MVP |
| analytics-service | 8097 | Projection/dashboard experimental, processing tắt mặc định |
| flashsale-service | 8092 (container riêng) | Public/admin read mapping; nằm trong Compose `optional-capabilities`, merchant/checkout reservation tắt |

## Luồng đặt hàng (rút gọn)

`Order (POST /api/orders)` → Kafka `order.created` → Saga command
`saga.command.create-delivery` → `Delivery`; nhà hàng phải confirm trước khi Saga
phát `saga.command.find-shipper` → `Match` truy vấn Redis GEO replica → một
`shipper.found` offer chỉ nhắm đúng một shipper → Delivery persist offer và
`Notification` persist inbox event → shipper accept → `Delivery` chuyển
`ASSIGNED → PICKED_UP → DELIVERING → DELIVERED` → Kafka `delivery.completed`
→ `Settlement` credit shipper + nhà hàng.

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
- Không service nào được `findAll()` không phân trang trên bảng lớn (OOM).
- Secret luôn qua env var, không có giá trị mặc định là secret thật trong repo.

## Trạng thái & việc còn thiếu

Xem `ROADMAP_MVP_TO_PRODUCTION.md` và
`../../docs/plans/active/priority-roadmap.md`. Static/H2 proof đã xanh nhưng Gate
B8 vẫn mở: cần PostgreSQL/Kafka/Redis/raw-WebSocket runtime rehearsal, duplicate/
restart/crash-window proof và COD E2E trước khi freeze backend contract. gRPC
không thuộc location transport hiện tại.
</content>
