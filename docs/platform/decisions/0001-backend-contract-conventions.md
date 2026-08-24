# 0001 Backend Contract Conventions

Date: 2026-07-22

## Status

Accepted. Identity subsection amended on 2026-08-08 by
[backend ADR 0001](../../decisions/0001-jwks-resource-server-authentication.md).

## Context

Mười bảy backend module hiện dùng nhiều biến thể `BaseResponse`, Spring `Page`
serialization, `LocalDateTime`, Kafka payload và status/role string. Ba client còn
trộn Gateway origin, `/api` prefix và direct service port. Chương trình MVP cần
một contract ổn định trước khi sửa backend theo wave rồi mới đồng bộ client.

## Decision

### HTTP edge and identity

- Public HTTP đi qua Gateway origin; endpoint luôn mang path đầy đủ bắt đầu bằng
  `/api`. Client không nối thêm một `/api` thứ hai và không gọi direct service.
- Gateway strip X-User-Id/X-Role nhưng không dựng lại identity, giữ JWT key hay
  xác thực Bearer token. Mỗi resource service xác thực token qua Auth JWKS, tự
  dựng actor/authority và áp role/ownership policy. Internal endpoint dùng service
  credential riêng và không có public route.
- Role canonical: `USER`, `SHOP_OWNER`, `SHIPPER`, `ADMIN`.
- ID là số nguyên dương do server sinh. ID trong path/body là reference, không là
  bằng chứng ownership; actor ID luôn lấy từ JWT/internal credential và quan hệ
  resource phải được kiểm tra ở service sở hữu dữ liệu.

### JSON response and errors

Success JSON giữ compatibility với shape đang chiếm đa số:

```json
{
  "status": 1,
  "message": "Thành công",
  "data": {}
}
```

Error JSON:

```json
{
  "status": 0,
  "message": "Request không hợp lệ",
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "fieldErrors": {"field": "reason"},
    "traceId": "opaque-correlation-id"
  }
}
```

- HTTP status code là authority (`400`, `401`, `403`, `404`, `409`, `422`,
  `429`, `5xx`); field `status` không được dùng để trả lỗi bằng HTTP 200.
- `message` dành cho người dùng/log ngắn; client branch theo HTTP status và
  `error.code`, không parse message.
- `fieldErrors` chỉ có cho validation; không trả stack trace, SQL, secret hoặc
  internal exception message.
- Các Java constructor khác thứ tự phải được chuẩn hóa theo named factory để
  tránh đảo `message` và `data`; JSON field order không mang ý nghĩa.

### Pagination

- Query dùng `page` zero-based, `size`, optional `sort`; `size` mặc định 20, tối
  đa 100 trừ export/admin job riêng.
- `data` của response phân trang có shape ổn định, không serialize trực tiếp
  implementation detail của Spring `Page`:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0,
  "hasNext": false
}
```

### Money and time

- Tiền MVP là VND, dùng `BigDecimal`/`DECIMAL(19,0)` và JSON integer decimal;
  không dùng `double`, không âm trừ ledger direction biểu diễn riêng.
- Rate/percentage dùng decimal rõ scale và rounding rule tại use case; ledger lưu
  amount tuyệt đối cùng direction/reason.
- Storage và event time dùng UTC. Public/event timestamp là ISO-8601 có offset,
  ưu tiên `Instant` và hậu tố `Z`. `LocalDateTime` legacy phải migrate ở boundary,
  không phát JSON timezone-less mới.
- Business date (dashboard/campaign theo ngày Việt Nam) dùng
  `Asia/Ho_Chi_Minh`, chuyển sang UTC khi query/storage.

### Kafka events

- Event mới/freeze có envelope: `eventId`, `eventType`, `eventVersion`,
  `occurredAt`, `aggregateType`, `aggregateId`, `correlationId`, optional
  `causationId`, và `payload` typed.
- Kafka key là canonical aggregate ID cần ordering; cùng aggregate không đổi key
  giữa các bước. Không thêm prefix tùy service như `delivery_<id>`.
- Consumer idempotent theo `eventId` hoặc unique business key trước side effect.
  Retry phải có terminal policy/DLT; không ack lỗi chưa xử lý chỉ để làm queue xanh.
- Contract event được test schema/example giữa producer và consumer; không dùng
  raw `Map`/`JsonNode` như public contract sau freeze.

### Realtime

- Vị trí MVP dùng raw WebSocket `/ws/shipper-locations`; gRPC ngoài scope.
- Vị trí raw WebSocket được Tracking xác thực qua JWKS trong handshake và kiểm
  participant theo Delivery. Notification/delivery STOMP đã bị xóa khỏi MVP,
  không phải compatibility surface để mở lại.

## Alternatives Considered

1. Giữ nguyên từng response/Page/event shape theo service: ít migration trước mắt
   nhưng tiếp tục buộc ba client có adapter riêng và không thể contract-test.
2. Chuyển ngay sang một response hoàn toàn mới không giữ `status/message/data`:
   sạch hơn nhưng tạo breaking change không cần thiết cho MVP.
3. Dùng epoch millis và `double` money: đơn giản nhưng mơ hồ timezone và không an
   toàn cho settlement.

## Consequences

Positive:

- Backend waves có target nhất quán và client có một contract freeze để theo.
- HTTP/Kafka lỗi có thể quan sát, retry và đối chiếu mà không parse log/message.
- VND, UTC và pagination không còn phụ thuộc framework serializer.

Tradeoffs:

- Search và endpoint đang trả raw `Page` phải migrate response và client cùng
  compatibility mapping sau Gate B8.
- `LocalDateTime`, duplicate `BaseResponse` và raw Kafka payload không thể sửa một
  commit; từng wave phải giữ test contract trong quá trình chuyển đổi.

## Follow-Up

- Phase 1 tạo shared test fixtures/factories và Gateway contract tests.
- Mỗi service wave cập nhật OpenAPI/example và adapter tạm nếu client chưa chuyển.
- Phase 7 xóa adapter/duplicate types chỉ sau polyrepo contract diff.

Implementation note 2026-07-27: Order, Shipper và Search public pagination đã
được chuyển khỏi Spring `Page` serialization sang exact
`{items,page,size,totalItems,totalPages,hasNext}`; Web/Flutter consumer được
migrate cùng contract và reject legacy `.content/.number/.totalElements`.

Implementation note 2026-07-27: Auth và Settlement đã bỏ constructor ba tham số
theo thứ tự riêng, chuyển sang `(status,data,message)` cùng named factory
`success/failure`. Flash Sale error giữ HTTP status ở transport và chỉ dùng
`BaseResponse.status=0`; baseline verifier chặn hai dạng drift này quay lại.
