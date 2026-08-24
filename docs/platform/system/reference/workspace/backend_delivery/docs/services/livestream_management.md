# 🎥 Livestream Management

## 1. Đặc tả (Specification)
**Mục tiêu:** Cho phép Admin hoặc Merchant phát trực tiếp (livestream) để bán hàng. Người dùng xem live và mua sản phẩm được ghim trực tiếp trên video. Nâng cao tương tác người dùng.

**Microservices liên quan:**
- `livestream-service`: Quản lý danh sách phòng live, xử lý chat, và cấp phát Token cho giao thức RTC.
- **Third-party:** Agora RTC (Real-Time Communication) đảm nhiệm truyền tải Video/Audio.

## 2. Danh sách Use Cases
| Mã UC | Tên Use Case | Nền tảng | Trạng thái |
|-------|--------------|----------|------------|
| UC-7.1 | Tạo & Quản lý phòng Live | Admin Web | ✅ Done |
| UC-7.2 | Xem danh sách Live đang phát | Customer App | ✅ Done |
| UC-7.3 | Ghim sản phẩm (Pinned Product) | Admin Web | ✅ Done |
| UC-7.4 | Mua sản phẩm từ Livestream | Customer App | ✅ Done |

## 3. Luồng nghiệp vụ (Business Flow)

### 3.1. Khởi tạo & Join Livestream
1. Host (Admin/Merchant) tạo phòng trên Web. `livestream-service` gọi API của Agora để lấy `Host_Token` và lưu trạng thái phòng thành `IS_LIVE`.
2. Khách hàng trên Mobile App bấm vào phòng đang phát, App sẽ gọi API lấy `Viewer_Token`.
3. SDK Agora trên cả Web (Host) và App (Viewer) tự động kết nối với nhau qua Token để truyền tải Video/Audio theo thời gian thực với độ trễ cực thấp (< 1s). Hệ thống backend của chúng ta không gánh băng thông video.

### 3.2. Tương tác sản phẩm (Product Pinning)
- Host chọn một sản phẩm đang bán từ danh sách và nhấn "Ghim".
- Server nhận lệnh, lập tức push một event (thông qua WebSocket nội bộ hoặc Agora RTM) xuống tất cả Client đang xem live đó.
- App của khách hàng bắt event này, hiển thị một Overlay Popup sản phẩm ngay trên góc màn hình video, cho phép user bấm "Thêm vào giỏ" ngay lập tức mà không cần thoát khỏi phiên xem live.
