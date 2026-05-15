# 🛵 Delivery Matching & Tracking Flow

## 1. Đặc tả luồng
Giải quyết bài toán làm sao để tìm được Shipper phù hợp nhất (gần nhà hàng nhất, đang rảnh) và cách thức duy trì vị trí của Shipper trên bản đồ của Khách hàng theo thời gian thực.
Sử dụng **Redis GEO** cho tính toán tọa độ siêu tốc và **WebSocket** cho Real-time communication.

## 2. Biểu đồ tuần tự (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    participant Order as Order Service
    participant Match as Match Service
    participant Redis as Redis (GEO)
    participant Ship as Shipper App
    participant Cust as Customer App
    participant WS as WebSocket Broker

    %% Luồng Tìm Shipper
    Note over Order, Redis: Giai đoạn 1: Match Shipper
    Order->>Match: Yêu cầu tìm Shipper (Kèm Lat/Lng của nhà hàng)
    Match->>Redis: GEORADIUS (Tìm shipper trong bán kính 3km)
    Redis-->>Match: Danh sách Shipper gần nhất (S1, S2, S3...)
    Match->>Match: Lọc bỏ các Shipper đang bận (Kiểm tra Redis Key 'shipper:busy')
    
    %% Phát đơn & Chờ nhận
    Note over Match, Ship: Giai đoạn 2: Phát đơn
    Match->>WS: Push notification (FCM + Socket) tới Shipper #1
    WS-->>Ship: Hiển thị Popup nhận đơn (Đếm ngược 15s)
    
    alt Shipper Bỏ qua / Từ chối
        Ship->>Match: Reject hoặc Timeout
        Match->>Match: Loại Shipper #1, lấy Shipper #2 tiếp tục gửi Push
    else Shipper Nhận đơn
        Ship->>Match: Bấm Accept
        Match->>Redis: Khóa Shipper (Set Redis Key 'shipper:busy:1' = true)
        Match->>Order: Cập nhật trạng thái đơn thành ASSIGNED
    end

    %% Real-time Tracking
    Note over Ship, Cust: Giai đoạn 3: Realtime GPS Tracking
    loop Cứ mỗi 5 giây
        Ship->>WS: Gửi tọa độ GPS hiện tại qua Websocket
        WS->>Cust: Broadcast payload trực tiếp tới Customer App
        Cust->>Cust: Di chuyển marker Shipper trên Bản đồ UI
    end
    
    Note over Ship, Order: Giai đoạn 4: Giao hàng
    Ship->>Order: Cập nhật Status (PICKED_UP -> DELIVERING -> DELIVERED)
    Order->>WS: Push thông báo cho Customer
```
