# Hướng dẫn sử dụng Docker cho dự án Backend Delivery

Tài liệu này tóm tắt cách chạy hạ tầng và 17 ứng dụng backend. Runbook chuẩn và
các lưu ý phục hồi nằm tại `docs/runbook-local.md`.

## 1. Yêu cầu chuẩn bị
- Đã cài đặt **Docker Desktop**.
- Đã cài đặt **Maven** (để build các file `.jar`).
- Cấp đủ tài nguyên cho Docker Desktop (Khuyên dùng: ít nhất 8-12GB RAM).

## 2. Các bước khởi chạy nhanh

### Bước 1: Tạo secret local và build JAR bằng JDK 17

```bash
bash scripts/gen-keys.sh
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn -DskipTests package
```

Dockerfile sẽ dừng build nếu `src/` hoặc Maven metadata mới hơn JAR, tránh chạy
nhầm source cũ. Có thể kiểm tra policy bằng
`bash scripts/verify-docker-artifact-freshness.sh`.

### Bước 2: Khởi động hệ thống
Sử dụng lệnh sau để build các images và chạy toàn bộ containers:
```bash
bash scripts/verify-compose-config.sh
docker compose -f docker-compose.yml -f docker-compose.secrets.yml up -d --build
```

### Bước 3: Kiểm tra trạng thái
Kiểm tra xem các container đã chạy chưa:
```bash
docker compose -f docker-compose.yml -f docker-compose.secrets.yml ps
```

## 3. Các lệnh hữu ích

| Lệnh | Mô tả |
|------|-------|
| `docker compose up -d` | Chạy toàn bộ hệ thống ở chế độ background. |
| `docker compose stop` | Dừng containers nhưng giữ volume dữ liệu. |
| `docker compose start` | Khởi động lại containers đã dừng. |
| `docker compose down` | Dừng và xóa containers/network, vẫn giữ named volume nếu không thêm `-v`. |
| `docker compose logs -f [service-name]` | Xem log của một service cụ thể. |
| `docker compose restart [service-name]` | Khởi động lại một service. |

## 4. Cấu hình kết nối local

Secret PostgreSQL và internal service được tạo trong `.env` bị Git ignore; không
dùng password mặc định trong source hoặc tài liệu. Các cổng hạ tầng local:

- **PostgreSQL**: `${POSTGRES_HOST_PORT:-5432}` (user `postgres`, password từ `.env`)
- **Redis**: `localhost:6379`
- **Kafka**: `localhost:29092` (container dùng `kafka:9092`)
- **Elasticsearch**: `localhost:9200`

Trong nhóm ứng dụng, chỉ Gateway được publish tại `http://localhost:8079`; client
không gọi trực tiếp cổng service nội bộ.

## 5. Lưu ý quan trọng
- **Database**: Toàn bộ database (`auth_db`, `order_db`, ...) sẽ tự động được tạo khi Postgres khởi động lần đầu nhờ script `docker/postgres/init-db.sql`.
- **Dữ liệu**: Không chạy `docker compose down -v` nếu không chủ đích xóa database
  local. Khi cần kiểm chứng startup, dùng `scripts/verify-runtime-startup.sh` để
  giữ đúng PostgreSQL/Kafka volume đang mount.
