# 011 — Thêm fullName cho shipper (bỏ placeholder "Shipper #id")

> Ngày: 2026-07-22 · Service: shipper-service
> Liên quan: [FEATURE_STATUS §6](../FEATURE_STATUS.md)

## Mình đã làm gì
Thêm field `fullName` vào Shipper (entity + CreateShipperRequest + ShipperResponse)
và dùng nó trong search sync thay cho placeholder `"Shipper #" + id`.
File: `Shipper.java`, `CreateShipperRequest.java`, `ShipperResponse.java`,
`SearchSyncPublisher.java`.

## Kỹ thuật quan trọng

### 1. Denormalize có chủ đích vs single-source-of-truth
Tên thật của shipper nằm ở **user-service** (theo `userId`). Hai lựa chọn:
- Gọi user-service mỗi lần cần tên (đúng chuẩn "một nguồn sự thật", nhưng thêm
  network call + coupling).
- **Denormalize**: lưu `fullName` ngay trong shipper (chọn cái này). Đổi lại phải
  chấp nhận dữ liệu có thể lệch nếu user đổi tên. Với hiển thị search, denormalize
  hợp lý (đọc nhiều, đổi tên hiếm). Chọn theo pattern đọc/ghi, không giáo điều.

### 2. Thay đổi additive, không phá cũ
`fullName` nullable; search có **fallback**: null/blank → vẫn `"Shipper #id"`. Nên:
shipper cũ chưa có tên không lỗi; client mới gửi tên thì hiện tên thật. Đổi schema
mà **không cần migration thủ công** nhờ `ddl-auto=update` (Hibernate tự thêm cột).

### 3. MapStruct map theo tên — thêm field là đủ
`@Mapper toEntity(request)` và `toResponse(shipper)` tự map `fullName` vì **trùng
tên** ở cả 3 lớp. Không phải sửa mapper. (Điều kiện: cả 3 lớp đều có getter/setter
đúng tên — ở đây viết tay nên phải thêm accessor thủ công, không có Lombok.)

## Quyết định & đánh đổi
- **Denormalize** thay vì gọi user-service: đơn giản, không thêm coupling/độ trễ.
  Follow-up nếu cần: sync lại `fullName` khi user đổi tên (nghe event user-updated).
- Không thêm vào `UpdateShipperRequest` lần này (giữ scope nhỏ) — có thể bổ sung sau.

## Cạm bẫy / lỗi dễ mắc
- Class viết getter/setter tay (không Lombok) → thêm field mà quên accessor thì
  MapStruct **im lặng bỏ qua** field đó. IDE cảnh báo "field not used" là dấu hiệu.
- `ddl-auto=update` tiện cho dev nhưng **không dùng cho production** (không xoá/əđổi
  cột an toàn) — prod nên Flyway/Liquibase.

## Cách kiểm chứng
- `mvn -o compile` shipper-service → **BUILD SUCCESS**.
- Khi chạy: tạo shipper có `fullName` → search hiển thị tên thật; không có → "Shipper #id".

## Câu hỏi mở
- Có sync `fullName` khi user đổi tên ở user-service không (nghe event)?
- Migration production: chuyển từ ddl-auto sang Flyway.

## Muốn đào sâu thêm
Từ khoá: "denormalization read-heavy", "MapStruct field mapping by name",
"Hibernate ddl-auto update vs Flyway production".
