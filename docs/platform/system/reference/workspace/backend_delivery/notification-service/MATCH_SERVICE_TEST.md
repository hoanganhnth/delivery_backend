# Shipper Offer Notification — Current Contract

Tài liệu này thay thế các ví dụ legacy `match.shipper-*` và STOMP. Các topic đó
không thuộc MVP contract hiện tại.

## Luồng canonical

1. Match Service phát `shipper.found` cho Saga.
2. Saga phát `saga.command.cache-shipper-found` cho Delivery.
3. Delivery persist offer/expiry rồi phát `delivery.shipper-offered` bằng outbox.
4. Notification persist durable inbox loại `MATCH_FOUND` cho đúng shipper.
5. FCM có thể wake app; app luôn recover offer bằng authenticated self endpoint
   của Delivery qua Gateway trước khi accept/reject.

Notification không cung cấp WebSocket. Raw `/ws/shipper-locations` thuộc Tracking
và chỉ truyền vị trí.

## Bằng chứng cần dùng

- Focused Notification tests kiểm event validation, dedup và owner access.
- Canonical COD harness bắt buộc quan sát durable `MATCH_FOUND` notification rồi
  recover đúng current offer trước accept.
- Exact replay của cùng `eventId + shipperId` không tạo thêm inbox/provider path;
  rematch event mới tới cùng shipper vẫn được phép.

Không publish synthetic money/order event vào topic production. Khi cần test Kafka
recovery, dùng topic, consumer group và PostgreSQL database cô lập theo Gate B8.
