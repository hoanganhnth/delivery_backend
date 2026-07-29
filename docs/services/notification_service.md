# Notification Service

## Contract MVP

Notification lưu inbox bền vững trong PostgreSQL. FCM là kênh đánh thức tùy
chọn; REST inbox là nguồn recovery. Notification không còn STOMP endpoint,
broker hay message DTO; raw WebSocket chỉ thuộc Tracking location contract.

## Input event

| Topic | Hành vi |
|---|---|
| `order.created` | Tạo notification cho customer theo stable event/order/user identity và canonical non-blank `restaurantName`; thiếu tên thì fail-closed, không dùng placeholder |
| `delivery.status-updated` | Tạo notification theo canonical delivery status; chỉ dùng `shipperName` nếu producer gửi, nếu thiếu thì message phải generic và không bịa tên |
| `delivery.shipper-offered` | Tạo đúng một inbox record cho shipper được chọn và FCM wake-up |

Listener chỉ ACK sau khi notification được lưu và external delivery thành công.
Deduplication key có unique constraint; replay của row `SENT` là no-op, row
`PENDING` dùng lại cùng notification ID. Replay chỉ hợp lệ khi `userId` và các
immutable notification fields khớp row đã lưu; tái sử dụng key cho payload khác
bị từ chối để không làm lộ hoặc ghi đè notification của account khác.

## REST public qua Gateway

- Inbox self: list, unread list/count, get by ID, mark one/all read và delete.
- FCM self: register/unregister token.
- `POST /api/notifications/send` là internal-only, yêu cầu `Internal-Token` và
  không có Gateway route.

List được cap 100. Read/update/delete luôn scope theo trusted `X-User-Id`.
Repeated mark-read trả state hiện tại và không đổi `readAt` lần nữa.

## Offer cho shipper

Push chỉ báo có offer và yêu cầu app fetch
`GET /api/deliveries/offers/current`. Notification không tự tính phí ship hoặc
earnings từ khoảng cách; các giá trị tài chính chỉ lấy từ Delivery response.

## Redis và FCM

Redis chỉ giữ FCM token membership và reverse owner bằng Lua atomic. Owner và
membership không dùng TTL lệch nhau: token tồn tại tới explicit unregister hoặc
Firebase trả `UNREGISTERED`, tránh token bị account khác claim trong khi account
cũ vẫn còn membership. Notification inbox không dùng Redis cache write-only.

Firebase không cấu hình là no-op để inbox vẫn hoạt động. Khi Firebase đã cấu
hình, Redis/provider failure được propagate để Kafka retry row `PENDING`.

## Proof còn mở

Gate B8 phải chứng minh PostgreSQL concurrent dedup, Kafka crash/restart/DLT,
Redis Lua ownership, FCM multi-token partial delivery và shipper foreground/
background recovery. Multi-token push là at-least-once; client cần dedup theo
notification/event identity.
