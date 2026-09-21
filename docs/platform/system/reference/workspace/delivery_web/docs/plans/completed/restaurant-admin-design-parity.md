# Đồng bộ giao diện Nhà hàng và Admin

## Mục tiêu và phạm vi

Ngày 08/09/2026: người dùng yêu cầu lập plan cho Nhà hàng và Admin sau khi triển
khai desktop khách hàng. Plan này chuẩn bị triển khai giao diện portal; chưa
thực hiện redesign portal trong lượt lập plan. Sau đó người dùng yêu cầu
“triển”; phạm vi giao diện đã được thực hiện như kết quả bên dưới.

Nguồn thiết kế: customer preview hiện tại và kết quả trong
`../completed/customer-desktop-mobile-parity.md`. Nguồn chức năng:
`src/components/navigation/portalNavigation.ts`, routes trong `src/App.tsx` và
các page/service/test hiện có. Màu sắc và thành phần cùng hệ với khách hàng;
bố cục phục vụ công việc quản lý và xử lý đơn.

## Hiện trạng đã kiểm tra

- Nhà hàng: 8 mục navigation — Dashboard, Đơn hàng, Menu, Hồ sơ, Đánh giá,
  Voucher, Livestream, Import.
- Admin: Dashboard, Đơn hàng, Shipper, Đánh giá, Coupon, Flash Sale, Import;
  có chuyển sang cổng nhà hàng để quản lý theo quán.
- Đã có responsive cơ bản, mobile drawer, form co cột và vùng cuộn bảng.
- Portal còn màu vàng/nâu, nhiều card bo lớn, title/padding không đồng nhất.
- `RestaurantHeader` dùng chung và có restaurant switcher cho Admin; đây là
  điểm phải kiểm tra kỹ để giữ nguyên ngữ cảnh nhà hàng.
- Có thay đổi chưa commit của người dùng trong các file portal và test.
  Phải rà diff từng file trước sửa, giữ nguyên các thay đổi nghiệp vụ đó.
- Whole-repo lint từng báo lỗi React Compiler memoization trong
  `RestaurantOrders.tsx`; chạy lại baseline trước triển khai để xác nhận.

## Quyết định thiết kế

- Primary đỏ/cam theo customer (`#ee4d2d`), nền xám nhạt, surface trắng,
  viền trung tính. Chữ nhỏ trên nền primary phải kiểm tra tương phản và dùng
  sắc đỏ đậm hơn khi cần; không dùng màu thương hiệu thay mọi màu trạng thái.
- Dùng semantic tokens cho brand, surface, border, text, focus và status.
  Scope portal để không vô tình đổi preview hoặc System Handbook.
- Typography đồng bộ nhịp với customer: title 24–28px desktop, 20–24px mobile,
  body 14–16px; số tiền và cột số dễ so sánh. Card radius 6–8px, ít shadow.
- Desktop sidebar khoảng 232px, topbar có tiêu đề/ngữ cảnh quán/tài khoản.
  Mobile dùng drawer và header gọn. Không thêm bottom nav khách hàng vào portal.
- Nút và trường nhập tối thiểu 44px khi thao tác cảm ứng; một hành động chính
  rõ ràng trên mỗi vùng, destructive action có phân biệt và giữ xác nhận hiện có.
- Dark mode giữ hỗ trợ tương ứng, kiểm tra cả border/text/status.

## Trình tự triển khai

### 1. Nền tảng dùng chung

- Chụp baseline Nhà hàng/Admin ở 390px và 1440px; ghi lỗi hiện có.
- Tạo/chuẩn hóa portal tokens và thành phần thực sự lặp lại: page header,
  toolbar, status badge, loading/empty/error, form field, dialog và pagination.
- Áp dụng vào `RestaurantHeader`, `RestaurantSidebar`, `AdminLayout` và
  `PortalMobileDrawer`; giữ navigation và quyền truy cập hiện có.
- Dialog có tên truy cập, focus ban đầu, trap/restore focus, Escape và vùng
  cuộn nội dung. Button icon-only có accessible name.

### 2. Nhà hàng — ưu tiên vận hành đặt hàng

| Thứ tự | Màn | Kết quả thiết kế |
| --- | --- | --- |
| P1 | Đơn hàng | Bộ lọc trạng thái rõ; mỗi đơn ưu tiên mã, thời gian, món, ghi chú, tổng tiền và hành động đang được phép; nút không bị che trên mobile |
| P1 | Menu | Ảnh món đồng nhất, tên/giá/trạng thái dễ quét; grid desktop và hàng gọn mobile; form thêm/sửa thống nhất |
| P1 | Dashboard | Lối tắt xử lý đơn/menu, ngữ cảnh quán rõ; chỉ hiển thị số liệu có nguồn API hiện hành |
| P2 | Hồ sơ | Nhóm ảnh, thông tin liên hệ, địa chỉ/toạ độ và giờ mở cửa; preview ảnh; hiển thị validation cạnh field |
| P2 | Đánh giá | Hàng đánh giá dễ đọc, nội dung dài không tràn, trạng thái rỗng/lỗi rõ |
| P2 | Voucher | Tách tạo voucher và danh sách; điều kiện, hạn dùng, trạng thái và phản hồi thao tác dễ đọc |
| P3 | Livestream | Thông tin phiên và nút lifecycle hiện có rõ ràng, trạng thái đang xử lý/không khả dụng nhất quán |
| P3 | Import | Chia nhập → kiểm tra → kết quả theo luồng hiện có; lỗi theo bản ghi, vùng JSON tự cuộn |

### 3. Admin — ưu tiên giám sát và xử lý

| Thứ tự | Màn | Kết quả thiết kế |
| --- | --- | --- |
| P1 | Dashboard | KPI gọn, lọc kỳ rõ, biểu đồ có legend, top nhà hàng; chỉ dùng số liệu analytics thật |
| P1 | Đơn hàng | Toolbar lọc/tìm kiếm, trạng thái, hành động hàng loạt và pagination cùng hệ; danh sách mobile ưu tiên trường chính |
| P1 | Chuyển cổng nhà hàng | Quán đang quản lý luôn nhìn thấy; đổi quán không để dữ liệu/hành động của quán cũ còn hiển thị |
| P2 | Shipper | Danh tính/liên hệ/trạng thái dễ đọc; mobile vẫn truy cập được trường phụ, không ẩn mất dữ liệu |
| P2 | Đánh giá | Danh sách và thao tác moderation hiện có rõ trạng thái đang xử lý, thành công hoặc lỗi |
| P2 | Coupon | Nhóm voucher chờ duyệt, danh sách hệ thống và form tạo; phân biệt duyệt/từ chối/tạm dừng |
| P2 | Flash Sale | Campaign, thời gian, trạng thái và danh sách item rõ; dialog con không che điều khiển |
| P3 | Import | Dùng cùng cấu trúc kiểm tra và kết quả với Nhà hàng, giữ đúng phạm vi quyền |
| P3 | Đăng nhập | Hai màn login Admin/Nhà hàng đồng bộ typography, field, thông báo lỗi và CTA |

Với bảng nhiều cột, ưu tiên mobile list có nội dung tương đương khi dễ thực hiện;
bảng so sánh cần giữ cột dùng vùng cuộn ngang riêng. Không để toàn trang tràn.

## Kiểm chứng và tiêu chí hoàn thành

- Baseline và kết quả: typecheck, build, scoped lint; chạy existing tests cho
  admin actions, restaurant routes/forms/order service theo file bị tác động.
- Browser smoke tại 320/390/768/1024/1440px: drawer, switcher, bảng, pagination,
  form/modal, tên quán/món dài và trạng thái loading/empty/error/populated.
- Xác nhận role guard và API payload không bị thay đổi do sửa presentation.
- Kiểm tra ngữ cảnh đổi quán, nút disabled khi pending và phản hồi lỗi bằng
  test hiện có hoặc test tập trung nếu thay đổi logic interaction.
- Visual QA light/dark, keyboard và focus. Luồng có mutation dùng fixture/API
  interception khi kiểm tra; không gửi thao tác duyệt/hủy/import vào data thật.
- Không chạy full E2E theo ưu tiên người dùng; thiếu backend thì ghi rõ phần
  nào dùng fixture và phần nào chưa kiểm chứng dữ liệu thật.
- Chỉ đánh dấu hoàn thành sau khi các màn trong phạm vi đạt kiểm tra liên quan.

## Rủi ro và phục hồi

- Token global có thể đổi Handbook/customer ngoài ý muốn: scope trước, kiểm tra
  trang đại diện ngoài portal nếu cần sửa Tailwind/global CSS.
- Bảng mobile có thể làm mất trường/hành động: lập đối chiếu desktop/mobile.
- Đổi layout có thể làm mất state form hoặc selection: giữ component identity
  và state owner, không tách hai cây fetch riêng cho desktop/mobile.
- Phục hồi bằng revert hunk UI của đợt này; giữ nguyên dirty changes có trước.
  Không cần migration hay thao tác DB. Không commit/push trong bước lập plan.

## Tiến độ

- [x] Kiểm tra navigation, route và cấu trúc page hiện có.
- [x] Xác định hướng thiết kế, phạm vi, thứ tự và tiêu chí kiểm chứng.
- [x] Baseline và shell/token chung.
- [x] Nhà hàng P1 → P2 → P3: theme dùng chung áp dụng cho toàn bộ route;
  Dashboard đổi bố cục thông tin quán, các page còn lại giữ bố cục responsive
  hiện có và dùng typography, surfaces, controls thống nhất.
- [x] Admin P1 → P2 → P3: theme, sidebar/topbar, bảng, chart legend, login và
  dialog được đồng bộ. Giữ vùng cuộn bảng; shipper không còn ẩn cột trên mobile.
- [x] Kiểm chứng, ghi kết quả và chuyển plan sang completed.

## Kết quả thực hiện

- `portal.css` định nghĩa theme theo route; Tailwind colors dùng CSS variables
  với fallback giữ màu ngoài portal. Không chuyển dữ liệu mock vào production.
- Reuse markup/form/table hiện có, không tạo abstraction cho các page không
  thực sự có chung cấu trúc. Sidebar 232px, active nav có aria-current, avatar
  bỏ gradient, ảnh/địa chỉ/giờ trạng thái quán hiển thị trên Dashboard.
- Hook `useDialogFocus` thêm focus containment, Escape và restore focus cho
  drawer, Menu, Coupon và hai dialog Flash Sale. Flash Sale bổ sung tên dialog.
- Sửa dependency callback Orders bằng restaurantId scalar để React Compiler
  lint hợp lệ. Test phát hiện wrapper route có thể remount AuthProvider;
  giữ wrapper ổn định đã khắc phục và auth restoration tests pass.
- Full unit suite: 26 files / 134 tests pass. Action-contract check pass.
  Typecheck/build pass; lint không còn error, còn 2 warning có trước trong
  useAuth và RestaurantForm. Một lượt full suite từng fail ở timing assertion
  của Handbook; lần chạy cuối full suite pass, không sửa/weaken test đó.
- Browser smoke dùng MockGateway fixture có sẵn: 15 route Nhà hàng/Admin ở
  390 và 1440px không tràn viewport; dashboard Admin, shipper, menu kiểm tra
  thêm 320/768/1024px. Kiểm tra bằng mắt ảnh dashboard/order/menu và dark mode.
- Keyboard smoke: 3 dialog tạo Menu/Coupon/Flash Sale giữ focus sau Shift+Tab,
  Escape đóng và trả focus về trigger; drawer Escape đóng được.
- Existing admin tests kiểm chứng chọn quán và scope thao tác. Không gửi
  mutation đến backend thật, không chạy full E2E. Chưa kiểm chứng vận hành trên
  data production; live data vẫn cần gateway đang chạy và phiên hợp lệ.
- Không commit/push. Các dirty changes có trước vẫn được giữ.
