# 012 — View count livestream + phân biệt "minor" thật/giả

> Ngày: 2026-07-22 · Service: livestream-service (+ khảo sát analytics)
> Liên quan: [FEATURE_STATUS §6](../FEATURE_STATUS.md)

## Mình đã làm gì
Thêm **tổng lượt xem** (`viewCount`) cho livestream: tăng mỗi lần viewer join, hiển
thị ở `LivestreamResponse`. File: `Livestream.java`, `LivestreamService.java`,
`LivestreamResponse.java`.

## Kỹ thuật quan trọng

### 1. "Minor" trên giấy chưa chắc minor trong code — khảo sát trước khi hứa
Hai mục được coi là "minor" hoá ra khác nhau xa:
- **analytics topMenuItems**: analytics chỉ lưu **stats tổng hợp** (DailyOrderStats),
  không có dữ liệu theo món. Làm được → phải dựng **pipeline mới** (nghe order-item
  event, entity mới, aggregate). KHÔNG minor → dừng, ghi lại đúng scope.
- **livestream view count**: chia làm hai metric khác nhau (xem dưới) — làm được phần
  đơn giản, phần khó thì tách ra.
Bài học: đọc data model trước khi cam kết "làm nhanh". Ước lượng theo **dữ liệu sẵn
có**, không theo tên task.

### 2. Tách 2 metric dễ nhầm: cumulative vs concurrent
- **Tổng lượt xem** (cumulative): đếm dồn mỗi lần join. Đơn giản, chỉ cần một counter
  trên entity + increment. → làm.
- **Viewer đồng thời** (concurrent): số người đang xem NGAY BÂY GIỜ. Cần join+leave,
  hoặc heartbeat TTL (Redis), hoặc gọi Agora RTM API. Phức tạp hơn hẳn. → không fake,
  ghi TODO rõ.
Field cũ tên `currentViewers` (concurrent) — mình **không nhét** total vào đó để
tránh nhãn sai; total nằm ở field mới `viewCount`. Đặt tên đúng ngữ nghĩa quan trọng
hơn là "điền cho có".

### 3. Additive + Lombok/MapStruct = chi phí thấp
Entity dùng Lombok `@Getter/@Setter`, mapper MapStruct map theo tên → chỉ cần thêm
field ở entity + response là xong, không sửa accessor/mapper. `ddl-auto=update` tự
thêm cột. Non-breaking: field mới mặc định 0.

## Quyết định & đánh đổi
- **Không fake concurrent viewers** bằng total — thà thiếu còn hơn sai nhãn. Ghi rõ
  concurrent cần Agora/heartbeat làm follow-up.
- Mỗi join = 1 view (kể cả cùng một người join lại). Đơn giản; nếu cần "unique
  viewers" thì phải lưu set viewerId (nặng hơn) — chưa cần.

## Cạm bẫy / lỗi dễ mắc
- Nhét cumulative vào field tên `current*` → nhãn sai, người đọc hiểu nhầm.
- `viewCount` nullable → NPE khi increment. Đặt default `0L` + guard `!= null`.

## Cách kiểm chứng
- `mvn -o compile` livestream-service → **BUILD SUCCESS**.
- Khi chạy: mỗi lần `POST /livestreams/{id}/join` → `viewCount` +1, thấy ở list/get.

## Câu hỏi mở
- Concurrent viewers: dùng Redis heartbeat (TTL) hay Agora RTM presence?
- Unique views (theo viewerId) có cần không?
- topMenuItems: có đáng dựng pipeline order-item cho analytics không?

## Muốn đào sâu thêm
Từ khoá: "cumulative vs concurrent metrics", "presence counting Redis TTL heartbeat",
"Agora RTM online users", "estimate task by data availability".
