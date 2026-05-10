# Hướng dẫn sử dụng Docker cho dự án Backend Delivery

Tài liệu này hướng dẫn cách chạy toàn bộ hệ thống (Hạ tầng + 14 Microservices) bằng Docker Desktop.

## 1. Yêu cầu chuẩn bị
- Đã cài đặt **Docker Desktop**.
- Đã cài đặt **Maven** (để build các file `.jar`).
- Cấp đủ tài nguyên cho Docker Desktop (Khuyên dùng: ít nhất 8-12GB RAM).

## 2. Các bước khởi chạy nhanh

### Bước 1: Build các file JAR
Chạy lệnh này ở thư mục gốc của dự án backend để tạo ra các file thực thi cho Docker:
```bash
mvn clean package -DskipTests
```

### Bước 2: Khởi động hệ thống
Sử dụng lệnh sau để build các images và chạy toàn bộ containers:
```bash
docker-compose up -d --build
```

### Bước 3: Kiểm tra trạng thái
Kiểm tra xem các container đã chạy chưa:
```bash
docker-compose ps
```

## 3. Các lệnh hữu ích

| Lệnh | Mô tả |
|------|-------|
| `docker-compose up -d` | Chạy toàn bộ hệ thống ở chế độ background. |
| `docker-compose stop` | Dừng tất cả các containers (giữ lại trạng thái). |
| `docker-compose start` | Khởi động lại các containers đã dừng. |
| `docker-compose down` | Dừng và xóa toàn bộ containers + network. |
| `docker-compose logs -f [service-name]` | Xem log của một service cụ thể (vd: `auth-service`). |
| `docker-compose restart [service-name]` | Khởi động lại một service cụ thể. |

## 4. Cấu hình kết nối (Dành cho Dev local)
Nếu bạn chạy Database trong Docker nhưng muốn chạy Code bằng IDE (IntelliJ/Eclipse), hãy sử dụng thông tin sau:

- **PostgreSQL**: `localhost:5432` (User: `postgres`, Pass: `123`)
- **Redis**: `localhost:6379`
- **Kafka**: `localhost:29092` (Dùng port này cho local, port `9092` cho Docker)
- **Elasticsearch**: `localhost:9200`

## 5. Lưu ý quan trọng
- **Database**: Toàn bộ database (`auth_db`, `order_db`, ...) sẽ tự động được tạo khi Postgres khởi động lần đầu nhờ script `docker/postgres/init-db.sql`.
- **Thứ tự**: Một số service có thể khởi động thất bại trong lần đầu do Kafka hoặc Postgres chưa kịp sẵn sàng. Hãy đợi 30 giây rồi chạy `docker-compose start` để các service đó tự kết nối lại.
