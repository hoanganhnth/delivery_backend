# Delivery Web MVP — Tính năng và màn hình hiện tại

Web portal hiện phục vụ ba actor/surface: khách hàng, quản trị viên hệ thống và
chủ nhà hàng. Trang kiến trúc hệ thống là một technical surface độc lập, không
thuộc business portal và không yêu cầu đăng nhập. Customer Web dùng COD core; realtime map, rating, reorder,
notification, payment online và promotion checkout vẫn chưa mở.

## 1. Xác thực và session

| Màn hình | Chức năng |
| :--- | :--- |
| **Đăng nhập nhà hàng** | Đăng nhập role `SHOP_OWNER`, khôi phục session, tải restaurant sở hữu và retry khi dữ liệu restaurant tạm thời không tải được. |
| **Đăng nhập admin** | Đăng nhập role `ADMIN` qua portal riêng. |
| **Đăng nhập/đăng ký khách hàng** | Đăng nhập role `USER`; đăng ký qua Auth → User handoff, xác minh email ở `/verify-email`. |
| **Session recovery** | Refresh access token single-flight; lỗi mạng/5xx có thể retry, session chỉ bị xóa khi refresh/authentication thất bại. |

## 2. Portal nhà hàng (`SHOP_OWNER`)

| Route | Chức năng |
| :--- | :--- |
| `/restaurant/dashboard` | Entry point và các shortcut tới nghiệp vụ MVP. Analytics/revenue dashboard chưa mở. |
| `/restaurant/orders` | Xem đơn phân trang, lọc local theo trạng thái, confirm/reject đơn pending và retry sau lỗi. |
| `/restaurant/menu` | Tải, tìm kiếm, tạo, sửa và xóa món ăn theo restaurant ownership. |
| `/restaurant/profile` | Xem/cập nhật thông tin nhà hàng, địa chỉ, tọa độ, giờ mở cửa và ảnh. |
| `/restaurant/reviews` | Xem các rating đã được admin duyệt. |

Desktop dùng sidebar; mobile/tablet dùng navigation drawer. Các action chính
vẫn truy cập được dưới breakpoint desktop.

## 3. Portal admin (`ADMIN`)

| Route | Chức năng |
| :--- | :--- |
| `/admin/dashboard` | KPI, xu hướng đơn/GMV, phân bổ trạng thái, top nhà hàng và shortcut nghiệp vụ; dữ liệu đọc qua analytics Gateway contract. |
| `/admin/orders` | Xem/phân trang/lọc đơn toàn hệ thống. |
| `/admin/shippers` | Xem danh sách shipper, filter online/offline và tìm kiếm local. |
| `/admin/ratings` | Moderation rating: approve/reject, retry được khi backend lỗi. |
| `/admin/coupons` | Tạo/xóa platform coupon, kiểm tra time window và scope. |
| `/admin/flash-sales` | Tạo campaign, đổi trạng thái, xem item và approve item. |

Các trang read phân biệt loading, empty, error và retry; mutation khóa submit
trùng trong lúc request đang chạy.

## 4. Technical system handbook surface

| Route | Quyền | Chức năng |
| :--- | :--- | :--- |
| `/system-overview` | Public, read-only | Flow-first handbook và searchable Markdown Docs Portal: chọn workflow, xem swimlane happy/failure path, mở inspector cho từng actor/service/API/event/state, xem architecture overlay theo flow và đọc corpus canonical theo tài liệu. Không chứa business action hoặc portal navigation. |

Handbook dùng 6 khu vực chính: Tổng quan, Flow Explorer, Architecture, Services &
Contracts, Operations và Tài liệu. `/system-overview/docs` là snapshot tĩnh được
generate từ allowlist canonical; có tìm kiếm, lọc nhóm, deep link, source/status
badge và Markdown heading/table/code rendering. Các route cũ dưới `/system-overview/*` vẫn được giữ để
deep link tương thích; capability/actor/API được mở như context liên kết trong
workspace mới. Flow Explorer lưu workflow, step, mode, node, operation và event
trong URL để có thể chia sẻ đúng màn hình đang đọc.

Nội dung là snapshot được generate/check từ system docs canonical và source-derived
API contract; event/interaction mapping được kiểm tra integrity khi test. Markdown
chỉ render nội dung allowlist, chặn raw HTML/URL scheme nguy hiểm và map link `.md`
nội bộ về route handbook. Handbook không gọi runtime backend, không có Try-it
console và không hiển thị secrets, customer data hoặc internal route như public
contract.

## 5. Storefront khách hàng (`USER`)

| Route | Chức năng |
| :--- | :--- |
| `/` và `/restaurants/:restaurantId` | Xem công khai danh sách nhà hàng, tìm kiếm và menu món đang bán. |
| `/cart` | Giỏ hàng localStorage, chỉ chứa món của một nhà hàng, chỉnh số lượng và ghi chú. |
| `/customer/addresses` | Tạo/sửa/xóa/đặt mặc định địa chỉ; lấy tọa độ bằng browser Geolocation hoặc nhập tay. |
| `/customer/checkout` | Chọn địa chỉ, gọi checkout preview server, hiển thị quote và đặt COD bằng idempotency key. |
| `/customer/orders` | Lịch sử đơn phân trang. |
| `/customer/orders/:orderId` | Chi tiết món/tổng tiền, REST status refresh mỗi 10 giây khi tab visible và hủy trước pickup. |

Customer catalog là public; checkout, address và order yêu cầu role `USER`.
Tổng tiền hiển thị trong giỏ chỉ mang tính tham khảo; quote và order service là
authority tài chính.

## 6. Explicit exclusions

- User administration CRUD chưa có route canonical trong Web MVP.
- Firebase/Chat, livestream, realtime shipper map, rating, reorder và notification.
- Dashboard nhà hàng chi tiết, export lịch sử và financial reporting nâng cao chỉ
  mở sau khi analytics ownership/backfill gates hoàn tất.
- Online payment, settlement, withdrawal, refund UI và voucher/flash-sale
  checkout activation.
- Direct service ports, Internal-Token, gRPC, STOMP và SockJS.
- Voucher/flash-sale checkout activation; các reservation/checkout capability
  vẫn disabled theo product architecture.

Mọi capability mới phải có Gateway route canonical, actor/ownership rule,
typed response/error contract và focused proof trước khi đưa vào router.
