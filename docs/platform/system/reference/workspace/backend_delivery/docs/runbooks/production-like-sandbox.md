# Production-like local sandbox

## Mục đích

Sandbox này chạy dữ liệu tổng hợp nhưng giữ nguyên đường đi production:

```text
Scenario Lab / delivery_app
  -> API Gateway
  -> Auth + resource services
  -> Kafka / Saga Orchestrator
  -> Match (Redis GEO + H3 rolling dispatch)
  -> Delivery (single hoặc batch offer)
  -> Tracking (location updates)
  -> delivery.completed / Settlement
```

Sandbox không phải staging và không được dùng credential, database, Kafka
cluster hoặc địa chỉ khách thật.

Để sandbox tổng hợp chạy ổn định trong giới hạn Docker Desktop, overlay chỉ cho
Sandbox hạ JVM của API/resource service xuống `Xms=48 MiB`/`Xmx=160 MiB`, còn
control plane giữ `Xms=64 MiB`/`Xmx=192 MiB`.
Kafka giữ heap tối đa 384 MiB để log cleaner khởi động được, còn Elasticsearch
xuống heap tối đa 128 MiB; đồng thời tắt ML/monitoring/
watcher/GeoIP downloader không dùng trong lab. Elasticsearch có cgroup 1.5 GiB
và Match/Restaurant/Notification 768 MiB để tránh native-memory startup spikes.
Image được build tuần tự trước khi khởi động process, và resource service cũng
khởi động tuần tự để tránh đỉnh bộ nhớ lúc cold start. Compose canonical vẫn giữ
sizing và cách khởi động mặc định; đây không phải thay đổi capacity hay topology
cho staging/production.

## Khởi động nhanh

Từ `backend_delivery/`:

```bash
bash scripts/sandbox-up.sh
```

Lệnh này sẽ:

1. tạo RSA/JWT, internal secret, database password và simulator token riêng
   dưới `.sandbox/<run-id>/`;
2. package các JAR cần thiết;
3. tạo Compose project, network và PostgreSQL/Kafka volume mới;
4. khởi động control plane, data plane, observability và core services;
5. bật H3, rolling batch scheduler, Delivery batch và Saga batch capability;
6. seed customer, owner, restaurant, menu item và shipper tổng hợp qua Gateway;
7. tạo các scenario JSON mode `0600`.

Không có host port cố định cho Gateway/simulator; chỉ bind vào `127.0.0.1`.
Prometheus và Grafana cũng được bind loopback bằng port động để quan sát
metrics/traces mà không mở ra mạng LAN. Port thật được ghi trong state file và
in ra sau khi startup thành công.

Nếu chỉ muốn dùng JAR đã package sẵn (image của sandbox vẫn được materialize
tuần tự trước khi runtime khởi động):

```bash
SANDBOX_SKIP_BUILD=true bash scripts/sandbox-up.sh
```

Tuỳ chọn này vẫn cần artifact hợp lệ theo Dockerfile. Khi source đã thay đổi,
hãy package lại.

## Chạy scenario

Không cần copy token ra terminal:

```bash
bash scripts/sandbox-run.sh happy
bash scripts/sandbox-run.sh restaurant-reject
bash scripts/sandbox-run.sh no-shipper
bash scripts/sandbox-run.sh shipper-reject
bash scripts/sandbox-run.sh offer-timeout
bash scripts/sandbox-run.sh customer-cancel
bash scripts/sandbox-run.sh customer-cancel-after-accept
bash scripts/sandbox-run.sh shipper-disconnect
bash scripts/sandbox-run.sh network-delay
bash scripts/sandbox-run.sh human-order  # chờ người test đặt từ delivery_app
```

`happy` đi qua COD, matching và giao hàng; nếu batch capability đang bật thì
runner nhận `GET /api/deliveries/offers/current-batch` và gọi
`POST /api/deliveries/batch/accept` (một item vẫn là batch contract thật).

`restaurant-reject` kiểm tra compensation khi nhà hàng từ chối ở `PENDING`.
`no-shipper` để shipper offline và chờ trạng thái `SHIPPER_NOT_FOUND` theo
matching deadline/retry của sandbox.
`shipper-reject`, `offer-timeout`, `customer-cancel`,
`customer-cancel-after-accept`, `shipper-disconnect` và `network-delay` lần
lượt kiểm tra rematch, expiry, customer compensation sau offer/assignment,
offline và retry Gateway read-poll. `customer-cancel-after-accept` gọi
`PUT /api/orders/{id}/cancel` bằng customer actor khi Delivery đã `ASSIGNED`;
nó không dùng shipper `cancel-assignment`. `network-delay` inject một HTTP 429
một lần vào poll của runner; nó không mô phỏng timeout mơ hồ sau khi server đã
commit mutation.
`human-order` không tự tạo đơn; runner chờ một đơn mới từ `delivery_app` rồi
điều khiển owner/shipper tổng hợp qua các API thật.

Theo dõi run trực tiếp:

```bash
bash scripts/sandbox-status.sh
```

Scenario Lab API và Kafka decision trace vẫn là read-only observer đối với
business state; token không xuất hiện trong snapshot/SSE response.

Mỗi snapshot run trả thêm hai mảng để console/web admin giải thích quyết định:

- `algorithmTraces`: quyết định thực tế, candidate sau GEO filter, lý do loại,
  điểm chọn và latency do Match phát ra.
- `algorithmComparisons`: kết quả replay `SHADOW`; hiện có
  `eta-distance/v1` và `balanced-eta/v1`. Profile `balanced-eta` tính ETA từ
  candidate trace và tốc độ cấu hình rồi cộng penalty nhỏ theo
  `completedDeliveries` trong scenario (mặc định `0`), giúp quan sát trade-off
  ETA/fairness.
  Đây chỉ là khuyến nghị (`recommendedShipperId`), không đổi
  `actualSelectedShipperId` hay trạng thái Delivery.

Web chỉ đọc `GET /api/simulator/runs/{runId}` hoặc SSE run stream qua Gateway;
không gọi Match service, Redis hay Kafka trực tiếp.

## Controlled active canary: `balanced-eta/v1`

Match projects canonical `delivery.completed` events idempotently using the
delivery `eventId`. Counters are isolated between REAL traffic and every
simulation run (`simulation:<runId>`). When active, Match decision traces add
`completedDeliveries` and `combinedScoreMinutes` for every candidate.

The active profile is fail-closed by default:

```properties
MATCHING_BALANCED_ETA_ENABLED=false
MATCHING_BALANCED_ETA_CANARY_PERCENT=0
MATCHING_BALANCED_ETA_FAIRNESS_PENALTY_MINUTES=0.03
MATCHING_BALANCED_ETA_SPEED_KM_PER_MINUTE=0.5
```

Use `ENABLED=true` and `CANARY_PERCENT=100` only for an isolated simulator
experiment. For REAL traffic, deploy config through the usual rollout process
and start with a bounded percentage after reviewing shadow comparisons. Profile
selection uses the stable Match command event ID, so retries cannot move a
command between profiles.

Rollback: deploy `CANARY_PERCENT=0` (or `ENABLED=false`). Existing counters
remain harmless, and any Match command already staged keeps its durable result.

For a fair-allocation replay, set `SANDBOX_ORDER_COUNT` (maximum 20) when
generating a `happy` scenario. The runner processes those orders sequentially
under one actor binding and one simulation namespace, and returns an `orders`
array plus all corresponding traces. This benchmark accepts only the happy
path: `HUMAN_ORDER` and triggers are rejected for `orderCount > 1`, avoiding
ambiguous repeat semantics. Increase `SANDBOX_RUN_TIMEOUT_SECONDS` for a larger
order set; simulation actors are deliberately not reused after an aborted run
that has a non-terminal delivery. The worker will attempt cleanup cancellation
only before pickup; otherwise it quarantines the actor lease and retains the
Auth binding for manual Delivery reconciliation. Do not clear the binding or
reuse that actor merely because the simulator run is `ABORTED`.

After Delivery has been independently confirmed terminal, an ADMIN can invoke
`POST /api/simulator/runs/{runId}/reconcile`. The endpoint reads durable run
journal delivery IDs, probes their current status through Gateway with a
short-lived re-bound run token, and unbinds actors only when every delivery is
terminal. It is safe to repeat; a non-terminal delivery leaves every fence in
place. A successful reconciliation also changes the corresponding simulator
lease rows to `RELEASED`, so the quarantine alert reflects only work still
requiring recovery.

For legacy runs whose append-only journal lacks a delivery ID, reconciliation
falls back to Delivery's private run-scoped lookup. It exposes only delivery
ID, order ID and status, requires the same internal secret, and remains
fail-closed when no terminal Delivery projection exists.

`DELETE /api/simulator/runs/{runId}` is idempotent for a durable terminal run
after restart. It only removes transient console state; it never clears Auth
bindings or quarantined leases. Use reconciliation first for `FAILED` or
`ABORTED` runs that still own an actor fence.

In `delivery_simulator_web`, a backend-mode `FAILED` or `ABORTED` run exposes
the **Reconcile Fences** control. It invokes the same ADMIN API and displays
the verified delivery statuses plus released actor count; a refusal leaves the
fence intact and reports why.

Prometheus exposes aggregate rollout volume as
`delivery_matching_algorithm_decisions_total{algorithm,version,execution_mode}`.
It deliberately excludes order, delivery, run and shipper IDs, so it is safe
for a long-lived dashboard. The counter increments only after Match has
persisted its terminal decision trace, not for each retry attempt. Use it with
decision traces to compare the number of `nearest-cod` and `balanced-eta`
decisions; it is not a substitute
for acceptance/cancellation or delivery-time quality analysis.

## Dùng console bản đồ

Console nằm ở `delivery_simulator_web/` và phải chạy backend mode:

```bash
cd ../delivery_simulator_web
VITE_SIMULATOR_MODE=backend \
VITE_SIMULATOR_API_BASE_URL=http://127.0.0.1:<simulator-port>/api/simulator \
npm run dev
```

Nếu console bật token protection, lấy token mà không echo ra terminal:

```bash
VITE_SIMULATOR_MODE=backend \
VITE_SIMULATOR_API_BASE_URL=http://127.0.0.1:<simulator-port>/api/simulator \
VITE_SIMULATOR_API_TOKEN="$(sed -n 's/^SANDBOX_SIMULATOR_API_TOKEN=//p' \
  ../backend_delivery/.sandbox/<run-id>/state.env)" \
npm run dev
```

Token runner được giữ trong state file local. Với console cần auth header,
đặt `VITE_SIMULATOR_API_TOKEN` từ state file trong shell riêng; không commit
giá trị này và không dùng query-string token.

Muốn dùng `delivery_app` hoặc `shipper_app2` thật, cấu hình API base của app về
Gateway URL được in bởi `sandbox-up.sh` (ví dụ
`http://127.0.0.1:<gateway-port>`). Không trỏ app vào các port service nội bộ;
mọi request phải đi qua Gateway như production.

## Dừng và reset

Giữ database/Kafka volume để xem lại log hoặc chạy tiếp:

```bash
bash scripts/sandbox-down.sh
```

Xoá đúng sandbox hiện tại (không chạm canonical project):

```bash
SANDBOX_PURGE=true SANDBOX_DELETE_STATE=true bash scripts/sandbox-down.sh
```

Các script đều từ chối project `backend_delivery`, volume không có prefix
`delivery_sandbox_`, hoặc state ngoài `.sandbox/`.

## Cờ vận hành

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `SANDBOX_BATCH_ENABLED` | `true` | bật Delivery/Match batch path |
| `SANDBOX_BATCH_SCHEDULER_ENABLED` | `true` | mở rolling dispatch round |
| `SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED` | `true` | Saga phát capability batch |
| `SANDBOX_H3_ENABLED` | `true` | lưu/tra cứu vùng H3 |
| `SANDBOX_RUN_SCENARIO` | `false` | tự chạy scenario happy sau seed |
| `SANDBOX_RUN_ID` | timestamp + pid | đặt tên run để dễ truy vết |
| `SANDBOX_STARTUP_TIMEOUT_SECONDS` | `1500` | ngân sách cold start tuần tự của Docker sandbox |
| `SANDBOX_SKIP_IMAGE_BUILD` | `false` | dùng image đã build đúng project/run; thiếu image sẽ fail closed |
| `SANDBOX_RETAIN_ON_FAILURE` | `false` | giữ container/volume sandbox để chẩn đoán, phải dọn bằng `sandbox-down.sh` |
| `SANDBOX_MATCHING_MAX_RETRY_ATTEMPTS` | `3` | retry Saga ngắn cho scenario lab |

So sánh legacy single-offer:

```bash
SANDBOX_BATCH_ENABLED=false \
SANDBOX_BATCH_CLIENT_CAPABILITY_ENABLED=false \
bash scripts/sandbox-up.sh
```

## Điều chưa được coi là production proof

- Kafka/PostgreSQL/Redis concurrency thật, multi-AZ, rolling upgrade, load và
  chaos chưa được chứng minh bằng sandbox đơn máy.
- Dữ liệu settlement được seed bằng fixture local để có COD capacity; không
  phải payment provider thật.
- Mobile device E2E, GPS spoof/fraud, FCM và network partition cần test riêng.
- `no-shipper` có thể mất thời gian theo retry/deadline; giảm các biến
  `MATCHING_INITIAL_*` khi cần scenario nhanh.

Kết quả runtime phải được báo cáo tách biệt: đã chạy sandbox smoke, chưa suy
diễn thành production readiness.
