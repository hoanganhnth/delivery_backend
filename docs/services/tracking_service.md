# 📍 Realtime Tracking Service

## 1. Đặc tả (Specification)
**Mục tiêu:** Đảm nhiệm luồng giao tiếp thời gian thực, chủ yếu là gửi/nhận tọa độ GPS của Shipper trên đường giao hàng để vẽ bản đồ cho khách hàng xem.
**Microservices liên quan:**
- `tracking-service`: Xử lý kết nối WebSocket (STOMP/SockJS hoặc Native WebSocket).
- `delivery-service` / `match-service`: Dịch vụ gọi tới tracking để cập nhật trạng thái đơn.

## 2. Danh sách Use Cases
| Mã UC | Tên Use Case | Nền tảng | Trạng thái |
|-------|--------------|----------|------------|
| UC-4.3 | Tracking đơn hàng (Customer App) | Customer App | ✅ Done |
| UC-4.4 | Shipper gửi vị trí GPS liên tục | Shipper App | ✅ Done |

## 3. Luồng nghiệp vụ (Business Flow)

### 3.1. Duy trì kết nối WebSocket
Do HTTP là giao thức 1 chiều, hệ thống sử dụng WebSocket để giữ kết nối 2 chiều liên tục:
1. Khi Shipper bắt đầu giao đơn, Shipper App mở kết nối WebSocket tới `tracking-service`.
2. Khách hàng khi vào màn hình Tracking cũng mở một kết nối WebSocket lắng nghe (Subscribe) vào một Topic cụ thể của đơn hàng (VD: `/topic/tracking/order_123`).

### 3.2. Broadcast Tọa Độ
1. Cứ mỗi 5 giây, Shipper App gửi gói tin chứa `{lat, lng, heading}` lên Server qua Socket.
2. `tracking-service` nhận gói tin, có thể lưu tạm vào Redis (nếu cần tính quãng đường) và ngay lập tức Broadcast (phát thanh) gói tin này vào Topic `/topic/tracking/order_123`.
3. Customer App nhận được gói tin, di chuyển Marker con xe máy trên bản đồ Google Maps (sử dụng Animation để di chuyển mượt mà giữa 2 điểm).
