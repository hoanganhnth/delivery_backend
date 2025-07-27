# Shipper Service

Shipper Service là một microservice trong hệ thống DeliveryVN, chịu trách nhiệm quản lý thông tin và hoạt động của các shipper.

## Chức năng chính

### 1. Quản lý Shipper
- **Tạo Shipper**: Đăng ký shipper mới với thông tin cá nhân và phương tiện
- **Cập nhật thông tin Shipper**: Cập nhật thông tin cá nhân, phương tiện, hình ảnh
- **Xóa Shipper**: Xóa tài khoản shipper
- **Xem thông tin Shipper**: Lấy thông tin chi tiết shipper theo ID hoặc User ID

### 2. Quản lý vị trí (Location)
- **Cập nhật vị trí**: Shipper cập nhật vị trí hiện tại (lat, lng)
- **Xem vị trí**: Lấy vị trí hiện tại của shipper
- **Tìm shipper gần**: Tìm shipper trong bán kính nhất định
- **Xóa vị trí**: Xóa thông tin vị trí của shipper

### 3. Quản lý số dư (Balance)
- **Xem số dư**: Hiển thị số dư hiện tại và số tiền đang giữ tạm
- **Nạp tiền**: Nạp tiền vào tài khoản shipper
- **Rút tiền**: Rút tiền từ tài khoản shipper
- **Giữ tạm tiền**: Giữ tạm một số tiền (khi nhận đơn)
- **Giải phóng tiền**: Giải phóng tiền đã giữ tạm
- **Kiếm tiền từ đơn hàng**: Cộng tiền thưởng từ việc giao đơn thành công
- **Lịch sử giao dịch**: Xem tất cả giao dịch của shipper

### 4. Quản lý trạng thái Online
- **Cập nhật trạng thái Online/Offline**: Shipper có thể bật/tắt trạng thái sẵn sàng nhận đơn
- **Lấy danh sách Shipper Online**: Xem tất cả shipper đang online

### 5. Thống kê và đánh giá
- **Rating**: Lưu trữ điểm đánh giá của shipper
- **Số đơn hoàn thành**: Theo dõi số lượng đơn hàng đã giao thành công

## API Endpoints

### Shipper Management
```
POST   /api/shippers                    - Tạo shipper mới
GET    /api/shippers/{id}               - Lấy thông tin shipper theo ID
GET    /api/shippers/user/{userId}      - Lấy thông tin shipper theo User ID
GET    /api/shippers                    - Lấy tất cả shipper
PUT    /api/shippers/{id}               - Cập nhật thông tin shipper
DELETE /api/shippers/{id}               - Xóa shipper
```

### Online Status Management
```
GET    /api/shippers/online             - Lấy danh sách shipper online
PATCH  /api/shippers/{id}/online-status - Cập nhật trạng thái online
```

### Shipper Location Management
```
PUT    /api/shipper-locations/shipper/{shipperId}     - Cập nhật vị trí shipper
GET    /api/shipper-locations/shipper/{shipperId}     - Lấy vị trí hiện tại của shipper
GET    /api/shipper-locations/nearby                  - Tìm shipper gần (lat, lng, radiusKm)
DELETE /api/shipper-locations/shipper/{shipperId}     - Xóa vị trí shipper
```

### Shipper Balance Management
```
GET    /api/shipper-balances/shipper/{shipperId}              - Xem số dư
POST   /api/shipper-balances/shipper/{shipperId}              - Tạo số dư cho shipper mới
POST   /api/shipper-balances/shipper/{shipperId}/deposit      - Nạp tiền
POST   /api/shipper-balances/shipper/{shipperId}/withdraw     - Rút tiền
POST   /api/shipper-balances/shipper/{shipperId}/hold         - Giữ tạm tiền
POST   /api/shipper-balances/shipper/{shipperId}/release      - Giải phóng tiền tạm
POST   /api/shipper-balances/shipper/{shipperId}/earn         - Cộng tiền từ đơn hàng
GET    /api/shipper-balances/shipper/{shipperId}/transactions - Lịch sử giao dịch
```

## Database Schema

### Shipper Table
```sql
CREATE TABLE shipper (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    user_id BIGINT NOT NULL,
    vehicle_type VARCHAR(50),
    license_number VARCHAR(50),
    id_card VARCHAR(20),
    driver_image TEXT,
    is_online BOOLEAN DEFAULT FALSE,
    rating DECIMAL(2,1) DEFAULT 5.0,
    completed_deliveries INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Shipper Locations Table
```sql
CREATE TABLE shipper_locations (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    shipper_id BIGINT NOT NULL UNIQUE,
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipper_id) REFERENCES shipper(id) ON DELETE CASCADE
);
```

### Shipper Balances Table
```sql
CREATE TABLE shipper_balances (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    shipper_id BIGINT NOT NULL UNIQUE,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    holding_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipper_id) REFERENCES shipper(id) ON DELETE CASCADE
);
```

### Shipper Transactions Table
```sql
CREATE TABLE shipper_transactions (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    shipper_id BIGINT NOT NULL,
    related_order_id BIGINT,
    transaction_type VARCHAR(20) NOT NULL, -- DEPOSIT, WITHDRAW, EARN, PENALTY, HOLD, RELEASE
    amount DECIMAL(12,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shipper_id) REFERENCES shipper(id) ON DELETE CASCADE
);
```
    rating DECIMAL(2,1) DEFAULT 5.0,
    completed_deliveries INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Configuration

### Port
Service chạy trên port **8086**

### Database
- Database name: `shipper_db`
- Connection: PostgreSQL
- URL: `jdbc:postgresql://localhost:5432/shipper_db`

## Request/Response Examples

### Tạo Shipper mới
```bash
POST /api/shippers
Headers:
  X-User-Id: 1
  X-Role: USER
  Content-Type: application/json

Body:
{
  "userId": 1,
  "vehicleType": "MOTORBIKE",
  "licenseNumber": "B1-123456",
  "idCard": "123456789012",
  "driverImage": "https://example.com/driver.jpg"
}

Response:
{
  "status": 1,
  "message": "Thành công",
  "data": {
    "id": 1,
    "userId": 1,
    "vehicleType": "MOTORBIKE",
    "licenseNumber": "B1-123456",
    "idCard": "123456789012",
    "driverImage": "https://example.com/driver.jpg",
    "isOnline": false,
    "rating": 5.0,
    "completedDeliveries": 0,
    "createdAt": "2025-01-27T10:00:00",
    "updatedAt": "2025-01-27T10:00:00"
  }
}
```

### Cập nhật thông tin Shipper
```bash
PUT /api/shippers/1
Headers:
  X-User-Id: 1
  Content-Type: application/json

Body:
{
  "vehicleType": "CAR",
  "licenseNumber": "C-654321",
  "isOnline": true
}

Response:
{
  "status": 1,
  "message": "Thành công",
  "data": {
    "id": 1,
    "userId": 1,
    "vehicleType": "CAR",
    "licenseNumber": "C-654321",
    "idCard": "123456789012",
    "driverImage": "https://example.com/driver.jpg",
    "isOnline": true,
    "rating": 5.0,
    "completedDeliveries": 0,
    "createdAt": "2025-01-27T10:00:00",
    "updatedAt": "2025-01-27T10:05:00"
  }
}
```

### Cập nhật trạng thái Online
```bash
PATCH /api/shippers/1/online-status?isOnline=true
Headers:
  X-User-Id: 1

Response:
{
  "status": 1,
  "message": "Thành công",
  "data": {
    "id": 1,
    "userId": 1,
    "vehicleType": "CAR",
    "licenseNumber": "C-654321",
    "isOnline": true,
    ...
  }
}
```

## Security và Authorization

### Quyền truy cập
- **ADMIN**: Có thể tạo shipper cho bất kỳ user nào
- **USER**: Chỉ có thể tạo/cập nhật/xóa shipper cho chính mình
- **SHIPPER**: Có thể cập nhật thông tin cá nhân và trạng thái online

### Headers yêu cầu
- `X-User-Id`: ID của user thực hiện request
- `X-Role`: Role của user (ADMIN, USER, SHIPPER)

## Validation Rules

### Tạo Shipper
- `userId`: Không được null, phải là số dương
- `licenseNumber`: Không được trùng lặp trong hệ thống
- `idCard`: Không được trùng lặp trong hệ thống
- User chỉ được tạo một shipper profile

### Cập nhật Shipper
- Chỉ shipper owner hoặc admin mới có thể cập nhật
- `licenseNumber` và `idCard` không được trùng với shipper khác

## Error Handling

### Common Error Responses
```json
{
  "status": 0,
  "message": "Error message",
  "data": null
}
```

### Các lỗi thường gặp
- **404**: Không tìm thấy shipper
- **403**: Không có quyền truy cập
- **400**: Dữ liệu đầu vào không hợp lệ
- **409**: Conflict (trùng lặp license number, ID card)

## Development

### Build và Run
```bash
# Build project
mvn clean compile

# Run tests
mvn test

# Package
mvn package

# Run application
mvn spring-boot:run
```

### Dependencies
- Spring Boot 3.5.3
- Spring Data JPA
- PostgreSQL Driver
- MapStruct (Object Mapping)
- Lombok
- H2 Database (Testing)

## Architecture Notes

Service này được xây dựng theo chuẩn architecture patterns của DeliveryVN microservices:
- **Layered Architecture**: Controller → Service → Repository
- **DTO Pattern**: Sử dụng Request/Response DTOs
- **MapStruct**: Tự động mapping Entity ↔ DTO
- **Global Exception Handling**: Centralized error handling
- **BaseResponse Wrapper**: Consistent API response format
- **Constants Classes**: Centralized constants management
