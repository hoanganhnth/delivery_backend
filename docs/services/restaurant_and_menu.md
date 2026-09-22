# 🍔 Restaurant & Menu Management

## 1. Đặc tả (Specification)
**Mục tiêu:** Quản lý toàn bộ thông tin nhà hàng, danh mục (category) và danh sách món ăn. Cung cấp API truy xuất nhanh cho khách hàng và API quản trị cho chủ nhà hàng (Merchant) & Admin.

**Microservices liên quan:**
- `restaurant-service`: Core service xử lý logic CRUD, tính toán khoảng cách.
- **Data Stores:** PostgreSQL (dữ liệu gốc), Redis (Cache danh sách nhà hàng/món ăn để tăng tốc độ phản hồi).

## 2. Danh sách Use Cases

### Ownership, lifecycle và public catalogue (2026-09-22)

- Menu kế thừa quyền từ Restaurant. `principalId` là identity chính; legacy ID
  chỉ áp dụng với Restaurant chưa có `ownerPrincipalId`, khi enforcement tắt.
- Management list và page, có hoặc không có `restaurantId`, đều kiểm tra role
  và ownership tại service. ADMIN xem toàn bộ; SHOP_OWNER chỉ xem tài nguyên của mình.
- Các route public `/api/menu-items/restaurant/{id}` và `/available`, kể cả
  biến thể `/page`, chỉ trả `AVAILABLE`. Muốn xem SOLD_OUT/DISCONTINUED dùng
  `/api/menu-items/my-menu-items` hoặc `/my-menu-items/page` đã xác thực.
- `DELETE /api/restaurants/{id}` và `DELETE /api/menu-items/{id}` là alias
  archive idempotent, không xóa vật lý. Restaurant chuyển sang `ARCHIVED`, Menu
  chuyển sang `ARCHIVED`; archive Restaurant không tự đổi trạng thái Menu.
- Public Restaurant list/page/search ẩn `ARCHIVED`; public Menu chỉ trả
  `AVAILABLE` khi Restaurant cha chưa archive. Detail Restaurant theo ID và các
  management list vẫn giữ dữ liệu lịch sử.
- Restaurant response bổ sung `lifecycleStatus`, `version`, `timeZone`; Menu
  response bổ sung `version`. Đây là field additive. Lifecycle PATCH đã có ở
  `/api/restaurants/{id}/lifecycle` và `/api/menu-items/{id}/lifecycle`; body
  nhận `targetStatus` và `expectedVersion` tùy chọn.
- Client từng dùng public route để quản trị phải chuyển sang management route.
  Không dựa vào ID do client gửi để cấp quyền.
- Flutter đọc tọa độ `latitude`/`longitude` trước legacy `addressLat/addressLng`;
  canonical null không fallback sang giá trị cũ.
- Unit/integration H2 và MockMvc có bằng chứng; chưa thay thế kiểm chứng live
  PostgreSQL/JWKS/Gateway/BFF trước release.

| Mã UC | Tên Use Case | Nền tảng | Trạng thái |
|-------|--------------|----------|------------|
| UC-2.1 | Xem danh sách nhà hàng (Featured, Nearby) | Customer App | ✅ Done |
| UC-2.2 | Xem chi tiết nhà hàng & Menu món ăn | Customer App | ✅ Done |
| UC-2.3 | Đánh giá nhà hàng (Rating & Review) | Customer App | ✅ Done |
| UC-2.4 | Admin Quản lý Nhà Hàng (CRUD) | Admin Web | 🔧 Partial |
| UC-2.5 | Merchant Quản lý Menu, Giá, Ảnh | Admin Web | 🔧 Partial |

## 3. Luồng nghiệp vụ (Business Flow)

### 3.1. Luồng truy xuất và Cache dữ liệu (Read-Through Cache)
Vì tần suất khách hàng xem danh sách nhà hàng và menu là cực kỳ lớn, `restaurant-service` sử dụng Redis để giảm tải cho Database.
1. Khi App gọi API lấy danh sách Menu của nhà hàng A, Service sẽ check Key tương ứng trong Redis trước.
2. **Cache Hit:** Trả dữ liệu ngay lập tức.
3. **Cache Miss:** Query PostgreSQL, lưu kết quả vào Redis (có set TTL, ví dụ 1 giờ), sau đó trả về cho App.

### 3.2. Luồng Invalidation (Cập nhật dữ liệu)
Khi Merchant hoặc Admin thực hiện thay đổi trên món ăn (Ví dụ: Đổi giá, cập nhật trạng thái "Hết hàng"):
1. Service cập nhật PostgreSQL và ghi Search outbox trong cùng transaction.
2. Chỉ sau khi transaction commit, service mới cache/evict Redis. Rollback không
   được phép tạo cache từ dữ liệu chưa tồn tại; lỗi Redis sau commit là best
   effort và không đảo ngược business commit.
3. Outbox relay publish `entity-sync` lên Kafka. Lời gọi Search trong business
   transaction chỉ lưu outbox row, không gửi broker trực tiếp.

### 3.3. Dữ liệu lifecycle và lịch sử

- Migration V10 backfill Restaurant cũ thành `ACTIVE`, version `0`, timezone
  `Asia/Ho_Chi_Minh`; Menu cũ có version `0`.
- Restaurant/Menu dùng optimistic version ở persistence. Nếu gửi
  `expectedVersion` không khớp, API trả `409 STALE_VERSION`; nếu chưa gửi thì
  vẫn tương thích ngược và tăng metric
  `delivery.catalog.expected_version.missing`. Lifecycle mutation lưu audit
  whitelist (actor, role, aggregate, before/after state, versions, timestamp,
  correlation ID) và search outbox trong cùng transaction PostgreSQL.
- Physical purge và retention policy chưa có authority, vì vậy normal business
  flow không xóa Restaurant/Menu.

### 3.4. Tính toán khoảng cách (Geolocation)
- Vị trí của nhà hàng (Lat/Lng) được lưu tĩnh trong DB. 
- Catalog "Nearby" hiện vẫn là read-only client presentation; checkout không
  được suy luận serviceability từ bán kính hoặc tọa độ client.
- Backend giữ vùng phục vụ theo polygon GeoJSON do ADMIN/SHOP_OWNER quản lý;
  điểm nằm trên biên được tính là hợp lệ và decision nội bộ fail-closed khi
  geometry không hợp lệ. ETA customer là range do `routing-service` tính từ
  driving duration + prep estimate, không phải khoảng cách thẳng do client tự
  suy luận. Capability này mặc định tắt cho tới khi có provider/runtime proof.

### 3.5. Inventory món ăn (default-off)

`restaurant-service` là authority duy nhất của `menu_item_inventory` và
`menu_item_inventory_reservations`. Owner/Admin cập nhật `on_hand_quantity` với
`expectedRevision`; Order gọi boundary nội bộ để giữ, commit hoặc release một
reservation UUID. Mỗi reservation có một order, khóa các dòng theo thứ tự
`menuItemId`, giữ 15 phút và xử lý all-or-nothing. Thiếu ledger row, món không
`AVAILABLE`, số lượng sai hoặc `on_hand - reserved` không đủ đều fail-closed;
không có fallback unlimited stock/backorder. Hai flag
`RESTAURANT_INVENTORY_ENABLED` và `ORDER_INVENTORY_RESERVATION_ENABLED` giữ
`false` cho tới khi có PostgreSQL/Kafka concurrency, replay/DLT, expiry và UX
runtime proof.
