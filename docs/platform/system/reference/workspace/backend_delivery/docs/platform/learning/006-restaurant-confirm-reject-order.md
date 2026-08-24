# 006 — Nhà hàng xác nhận/từ chối đơn (nối producer cho consumer chết)

> Ngày: 2026-07-22 · Service: restaurant-service (producer), order-service (consumer + Kafka config)
> Liên quan: [FEATURE_STATUS §6](../FEATURE_STATUS.md), [order-lifecycle](../product/features/order-lifecycle.md)

## Mình đã làm gì
Đóng gap: order-service *lắng nghe* `restaurant.order-confirmed/rejected` nhưng
không service nào publish → nhà hàng không có cách xác nhận/từ chối đơn. Thêm
endpoint + publisher ở restaurant-service, và **sửa Kafka config order-service** để
listener POJO thực sự nhận được event.
File: `RestaurantOrderController.java`, `RestaurantOrderEventPublisher.java` (mới),
`KafkaConfig.java` (order), `application.properties` (cả hai).

## Kỹ thuật quan trọng

### 1. "Dead consumer" — bug ẩn nguy hiểm hơn code thiếu
Có `@KafkaListener` không nghĩa là luồng chạy. Ở đây consumer tồn tại nhưng **không
có producer** → nhìn code tưởng có tính năng, thực tế chết. Luôn kiểm tra **cả hai
đầu** của một event (ai publish, ai consume) trước khi tin là đã xong.

### 2. StringDeserializer + @Payload POJO ⇒ phải có message converter
order-service đặt `value-deserializer=StringDeserializer` nhưng listener nhận
`@Payload RestaurantEvent` (POJO). Không có converter → String không tự thành POJO,
listener **không bao giờ chạy đúng**. Fix: `factory.setRecordMessageConverter(
new StringJsonMessageConverter(objectMapper))`. Cơ chế: container đọc value ra String;
khi resolve `@Payload`, converter parse JSON→POJO theo **kiểu tham số của method**.
- Listener nhận `String` (Saga) vẫn pass-through — không vỡ.
- **Phải truyền ObjectMapper của Spring** (đã có JavaTimeModule), nếu `new
  StringJsonMessageConverter()` tự tạo mapper → parse `LocalDateTime` sẽ lỗi.

### 3. Khớp "hợp đồng" theo tên field, không theo class
Producer (restaurant) gửi `Map<String,Object>` với key trùng field của
`RestaurantEvent` bên order (orderId, restaurantId, status, action, estimatedPrepTime,
rejectionReason, processedAt, notes). Consumer parse JSON theo tên field, không cần
chung class/DTO giữa 2 service. Gửi Map tránh phụ thuộc type-header xuyên service.

### 4. Cấu hình producer tường minh > dựa vào default
restaurant-service không khai báo producer serializer → hành vi mơ hồ (Map có ra JSON
không?). Mình set rõ `value-serializer=JsonSerializer`. Nguyên tắc: thứ gì đi qua ranh
giới service thì khai báo tường minh, đừng phó mặc default.

## Quyết định & đánh đổi
- **Scope gọn có chủ đích**: reject hiện chỉ đổi trạng thái đơn (`REJECTED_BY_RESTAURANT`),
  **chưa dừng** delivery đang tìm shipper. Vì huỷ delivery đi qua **Saga command**
  (`saga.command.cancel-delivery`), nối thẳng sẽ phá pattern orchestration. Để lại
  follow-up làm qua Saga cho đúng kiến trúc. Ghi rõ để không quên.
- **Ownership yếu**: restaurant-service không có dữ liệu order nên chưa verify order
  thuộc nhà hàng của owner (chỉ check role qua header). Cần order-service verify
  `creatorId` để chặt hơn — câu hỏi mở.

## Cạm bẫy / lỗi dễ mắc
- Thêm converter mà quên ObjectMapper có JavaTimeModule → lỗi `LocalDateTime`.
- Đổi producer serializer có thể ảnh hưởng consumer khác (SearchSync). Mình giữ
  JsonSerializer mặc định (có type header) nên search-service (trusted.packages=*) vẫn ok.

## Cách kiểm chứng
- `mvn -o compile` order-service + restaurant-service → **BUILD SUCCESS**.
- Khi chạy cụm: `POST /api/restaurants/orders/{id}/confirm` (role SHOP_OWNER) →
  order chuyển `CONFIRMED_BY_RESTAURANT`; `/reject` → `REJECTED_BY_RESTAURANT`.
- Chưa integration test thật (cần cụm docker).

## Câu hỏi mở
- Reject có nên **hủy delivery + dừng matching** (qua Saga) không? (hiện chưa)
- Confirm có nên **gate** việc tìm shipper (chỉ tìm sau khi nhà hàng đồng ý) không?
  Hiện đơn tìm shipper ngay khi tạo, không chờ nhà hàng.
- Hardening ownership: order-service verify order thuộc nhà hàng của owner.

## Muốn đào sâu thêm
Từ khoá: "Spring Kafka StringJsonMessageConverter", "RecordMessageConverter @Payload
POJO", "Kafka dead consumer no producer", "event contract by field name".
