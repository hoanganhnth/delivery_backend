# 007 — Vá lỗ hổng phân quyền user-service (IDOR)

> Ngày: 2026-07-22 · Service: user-service
> Liên quan: [FEATURE_STATUS §6](../FEATURE_STATUS.md), [SECURITY.md](../../../SECURITY.md)

## Mình đã làm gì
Thêm kiểm tra quyền cho `PUT /api/users/{id}` và `DELETE /api/users/{id}` — trước đó
**không có check nào**, ai đăng nhập cũng sửa/xoá được user bất kỳ. Giờ chỉ chính chủ
hoặc ADMIN. File: `UserController.java`.

## Kỹ thuật quan trọng

### 1. IDOR — Insecure Direct Object Reference
Endpoint nhận `{id}` từ URL rồi thao tác thẳng, **không kiểm tra id đó có thuộc về
người gọi không**. Kẻ tấn công chỉ cần đổi số id là sửa/xoá tài nguyên người khác.
Đây là một trong các lỗ hổng web phổ biến nhất (OWASP "Broken Access Control").
Fix: so khớp `id` với danh tính người gọi (`AuthenticatedActor` được User Service
derive từ Bearer JWT đã validate qua JWKS) hoặc ADMIN.

### 2. Cái được flag chưa chắc là lỗ hổng thật
Comment `TODO: Add authorization check` nằm ở endpoint `/admin/statistics` — nhưng
check ADMIN **đã có ngay bên dưới**. Lỗ hổng thật lại ở chỗ **không** được flag:
`PUT`/`DELETE` CRUD. Bài học: đừng tin comment, đọc luồng thực tế; quét **tất cả**
endpoint mutation xem cái nào thiếu guard, không chỉ cái có TODO.

### 3. Ranh giới internal vs external
`POST /api/users` (createUser) và `/admin/*` được **auth-service gọi nội bộ**
(đăng ký sync; block/unblock với `Internal-Token` và payload audit). Nếu khoá
createUser theo role sẽ **phá luồng đăng ký**. Phải phân biệt endpoint nào là
service-to-service, endpoint nào là client-facing trước khi siết — siết nhầm gây
outage. Mình chỉ siết đúng 2 endpoint client-facing nguy hiểm.

## Quyết định & đánh đổi
- **Chỉ vá PUT/DELETE** (mutation/destructive, rõ ràng phải owner-or-admin), không
  đụng createUser (internal) → an toàn, không phá đăng ký.
- **Để lại `GET /by-auth/{authId}`**: cũng là IDOR (đọc), nhưng chưa rõ ai gọi
  (nghi ngờ frontend dùng sau login để lấy profile theo authId). Khoá cứng có thể
  phá app. Verify ownership cần map `userId→authId` (không có trong header). Ghi
  follow-up thay vì siết mù.

## Cạm bẫy / lỗi dễ mắc
- Siết endpoint internal theo role → gọi service-to-service (không có header role) bị 403.
- Không lấy actor từ client-supplied identity header; service-to-service calls dùng
  `Internal-Token` ở private route và payload được kiểm tra riêng. Với user request,
  yêu cầu actor không null trước khi so owner để không cho qua nhầm.

## Cách kiểm chứng
- `mvn -o compile` user-service → **BUILD SUCCESS**.
- Khi chạy: user A (Bearer JWT có `sub=A`) gọi `PUT /api/users/B` → 403; gọi
  `PUT /api/users/A` hoặc ADMIN → OK.

## Câu hỏi mở
- `GET /by-auth/{authId}` — vẫn là IDOR đọc. Nên: thêm authId vào JWT claim để verify
  ownership, hoặc chuyển thành endpoint internal (Internal-Token) như auth-service.
- `POST /api/users` (createUser) nên bảo vệ bằng Internal-Token thay vì để mở.

## Muốn đào sâu thêm
Từ khoá: "IDOR OWASP Broken Access Control", "object-level authorization",
"service-to-service auth internal endpoints", "BOLA API security".
