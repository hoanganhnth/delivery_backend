# 💰 Settlement & Finance Flow

## 1. Đặc tả luồng
Luồng xử lý tài chính cực kỳ khắt khe của hệ thống, quản lý tiền bạc của Shipper. Hệ thống phân chia rõ ràng 2 loại ví ảo:
- **Ví thu nhập (Earnings Wallet):** Lưu trữ tiền công ship, tiền thưởng, tiền hoàn thành đơn. Từ ví này Shipper có thể rút ra ngân hàng.
- **Ví ký quỹ (Deposit Wallet):** Số tiền Shipper nạp trước vào hệ thống để làm "tài sản thế chấp" khi nhận các đơn hàng thu tiền mặt (COD - Cash On Delivery).

## 2. Biểu đồ tuần tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    participant Ship as Shipper App
    participant Deli as Delivery Service
    participant Set as Settlement Service
    participant Admin as Admin Web

    %% Cộng tiền khi hoàn thành đơn
    Note over Deli, Set: Luồng 1: Quyết toán sau giao hàng
    Deli->>Deli: Shipper bấm Giao hàng thành công (DELIVERED)
    Deli->>Set: Kafka Event: delivery.completed (Kèm phí ship)
    Set->>Set: Kiểm tra gian lận (Fraud Check)
    Set->>Set: Cộng phí ship vào Ví Thu Nhập (Available Balance)
    
    %% Luồng Rút tiền
    Note over Ship, Admin: Luồng 2: Yêu cầu Rút tiền (Withdrawal)
    Ship->>Set: POST /withdraw (Yêu cầu rút 500k)
    Set->>Set: Validate: Ví Thu Nhập >= 500k
    Set->>Set: Trừ 500k từ Ví Thu Nhập -> Chuyển số tiền sang Ví Pending
    Set-->>Ship: 200 OK (Đã ghi nhận yêu cầu)
    
    Admin->>Set: GET /admin/withdrawals (Xem danh sách chờ duyệt)
    Admin->>Set: PUT /approve/{id} (Kế toán duyệt lệnh rút)
    Set->>Set: Chính thức trừ tiền ở Ví Pending
    Set->>Ship: Gửi Push Notification (Đã giải ngân qua TK Ngân hàng)
    
    %% Kiểm tra Ví Ký quỹ cho đơn COD
    Note over Ship, Set: Luồng 3: Kiểm tra điều kiện COD
    Deli->>Set: Chuẩn bị phát đơn COD 1 triệu cho Shipper X
    Set->>Set: Kiểm tra Ví Ký Quỹ của Shipper X
    alt Deposit >= 1.000.000đ
        Set-->>Deli: Đủ điều kiện (Eligible = True)
        Deli->>Ship: Bắn Popup mời nhận đơn
    else Deposit < 1.000.000đ
        Set-->>Deli: Không đủ điều kiện (Eligible = False)
        Deli->>Deli: Bỏ qua Shipper X, tìm người khác
    end
```
