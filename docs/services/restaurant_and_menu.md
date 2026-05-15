# 🍔 Restaurant & Menu Management

## 1. Đặc tả (Specification)
**Mục tiêu:** Quản lý toàn bộ thông tin nhà hàng, danh mục (category) và danh sách món ăn. Cung cấp API truy xuất nhanh cho khách hàng và API quản trị cho chủ nhà hàng (Merchant) & Admin.

**Microservices liên quan:**
- `restaurant-service`: Core service xử lý logic CRUD, tính toán khoảng cách.
- **Data Stores:** PostgreSQL (dữ liệu gốc), Redis (Cache danh sách nhà hàng/món ăn để tăng tốc độ phản hồi).

## 2. Danh sách Use Cases

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
1. Service cập nhật trực tiếp vào PostgreSQL.
2. Xóa (Evict) Key tương ứng trong Redis ngay lập tức để dữ liệu cũ không bị dính cache.
3. Publish event `entity-sync` lên Kafka để báo cho các service khác (ví dụ: Search Service) biết về sự thay đổi này.

### 3.3. Tính toán khoảng cách (Geolocation)
- Vị trí của nhà hàng (Lat/Lng) được lưu tĩnh trong DB. 
- Khi App gọi API lấy danh sách "Nearby", App phải gửi kèm Tọa độ của User hiện tại.
- Backend sử dụng hàm tính toán (VD: Haversine) hoặc query GIS (PostGIS) để trả về các nhà hàng trong bán kính 5km, đồng thời tính luôn thời gian giao hàng ước tính (ETA) dựa trên khoảng cách.
