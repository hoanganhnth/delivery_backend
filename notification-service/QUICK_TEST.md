# Notification Service — Quick Verification

Notification Service chạy nội bộ ở cổng `8091`; client chỉ đi qua Gateway
`http://localhost:8079`. Không có Notification WebSocket/STOMP endpoint.

## Unit/integration tests

```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH \
  mvn test
```

## Runtime reads qua Gateway

Thay `<JWT>` bằng access token của chính user đang đọc:

```bash
curl -fsS http://localhost:8079/api/notifications/unread-count \
  -H 'Authorization: Bearer <JWT>'

curl -fsS 'http://localhost:8079/api/notifications/unread?page=0&size=20' \
  -H 'Authorization: Bearer <JWT>'
```

Không tự gửi `X-User-Id` hoặc `X-Role`; Gateway strip và derive identity từ JWT.
Internal send API cần `Internal-Token` và không được public route qua Gateway.

## Canonical cross-service proof

Từ thư mục `backend_delivery/`, dùng harness COD để kiểm
`delivery.shipper-offered` → durable `MATCH_FOUND` inbox → self current-offer
recovery. FCM chỉ là wake-up optional; PostgreSQL inbox là recovery authority.
