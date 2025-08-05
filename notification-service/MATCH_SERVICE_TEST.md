# 🎯 Match Service Integration Test Commands

## Test Match Service Events

### 1. Send Match Found Event (to Shipper)
```bash
# Start Kafka console producer
bin/kafka-console-producer.bat --topic match.shipper-found --bootstrap-server localhost:9092

# Send MatchFoundEvent:
{
  "matchId": 123,
  "orderId": 456,
  "userId": 789,
  "shipperId": 101,
  "shipperName": "Nguyễn Văn A",
  "shipperPhone": "0901234567",
  "status": "FOUND",
  "latitude": 10.7769,
  "longitude": 106.7009,
  "distance": 2.5,
  "pickupAddress": "123 Nguyen Hue, District 1, HCMC",
  "deliveryAddress": "456 Le Lai, District 1, HCMC",
  "estimatedPrice": 25000,
  "estimatedTime": 15,
  "restaurantName": "Nhà hàng ABC",
  "customerName": "Trần Thị B",
  "customerPhone": "0987654321",
  "orderValue": 150000
}
```

### 2. Send Match Request Event (to Shipper)
```bash
# Start Kafka console producer
bin/kafka-console-producer.bat --topic match.shipper-request --bootstrap-server localhost:9092

# Send MatchRequestEvent:
{
  "matchId": 124,
  "orderId": 457,
  "userId": 790,
  "shipperId": 102,
  "shipperName": "Lê Văn C",
  "shipperPhone": "0912345678",
  "status": "REQUESTED",
  "pickupAddress": "789 Hai Ba Trung, District 3, HCMC",
  "deliveryAddress": "321 Vo Van Tan, District 3, HCMC",
  "estimatedPrice": 30000,
  "estimatedTime": 20,
  "restaurantName": "Quán cơm DEF",
  "customerName": "Phạm Văn D",
  "customerPhone": "0976543210",
  "orderValue": 200000,
  "distance": 3.2
}
```

### 3. Send Match Accepted Event (to Customer & Shipper)
```bash
# Start Kafka console producer  
bin/kafka-console-producer.bat --topic match.shipper-accepted --bootstrap-server localhost:9092

# Send MatchAcceptedEvent:
{
  "matchId": 125,
  "orderId": 458,
  "userId": 791,
  "shipperId": 103,
  "shipperName": "Hoàng Thị E",
  "shipperPhone": "0923456789",
  "status": "ACCEPTED",
  "pickupAddress": "555 Dong Khoi, District 1, HCMC",
  "deliveryAddress": "888 Nguyen Thai Binh, District 1, HCMC",
  "estimatedTime": 18,
  "restaurantName": "Pizza GHI",
  "customerName": "Ngô Văn F",
  "customerPhone": "0965432109"
}
```

### 4. Send Match Rejected Event (from Shipper)
```bash
# Start Kafka console producer
bin/kafka-console-producer.bat --topic match.shipper-rejected --bootstrap-server localhost:9092

# Send MatchRejectedEvent:
{
  "matchId": 126,
  "orderId": 459,
  "userId": 792,
  "shipperId": 104,
  "shipperName": "Vũ Văn G",
  "status": "REJECTED",
  "reason": "Khoảng cách quá xa",
  "distance": 8.5
}
```

## Expected Notifications

### For Shipper (Match Found)
```json
{
  "title": "🎯 Đơn hàng phù hợp!",
  "message": "Đơn hàng #456 từ Nhà hàng ABC - Khoảng cách: 2.5km - Phí: 25,000 VND - Thời gian: 15 phút",
  "type": "MATCH_FOUND",
  "priority": "HIGH"
}
```

### For Customer (Shipper Accepted)
```json
{
  "title": "✅ Shipper đã nhận đơn hàng!",
  "message": "Hoàng Thị E (0923456789) đã nhận đơn hàng #458 của bạn. Thời gian dự kiến: 18 phút",
  "type": "SHIPPER_ACCEPTED",
  "priority": "HIGH"
}
```

### For Shipper (Confirmation)
```json
{
  "title": "✅ Xác nhận nhận đơn",
  "message": "Bạn đã nhận đơn hàng #458 từ Pizza GHI. Địa chỉ lấy hàng: 555 Dong Khoi, District 1, HCMC. Khách hàng: 0965432109",
  "type": "SHIPPER_CONFIRMED",
  "priority": "MEDIUM"
}
```

## Test WebSocket Real-time

### Connect as Shipper
```javascript
// Connect WebSocket as shipper (userId = 101)
const socket = new SockJS('http://localhost:8087/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function (frame) {
    // Subscribe to shipper notifications
    stompClient.subscribe('/topic/user/101', function (message) {
        const notification = JSON.parse(message.body);
        console.log('🎯 Shipper received:', notification);
        
        if (notification.type === 'MATCH_FOUND') {
            // Show order matching notification to shipper
            displayMatchNotification(notification);
        }
    });
});
```

### Connect as Customer
```javascript
// Connect WebSocket as customer (userId = 791)
const socket = new SockJS('http://localhost:8087/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function (frame) {
    // Subscribe to customer notifications
    stompClient.subscribe('/topic/user/791', function (message) {
        const notification = JSON.parse(message.body);
        console.log('👤 Customer received:', notification);
        
        if (notification.type === 'SHIPPER_ACCEPTED') {
            // Show shipper accepted notification
            displayShipperAcceptedNotification(notification);
        }
    });
});
```

## Verify Notifications in Database

```sql
-- Check notifications for shipper
SELECT * FROM notifications 
WHERE user_id = 101 AND type IN ('MATCH_FOUND', 'DELIVERY_REQUEST', 'SHIPPER_CONFIRMED')
ORDER BY created_at DESC;

-- Check notifications for customer  
SELECT * FROM notifications
WHERE user_id = 791 AND type = 'SHIPPER_ACCEPTED'
ORDER BY created_at DESC;

-- Check all match-related notifications
SELECT user_id, title, message, type, priority, created_at
FROM notifications 
WHERE type IN ('MATCH_FOUND', 'DELIVERY_REQUEST', 'SHIPPER_ACCEPTED', 'SHIPPER_CONFIRMED')
ORDER BY created_at DESC;
```

## Performance Test

```bash
# Send multiple match events quickly
for i in {1..10}; do
  echo '{
    "matchId": '$i',
    "orderId": '$((i+100))',
    "userId": '$((i+200))',
    "shipperId": '$((i+300))',
    "shipperName": "Shipper '$i'",
    "status": "FOUND",
    "distance": 2.5,
    "estimatedPrice": 25000,
    "estimatedTime": 15,
    "restaurantName": "Restaurant '$i'"
  }' | bin/kafka-console-producer.bat --topic match.shipper-found --bootstrap-server localhost:9092
done
```

---

**🎯 Result**: Match Service integration hoàn tất! Shipper sẽ nhận notifications về orders phù hợp thay vì từ Delivery Service.
