# Execution Plan: Thu thập catalog public Hà Nội cho local mock data

Date: 2026-08-21

## Status

Completed

## Outcome

Có snapshot JSON catalog nhà hàng Hà Nội đủ rộng để demo local/UI và seed qua
API hiện tại, được gom từ listing public của GrabFood/ShopeeFood, có source URL,
ngày quan sát, platform, độ tin cậy địa chỉ/toạ độ và menu mock tách biệt với dữ
liệu nguồn. Collector có thể chạy lại với cache/rate limit và không gọi endpoint
private hay vượt anti-bot.

## Context

- Quy trình: [docs/WORKFLOW.md](../../WORKFLOW.md), [AGENTS.md](../../../../../AGENTS.md).
- Data contract hiện tại: [data/README.md](../../../../../data/README.md) và
  `backend_delivery/scripts/seed-realistic-catalog.sh`.
- Nguồn tham chiếu: listing public chính thức của
  `shopeefood.vn` và `food.grab.com`; snapshot không phải production truth.

## Scope

In scope:

- Public category/dish/area listing pages cho Hà Nội.
- Cache raw response có giới hạn, parser HTML/JSON chịu thay đổi nhẹ, dedupe
  merchant theo platform/source id hoặc URL/name chuẩn hóa.
- Catalog normalized gồm restaurant, source facts, coverage theo quận/cuisine
  và menu mock 4 món/nhà hàng để dùng với write contract hiện tại.
- Dry-run/validation; không seed API thật nếu không có token do người dùng cấp.

Out of scope:

- Bypass AWS WAF/anti-bot, private/client-authenticated endpoint, review,
  phone cá nhân, account, CCCD/giấy tờ shipper hoặc dữ liệu thanh toán.
- Khẳng định địa chỉ/toạ độ/giá/menu là chính xác nếu nguồn không hiển thị;
  các field đó phải ghi `sourceBacked`, `normalized` hoặc `synthetic_mock`.
- Xoá hoặc reset thay đổi chưa commit trong `delivery_web`.

## Approach

1. Xác định các page public theo nhiều quận/cuisine của Hà Nội và lưu URL
   snapshot trong `data/sources/hanoi-*`.
2. Collector fetch tuần tự với User-Agent rõ ràng, timeout, cache và delay;
   parser chỉ lấy listing facts quan sát được.
3. Chuẩn hóa/dedupe, gán quận từ địa chỉ/URL/tên khi có bằng chứng; ghi
   `addressConfidence`/`coordinateConfidence` thay vì giả vờ geocode chính xác.
4. Sinh menu synthetic có `provenance: synthetic_mock`, tạo catalog và report
   coverage. Seed script dùng catalog path truyền vào, mặc định dry-run.
5. Chạy invariant checks: JSON parse, unique key/source id, đủ quận/city,
   menu join, dry-run và diff hygiene.

## Risks And Recovery

- Listing thay đổi hoặc bị chặn: giữ raw cache, giảm tốc độ, bỏ qua page lỗi và
  report rõ; không retry vô hạn và không bypass challenge.
- Tên/địa chỉ trùng nhiều chi nhánh: giữ bản ghi nếu source id khác; chỉ merge
  khi cùng source identity hoặc canonical URL chắc chắn.
- Dữ liệu thiếu address/menu/coordinate: vẫn giữ merchant với confidence thấp,
  menu synthetic; không dùng tọa độ fixture cho dispatch production.
- Nếu snapshot/collector sai: xoá các file snapshot mới và chạy lại từ cache;
  API seed không chạy mặc định nên không có rollback database cần thiết.

## Progress

- [x] Đọc workflow, data contract và trạng thái worktree.
- [x] Thêm collector public + cache/rate limit/dedupe.
- [x] Thu thập snapshot Grab/ShopeeFood Hà Nội.
- [x] Sinh catalog/menu/report và cập nhật tài liệu sử dụng.
- [x] Mở rộng fixture path/seed dry-run nếu cần.
- [x] Chạy validation và ghi kết quả/giới hạn.

## Decisions

- 2026-08-21: Bắt đầu bằng listing public, không dùng API private hoặc bypass
  anti-bot; dữ liệu menu không hiển thị sẽ synthetic và gắn provenance rõ.
- 2026-08-21: Tọa độ chỉ là approximate/area centroid khi không có geocode
  authoritative; không dùng snapshot làm dữ liệu vận hành thật.
- 2026-08-21: Giữ ShopeeFood ở dạng public snapshot adapter vì fetch trực tiếp
  trả app shell; không gọi client/private endpoint. Bổ sung branch public GrabFood
  từ chain page để tăng coverage suburb.
- 2026-08-21: Snapshot đạt 485 restaurant/1.940 menu item; 442 record GrabFood
  và 43 record ShopeeFood sau dedupe. 15/16 khu vực filter mục tiêu có record;
  Thường Tín được report là unobserved thay vì sinh record giả.

## Validation

- Focused proof: `node --check` collector/validator; validator PASS với
  `data/catalog/hanoi-catalog.json` và catalog TP.HCM; JSON parse toàn bộ
  `data/` (43 files); unique restaurant/menu join và đúng 4 menu/restaurant.
- Integration or end-to-end proof: collector đã fetch 25 GrabFood public pages
  tuần tự, cache-only replay PASS; `CATALOG_FILE=... bash
  backend_delivery/scripts/seed-realistic-catalog.sh` dry-run PASS; runner
  `data/scenarios/hanoi-catalog.json` dry-run PASS. Không seed API thật vì chưa
  có runtime/token.
- Repository-required checks: `bash -n` seed script PASS; không reset/đụng
  các thay đổi đang có trong `delivery_web`; cache raw HTML nằm trong ignored
  `data/sources/hanoi-grab/.cache/`.

## Result

Đã hoàn tất snapshot và workflow local. Các field menu/description/phone/image/
tọa độ và giờ Grab không hiển thị được đều được đánh dấu synthetic; dữ liệu
không phải production truth. Refresh bằng collector để cập nhật listing thay đổi.
