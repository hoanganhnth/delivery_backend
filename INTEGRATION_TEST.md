# 🧪 **Integration Test Script**

## 🚀 **Test Event Processing Flow**

### **Test Case 1: Valid Order with Full Coordinates**
```bash
# 1. Create order với đầy đủ address information
curl -X POST http://localhost:8084/api/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 123" \
  -H "X-Role: USER" \
  -d '{
    "restaurantId": 1,
    "restaurantName": "Phở Hà Nội",
    "restaurantAddress": "123 Nguyễn Huệ, Quận 1, TP.HCM",
    "restaurantPhone": "0901234567",
    "deliveryAddress": "456 Lê Lợi, Quận 1, TP.HCM",
    "deliveryLat": 10.762622,
    "deliveryLng": 106.660172,
    "customerName": "Nguyễn Văn A",
    "customerPhone": "0987654321",
    "paymentMethod": "COD",
    "items": [
      {
        "menuItemId": 101,
        "menuItemName": "Phở Bò Tái",
        "quantity": 1,
        "price": 65000
      }
    ]
  }'
```

### **Expected Log Flow:**
```
# Order Service:
📤 Publishing OrderCreatedEvent for order: 1

# Delivery Service:
📥 Received OrderCreatedEvent for order: 1
🎯 Set pickup coordinates: 10.762622, 106.660172 for address: 123 Nguyễn Huệ, Quận 1, TP.HCM
🚀 Publishing FindShipperEvent for delivery: 1 to Match Service

# Match Service:
📥 Received FindShipperEvent for delivery: 1
🎯 Using pickup location: 10.762622, 106.660172 for delivery: 1
✅ Found 3 nearby shippers for delivery: 1
🚚 Found shipper at location: 10.763, 106.661 with distance: 0.05km for delivery: 1
🎯 Best shipper selected at location: 10.763, 106.661 for delivery: 1
```

## 🔍 **Monitoring Commands**

### **Kafka Topic Monitoring:**
```bash
# Monitor find-shipper topic
kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic delivery.find-shipper --from-beginning

# Check consumer groups
kafka-consumer-groups.bat --bootstrap-server localhost:9092 --describe --group match-service
kafka-consumer-groups.bat --bootstrap-server localhost:9092 --describe --group delivery-service
```

### **Service Logs:**
```bash
# Monitor all services
tail -f delivery-service.log | grep "📥\|🚀\|🎯"
tail -f match-service.log | grep "📥\|🚚\|✅"
tail -f order-service.log | grep "📤\|✅"
```

## 🛠️ **Troubleshooting Commands**

### **Check Database:**
```sql
-- Check delivery records
SELECT id, order_id, pickup_address, pickup_lat, pickup_lng, status 
FROM deliveries 
ORDER BY created_at DESC 
LIMIT 5;

-- Check if coordinates are set
SELECT id, pickup_lat, pickup_lng, 
       CASE 
           WHEN pickup_lat IS NULL THEN 'Missing pickup coordinates'
           ELSE 'Coordinates OK'
       END as status
FROM deliveries;
```

### **Kafka Health Check:**
```bash
# List topics
kafka-topics.bat --bootstrap-server localhost:9092 --list

# Check topic details
kafka-topics.bat --bootstrap-server localhost:9092 --describe --topic delivery.find-shipper

# Reset consumer offset (if needed)
kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group match-service --reset-offsets --to-earliest --topic delivery.find-shipper --execute
```

## 🎯 **Expected Results**

### **✅ Success Scenario:**
1. Order created successfully
2. Delivery auto-created với pickup coordinates
3. FindShipperEvent published to Kafka  
4. Match Service receives event
5. Nearby shippers found và logged
6. No NullPointerException errors

### **⚠️ Fallback Scenarios:**
1. **Null pickup coordinates**: Uses delivery coordinates as fallback
2. **Null both coordinates**: Uses default TP.HCM center coordinates
3. **No shippers found**: Logs warning, continues processing
4. **Tracking service down**: Returns empty list, continues processing

### **🔧 Fixed Issues:**
- ✅ NullPointerException for pickup coordinates
- ✅ Fallback logic cho missing coordinates  
- ✅ Validation for invalid events
- ✅ Default coordinates mapping cho TP.HCM districts
- ✅ Comprehensive error handling và logging

---

**🎯 Integration test ready! Run test commands to verify the complete event flow.**
