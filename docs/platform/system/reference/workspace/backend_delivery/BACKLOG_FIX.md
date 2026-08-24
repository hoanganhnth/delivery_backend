# 🛠️ Backlog Fix & Roadmap - Delivery Backend

> Cập nhật: 2026-04-25

## 🔴 P0 — Bug & Sai Logic

- [x] **[Security]** Fix `getOrdersByRestaurantOwner`: ~~trả về toàn bộ order~~ → dùng `findByCreatorIdOrderByCreatedAtDesc(ownerId)`
- [x] **[Data]** Fix `DeliveryCompletedEvent`: Thêm `restaurantId` vào `Delivery` entity + event → settlement credit đúng nhà hàng
- [x] **[Communication]** Event field name mismatch: ✅ Đã được handle bằng fallback getters trong `DeliveryStatusUpdatedEvent`
- [x] **[Bug]** Fix NPE khi notes null: Thêm `appendNotes()` helper null-safe → `OrderEventServiceImpl.java`
- [x] **[Security]** Gateway Header Strip: Thêm `headers.remove("X-User-Id"/"X-Role")` trước khi add → `JwtAuthenticationFilter.java`
- [x] **[Security]** Internal Secret: Chuyển sang `@Value("${app.internal.secret}")` + env variable → `AuthController.java`
- [x] **[Bug]** Fix status mapping: Thêm `FINDING_SHIPPER`, `WAIT_SHIPPER_CONFIRM`, `DELIVERING`, `SHIPPER_NOT_FOUND` → `OrderEventServiceImpl.java`
- [x] **[Bug]** WebSocket broadcast: Uncomment `broadcastShipperLocation()` → `ShipperLocationService.java`
- [x] **[Data]** Role mismatch: ✅ Xác nhận role `SHOP_OWNER` consistent toàn hệ thống (dùng `RESTAURANT_OWNER` constant = `"SHOP_OWNER"`)

> ⚠️ **Lưu ý**: Cần tạo migration thêm cột `restaurant_id` vào bảng `deliveries` (delivery-service DB).

## 🟠 P1 — Cần bổ sung sớm

- [ ] **Shipper Cancellation Flow**: Shipper hủy đơn sau accept → tự động tìm shipper mới
- [ ] **Tích hợp DeliveryWaitingService**: Gắn Redis TTL auto-retry vào luồng chính `DeliveryServiceImpl`
- [ ] **Payment (COD)**: Logic thu tiền mặt khi hoàn thành đơn

## 🟡 P2 — Tối ưu kỹ thuật

- [ ] Match retry block Kafka thread 50 phút → dùng scheduler/delayed topic
- [ ] Kafka idempotency (eventId + dedup) → tránh duplicate settlement
- [ ] Dead Letter Queue cho Kafka consumers
- [ ] Order status từ `String` → `Enum`
- [ ] Gateway hardcode localhost → service discovery
- [ ] Tracking không filter busy shipper → trạng thái IDLE/ON_DELIVERY
- [ ] Restaurant controller `@Autowired` → constructor injection
- [ ] Auth service `System.out.println` → `log.info`
- [ ] `deactivateSessions` gọi `save()` trong loop → batch `saveAll()`

## 🟢 P3 — Tính năng phụ (sau này)

- [ ] Rating & Review
- [ ] Voucher / Promotion
- [ ] Live tracking trên bản đồ cho khách
- [ ] Dashboard thu nhập shipper & nhà hàng

## ⏸️ Tạm hoãn

- ~~Restaurant confirm/reject order~~
- ~~Thanh toán online (VNPay/Momo)~~
- ~~Hoàn tiền (refund)~~
- ~~Xác nhận giao hàng (OTP/chữ ký)~~
- ~~Restaurant chuẩn bị (PREPARING → READY)~~
