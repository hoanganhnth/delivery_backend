# 004 — Phân trang cho getAllShippers (chống OOM)

> Ngày: 2026-07-22 · Service: shipper-service
> Liên quan: [priority-roadmap](../plans/active/priority-roadmap.md) Sprint 2

## Mình đã làm gì
Đổi `getAllShippers()` từ trả `List` (dùng `findAll()`) sang trả `Page` với
`Pageable`. File: `ShipperService.java` (interface), `ShipperServiceImpl.java`,
`ShipperController.java`.

## Kỹ thuật quan trọng

### 1. Vì sao `findAll()` nguy hiểm ở production
`findAll()` nạp **toàn bộ bảng vào RAM** một lần. Với vài chục shipper thì không sao,
nhưng khi dữ liệu lớn (chục nghìn+) → một request có thể ngốn hết heap → `OutOfMemoryError`,
kéo sập cả service (và ảnh hưởng request khác). Đây là "quả bom hẹn giờ": chạy tốt
lúc demo, nổ khi scale.

### 2. `Pageable` của Spring Data — gần như miễn phí
`JpaRepository` có sẵn `findAll(Pageable)`. Controller chỉ cần thêm tham số `Pageable`
→ Spring tự bind từ query param `?page=0&size=20&sort=id,desc`. Không cần code parse.
Trả `Page<T>` kèm luôn metadata (tổng số trang, tổng phần tử) cho client.

### 3. `Page.map()` giữ nguyên phân trang khi đổi kiểu
`repository.findAll(pageable).map(mapper::toResponse)` — biến `Page<Shipper>` thành
`Page<ShipperResponse>` mà **không mất** thông tin phân trang. Gọn hơn nhiều so với
lấy List rồi stream/collect.

## Quyết định & đánh đổi
- **Đây là breaking change của API** (response đổi từ mảng phẳng sang object có
  `content[]` + metadata). Client (admin web) gọi `GET /api/shippers` cần đọc
  `data.content` thay vì `data`. Đánh đổi chấp nhận được vì lợi ích chống OOM; đã
  ghi để đồng bộ frontend.

## Cạm bẫy / lỗi dễ mắc
- Đổi kiểu trả về mà quên cập nhật **mọi caller** → lỗi biên dịch/runtime. Mình grep
  `getAllShippers` toàn repo: chỉ 3 chỗ nội bộ, không caller ngoài → an toàn.
- Quên import `Page`/`Pageable` (org.springframework.data.domain) — lỗi biên dịch
  ngay (IDE báo). Nhớ import ở cả interface, impl, controller.

## Cách kiểm chứng
- `mvn -o compile` shipper-service → **BUILD SUCCESS**.
- Khi chạy: `GET /api/shippers?page=0&size=10` trả `content[]` + `totalElements`.

## Câu hỏi mở
- Cần đồng bộ admin web đọc `data.content` (breaking change) — ai làm phía FE?
- Các list khác còn `findAll` ở service khác không? (đã fix order từ trước; nên rà tiếp
  restaurant/menu nếu có bảng lớn.)

## Muốn đào sâu thêm
Từ khoá: "Spring Data Pageable web binding", "Page vs Slice", "OOM findAll pagination".
