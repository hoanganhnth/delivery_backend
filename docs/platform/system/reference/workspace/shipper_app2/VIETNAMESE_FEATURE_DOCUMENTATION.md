# 📱 Tài Liệu Tính Năng Ứng Dụng Shipper - Amber Hearth

**Ứng Dụng**: Amber Hearth - Hệ Thống Giao Hàng Cao Cấp  
**Phiên Bản**: 0.0.1  
**Nền Tảng**: React Native (iOS & Android)  
**Ngôn Ngữ**: TypeScript  
**Hệ Thống Quản Lý Trạng Thái**: Redux + Redux Toolkit

---

## 🎯 Tổng Quan Ứng Dụng

Ứng dụng Amber Hearth Shipper là nền tảng di động toàn diện được thiết kế cho các tài xế giao hàng. Ứng dụng này cho phép các shipper quản lý đơn hàng, theo dõi vị trí GPS, quản lý thu nhập, và duy trì tuân thủ quy định.

**Luồng Người Dùng Chính**:
1. **Xác Thực** → Đăng ký/Đăng nhập
2. **Cấp Quyền** → Cho phép GPS, Thông báo, v.v.
3. **Trang Chủ** → Xem bản đồ với đơn hàng
4. **Nhận Đơn Hàng** → Chấp nhận hoặc từ chối
5. **Giao Hàng** → Theo dõi GPS và hoàn tất
6. **Lịch Sử** → Xem các đơn hàng đã hoàn tất

---

## 📋 18 MÀN HÌNH - TÀI LIỆU CHI TIẾT

### **1️⃣ Màn Hình Đăng Ký Shipper (ShipperRegistration)**

**Mục Đích**: Cho phép người dùng mới tạo tài khoản và thêm thông tin shipper

**Mô Tả Chức Năng**:  
Màn hình này là bước đầu tiên trong quy trình đăng ký cho các tài xế giao hàng mới. Người dùng nhập thông tin cá nhân, thông tin liên hệ, chọn loại phương tiện, và tạo mật khẩu. Đây là điểm cửa vào để trở thành shipper trong hệ thống Amber Hearth.

**Các Thành Phần Chính**:
- 📝 **Biểu mẫu đăng ký** với các trường:
  - Họ và Tên Đầy Đủ (Full Name)
  - Địa Chỉ Email (Email Address)
  - Số Điện Thoại (Phone Number)
  - **Lựa Chọn Loại Phương Tiện** (Vehicle Type):
    - 🚲 Xe Đạp (Bike)
    - 🏍️ Xe Máy (Motorcycle) - *Được chọn mặc định*
    - 🚗 Ô Tô (Car)
  - Mật Khẩu (Password) - Với nút ẩn/hiện
- ✅ **Nút Tạo Tài Khoản** (Create Account) - Màu cam, viên tròn
- 🔗 Liên kết đăng nhập nếu đã có tài khoản (Already part of fleet? Login here)
- ©️ Chân trang với thông tin bản quyền

**Luồng Tương Tác**:
1. Người dùng điền thông tin vào từng trường
2. Chọn loại phương tiện phù hợp
3. Tạo mật khẩu mạnh
4. Nhấn nút "Create Account"
5. Được chuyển đến màn hình xác nhận

**Tính Năng Nổi Bật**:
- ✨ Giao diện trực quan với biểu tượng input
- ✨ Lựa chọn loại phương tiện dễ dàng
- ✨ Hiển thị/ẩn mật khẩu động
- ✨ Xác thực biểu mẫu phía máy khách
- ✨ Hỗ trợ cho phép liên kết đến trang đăng nhập

---

### **2️⃣ Màn Hình Yêu Cầu Cấp Quyền (PermissionsRequest)**

**Mục Đích**: Yêu cầu những quyền cần thiết từ người dùng (GPS, Thông báo, Lịch, v.v.)

**Mô Tả Chức Năng**:  
Sau khi đăng ký thành công, shipper được yêu cầu cấp các quyền cần thiết để ứng dụng hoạt động đúng. Bao gồm GPS (định vị thực thời), Thông báo (nhận đơn hàng), Lịch (lên lịch giao hàng), v.v.

**Các Thành Phần Chính**:
- 📌 **Tiêu đề**: "Cho Phép Truy Cập"
- 📋 **Danh Sách Quyền Cần Thiết**:
  - 📍 GPS/Vị Trí (Location Services)
  - 🔔 Thông Báo (Notifications)
  - 📅 Lịch (Calendar)
  - 💾 Lưu Trữ (Storage)
- ✅ **Nút "Tiếp Tục"** - Cấp tất cả quyền
- ⏭️ **Nút "Bỏ Qua"** - Bỏ qua (không khuyến khích)
- ℹ️ **Giải thích** về tại sao cần từng quyền

**Luồng Tương Tác**:
1. Người dùng đọc giải thích về các quyền
2. Nhấn "Tiếp Tục" để cấp tất cả quyền
3. Hệ thống yêu cầu từng quyền lần lượt
4. Được chuyển đến màn hình chính

**Tính Năng Nổi Bật**:
- ✨ Giải thích rõ ràng tại sao cần từng quyền
- ✨ Xử lý từng quyền riêng lẻ
- ✨ Lưu trữ trạng thái cấp quyền
- ✨ Cho phép người dùng quay lại để cấp quyền sau

---

### **3️⃣ Màn Hình Bản Đồ Chính - Xem Đơn Hàng (MainMapOrderRequest)**

**Mục Đích**: Hiển thị bản đồ thực thời với các đơn hàng gần nhất và cho phép shipper chấp nhận

**Mô Tả Chức Năng**:  
Đây là màn hình chính của ứng dụng. Shipper thấy bản đồ với vị trí thực thời của mình, các nhà hàng xung quanh, các đơn hàng khả dụng, và có thể chấp nhận hoặc từ chối đơn hàng. Bản đồ tích hợp Mapbox để hiển thị định vị chính xác.

**Các Thành Phần Chính**:
- 🗺️ **Bản đồ Mapbox** - Hiển thị:
  - 📍 Vị trí hiện tại của shipper (dấu xanh)
  - 🏪 Nhà hàng (biểu tượng nhà ăn)
  - 🛵 Các shipper khác (vị trí cách xa)
  - 📦 Các đơn hàng (chỉ định vị trí nhà hàng)
- **Modal Đơn Hàng** (Nổi lên từ dưới):
  - 🏪 Tên nhà hàng (ví dụ: "The Ember Kitchen")
  - 📍 Địa chỉ giao hàng
  - 📦 Danh sách sản phẩm (3-5 món)
  - 💰 Tiền hoa hồng (Commission)
  - ⏱️ Thời gian dự kiến (Estimated Time)
  - ✅ Nút "Chấp Nhận Đơn Hàng"
  - ❌ Nút "Từ Chối"
- **Header**:
  - 👤 Hồ sơ shipper + Tên
  - 🔔 Biểu tượng thông báo
  - ☰ Menu chính

**Luồng Tương Tác**:
1. Shipper mở ứng dụng → Thấy bản đồ
2. Nhấn vào một đơn hàng/nhà hàng
3. Xem chi tiết modal
4. Chọn "Chấp Nhận" hoặc "Từ Chối"
5. Nếu chấp nhận → Chuyển sang màn hình Đơn Hàng Hoạt Động

**Tính Năng Nổi Bật**:
- ✨ Cập nhật vị trí thực thời
- ✨ Bộ lọc đơn hàng theo khoảng cách/tiền hoa hồng
- ✨ Hiển thị đơn hàng chỉ khi phù hợp loại phương tiện
- ✨ Âm thanh/rung thông báo khi có đơn hàng mới
- ✨ Hỗ trợ xem chi tiết chi phối trước khi chấp nhận

---

### **4️⃣ Màn Hình Xem Chi Tiết Đơn Hàng - Phiên Bản Mở Rộng (RefinedOrderDetailsExpanded)**

**Mục Đích**: Hiển thị toàn bộ chi tiết đơn hàng khi đang giao (Delivery In Progress)

**Mô Tả Chức Năng**:  
Khi shipper chấp nhận và bắt đầu giao hàng, màn hình này cho phép xem chi tiết đầy đủ về đơn hàng, bao gồm danh sách sản phẩm, thông tin người giao, liên hệ nhanh. Được hiển thị dưới dạng bottom sheet (modal từ dưới).

**Các Thành Phần Chính**:
- 🎨 **Hình Nền**: Bản đồ mờ với gradient lớp phủ
- **Bottom Sheet Modal**:
  - 🔘 **Thanh Kéo** (Pull handle) ở đầu
  - 🏷️ **Tiêu Đề Đơn Hàng**:
    - "GIAO HÀNG HOẠT ĐỘNG" (Active Delivery badge)
    - Số đơn hàng: "#8829"
    - Thời gian dự kiến: "Khoảng 12 phút"
    - Tổng tiền hoa hồng: "$24.50"
    - Ảnh đại diện tài xế
  
  - **Tabs**:
    - 📦 "Sản Phẩm Đặt Hàng" (Order Items) - Mặc định
    - 🗺️ "Chi Tiết Giao Hàng" (Delivery Details)
  
  - **Tab: Sản Phẩm Đặt Hàng**:
    - Tiêu đề: "SẢN PHẨM (3)" với badge "ĐÃ CHUẨN BỊ"
    - Danh sách sản phẩm:
      - 1× Wild Hearth Wagyu Burger - $18.00 (Medium, không rau dền, thêm phô mai)
      - 2× Truffle Parmesan Fries - $9.50
      - 1× Cà Phê Espresso - $4.50
    - **Thông Tin Giao**:
      - 📍 NHẬN HÀng: The Ember Kitchen - Địa chỉ
      - Người quản lý: Sarah Jenkins - (415) 555-0192
      - 📍 GỬI ĐẾN: Marcus Vane - 128 Laurel St Apt 4C
      - Mã cửa: 4492
      - ⚠️ Cảnh báo: "Liên hệ khách hàng nếu không thể giao như chỉ định"
  
  - **Nút Hành Động Dính Dưới**:
    - 💬 Tin nhắn
    - ☎️ Gọi điện
    - 🎯 "VUỐT ĐỂ HOÀN THÀNH" (Swipe to Complete button)

**Luồng Tương Tác**:
1. Shipper chấp nhận đơn hàng
2. Thấy bottom sheet với chi tiết
3. Xem sản phẩm và hướng dẫn
4. Liên hệ nhà hàng nếu cần
5. Kéo xuống bottom sheet để hoàn tất

**Tính Năng Nổi Bật**:
- ✨ Tab để chuyển đổi giữa sản phẩm và chi tiết giao
- ✨ Liên hệ nhanh (gọi/tin nhắn) trực tiếp
- ✨ Cảnh báo các hướng dẫn đặc biệt
- ✨ Hiển thị thời gian dự kiến cập nhật động
- ✨ Nút "Vuốt để hoàn tất" an toàn

---

### **5️⃣ Màn Hình Thành Công Giao Hàng (DeliverySuccess)**

**Mục Đích**: Xác nhận hoàn thành đơn hàng và hiển thị kết quả

**Mô Tả Chức Năng**:  
Sau khi shipper hoàn tất đơn hàng, màn hình này hiển thị xác nhận thành công, tiền hoa hồng nhận được, và một số thống kê nhanh.

**Các Thành Phần Chính**:
- ✅ **Biểu tượng thành công** (Checkmark lớn)
- 🎉 **Tiêu đề**: "GIAO HÀNG THÀNH CÔNG!"
- 📊 **Thống Kê Đơn Hàng**:
  - Số đơn hàng: "#AH-8829"
  - Tên nhà hàng: "The Ember Kitchen"
  - Địa chỉ giao: "128 Laurel St, Apt 4C"
  - Thời gian giao: "7:42 PM"
  - Thời gian kéo dài: "52 phút"
- 💰 **Tiền Hoa Hồng**: $24.50 (Lớn, màu cam)
- **Nút Hành Động**:
  - ➕ "NHẬN THÊM ĐƠN HÀNG" (Accept Another) - Chính
  - 🏠 "VỀ TRANG CHỦ" (Back to Map) - Phụ

**Luồng Tương Tác**:
1. Hoàn tất đơn hàng
2. Thấy màn hình thành công
3. Xem tiền hoa hồng
4. Chọn nhận thêm đơn hoặc quay về bản đồ

**Tính Năng Nổi Bật**:
- ✨ Xác nhận trực quan về sự thành công
- ✨ Hiển thị tiền kiếm được tức thời
- ✨ Khuyến khích nhận thêm đơn
- ✨ Thống kê chi tiết giao hàng

---

### **6️⃣ Màn Hình Báo Cáo Sự Cố (ReportIncident)**

**Mục Đích**: Cho phép shipper báo cáo các sự cố trong quá trình giao hàng

**Mô Tả Chức Năng**:  
Khi gặp vấn đề (khách hàng không trả lời, nhà hàng đóng cửa, v.v.), shipper có thể báo cáo sự cố. Ứng dụng sẽ ghi lại thông tin và chuyển đến hỗ trợ khách hàng.

**Các Thành Phần Chính**:
- 📌 **Header**: Nút quay lại + Biểu tượng trợ giúp
- 📦 **Thông Tin Đơn Hàng**: "#8829" với badge "GIAO HÀNG HOẠT ĐỘNG"
- 🚨 **Các Lý Do Báo Cáo** (Thẻ có thể chọn):
  - 🤐 Khách hàng không trả lời điện thoại
  - 🔒 Nhà hàng đóng cửa
  - 🚗 Xe gặp sự cố
  - 💥 Tai nạn
  - 📦 Sản phẩm bị hư hỏng
  - 🚫 Khác (có thể nhập)
- 📝 **Ô Mô Tả Chi Tiết**: "Mô tả tình huống..."
- 📸 **Tải Lên Hình Ảnh/Video**: (Tùy chọn)
- ⚠️ **Cảnh báo**: "Nếu có nguy hiểm an toàn, vui lòng liên hệ ngay"
- ✅ **Nút Gửi**: "GỬI BÁO CÁO" (Màu cam)
- 🔗 **Liên hệ hỗ trợ**: Số điện thoại 24/7

**Luồng Tương Tác**:
1. Shipper gặp vấn đề
2. Nhấn biểu tượng "Báo cáo" hoặc "SOS"
3. Chọn lý do sự cố
4. Nhập mô tả chi tiết
5. Tải lên bằng chứng (nếu có)
6. Gửi báo cáo
7. Nhận xác nhận và hướng dẫn từ hỗ trợ

**Tính Năng Nổi Bật**:
- ✨ Danh sách lý do được sắp xếp hợp lý
- ✨ Hỗ trợ tải lên ảnh/video
- ✨ Liên hệ trực tiếp với hỗ trợ
- ✨ Theo dõi báo cáo theo thời gian thực
- ✨ Ghi log tất cả báo cáo cho bảo hiểm

---

### **7️⃣ Màn Hình Chi Tiết Lịch Sử Đơn Hàng (OrderHistoryItemDetail)**

**Mục Đích**: Xem chi tiết đầy đủ của một đơn hàng đã hoàn thành trong quá khứ

**Mô Tả Chức Năng**:  
Từ lịch sử đơn hàng, shipper có thể xem chi tiết về bất kỳ đơn hàng nào đã hoàn thành. Bao gồm thông tin sản phẩm, lộ trình giao, thanh toán, và thời gian.

**Các Thành Phần Chính**:
- **Header**: Back button + "Amber Hearth" branding + Avatar
- ✅ **Trạng Thái Badge**: "ĐÃ GIAO" (Xanh lá)
- 🏷️ **Số Đơn Hàng**: "#AH-99210"
- 📅 **Thời Gian**: "Hoàn thành vào 24 tháng 10, 2023 lúc 19:42"
- **Nút Hành Động**:
  - 🆘 "CẦN GIÚP ĐỠ?" (Trắng)
  - 🔄 "ĐẶT LẠI" (Cam)
- **Lưới Bento** (3 cột):
  1. **Cột Trái - Dòng Thời Gian Hành Trình**:
     - Tiêu đề: "DÒNG THỜI GIAN HÀNH TRÌNH"
     - 4 bước:
       - 🟠 Nhận Đơn - 6:50 PM
       - 🟠 Chuẩn Bị & Đóng Gói - 7:12 PM
       - 🟠 Nhận Từ - 7:20 PM
       - 🟠 Đã Giao - 7:42 PM
     - Đường nối dọc giữa các bước
  
  2. **Cột Phải - Thông Tin Sản Phẩm & Thanh Toán**:
     - **Thẻ Sản Phẩm Đã Giao**:
       - 3 sản phẩm với hình ảnh:
         - Truffle Umami Burger - $18.50
         - Sweet Potato Fries - $6.00
         - Amber Cold Brew - $5.50
     - **Thẻ Tóm Tắt Thanh Toán**:
       - Cộng lại: $30.00
       - Phí giao: $2.99
       - Phí dịch vụ: $1.50
       - Tổng: $34.49 (Cam, lớn)
       - "THANH TOÁN QUA APPLE PAY"

- **Thông Tin Địa Chỉ**:
  - Từ: "The Penthouse, Sky View Tower" - "452 Urban District, Floor 24"
  - Đến: "Gourmet Hearth Kitchen" - "Downtown Flagship Location"

**Luồng Tương Tác**:
1. Mở lịch sử đơn hàng
2. Chọn một đơn hàng
3. Xem chi tiết đầy đủ
4. Có thể liên hệ hỗ trợ hoặc đặt lại

**Tính Năng Nổi Bật**:
- ✨ Trực quan hóa dòng thời gian
- ✨ Chi tiết sản phẩm với hình ảnh
- ✨ Tóm tắt thanh toán rõ ràng
- ✨ Liên hệ hỗ trợ dễ dàng
- ✨ Chức năng đặt lại (tạo đơn tương tự)

---

### **8️⃣ Màn Hình Lịch Sử & Thu Nhập (HistoryEarnings)**

**Mục Đích**: Tổng quan về thu nhập, thống kê, và lịch sử đơn hàng

**Mô Tả Chức Năng**:  
Màn hình này là bảng điều khiển cho Thu nhập & Lịch sử. Shipper có thể thấy tổng thu nhập, đơn hàng hôm nay, lịch sử chi tiết, và các xu hướng.

**Các Thành Phần Chính**:
- 🔄 **Biểu tượng Pull-to-Refresh** (ở đầu, có thể cuộn)
- **Lưới Bento Thống Kê Nhanh** (2 thẻ):
  1. **Đơn Hàng Hôm Nay** (Thẻ trắng):
     - "ĐƠN HÀNG HÔM NAY: 14" (Lớn)
     - Stack avatars khách hàng + "+12 khách"
  
  2. **Thu Nhập Hàng Tuần** (Gradient cam-đỏ):
     - "THU NHẬP HÀNG TUẦN: $1,248.50" (Trắng, lớn)
     - Badge: "+12% so với tuần trước" (Lớp kính mờ)
     - Watermark trang trí

- **Phần Lịch Sử Đơn Hàng**:
  - Tiêu đề: "LỊCH SỬ ĐƠN HÀNG" + Nút "Bộ Lọc"
  - **3 Thẻ Đơn Hàng**:
    
    **Thẻ 1: Hoàn Thành**
    - Số: "#AH-99210" - "Premium Sushi Set & Miso"
    - Badge: ✅ HOÀN THÀNH (Xanh)
    - Lộ trình: 🏪 NHẬN → 📍 GIAO
    - Biểu tượng kết nối (Đường nối dọc)
    - Phí giao: $12.40
    - Thời gian: "Giao vào Hôm nay, 14:45"
    
    **Thẻ 2: Đã Hủy** (Mờ 80%)
    - Số: "#AH-99184" - "Double Wagyu Burger Combo"
    - Badge: ❌ ĐÃ HỦY (Đỏ)
    - Lộ trình: 🏪 NHẬN → 📍 GIAO
    - Phí giao: $0.00 (Mờ)
    - Thời gian: "Hủy vào Hôm nay, 11:20 AM"
    
    **Thẻ 3: Hoàn Thành**
    - Số: "#AH-99150" - "Espresso Roast & 3x Pastries"
    - Badge: ✅ HOÀN THÀNH (Xanh)
    - Lộ trình: 🏪 NHẬN → 📍 GIAO
    - Phí giao: $8.50
    - Thời gian: "Giao vào Hôm qua, 18:15"

- **Header**: Back + "Amber Hearth" branding + Avatar

**Luồng Tương Tác**:
1. Mở màn hình Thu Nhập
2. Cuộn để refresh dữ liệu
3. Xem thống kê nhanh
4. Cuộn xuống để xem lịch sử
5. Nhấn bộ lọc để lọc theo ngày/trạng thái
6. Nhấn một đơn hàng để xem chi tiết

**Tính Năng Nổi Bật**:
- ✨ Pull-to-refresh cập nhật dữ liệu
- ✨ Hiển thị xu hướng thu nhập
- ✨ Lọc đơn hàng theo nhiều tiêu chí
- ✨ Biểu tượng trạng thái rõ ràng
- ✨ Tính toán tự động phí và tiền hoa hồng

---

### **9️⃣ Màn Hình Quản Lý Phương Tiện (VehicleDetails)**

**Mục Đích**: Quản lý thông tin phương tiện, loại phương tiện, và tài liệu tuân thủ

**Mô Tả Chức Năng**:  
Shipper quản lý chi tiết phương tiện, bao gồm đặc điểm kỹ thuật, loại phương tiện, tài liệu tuân thủ (bảo hiểm, đăng ký), và tình trạng phê duyệt. Đây là nơi duy trì các tài liệu cần thiết.

**Các Thành Phần Chính**:
- **Phần Hình Ảnh Hero** (Nền tối với ảnh phương tiện):
  - Ảnh: Ford E-Transit 2024
  - Overlay gradient
  - Tiêu đề: "Ford E-Transit 2024" (Trắng, lớn)
  - Mô tả: "Full-Size Cargo Van"
  - Nút "CHỈNH SỬA TÊN" (Trắng CTA với mũi tên)

- **Lưới Bento** (4 thẻ lớn):
  
  **1. Thẻ Đặc Điểm Kỹ Thuật**:
  - MẪU & HÃN: "Ford Electric Transit" / "L3H2 High Roof Line"
  - BIỂN SỐ: "TX-HEARTH-24" (Hiển thị kiểu biển)
  - MÀU SẮC: "Frozen White" (Với dấu hiệu màu)
  - LOẠI NHIÊN LIỆU: "100% Electric" (Cam, với biểu tượng)
  
  **2. Thẻ Lựa Chọn Loại Phương Tiện** (Nền cam/cảnh báo):
  - Tiêu đề: "LOẠI PHƯƠNG TIỆN" (Cam)
  - 3 tùy chọn:
    - 📦 Cargo Van (Được chọn - Viền cam)
    - 🚗 Compact Car (Vô hiệu hoá - 60% mờ)
    - 🚲 Electric Bike (Vô hiệu hoá - 60% mờ)
  - Nút: "CHUYỂN DANH MỤC" (Trắng CTA)
  
  **3. Thẻ Tài Liệu Tuân Thủ** (Lớn):
  - Tiêu đề: "TÀI LIỆU TUÂN THỨ" + Nút "NHẬT KÝ KIỂM TOÁN"
  - **3 Thẻ Tài Liệu**:
    
    a. **Bảo Hiểm Thương Mại**:
    - Biểu tượng: 🟠 (Cam)
    - Badge: ✅ ĐƯỢC XÁCNHẬN (Xanh)
    - Hết hạn: "Tháng 12 2024"
    - Nút: "XEM TẬP TIN" / "TẢI XUỐNG"
    
    b. **Đăng Ký Phương Tiện**:
    - Biểu tượng: 🟠
    - Badge: ✅ ĐƯỢC XÁCNHẬN (Xanh)
    - Info: "Chứng chỉ Tiểu bang Texas"
    - Nút: "XEM TẬP TIN" / "TẢI XUỐNG"
    
    c. **Kiểm Định An Toàn**:
    - Biểu tượng: 🟠
    - Badge: ❌ HẾT HẠN (Đỏ, cảnh báo)
    - Info: "Hành động cần thiết ngay lập tức"
    - Nút: "TẢI LÊN CHỨNG CHỈ MỚI" (Cam CTA)
  
  **4. Thẻ Thông Tin Hộp Đơn** (Nền tối, A+ badge):
  - Biểu tượng: 🅰️➕ (Vòng tròn cam)
  - Tiêu đề: "PHƯƠNG TIỆN ĐƯỢC PHÊ DUYỆT BỞI HEARTH"
  - Mô tả: "Phương tiện này đáp ứng tất cả tiêu chuẩn giao hàng cao cấp... bao gồm khả năng kiểm soát nhiệt độ"
  - Nút: "TẢI XUỐNG BADGE" (Trắng)
  - Văn bản: "THÀNH VIÊN TỪ THÁNG 1 2024"

- **Header**: Back + "AMBER HEARTH" (Cam, hoa) + Avatar

**Luồng Tương Tác**:
1. Mở Quản Lý Phương Tiện
2. Xem chi tiết phương tiện
3. Tùy chọn: Chỉnh sửa tên, chuyển loại
4. Xem trạng thái tài liệu
5. Tải lên/tải xuống tài liệu khi cần
6. Liên hệ hỗ trợ nếu tài liệu hết hạn

**Tính Năng Nổi Bật**:
- ✨ Hiển thị toàn bộ chi tiết phương tiện
- ✨ Quản lý tài liệu tuân thủ
- ✨ Cảnh báo khi tài liệu sắp hết hạn
- ✨ Tải lên tài liệu mới
- ✨ Hộp đơn tự động tải xuống/chia sẻ
- ✨ Chuyển loại phương tiện nếu cần

---

### **🔟 Màn Hình Thiết Đặt Rút Tiền (PayoutSettings)**

**Mục Đích**: Quản lý phương thức rút tiền, lịch trình, ngưỡng, và xem lịch sử

**Mô Tả Chức Năng**:  
Shipper cấu hình cách họ muốn nhận thu nhập. Bao gồm tài khoản ngân hàng, tần suất rút tiền (hàng ngày/hàng tuần), ngưỡng rút tiền tối thiểu, và xem lịch sử rút tiền.

**Các Thành Phần Chính**:
- **Header**: "CÀI ĐẶT RÚT TIỀN"
- Mô tả: "Quản lý cách và khi nào bạn nhận thu nhập"

- **Lưới Bento** (5 thẻ):
  
  **1. Thẻ Hero - Số Dư Có Sẵn** (Gradient cam-đỏ):
  - Tiêu đề: "CÓ SẴN RÚT TIỀN: $1,248.50" (Trắng, lớn)
  - Badge: "Rút tiền tiếp theo: 27 tháng 10, 2023" (Kính mờ)
  - Watermark trang trí

  **2. Thẻ Tài Khoản Ngân Hàng Chính**:
  - Tiêu đề: "TÀI KHOẢN NGÂN HÀNG CHÍNH"
  - Mô tả: "Đích đến gửi tiền trực tiếp"
  - Nút "CHỈNH SỬA" (Cam)
  - Khung viền đứt nét:
    - Ngân hàng: "Chase Signature Business"
    - Tài khoản: "•••• •••• •••• 8829"
    - Badge: ✅ ĐƯỢC XÁCNHẬN (Xanh)

  **3. Thẻ Lịch Trình Rút Tiền**:
  - Tiêu đề: "LỊCH TRÌNH RÚT TIỀN"
  - 2 tùy chọn (Radio button):
    - 🔘 "HÀNG TUẦN" (Được chọn - Cam)
    - ⭕ "HÀNG NGÀY (RÚT TIỀN TỨC THỜI)" 
  - Ghi chú: "*Rút tiền tức thời hàng ngày phí xử lý 1.5%. Rút tiền hàng tuần miễn phí."

  **4. Thẻ Ngưỡng Rút Tiền**:
  - Tiêu đề: "NGƯỠNG RÚT TIỀN"
  - Slider: $0 --- [●] --- $500 (Hiện tại: $100)
  - Ghi chú về lợi ích báo cáo thuế
  - Khung thông tin: Mẹo về ngưỡng để tối ưu hóa khoản phí

  **5. Thẻ Lịch Sử Gần Đây** (Lớn):
  - Tiêu đề: "LỊCH SỬ GẦN ĐÂY" + Liên kết "XEM BÁO CÁO ĐẦY ĐỦ"
  - **Bảng**:
    - Cột: NGÀY | ĐẮC ĐIỂM | TRẠNG THÁI | SỐ TIỀN
    - Hàng 1: 2023-10-26 | Chase ••• 8829 | ✅ Đã Thanh Toán | +$1,200.00
    - Hàng 2: 2023-10-19 | Chase ••• 8829 | ✅ Đã Thanh Toán | +$950.50

- **Header**: Back + "AMBER HEARTH" branding + Avatar

**Luồng Tương Tác**:
1. Mở Cài Đặt Rút Tiền
2. Xem số dư có sẵn
3. Chỉnh sửa tài khoản ngân hàng nếu cần
4. Chọn lịch trình rút tiền
5. Điều chỉnh ngưỡng rút tiền
6. Xem lịch sử rút tiền trước đó

**Tính Năng Nổi Bật**:
- ✨ Hiển thị số dư trực tiếp và rõ ràng
- ✨ Quản lý tài khoản ngân hàng
- ✨ Chọn tần suất rút tiền
- ✨ Tính toán tự động phí dựa trên lịch trình
- ✨ Ngưỡng tùy chỉnh để tối ưu hóa
- ✨ Lịch sử giao dịch chi tiết
- ✨ Xem báo cáo đầy đủ

---

### **1️⃣1️⃣ Màn Hình Thông Báo (Notifications)**

**Mục Đích**: Xem tất cả thông báo từ hệ thống, khách hàng, nhà hàng

**Mô Tả Chức Năng**:  
Trung tâm hoạt động cho tất cả các thông báo. Shipper nhận thông báo về đơn hàng mới, giao hàng sắp tới, tin nhắn từ khách hàng/nhà hàng, cập nhật tài khoản, v.v.

**Các Thành Phần Chính**:
- **Header**: "THÔNG BÁO" + Nút "Đánh dấu tất cả là đã đọc"
- **Tabs/Bộ Lọc**:
  - Tất Cả
  - Đơn Hàng
  - Tin Nhắn
  - Hệ Thống
  - (Có thể mở rộng)

- **Danh Sách Thông Báo** (Scroll):
  - **Thông Báo Đơn Hàng**:
    - 🏪 "Đơn Hàng Mới Từ The Ember Kitchen"
    - Tiêu đề: "Premium Burger Set - $24.50 tiền hoa hồng"
    - Thời gian: "2 phút trước"
    - Trạng thái: Chưa đọc (Điểm cam)
    - Nút: "CHẤP NHẬN" / "TỪCHỐI"
  
  - **Thông Báo Tin Nhắn**:
    - 💬 "Tin nhắn từ Marcus Vane"
    - "Vui lòng giao vào cửa sau, cảm ơn!"
    - Thời gian: "5 phút trước"
    - Trạng thái: Chưa đọc
  
  - **Thông Báo Hệ Thống**:
    - ⚠️ "Cập Nhật Tài Liệu Cần Thiết"
    - "Chứng chỉ kiểm định an toàn sắp hết hạn"
    - Thời gian: "1 giờ trước"
    - Nút: "KIỂM TRA NGAY"
  
  - **Thông Báo Động Lực**:
    - 🎉 "Chúc Mừng! Bạn Đạt 50 Lần Giao Hàng!"
    - "Mở khóa huy hiệu 'Giao Hàng Siêu Sao'"
    - Thời gian: "1 ngày trước"

- **Cài Đặt Thông Báo** (Nằm ở footer):
  - Nút: "QUẢN LÝ CÀI ĐẶT THÔNG BÁO"

**Luồng Tương Tác**:
1. Mở Thông Báo
2. Bộ lọc theo loại (Đơn hàng, Tin nhắn, v.v.)
3. Nhấn thông báo để xem chi tiết/hành động
4. Chấp nhận/từ chối đơn hàng
5. Xem tin nhắn
6. Quản lý cài đặt thông báo

**Tính Năng Nổi Bật**:
- ✨ Bộ lọc theo loại thông báo
- ✨ Thay đổi nhanh chóng từ thông báo (chấp nhận đơn)
- ✨ Đánh dấu tất cả là đã đọc
- ✨ Cài đặt thông báo tùy chỉnh
- ✨ Hữu ích thông tin (giờ, loại, hành động)

---

### **1️⃣2️⃣ Màn Hình Hồ Sơ Shipper (ShipperProfile)**

**Mục Đích**: Xem và chỉnh sửa thông tin hồ sơ shipper

**Mô Tả Chức Năng**:  
Hồ sơ cá nhân của shipper hiển thị thông tin cá nhân, thống kê hiệu suất, xếp hạng, huy hiệu, và các tùy chọn để chỉnh sửa.

**Các Thành Phần Chính**:
- **Phần Header Hồ Sơ**:
  - Avatar lớn (180x180px)
  - Tên: "Marcus Vane"
  - Thống kê nhanh:
    - ⭐ Xếp hạng: 4.92 (Màu cam)
    - 📦 Tổng giao hàng: 284
    - 🎯 Tỷ lệ thành công: 99.8%
  - Nút "CHỈNH SỬA HỒ SƠ" (Cam CTA)

- **Phần Thống Kê Chi Tiết** (Bento grid - 4 thẻ):
  - THU NHẬP HÔM NAY: $145.50
  - GIA TỬ HÀNG TUẦN: $1,248.50
  - ĐƠN HUỶ HẢI TUẦN: 2
  - THÀNH VIÊN TỪ: JAN 2024

- **Phần Huy Hiệu & Thành Tích**:
  - 🏆 "Giao Hàng Siêu Sao" - 50 lần giao
  - 🏆 "Shipper Trong Tuần" - Jan 2024
  - 🏆 "Khách Hàng Hài Lòng" - 4.8+ xếp hạng

- **Phần Cài Đặt** (Danh sách):
  - ⚙️ Cài Đặt Tài Khoản
  - 🔐 Bảo Mật & Quyền Riêng Tư
  - 💳 Phương Thức Thanh Toán
  - 🚗 Quản Lý Phương Tiện
  - 📞 Hỗ Trợ & Phản Hồi
  - 📋 Điều Khoản & Chính Sách
  - 🚪 Đăng Xuất

**Luồng Tương Tác**:
1. Mở Hồ Sơ
2. Xem thông tin và thống kê
3. Xem huy hiệu
4. Chỉnh sửa hồ sơ nếu cần
5. Truy cập các cài đặt khác
6. Đăng xuất nếu cần

**Tính Năng Nổi Bật**:
- ✨ Hiển thị xếp hạng và thống kê hiệu suất
- ✨ Hệ thống huy hiệu để khuyến khích
- ✨ Chỉnh sửa thông tin hồ sơ
- ✨ Quản lý cài đặt tài khoản
- ✨ Liên hệ hỗ trợ dễ dàng

---

### **1️⃣3️⃣ Màn Hình Bản Đồ Giao Hàng Hoạt Động (ActiveDeliveryMapView)**

**Mục Đích**: Hiển thị bản đồ thực thời cho giao hàng hiện tại

**Mô Tả Chức Năng**:  
Khi shipper đang giao hàng, màn hình này hiển thị bản đồ hoạt động thực thời, vị trí giao hàng, lộ trình tối ưu, và cập nhật GPS.

**Các Thành Phần Chính**:
- **Bản Đồ Mapbox** (Toàn màn hình):
  - 📍 Vị trí hiện tại (Điểm xanh lá)
  - 🏪 Nhà hàng (Biểu tượng)
  - 📦 Điểm giao (Biểu tượng chỉ định địa chỉ)
  - 🛣️ Lộ trình (Đường cam/xanh)
  - 🚗 Các shipper khác (Nếu hiển thị)

- **Bottom Sheet Mini** (Nửa phía dưới):
  - Tiêu đề: Tên nhà hàng
  - Đơn hàng: "#8829"
  - Địa chỉ giao
  - Thời gian dự kiến
  - Phí giao
  - Nút "XEM CHI TIẾT" (Mở bottom sheet đầy đủ)

- **Header Controls**:
  - ← Back button
  - 🎯 Nút "Định Tâm" (Căn giữa vị trí hiện tại)
  - 📞 Nút "Liên Hệ" (Gọi nhà hàng hoặc khách hàng)

- **Lớp Phủ Thông Tin**:
  - Tốc độ: "42 km/h"
  - Độ Chính Xác: "7m"
  - Thời gian còn lại: "8 phút"

**Luồng Tương Tác**:
1. Shipper chấp nhận đơn
2. Mở bản đồ
3. Theo dõi lộ trình được đề xuất
4. Có thể liên hệ nhà hàng
5. Hoàn tất khi đến đích

**Tính Năng Nổi Bật**:
- ✨ GPS thực thời chính xác cao
- ✨ Lộ trình tối ưu tính toán tự động
- ✨ Cực kỳ chi tiết vị trí (tốc độ, độ chính xác)
- ✨ Liên hệ nhanh từ bản đồ
- ✨ Offline map capability

---

### **1️⃣4️⃣ Màn Hình Quên Mật Khẩu (ForgotPasswordShipper)**

**Mục Đích**: Giúp shipper đặt lại mật khẩu nếu quên

**Mô Tả Chức Năng**:  
Quy trình đặt lại mật khẩu gồm xác thực email/sđt, gửi mã OTP, xác minh mã, và tạo mật khẩu mới.

**Các Thành Phần Chính**:
- **Bước 1: Nhập Email/Số ĐT**:
  - Tiêu đề: "QUÊN MẬT KHẨU?"
  - Trường nhập: "Nhập Email hoặc Số Điện Thoại"
  - Nút "TIẾP TỤC" (Cam)
  - Liên kết quay lại: "Quay Lại Đăng Nhập"

- **Bước 2: Xác Thực OTP**:
  - Tiêu đề: "NHẬP MÃ XÁC MINH"
  - Mô tả: "Chúng tôi đã gửi mã đến ..."
  - 4-6 trường nhập OTP
  - Nút "XÁC MINH" (Cam)
  - Liên kết "Gửi lại mã" (Nếu không nhận)

- **Bước 3: Tạo Mật Khẩu Mới**:
  - Tiêu đề: "TẠO MẬT KHẨU MỚI"
  - Trường 1: "Mật Khẩu Mới"
  - Trường 2: "Xác Nhận Mật Khẩu"
  - Thanh yêu cầu mật khẩu mạnh:
    - ✅ Ít nhất 8 ký tự
    - ✅ Ít nhất 1 chữ hoa
    - ✅ Ít nhất 1 số
    - ✅ Ít nhất 1 ký tự đặc biệt
  - Nút "ĐẶT LẠI MẬT KHẨU" (Cam)

- **Bước 4: Thành Công**:
  - ✅ Biểu tượng thành công
  - "MẬT KHẨU ĐÃ ĐẶT LẠI!"
  - Nút "QUAY VỀ ĐĂNG NHẬP" (Cam)

**Luồng Tương Tác**:
1. Nhấn "Quên Mật Khẩu?" trên màn hình đăng nhập
2. Nhập email hoặc số điện thoại
3. Nhận OTP qua email/SMS
4. Nhập mã OTP
5. Tạo mật khẩu mới mạnh
6. Được chuyển về đăng nhập

**Tính Năng Nổi Bật**:
- ✨ Xác minh 2 bước (Email/SMS)
- ✨ Yêu cầu mật khẩu mạnh hiển thị rõ
- ✨ OTP hết hạn sau 10 phút
- ✨ Gửi lại OTP (Nếu không nhận)
- ✨ Hỗ trợ khách hàng nếu vấn đề

---

### **1️⃣5️⃣ Màn Hình Bắt Đầu (Splash Screen)**

**Mục Đích**: Hiển thị logo và tải ứng dụng

**Mô Tả Chức Năng**:  
Màn hình tải ban đầu khi khởi động ứng dụng, kiểm tra xác thực, và chuẩn bị dữ liệu.

**Các Thành Phần Chính**:
- 🎨 **Hình Nền**: Gradient cam-đỏ hoặc ảnh luyện tập
- 🏢 **Logo Amber Hearth** (Lớn, giữa màn hình)
- ⏳ **Thanh Tải** hoặc **Spinner** (Dưới logo)
- 💬 **Tin Nhắn Trạng Thái**:
  - "Đang chuẩn bị..."
  - "Đang tải dữ liệu..."
  - "Đã sẵn sàng"
- ©️ **Chân trang**: Thông tin bản quyền
- **Thời gian**: 3-5 giây

**Luồng Tương Tác**:
1. Mở ứng dụng → Splash screen
2. Ứng dụng kiểm tra xác thực
3. Tải dữ liệu cần thiết
4. Tự động chuyển đến:
   - Màn hình đăng nhập (Nếu chưa đăng nhập)
   - Bản đồ chính (Nếu đã đăng nhập)

**Tính Năng Nổi Bật**:
- ✨ Kiểm tra kết nối internet
- ✨ Xác thực token từ bộ nhớ cục bộ
- ✨ Tải dữ liệu cơ sở (hồ sơ, đơn hàng)
- ✨ Hiển thị tiến trình tải
- ✨ Xử lý lỗi kết nối (Retry)

---

### **1️⃣6️⃣ Màn Hình Menu Điều Hướng (NavigationMenu)**

**Mục Đích**: Hiển thị menu chính để điều hướng đến các phần khác nhau

**Mô Tả Chức Năng**:  
Menu chính (Drawer hoặc Bottom Tab) với các liên kết đến tất cả phần chính của ứng dụng.

**Các Thành Phần Chính**:
- **Header Menu**:
  - 👤 Avatar + Tên Shipper
  - ⭐ Xếp Hạng + Thống kê nhanh
  - 🔔 Số thông báo chưa đọc

- **Mục Menu**:
  - 🏠 Trang Chủ (Bản Đồ)
  - 📦 Đơn Hàng Hoạt Động
  - 📊 Lịch Sử & Thu Nhập
  - 🚗 Quản Lý Phương Tiện
  - 💳 Thiết Đặt Rút Tiền
  - 👤 Hồ Sơ
  - ⚙️ Cài Đặt
  - 📞 Hỗ Trợ & Phản Hồi
  - 🚪 Đăng Xuất

- **Footer**:
  - Phiên bản ứng dụng
  - Liên kết đến chính sách

**Luồng Tương Tác**:
1. Nhấn biểu tượng menu (☰) hoặc vuốt từ cạnh
2. Menu xuất hiện
3. Chọn mục muốn vào
4. Chuyển đến màn hình tương ứng

**Tính Năng Nổi Bật**:
- ✨ Truy cập nhanh từ bất kỳ màn hình
- ✨ Hiển thị thông tin người dùng
- ✨ Chỉ báo thông báo chưa đọc
- ✨ Responsive (Drawer hoặc Bottom Tab tuỳ thiết bị)

---

### **1️⃣7️⃣ Màn Hình Yêu Cầu Vị Trí GPS (LocationPermission)**

**Mục Đích**: Yêu cầu quyền truy cập GPS chi tiết

**Mô Tả Chức Năng**:  
Cho phép người dùng bật GPS để ứng dụng có thể theo dõi vị trí thực thời.

**Các Thành Phần Chính**:
- 📍 **Biểu Tượng GPS** (Lớn, cam)
- 📌 **Tiêu Đề**: "TRUY CẬP VỊ TRỊ"
- 📝 **Mô Tả**: "Chúng tôi cần truy cập vị trị để:"
  - ✅ Cập nhật vị trí thực thời trên bản đồ
  - ✅ Tính toán lộ trình tối ưu
  - ✅ Thông báo đơn hàng gần nhất
  - ✅ Đảm bảo an toàn giao hàng
- 🔐 **Bảo Mật**: "Chúng tôi chỉ sử dụng vị trị khi ứng dụng chạy"
- ✅ **Nút "CẤP QUYỀN"** (Cam, lớn)
- ❌ **Nút "BỎ QUA"** (Trắng hoặc text link)

**Luồng Tương Tác**:
1. Thấy yêu cầu quyền
2. Nhấn "Cấp Quyền"
3. Hệ thống mở cài đặt quyền
4. Chọn "Cho Phép Khi Sử Dụng" hoặc "Luôn Cho Phép"
5. Quay lại ứng dụng
6. GPS được kích hoạt

**Tính Năng Nổi Bật**:
- ✨ Giải thích rõ tại sao cần GPS
- ✨ Đảm bảo bảo mật dữ liệu
- ✨ Dễ cấp quyền
- ✨ Có thể bỏ qua nếu muốn (Không khuyến khích)

---

### **1️⃣8️⃣ Màn Hình Trạng Thái Shipper Online (StatusOnlineShipper)**

**Mục Đích**: Cho phép shipper chuyển đổi trạng thái online/offline

**Mô Tả Chức Năng**:  
Shipper có thể bật/tắt trạng thái "Online" để nhận hoặc từ chối các đơn hàng. Khi online, sẽ nhận được thông báo đơn hàng.

**Các Thành Phần Chính**:
- **Toggle Online/Offline** (Lớn, nổi bật):
  - 🟢 "ONLINE" - Sẵn sàng nhận đơn
  - 🔴 "OFFLINE" - Tạm dừng
- **Thông Tin Trạng Thái**:
  - "Bạn hiện đang online"
  - "Sẽ nhận thông báo đơn hàng mới"
  - Hoặc: "Bạn hiện đang offline"
  - "Sẽ không nhận thông báo"
- **Thống Kê Nhanh**:
  - Đơn Hôm Nay: 12
  - Thu Nhập Hôm Nay: $145.50
  - Xếp Hạng: 4.92⭐
- **Nút Hành Động**:
  - 🔔 "Cài Đặt Thông Báo" (Để cấu hình)
  - 📊 "Xem Thống Kê" (Mở màn hình lịch sử)
  - ⚙️ "Cài Đặt" (Mở cài đặt)

**Luộc Tương Tác**:
1. Mở ứng dụng hoặc vào menu
2. Nhấn nút Online/Offline
3. Chuyển đổi trạng thái
4. Nhận xác nhận thay đổi
5. Bắt đầu/dừng nhận đơn hàng

**Tính Năng Nổi Bật**:
- ✨ Toggle dễ sử dụng
- ✨ Xác nhận trạng thái rõ ràng
- ✨ Hiển thị thống kê nhanh
- ✨ Cấu hình thông báo dễ dàng
- ✨ Lưu trạng thái giữa các phiên

---

## 🎨 Hệ Thống Thiết Kế Toàn Cục

### **Bảng Màu**:
- 🟠 **Cam Chính**: #f49d25 (CTA, Nhấn Mạnh)
- 🔴 **Cam Tối**: #ea580c (Tiêu Đề, Đối Cảnh)
- 🤎 **Nền**: #f8f7f5 (Màu Nền Chính)
- ⚫ **Văn Bản**: #1c160d (Văn Bản Chính)
- 🟤 **Phụ**: #9c7a49 (Nhãn, Mô Tả)
- ✅ **Xanh Lá**: #15803d, #22c55e (Thành Công)
- ❌ **Đỏ**: #b91c1c (Lỗi, Đã Hủy)
- ⚪ **Trắng**: #ffffff (Nền Card)

### **Typography**:
- 🔤 **Font**: Plus Jakarta Sans
- **Kiểu**: Bold, ExtraBold, Regular, Medium
- **Heading**: 24-36px ExtraBold
- **Body**: 14-16px Regular
- **Label**: 10-12px Bold (Uppercase)

### **Component Library**:
- ✅ Nút (Rounded, Shadow)
- ✅ Card (Rounded Corner, Subtle Shadow)
- ✅ Badge (Status, Color-coded)
- ✅ Input (Icon, Label Nổi)
- ✅ Bottom Sheet (40px radius)
- ✅ Timeline (Connector)
- ✅ Slider (Range)
- ✅ Table (Rows, Status)

---

## 📱 Responsive Design

**Thiết Kế Cho**:
- 📱 Mobile: 390px - 480px
- 📱 Tablet: 600px - 1024px
- 🖥️ Desktop Web: 1024px+

**Nguyên Tắc**:
- Mobile-First Approach
- Touch-Friendly (Min 44px tap targets)
- Responsive Layout (Flexbox, Grid)
- Landscape Support

---

## 🔄 Luồng Người Dùng Chính

### **Cycle 1: Onboarding (Lần Đầu)**
```
Splash → Đăng Ký → Cấp Quyền (GPS/Notification) → GPS Permission → Bản Đồ Chính
```

### **Cycle 2: Giao Hàng Hàng Ngày**
```
Bản Đồ → Chấp Nhận Đơn → Chi Tiết Đơn → Giao Hàng (GPS) → Hoàn Thành → Thành Công
```

### **Cycle 3: Quản Lý & Lịch Sử**
```
Menu → Lịch Sử & Thu Nhập → Chi Tiết Đơn → Xem Lộ Trình
```

### **Cycle 4: Cài Đặt & Quản Lý**
```
Menu → Hồ Sơ / Phương Tiện / Rút Tiền → Chỉnh Sửa → Lưu
```

---

## 🚀 Các Tính Năng Nổi Bật

### **Tích Hợp GPS**
- GPS thực thời chính xác cao
- Cập nhật vị trí mỗi 5-10 giây
- Bản đồ offline (Cache)
- Lộ trình tối ưu (Mapbox Directions API)

### **Thông Báo**
- Push notification cho đơn hàng mới
- Thông báo tài liệu sắp hết hạn
- Tin nhắn từ khách hàng/nhà hàng
- Badge count cập nhật động

### **An Toàn**
- Xác thực 2 lớp (Email + OTP)
- Mã hóa dữ liệu nhạy cảm
- Ghi log tất cả hành động
- Báo cáo sự cố tích hợp

### **Thu Nhập & Thanh Toán**
- Tính toán tự động tiền hoa hồng
- Rút tiền hàng ngày/hàng tuần
- Lịch sử giao dịch chi tiết
- Hóa đơn điện tử

### **Hiệu Suất & Xếp Hạng**
- Xếp hạng thực thời (1-5 sao)
- Huy hiệu & thành tích
- Xu hướng hiệu suất hàng tuần
- So sánh với các shipper khác (Anonymous)

---

## ✅ Checklist Triển Khai

- [ ] Kết nối API backend cho authentication
- [ ] Thiết lập GPS & Mapbox
- [ ] Cấu hình Push Notification
- [ ] Kết nối Redux store
- [ ] Thiết lập WebSocket (STOMP)
- [ ] Cấu hình đơn vị chính xác
- [ ] Thử nghiệm trên thiết bị thực
- [ ] Tối ưu hóa hiệu suất
- [ ] Kiểm tra tính bảo mật
- [ ] Beta testing với nhóm
- [ ] Phát hành App Store/Play Store

---

## 📞 Liên Hệ & Hỗ Trợ

**Hỗ Trợ 24/7**: support@amberhealth.com  
**Hotline**: 1-800-AMBER-HEARTH  
**Website**: www.amberhealth.com/shipper

---

**Tài Liệu được chuẩn bị bằng Tiếng Việt**  
**Version**: 1.0  
**Ngày Cập Nhật**: 2024-10-27  
**Trạng Thái**: Hoàn Thiện & Sẵn Sàng Triển Khai

