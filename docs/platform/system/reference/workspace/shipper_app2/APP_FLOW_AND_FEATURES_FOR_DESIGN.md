# TỔNG HỢP CHỨC NĂNG & LUỒNG Ứng Dụng (Shipper App)
*Tài liệu dành cho UI/UX Designer để thiết kế và tinh chỉnh giao diện.*

---

## Phần 1: Các Chức Năng Theo Từng Màn Hình

### 1. Màn hình Khởi động (Splash Screen) & Cấp Quyền (Permissions)
- **Chức năng chính**: Hiển thị logo khi mở app.
- **Xin quyền truy cập (Rất quan trọng)**: Giao diện yêu cầu cấp quyền Vị trí (GPS luôn bật) và Thông báo (Push Notification) để đảm bảo app hoạt động đúng.
- **Logic ngầm**: Kiểm tra token đăng nhập (Auth state). Nếu đã đăng nhập -> chuyển sang màn Home (Bản đồ). Nếu chưa -> chuyển sang màn Đăng nhập.

### 2. Cụm Màn hình Xác thực (Auth)
#### a. Màn Đăng nhập (LoginScreen)
- **Chức năng**: Nhập Email/Số điện thoại và Mật khẩu để đăng nhập.
- **Action**: Nút Đăng nhập, Nút Quên mật khẩu, Nút chuyển sang màn Đăng ký.

#### b. Màn Đăng ký (RegisterScreen)
- **Chức năng**: Đăng ký tài khoản Shipper mới (Tên, Email, SĐT, Mật khẩu, v.v.).

### 3. Menu Điều Hướng (Custom Drawer / Side Menu)
Được mở ra khi Shipper bấm vào icon Menu (Hamburger) trên Bản đồ hoặc vuốt từ cạnh trái sang.
- **Header**: Avatar, Tên Shipper, Số điện thoại/Email.
- **Nút Chuyển Trạng Thái (StatusOnlineShipper)**: Công tắc (Switch) để bật/tắt chế độ Nhận Mốc (Online/Offline).
- **Danh sách Menu**: Bản đồ trang chủ, Lịch sử chuyến đi, Hồ sơ, Cài đặt.
- **Footer**: Nút Đăng xuất.

### 4. Màn hình Chính / Màn hình Bản đồ (MainMapScreen) - *Quan trọng nhất*
Đây là không gian làm việc chính của Shipper.
- **Chức năng Bản đồ (Mapbox)**:
  - Hiển thị bản đồ với vị trí hiện tại của Shipper (Marker Đỏ 🔴).
  - Hiển thị điểm lấy hàng - Pickup (Marker Xanh lá 🟢).
  - Hiển thị điểm giao hàng - Delivery (Marker Xanh dương 🔵).
  - Vẽ đường đi (Route) từ vị trí hiện tại đến điểm lấy/giao hàng.
- **Chức năng Trạng thái hoạt động**:
  - Nút bật/tắt trạng thái (Online / Offline) để nhận cuốc.
- **Chức năng Nhận đơn (MatchFoundPopup / TopSheetPopup)**:
  - Khi có đơn mới: Hiển thị Popup ở nửa trên màn hình (hoặc chính giữa) gồm thông tin: Khoảng cách, Tiền ship, Điểm lấy, Điểm giao.
  - Phím bấm: Đồng ý nhận đơn / Từ chối.
  - Thời gian đếm ngược (Đồng hồ cát 10s-15s) để tạo sự khẩn trương.

- **Giao diện Cập nhật trạng thái đơn (BottomSheetDelivery)**:
  - Thẻ thông tin đơn chi tiết: Mã đơn, Tên quán, Địa chỉ khách.
  - **Công cụ hỗ trợ**: 
    - Nút Gọi điện / Nhắn tin cho Khách hàng & Quán.
    - Nút mở ứng dụng bên ngoài (Google Maps / Apple Maps) để chỉ đường chuyên sâu.
    - Nút "Báo cáo sự cố / Huỷ đơn" (nâng cao).
  - Vuốt thanh trượt (Swipe to confirm) để cập nhật các bước giao:
    1. Trạng thái Đang đến lấy hàng -> Vuốt "Đã lấy hàng".
    2. Trạng thái Đang giao hàng -> Vuốt "Bắt đầu giao hàng" / "Giao hàng thành công".
  - Tự động ẩn đi khi giao hàng xong.

### 5. Màn hình Lịch sử Đơn hàng & Thu Nhập (OrderHistoryScreen)
- **Chức năng**: Xem danh sách các chuyến đi đã hoàn thành/huỷ và Thống kê tiền.
- **UI Element**: 
  - Thẻ Thống kê nhanh: Tổng đơn hoàn thành trong ngày/tuần, Tổng thu nhập.
  - Danh sách dạng Card (Mã đơn, Tiền ship, Trạng thái: Hoàn thành/Đã huỷ, Điểm Nhận, Điểm Giao, Thời gian).
  - Màu sắc Trạng thái: Xanh lá (Hoàn thành), Đỏ (Đã huỷ), Xanh dương (Đang giao/Đã lấy).
  - Kéo xuống để tải lại (Pull to refresh).

### 6. Màn hình Hồ sơ (ProfileScreen)
- **Chức năng**: Xem và cập nhật thông tin cá nhân của Shipper.
- **UI Element**: Ảnh đại diện (Avatar), Họ tên, Số điện thoại, Email. Nút "Lưu/Cập nhật".

### 7. Màn hình Cài đặt (SettingsScreen)
- **Chức năng**: Các tuỳ chỉnh bảo mật ứng dụng.
- **UI Element**: 
  - Giao diện Đổi mật khẩu (Mật khẩu hiện tại, mật khẩu mới, xác nhận).
  - Chuyển đổi ngôn ngữ/Giao diện (nếu có).
  - Nút Đăng xuất (Logout).

### 8. Màn hình Thông báo (Notification Screen)
- **Chức năng**: Danh sách các thông báo từ hệ thống (Cộng tiền, trừ tiền, cảnh báo, tin tức...).

---

## Phần 2: Luồng Hoạt Động (User Flow)

Chạm vào các luồng thao tác chính để Designer hình dung trải nghiệm của Shipper:

### Luồng 1: Khởi động & Nhận việc 🚀
1. Mở App -> Splash Screen.
2. Login thành công -> Vào phần Bản đồ (MainMapScreen).
3. Shipper vuốt mở Side Menu (Drawer) -> Gạt công tắc (Toggle) qua `"Trực Tuyến" (Online)` để nhận đơn.
4. Shipper rảnh rỗi chờ đơn (chỉ hiện bản đồ và định vị).

### Luồng 2: Chốt đơn & Đi lấy hàng 🏍️
1. Hệ thống "nổ" đơn -> Popup hiển thị đè lên bản đồ. Chông báo tóm tắt đơn (Giá, khoảng cách, địa chỉ).
2. Shipper bấm `"Nhận đơn"`.
3. Bản đồ vẽ đường đi từ `Vị trí shipper` -> `Điểm lấy hàng`.
4. Bottom Sheet trượt lên ở góc dưới màn hình ("Đơn hàng #123...").
5. Shipper đi theo bản đồ đến quán. Tới nơi, Shipper cầm hàng xong, vuốt thanh trượt (Swipe) từ trái qua phải: `"Đã lấy hàng"`.

### Luồng 3: Đi giao hàng & Hoàn thành ✅
1. Sau khi vuốt lấy hàng, hệ thống vẽ lại đường đi: `Điểm lấy hàng` -> `Điểm giao hàng` (khách).
2. Bottom Sheet tự đổi text trên thanh vuốt thành `"Giao hàng thành công"`.
3. Shipper đến nhà khách, giao đồ xong, lại vuốt thanh trượt (Swipe) sang phải.
4. Đơn hàng hoàn thành -> Bottom sheet trượt biến mất.
5. Popup thông báo `"Giao hàng thành công"` hiện ra chúc mừng. 
6. Trở lại trạng thái Rảnh rỗi chờ đơn.

### Luồng 4: Quản lý cá nhân & Xem thu nhập 👤
1. Shipper vuốt mở Side Menu (Drawer Navigation) từ màn hình bản đồ.
2. Bấm vào "Lịch sử" để xem lịch sử nhận đơn và kiểm tra thu nhập trong ngày.
3. Bấm vào "Hồ sơ" để cập nhật thông tin.
4. Bấm vào "Cài đặt" để đổi mật khẩu.
5. Khi hết ca làm việc, bấm "Đăng xuất" ngay từ Drawer Menu.

### Luồng 5: Xử lý sự cố & Khách không nhận hàng 🆘
1. Trong quá trình giao hàng, xe bị hỏng hoặc khách không nghe máy.
2. Shipper mở Bottom Sheet trên Bản đồ báo cáo, bấm nút "Báo cáo sự cố / Huỷ đơn".
3. Form hiện lên yêu cầu chọn Lý do (Khách không nghe máy, Quán đóng cửa, Hỏng xe...).
4. (Tuỳ chọn) Chụp ảnh làm bằng chứng tải lên minh chứng.
5. Hệ thống xác nhận -> Đơn chuyển trạng thái "Đã Huỷ", Shipper trở lại trạng thái chờ đơn tiếp theo.
