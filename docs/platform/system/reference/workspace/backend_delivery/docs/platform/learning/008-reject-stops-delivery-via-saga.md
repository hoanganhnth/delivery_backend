# 008 — Reject của nhà hàng dừng delivery (tái dùng Saga huỷ)

> Ngày: 2026-07-22 · Service: order-service (+ Saga, delivery)
> Liên quan: [006](./006-restaurant-confirm-reject-order.md), [order-lifecycle](../product/features/order-lifecycle.md)

## Mình đã làm gì
Khi nhà hàng từ chối đơn, ngoài đổi trạng thái, order-service nay **publish
`order.cancelled`** để dừng luôn việc tìm/giao shipper. File: `OrderEventServiceImpl.java`.

## Kỹ thuật quan trọng

### 1. Kích hoạt orchestration bằng EVENT, không "ra lệnh" trực tiếp
Cần dừng delivery. Delivery chỉ dừng khi nhận `saga.command.cancel-delivery` từ
**Saga**. Có 2 cách:
- ❌ order publish thẳng `saga.command.cancel-delivery` → **vượt mặt Saga**, phá vai
  trò điều phối, dễ lệch state.
- ✅ order publish `order.cancelled` (sự kiện "đã có chuyện xảy ra") → Saga nghe và
  **tự quyết** chuỗi bù trừ (cancel-delivery, stop-matching...).
Nguyên tắc: service phát **sự kiện miền của mình**, để orchestrator điều phối. Đây
chính là luồng huỷ đơn bình thường — mình chỉ tái dùng, không thêm đường mới.

### 2. Tái dùng > viết mới
`OrderEventPublisher.publishOrderCancelledEvent(order, previousStatus, cancelledBy)`
đã tồn tại cho luồng khách huỷ. Reject chỉ cần gọi lại nó → tự động có đủ Saga
orchestration + stop-matching + (tương lai) refund, mà không thêm code luồng.

### 3. Chuẩn bị state trước khi publish
Publisher build event từ entity `Order` (lấy `cancelReason`, `restaurantId`...).
Nên phải `setCancelReason(...)` **trước** khi gọi publish, nếu không event thiếu lý do.

## Quyết định & đánh đổi
- **Giữ status `REJECTED_BY_RESTAURANT`** (không đổi thành CANCELLED) để giữ ngữ nghĩa
  "nhà hàng từ chối"; event mang `currentStatus=CANCELLED` để Saga xử lý huỷ. Có thể
  downstream flip status — chấp nhận được (rejected là một dạng cancelled).
- **cancelledBy = restaurantId**: người thực hiện là chủ nhà hàng.

## Cạm bẫy / lỗi dễ mắc
- Inject thêm dependency dễ gây **vòng lặp bean**. Ở đây OrderEventPublisher chỉ phụ
  thuộc KafkaTemplate → không cycle. Luôn kiểm tra hướng phụ thuộc khi thêm @Autowired.
- Publish cancel khi đơn chưa có delivery (reject sớm) — luồng huỷ hiện có đã xử lý
  gracefully (giống khách huỷ đơn PENDING), nên an toàn.

## Cách kiểm chứng
- `mvn -o compile` order-service → **BUILD SUCCESS**.
- Khi chạy: reject đơn đang `FINDING_SHIPPER` → match ngừng tìm, delivery CANCELLED.
- Chưa integration test thật (cần cụm docker).

## Câu hỏi mở
- Confirm có nên **gate** việc tìm shipper (chỉ match sau khi nhà hàng đồng ý)? Hiện
  đơn tìm shipper ngay khi tạo — reject chỉ "chữa cháy" dừng lại. Gate sẽ đúng hơn.

## Muốn đào sâu thêm
Từ khoá: "event choreography vs orchestration", "saga compensation trigger by domain
event", "avoid bypassing orchestrator".
