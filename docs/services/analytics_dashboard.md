# 📊 Analytics & Dashboard Service

## 1. Đặc tả (Specification)
**Mục tiêu:** Cung cấp số liệu thống kê thời gian thực và tổng hợp cho hệ thống (Admin, Merchant). Thu thập dữ liệu qua Event-Driven để không làm ảnh hưởng hiệu năng luồng giao dịch chính.

**Microservices liên quan:** 
- `analytics-service`: Lắng nghe Kafka, tổng hợp dữ liệu, cung cấp API Dashboard.
- `order-service` / `payment-service`: Nguồn phát sinh sự kiện.
- **Data Stores:** PostgreSQL (bảng `DailyOrderStats`, `DailyRevenueStats` và
  `DailyItemSales`).

## 2. Danh sách Use Cases
| Mã UC | Tên Use Case | Nền tảng | Trạng thái |
|-------|--------------|----------|------------|
| UC-13.1 | Dashboard Admin toàn hệ thống | Admin Web | 🔧 Partial |
| UC-13.2 | Dashboard theo từng Nhà hàng | Restaurant Web | ❌ Not Started |
| UC-13.3 | Reconciliation (Phục hồi/Cân bằng dữ liệu) | Backend | ✅ Done |

## 3. Luồng nghiệp vụ (Business Flow)

### 3.1. Thu thập sự kiện (Event Processing)
Thay vì dùng lệnh `GROUP BY` liên tục trên bảng Orders gây nghẽn Database chính, hệ thống áp dụng pattern **CQRS** kết hợp Kafka:
- Khi một đơn hàng hoàn tất hoặc thanh toán thành công, Kafka broker nhận event.
- `analytics-service` (Consumer) nhận event và update tăng biến đếm (`+1` hoặc `+amount`) vào các bản ghi thống kê nhóm theo ngày (`DailyOrderStats`). API truy vấn Dashboard chỉ cần đọc từ các bảng đã aggregate sẵn này nên tốc độ cực nhanh (O(1)).

### 3.2. Cân bằng dữ liệu (Reconciliation Job)
Trong hệ thống Event-Driven phân tán, event có thể bị mất mạng, lỗi server hoặc xử lý sai lệch.
- Một Cron Job (`StatsReconciliationJob`) sẽ chạy mỗi đêm lúc 2:00 AM để quét lại toàn bộ dữ liệu gốc trong Database của Order Service của ngày hôm trước.
- Thuật toán sẽ tính tổng chính xác 100% và ghi đè lại kết quả vào bảng thống kê, đảm bảo tính vẹn toàn dữ liệu (Data Integrity).

### 3.3. Per-item projection (T7, default-off)

Order owns the immutable `order_items` snapshot. `order.created` and
`order.cancelled` carry that snapshot additively; Analytics claims the whole
event by stable `eventId`/payload fingerprint first, then upserts
`daily_item_sales` by `(stat_date, restaurant_id, menu_item_id)`.

The projection deliberately keeps `ordered_*` and `cancelled_*` counters
separate. A duplicate event is a no-op, a contradictory event ID is rejected,
and a malformed line rolls back the item update. Legacy events without an
`items` field remain compatible but contribute no per-item row. PostgreSQL
concurrent upsert and broker replay rehearsal are still release evidence; the
analytics capability remains `ANALYTICS_PROCESSING_ENABLED=false`.
