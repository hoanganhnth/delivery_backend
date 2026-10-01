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
- `StatsReconciliationJob` chạy lúc 00:05, đọc raw events đã lưu trong
  `analytics_events` của ngày hôm trước theo từng trang 500 dòng, sắp xếp theo ID.
- Job ghi đè `daily_order_stats` cho platform và các nhà hàng có event; chạy lại
  không cộng dồn và không sửa raw events. Cả đường scheduled và lời gọi trực
  tiếp dùng transaction: lỗi ghi một projection rollback toàn bộ lần chạy và
  được truyền ra ngoài, không báo thành công giả.
- Những scope đã có thống kê trong ngày nhưng không có order event được nhận
  được reset về 0, giữ nguyên ID và raw receipts; ngày rỗng không tạo scope mới.
- Hiện job chỉ phục hồi order counters/revenue theo thời điểm receipt
  `event_time`; chưa rebuild payment/item projections. PostgreSQL concurrency/recovery drill vẫn cần
  bằng chứng riêng trước release.

Order/Payment listeners chặn JSON không phải object, ID thiếu hoặc không phải
số nguyên dương trong miền `long`, và payment amount không hữu hạn/không phải
số trước khi gọi projection hoặc ACK. Lỗi payload là `IllegalArgumentException`
(đi DLT theo cấu hình hiện có); lỗi storage vẫn retry và không ACK. Các ID tùy
chọn vắng mặt giữ `null`, không bị ép thành `0`.

### 3.3. Per-item projection (T7, default-off)

Order owns the immutable `order_items` snapshot. `order.created` and
`order.cancelled` carry that snapshot additively; Analytics claims the whole
event by stable `eventId`/payload fingerprint first, then upserts
`daily_item_sales` by `(stat_date, restaurant_id, menu_item_id)`.

The projection deliberately keeps `ordered_*` and `cancelled_*` counters
separate. A duplicate event is a no-op, a contradictory event ID is rejected,
and the complete immutable item snapshot is validated before any item row is
written. A malformed line rolls back the receipt and order aggregates in the
same transaction. Correcting a rejected payload allows retry with the
same event ID because the failed receipt was rolled back. Raw payloads must
be JSON objects; aggregate versions, menu-item IDs and quantities must be
positive integers that fit a Java long. Fractional values are rejected rather
than truncated. Legacy events without an
`items` field remain compatible but contribute no per-item row. PostgreSQL
concurrent upsert and broker replay rehearsal are still release evidence; the
analytics capability remains `ANALYTICS_PROCESSING_ENABLED=false`.
