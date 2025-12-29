# ✅ Tích hợp Tổng Tiền Đơn Hàng và Thu Nhập Shipper

## 📋 Tổng quan

Hệ thống đã được nâng cấp để:
1. **Tính tổng tiền đơn hàng** tự động khi user tạo order
2. **Tự động cộng tiền vào balance của shipper** khi giao hàng hoàn thành

## 🔄 Luồng hoạt động

```
User tạo Order
  ↓
Order Service: Calculate totalPrice = subtotal + shippingFee - discount
  ↓
Publish OrderCreatedEvent (includes shippingFee)
  ↓
Delivery Service: Create Delivery + set shippingFee
  ↓
Find & assign Shipper
  ↓
Shipper delivers (status = DELIVERED)
  ↓
Delivery Service: Publish DeliveryCompletedEvent
  ↓
Shipper Service: Auto credit shippingFee to shipper balance
  ✅ Done
```

## 📦 Các thay đổi

### 1. Order Service
- ✅ Order entity đã có `totalPrice`, `subtotalPrice`, `shippingFee`, `discountAmount`
- ✅ `OrderServiceImpl.createOrder()` tự động tính:
  ```java
  subtotal = sum(item.price * item.quantity)
  shippingFee = 15000 (default)
  totalPrice = subtotal + shippingFee - discount
  ```

### 2. Delivery Service

#### Entity
- ✅ Thêm field `shippingFee` vào `Delivery` entity
  ```java
  @Column(name = "shipping_fee", precision = 12, scale = 2)
  private BigDecimal shippingFee;
  ```

#### Event
- ✅ Tạo `DeliveryCompletedEvent` với các field:
  - `deliveryId`, `orderId`, `shipperId`
  - `shippingFee` - số tiền shipper nhận
  - `deliveredAt`, `deliveryAddress`

#### Logic
- ✅ `createDeliveryFromOrderEvent()`: Copy `shippingFee` từ OrderCreatedEvent
- ✅ `updateDeliveryStatus()`: Khi status = DELIVERED → publish `DeliveryCompletedEvent`
- ✅ `DeliveryEventPublisher.publishDeliveryCompletedEvent()`

#### Kafka Topic
- ✅ Thêm topic: `delivery.completed`

### 3. Shipper Service

#### Dependency
- ✅ Thêm Spring Kafka dependency vào `pom.xml`

#### Event DTO
- ✅ Tạo `DeliveryCompletedEvent` DTO (mirror delivery-service)

#### Listener
- ✅ `DeliveryCompletedEventListener`:
  - Listen topic `delivery.completed`
  - Validate `shipperId` và `shippingFee`
  - Auto call `shipperBalanceService.earnFromOrderByUserId()`
  - Log thành công/thất bại

## 🗄️ Database Migration

### Delivery Service - Thêm cột shipping_fee

```sql
-- File: delivery-service/src/main/resources/db/migration/V3__add_shipping_fee_to_deliveries.sql
ALTER TABLE deliveries 
ADD COLUMN shipping_fee DECIMAL(12,2);

-- Set default value cho records hiện tại
UPDATE deliveries 
SET shipping_fee = 15000.00 
WHERE shipping_fee IS NULL;

COMMENT ON COLUMN deliveries.shipping_fee IS 'Phí giao hàng mà shipper sẽ nhận được';
```

## 🎯 API Endpoints

### Order Controller
**POST** `/api/orders`
```json
{
  "restaurantId": 1,
  "items": [
    {
      "menuItemId": 10,
      "menuItemName": "Phở Bò",
      "quantity": 2,
      "price": 50000
    }
  ],
  "deliveryAddress": "123 Nguyễn Huệ, Q1, TP.HCM",
  "deliveryLat": 10.762622,
  "deliveryLng": 106.660172,
  "customerName": "Nguyen Van A",
  "customerPhone": "0901234567",
  "paymentMethod": "COD"
}
```

**Response:**
```json
{
  "status": 1,
  "data": {
    "id": 100,
    "subtotalPrice": 100000,
    "shippingFee": 15000,
    "discountAmount": 0,
    "totalPrice": 115000,
    "status": "PENDING",
    ...
  }
}
```

### Shipper Balance Controller
**GET** `/api/shipper-balances`
```
Headers:
  X-User-Id: 5
  X-Role: SHIPPER
```

**Response:**
```json
{
  "status": 1,
  "data": {
    "shipperId": 5,
    "balance": 345000,
    "holdingBalance": 0,
    "updatedAt": "2025-12-29T10:30:00"
  }
}
```

**GET** `/api/shipper-balances/transactions`
```json
{
  "status": 1,
  "data": [
    {
      "id": 50,
      "type": "EARN",
      "amount": 15000,
      "description": "Thu nhập từ đơn hàng #100",
      "createdAt": "2025-12-29T10:00:00"
    }
  ]
}
```

## 🧪 Test Flow

1. **Tạo order mới**:
   ```bash
   curl -X POST http://localhost:8082/api/orders \
     -H "Content-Type: application/json" \
     -H "X-User-Id: 1" \
     -H "X-Role: USER" \
     -d '{
       "restaurantId": 1,
       "items": [{"menuItemId": 10, "quantity": 2, "price": 50000}],
       "deliveryAddress": "123 Nguyễn Huệ",
       "deliveryLat": 10.762622,
       "deliveryLng": 106.660172,
       "customerName": "Test User",
       "customerPhone": "0901234567"
     }'
   ```

2. **Shipper accept order**:
   ```bash
   curl -X POST http://localhost:8083/api/deliveries/accept \
     -H "Content-Type: application/json" \
     -H "X-User-Id: 5" \
     -H "X-Role: SHIPPER" \
     -d '{
       "orderId": 100,
       "action": "ACCEPT"
     }'
   ```

3. **Update delivery status → PICKED_UP**:
   ```bash
   curl -X PUT http://localhost:8083/api/deliveries/1/status?status=PICKED_UP \
     -H "X-User-Id: 5" \
     -H "X-Role: SHIPPER"
   ```

4. **Update delivery status → DELIVERING**:
   ```bash
   curl -X PUT http://localhost:8083/api/deliveries/1/status?status=DELIVERING \
     -H "X-User-Id: 5" \
     -H "X-Role: SHIPPER"
   ```

5. **Update delivery status → DELIVERED** (tự động cộng tiền):
   ```bash
   curl -X PUT http://localhost:8083/api/deliveries/1/status?status=DELIVERED \
     -H "X-User-Id: 5" \
     -H "X-Role: SHIPPER"
   ```

6. **Kiểm tra balance của shipper**:
   ```bash
   curl http://localhost:8085/api/shipper-balances \
     -H "X-User-Id: 5" \
     -H "X-Role: SHIPPER"
   ```

## 📊 Logs để theo dõi

### Delivery Service
```
💰 Publishing DeliveryCompletedEvent for delivery: 1, shipper: 5, amount: 15000
✅ Published DeliveryCompletedEvent for delivery: 1 to partition: 0 offset: 123
```

### Shipper Service
```
💰 Received DeliveryCompletedEvent for delivery: 1, shipper: 5, amount: 15000
✅ Successfully credited 15000 to shipper 5 balance for delivery 1
```

## 🔧 Configuration

### application.yml (Delivery & Shipper Services)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: ${spring.application.name}-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    listener:
      ack-mode: manual
```

## ✅ Checklist

- [x] Order có `totalPrice`, `subtotalPrice`, `shippingFee`
- [x] Order Service tính toán giá tự động
- [x] Delivery entity có field `shippingFee`
- [x] Delivery copy `shippingFee` từ OrderCreatedEvent
- [x] `DeliveryCompletedEvent` được tạo và publish
- [x] Shipper Service có Kafka dependency
- [x] `DeliveryCompletedEventListener` auto credit balance
- [x] `ShipperBalanceService.earnFromOrderByUserId()` hoạt động
- [x] Kafka topic `delivery.completed` được config

## 🚀 Next Steps

1. Run migration script để thêm cột `shipping_fee`
2. Restart các services để load code mới
3. Test flow end-to-end
4. Monitor logs để đảm bảo events được publish/consume thành công
5. Kiểm tra shipper balance sau mỗi delivery completed

## 📝 Notes

- Mặc định `shippingFee = 15000` VNĐ
- Có thể customize shipping fee based on distance sau
- Event-driven nên nếu Kafka down, cần retry mechanism
- Shipper balance transaction history được lưu để audit
