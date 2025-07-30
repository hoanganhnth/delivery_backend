# Tracking Service - Simple Shipper Location Tracker

Dịch vụ tracking đơn giản để quản lý vị trí shipper theo thời gian thực sử dụng Redis.

## Chức năng chính

- **Cập nhật vị trí shipper**: Lưu latitude/longitude của shipper vào Redis
- **Lấy vị trí shipper**: Truy xuất vị trí hiện tại từ Redis  
- **Lấy danh sách shipper online**: Cho Match Service tìm shipper gần nhất
- **Đánh dấu offline**: Đặt trạng thái offline cho shipper

## API Endpoints

### 🔧 **Core APIs**

#### 1. Cập nhật vị trí (Shipper only)
```bash
POST /api/shipper-locations/update
Headers:
  X-User-Id: 123
  X-Role: SHIPPER
Body:
{
  "latitude": 10.762622,
  "longitude": 106.660172,
  "accuracy": 5.0,
  "speed": 0.0,
  "heading": 0.0,
  "isOnline": true
}
```

#### 2. Lấy vị trí shipper (Public)
```bash
GET /api/shipper-locations/{shipperId}
Response:
{
  "status": 1,
  "message": "Lấy vị trí thành công",
  "data": {
    "shipperId": 123,
    "latitude": 10.762622,
    "longitude": 106.660172,
    "accuracy": 5.0,
    "speed": 0.0,
    "heading": 0.0,
    "isOnline": true,
    "lastPing": "2025-07-30T10:30:00",
    "updatedAt": "2025-07-30T10:30:00"
  }
}
```

#### 3. Lấy danh sách shipper online (For Match Service)
```bash
GET /api/shipper-locations/online
Response:
{
  "status": 1,
  "message": "Lấy danh sách shipper online thành công",
  "data": [
    {
      "shipperId": 123,
      "latitude": 10.762622,
      "longitude": 106.660172,
      "isOnline": true,
      "lastPing": "2025-07-30T10:30:00"
    }
  ]
}
```

#### 4. Đánh dấu offline
```bash
POST /api/shipper-locations/offline
Headers:
  X-User-Id: 123
  X-Role: SHIPPER
```

### Health Check
```bash
GET /api/health
```

## Cấu hình

### Redis
- Host: localhost
- Port: 6379
- Database: 0
- TTL: 5 phút cho mỗi vị trí

### Dependencies
- Spring Boot Web
- Spring Data Redis
- Lombok
- Validation

## Workflow sử dụng

### 🚀 **Cho Shipper App**
1. **Login**: Shipper mở app
2. **Start tracking**: Gọi `/update` mỗi 10-30 giây
3. **Stop tracking**: Gọi `/offline` khi tắt app

### 🎯 **Cho Match Service**  
1. **Tìm shipper**: Gọi `/online` để lấy danh sách
2. **Tính khoảng cách**: Dùng lat/lng để tìm shipper gần nhất
3. **Assign order**: Chọn shipper phù hợp

### 📱 **Cho Customer App**
1. **Track order**: Gọi `/api/shipper-locations/{shipperId}` 
2. **Show map**: Hiển thị vị trí real-time của shipper

## Chạy service

1. Khởi động Redis server
2. Chạy ứng dụng:
   ```bash
   mvn spring-boot:run
   ```

Service sẽ khởi động trên port **8090**.

## Performance

✅ **Redis Only**: Không cần database, chỉ Redis  
✅ **Fast**: Sub-millisecond response time  
✅ **Simple**: Ít dependencies, dễ maintain  
✅ **Auto-cleanup**: TTL 5 phút tự động xóa dữ liệu cũ  
✅ **Lightweight**: Minimal resource usage  

## Lưu trữ dữ liệu

- **Redis**: Lưu trữ vị trí hiện tại với TTL 5 phút
- **Key pattern**: `shipper:location:{shipperId}`
- **Tự động xóa**: Dữ liệu cũ sẽ tự động hết hạn
- **No database**: Không cần PostgreSQL hay MongoDB
