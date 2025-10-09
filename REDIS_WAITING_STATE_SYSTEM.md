# 🔄 Redis Waiting State System for Shipper Acceptance

## 📋 Tổng quan hệ thống

Hệ thống Redis cache để quản lý trạng thái "chờ shipper nhận đơn" với TTL tự động và retry mechanism:

1. **Shipper Found**: Cache waiting state trong Redis với TTL
2. **TTL Expiration**: Tự động retry tìm shipper khi hết hạn
3. **Shipper Acceptance**: Remove waiting state khi shipper accept
4. **Auto Retry**: Republish FindShipperEvent để tìm shipper mới

## 🔄 Flow Architecture

### 1. Shipper Found & Cache Flow
```
🎯 Match Service (Found Shippers) → 📤 ShipperFoundEvent
                                        ↓
🚚 Delivery Service ← 📥 Kafka ← ShipperFoundEvent
                    ↓
📦 Redis Cache với TTL → Key: "delivery:waiting:" + deliveryId
                         ↓
🔄 Status: WAIT_SHIPPER_CONFIRM
```

### 2. TTL Expiration & Retry Flow
```
⏰ Redis TTL Expired → 🔔 Notification
                        ↓
🚚 Delivery Service → 📤 republishFindShipperEvent
                        ↓
🎯 Match Service ← 📥 Kafka ← FindShipperEvent (Retry)
                ↓
🔍 Search Again
```

### 3. Shipper Acceptance Flow
```
📱 Shipper Accept → 🚚 Delivery Service
                    ↓
🗑️ Remove Redis Cache → Key deleted
                       ↓
🔄 Status: ASSIGNED
```

## 📝 Data Structures

### ShipperFoundEvent
```java
{
  "deliveryId": 789,
  "orderId": 123,
  "availableShippers": [
    {
      "shipperId": 456,
      "shipperName": "Nguyen Van A",
      "shipperPhone": "0901234567",
      "distanceKm": 1.2,
      "latitude": 10.762622,
      "longitude": 106.660172,
      "rating": 4.8,
      "isOnline": true
    }
  ],
  "foundAt": "2024-10-06T10:30:00",
  "waitingTimeoutSeconds": 300,
  "matchingSessionId": "delivery_789"
}
```

### Redis Waiting State
```json
{
  "deliveryId": 789,
  "orderId": 123,
  "availableShippers": [...],
  "createdAt": "2024-10-06T10:30:00",
  "timeoutSeconds": 300,
  "matchingSessionId": "delivery_789"
}
```

## 🔑 Redis Keys & TTL

| Key Pattern | TTL | Purpose |
|-------------|-----|---------|
| `delivery:waiting:{deliveryId}` | 300s (5 min) | Cache shipper waiting state |

### Redis Operations
```java
// Cache waiting state với TTL
redisTemplate.opsForValue().set(key, waitingState, Duration.ofSeconds(300));

// Check if waiting
Boolean exists = redisTemplate.hasKey(key);

// Remove when accepted
Boolean deleted = redisTemplate.delete(key);

// Get waiting state
WaitingState state = (WaitingState) redisTemplate.opsForValue().get(key);
```

## 🎯 Kafka Topics

| Topic | Publisher | Consumer | Purpose |
|-------|-----------|----------|---------|
| `shipper.found` | Match Service | Delivery Service | Notify shippers found |
| `delivery.find-shipper` | Delivery Service | Match Service | Retry find shipper |

## 🏗️ Service Implementation

### Match Service
- **FindShipperEventListener**: Enhanced để bắn ShipperFoundEvent khi success
- **MatchEventPublisher.publishShipperFoundEvent()**: Publish shipper found events
- **Flow**: findNearbyShippers() success → publishShipperFoundEvent()

### Delivery Service
- **OrderEventListener.handleShipperFoundEvent()**: Process shipper found events
- **DeliveryWaitingService**: Core Redis cache management
- **RedisConfig**: Redis configuration với JSON serialization
- **Flow**: ShipperFoundEvent → cacheWaitingForShipperAcceptance() → Redis TTL

## 🛠️ Key Components

### 1. DeliveryWaitingService
```java
// Cache waiting state với TTL
public void cacheWaitingForShipperAcceptance(ShipperFoundEvent event)

// Remove khi shipper accept
public void removeWaitingState(Long deliveryId)

// Check waiting status
public boolean isWaitingForShipper(Long deliveryId)

// Handle TTL expiration
public void handleWaitingTimeout(Long deliveryId)
```

### 2. DeliveryStatus Updates
- **FINDING_SHIPPER**: Đang tìm shipper
- **WAIT_SHIPPER_CONFIRM**: Chờ shipper nhận đơn ✨ 
- **ASSIGNED**: Shipper đã nhận đơn
- **SHIPPER_NOT_FOUND**: Không tìm được shipper

### 3. Status Transitions
```
FINDING_SHIPPER → WAIT_SHIPPER_CONFIRM (khi tìm được shipper)
WAIT_SHIPPER_CONFIRM → ASSIGNED (khi shipper accept)
WAIT_SHIPPER_CONFIRM → FINDING_SHIPPER (khi TTL expired retry)
```

## ⚙️ Configuration

### Redis Properties
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-wait: -1ms
        max-idle: 8
        min-idle: 0
```

### Waiting Timeout Configuration
```java
private static final int DEFAULT_WAITING_TIMEOUT_SECONDS = 300; // 5 minutes
private static final String WAITING_KEY_PREFIX = "delivery:waiting:";
```

## 🔧 Retry Mechanism

### 1. TTL-based Retry
- **Redis TTL**: 5 minutes default
- **Auto Expiration**: Redis tự động xóa key khi hết TTL
- **Keyspace Notifications**: Có thể dùng để listen expiration events

### 2. Manual Retry Triggers
```java
// Check TTL manually
Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

// Handle expiration manually
if (ttl != null && ttl <= 0) {
    handleWaitingTimeout(deliveryId);
}
```

### 3. Republish Logic
```java
private void republishFindShipperEvent(WaitingState waitingState) {
    // Create new FindShipperEvent
    // Call deliveryEventPublisher.publishFindShipperEvent()
    log.info("🔄 Republishing FindShipperEvent for delivery: {}", deliveryId);
}
```

## 📊 Monitoring & Metrics

### Redis Metrics
- ✅ Cache hit/miss ratio cho waiting states
- ✅ TTL expiration frequency
- ✅ Average waiting time before acceptance
- ✅ Retry attempts per delivery

### Business Metrics
- ✅ Shipper acceptance rate
- ✅ Time to acceptance (from found to accepted)
- ✅ Retry success rate
- ✅ Timeout frequency

## 🔮 Future Enhancements

### 1. Smart TTL Management
- [ ] Dynamic TTL dựa trên demand patterns
- [ ] Priority orders với TTL ngắn hơn
- [ ] Location-based TTL adjustment

### 2. Advanced Retry Logic
- [ ] Exponential backoff cho retry attempts
- [ ] Maximum retry limits per delivery
- [ ] Different retry strategies based on failure reasons

### 3. Redis Optimization
- [ ] Redis Keyspace Notifications để listen expiration
- [ ] Redis Streams cho ordered event processing
- [ ] Redis Clustering cho high availability

## ✅ Implementation Status

- [x] ShipperFoundEvent creation và publishing
- [x] Redis cache infrastructure với TTL
- [x] DeliveryWaitingService implementation
- [x] Event listeners cho shipper found events
- [x] DeliveryStatus enum updates với WAIT_SHIPPER_CONFIRM
- [x] Status transition logic updates
- [ ] TTL expiration handling implementation
- [ ] Republish FindShipperEvent on timeout
- [ ] Integration testing end-to-end

## 🧪 Testing Scenarios

### 1. Happy Path
1. Order created → FindShipperEvent
2. Shippers found → ShipperFoundEvent  
3. Cache waiting state → Redis TTL set
4. Shipper accepts → Cache removed

### 2. Timeout Scenarios
1. No shipper acceptance within TTL
2. Cache expires → Retry mechanism
3. Republish FindShipperEvent
4. Find new shippers or timeout again

### 3. Edge Cases
1. Redis connection failure
2. Multiple shippers accept simultaneously
3. Order cancelled during waiting
4. Service restart với pending cache

---

*📅 Created: October 6, 2024*  
*🔧 Status: Core Implementation Complete, TTL Handling Pending*  
*⚡ Redis Key Pattern: `delivery:waiting:{deliveryId}`*
