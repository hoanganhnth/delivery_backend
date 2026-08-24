# System Overview — Delivery Platform

> Cập nhật: 2026-08-24. Đây là product boundary cấp platform; service-level
> details nằm trong `backend_delivery/docs/product/overview.md` và generated
> contract inventories.

Nền tảng đặt & giao đồ ăn end-to-end: khách đặt món → hệ thống match shipper gần
nhất → giao & tracking realtime → đối soát tài chính. Kiến trúc polyrepo, 4
project độc lập chia sẻ chung backend qua API Gateway.

## Các thành phần (polyrepo)

### backend_delivery/ — Spring Boot microservices
Toàn bộ nghiệp vụ server. 18 controller-owning service surfaces qua API Gateway
(`:8079`): auth, user, restaurant, order, delivery, shipper, notification,
tracking, match, livestream, settlement, search, flashsale, promotion, analytics,
routing, saga. Giao tiếp async qua
Kafka, realtime raw WebSocket, cache/GEO Redis. Auth phát JWT RS256/JWKS; Gateway
chỉ route/rate-limit/strip legacy identity header và mỗi resource service tự
xác thực Bearer token. gRPC không thuộc MVP.
→ Xem sơ đồ Mermaid editable: [Architecture](../ARCHITECTURE.md). Chi tiết backend:
[backend product overview](../../product/overview.md). Bộ
reconstruction đầy đủ nằm ở [docs/system/README](../system/README.md), với quy
chuẩn sơ đồ tại [diagram standards](../system/diagram-standards.md).

### delivery_app/ — App khách (Flutter)
Đặt món, giỏ hàng, checkout COD, theo dõi shipper realtime, lịch sử đơn và
Mapbox cho bản đồ. Module/ví voucher và flash-sale reservation boundary vẫn có,
nhưng user-facing checkout đang hidden/default-off; chat CSKH chưa thuộc MVP vì
Firebase Auth/custom-token/Firestore rules chưa có proof.

### delivery_web/ — Web admin & nhà hàng (React + Vite + Firebase)
Admin: quản trị nhà hàng/menu/user/shipper/đơn/rating/withdrawal và dashboard.
Web dùng canonical Gateway action contracts; không dùng STOMP/SockJS hoặc direct
service ports. Chat/Firebase graph đã được hide khỏi MVP. Nhà hàng: dashboard
doanh thu, flash sale, coupon, quản lý menu.

### shipper_app2/ — App shipper (React Native)
Nhận/từ chối single-order hoặc additive batch offer, hydrate route snapshot,
cập nhật trạng thái giao/POD/exception theo capability flag, vị trí realtime,
thu nhập, social login (Google/Apple). `shipper.id` là identity canonical cho
fulfilment; FCM chỉ là wake-up best effort.

## Current capability boundary

- Active COD path: catalog → quote/create order → restaurant decision → Saga/
  Match → single/batch Delivery offer → shipper lifecycle → raw WebSocket
  tracking → idempotent settlement ledger.
- Default-off/gated boundaries: batch canary, POD/return exception, polygon
  serviceability/ETA provider, menu inventory reservation, promotion/flash-sale
  checkout reservation, analytics processing/backfill, marketing dispatch,
  provider payment/payout and support chat/Agora extras.
- Public handbook: `delivery_web` exposes `/system-overview` as an unauthenticated
  read-only Flow Explorer plus a generated Markdown document portal. It never
  executes business APIs and never presents internal/dev-only routes as public.

## Luồng xuyên hệ thống: đặt hàng

```
delivery_app (khách đặt)
   → Gateway → order-service (POST /api/orders)
   → Kafka order.created → Saga → create Delivery
   → nhà hàng confirm → Saga command saga.command.find-shipper
   → match-service đọc Redis GEO replica → shipper.found
   → Saga cache offer → Delivery persist offer/expiry
   → delivery.shipper-offered → notification durable inbox + FCM wake-up
   → shipper_app2 recover qua authenticated GET /api/deliveries/offers/current
   → shipper accept → delivery: ASSIGNED→PICKED_UP→DELIVERING→DELIVERED
   → raw /ws/shipper-locations cho location realtime qua Gateway
   → Kafka delivery.completed → settlement COD ledger
```

Saga là authority cho ordering và compensation: `order.cancelled` hoặc
`restaurant.order-confirmed` đến trước aggregate được stage bền rồi replay sau
`order.created`; timeout dùng fence status/version/deadline; matching giữ một
`matchingDeadlineAt` tuyệt đối qua mọi rematch. Khi Delivery đã tồn tại,
cancellation chỉ kết thúc sau `delivery.status-updated(CANCELLED)`; refusal được
ghi thành `delivery.cancel.failed` để reconciliation, không ACK như thành công.
Nhánh `SHIPPER_NOT_FOUND` vẫn tách khỏi `CANCELLED` ở Order/Delivery/client.

Luồng này chạm cả 4 project — thay đổi contract (Kafka event field, API shape)
phải đồng bộ ở nhiều nơi, nên dùng plan cấp hệ thống trong `docs/plans/active/`.

## Điểm đồng bộ chéo cần lưu ý

- **Kafka event contract**: đổi field event ở backend phải cập nhật consumer
  tương ứng; các app đọc qua REST/WebSocket nên ít ảnh hưởng, nhưng payload
  WebSocket (delivery status, shipper location) thì cả app + web phụ thuộc.
- **Auth**: cả 3 client gửi Bearer token qua Gateway, nhưng Auth phát JWT/JWKS
  và từng resource service tự kiểm RS256, kid, issuer, audience, token type, role
  và ownership. Public password registration là hai request: Auth identity trước,
  rồi User profile với provisioning token opaque.
- **Role naming**: backend canonical dùng `SHOP_OWNER`, `USER`, `SHIPPER`,
  `ADMIN` giữa backend và các client.
- **Thông báo**: durable inbox và FCM wake-up giữ nguyên contract. Preference
  được owner bởi `principalId` canonical, transactional notification không có
  opt-out và marketing mặc định opt-out; API preference vẫn default-off, chưa
  có marketing dispatch enforcement hoặc client rollout.

## Trạng thái

Các completed plans có bằng chứng Gate B8/COD failure-matrix/replay và no-runtime
contract gates; không suy diễn từ đó thành production proof cho PostgreSQL/Kafka
concurrency, Redis multi-instance/WebSocket reconnect, provider hoặc device.
MVP chấp nhận access JWT đã phát còn hiệu lực tối đa 15 phút sau khi
refresh/session bị revoke. Backend contract MVP đã freeze sau API surface
classification và full no-runtime reactor tests. Promotion/flash checkout,
online payment và các capability analytics/livestream nâng cao tiếp tục
hidden/disabled. Ba client đã được align theo Gateway/canonical backend contract
và verified không cần emulator theo
`docs/plans/completed/mvp-client-alignment.md`.
