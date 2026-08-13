# 🛠️ Technical Bottlenecks & Solutions (Xử lý luồng kỹ thuật khó)

Tài liệu này ghi chú lại những "điểm nghẽn" (bottlenecks) nguy hiểm trong kiến trúc hệ thống và cách xử lý, nhằm mục đích làm cẩm nang (playbook) khi team scale dự án lên lượng user lớn.

Khác với các file workflow khác tập trung vào nghiệp vụ, file này tập trung thuần túy vào **Hiệu năng (Performance)** và **Độ ổn định (Reliability)**.

---

## 1. Bài toán Tràn RAM (OOM) do Unpaginated Queries
**🔍 Vấn đề:**
Ở `ShipperService` và `OrderService`, các hàm như `getAllShippers()` hay `adminCancelAllNonTerminalOrders()` sử dụng lệnh `findAll()` của Spring Data JPA.
- Nếu hệ thống có 100,000 shipper, lệnh này sẽ kéo **toàn bộ** 100,000 records từ MySQL vào RAM JVM.
- Sau đó map qua DTO và parse thành một file JSON khổng lồ trả về cho Client. Hệ quả: Ăn sạch Heap Memory, Garbage Collector (GC) bị pause liên tục, làm crash toàn bộ Node.

**💡 Giải pháp (Best Practice):**
- **Luôn luôn dùng Pagination:** Bắt buộc thay thế bằng `Pageable` (`PageRequest.of(page, size)`).
- **Keyset Pagination (Cursor):** Với dữ liệu thay đổi siêu nhanh (như order list), dùng Cursor Pagination thay cho Offset Pagination để tránh bị bỏ sót hoặc trùng lặp bản ghi khi trang bị dịch chuyển.

---

## 2. Bài toán "Bức tử" Database (Write Heavy) trong Tracking
**🔍 Vấn đề:**
`ShipperLocationServiceImpl.updateLocationByUserId` hiện tại đang gọi `repository.save()` (MySQL) cho mỗi lần Shipper cập nhật vị trí.
- Một Shipper gửi vị trí mỗi 5 giây. Với 1,000 Shipper đang chạy, MySQL phải gánh `200 IOPS` (lệnh UPDATE) chỉ riêng cho tracking.
- Connection Pool bị cạn kiệt, DB Disk I/O tăng vọt 100%, làm chậm toàn bộ các chức năng khác (như tạo Order).

**💡 Giải pháp (Best Practice):**
- **Tách Write-Store:** Sử dụng **Redis (GEO Hash)** làm Primary Store cho vị trí Real-time. App chỉ đẩy vị trí lên Redis qua WebSocket/HTTP.
- **Batch Sync (Write-behind):** Dùng một Background Job (Scheduler) hoặc Kafka Consumer gom các vị trí thay đổi (Batch) và bulk-update xuống MySQL/PostgreSQL mỗi 1 - 5 phút/lần.

---

## 3. Bài toán "Kẹt xe" trong Kafka (Head-of-Line Blocking)
**🔍 Vấn đề:**
Trong `Match Service`, khi không tìm thấy Shipper, hệ thống chờ một khoảng thời gian (Backoff retry). Nếu dùng `Thread.sleep()` hoặc vòng lặp delay trực tiếp bên trong `@KafkaListener`.
- Thread consume của partition Kafka đó sẽ bị Block hoàn toàn trong 5 - 50 phút.
- Mọi event (đơn hàng mới, notification) được đẩy vào cùng Partition đó sẽ xếp hàng chờ (kẹt xe) vô thời hạn.

**💡 Giải pháp (Best Practice):**
- Tuyệt đối không block Consumer Thread.
- **Sử dụng Delayed Topic / Dead Letter Queue (DLQ):** Khi cần retry, đẩy event sang một Topic đặc biệt chuyên xử lý trễ (Ví dụ: dùng `RabbitMQ Delayed Message Plugin` hoặc lưu trạng thái vào Redis kèm TTL, khi hết TTL sinh ra event retry mới).

---

## 4. Bài toán "Bão Broadcast" trong WebSocket
**🔍 Vấn đề:**
`ShipperLocationWebSocketHandler.broadcastAreaLocationUpdate()` thực hiện vòng lặp qua **toàn bộ các kết nối đang mở (activeSessions)** mỗi khi có 1 Shipper cập nhật vị trí, kèm theo phép toán lượng giác (Haversine) để xem Shipper có nằm trong vùng của User không.
- Chạy độ phức tạp `O(N_Shipper * N_User)` mỗi giây trên Main Thread của Node. Rất tốn CPU.

**💡 Giải pháp (Best Practice):**
- **Áp dụng Pub/Sub Rooms theo Geo-hash:**
  - Chia bản đồ thành các ô vuông (Geo-hash grid). Ví dụ ô lưới có mã `w21z`.
  - Khi Customer mở app ở ô `w21z`, tự động Subscribe vào WebSocket channel `/topic/geohash/w21z`.
  - Khi Shipper chạy vào ô `w21z`, Server chỉ Broadcast thông điệp tới những ai đang trong Channel đó (độ phức tạp O(1) qua map tra cứu). Không cần chạy vòng lặp tính khoảng cách trên server.

---

## 5. Security: Lỗ hổng giả mạo Định danh (Identity Spoofing)
**🔍 Vấn đề:**
Mỗi resource service xác thực Bearer JWT qua JWKS của Auth và tự dựng actor từ
claims. Gateway không inject identity; nó chỉ strip các header legacy trước khi
forward request.
- Nếu Hacker chủ động gửi kèm `X-User-Id: 1` hoặc `X-Role: ADMIN` từ bên ngoài
Internet, các header này không được dùng để xác thực/quyền và Gateway loại bỏ
chúng trước routing.

**💡 Giải pháp (Best Practice):**
- Tại API Gateway: **BẮT BUỘC** remove (strip) toàn bộ header
  `X-User-Id`, `X-User-Role` xuất phát từ Client; Gateway không parse hoặc
  inject JWT identity.
- Tại resource service: chỉ dùng `AuthenticatedActor` sinh từ JWT đã kiểm tra
  RS256, `kid`, issuer, audience và token type qua JWKS.
