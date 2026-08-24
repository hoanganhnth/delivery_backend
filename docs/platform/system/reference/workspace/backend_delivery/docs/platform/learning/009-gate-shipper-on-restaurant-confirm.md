# 009 — Gate tìm shipper theo nhà hàng confirm (Saga)

> Ngày: 2026-07-22 · Service: saga-orchestrator-service
> Liên quan: [008](./008-reject-stops-delivery-via-saga.md), [delivery-matching](../product/features/delivery-matching.md)

## Mình đã làm gì
Đổi luồng: đơn **chỉ tìm shipper SAU KHI nhà hàng confirm** (trước đây tìm ngay khi
tạo delivery). Sửa trong Saga: tách "tạo delivery" khỏi "tìm shipper", và mở cổng
tìm shipper khi nhận `restaurant.order-confirmed`.
File: `SagaManager.java`, `KafkaEventListener.java` (saga).

## Kỹ thuật quan trọng

### 1. Saga state machine để gate một bước theo điều kiện ngoài
Trước: `delivery.created.result` → tìm shipper ngay. Sau: `delivery.created.result`
→ dừng ở `DELIVERY_CREATED`; chỉ `restaurant.order-confirmed` mới đẩy sang
`FINDING_SHIPPER`. Đây là điểm mạnh của **orchestration Saga**: chèn một điều kiện
chờ vào giữa luồng chỉ bằng cách đổi state machine ở nhạc trưởng, không service
downstream nào phải biết.

### 2. Xử lý RACE bằng "cả hai thứ tự đều đúng"
Hai sự kiện độc lập: delivery tạo xong (`delivery.created.result`) và nhà hàng
confirm (`restaurant.order-confirmed`) — **không đảm bảo thứ tự**. Giải: cả hai
handler cùng kiểm tra điều kiện còn lại rồi mới trigger:
- delivery-created: nếu đã có step `RESTAURANT_CONFIRMED` → tìm shipper luôn.
- restaurant-confirmed: nếu status đã `DELIVERY_CREATED` → tìm shipper luôn; nếu chưa
  → chỉ ghi step, để delivery-created lo sau.
Trigger chỉ chạy khi **cả hai** đã xảy ra, bất kể ai đến trước. Đây là mẫu "join"
hai nhánh async — dùng step đã lưu làm cờ trạng thái bền (không giữ trong RAM).

### 3. Lưu payload để dùng lại sau
Lệnh find-shipper cần dữ liệu từ `delivery.created.result` (toạ độ pickup/giao).
Khi confirm đến sau, payload đó đã nằm trong step `DELIVERY_CREATED` (saga lưu
`eventData` mỗi step). Lấy lại từ đó thay vì bắt client gửi lại → state bền qua DB.

### 4. Fan-out một topic cho nhiều consumer
`restaurant.order-confirmed` giờ có 2 consumer: order-service (group riêng → set
`CONFIRMED_BY_RESTAURANT`) và saga (group `saga-orchestrator` → tìm shipper). Khác
group-id nên **mỗi bên đều nhận** — không giành mất message của nhau.

## Quyết định & đánh đổi
- **Không thêm enum status mới**: tái dùng `DELIVERY_CREATED` làm trạng thái "chờ
  confirm" (đúng ngữ nghĩa: delivery đã tạo, chưa qua bước sau). Tránh đụng schema.
- **Chưa có timeout**: nếu nhà hàng không confirm, đơn kẹt ở `DELIVERY_CREATED`. Đúng
  yêu cầu gating, nhưng production cần timeout auto-cancel/nhắc. Ghi follow-up.

## Cạm bẫy / lỗi dễ mắc
- Chỉ sửa một handler → race làm đơn kẹt (confirm đến trước delivery-created mà
  delivery-created không check cờ → không bao giờ tìm shipper). Phải sửa **cả hai** đầu.
- **Thay đổi luồng ảnh hưởng test**: từ giờ smoke test phải có bước nhà hàng confirm,
  nếu không đơn sẽ đứng, không match. Đã cập nhật TESTING_READINESS.

## Cách kiểm chứng
- `mvn -o compile` saga-orchestrator-service → **BUILD SUCCESS**.
- Khi chạy: đặt đơn → delivery `FINDING_SHIPPER` nhưng match KHÔNG chạy; gọi
  `POST /api/restaurants/orders/{id}/confirm` → match bắt đầu tìm shipper.
- Reject → order.cancelled → dừng (đã làm ở [008]).

## Câu hỏi mở
- Timeout chờ confirm bao lâu? Hết hạn thì auto-cancel hay tự tìm shipper?
- Có cần trạng thái order riêng "WAITING_RESTAURANT_CONFIRM" cho client thấy không?

## Muốn đào sâu thêm
Từ khoá: "saga orchestration gating step", "join two async events idempotent",
"durable saga state as flags", "kafka consumer group fan-out".
