# 🔍 Search Service (Elasticsearch)

## 1. Đặc tả (Specification)
**Mục tiêu:** Tách biệt tính năng tìm kiếm Full-text (toàn văn) ra khỏi các database quan hệ (PostgreSQL) nhằm tăng cường hiệu suất, tốc độ và cho phép tìm kiếm mờ (Fuzzy Search), tìm kiếm theo cụm từ.
**Kiến trúc:** Xây dựng một Search Engine trung tâm bằng Elasticsearch để index và tìm kiếm nhiều thực thể khác nhau (Nhà hàng, Món ăn, Shipper).

**Microservices liên quan:**
- `search-service`: Cung cấp API tìm kiếm cho Client, đọc dữ liệu từ Elasticsearch.
- `restaurant-service` / `shipper-service`: Nguồn phát sinh dữ liệu (Publish events qua Kafka).
- **Data Stores:** Elasticsearch (Lưu documents), Redis (Cache API kết quả).

## 2. Danh sách Use Cases

| Mã UC | Tên Use Case | Nền tảng | Trạng thái |
|-------|--------------|----------|------------|
| UC-12.1 | Tìm kiếm Full-text Nhà hàng, Món ăn, Shipper | All | ✅ Backend Done |
| UC-12.2 | Đồng bộ dữ liệu Real-time (Entity Sync) | Backend | ✅ Consumer Done |
| UC-12.3 | Tích hợp giao diện Tìm kiếm App | Customer App | ❌ Not Started |

## 3. Luồng nghiệp vụ (Business Flow)

### 3.1. Luồng đồng bộ dữ liệu vào Elasticsearch (Data Ingestion)
Để Elasticsearch có dữ liệu để tìm kiếm, hệ thống không gọi API trực tiếp giữa các service mà sử dụng kiến trúc Event-Driven qua Kafka nhằm đảm bảo Eventual Consistency.

1. Khi Admin/Merchant **tạo, sửa, xóa** một nhà hàng hoặc món ăn ở `restaurant-service`, service này cập nhật vào PostgreSQL của nó.
2. Ngay lập tức, `restaurant-service` publish một message lên Kafka topic `entity-sync` với cấu trúc: `{ entityType: "RESTAURANT", action: "UPDATE", entityId: 1, payload: {...} }`.
3. `search-service` đóng vai trò là Consumer, lắng nghe topic này.
4. Khi nhận được message, `search-service` thực hiện hành động `upsert` hoặc `delete` tương ứng vào Document trên Elasticsearch.
5. Đồng thời, `search-service` xóa cache Redis (`evictByPrefix`) liên quan đến từ khóa đó để kết quả search được cập nhật mới nhất.

### 3.2. Luồng truy vấn tìm kiếm (Search Execution)
Khi User gõ từ khóa tìm kiếm trên App (VD: "Cơm tấm"):
1. Request gọi vào API `/api/search/dishes?q=Cơm tấm&page=0`.
2. `search-service` kiểm tra Redis cache xem cụm khóa này đã được ai tìm chưa.
3. Nếu có (Cache hit), trả về ngay.
4. Nếu chưa (Cache miss), gọi query vào Elasticsearch (kết hợp các điều kiện như match phrase, fuzzy, phân trang).
5. Trả kết quả cho App và lưu lại vào Redis để phục vụ các user sau.
