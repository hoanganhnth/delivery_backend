# 🗺️ Roadmap: Hoàn thiện MVP → Vận hành Production

> Cập nhật: 2026-07-21
> Thay thế cho `SYSTEM_REVIEW.md` (đã lỗi thời từ 2026-04-25)

## 0. Đánh giá lại hiện trạng (verify bằng code, 2026-07-21)

`SYSTEM_REVIEW.md` viết ngày 25/04 và **phần lớn bug P0 trong đó đã được sửa**. Xác minh trực tiếp trên source:

| Vấn đề cũ (P0) | Trạng thái thật hiện tại |
|---|---|
| `getOrdersByRestaurantOwner` trả toàn bộ order | ✅ Đã sửa — có check role + `findByCreatorIdOrderByCreatedAtDesc` + `Pageable` (`OrderServiceImpl.java:230`) |
| Gateway không strip `X-User-Id` → spoof | ✅ Đã sửa — `headers.remove()` trước khi add (`JwtAuthenticationFilter.java:47-48`) |
| Hard-coded internal secret | ⚠️ Đã externalize qua `${INTERNAL_SECRET:...}` nhưng **default fallback vẫn là secret thật** trong `application.properties` |
| `DeliveryCompletedEvent` thiếu `restaurantId` | ✅ Đã thêm field vào các event delivery |
| Settlement thiếu idempotency | ✅ Đã có — check `orderId` trong `TransactionRepository` + listener |
| Tracking realtime | ✅ Raw WebSocket broadcast là contract duy nhất; gRPC đã bị loại khỏi MVP |
| Event field mismatch newStatus/status | ✅ Xử lý bằng fallback getters |
| NPE khi `notes == null` | ✅ Đã có `appendNotes()` null-safe |

**Kết luận kiến trúc:** Thiết kế microservices đi đúng hướng và đã vượt mức prototype — có gateway (JWT RSA), Kafka event-driven, Redis GEO/TTL, raw WebSocket realtime, idempotency ở settlement, và đã mở rộng thêm flashsale / promotion / analytics / search / saga. **Nền tảng ổn.** Khoảng cách còn lại **không phải ở kiến trúc mà ở độ chín vận hành**: observability gần như bằng 0, chưa có rate-limit/circuit-breaker, vài luồng nghiệp vụ chưa khép kín, và một số điểm hiệu năng có thể sập khi tải lớn.

---

## 1. 🟥 MVP — Cần xong để hệ thống chạy trọn vẹn, ổn định

Đây là các mảnh chặn luồng chính hoặc gây lỗi dữ liệu/sập dưới tải thật. Ưu tiên theo thứ tự.

### 1.1 Khép kín luồng giao hàng
- [x] **Loại `DeliveryWaitingService`/Redis waiting dead path.** Canonical timeout
  nằm ở Saga, phát exact expire-offer command rồi rematch; Delivery không còn
  Redis caller/dependency hoặc tự sửa keyspace notification lúc startup.
- [x] **Shipper hủy sau khi đã accept → tự tìm shipper mới.** Delivery reset về
  `FINDING_SHIPPER`, phát `delivery.shipper-rejected`; Saga giữ excluded shipper
  và re-trigger canonical `saga.command.find-shipper`. Legacy
  `delivery.find-shipper` publisher/DTO/constants đã loại.
- [x] **Loại gRPC tracking khỏi MVP** — raw WebSocket là transport duy nhất; dependency, Java skeleton, `.proto` và guide cũ đã xóa.

### 1.2 Chống sập dưới tải
- [x] **`findAll()` → `Pageable` ở `ShipperService`** — admin list dùng bounded `Pageable`; online/rating reads cũng có giới hạn.
- [x] **Match retry không block Kafka consumer thread** — listener trả `Mono`, delay chạy bằng Reactor và Spring Kafka quản lý async ACK; không còn `sleep()`/blocking backoff trên consumer thread. Infrastructure failure dùng retry topic/DLT; runtime restart/order proof vẫn được theo dõi ở Gate B8.
- [x] **Location hot path dùng Redis GEO** — raw WebSocket/REST update chỉ ghi Redis và phát Kafka projection; không có active JPA/MySQL write theo từng ping. Bảng `shipper_locations` cũ không nằm trong realtime write graph.

### 1.3 An toàn dữ liệu & nhất quán
- [x] **Bỏ default internal secret** — không còn `GATEWAY_INTERNAL_SECRET_ABC123` hay secret fallback trong application config; startup/Compose verifier bắt credential explicit.
- [x] **Order persistence dùng `OrderStatus` enum** — entity/service transition dùng enum; String chỉ còn ở HTTP/event adapter và được parse fail-closed.
- [x] **Delivery migration có `restaurant_id`** — Flyway V1 tạo cột và entity map `@Column(name = "restaurant_id")`; migration/schema validation đã có test.

### 1.4 Vệ sinh code tối thiểu
- [x] Service runtime không còn `System.out`; output trực tiếp chỉ còn ở CLI/probe scripts. `GrpcServerRunner` đã bị xóa cùng gRPC tracking.
- [x] Restaurant/Menu controller dùng constructor injection.
- [x] `deactivateSessions` dùng một bulk update query `deactivateActiveSessionsForDevice`, không còn `save()` trong loop.

---

## 2. 🟦 Production — Nâng cấp để vận hành chuyên nghiệp, scale thật

Sau khi MVP chạy ổn, đây là các mảng đưa hệ thống từ "chạy được" lên "vận hành được ở quy mô lớn".

### 2.1 Observability (đang là lỗ hổng lớn nhất — hiện gần như không có)
- [ ] Thêm **Spring Boot Actuator** vào tất cả service (health, readiness, liveness) — hiện **không service nào có actuator**.
- [ ] **Metrics**: Micrometer + Prometheus, dựng **Grafana** dashboard (throughput, latency p95/p99, Kafka lag, error rate). docker-compose hiện **chưa có** prometheus/grafana.
- [ ] **Distributed tracing**: OpenTelemetry / Zipkin — trace một đơn hàng đi xuyên order → delivery → match → settlement.
- [ ] **Log tập trung**: correlation-id (trace-id) xuyên service + đẩy về ELK/Loki thay vì log rời rạc.

### 2.2 Khả năng chịu lỗi (resilience)
- [ ] **Rate limiting ở Gateway** — dùng `RequestRateLimiter` (Redis) chống abuse/DDoS.
- [ ] **Circuit breaker** — Resilience4j cho các call HTTP đồng bộ (match→tracking, auth→user) để lỗi không lan truyền.
- [ ] **Dead Letter Queue** cho Kafka consumers — hiện chỉ có `FixedBackOff(1000,3)`, message lỗi hết retry sẽ mất. Cần DLQ + alert.
- [ ] **Retry/backoff chuẩn hoá** và **idempotency mở rộng** ra các consumer khác ngoài settlement (order, delivery, notification).

### 2.3 Service discovery & cấu hình
- [ ] **Hoàn tất Eureka** — gateway đã có `eureka-client` dep nhưng route vẫn khai báo trong `application.properties` (localhost). Chuyển sang `lb://service-name` và đăng ký toàn bộ service với registry.
- [ ] **Config tập trung** (Spring Cloud Config / Consul) thay cho `application.properties` rải rác.
- [ ] **Secrets management** thật (Vault / AWS Secrets Manager / K8s Secrets) — không để secret trong repo.

### 2.4 Bảo mật nâng cao
- [ ] Forgot/reset password, email verification, OAuth2 social login ở auth-service.
- [ ] Refresh token rotation + revocation list.
- [ ] Input validation & audit log cho các API quản trị (block user, duyệt withdrawal).

### 2.5 Testing & CI/CD
- [ ] **Integration test end-to-end** cho luồng đặt hàng (Testcontainers: Kafka + Redis + MySQL).
- [ ] Contract test cho các Kafka event (tránh mismatch field như từng xảy ra).
- [ ] Pipeline CI: build + test + security scan; CD với health-check rollout.

### 2.6 Data & scale
- [x] Chiến lược **DB per service** rõ ràng + backup/restore — backup PostgreSQL
  theo service được mã hóa/checksum, giữ Kafka recovery metadata, restore chỉ vào
  database cô lập và có rehearsal fixture/reconciliation/smoke.
- [x] Index review cho các query nóng (orders theo creator/shipper/status) — có
  PostgreSQL `EXPLAIN ANALYZE` trước/sau, migration tối thiểu, query bounded và
  regression N+1/outbox/settlement.
- [x] Tối ưu WebSocket broadcast — exact room theo `deliveryId`, authorized
  audience, Redis Pub/Sub cross-instance và bounded coalescing backpressure;
  socket contract/generation fence/tombstone được giữ nguyên.
- [x] Location history store — Kafka consumer ghi async vào `tracking_db`,
  sampling 10 giây/25 m, idempotent replay, retention 90 ngày và chỉ có API
  support/admin theo một delivery.

### 2.7 Hoàn thiện nghiệp vụ (nice-to-have production)
- [ ] Rating & review shipper/restaurant, và dùng rating trong thuật toán match (hiện chỉ match theo khoảng cách).
- [ ] Trạng thái availability shipper (`IDLE`/`ON_DELIVERY`/`OFFLINE`) + filter shipper đang bận khi match.
- [ ] Refund khi hủy đơn (settlement), notification preferences, viewer count/chat cho livestream.

---

## 3. Thứ tự đề xuất

1. **Tuần 1–2 (MVP block):** WaitingService integration, shipper-cancel→rematch, match thread không block, ShipperService pagination, đổi default secret.
2. **Tuần 3–4 (MVP polish):** Order status enum, raw WebSocket rehearsal, logger cleanup, migration check.
3. **Tháng 2 (Production nền tảng):** Actuator + Prometheus/Grafana + tracing, rate limit + circuit breaker + DLQ, hoàn tất Eureka.
4. **Tháng 3+ (Production chín):** Secrets management, integration/contract test + CI/CD, tối ưu WebSocket & data, nghiệp vụ nâng cao.
</content>
