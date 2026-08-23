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

Nếu chỉ muốn dùng JAR/image đã có:

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
bash scripts/sandbox-run.sh human-order  # chờ người test đặt từ delivery_app
```

`happy` đi qua COD, matching và giao hàng; nếu batch capability đang bật thì
runner nhận `GET /api/deliveries/offers/current-batch` và gọi
`POST /api/deliveries/batch/accept` (một item vẫn là batch contract thật).

`restaurant-reject` kiểm tra compensation khi nhà hàng từ chối ở `PENDING`.
`no-shipper` để shipper offline và chờ trạng thái `SHIPPER_NOT_FOUND` theo
matching deadline/retry của sandbox.
`human-order` không tự tạo đơn; runner chờ một đơn mới từ `delivery_app` rồi
điều khiển owner/shipper tổng hợp qua các API thật.

Theo dõi run trực tiếp:

```bash
bash scripts/sandbox-status.sh
```

Scenario Lab API và Kafka decision trace vẫn là read-only observer đối với
business state; token không xuất hiện trong snapshot/SSE response.

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
| `SANDBOX_STARTUP_TIMEOUT_SECONDS` | `900` | ngân sách cold start Docker |
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
