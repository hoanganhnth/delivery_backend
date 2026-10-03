# Tracking Service

## Phạm vi MVP

Tracking sở hữu heartbeat/vị trí realtime của shipper bằng Redis GEO và raw
WebSocket. gRPC, STOMP/SockJS và topic-style subscription không thuộc contract
MVP.

- Raw socket qua Gateway: `/ws/shipper-locations`.
- REST retained: shipper tự update/offline; Shipper's explicit `false` status
  command calls the credential-protected internal Tracking offline endpoint so
  Tracking remains the availability authority; internal Delivery participant check
  bảo vệ subscription. REST update dùng cùng policy fail-closed với socket:
  tọa độ phải hữu hạn trong range hợp lệ, optional `accuracy/speed/heading` nếu
  gửi phải hữu hạn, và `isOnline` không được null/sai kiểu.
- Redis lưu location/online freshness và active-delivery routing projection của Tracking.
- Kafka `shipper.location-updated` replicate vị trí sang Match và async history consumer.
- PostgreSQL `tracking_db` chỉ lưu sampled audit/support history; không nằm trong hot path.
- Tracking không sở hữu BUSY/AVAILABLE matching state. Tracking dùng event
  `shipper.status-change` của Delivery để cập nhật routing projection; Match
  dùng event này cho availability phục vụ matching.

## Luồng publisher

Shipper handshake bằng JWT. Tracking consume `shipper.identity.upserted` vào
projection local `principalId ↔ legacyUserId ↔ shipper.id`, rồi server derive
đúng `shipper.id` từ principal; không
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
Assignment BUSY/AVAILABLE fence room cũ/mới bằng Redis Lua compare-and-set trên
`(deliveryId,timestamp,eventId)`: event cũ là no-op, còn hai BUSY facts khác
nhau cùng timestamp fail-closed. Redis Pub/Sub chuyển exact `deliveryId` giữa
các Tracking instance. Routing listener có bounded retry và owner DLT
`shipper.status-change.tracking.DLT`; nó không đổi Match availability state.
AVAILABLE giữ terminal timestamp riêng trong cùng Lua transaction xoá active
assignment. Trong TTL projection 24 giờ, BUSY cũ hoặc cùng timestamp không
khôi phục assignment đã kết thúc; AVAILABLE đến trước BUSY cũng giữ fence.
Batch fence scope theo shipper/delivery nên kết thúc một item không chặn sibling.
Active key giữ format cũ. Các writer cần cùng phiên bản fence mới để bảo vệ này
có hiệu lực; writer phiên bản cũ bỏ qua terminal key. Redis mất dữ liệu hoặc
terminal key hết TTL vẫn cần recovery/replay audit riêng.
Slow session dùng bounded coalescing queue và subscribe/reconnect luôn đọc
location cuối từ Redis nên không mất final state.

## Location history support

History là audit/support-only: giữ 90 ngày, tọa độ 5 chữ số thập phân, sample
10 giây hoặc 25 m. Consumer `tracking-location-history` ghi PostgreSQL async với
receipt atomic theo event ID, identity delivery/shipper/time và SHA-256 raw
payload. Hai replica cùng claim bằng PostgreSQL `ON CONFLICT DO NOTHING`; replay
exact là no-op, còn tái dùng event ID với identity/payload khác fail-closed vào
`shipper.location-updated.tracking.DLT`. Out-of-order được so với cả điểm
trước/sau. Chỉ internal secret + ADMIN support được query bounded theo một
delivery; không có public/client/fleet history API. Retry/DLT owner riêng là
`shipper.location-updated-retry-tracking-*` / `.tracking.DLT`; nó không đổi
Redis/WebSocket realtime hay Match eligibility. Chi tiết vận hành:
`../operations/location-history.md`.

## Trạng thái và proof còn mở

Focused test khóa JWT/session participant, Redis/Kafka failure propagation,
publisher generation/offline tombstone và payload validation fail-closed. Runtime rehearsal đã chứng minh
same-instance supersession/reconnect, cross-instance generation fence, hard crash
và process chết sau clean disconnect đều hội tụ Tracking/Match về offline. Gate
B8 vẫn cần token revocation, Redis reorder/failure matrix và các race fulfilment
khác trước contract freeze.


## Kiến trúc đang hợp nhất

Policy principal/projection identity và identity inbox, offline/tombstone, publication và publisher lease/grace/recovery hiện chạy trong
`tracking-application`; adapter JPA/Redis/Kafka/HTTP giữ mapping và transport.
REST và internal offline dùng cùng core, gồm cả khi không có cache hoặc tọa độ
cache chỉ có một phía. Lease/grace vẫn dùng operation Kafka-only rồi callback
hiện tại; explicit HTTP offline broadcast sau khi publish thành công.
REST và WebSocket update dùng chung core, Redis/Kafka adapter và Redis PubSub
fanout. Source do server quyết định; giữ encoding Instant của REST và local
date-time của WebSocket. REST bỏ qua `isOnline` vẫn mặc định true; null hoặc
giá trị khác boolean bị từ chối trước khi ghi vị trí. Hai service location và
availability cũ không còn caller đã được xoá, test lỗi chuyển sang core/adapter.
`spring.task.scheduling.enabled=false` tắt các job định kỳ (lease sweep/history
retention); scheduler cho callback grace vẫn hoạt động. Khi không cấu hình cờ,
hai job định kỳ vẫn bật như trước. Spring context thật kiểm chứng cả ba cấu hình.
Identity inbox dùng core để kiểm raw-payload fingerprint, replay, stale version
và version gap. PostgreSQL adapter khoá theo event rồi principal trong cùng
transaction ghi projection/receipt, kể cả khi chưa có row. Exact replay không
ghi lại, event cũ chỉ tạo receipt và gap không tạo receipt. Giữ admission cũ:
snapshot đầu tiên có thể có version bất kỳ lớn hơn 0; event mới cùng version
vẫn được apply. Kafka retry/DLT và shape event không đổi.
History hiện dùng `LocationHistoryPolicy` và `DefaultLocationHistoryUseCase`:
sampling/precision trong domain; replay, query bound và retention trong application.
JPA adapter giữ SQL claim, transaction và khoá sampling theo delivery/shipper.
Listener/controller/job gọi core trực tiếp; `LocationHistoryService` cũ đã xoá.
Fanout chọn exact delivery set, fallback projection cũ và tiếp tục gửi room khác
khi một lần PubSub lỗi trong `DefaultLocationFanoutUseCase`; Redis/JSON/log ở adapter.
`DefaultDeliveryRoomAssignmentUseCase` sở hữu BUSY/AVAILABLE single/batch orchestration.
Sau Redis fence, core kiểm projection trước khi activate/end room local, tránh
event cũ sửa index local dù Redis đã bỏ qua event đó. Lua và retry/DLT không đổi.
`DefaultDeliveryRoomSubscriptionUseCase` dùng projection shared để giữ tất cả
room của batch sau khi Delivery authorize participant. Projection thiếu/stale
so với delivery vừa được authorize dùng fallback đúng delivery đó. Registry
index tập delivery và session membership; kết thúc một item không xoá audience
item khác. HTTP offline dùng cùng distributed fanout core qua Redis PubSub như
REST/WebSocket update; subscriber trên instance khác nhận tombstone kể cả thiếu tọa độ.
Terminal replay fence trong TTL đã có proof Redis; race đọc projection/index
local được tuần tự hoá theo shipper trên mỗi instance bằng bounded striped lock.
Membership có version riêng: message còn trong queue chỉ gửi nếu version vẫn
còn hiệu lực ngay trước send. Unsubscribe/rejoin, kết thúc item và authorized
reassignment vô hiệu queued message cũ; old-owner cleanup không xoá room mới.
Đây chưa phải distributed transaction với Redis hoặc Delivery authorization.
Race giữa JVM, recovery ngoài TTL/mất Redis và runtime còn cần audit;
chưa xoá host cũ hoặc coi toàn bộ Tracking hoàn tất. Bằng chứng và tiến độ nằm ở
`../plans/active/service-architecture-consolidation.md`.
