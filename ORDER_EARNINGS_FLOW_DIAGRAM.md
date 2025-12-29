```mermaid
sequenceDiagram
    participant User
    participant OrderService
    participant DeliveryService
    participant MatchService
    participant Shipper
    participant ShipperService
    participant Kafka

    Note over User,ShipperService: 💰 Flow: Tổng Tiền Order & Thu Nhập Shipper

    %% 1. User tạo order
    User->>OrderService: POST /api/orders
    Note right of OrderService: Calculate:<br/>subtotal = sum(items)<br/>shippingFee = 15000<br/>totalPrice = subtotal + fee
    
    OrderService->>OrderService: Save Order (totalPrice)
    OrderService->>Kafka: Publish OrderCreatedEvent<br/>(includes shippingFee)
    OrderService-->>User: OrderResponse<br/>(totalPrice: 115000)

    %% 2. Delivery Service nhận event
    Kafka->>DeliveryService: OrderCreatedEvent
    DeliveryService->>DeliveryService: Create Delivery<br/>(copy shippingFee = 15000)
    DeliveryService->>Kafka: Publish FindShipperEvent
    
    %% 3. Match & Assign Shipper
    Kafka->>MatchService: FindShipperEvent
    MatchService->>Kafka: Publish ShipperFoundEvent
    
    Kafka->>DeliveryService: ShipperFoundEvent
    DeliveryService->>DeliveryService: Status = WAIT_SHIPPER_CONFIRM
    
    Shipper->>DeliveryService: POST /api/deliveries/accept<br/>(action: ACCEPT)
    DeliveryService->>DeliveryService: Status = ASSIGNED
    DeliveryService-->>Shipper: Accepted ✅

    %% 4. Shipper giao hàng
    Shipper->>DeliveryService: PUT /status?status=PICKED_UP
    DeliveryService->>DeliveryService: Status = PICKED_UP
    
    Shipper->>DeliveryService: PUT /status?status=DELIVERING
    DeliveryService->>DeliveryService: Status = DELIVERING
    
    %% 5. Hoàn thành giao hàng - Auto credit
    Shipper->>DeliveryService: PUT /status?status=DELIVERED
    
    rect rgb(200, 255, 200)
        Note over DeliveryService: 💰 Trigger Auto Credit
        DeliveryService->>DeliveryService: Status = DELIVERED<br/>deliveredAt = now()
        
        DeliveryService->>Kafka: Publish DeliveryCompletedEvent<br/>(shipperId: 5, shippingFee: 15000)
        
        Kafka->>ShipperService: DeliveryCompletedEvent
        
        ShipperService->>ShipperService: earnFromOrderByUserId()<br/>(shipper: 5, amount: 15000)
        
        ShipperService->>ShipperService: Update Balance<br/>+ Create Transaction
        
        Note right of ShipperService: ✅ Balance:<br/>330000 → 345000<br/>Transaction: EARN +15000
    end
    
    DeliveryService-->>Shipper: Delivered ✅
    
    %% 6. Shipper check balance
    Shipper->>ShipperService: GET /api/shipper-balances
    ShipperService-->>Shipper: Balance: 345000 💰
    
    Shipper->>ShipperService: GET /api/shipper-balances/transactions
    ShipperService-->>Shipper: History:<br/>[EARN +15000, Order #100]
```

## Các thành phần chính

### 1. Order Service
- Tính `totalPrice` = subtotal + shippingFee - discount
- Publish `OrderCreatedEvent` (có shippingFee)

### 2. Delivery Service  
- Copy `shippingFee` từ OrderCreatedEvent
- Khi status = DELIVERED → Publish `DeliveryCompletedEvent`

### 3. Shipper Service
- Listen `DeliveryCompletedEvent`
- Auto call `earnFromOrderByUserId()`
- Update balance + tạo transaction record

### 4. Kafka Topics
- `order.created` - Order → Delivery
- `delivery.find-shipper` - Delivery → Match
- `shipper.found` - Match → Delivery
- **`delivery.completed`** - Delivery → Shipper (NEW)
