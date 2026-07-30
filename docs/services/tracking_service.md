# Tracking Service

## Phạm vi MVP

Tracking sở hữu heartbeat/vị trí realtime của shipper bằng Redis GEO và raw
WebSocket. gRPC, STOMP/SockJS và topic-style subscription không thuộc contract
MVP.

- Raw socket qua Gateway: `/ws/shipper-locations`.
- REST retained: shipper tự update/offline; internal Delivery participant check
  bảo vệ subscription. REST update dùng cùng policy fail-closed với socket:
  tọa độ phải hữu hạn trong range hợp lệ, optional `accuracy/speed/heading` nếu
  gửi phải hữu hạn, và `isOnline` không được null/sai kiểu.
- Redis lưu location/online freshness và active-delivery routing projection của Tracking.
- Kafka `shipper.location-updated` replicate vị trí sang Match và async history consumer.
- PostgreSQL `tracking_db` chỉ lưu sampled audit/support history; không nằm trong hot path.
- Tracking không sở hữu BUSY/AVAILABLE matching state và không consume
  `shipper.status-change`; Match là consumer duy nhất cần trạng thái đó.

## Luồng publisher

Shipper handshake bằng JWT; server derive shipper identity từ principal, không
tin shipper ID/header do client tự khai. Mỗi location update phải có tọa độ hợp
lệ; optional telemetry không có dữ liệu được giữ `null`, còn giá trị không hữu
hạn bị reject trước khi ghi Redis/Kafka. Update được ghi Redis trước, broadcast
cho participant đã authorize và publish sang Match. Redis hoặc Kafka lỗi phải
báo lỗi để client retry, không trả success giả.

Explicit offline tạo timestamped tombstone kể cả khi chưa có tọa độ cache. Match
dùng timestamp này để chặn online event cũ làm shipper sống lại trong freshness
window.

Mỗi shipper chỉ có một publisher generation trong Redis. Connection mới tăng
generation và connection cũ bị fence ở lần `ping`/`update_location` tiếp theo.
Clean disconnect lưu deadline grace 30 giây trong Redis; hard crash giữ deadline
theo lease TTL 120 giây. Sweeper phân tán claim deadline, kiểm generation và
active lease trước khi phát offline tombstone, nên restart hoặc process chết
trong cửa sổ grace không làm mất transition offline.

## Luồng subscriber

Customer/restaurant/shipper/admin chỉ subscribe một `deliveryId` mà internal
Delivery access check xác nhận họ là participant phù hợp. Tracking phát payload
location raw WebSocket, không phát offer hoặc delivery state; shipper offer được
khôi phục qua `GET /api/deliveries/offers/current`.

Subscription nội bộ được index theo delivery room, không theo shipper đơn thuần.
Assignment BUSY/AVAILABLE fence room cũ/mới; Redis Pub/Sub chuyển exact
`deliveryId` giữa các Tracking instance. Slow session dùng bounded coalescing
queue và subscribe/reconnect luôn đọc location cuối từ Redis nên không mất final
state.

## Location history support

History là audit/support-only: giữ 90 ngày, tọa độ 5 chữ số thập phân, sample
10 giây hoặc 25 m. Consumer `tracking-location-history` ghi PostgreSQL async với
receipt unique theo event ID; replay không tạo duplicate và out-of-order được so
với cả điểm trước/sau. Chỉ internal secret + ADMIN support được query bounded
theo một delivery; không có public/client/fleet history API. Chi tiết vận hành:
`../operations/location-history.md`.

## Trạng thái và proof còn mở

Focused test khóa JWT/session participant, Redis/Kafka failure propagation,
publisher generation/offline tombstone và payload validation fail-closed. Runtime rehearsal đã chứng minh
same-instance supersession/reconnect, cross-instance generation fence, hard crash
và process chết sau clean disconnect đều hội tụ Tracking/Match về offline. Gate
B8 vẫn cần token revocation, Redis reorder/failure matrix và các race fulfilment
khác trước contract freeze.
