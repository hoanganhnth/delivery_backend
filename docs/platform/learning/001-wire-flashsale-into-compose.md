# 001 — Ghép flashsale-service vào docker-compose

> Ngày: 2026-07-22 · Service: flashsale-service, order-service, api-gateway
> Liên quan: [TESTING_READINESS](../TESTING_READINESS.md)

## Mình đã làm gì
Đưa `flashsale-service` (trước đó bị thiếu hoàn toàn trong `docker-compose.yml`)
vào cụm chạy chung, và sửa order-service để gọi được các service khác trong mạng Docker.
File đổi: `docker-compose.yml`, `flashsale-service/pom.xml`, `docker/postgres/init-db.sql`.

## Kỹ thuật quan trọng

### 1. Service discovery "nghèo" bằng DNS của Docker network
Trong một Docker Compose network, mỗi service gọi nhau bằng **tên service** làm
hostname (`http://restaurant-service:8083`), không phải `localhost`. Đây là cạm bẫy
kinh điển: code default `@Value("${restaurant.service.url:http://localhost:8083}")`
chạy tốt khi dev trên máy, nhưng trong container `localhost` = **chính container đó**
→ gọi thất bại. Phải override URL qua biến môi trường khi chạy trong compose.

- order-service thiếu 3 env (`RESTAURANT_SERVICE_URL`, `FLASHSALE_SERVICE_URL`,
  `PROMOTION_SERVICE_URL`) → tạo đơn sẽ fail vì không gọi được restaurant validate.

### 2. Relaxed binding: env var → property
Spring map `RESTAURANT_SERVICE_URL` (env) ↔ `restaurant.service.url` (property):
UPPER_SNAKE_CASE, dấu chấm thành gạch dưới. Nhờ vậy override được config mà không
sửa code. Tương tự `SPRING_DATASOURCE_URL` ↔ `spring.datasource.url`.

### 3. Đổi DB engine mà không sửa code: override driver qua env
flashsale viết cho MySQL (`spring.datasource.driver-class-name=com.mysql...`), cả
hệ thống lại dùng Postgres. Thay vì sửa `application.properties`, mình:
- Thêm dependency `org.postgresql:postgresql` vào pom (driver phải có trên classpath).
- Override 4 biến env trong compose: `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` +
  `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver`.
- Không cần khai báo Hibernate dialect: Hibernate 6 tự nhận dialect từ JDBC metadata.

### 4. Xử lý xung đột cổng: tách host port vs container port
flashsale mặc định `server.port=8092`, trùng với match-service. Trong Docker mỗi
container có không gian cổng riêng nên **cổng nội bộ trùng nhau không sao**; chỉ
cổng **host** mới xung đột. Giải: `ports: ["8098:8092"]` — host 8098 → container 8092.
Gateway và order-service gọi bằng cổng **nội bộ** (`flashsale-service:8092`).

## Quyết định & đánh đổi
- **Chọn chuyển flashsale sang Postgres** thay vì thêm hẳn một container MySQL: giữ
  một loại DB engine cho toàn hệ thống → vận hành/backup đơn giản hơn. Đánh đổi: phải
  thêm driver + tin vào override env (application.properties vẫn ghi MySQL, dễ gây nhầm
  khi chạy local — xem câu hỏi mở).

## Cạm bẫy / lỗi dễ mắc
- Quên tạo database trong Postgres: Postgres **không** tự tạo DB từ URL (khác MySQL
  có `createDatabaseIfNotExist=true`). Phải thêm `CREATE DATABASE flashsale_db;` vào
  `init-db.sql`. Lưu ý init script chỉ chạy khi volume postgres **còn trống** — nếu đã
  chạy trước đó phải `docker compose down -v` hoặc tạo DB thủ công.
- Override driver mà quên thêm dependency driver → `ClassNotFoundException` lúc boot.

## Cách kiểm chứng
- `docker compose config --quiet` → EXIT 0 (cú pháp hợp lệ).
- Sau khi chạy: `docker compose logs flashsale-service` thấy connect Postgres OK;
  đặt đơn có `flashSaleItemId` → gọi `/api/flashsales/internal/reserve` thành công.

## Câu hỏi mở
- Nên sửa luôn `application.properties` của flashsale sang Postgres (đồng bộ default),
  hay giữ MySQL cho local? Hiện đang lệch giữa default (MySQL) và runtime (Postgres).
- promotion-service trong compose trỏ `delivery_db` chứ không phải `promotion_db` —
  có chủ ý không? (một điểm lệch sẵn có, chưa đụng tới)

## Muốn đào sâu thêm
Từ khoá: "Spring Boot relaxed binding environment variables", "Docker Compose
networking service name DNS", "Hibernate automatic dialect resolution".
