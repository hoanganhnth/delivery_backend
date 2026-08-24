# Notification Service — Setup and Testing

Contract hiện tại nằm tại `NOTIFICATION_SERVICE_README.md` và
`../docs/system-contract-inventory.md`. Source, Flyway migrations và tests là
executable truth.

## Chạy trong full stack

Từ thư mục `backend_delivery/`, dùng Compose canonical với secrets local. Service
chạy nội bộ ở `8091`; Gateway `8079` là client ingress duy nhất. PostgreSQL giữ
durable inbox, Redis giữ FCM token và Kafka cấp các event:

- `order.created`
- `delivery.status-updated`
- `delivery.shipper-offered`

Không có Notification WebSocket, SockJS, STOMP hoặc `/topic/user/{id}`. Realtime
vị trí dùng raw Tracking `/ws/shipper-locations`, không đi qua service này.

## Chạy tests

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
  mvn test
```

## HTTP smoke qua Gateway

```bash
curl -fsS http://localhost:8079/api/notifications/unread-count \
  -H 'Authorization: Bearer <JWT>'

curl -fsS 'http://localhost:8079/api/notifications/user/<SELF_USER_ID>?page=0&size=20' \
  -H 'Authorization: Bearer <JWT>'

curl -fsS -X POST http://localhost:8079/api/firebase/register-token \
  -H 'Authorization: Bearer <JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"token":"<FCM_TOKEN>"}'
```

Không tự cấp `X-User-Id`/`X-Role`; client chỉ gửi
`Authorization: Bearer <access-token>`. Gateway không sở hữu hay inject trusted identity headers;
Notification tự validate JWT qua Auth JWKS.
Internal send endpoint yêu cầu `Internal-Token` và không public qua Gateway.

## Firebase

FCM là optional. Chỉ cấu hình `FIREBASE_SERVICE_ACCOUNT_KEY_PATH` tới credential
ngoài source tree. Khi không cấu hình, provider là no-op nhưng durable inbox vẫn
hoạt động. Khi đã cấu hình, provider/Redis failure giữ row `PENDING` để retry.

## Cross-service verification

Canonical COD harness kiểm đầy đủ durable `MATCH_FOUND` inbox, self current-offer
recovery, accept, lifecycle và settlement. Runtime recovery test phải dùng topic,
consumer group và database cô lập; không publish synthetic event vào production
topic/database.
