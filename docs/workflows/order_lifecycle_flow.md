# 🛒 Order Lifecycle Flow (Saga Pattern)

## 1. Đặc tả luồng
Đây là luồng quan trọng nhất và phức tạp nhất hệ thống. Do sử dụng Microservices, một thao tác đặt hàng liên quan tới rất nhiều Database khác nhau (Order DB, Promotion DB, FlashSale DB, Payment DB, Delivery DB). 
Hệ thống sử dụng **Saga Orchestration Pattern** (Quản lý tập trung qua `saga-orchestrator-service`) để quản lý transaction phân tán. Nếu 1 bước thất bại, hệ thống tự động phát ra event "Rollback" (Compensation) để trả lại tiền/voucher/kho hàng, tránh tình trạng mất mát dữ liệu.

## 2. Biểu đồ tuần tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    participant App as Customer App
    participant Saga as Saga Orchestrator
    participant Order as Order Service
    participant Promo as Promotion Service
    participant Flash as FlashSale Service
    participant Pay as Payment Service

    App->>Order: POST /api/orders (Tạo đơn hàng)
    Order->>Order: Lưu DB trạng thái PENDING
    Order->>Saga: Kafka Emit "OrderCreated"

    Note over Saga, Pay: Luồng Saga Khởi Chạy (Giai đoạn Lock Tài Nguyên)
    Saga->>Promo: Lệnh "Reserve Voucher"
    Promo-->>Saga: OK
    
    Saga->>Flash: Lệnh "Reserve Stock" (Redis API)
    Flash-->>Saga: OK
    
    Saga->>Pay: Khởi tạo Payment (VNPay/COD)
    Pay-->>Saga: Trả về URL Thanh Toán / Transaction ID
    Saga-->>App: Trả kết quả Thanh toán cho User (Bypass)

    Note over App, Pay: Giai đoạn User Thanh Toán
    App->>Pay: User thanh toán thành công (Webhook / App Trigger)
    Pay->>Saga: Kafka Emit "PaymentSuccess"
    
    Saga->>Order: Lệnh "Update PAID"
    Order-->>Saga: OK
    Saga->>Saga: Tự động Emit "OrderReadyForDelivery" (Chuyển sang luồng tìm Shipper)

    %% Luồng Compensation
    Note over Saga, Pay: Luồng Compensation (Nếu User hủy thanh toán hoặc Timeout)
    Pay->>Saga: Kafka Emit "PaymentFailed"
    Saga->>Promo: Lệnh Compensation "Release Voucher" (Nhả mã)
    Saga->>Flash: Lệnh Compensation "Release Stock" (Nhả kho)
    Saga->>Order: Lệnh "Update CANCELED"
```
