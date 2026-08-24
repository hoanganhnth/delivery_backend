# 003 — Shipper huỷ đơn sau khi accept → rematch

> Ngày: 2026-07-22 · Service: delivery-service (+ tái dùng Saga orchestration)
> Liên quan: [delivery-matching](../product/features/delivery-matching.md), [priority-roadmap](../plans/active/priority-roadmap.md)

## Mình đã làm gì
Thêm luồng shipper huỷ đơn **sau khi đã accept** (khi chưa lấy hàng): reset đơn về
`FINDING_SHIPPER`, giải phóng shipper, và tìm shipper mới — **loại trừ** shipper vừa huỷ.
Endpoint mới `POST /api/deliveries/cancel-assignment`.
File: `DeliveryService.java`, `DeliveryServiceImpl.java`, `DeliveryController.java`.

## Kỹ thuật quan trọng

### 1. Đọc kiến trúc TRƯỚC khi code — tránh xây lại cái đã có
Task ban đầu (theo review cũ) là "gắn `DeliveryWaitingService`". Khi grep thì
**component đó không tồn tại**. Sự thật: matching được **Saga orchestrator** điều
phối, và luồng *reject-trước-accept* đã có sẵn re-trigger tìm shipper với
`excludedShipperIds` + giới hạn số lần (`handleShipperRejected` trong `SagaManager`).
→ Bài học: một dòng grep tiết kiệm cả ngày xây nhầm. Luôn xác minh giả định của
tài liệu bằng code thật.

### 2. Tái dùng cơ chế sẵn có thay vì phát minh mới
"Huỷ sau accept" về bản chất giống "reject", chỉ khác trạng thái xuất phát
(ASSIGNED thay vì FINDING_SHIPPER). Thay vì viết luồng rematch riêng, mình **bắn lại
đúng event `delivery.shipper-rejected`** mà Saga đã biết xử lý → tự động có exclusion
+ giới hạn retry, không phải đụng vào Saga. Ít code, ít rủi ro, hành vi nhất quán.

### 3. Khử trùng lặp an toàn
Luồng reject cũ có sẵn `publishMatchRejectedEvent` build map event. Mình tách phần
publish thành helper `publishShipperRejectedForRematch(delivery, shipperId, reason)`
và cho **cả hai** (reject + cancel) gọi chung. Đổi hàm cũ thành 1 dòng delegate —
giữ nguyên hành vi, bớt lặp.

### 4. Guard trạng thái = quy tắc nghiệp vụ trong code
Chỉ cho huỷ khi `status == ASSIGNED` (chưa lấy hàng). Sau `PICKED_UP` hàng đã ở
shipper → chặn với thông báo rõ. Guard này là nơi *business rule* sống trong code;
chọn mặc định an toàn và đánh dấu là câu hỏi mở thay vì tự quyết ngầm.

## Quyết định & đánh đổi
- **Endpoint riêng** `cancel-assignment` thay vì thêm action `CANCEL` vào `/accept`:
  `/accept` có guard cứng `status == FINDING_SHIPPER`, nhồi thêm sẽ rối. Endpoint
  riêng rõ ràng hơn. Đánh đổi: thêm một route (nhưng cùng path `/api/deliveries/**`
  nên gateway không cần đổi).
- **Tái dùng `AcceptDeliveryRequest`** làm body (chỉ cần `orderId` + `rejectReason`)
  thay vì tạo DTO mới — giảm bề mặt code cho một thao tác đơn giản.

## Cạm bẫy / lỗi dễ mắc
- Quên **giải phóng shipper** (`publishShipperStatusChange AVAILABLE`) → shipper kẹt
  trạng thái BUSY, không nhận đơn mới dù đã huỷ.
- Nếu tự viết rematch riêng mà quên exclusion → shipper vừa huỷ lại được match ngay lại.
- Cho huỷ sau `PICKED_UP` mà không có quy trình hoàn hàng → mất kiểm soát món ăn.

## Cách kiểm chứng
- `mvn -o compile` (force recompile 3 file) → **BUILD SUCCESS**.
- Test luồng (khi chạy cụm): accept đơn → gọi `/cancel-assignment` → đơn về
  `FINDING_SHIPPER`, shipper cũ không được match lại, shipper khác nhận được.
- Chưa chạy integration thật (cần cụm docker) — đây là giới hạn hiện tại.

## Câu hỏi mở
- Có cho phép huỷ sau `PICKED_UP` không? Nếu có, quy trình hoàn món + đối soát COD?
- Có phạt/đếm số lần shipper huỷ sau accept để hạn chế lạm dụng không?

## Muốn đào sâu thêm
Từ khoá: "Saga orchestration compensation", "event reuse idempotent re-trigger",
"state guard as business invariant", "DRY refactor extract method".
