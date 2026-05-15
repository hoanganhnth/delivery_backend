# 📋 DELIVERY PLATFORM — TỔNG HỢP USE CASES

> **Cập nhật:** 2026-05-14  
> **Platforms:** Customer App (Flutter) · Shipper App (React Native) · Restaurant Web (Vite/React) · Admin Web (Vite/React) · Backend (17 Microservices)  
> **Ưu tiên:** 🔴 Critical · 🟡 High · 🟢 Medium · ⚪ Low  
> **Trạng thái:** ✅ Done · 🔧 Partial · ❌ Not Started

---

## 1. AUTHENTICATION & USER MANAGEMENT

### UC-1.1: Đăng ký / Đăng nhập (Email + Social) 🔴
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Đăng nhập Email/Password | ✅ |
| Customer App | Đăng nhập Google/Facebook | ✅ |
| Shipper App | Đăng nhập Email/Password | ✅ |
| Shipper App | Đăng nhập Social | ❌ |
| Admin Web | Đăng nhập Admin | ✅ |
| Backend | Auth Service (JWT + Social OAuth) | ✅ |

### UC-1.2: Quản lý hồ sơ người dùng 🟡
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Xem/sửa profile, avatar, phone | ✅ |
| Customer App | Quản lý địa chỉ giao hàng | ✅ |
| Shipper App | Xem/sửa profile (biển số, CCCD, ảnh) | ✅ |
| Admin Web | Quản lý user (CRUD, khóa tài khoản) | 🔧 |
| Backend | User Service + Shipper Service | ✅ |

### UC-1.3: Quản lý phiên đăng nhập & Token 🟢
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Auto refresh token, logout | ✅ |
| Shipper App | Auto refresh token, logout | ✅ |
| Backend | JWT refresh token flow | ✅ |

---

## 2. RESTAURANT & MENU

### UC-2.1: Duyệt nhà hàng 🔴
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Danh sách nhà hàng (featured, nearby, category) | ✅ |
| Customer App | Tìm kiếm nhà hàng theo tên/loại | ✅ |
| Customer App | Xem chi tiết nhà hàng + menu | ✅ |
| Customer App | Hiển thị khoảng cách & thời gian giao ước tính | ✅ |
| Admin Web | CRUD nhà hàng | 🔧 |
| Backend | Restaurant Service (CRUD, search, location-based) | ✅ |

### UC-2.2: Quản lý menu 🟡
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Xem menu theo category | ✅ |
| Admin Web | CRUD menu items, giá, ảnh, trạng thái | 🔧 |
| Backend | MenuItem CRUD + cache (Redis) | ✅ |

### UC-2.3: Đánh giá nhà hàng 🟢
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Đánh giá sao + viết review sau khi nhận hàng | ✅ |
| Admin Web | Xem/duyệt đánh giá | ✅ |
| Backend | Rating API trong Restaurant Service | ✅ |

---

## 3. GIỎ HÀNG & ĐẶT HÀNG

### UC-3.1: Giỏ hàng 🔴
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Thêm/xoá/sửa số lượng món | ✅ |
| Customer App | Hiển thị tổng tiền + phí ship | ✅ |
| Customer App | Ghi chú cho nhà hàng | ✅ |
| Backend | Cart tính tổng + validate giá | ✅ |

### UC-3.2: Đặt hàng 🔴
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Chọn địa chỉ giao hàng | ✅ |
| Customer App | Xác nhận đơn & thanh toán | ✅ |
| Customer App | Hiển thị loading khi đang tìm shipper | ✅ |
| Admin Web | Xem danh sách đơn hàng | 🔧 |
| Backend | Order Service → Kafka → Delivery Service → Match | ✅ |

### UC-3.3: Huỷ đơn hàng 🟡
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Huỷ đơn khi chưa có shipper | ✅ |
| Customer App | Huỷ đơn khi đã có shipper (yêu cầu lý do) | ✅ |
| Shipper App | Huỷ đơn đang nhận (yêu cầu lý do) | ✅ |
| Backend | Cancel flow + Kafka event + compensation | ✅ |

### UC-3.4: Thanh toán 🟡
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Thanh toán COD (tiền mặt) | ✅ |
| Customer App | Thanh toán online (VNPay/MoMo/ZaloPay) | ✅ |
| Backend | Payment integration | ✅ |

---

## 4. GIAO HÀNG & TRACKING

### UC-4.1: Tìm shipper phù hợp 🔴
| Platform | Task | Status |
|----------|------|--------|
| Backend | Match Service: tìm shipper gần nhất (Redis GEO) | ✅ |
| Backend | Loại trừ shipper đang bận (busy Redis key + TTL) | ✅ |
| Backend | Retry tìm shipper mới khi bị từ chối | ✅ |
| Backend | Timeout nếu không tìm được shipper | ✅ |

### UC-4.2: Shipper nhận/từ chối đơn 🔴
| Platform | Task | Status |
|----------|------|--------|
| Shipper App | Popup nhận đơn (MatchFoundPopup, countdown 15s) | ✅ |
| Shipper App | Chặn nhận đơn mới khi đang có đơn | ✅ |
| Backend | Accept/Reject API + guard check active delivery | ✅ |

### UC-4.3: Tracking đơn hàng (Customer) 🔴
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Theo dõi vị trí shipper realtime (WebSocket) | ✅ |
| Customer App | Hiện tiến trình đơn (timeline status) | ✅ |
| Customer App | Vẽ route shipper → nhà hàng → nhà khách | ✅ |
| Backend | WebSocket broadcast location_update | ✅ |

### UC-4.4: Shipper giao hàng (Shipper App) 🔴
| Platform | Task | Status |
|----------|------|--------|
| Shipper App | Vẽ route đến pickup/delivery trên bản đồ | ✅ |
| Shipper App | Cập nhật status (ASSIGNED→PICKED_UP→DELIVERING→DELIVERED) | ✅ |
| Shipper App | BottomSheet hiện thông tin đơn hàng | ✅ |
| Shipper App | Gửi vị trí GPS realtime qua WebSocket | ✅ |
| Backend | Delivery status transition + validation | ✅ |

### UC-4.5: Khôi phục đơn khi shipper tắt/mở lại app 🟡
| Platform | Task | Status |
|----------|------|--------|
| Shipper App | fetchActiveDelivery khi mount + foreground | ✅ |
| Shipper App | Tự vẽ route + BottomSheet khi restore | ✅ |
| Backend | GET /api/deliveries/shipper/{id}/active | ✅ |

### UC-4.6: Auto-cancel khi shipper timeout (Saga impl) 🟡
| Platform | Task | Status |
|----------|------|--------|
| Backend | @Scheduled check timeout trong Saga (SagaTimeoutScheduler) | ✅ |
| Backend | Timeout tiers (2p tìm shipper, 3p shipper accept, 5p tìm shipper mới) | ✅ |
| Backend | Phát hiện shipper offline (Redis + WebSocket) | 🔧 |
| Backend | Re-assign cho shipper khác | ✅ |
| Customer App | Thông báo khi đơn bị auto-cancel (qua notification/websocket) | ✅ |
> 📌 Design doc: `saga-orchestrator-service/SAGA_DESIGN.md`

---

## 5. THÔNG BÁO

### UC-5.1: Push Notification 🟢
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Nhận thông báo đơn hàng (FCM) | ✅ |
| Shipper App | Nhận thông báo đơn mới (FCM + WebSocket) | ✅ |
| Backend | Notification Service (Firebase + Kafka listener) | ✅ |

### UC-5.2: In-app Notification 🟢
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Notification screen + badge count | ✅ |
| Shipper App | Notification screen | ✅ |
| Backend | Notification CRUD + STOMP WebSocket | ✅ |

---

## 6. TÀI CHÍNH & THANH TOÁN SHIPPER

### UC-6.1: Xem số dư & thu nhập 🟡
| Platform | Task | Status |
|----------|------|--------|
| Shipper App | EarningsScreen hiện balance, hôm nay, tuần | ✅ |
| Backend | Settlement Service (balance, transactions) | ✅ |
| Backend | Auto cộng tiền khi hoàn thành delivery (85%) | ✅ |

### UC-6.2: Rút tiền 🟢
| Platform | Task | Status |
|----------|------|--------|
| Shipper App | Yêu cầu rút tiền | ✅ |
| Admin Web | Duyệt yêu cầu rút tiền | ✅ |
| Backend | Withdrawal flow + admin approval | ✅ |

### UC-6.3: Lịch sử giao dịch 🟢
| Platform | Task | Status |
|----------|------|--------|
| Shipper App | Danh sách giao dịch chi tiết | ✅ |
| Backend | Transaction history API | ✅ |

### UC-6.4: Hệ thống ví ký quỹ (Deposit Wallet) 🟡
| Platform | Task | Status |
|----------|------|--------|
| Shipper App | Xem số dư ký quỹ, nạp tiền | ✅ |
| Backend | Settlement Service: quản lý ví ký quỹ, check COD eligibility | ✅ |

---

## 7. LIVESTREAM

### UC-7.1: Tạo & xem livestream 🟢
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Xem danh sách livestream đang phát | ✅ |
| Customer App | Xem livestream (viewer, Agora RTC) | ✅ |
| Admin Web | Tạo/quản lý livestream (host, Agora) | ✅ |
| Backend | Livestream Service (CRUD + Agora token) | ✅ |

### UC-7.2: Mua hàng qua livestream 🟢
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Xem sản phẩm trong livestream + đặt hàng | ✅ |
| Admin Web | Gắn sản phẩm vào livestream | ✅ |
| Backend | Livestream Product API | ✅ |

---

## 8. ADMIN & QUẢN LÝ

### UC-8.1: Dashboard tổng quan ⚪
| Platform | Task | Status |
|----------|------|--------|
| Admin Web | Thống kê đơn hàng, doanh thu, shipper | ✅ |
| Backend | Aggregate APIs | ✅ |

### UC-8.2: Quản lý đơn hàng 🟡
| Platform | Task | Status |
|----------|------|--------|
| Admin Web | Xem/lọc/tìm đơn hàng | ✅ |
| Admin Web | Manual assign shipper | ✅ |
| Admin Web | Cancel/refund đơn | ✅ |

### UC-8.3: Quản lý shipper 🟢
| Platform | Task | Status |
|----------|------|--------|
| Admin Web | CRUD shipper, duyệt đăng ký | ✅ |
| Admin Web | Xem vị trí shipper realtime | 🔧 |
| Admin Web | Khoá/mở khoá shipper | ✅ |

### UC-8.4: Chat support 🟢
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Chat với CSKH | ❌ |
| Admin Web | Module chat (Firebase integration) | ✅ |
| Backend | Chat service (Firebase bypass Java) | ✅ |

---

## 9. SAGA ORCHESTRATOR (Backend) 🟡

### UC-9.1: Saga pattern cho luồng đặt hàng
| Task | Status |
|------|--------|
| Design Architecture (SAGA_DESIGN.md) | ✅ |
| PostgreSQL + JPA Entities | ✅ |
| Kafka Listeners cho existing topics | ✅ |
| Order Creation Saga Logic | ✅ |
| Compensation (Rollback) Logic | ✅ |
> 📌 Design doc: `saga-orchestrator-service/SAGA_DESIGN.md`

---

## 10. FLASH SALE ⚡ (MỚI)

### UC-10.1: Quản lý Flash Sale Campaign (Admin) 🟡
| Platform | Task | Status |
|----------|------|--------|
| Admin Web | Tạo campaign (tên, thời gian, loại recurring) | ❌ |
| Admin Web | Xem/lọc danh sách campaign | ❌ |
| Admin Web | Bật/tắt/duyệt campaign (`PUT /status`) | ❌ |
| Admin Web | Duyệt item đăng ký từ merchant (`PUT /items/{id}/approve`) | ❌ |
| Backend | AdminFlashSaleController (CRUD campaign, approve item) | ✅ |

### UC-10.2: Đăng ký Flash Sale (Merchant/Restaurant) 🟡
| Platform | Task | Status |
|----------|------|--------|
| Restaurant Web | Đăng ký món ăn vào campaign (giá sale, số lượng) | ❌ |
| Restaurant Web | Xem trạng thái item đã đăng ký (PENDING/APPROVED) | ❌ |
| Backend | MerchantFlashSaleController (`POST /items`) | ✅ |

### UC-10.3: Xem & mua Flash Sale (Customer) 🔴
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Hiển thị banner Flash Sale trên Home (countdown) | ✅ |
| Customer App | Danh sách sản phẩm flash sale (giá gốc, giá sale, progress bar) | ✅ |
| Customer App | Thêm sản phẩm flash sale vào giỏ hàng | 🔧 |
| Landing Web | Hiển thị Flash Sale section (API real-time) | ✅ |
| Backend | PublicFlashSaleController (campaigns, items) | ✅ |

### UC-10.4: Reserve & Release kho Flash Sale (Internal) 🔴
| Platform | Task | Status |
|----------|------|--------|
| Backend | Redis-based stock reservation (`InternalFlashSaleController`) | ✅ |
| Backend | FlashSaleStockService (reserve/release atomically) | ✅ |
| Backend | Kafka listener: release stock on `order.cancelled` / `payment.failed` | ✅ |
| Backend | CronService: auto-activate/deactivate campaign theo schedule | ✅ |
| Backend | Order Service → gọi internal reserve khi checkout | 🔧 |

---

## 11. PROMOTION & VOUCHER 🎟️ (MỚI)

### UC-11.1: Tạo & quản lý Voucher (Admin/Platform) 🟡
| Platform | Task | Status |
|----------|------|--------|
| Admin Web | Tạo voucher platform-wide (code, giá trị, điều kiện) | ❌ |
| Admin Web | Xem danh sách tất cả voucher | ❌ |
| Admin Web | Xoá voucher | ❌ |
| Backend | PromotionController — `POST /platform`, `GET /admin`, `DELETE /{id}` | ✅ |

### UC-11.2: Tạo Voucher (Merchant/Restaurant) 🟢
| Platform | Task | Status |
|----------|------|--------|
| Restaurant Web | Tạo voucher cho shop mình (scope = SHOP) | ❌ |
| Restaurant Web | Xem danh sách voucher shop | ❌ |
| Backend | PromotionController — `POST /merchant`, `GET /merchant` | ✅ |

### UC-11.3: Thu thập & sử dụng Voucher (Customer) 🔴
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Nhập mã voucher để lưu vào kho | 🔧 |
| Customer App | Xem danh sách voucher đã thu thập | ❌ |
| Customer App | Áp dụng voucher khi checkout (auto calculate giảm giá) | ❌ |
| Backend | `POST /collect/{code}` — lưu UserVoucher | ✅ |
| Backend | `POST /calculate` — tính giảm giá dựa trên cart context | ✅ |
| Backend | `POST /reserve` — lock voucher khi đặt hàng | ✅ |
| Backend | SagaPromotionListener — release voucher khi order fail | ✅ |

---

## 12. TÌM KIẾM NÂNG CAO 🔍 (MỚI)

### UC-12.1: Tìm kiếm Full-text (Elasticsearch) 🟡
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Tìm kiếm nhà hàng full-text (tên, mô tả, cuisine) | ❌ |
| Customer App | Tìm kiếm món ăn (tên, giá, category) | ❌ |
| Admin Web | Tìm kiếm shipper (tên, phone, biển số) | ❌ |
| Backend | SearchController — `/restaurants`, `/dishes`, `/shippers` (paginated) | ✅ |
| Backend | Elasticsearch indices: RestaurantDocument, DishDocument, ShipperDocument | ✅ |

### UC-12.2: Đồng bộ dữ liệu tìm kiếm (Real-time sync) 🟡
| Platform | Task | Status |
|----------|------|--------|
| Backend | Kafka consumer `entity-sync` → upsert/delete Elasticsearch documents | ✅ |
| Backend | Redis cache layer cho search results (SearchCacheService) | ✅ |
| Backend | Restaurant/Dish/Shipper service publish sync events khi CRUD | 🔧 |

---

## 13. ANALYTICS & DASHBOARD 📊 (MỚI)

### UC-13.1: Dashboard Admin (Thống kê toàn platform) 🟡
| Platform | Task | Status |
|----------|------|--------|
| Admin Web | Dashboard tổng quan: đơn hàng, doanh thu, users theo period | 🔧 |
| Backend | `GET /analytics/dashboard/admin?period=month&year=2026` | ✅ |
| Backend | DailyOrderStats + DailyRevenueStats (aggregated tables) | ✅ |
| Backend | Kafka listeners: OrderEventListener, PaymentEventListener | ✅ |

### UC-13.2: Dashboard Restaurant (Thống kê cho nhà hàng) 🟢
| Platform | Task | Status |
|----------|------|--------|
| Restaurant Web | Dashboard riêng: đơn hàng, doanh thu nhà hàng | ❌ |
| Backend | `GET /analytics/dashboard/restaurant/{id}` | ✅ |
| Backend | `GET /analytics/dashboard/my-restaurant` (self-service) | ✅ |

### UC-13.3: Reconciliation & Data Integrity 🟢
| Platform | Task | Status |
|----------|------|--------|
| Backend | StatsReconciliationJob — tự động tính lại thống kê hàng ngày | ✅ |
| Backend | Manual reconcile API (`POST /analytics/reconcile?date=...`) | ✅ |

---

## 14. NÂNG CAO

### UC-14.1: Đánh giá shipper ⚪
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Đánh giá sao sau khi nhận hàng | ✅ |
| Shipper App | Xem rating của mình | ✅ |
| Backend | Rating API | ✅ |

### UC-14.2: Lịch sử đơn hàng chi tiết ⚪
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Xem lịch sử đặt hàng + đặt lại | 🔧 |
| Shipper App | OrderHistoryScreen | ✅ |

### UC-14.3: Multi-language / Dark mode ⚪
| Platform | Task | Status |
|----------|------|--------|
| Customer App | Hỗ trợ EN/VI | ✅ |
| Shipper App | Hỗ trợ EN/VI | ❌ |

---

## 📊 TỔNG KẾT THEO ĐỘ ƯU TIÊN

### 🔴 Critical (Cần hoàn thiện ngay)
1. **UC-10.3** — Customer App: Thêm flash sale vào giỏ hàng (kết nối reserve API)
2. **UC-10.4** — Order Service gọi internal reserve khi checkout flash sale
3. **UC-11.3** — Customer App: Áp dụng voucher khi checkout (calculate + reserve)

### 🟡 High (Cần làm sớm)
4. **UC-10.1** — Admin Web: UI quản lý Flash Sale campaign
5. **UC-10.2** — Restaurant Web: UI đăng ký item vào flash sale
6. **UC-11.1** — Admin Web: UI tạo/quản lý voucher platform
7. **UC-12.1** — Customer App: Tích hợp Elasticsearch search (thay thế search cũ)
8. **UC-12.2** — Backend: Các service publish `entity-sync` event khi CRUD
9. **UC-13.1** — Admin Web: Tích hợp Analytics Dashboard API thực

### 🟢 Medium
10. **UC-11.2** — Restaurant Web: UI tạo voucher cho shop
11. **UC-13.2** — Restaurant Web: Dashboard thống kê riêng
12. **UC-8.4** — Customer App: Chat với CSKH (chưa triển khai)

### ⚪ Low (Nice-to-have)
13. **UC-1.1** — Shipper App: Social login
14. **UC-14.3** — Shipper App: Multi-language

### 💎 Recent Achievements
- **Flash Sale System**: Backend hoàn chỉnh — Redis stock, Kafka compensation, Cron auto-activate. Customer App đã tích hợp banner + countdown.
- **Promotion Engine**: Voucher platform/merchant, calculate discount, Saga compensation listener sẵn sàng.
- **Search Service**: Elasticsearch 3 loại document (Restaurant/Dish/Shipper), Kafka sync consumer, Redis cache layer.
- **Analytics Service**: Kafka event processing, daily stats aggregation, reconciliation job, admin/restaurant dashboards.
- **Online Payment**: VNPay integration với auto-confirm flow.
- **Deposit System**: Ví ký quỹ shipper + COD eligibility check.
- **Saga Stability**: Luồng đặt hàng + compensation hoạt động ổn định.

---

## 🚨 REVIEW: CÁC CASE QUAN TRỌNG CHƯA LÀM

### ❗ Critical Gaps (ảnh hưởng trực tiếp đến trải nghiệm người dùng)

| # | Use Case | Vấn đề | Ưu tiên |
|---|----------|--------|---------|
| 1 | **Flash Sale → Order Integration** | Order Service chưa gọi `POST /flashsales/internal/reserve` khi checkout. Không có reserve = bán vượt kho. | 🔴 |
| 2 | **Voucher Checkout Flow** | Customer App chưa gọi `POST /calculate` trước khi thanh toán và `POST /reserve` khi đặt hàng. Voucher chỉ mới collect được. | 🔴 |
| 3 | **Search Integration trên App** | Customer App vẫn dùng search cũ từ Restaurant Service. Chưa kết nối Elasticsearch API. | 🟡 |
| 4 | **Entity Sync Events** | Restaurant/Dish/Shipper service chưa publish Kafka event `entity-sync` khi CRUD → Elasticsearch data sẽ stale. | 🟡 |
| 5 | **Admin Flash Sale UI** | Backend đã sẵn sàng nhưng Admin Web chưa có trang quản lý campaign & duyệt item. | 🟡 |
| 6 | **Restaurant Dashboard UI** | Analytics API sẵn sàng nhưng Restaurant Web chưa hiển thị thống kê. | 🟢 |
| 7 | **Customer Chat** | UC-8.4: Backend + Admin chat sẵn sàng nhưng Customer App chưa có UI chat. | 🟢 |

---

## 📱 ROADMAP ƯU TIÊN APP TRƯỚC

### Sprint 1–4: ĐÃ HOÀN THÀNH ✅
- [x] UC-4.3: Customer tracking realtime
- [x] UC-3.3: Huỷ đơn customer/shipper
- [x] UC-4.6: Backend auto-cancel timeout
- [x] UC-5.1: Push notification ổn định
- [x] UC-6.2: Admin Web duyệt rút tiền
- [x] UC-3.4: Thanh toán online (VNPay)
- [x] UC-8.2: Admin quản lý đơn hàng
- [x] UC-7.2: Mua hàng qua livestream
- [x] UC-1.2: Profile shipper đầy đủ
- [x] UC-10.2 (cũ): Coupon UI input done

### Sprint 5: Flash Sale & Promotion E2E 🔴
- [ ] Order Service integrate flash sale reserve API
- [ ] Customer App: checkout flow với voucher (calculate → reserve)
- [ ] Customer App: xem voucher đã collect
- [ ] Admin Web: Flash Sale campaign management UI

### Sprint 6: Search & Analytics 🟡
- [ ] Customer App: kết nối Elasticsearch search API
- [ ] Backend: publish entity-sync event từ Restaurant/Shipper service
- [ ] Admin Web: tích hợp Analytics dashboard thực
- [ ] Restaurant Web: dashboard thống kê

### Sprint 7: Polish & Extras 🟢
- [ ] Customer App: Chat CSKH
- [ ] Restaurant Web: tạo voucher cho shop
- [ ] Restaurant Web: đăng ký flash sale item
- [ ] Shipper App: multi-language
