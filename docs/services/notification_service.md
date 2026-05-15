# 🔔 Notification Service

## 1. Đặc tả (Specification)
**Mục tiêu:** Quản lý và gửi thông báo đa kênh (Push Notification qua Firebase Cloud Messaging và In-app notification qua WebSocket/REST) cho Customer, Shipper và Merchant.
**Microservices liên quan:**
- `notification-service`: Service trung tâm xử lý template thông báo, gửi push, lưu trữ lịch sử thông báo.
- **Third-party:** Firebase Cloud Messaging (FCM).

## 2. Danh sách Use Cases
| Mã UC | Tên Use Case | Nền tảng | Trạng thái |
|-------|--------------|----------|------------|
| UC-5.1 | Gửi Push Notification (FCM) | All | ✅ Done |
| UC-5.2 | Hiển thị & Quản lý In-app Notification | All | ✅ Done |

## 3. Luồng nghiệp vụ (Business Flow)

### 3.1. Cơ chế Event-Driven (Không gọi API trực tiếp)
Để tránh làm chậm các service khác khi gửi thông báo, hệ thống sử dụng Kafka:
1. Khi có sự kiện (Ví dụ: Order Service tạo đơn thành công, Delivery Service đổi status).
2. Các service này bắn event lên Kafka topic (VD: `order.created`, `delivery.status_changed`).
3. `notification-service` đóng vai trò là Consumer, lắng nghe các topic này.
4. Nó đọc Payload, build nội dung thông báo dựa trên Template (Đa ngôn ngữ), lưu vào Database (PostgreSQL/MongoDB) làm In-app Notification.

### 3.2. Gửi Push Notification
- Sau khi lưu DB, service gọi sang API của Firebase Cloud Messaging (FCM) kèm theo `fcm_token` của user nhận.
- Firebase sẽ bắn push notification xuống thiết bị iOS/Android của khách hàng/shipper.
