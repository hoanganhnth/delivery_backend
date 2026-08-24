# 🎓 Learning Notes — Kỹ thuật Backend

Mỗi khi mình (Claude) code một chức năng, mình ghi lại vào đây các **kỹ thuật quan
trọng, quyết định thiết kế, và câu hỏi** — để bạn học lại được cách nghĩ, không chỉ
đọc code thành phẩm.

## Quy ước

- Mỗi chức năng/lần code → 1 file: `NNN-<slug>.md` (ví dụ `001-delivery-waiting-service.md`).
- Đánh số tăng dần để dễ theo dõi thứ tự.
- Theo template [`_TEMPLATE.md`](./_TEMPLATE.md).
- Ghi cả **cái làm được** lẫn **cái đắn đo/đánh đổi** và **câu hỏi mở**.

## Mục lục

| # | Chức năng | Kỹ thuật chính | Ngày |
|---|---|---|---|
| [001](./001-wire-flashsale-into-compose.md) | Ghép flashsale vào docker-compose | Docker DNS, relaxed binding, override DB driver qua env, port host vs container | 2026-07-22 |
| [002](./002-seed-script.md) | Seed script test end-to-end | Đọc code lấy API contract, tiền điều kiện ẩn (Redis GEO), parse response bền, script idempotent | 2026-07-22 |
| [003](./003-shipper-cancel-after-accept.md) | Shipper huỷ sau accept → rematch | Xác minh giả định bằng grep, tái dùng Saga event, khử trùng lặp an toàn, guard = business rule | 2026-07-22 |
| [004](./004-shipper-pagination.md) | Phân trang getAllShippers (chống OOM) | findAll nguy hiểm, Pageable web binding, Page.map, breaking change API | 2026-07-22 |
| [005](./005-jwt-key-security.md) | Bảo mật khóa JWT & secret | Rotate key, externalize env, git rm --cached, fail-closed, PKCS8/X509 | 2026-07-22 |
| [006](./006-restaurant-confirm-reject-order.md) | Nhà hàng xác nhận/từ chối đơn | Dead consumer, StringJsonMessageConverter, khớp event theo field, producer tường minh | 2026-07-22 |
| [007](./007-user-authorization-idor.md) | Vá IDOR user-service | Broken access control, object-level auth, internal vs external endpoint | 2026-07-22 |
| [008](./008-reject-stops-delivery-via-saga.md) | Reject dừng delivery qua Saga | Event choreography, kích hoạt orchestration bằng event, tái dùng luồng huỷ | 2026-07-22 |
| [009](./009-gate-shipper-on-restaurant-confirm.md) | Gate tìm shipper theo confirm | Saga gating, join 2 event async xử lý race, state bền làm cờ, fan-out topic | 2026-07-22 |
| [010](./010-confirm-timeout-and-compensation-bug.md) | Timeout confirm + vá bug compensation | Tái dùng scheduler sẵn có, bug mutate-rồi-switch, compensation khớp trạng thái | 2026-07-22 |
| [011](./011-shipper-fullname.md) | Thêm fullName cho shipper | Denormalize read-heavy, thay đổi additive + fallback, MapStruct map theo tên | 2026-07-22 |
| [012](./012-livestream-view-count.md) | View count livestream | Cumulative vs concurrent, ước lượng theo data sẵn có, không fake nhãn | 2026-07-22 |
| [013](./013-e2e-test-async-flow.md) | Test e2e luồng async | Poll cho hệ event-driven, ranh giới sync/async, test theo luồng thật | 2026-07-22 |

## Chủ đề kỹ thuật sẽ gặp trong dự án này

Danh sách để bạn hình dung sẽ học gì (đánh dấu khi đã có note):

- [ ] Idempotency cho Kafka consumer (chống xử lý trùng)
- [ ] Outbox pattern & event publishing đáng tin cậy
- [ ] Reactive retry với exponential backoff (Reactor)
- [ ] Transaction boundary & rollback (khi nào `@Transactional` không đủ)
- [ ] Saga pattern cho distributed transaction
- [ ] Redis GEO cho tìm kiếm không gian
- [ ] WebSocket/STOMP broadcast & tối ưu O(N)
- [ ] Constructor injection vs field injection (vì sao)
- [ ] Enum vs String cho domain state (an toàn kiểu)
- [ ] Phân trang & tránh OOM với `findAll()`
- [ ] Bảo mật gateway: JWT verify, header strip chống spoof
- [ ] Fail-fast config & secrets management
- [ ] DLQ & xử lý message lỗi
- [ ] Circuit breaker & rate limiting (Resilience4j)
- [ ] Observability: metrics, tracing, correlation-id
</content>
