# ✅ Rà soát: Việc cần làm để hệ thống chạy được cho Test

> Cập nhật: 2026-07-22 · Mục tiêu: đưa hệ thống lên trạng thái **test được luồng chính end-to-end**.

## Tóm tắt

Tin tốt: hạ tầng + 16 service đã có trong `docker-compose.yml` (Postgres 16, Redis 7,
Kafka 7.4 KRaft, Elasticsearch 7.17), build từ `Dockerfile` chung. **Luồng chính
(đặt hàng → giao → đối soát) về code là chạy được.** Cần xử lý vài điểm cấu hình +
2 lỗ hổng luồng trước khi test trơn tru.

## ✅ Đã xử lý (2026-07-22)

- **`flashsale-service` đã được thêm vào docker-compose** (chuyển sang Postgres, host port 8098). Xem [learning/001](./learning/001-wire-flashsale-into-compose.md).
- **order-service đã có URL gọi restaurant/flashsale/promotion** (trước đó thiếu → tạo đơn fail trong container).
- **Có `backend_delivery/scripts/seed.sh`** tự dựng khách + nhà hàng + menu + shipper online có vị trí. Xem [learning/002](./learning/002-seed-script.md).

## 🔴 Còn cần lưu ý khi test

| # | Vấn đề | Vì sao | Việc cần làm |
|---|---|---|---|
| 1 | **Shipper phải ONLINE + có vị trí trong Redis GEO** | match tìm qua GEORADIUS; không có location → đơn kẹt `FINDING_SHIPPER` → `SHIPPER_NOT_FOUND` | Chạy `seed.sh` (đã lo bước này), hoặc tự bật online + `POST /api/tracking/shipper-locations/update` |
| 2 | **Điểm lệch role** (USER vs CUSTOMER giữa auth và service) | Có thể gây 403 ở vài endpoint | Chốt chuỗi role chuẩn — xem câu hỏi mở trong [order-lifecycle §11](./product/features/order-lifecycle.md) |

## 🟠 Nên fix để test đỡ vướng (không chặn cứng)

| # | Vấn đề | Ảnh hưởng khi test |
|---|---|---|
| 3 | Delivery offer timeout cần runtime proof | Saga/Delivery đã có exact-generation expiry + rematch; còn phải chứng minh Redis/Kafka/PostgreSQL live ở Gate B8 |
| 4 | Shipper cancel-after-accept cần E2E | Backend command/rematch đã có focused proof; client vẫn gọi nhầm Order cancel tới Phase 11 |
| 5 | Raw location socket client còn lệch Gateway | Shipper app đang hard-code port cũ; sửa ở client phase sau khi backend contract freeze |

## Cách chạy để test

```bash
cd backend_delivery
bash scripts/gen-keys.sh             # ⚠️ BẮT BUỘC trước lần build đầu — sinh khóa JWT
                                     #    (khóa không còn trong git; xem SECURITY.md)
docker compose up -d --build         # build + chạy toàn bộ
docker compose ps                    # kiểm tra service healthy
docker compose logs -f api-gateway   # xem log gateway :8079
```
> Lưu ý: build 17 service lần đầu khá lâu. Có thể chạy từng phần: infra trước
> (`postgres redis kafka elasticsearch`), rồi tới service.

Sau khi cụm healthy, có 2 lựa chọn (cần `jq`):

```bash
bash scripts/seed.sh            # chỉ dựng dữ liệu (khách + NH + menu + shipper online)
bash scripts/verify-mvp-cod-flow.sh # luồng canonical fail-fast
```
`test-order-flow.sh` chỉ còn là compatibility wrapper sang harness canonical.
Harness quan sát durable `MATCH_FOUND` notification, recover self-offer, kiểm raw
WebSocket participant authorization/location, hoàn tất delivery, kiểm đúng bốn
ledger rows và replay exact completion mà không đổi cardinality. Không polling
Delivery bằng customer token để giả lập shipper offer và không gọi settlement
self-service API đang hidden.

> **Lưu ý lặp lại**: nếu một lần chạy fail giữa chừng, shipper có thể còn "đơn đang
> xử lý" (guard 1-đơn-1-lúc) làm lần sau accept lỗi. Khi đó hoàn tất đơn đang treo
> hoặc dùng shipper email khác (đổi biến trong script).

## Smoke test luồng chính (checklist)

1. [ ] `POST /api/auth/register` → `POST /api/users/registrations` → verify email
   → `login` cho khách/chủ nhà hàng; SHIPPER dùng operator provisioning.
2. [ ] Chủ nhà hàng tạo nhà hàng + vài menu item (`POST /api/restaurants`, `/menu-items`).
3. [ ] **Shipper bật online + gửi location** (điều kiện #2).
4. [ ] Khách `POST /api/orders` (đơn **thường**, chưa flash sale) → kỳ vọng đơn tạo, delivery `FINDING_SHIPPER` (nhưng **chưa** tìm shipper — đang chờ nhà hàng).
5. [ ] ⚠️ **MỚI (bắt buộc):** Nhà hàng confirm đơn: `POST /api/restaurants/orders/{orderId}/confirm` body `{"restaurantId": <id>, "estimatedPrepTime": 15}` → lúc này Saga mới bắt đầu tìm shipper. (Không confirm = đơn đứng, không match.) Từ chối: `/reject` body `{"restaurantId": <id>, "reason": "..."}` → đơn bị huỷ, dừng tìm shipper.
6. [ ] Shipper thấy durable Notification inbox rồi gọi
   `GET /api/deliveries/offers/current` trước `POST /accept`.
7. [ ] Raw WebSocket từ Gateway từ chối outsider, cho participant subscribe và
   phát location với shipper identity derive từ JWT.
8. [ ] Shipper cập nhật `PICKED_UP → DELIVERING → DELIVERED`.
9. [ ] Kiểm tra trực tiếp observable settlement ledger có đúng bốn entries; exact
   `delivery.completed` replay không thay đổi cardinality.

## Đề xuất thứ tự làm (để test được sớm nhất)

1. **Thêm flashsale-service vào docker-compose** (#1) — hoặc xác nhận test bỏ qua flash sale.
2. **Viết script/seed nhanh** để có sẵn: 1 nhà hàng + menu + 1 shipper online có location (#2) — giúp lặp lại test nhanh.
3. Chạy smoke test luồng chính, ghi lại chỗ hỏng thực tế.
4. Sau khi luồng chính pass → làm #3, #4 (WaitingService, shipper cancel) để test case biên.

> Chi tiết kỹ thuật từng việc: [ROADMAP §1](../../ROADMAP_MVP_TO_PRODUCTION.md).
> Cách hoạt động từng chức năng: [docs/product/features/](./product/features).
</content>
