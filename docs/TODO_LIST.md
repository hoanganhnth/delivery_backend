# 📋 TODO LIST & CRITICAL GAPS

Dưới đây là danh sách toàn bộ các tính năng (Use Cases) chưa hoàn thiện hoặc chưa bắt đầu, được trích xuất từ tài liệu đặc tả. Danh sách được phân loại theo mức độ ưu tiên để theo dõi và triển khai.

> **Cập nhật lần cuối:** 2026-05-15

## 🔴 ƯU TIÊN CẤP BÁCH (Critical Gaps - Chặn luồng chính)

- [x] **[Backend - Order Service]** Tích hợp Flash Sale Reserve:
  - ✅ `OrderServiceImpl.java` đã gọi `flashSaleClient.reserveStock()` khi checkout.
- [x] **[Customer App]** Truyền `flashSaleItemId` khi checkout:
  - ✅ `CartItemEntity`, `OrderItemRequest`, `checkout_screen.dart` đã có trường `flashSaleItemId`.
- [x] **[Backend - Order Service]** Tích hợp Promotion Reserve:
  - ✅ `OrderServiceImpl.java` đã gọi `promotionClient.reserveVouchers()`.
- [x] **[Customer App]** Tích hợp thanh toán Voucher:
  - ✅ `CreateOrderRequestDto` có `voucherIds`, `VoucherBottomSheet` gọi `/calculate`.
- [x] **[Backend - Sync]** Tích hợp Kafka `entity-sync`:
  - ✅ `SearchSyncPublisher` đã publish event trong cả `restaurant-service` + `shipper-service`.

## 🟡 ƯU TIÊN CAO (Quản trị & Trải nghiệm cốt lõi)

- [x] **[Customer App]** Tích hợp Elasticsearch:
  - ✅ Module `features/search/` đã có đầy đủ: datasource gọi `/api/search/*`, models, providers, screens (tabs: Dishes/Restaurants/Shippers).
- [x] **[Admin Web]** UI Flash Sale:
  - ✅ `AdminFlashSalePage.tsx` (337 dòng) — Tạo Campaign, set giờ, duyệt Item.
- [x] **[Admin Web]** UI Promotion:
  - ✅ `AdminCouponsPage.tsx` (262 dòng) — Tạo, xem, xóa mã giảm giá.
- [x] **[Restaurant Web]** UI Flash Sale:
  - ✅ `RestaurantFlashSalePage.tsx` (291 dòng) — Form đăng ký món, giá, số lượng.
- [x] **[Restaurant Web]** UI Promotion (Shop):
  - ✅ `CouponManagementPage.tsx` (261 dòng) — CRUD mã giảm giá nội bộ nhà hàng.
- [x] **[Restaurant Web]** Dashboard Thống kê:
  - ✅ `RestaurantDashboard.tsx` (468 dòng) — Biểu đồ doanh thu, đơn hàng.
- [x] **[Admin Web]** Dashboard Thống kê:
  - ✅ `AdminDashboard.tsx` (469 dòng) — Overview toàn sàn, biểu đồ, Top Restaurants.

## 🟢 ƯU TIÊN TRUNG BÌNH (Hoàn thiện tính năng)

- [x] **[Admin Web]** Quản lý Nhà Hàng / Menu / User:
  - ✅ Có đầy đủ `RestaurantListPage`, `MenuManagement`, `AdminShippersPage`, `AdminOrdersPage`, `AdminRatingsPage`, `AdminWithdrawalsPage`.
- [x] **[Admin Web]** Realtime Shipper Tracking: Hiển thị vị trí Shipper đang giao đơn lên bản đồ admin.
  - ✅ Đã implement `ShipperLocationSocketService` dùng `@stomp/stompjs` + `react-leaflet` trên `AdminShipperTrackingPage.tsx`.
- [x] **[Customer App]** Ví Voucher:
  - ✅ Backend: Đã thêm `GET /api/promotions/my-vouchers` endpoint.
  - ✅ App: Đã thêm `getMyVouchers()` vào `PromotionDataSource`.
  - ⚠️ Còn thiếu: Màn hình UI hiển thị danh sách voucher đã lưu (cần tạo thêm screen).
- [x] **[Customer App]** Chat CSKH:
  - ✅ Module `features/support/` đã implement đầy đủ: screen, input bar, message bubble, typing indicator, Firebase datasource.

## ⚪ ƯU TIÊN THẤP (Nice-to-have)

- [x] **[Customer App]** Cải thiện màn Lịch sử đặt hàng (Thêm nút "Đặt lại đơn này").
  - ✅ Đã implement `onOrderReorder` trong `OrdersScreen` để đưa các món cũ vào Giỏ hàng và điều hướng tới CartScreen.
- [x] **[Shipper App]** Hỗ trợ Đăng nhập Social (Google, Apple).
  - ✅ Đã implement `GoogleSignin` và `appleAuth` trên `LoginScreen.tsx` (React Native).
