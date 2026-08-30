# Thiết kế cập nhật README cho polyrepo Delivery

> Ngày: 2026-08-26
> Trạng thái: Đã được duyệt để triển khai

## Outcome

Khi mở README ở bất kỳ repo nào, một contributor mới có thể trả lời được:

- Repo này phục vụ actor hoặc phần nào của nền tảng?
- Nó giao tiếp với các thành phần khác qua boundary nào?
- Những chức năng nào đang hoạt động trong MVP và chức năng nào đang ẩn hoặc
  chưa có contract?
- Cấu trúc code nằm ở đâu, chạy local thế nào và kiểm tra bằng lệnh nào?
- Tài liệu canonical nào cần đọc tiếp khi muốn hiểu sâu hơn?

Phạm vi gồm README gốc của bốn Git repo: `backend_delivery`, `delivery_app`,
`delivery_web` và `shipper_app2`. Thư mục `delivery_simulator_web` không thuộc
phạm vi vì không phải một Git repo trong workspace map hiện tại.

## Nguyên tắc nguồn sự thật

- README là lớp định hướng, không trở thành bản sao thứ hai của HTTP API,
  Kafka event hay product policy.
- Kiến trúc liên repo và capability boundary lấy từ
  `backend_delivery/docs/platform/product/overview.md`,
  `backend_delivery/docs/platform/ARCHITECTURE.md` và các tài liệu system
  tương ứng.
- Chức năng và command của từng ứng dụng phải khớp với code, package manifest,
  route hiện tại và tài liệu test của chính repo đó.
- Các capability có code nhưng đang hidden/default-off phải được ghi rõ là
  không thuộc public MVP; không mô tả chúng như tính năng đã phát hành.
- Không đưa secret, token thật, dữ liệu người dùng hoặc service port nội bộ vào
  README.

## Cấu trúc chung của README

Mỗi README dùng cùng thứ tự đọc, với độ chi tiết phù hợp vai trò repo:

1. Tên dự án và một câu mô tả chức năng.
2. Vai trò trong polyrepo và actor/surface phục vụ.
3. Sơ đồ boundary hoặc luồng tích hợp ngắn.
4. Kiến trúc code và bản đồ thư mục quan trọng.
5. Chức năng MVP đang hoạt động.
6. Capability hidden, default-off hoặc chưa được hỗ trợ.
7. Công nghệ và tích hợp chính.
8. Cài đặt/chạy local, cấu hình môi trường và lưu ý secret.
9. Validation commands.
10. Liên kết đến tài liệu chi tiết/canonical.

Nội dung viết bằng tiếng Việt dễ đọc; tên service, route, role, package và shell
command giữ nguyên dạng kỹ thuật.

## Nội dung theo repo

### `backend_delivery`

Tạo README mới với:

- Vai trò backend của nền tảng đặt và giao đồ ăn.
- Sơ đồ `clients → API Gateway → domain services → Kafka/Redis/PostgreSQL/
  Elasticsearch`.
- Service catalog theo nhóm: control plane/identity, ordering/fulfilment,
  realtime/notification, search và capability mở rộng.
- Luồng canonical: checkout → restaurant decision → Saga/Match → delivery
  offer → shipper lifecycle → tracking → COD settlement.
- Boundary auth RS256/JWKS, Gateway-only cho client, DB per service,
  transactional outbox, retry/DLT và idempotency.
- Quick start Docker/Maven, core profile và optional capability profile.
- Link đến overview, architecture, API inventory, runbook, roadmap và testing
  readiness.

### `delivery_app`

Cập nhật README để mô tả:

- App khách hàng Flutter.
- Catalog/restaurant, cart, checkout COD, order history/detail, address,
  tracking và notification wake-up.
- `lib/core` cho network, routing, storage, platform integration và shared UI;
  `lib/features` cho các use case theo feature.
- Riverpod dependency boundary, Dio/Retrofit, Hive/SharedPreferences, Mapbox
  và Firebase/FCM.
- Gateway-only với Bearer/refresh; không gọi service port hoặc dùng
  Internal-Token.
- Voucher/flash-sale, support chat, livestream và các capability chưa có proof
  phải được đánh dấu hidden/default-off theo platform boundary.
- Setup Mapbox/env và các lệnh Flutter analyze/test/coverage.

### `delivery_web`

Cập nhật README để mô tả ba surface đang có:

- Storefront customer: catalog, cart, address, checkout preview/COD và orders.
- Restaurant owner: dashboard entry, orders, menu, profile, reviews, voucher và
  catalog import.
- Admin: dashboard, orders, shippers, rating moderation, coupons, flash-sale và
  catalog import.
- Public read-only system handbook tại `/system-overview`.
- React 19/Vite/TypeScript, Axios session layer, React Router, Tailwind và
  `AppDependenciesProvider`.
- Role/ownership do backend quyết định; browser chỉ gọi Gateway origin.
- Các route payment/settlement/refund/realtime map/chat/livestream và
  voucher/flash-sale checkout activation là exclusions.
- Dev, verify standalone và polyrepo handbook checks.

### `shipper_app2`

Cập nhật README để mô tả:

- App React Native cho shipper.
- Password/Google login, current offer recovery, accept/reject, cancel
  assignment, delivery lifecycle, map/GPS tracking, history/detail,
  notification, profile, rating và documents.
- `Route → ViewModel → View`, cùng bản đồ `src/app`, `src/core`, `src/shared` và
  `src/features`.
- Redux Toolkit/store factory, repository ports, native adapters, Mapbox,
  FCM wake-only và raw Gateway WebSocket.
- Gateway/Bearer/session refresh contract và các capability bị ẩn như
  self-registration, payout/withdrawal, fake GPS hoặc STOMP.
- Setup Android/iOS, env và các lệnh typecheck/lint/architecture/Jest/coverage.

## Liên kết tài liệu

README của client sẽ không phụ thuộc vào đường dẫn `../docs/...` không tồn tại
khi clone riêng từng repo. Các tài liệu client-local dùng relative link trong
chính repo; tài liệu cross-repo dùng link GitHub trực tiếp đến nhánh `main` của
`delivery_backend`, đồng thời ghi rõ đây là canonical source.

## Validation và phát hành

- Sau mỗi README, chạy kiểm tra newline/trailing whitespace bằng
  `git diff --check` trong repo tương ứng.
- Kiểm tra các link local, lệnh được nêu và không có placeholder/secret bằng
  review diff và tìm kiếm tĩnh.
- Chạy validation hiện có phù hợp với từng repo: Flutter analyze/test,
  web verify/check và shipper typecheck/lint/architecture/test; backend chạy
  các kiểm tra tài liệu/build tối thiểu thay vì suy diễn README proof thành
  runtime proof.
- Commit riêng cho từng repo với message nêu rõ README reconciliation.
- Trước push, kiểm tra branch/remotes, working tree và remote tracking; push
  `main` của từng repo sau khi tất cả kiểm tra tương ứng hoàn tất.

## Ngoài phạm vi

- Không sửa code, API, Kafka event, route, product behavior hoặc capability
  flags.
- Không chỉnh sửa toàn bộ tài liệu feature-level đã tồn tại.
- Không push thư mục workspace root hoặc `delivery_simulator_web`.
- Không chạy production/staging deployment hay gửi thông báo bên ngoài Git.

