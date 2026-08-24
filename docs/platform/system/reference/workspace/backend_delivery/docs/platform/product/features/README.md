# 📚 Tài liệu Chức năng (Feature Docs)

Mỗi file mô tả **cách hoạt động thật** và **mọi case** của một chức năng, verify
từ code. Dùng làm nguồn chân lý để review nghiệp vụ và để code theo sau này.

Quy trình: mình viết nháp verify từ code → bạn review & sửa ý định nghiệp vụ →
tài liệu thành chuẩn → code/sửa theo tài liệu.

Chuẩn viết: xem [`_TEMPLATE.md`](./_TEMPLATE.md).
Trạng thái: 🟢 verified từ code · 🟡 nháp cần review · 🔴 chưa viết.

## Độ phủ tài liệu

| # | Chức năng | Service | Trạng thái |
|---|---|---|---|
| 1 | [Vòng đời đơn hàng](./order-lifecycle.md) | order | 🟢 (chờ bạn review) |
| 2 | [Delivery & Matching shipper](./delivery-matching.md) | delivery, match, tracking | 🟢 (chờ bạn review) |
| 2a | [Serviceability polygon & ETA window](./serviceability-and-eta.md) | restaurant, order, routing | 🟢 (capability mặc định tắt) |
| 2b | [Menu inventory reservation](./menu-inventory.md) | restaurant, order | 🟢 (capability mặc định tắt) |
| 3 | Tracking realtime (raw WebSocket) | tracking | 🔴 |
| 4 | Auth & session (login, social, refresh, block) | auth, user | 🔴 |
| 5 | Nhà hàng & Menu (CRUD, validate, operating hours) | restaurant | 🔴 |
| 6 | Rating & Review (nhà hàng + shipper) | restaurant, shipper | 🔴 |
| 7 | [Settlement — balance, COD, withdrawal](./settlement.md) | settlement | 🟢 (chờ bạn review) |
| 8 | Thanh toán online (VNPay) | settlement | 🔴 |
| 9 | [Voucher & Flash-sale checkout](./voucher-flashsale-checkout.md) | promotion, flashsale, order | 🟢 (rollout mặc định tắt) |
| 10 | [Promotion / Voucher workflow](../../../workflows/promotion_voucher_flow.md) | promotion | 🟢 (rollout mặc định tắt) |
| 11 | Notification (FCM + WebSocket) | notification | 🔴 |
| 12 | Livestream (Agora, pin product) | livestream | 🔴 |
| 13 | Search (Elasticsearch, entity-sync) | search | 🔴 |
| 14 | Analytics / Dashboard | analytics, order | 🔴 |
| 15 | Shipper — profile, online, availability | shipper | 🔴 |

> Thứ tự đề xuất viết tiếp: #2 (delivery+matching) và #7 (settlement) — là 2 luồng
> phức tạp & nhiều case nhất, sau đó tới các chức năng CRUD đơn giản hơn.
