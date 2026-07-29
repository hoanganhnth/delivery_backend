# Notification Service — MVP Contract

Notification Service chạy cổng nội bộ `8091`. Contract cấp hệ thống nằm tại
`../docs/system-contract-inventory.md`; source, migrations và tests là executable
truth khi tài liệu có mâu thuẫn.

## Trách nhiệm đang hoạt động

- Consume `order.created`, `delivery.status-updated` và
  `delivery.shipper-offered`.
- Persist inbox notification trong PostgreSQL trước external delivery.
- Dedup event bằng stable key; offer dùng `eventId + shipperId` để exact replay
  không gửi lại nhưng rematch mới tới cùng shipper vẫn hợp lệ.
- Cho user đã xác thực list/read/mark/delete notification của chính mình.
- Quản lý FCM token theo user trong Redis; reverse-owner Lua chặn một token thuộc
  nhiều account.
- Cho internal service gửi bounded notification command qua
  `POST /api/notifications/send` với `Internal-Token`; endpoint này không được
  Gateway public route.

## Public HTTP qua Gateway

Tất cả route dưới đây cần JWT; `X-User-Id` do Gateway strip rồi derive lại:

| Method | Path | Quyền |
|---|---|---|
| `GET` | `/api/notifications/user/{userId}` | path user phải là self |
| `GET` | `/api/notifications/unread` | self |
| `GET` | `/api/notifications/unread-count` | self |
| `GET` | `/api/notifications/{id}` | notification owner |
| `PUT` | `/api/notifications/{id}/read` | notification owner |
| `PUT` | `/api/notifications/mark-all-read` | self |
| `DELETE` | `/api/notifications/{id}` | notification owner |
| `POST` | `/api/firebase/register-token` | self |
| `POST` | `/api/firebase/unregister-token` | self |

Owned list endpoints cap 100 row; unread count vẫn là exact database count.

## Realtime và push

- Raw WebSocket `/ws/shipper-locations` của Tracking là transport vị trí MVP;
  Notification service không cung cấp location socket và không dùng gRPC.
- Legacy Notification STOMP `/ws-native` và simple broker đã bị xóa sau
  zero-call-site proof. Không tái tạo `/topic/user/{id}`; shipper offer dùng
  durable inbox + FCM wake-up và self recovery endpoint của Delivery.
- Firebase là optional. Khi cấu hình
  `FIREBASE_SERVICE_ACCOUNT_KEY_PATH`, resource phải tồn tại và đọc được; không
  có credential nhúng trong source/JAR.
- Khi Firebase không cấu hình, push là no-op và inbox vẫn hoạt động. Khi đã cấu
  hình, Redis/provider failure phải giữ notification PENDING và propagate để
  Kafka retry; chỉ token `UNREGISTERED` được loại rồi coi là terminal cho token đó.

## Persistence và retry

- Flyway V1 sở hữu bảng `notifications`, dedup unique constraint và query indexes.
- Production dùng Hibernate `ddl-auto=validate`, Open Session in View tắt.
- Kafka listener chỉ ACK sau processing thành công; error handler retry hai lần
  rồi publish same-partition DLT.
- PENDING row giữ stable notification ID để retry external delivery; SENT exact
  replay được skip.
- Delivery hiện là at-least-once: nếu một channel/token thành công rồi channel
  khác lỗi, retry có thể gửi lặp. Secure offer contract phải mang stable
  notification/event ID và runtime rehearsal phải kiểm client dedup.

## Capability đã loại hoặc chưa mở

- Broadcast/topic push, typing indicator, arbitrary connect/disconnect user
  message mapping và các helper shipper notification cũ đã bị xóa sau polyrepo
  zero-call-site proof.
- Analytics, online payment và livestream không được mở gián tiếp qua service này.

## Verification

```bash
env JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.16/libexec/openjdk.jdk/Contents/Home \
  mvn clean test
```

Focused/H2 proof không thay thế Gate B8: PostgreSQL unique race, Redis token race,
Kafka duplicate/crash/restart/DLT và kênh shipper-offer authenticated phải được
rehearsal trên runtime thật trước backend contract freeze.
