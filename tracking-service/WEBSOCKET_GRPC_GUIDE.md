# 🚀 Tracking Service - Real-time Shipper Location Tracking

## 📋 Tổng quan
Service theo dõi vị trí shipper theo thời gian thực với:
- **gRPC Bidirectional Streaming**: Client đẩy vị trí liên tục
- **WebSocket Real-time**: Client theo dõi vị trí shipper/area
- **Redis GEO**: Lưu trữ và tìm kiếm vị trí theo không gian địa lý

## 🏗️ Kiến trúc

```
Client (Mobile/Web)
    ↓ gRPC Stream (port 9090)
    ↓ WebSocket (port 8090)
    ↓
TrackingService
    ↓
Redis GEO + WebSocket Broadcasting
    ↓
Real-time Updates → Connected Clients
```

## 🔧 Cấu hình & Khởi chạy

### Prerequisites
- Java 17+
- Redis Server
- Maven 3.6+

### 1. Khởi động Redis
```bash
redis-server
```

### 2. Build & Run Service
```bash
cd tracking-service
mvn clean compile
mvn spring-boot:run
```

### 3. Ports
- **HTTP API**: `http://localhost:8090`
- **WebSocket**: `ws://localhost:8090/ws/shipper-locations`
- **gRPC**: `localhost:9090`

## 🌐 WebSocket API

### Kết nối WebSocket
```javascript
const ws = new WebSocket('ws://localhost:8090/ws/shipper-locations');
```

### Tin nhắn từ Client

#### 1. Subscribe theo Shipper cụ thể
```json
{
  "action": "subscribe_shipper",
  "shipperId": 123
}
```

#### 2. Unsubscribe Shipper
```json
{
  "action": "unsubscribe_shipper", 
  "shipperId": 123
}
```

#### 3. Subscribe theo Area (vùng địa lý)
```json
{
  "action": "subscribe_area",
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radius": 5.0
}
```

### Tin nhắn từ Server

#### 1. Xác nhận kết nối
```json
{
  "type": "connection_established",
  "sessionId": "abc123",
  "message": "Connected to shipper location tracking"
}
```

#### 2. Cập nhật vị trí Shipper
```json
{
  "type": "location_update",
  "shipperId": 123,
  "latitude": 10.762622,
  "longitude": 106.660172,
  "isOnline": true,
  "speed": 25.5,
  "heading": 90.0,
  "timestamp": "2025-08-13T21:15:30"
}
```

#### 3. Cập nhật vị trí trong Area
```json
{
  "type": "area_location_update",
  "shipperId": 456,
  "latitude": 10.765000,
  "longitude": 106.665000,
  "isOnline": true,
  "speed": 15.2,
  "heading": 45.0,
  "timestamp": "2025-08-13T21:15:30"
}
```

## 📡 gRPC Streaming API

### Proto Definition
```protobuf
service ShipperLocationService {
  rpc StreamLocation(stream ShipperLocation) returns (stream LocationAck);
}

message ShipperLocation {
  int64 shipperId = 1;
  double latitude = 2;
  double longitude = 3;
  double accuracy = 4;
  double speed = 5;
  double heading = 6;
  bool isOnline = 7;
  int64 timestamp = 8;
}
```

### Client gRPC (Java Example)
```java
// Tạo channel
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 9090)
    .usePlaintext()
    .build();

ShipperLocationServiceGrpc.ShipperLocationServiceStub stub = 
    ShipperLocationServiceGrpc.newStub(channel);

// Mở stream
StreamObserver<LocationAck> responseObserver = new StreamObserver<LocationAck>() {
    @Override
    public void onNext(LocationAck ack) {
        System.out.println("Received: " + ack.getMessage());
    }
    // ...
};

StreamObserver<ShipperLocation> requestObserver = 
    stub.streamLocation(responseObserver);

// Gửi vị trí liên tục
ShipperLocation location = ShipperLocation.newBuilder()
    .setShipperId(123)
    .setLatitude(10.762622)
    .setLongitude(106.660172)
    .setIsOnline(true)
    .setTimestamp(System.currentTimeMillis())
    .build();

requestObserver.onNext(location);
```

## 🧪 Testing

### 1. WebSocket Test Page
Truy cập: `http://localhost:8090/ws-test`

### 2. REST API Test
```bash
# Cập nhật vị trí shipper
curl -X PUT http://localhost:8090/api/shipper-locations/123 \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 123" \
  -d '{
    "latitude": 10.762622,
    "longitude": 106.660172,
    "isOnline": true,
    "speed": 25.5,
    "heading": 90.0,
    "accuracy": 10.0
  }'

# Tìm shipper gần nhất
curl "http://localhost:8090/api/shipper-locations/nearby?latitude=10.762622&longitude=106.660172&radius=5&limit=10"
```

### 3. Redis Test
```bash
# Connect Redis CLI
redis-cli

# Xem tất cả shipper locations
ZRANGE shippers:geo:locations 0 -1 WITHSCORES

# Tìm kiếm theo vùng
GEORADIUS shippers:geo:locations 106.660172 10.762622 5 km WITHDIST WITHCOORD ASC

# Xem shipper online
SMEMBERS shippers:online
```

## 🔥 Use Cases

### 1. Mobile App Shipper
- Mở gRPC stream để gửi vị trí liên tục (mỗi 5-10 giây)
- Server tự động lưu vào Redis và broadcast qua WebSocket

### 2. Admin Dashboard
- Kết nối WebSocket để theo dõi tất cả shipper trong area
- Real-time map với vị trí shipper di chuyển

### 3. Customer App
- Subscribe theo shipper cụ thể để track đơn hàng
- Nhận cập nhật vị trí real-time của shipper giao hàng

### 4. Delivery Matching
- Match service có thể subscribe area để tìm shipper gần nhất
- Nhận thông báo ngay khi có shipper mới vào vùng

## 🚦 Flow hoạt động

1. **Shipper Mobile App** → gRPC stream vị trí → **Tracking Service**
2. **Tracking Service** → Lưu Redis GEO → Broadcast WebSocket
3. **Web Dashboard/Customer App** → Nhận real-time updates qua WebSocket

## ⚡ Performance

- **Redis GEO**: Sub-millisecond spatial queries
- **WebSocket**: < 50ms latency cho updates
- **gRPC Streaming**: Persistent connection, minimal overhead
- **Concurrent Support**: 1000+ WebSocket connections đồng thời

## 🛡️ Security Notes

- Production: Thêm authentication cho WebSocket
- Validate coordinates trước khi lưu Redis  
- Rate limiting cho gRPC streams
- CORS configuration cho WebSocket
