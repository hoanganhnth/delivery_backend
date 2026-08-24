# Execution Plan: Delivery Scenario Lab & Multi-Shipper Simulator

Date: 2026-08-18

## Status

Active — MVP S0–S3 implementation complete; baseline Decision Trace vertical
slice and a runnable production-like synthetic sandbox are implemented.
Multi-order fixture provisioning, durable observer storage and
shadow-algorithm comparison remain follow-up phases.

## Outcome

Xây dựng bộ công cụ **Delivery Scenario Lab** gồm:
1. **`backend_delivery/simulator-service`**: Microservice / Scenario Runner độc lập trong môi trường test cô lập, có khả năng quản lý actor ảo (nhiều shipper, customer, restaurant), điều khiển di chuyển GPS thời gian thực qua WebSocket/REST, lắng nghe sự kiện qua Kafka/DB để phân tích matching decision và kiểm thử các ma trận huỷ/lỗi (cancellation, rejection, timeout, rematch, contention).
2. **`delivery_simulator_web/`**: Web console độc lập (React + Vite + Leaflet map) cho phép cấu hình kịch bản trực quan, theo dõi vị trí live của nhiều shipper trên bản đồ, xem timeline liên kết các sự kiện từ Gateway/Kafka/Saga/Delivery/Settlement và báo cáo assertion tự động.

## Context

- Thiết kế tổng thể simulator: `docs/system/simulator/README.md`
- Thuật toán & luồng matching: `backend_delivery/match-service/`
- Trạng thái đơn và vòng đời delivery: `backend_delivery/docs/workflows/order_lifecycle_flow.md`
- Kịch bản kiểm thử COD và failure matrix hiện hữu:
  - `backend_delivery/scripts/verify-mvp-cod-flow.sh`
  - `backend_delivery/scripts/verify-mvp-failure-matrix.sh`
- Isolated compose setup: `backend_delivery/docker-compose.isolated-e2e.yml`

## Scope

In scope:
- Quản lý kịch bản với 2 chế độ: `HUMAN_ORDER` (chờ người test thật đặt qua `delivery_app`) và `SIMULATED_ORDER` (runner tự tạo).
- Cấu hình nhiều shipper đồng thời: toạ độ ban đầu, số dư ký quỹ COD, trạng thái online/offline, cấu hình hành vi (ACCEPT, REJECT, TIMEOUT, CANCEL, MOVE).
- Mô phỏng di chuyển GPS của shipper qua WebSocket `/ws/tracking` hoặc API `/api/tracking/shipper-locations/update`.
- Đánh giá tính đúng đắn của Matching Engine: kiểm tra việc ưu tiên shipper gần nhất đủ điều kiện, xử lý khi từ chối, chuyển candidate tiếp theo, chống double-assign.
- Đánh giá ma trận huỷ đơn: huỷ ở các giai đoạn (Pending, Confirmed, Finding Shipper, Assigned), từ chối huỷ sau khi đã Picked Up.
- Web Console: Bản đồ trực quan, Builder kịch bản, Live Event Stream (SSE), Bảng phân tích Matching Candidate, Báo cáo Assertion.

Out of scope:
- Chạy trực tiếp trên môi trường Production hoặc dùng chung database/Kafka với Production.
- Thay đổi logic core của `match-service` hay `delivery-service` (simulator đóng vai trò kiểm thử và quan sát black-box/grey-box thông qua API và Kafka observer).
- Tích hợp cổng thanh toán trực tuyến bên thứ 3 thật (VNPay/Momo sandbox) trong simulator (tập trung vào COD và ví nội bộ).

## Approach

1. **Phase S0 (Foundations & Safety):**
   - Xác định ranh giới cách ly môi trường (isolated Compose / test namespace).
   - Thiết lập data contract cho Scenario Configuration & Assertion Report.
2. **Phase S1 (Backend Simulator Core & Actor Engine):**
   - Xây dựng `simulator-service` (Spring Boot 3.x).
   - Triển khai Fixture Provisioner (tạo N shipper, nạp số dư COD, đặt vị trí ban đầu).
   - Triển khai Shipper Virtual Agent (duy trì token, kết nối WebSocket tracking, nhận và phản hồi offer, cập nhật vị trí di chuyển).
3. **Phase S2 (Observer & Assertion Engine):**
   - Triển khai Kafka Event Listener riêng biệt để theo dõi luồng sự kiện end-to-end.
   - Xây dựng Assertion Engine kiểm tra các invariant (Matching correctness, Financial ledger, State convergence).
   - Xây dựng SSE Stream endpoint đẩy trạng thái thời gian thực về UI.
4. **Phase S3 (Frontend Simulator Web Console):**
   - Khởi tạo project `delivery_simulator_web/` (React + Vite + Tailwind CSS + Leaflet).
   - Xây dựng màn hình Scenario Builder (chọn vị trí trên map, thêm N shipper, đặt tham số).
   - Xây dựng màn hình Live Run Observer (bản đồ trực tiếp xe máy di chuyển, timeline sự kiện, kết quả assertion).
5. **Phase S4 (Advanced Scenarios & Failure Matrix):**
   - Tích hợp đầy đủ các kịch bản huỷ đơn, shipper reject, timeout 180s, mất mạng đột ngột và tranh chấp nhiều đơn (contention).

## Risks And Recovery

- **Rủi ro rò rỉ vào production:** Simulator tuyệt đối không được cấu hình trỏ vào DB/Kafka production.
  *Phòng ngừa:* Thêm kiểm tra biến môi trường nghiêm ngặt (Fail-fast nếu `SPRING_PROFILES_ACTIVE` chứa `prod` hoặc URL DB chứa domain production).
- **Rủi ro nghẽn tải WebSocket/Kafka khi có nhiều shipper ảo:**
  *Phòng ngừa:* Coalesce vị trí trên frontend (throttle render 200-500ms), giới hạn tối đa 50 shipper ảo trong 1 kịch bản local test.
- **Rủi ro dọn dẹp dữ liệu thừa:**
  *Phòng ngừa:* Mỗi run gán một `runId` duy nhất (UUID), mọi fixture và token đều được dọn dẹp khi bấm Clean hoặc kết thúc run.

## Progress

- [x] Soạn thảo kế hoạch chi tiết và tài liệu kiến trúc.
- [x] Khởi tạo module `backend_delivery/simulator-service` với safety guard, REST control API và SSE.
- [x] Runner gọi Gateway thật cho checkout quote/idempotent order, restaurant, offer accept/reject, cancellation, delivery status và Tracking REST; hỗ trợ `HUMAN_ORDER`/`SIMULATED_ORDER`.
- [x] Cấu hình nhiều shipper, hành vi accept/reject/timeout/cancel-after-accept, trigger huỷ/từ chối/disconnect/delay, candidate oracle và redacted snapshots.
- [ ] Triển khai Fixture Provisioner & Virtual Shipper Agent đầy đủ; MVP hiện nhận token/profile đã provisioned và điều khiển actor qua Gateway/Tracking REST.
- [ ] Triển khai Event Observer & Assertion Engine đầy đủ; MVP hiện poll state qua Gateway, chưa đọc Kafka/DB ledger.
- [x] Decision Trace vertical slice: Match emits a versioned, read-only trace for the current `nearest-cod-v1` path; simulator observer consumes it and the console renders the real candidate decisions. The trace is staged only after the durable business result and never participates in reservation/assignment success. It includes stage/total latency, actual search-attempt count and durable-candidate resume metadata; early Kafka delivery is held briefly until the runner learns order/delivery IDs.
- [x] Runnable isolated production-like sandbox: `docker-compose.sandbox.yml`, run-scoped secrets/volumes, Gateway-driven seed, happy/reject/no-shipper scenarios, safe up/run/status/down scripts, and one-item batch offer recovery.
- [x] Disposable Docker smoke completed on 2026-08-23: the batch no-shipper
  path reached `SHIPPER_NOT_FOUND` in both projections with one durable Match
  terminal outbox row; simulator polling treats Gateway `429` as transient
  read backpressure without weakening Gateway policy.
- [ ] Shadow algorithm registry and baseline-vs-candidate comparison; no candidate algorithm may mutate a real offer until an explicit rollout policy is approved.
- [x] Khởi tạo `delivery_simulator_web`, nối backend mode vào runner và giữ mock mode explicit cho demo offline.
- [ ] Kiểm thử toàn diện kịch bản Multi-Shipper và Failure Matrix.

## Validation

- `mvn -q -pl simulator-service -am test` — PASS.
- `mvn -q -pl match-service -am clean test` — PASS for all available Match tests (Docker/Testcontainers-dependent suites are skipped when no Docker daemon is present); the previous stale-classpath `NoClassDefFoundError` does not reproduce after clean build.
- `mvn -q -pl match-service -am -DskipTests compile` — PASS after adding trace schema/stage timing and replay metadata.
- `mvn -q -pl simulator-service -am test` — PASS with observer validation, strict identity correlation and duplicate-trace coverage.
- Frontend `tsc --noEmit` và Vite production build — PASS.
- `./scripts/verify-http-api-inventory.sh` — PASS after registering the algorithm-traces endpoint (174 mapped methods).
- `bash -n scripts/provision-kafka-resilience-topics.sh` và `bash -n scripts/verify-kafka-resilience-topics.sh` — PASS.
- `node docs/system/api/generate-http-contract.mjs --write` + `node docs/system/verify-docs.mjs` — PASS; source-derived HTTP contract and reference bundle are synchronized at 174 operations.
- `bash -n scripts/scenario-lab-runner.sh`, HTTP inventory, Compose config — PASS.
- `bash scripts/verify-sandbox-config.sh` and all sandbox shell syntax checks — PASS; isolated Docker startup plus happy/reject/no-shipper smoke passed on the run-scoped sandbox documented above.
- Source-derived HTTP contract, documentation lint và offline reference bundle — PASS.
- Enabled runtime smoke: service booted on an isolated port; invalid `POST /validate` returned a structured `valid=false` response, invalid `POST /runs` returned HTTP 400, a configured console CORS preflight returned HTTP 200, and shutdown was graceful.

Kafka topic rehearsal/full isolated E2E has not run yet because it needs a disposable Docker/Compose stack,
real restaurant/menu IDs and provisioned customer/owner/shipper tokens. Do not
claim delivery or settlement behavior from the static/unit/runtime smoke alone.

## Decisions

- 2026-08-18: Tách Frontend Web Console thành một ứng dụng riêng (`delivery_simulator_web`) thay vì nhúng vào `delivery_web` để đảm bảo an toàn tuyệt đối, tránh bundle công cụ test vào Admin Production.
- 2026-08-18: Hỗ trợ cả 2 chế độ đặt hàng `HUMAN_ORDER` (dành cho người test cầm app thật) và `SIMULATED_ORDER` (dành cho tự động hoá CI/CD).
- 2026-08-20: MVP dùng access token của các actor đã provisioned trong isolated environment; runner không tự tạo account/deposit và không expose token trong response/SSE.
- 2026-08-20: Location agent dùng Tracking REST update trong MVP để tránh tạo client WebSocket server-side sớm; WebSocket publisher/reconnect vẫn là phase S4.
- 2026-08-21: Candidate panel được seed như scenario oracle và ghi rõ giới hạn; offer/assignment thật chỉ cập nhật row quan sát được. Sau mỗi cancellation/rejection trigger runner poll lại trước action tiếp theo để tránh auto-confirm hoặc giao hàng từ snapshot cũ.
- 2026-08-21: SSE chỉ nhận `X-Simulator-Token`, không hỗ trợ token trong query string.
- 2026-08-21: Gateway target chỉ nhận HTTP(S), host allowlist test-only hoặc explicit non-local test target; host có dấu hiệu production/staging luôn bị từ chối.
- 2026-08-21: Direct browser calls được giới hạn bằng CORS origin allowlist test-only; không bật wildcard hoặc credentials.
- 2026-08-21: Algorithm explainability uses structured Decision Trace events, not source-code execution in the browser. `nearest-cod-v1` remains the authoritative active algorithm for this slice; shadow evaluation is read-only and test-only.
- 2026-08-21: `matching.decision-trace` is a source-only, read-only topic with a dedicated simulator consumer group and no retry/DLT topology. The Match outbox writes it after `shipper.found`/`shipper.not-found`; a missing trace is allowed to reduce observability but cannot change business state.
- 2026-08-21: Simulator correlates a trace only when every identity already known by the run agrees. A bounded 20-minute pending buffer covers Kafka-before-poll ordering; duplicate trace delivery is idempotent in the run timeline.
