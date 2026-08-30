# Architecture Hub Design

## Goal

Biến `/system-overview/architecture` thành một Architecture Hub nhiều góc nhìn,
giúp người đọc đi từ bối cảnh hệ thống tới runtime, domain, dữ liệu/sự kiện và
chi tiết từng service mà không nhồi toàn bộ thông tin vào một canvas.

## Decisions

- `Bối cảnh hệ thống` là view mặc định cho người mới: actor/client → Gateway → Delivery Platform → external integrations.
- `Runtime topology` là view chính cho developer: client, public edge, service theo domain, data/event plane và control plane.
- `Domain & capability` là view nối chức năng với service và client, không dùng workflow step làm node chính.
- `Dữ liệu & sự kiện` là view ownership/projection/topic, phân biệt source of truth, cache và event.
- Flow Explorer tiếp tục là trang riêng; Architecture chỉ có liên kết flow liên quan.
- Mọi dữ liệu vẫn lấy từ snapshot allowlist/canonical hiện có; không invent API, topic, database hay production topology.
- Gated/experimental hiển thị trong nhóm đóng mặc định, có nhãn trạng thái rõ ràng.

## Visual model

- Header có breadcrumb, trạng thái snapshot và view switcher.
- Canvas bên trái, inspector bên phải trên desktop; mobile dùng accordion và relationship list.
- Mỗi view có lane/bounded context riêng, edge filter theo loại giao tiếp và legend cố định.
- Node hiển thị tên kỹ thuật, tên dễ hiểu, domain, status và data-owner badge khi có.

## Acceptance

- Người mới mở trang thấy ngay ai dùng hệ thống và Gateway là public edge duy nhất.
- Developer chuyển Runtime để thấy service group và các quan hệ chính.
- Click node/edge vẫn mở inspector và deep-link query state.
- Chọn view không làm mất flow lens, search, mobile fallback hoặc các section handbook khác.
- Không có horizontal overflow trên mobile; không có business mutation/live backend call.
