# 010 — Timeout chờ confirm + vá bug compensation

> Ngày: 2026-07-22 · Service: saga-orchestrator-service
> Liên quan: [009](./009-gate-shipper-on-restaurant-confirm.md)

## Mình đã làm gì
Đóng follow-up của #009: đơn kẹt ở "chờ nhà hàng confirm" nay tự huỷ sau timeout.
Đồng thời vá một **bug có sẵn** khiến mọi timeout không huỷ được delivery.
File: `SagaTimeoutScheduler.java`, `SagaManager.java`.

## Kỹ thuật quan trọng

### 1. Đọc trước khi thêm — hạ tầng có thể đã tồn tại
Định viết scheduler timeout mới, nhưng grep ra `@EnableScheduling` +
`SagaTimeoutScheduler` + `findStuckSagas(status, cutoff)` **đã có sẵn**, và còn xử
lý cả `DELIVERY_CREATED`. Sau khi bật gate confirm, `DELIVERY_CREATED` chính là
trạng thái "chờ confirm" → timeout đã tự chạy. Việc còn lại chỉ là **đặt tên đúng**
(reason "Restaurant confirmation timeout") và **ngưỡng riêng** (10 phút, dài hơn để
nhà hàng kịp confirm). Bài học: luôn khảo sát hạ tầng hiện có, đừng xây trùng.

### 2. Bug kinh điển: ghi đè state TRƯỚC khi switch trên chính nó
```java
saga.setStatus(COMPENSATING);      // ghi đè
switch (saga.getStatus()) {         // ⚠️ giờ luôn là COMPENSATING
    case FINDING_SHIPPER, ... -> huỷ delivery;  // KHÔNG BAO GIỜ chạy
    default -> chỉ báo FAILED;                   // luôn vào đây
}
```
Hậu quả: **mọi** timeout chỉ đánh dấu order FAILED, **bỏ sót** việc huỷ delivery +
dừng match → rác tài nguyên. Fix: chụp `prevStatus` TRƯỚC khi đổi, switch trên
`prevStatus`. Nguyên tắc: **đừng đọc lại biến vừa mutate để ra quyết định** — giữ
snapshot của giá trị quyết định.

### 3. Compensation phải khớp trạng thái
Thêm `DELIVERY_CREATED` vào nhánh huỷ delivery, nhưng **không** gửi stop-matching
(vì lúc chờ confirm chưa hề match). Bù trừ đúng bằng đúng những gì đã tạo ra —
không thừa (stop-matching vô nghĩa), không thiếu (bỏ sót huỷ delivery).

## Quyết định & đánh đổi
- **Ngưỡng 10 phút** cho confirm là hằng số cứng — production nên đưa ra config
  (`application.properties`) để chỉnh không cần build lại. Ghi follow-up.
- Timeout dùng `handleStepFailed` sẵn có (order → FAILED) thay vì tạo trạng thái
  "RESTAURANT_TIMEOUT" riêng — gọn, nhưng client không phân biệt được lý do fail.

## Cạm bẫy / lỗi dễ mắc
- Ngưỡng confirm ngắn hơn thực tế nhà hàng thao tác → huỷ nhầm đơn hợp lệ. Chọn 10'.
- Scheduler `findStuckSagas` lọc theo `updatedAt < cutoff`: mọi lần `save` cập nhật
  `updatedAt` (@PreUpdate) → đơn vừa đổi state không bị tính nhầm là "kẹt". Tốt.

## Cách kiểm chứng
- `mvn -o compile` saga-orchestrator → **BUILD SUCCESS**.
- Khi chạy: đặt đơn, KHÔNG confirm → sau 10' saga timeout → delivery bị huỷ + order FAILED.
- Confirm trong 10' → tìm shipper bình thường.

## Câu hỏi mở
- Đưa các ngưỡng timeout ra config thay vì hằng số cứng.
- Có nên có trạng thái order "RESTAURANT_TIMEOUT" riêng để client hiển thị đúng lý do?

## Muốn đào sâu thêm
Từ khoá: "don't mutate then read for decision", "saga timeout compensation",
"scheduled stuck-state sweeper", "compensation match the effects".
