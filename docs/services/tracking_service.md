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
window. Kafka timestamp là thời điểm core tạo location/tombstone fact, được
truyền nguyên epoch millis đến publisher; thời gian chờ Kafka không làm fact cũ
trở thành mới. Core giữ Instant tuyệt đối; adapter giữ encoding timestamp
local-date-time hiện tại cho WebSocket/offline payload.

Mỗi shipper chỉ có một publisher generation trong Redis. Connection mới tăng
generation và connection cũ bị fence ở lần `ping`/`update_location` tiếp theo.
WebSocket update mang nguyên lease qua application core; Lua kiểm generation và
active lease cùng lúc ghi cache/GEO/online set. Publisher bị thay thế sau refresh
vẫn không thể ghi đè vị trí mới hoặc phát Kafka/fanout từ attempt bị chặn.
Clean disconnect lưu deadline grace 30 giây trong Redis; hard crash giữ deadline
theo lease TTL 120 giây. Deadline lease/refresh/grace, discovery và claim đều
tính ngay trong Redis Lua bằng giờ Redis, cùng clock với active-key TTL và
kiểm tra claim cuối cùng; thời gian trễ gửi lệnh không bị trừ vào duration.
Claim trả lại nguyên deadline Redis đã ghi. Callback scheduler dùng giờ JVM chỉ
là trigger; Redis quyết định deadline đã đến hay chưa, sweeper giữ recovery
khi callback mất hoặc đến sớm. Sweeper phân tán claim deadline; một Lua operation kiểm
đồng thời generation, không có active lease và claim còn hiệu lực trước khi ghi
offline vào cache/GEO/online set. Reconnect hoặc worker claim mới chặn mutation
cũ và không phát tombstone từ attempt bị chặn. Với attempt được chấp nhận,
claim chỉ hoàn tất sau Kafka và fanout. Claim hết hạn không được xóa deadline;
lỗi giữ deadline để retry sau restart/process chết trong cửa sổ grace.

Fence này bảo vệ mutation Redis; Kafka/PubSub vẫn là các bước tiếp theo, chưa có
transaction chung với Redis. Match dùng occurrence timestamp để chặn fact cũ
publish muộn trong freshness window; cùng millisecond vẫn theo policy hiện tại
của Match. Realtime receiver dùng occurrence epoch millis trong envelope nội bộ và watermark
theo membership `(deliveryId, shipperId, sessionId, membershipVersion)`: fact cũ
hoặc cùng millisecond không vào queue; unsubscribe/end/rejoin tạo membership mới.
Admission và enqueue cùng lock theo shipper, bootstrap đọc cache/register/seed
cũng cùng lock để không chen một packet cũ sau snapshot mới. Cache là một value
`StoredShipperLocation(location, occurredAt)`, giữ metadata ngay cả offline không
có tọa độ; HTTP/WebSocket payload công khai giữ nguyên.

Cutover cache/envelope này cần dừng toàn bộ Tracking writer cũ trước khi bật bản
mới; chờ cache location cũ hết TTL 300 giây hoặc refresh bằng writer mới. Cache
cũ vẫn đọc được cho tọa độ offline nhưng không dùng để đoán watermark; subscribe
với cache thiếu metadata và PubSub envelope thiếu occurrence time fail-closed.
Rollback cũng dừng writer mới rồi đợi cache hết TTL trước khi chạy bản cũ.
Không chạy mixed-version Tracking writers. Mỗi source mutation so occurrence
metadata trong cùng Lua với cache/GEO/online set; fact cũ hoặc cùng millisecond
giữ nguyên projection và không refresh TTL. Lease/claim guard vẫn chạy trước;
no-op của fact cũ không giả thành publisher superseded hoặc claim bị từ chối.
ACK và pipeline phát fact giữ nguyên, receiver Kafka/realtime dùng watermark để
bỏ qua fact cũ. Metadata hỏng làm mutation lỗi trước mọi write; chỉ legacy DTO
đúng type mới được thay bằng record mới mà không đoán timestamp local.
Cache/ordering không phải state bền vững: mất Redis hoặc hết TTL làm mất basis
cũ, nên không có bảo đảm chống replay vô hạn. Quy trình cold recovery ở dưới
giữ giới hạn MVP này; không thêm PostgreSQL vào hot path.

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
Hàm đọc routing kiểm fence từng item bằng một MGET: shared set được sibling
gia hạn không làm item hết fence tiếp tục được route.
Active key giữ format cũ. Các writer cần cùng phiên bản fence mới để bảo vệ này
có hiệu lực; writer phiên bản cũ bỏ qua terminal key. Redis mất dữ liệu hoặc
terminal key hết TTL làm mất basis chống replay cũ; phải khôi phục từ current
Delivery facts theo quy trình dưới trước khi mở lại traffic.
Slow session dùng bounded coalescing queue và subscribe/reconnect luôn đọc
location cuối từ Redis nên không mất final state.

## Cold recovery khi Redis mất state

1. Dừng toàn bộ Tracking writer/consumer đang dùng state cũ; close socket cũ và
   khởi động instance với local room/membership rỗng. Không rolling-mix phiên bản.
2. Khôi phục routing bằng current BUSY/AVAILABLE facts đã đối chiếu trạng thái
   Delivery, gồm từng item trong batch. Không replay riêng một BUSY lịch sử khi
   terminal/freshness fence đã mất. Replay/reset Kafka offset phải được lập plan
   vận hành riêng và chạy lúc quiesced; hiện không có job tự rebuild toàn bộ Redis.
3. Shipper reconnect để lấy lease/session mới và gửi observation vị trí mới;
   subscriber xác thực lại participant rồi đọc source hiện có. Generation có
   thể reset sau mất Redis, nhưng session value khác vẫn fence publisher cũ;
   claim/deadline cũ thiếu state không được mutation/completion.
4. Chỉ mở traffic sau khi current routing/source và reconnect đã được kiểm tra.
   Nếu projection trống, fanout không có assigned room để gửi; Delivery-authorized
   subscribe fallback chỉ tạo local membership, không tự tạo shared assignment.

Executable boundary proof dùng real Redis flush/expiry rồi apply current facts,
không giả lập rằng có thể dựng lại history đã mất. Location source hết TTL cần
observation mới; per-membership watermark chỉ bảo vệ lifetime của membership đó.
PostgreSQL support history không được dùng tự suy đoán current assignment/lease.

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

Source nằm tại `tracking/{domain,application-api,application,infrastructure,boot}`.
Infrastructure giữ toàn bộ HTTP/WebSocket/Redis/Kafka/JPA adapter và Spring
composition; boot chỉ giữ entrypoint, runtime properties và integration tests.
Artifact/DNS vẫn là `tracking-service`. Packaged two-JVM HTTP/WebSocket/JWKS,
Kafka/Redis/PostgreSQL, hard-kill recovery và restart đã có executable proof tại
`scripts/verify-tracking-runtime.py`. Generation-check → mutation đã có Lua fence
và proof reconnect giữa hai bước; realtime watermark/cache metadata cũng có
proof receiver và reconnect. Source-cache ordering đã có Lua compare-and-set trên paired occurrence metadata;
giới hạn recovery ngoài TTL/mất Redis và audit cuối còn cần hoàn tất trước main. Host `tracking-service/` và `modules/tracking/` đã được thay bằng
các layer ở gốc trong worktree refactor.

Policy principal/projection identity và identity inbox, offline/tombstone, publication và publisher lease/grace/recovery hiện chạy trong
`tracking-application`; adapter JPA/Redis/Kafka/HTTP giữ mapping và transport.
REST và internal offline dùng cùng core, gồm cả khi không có cache hoặc tọa độ
cache chỉ có một phía. Grace và expiry sweeper dùng cùng operation offline +
distributed fanout; Redis mutation → Kafka tombstone → PubSub trước khi complete
expiry claim. HTTP offline cũng dùng operation này. WebSocket gọi publisher core
trực tiếp; facade/callback fanout cũ đã được xoá.
REST và WebSocket update dùng chung core, Redis/Kafka adapter và Redis PubSub
fanout. Source do server quyết định; giữ encoding Instant của REST và local
date-time của WebSocket. REST bỏ qua `isOnline` vẫn mặc định true; null hoặc
giá trị khác boolean bị từ chối trước khi ghi vị trí. Hai service location và
availability cũ không còn caller đã được xoá, test lỗi chuyển sang core/adapter.
`spring.task.scheduling.enabled=false` tắt các job định kỳ (lease sweep/history
retention); scheduler cho callback grace vẫn hoạt động. Khi không cấu hình cờ,
hai job định kỳ vẫn bật như trước. Spring context thật kiểm chứng cả ba cấu hình.
Identity inbox dùng core để kiểm raw-payload fingerprint, replay, stale version
và version gap. Support-denial HTTP403 giữ nguyên qua global advice. PostgreSQL adapter khoá theo event rồi principal trong cùng
transaction ghi projection/receipt, kể cả khi chưa có row. Exact replay không
ghi lại, event cũ chỉ tạo receipt và gap không tạo receipt. Giữ admission cũ:
snapshot đầu tiên có thể có version bất kỳ lớn hơn 0; event mới cùng version
vẫn được apply. Listener ACK sau khi inbox transaction hoàn tất; decode/conflict/
storage failure không ACK. Kafka retry/DLT và shape event không đổi.
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
Recovery ngoài TTL/mất Redis và audit cuối/main integration còn cần hoàn tất;
chưa coi toàn bộ Tracking hoàn tất. Bằng chứng và tiến độ nằm ở
`../plans/active/service-architecture-consolidation.md`.
