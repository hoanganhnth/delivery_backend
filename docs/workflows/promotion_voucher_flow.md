# 🎟️ Promotion & Voucher Flow

## 1. Đặc tả luồng
Quy trình áp dụng mã giảm giá trong hệ thống Microservices không thể thực hiện chung 1 bước, mà được chia làm 3 pha riêng biệt:
- **Calculate (Tính toán):** Chỉ tính thử số tiền giảm trên giao diện, không khóa voucher.
- **Reserve (Khóa/Tạm giữ):** Khóa voucher lại khi user bắt đầu bấm thanh toán, để user khác không cướp được mã giới hạn.
- **Compensation (Hoàn trả):** Tự động nhả voucher ra nếu thanh toán thất bại.

## 2. Biểu đồ tuần tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    participant App as Customer App
    participant Order as Order Service
    participant Promo as Promotion Service
    participant Saga as Saga Orchestrator

    %% Bước Calculate
    Note over App, Promo: Phase 1: Preview Calculation
    App->>Promo: POST /api/promotions/calculate (Context: Tổng đơn, Mã Voucher)
    Promo->>Promo: Kiểm tra điều kiện (Tối thiểu, User hợp lệ, Hạn sử dụng)
    Promo-->>App: Trả về Số tiền sẽ được giảm (Ví dụ: 30k)
    App->>App: Render UI Tổng tiền mới

    %% Bước Đặt hàng (Reserve)
    Note over App, Promo: Phase 2: Lock Resource
    App->>Order: Nhấn Place Order (Gửi kèm Voucher Code)
    Order->>Promo: Internal POST /api/promotions/reserve
    Promo->>Promo: Khóa Voucher (Đổi status sang RESERVED cho User này)
    Promo-->>Order: 200 OK

    %% Bước Hoàn tất / Hoàn trả
    Note over Promo, Saga: Phase 3: Finalize / Compensation
    alt Thanh toán thành công
        Saga->>Promo: Kafka Event: order.completed
        Promo->>Promo: Chốt Voucher (Chuyển sang trạng thái USED)
    else Thanh toán thất bại / Hủy
        Saga->>Promo: Kafka Event: payment.failed / order.cancelled
        Promo->>Promo: Hoàn trả Voucher (Đổi trạng thái về AVAILABLE)
    end
```
