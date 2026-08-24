# 002 — Seed script cho test end-to-end

> Ngày: 2026-07-22 · Service: auth, restaurant, shipper, tracking (qua gateway)
> Liên quan: [TESTING_READINESS](../TESTING_READINESS.md), `backend_delivery/scripts/seed.sh`

## Mình đã làm gì
Viết `scripts/seed.sh`: dựng sẵn dữ liệu tối thiểu (khách, chủ nhà hàng + nhà hàng
+ menu, shipper online có vị trí) để lặp lại test luồng đặt hàng nhanh, không phải
click tay qua nhiều API.

## Kỹ thuật quan trọng

### 1. Đọc code để lấy "hợp đồng" API, không đoán
Trước khi viết một dòng curl, mình đọc: request DTO (field name chính xác), path
constant (`ApiPathConstants`), và **nguồn của định danh**. Điểm mấu chốt hay bị bỏ
sót: nhiều endpoint **không nhận shipperId/userId trong body** mà lấy actor từ
Bearer JWT đã được resource service validate qua JWKS (ví dụ
`PATCH /shippers/online-status?isOnline=true` và
`POST /tracking/shipper-locations/update`). Gateway chỉ forward Bearer token và
strip các header legacy `X-User-Id`/`X-Role`; nó không set lại identity header.

### 2. Điều kiện ẩn của match: shipper phải có mặt trong Redis GEO
Đặt đơn không đủ để match tìm ra shipper. Phải: (a) shipper `online-status=true`,
(b) **đẩy vị trí** qua `/tracking/shipper-locations/update` để ghi vào Redis GEO —
match dùng `GEORADIUS` từ điểm lấy hàng. Seed đặt shipper gần nhà hàng để chắc chắn
nằm trong bán kính. Đây là loại "tiền điều kiện vô hình" chỉ lộ ra khi đọc luồng.

### 3. Parse response chịu được nhiều hình dạng
API bọc dữ liệu trong `BaseResponse<>` (`.data.x`) nhưng vài endpoint trả phẳng
(`.x`). Dùng `jq -r '.accessToken // .data.accessToken // empty'` để không phụ thuộc
một dạng — script bền hơn khi format khác nhau giữa service.

### 4. Idempotent & không giòn
Dùng `|| true` cho các bước tạo (register/tạo hồ sơ) để chạy lại nhiều lần không
chết giữa chừng khi dữ liệu đã tồn tại. `set -euo pipefail` cho phần còn lại để lỗi
thật thì dừng sớm.

## Quyết định & đánh đổi
- **Chọn role `USER`/`SHOP_OWNER`/`SHIPPER`** theo chuỗi mà service *downstream*
  kiểm tra (`RoleConstants`), không theo tên "đẹp". Có điểm lệch role đã biết giữa
  auth và service khác → ghi chú thẳng trong script để người test biết chỗ dễ vỡ.
- **Không tự đặt đơn trong seed**: seed chỉ dựng tiền đề; việc đặt đơn để người test
  chủ động chạy (in sẵn lệnh mẫu). Tách "chuẩn bị" khỏi "hành động test".

## Cạm bẫy / lỗi dễ mắc
- Tưởng `POST /orders` là đủ — quên đẩy vị trí shipper → đơn kẹt `FINDING_SHIPPER`.
- Hardcode `localhost:8080` — gateway ở đây là **8079** (`server.port`).
- Quên `Content-Type: application/json` → body không parse.

## Cách kiểm chứng
- `bash -n scripts/seed.sh` (syntax) đã pass.
- Chạy thật sau khi `docker compose up`: kỳ vọng in ra id nhà hàng/menu, "shipper
  online", "vị trí đã cập nhật"; rồi lệnh đặt đơn mẫu tạo được đơn `FINDING_SHIPPER`
  → chuyển `ASSIGNED` khi shipper accept.

## Câu hỏi mở
- Chuỗi role chuẩn cuối cùng là gì (USER vs CUSTOMER)? Cần chốt để bỏ điểm lệch.
- Login có bắt buộc `deviceType`/`deviceId` không? Seed đang gửi tối giản email+password.

## Muốn đào sâu thêm
Từ khoá: "Redis GEOADD/GEORADIUS", "API gateway header propagation JWT claims",
"jq alternative operator //", "bash set -euo pipefail idempotent scripts".
