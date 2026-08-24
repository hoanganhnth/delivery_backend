# Execution Plan: Đồng bộ ba client với backend MVP đã freeze

Date: 2026-07-26

## Status

Completed

### Current execution frontier (2026-07-29)

- Static/contract/build implementation của cả ba client đã green; mobile không
  dùng emulator/device làm acceptance gate. No-emulator usecase/action,
  adapter/contract và state tests hiện đủ làm MVP gate; lần test thật cuối chỉ
  còn sanity check trên thiết bị thật.
- Client static/build gate rerun không dùng emulator ngày 2026-07-29:
  `scripts/verify-mvp-polyrepo-contract.sh` PASS; `shipper_app2`
  `npm run verify` PASS với typecheck, ESLint và 16 suite/57 test; Android
  `./gradlew assembleDebug` PASS 411 task từ checkpoint trước. `delivery_web`
  ESLint + production build PASS, còn warning chunk lớn/browserslist cũ.
  `delivery_app` `fvm flutter analyze` PASS, `fvm flutter test` PASS 130/130 và
  `fvm flutter build apk --debug` PASS từ checkpoint trước. `delivery_app`
  thêm 10 regression test cho user-address, order và restaurant-rating service
  đã pass với `http://gateway.test/api`, khóa `/addresses`, `/orders` và
  `/restaurants/{id}/ratings` vào canonical Gateway contract.
- `delivery_web` Chat/Firebase đã bị loại khỏi MVP ngày 2026-07-29: không còn
  `ChatProvider`, floating `ChatWidget`, `/admin/chat` hoặc `/restaurant/chat`
  route/nav; `src/modules/chat`, `AdminChatPage`, `src/config/firebase.ts`,
  `src/types/chat.types.ts` và dependency `firebase` đã bị xóa vì chưa có Firebase
  Auth/custom-token và Firestore rules proof. Web lint/build PASS, bundle JS còn
  `401.57 kB`; source scan không còn Chat/Firebase match; root
  `scripts/verify-mvp-polyrepo-contract.sh` PASS và có guard chống tái expose/
  khôi phục graph.
- Backend Order → Delivery lifecycle checkpoint đã được xác nhận bằng Delivery
  clean test `68/68`: `assignedAt` chỉ xuất hiện khi shipper accept và chuyển
  sang `ASSIGNED`; không có migration, và historical `assigned_at` không tự
  động rewrite.
- Saga rematch checkpoint đã chặn acceptance trễ của shipper bị reject ngay cả
  sau khi offer mới đã được tìm thấy (`SHIPPER_FOUND`). Saga clean suite đạt
  `50/50`; native app/emulator không liên quan đến boundary này.
- Match cancellation nay giải phóng exact Redis offer ngay khi order bị huỷ và
  tombstone chặn in-flight reserve; clean Match `46` test + live Redis `2/2`
  PASS. Thay đổi nằm hoàn toàn ở backend, không cần chạy app/emulator.
- Auth public flow nay reject tạo mới `SHIPPER` khi chưa có Shipper profile
  onboarding, nhưng social login của existing operator-provisioned SHIPPER vẫn
  hợp lệ. Auth/User clean `44/44` và `31/31`; shipper client không cần emulator
  cho contract này và phải hiển thị backend error nếu email chưa được provision.
- Runtime seed/harness nay theo đúng contract đó: fixture SHIPPER được tạo bằng
  auth-service one-shot operator runner qua AuthService + User provisioning,
  không dùng public self-register và không mở ADMIN. Seed cleanup đưa fixture
  shipper cũ offline để current-offer E2E không bị dữ liệu test cũ chiếm offer.
  Evidence 2026-07-29: seed standalone PASS, COD harness PASS, failure matrix
  PASS.
- Audit reachability sâu của `shipper_app2` đã xanh; current-offer wake-up cho
  MVP đã được chốt bằng bounded polling fallback, nên không cần Firebase native
  config/FCM SDK để tiếp tục E2E. FCM native còn là tối ưu dài hạn sau MVP.
- Runtime ADMIN browser smoke đã PASS ngày 2026-07-29 bằng operator-only runner:
  Gateway login chuyển đúng tới `/admin/dashboard`; Dashboard, Orders, Shippers,
  Ratings, Coupons và Flash Sales đều render dữ liệu/empty state qua Gateway và
  browser console không có error/warning. CORS preview origin
  `127.0.0.1:4173` đã được fix trước đó. Fixture tạm bị block qua API, đăng nhập
  lại trả `401`, active `admin+*@test.dev` trở về `0` và không còn credential/
  response temp file. Cleanup đồng thời phát hiện và loại zero-consumer
  `GET /api/auth/admin/accounts` từng cắt âm thầm ở 100 bản ghi; inventory/build/
  polyrepo gates xanh với 146 handler và runtime Gateway trả `404` cho route này.
- 2026-07-29 rerun admin API smoke qua Gateway PASS cho
  `/api/users`, `/api/orders/all`, `/api/shippers`, `/api/shippers/online`,
  `/api/restaurants/admin/ratings`, `/api/promotions/admin` và
  `/api/flashsales/admin/campaigns` sau khi sửa runtime backend defects ở
  `shipper-service` và `flashsale-service`; fixture tạm đã được block cleanup
  lại ngay sau smoke.
- 2026-07-29 E2E fixture seed root-run PASS after path fix: running
  `RUN_ID=1785337201 SEED_OUTPUT_FILE=/tmp/delivery-seed-1785337201.json bash
  backend_delivery/scripts/seed.sh` from the polyrepo root created customer,
  outsider, owner, restaurant `2`, menu item `2`, operator-provisioned shipper,
  settlement deposit and tracking location through the current Gateway/runtime
  stack. This fixture path is now usable for the final web/mobile/cross-client
  smoke without requiring emulator.
- Tracking publisher convergence đã rerun current runtime qua Gateway mà không
  cần app: generation fence, reconnect quá grace và Redis→Kafka→Match tombstone
  đều PASS.
- Backend Order legacy API closeout đã sync xong với client-facing contract:
  inventory hiện còn 146 mapped controller methods; generic Order read/mutation/
  assign/status compatibility controllers không còn tồn tại và Compose không còn
  set legacy flags. Polyrepo contract gate PASS.
- `delivery_app` support-chat graph đã bị hide khỏi runtime route/navigation:
  `/support-chat`, `pushSupport()` và profile entry points đã bị gỡ; static
  analyzer cho routing/profile PASS sau cleanup. Firebase chat code vẫn còn
  trên disk nhưng không còn là runtime path MVP.
- 2026-07-29 no-emulator/action-proof policy update: mobile emulator/device is
  no longer an acceptance gate. Added `delivery_web` action-contract verifier
  (`npm run test:actions`, included in `npm run verify`) to guard owner/admin
  login/logout, restaurant order confirm/reject, menu/profile create/update/
  delete, admin rating/coupon/flash-sale actions, read-only shipper admin and
  hidden MVP graph cleanup. Evidence: `delivery_web npm run verify` PASS,
  root `scripts/verify-mvp-polyrepo-contract.sh` PASS.
- 2026-07-29 mobile action-proof follow-up: `shipper_app2` now has service/slice
  regression coverage for delivery lifecycle status updates
  `PICKED_UP/DELIVERING` and reject-offer state convergence; `npm run verify`
  PASS with `16/16` suites and `61/61` tests. `delivery_app` now has widget
  action tests for checkout place-order, disabled checkout loading state, cart
  checkout navigation, address default switch/save and disabled submit state.
  These tests caught and fixed two UI logic defects: checkout bottom no longer
  null-crashes when only `serverTotal` is present, and address default row no
  longer overflows on a phone viewport. Evidence: focused widget test PASS,
  `fvm flutter analyze` PASS and `fvm flutter test` PASS `135/135`.
- 2026-07-29 final no-emulator gate rerun: root
  `scripts/verify-mvp-polyrepo-contract.sh` PASS; `delivery_web npm run verify`
  PASS; `shipper_app2 npm run verify` PASS with `16/16` suites and `61/61`
  tests; `delivery_app` `fvm flutter analyze` PASS and `fvm flutter test` PASS
  `135/135`. No emulator/device was launched.

### Open client decision

- The shipper foreground offer wake-up decision is resolved for MVP via bounded
  polling. FCM native remains a post-MVP optimization, not a blocker for the
  current runtime smoke.
- Keep real-device work out of the required gate. Browser work is allowed for
  `delivery_web` because it is practical locally; mobile real-device testing is
  only final sanity after automated logic/usecase coverage is complete.
- Treat `delivery_web` as the functional reference when verifying shared
  workflows; mobile clients should match the same canonical backend contract
  and hide anything web already proved unsupported or hidden.
  Static/build/test gates remain the normal iteration path.

### MVP acceptance gate ledger (current)

| Gate | Current evidence | Next action |
|---|---|---|
| ADMIN browser smoke | PASS; owner browser smoke PASS; runtime ADMIN fixture/API smoke đã PASS bằng one-shot runner; browser render được `/admin/login`; CORS preview origin `4173` đã fix và preflight PASS. | None; keep as regression gate only. |
| Cross-client usecase/action proof | PASS no-emulator: backend COD/failure harness, root contract scan, web action contracts/build, shipper service/slice contracts and customer Flutter logic/widget tests. | Regression-only unless backend/client contract changes. |
| Mobile device sanity | Android debug APK/build gates PASS; iOS authenticated smoke lacks local signing/device proof. | Not an acceptance gate; record as final manual sanity/limitation after automated proof. |
| Final docs closeout | Contract/client no-emulator gates đang PASS. | Cập nhật result và đóng plan khi người dùng muốn chốt MVP; không cần thêm emulator proof. |

## Outcome

Ba client chỉ dùng API Gateway `:8079`, role/path/payload đúng contract backend
MVP, hoàn thành journey COD cơ bản và không còn phụ thuộc gRPC, STOMP/direct
service port hoặc capability đang hidden. Màn hình chưa có contract MVP được
ẩn/disable rõ ràng thay vì gọi API chắc chắn lỗi.

## Context

- Backend authority: `backend_delivery/docs/http-api-inventory.md` và
  `backend_delivery/docs/system-contract-inventory.md`.
- Luồng chuẩn: `docs/product/overview.md`, `docs/ARCHITECTURE.md` và
  `docs/product/features/order-lifecycle.md`.
- Freeze evidence: clean Gate B8 + failure matrix; 602 test/179 suite; polyrepo
  contract gate PASS; JDK/Compose/diff gates PASS ngày 2026-07-28.
- Tracking MVP dùng raw `/ws/shipper-locations?deliveryId=...` qua Gateway với
  JWT và participant check. gRPC và STOMP không thuộc contract.
- Auth MVP revoke refresh/session ngay; access JWT đã phát có thể còn hợp lệ tối
  đa 15 phút.

## Scope

In scope:

- Một base URL Gateway theo environment cho mỗi client; không hard-code direct
  service ports trong feature code.
- Chuẩn hóa refresh-token single-flight, response envelope, role và canonical
  status vocabulary.
- Customer: catalog/search, address, COD checkout/order history/cancel,
  notification và participant tracking.
- Shipper: profile/online, durable offer recovery, accept, cancel-assignment,
  delivery lifecycle, location publish, history và notification inbox. Mỗi
  action chính phải có logic/service/redux/adapter test thay cho emulator proof.
- Web: admin read/moderation, restaurant/menu ownership, restaurant
  confirm/reject và public catalog surfaces. Web có thể dùng browser smoke hoặc
  component/action tests vì chạy được local.
- Ẩn/disable payment/VNPay, self settlement/withdrawal, voucher/flash checkout,
  merchant flash/promotion create, analytics, livestream, shipper rating write,
  customer shipper search và admin fleet-location realtime.

Out of scope:

- Mở backend capability hidden/disabled hoặc thêm API mới để giữ UI cũ.
- gRPC, STOMP compatibility và direct service access.
- Payment online, promotion/flash reservation, settlement self-service,
  analytics/livestream và production observability/scale work.
- Đổi Kafka event/schema hoặc backend state machine sau freeze; nếu bắt buộc phải
  mở lại backend plan và chạy lại contract gates.

## Approach

### No-emulator usecase/action proof matrix

| Surface | Action/usecase | Automated evidence | Remaining note |
|---|---|---|---|
| Web owner/admin | Login/logout, dashboard navigation, role routing | `delivery_web npm run verify`, Auth/page/action contract scan | Browser smoke remains useful but not the only proof. |
| Web restaurant | Order list/filter/pagination, confirm, reject, bounded refresh | `delivery_web scripts/verify-action-contracts.mjs`, service contract assertions, browser smoke history | PASS; regression-only unless backend contract changes. |
| Web restaurant | Restaurant profile create/update, menu item create/update/delete | `delivery_web scripts/verify-action-contracts.mjs` | PASS. |
| Web admin | Ratings approve/reject, coupon create/delete, flash-sale campaign create/status, item approve | `delivery_web scripts/verify-action-contracts.mjs` | PASS. |
| Web admin | Shipper admin list/online filter | `delivery_web scripts/verify-action-contracts.mjs` proves read/filter only and no unsupported block/delete mutation | PASS. |
| Customer app | Auth/session/refresh/logout, profile update, search, address API, order API, rating API, tracking socket adapter | `fvm flutter test` service/provider/adapter tests | PASS no-emulator. |
| Customer app | Checkout button, cart checkout button, address default/save buttons | `test/features/cart/presentation/widgets/checkout_action_widgets_test.dart` | PASS; found/fixed checkout null crash and address overflow. |
| Customer app | Reorder, cancel-order, rating submit | Existing reorder utility, order API/repository, cancel provider and rating API tests | Covered below UI through logic/service layers; final real-device sanity only. |
| Shipper app | Login/social/logout/session, current offer recovery, accept/reject/cancel assignment, history, notification, WebSocket config | `shipper_app2 npm run verify` service/redux/contract tests | PASS no-emulator. |
| Shipper app | Delivery lifecycle status updates and reject-offer convergence | `deliveryService.contract.test.ts`, `currentDeliverySlice.test.ts` | PASS; final background/GPS sanity only. |

### Wave 1 — Shared contract và cấu hình

1. Tạo endpoint fixture từ inventory cho các public route client dùng; thêm
   contract tests tại từng client để chặn `/api/api`, direct port và hidden path.
2. Dùng một convention:
   - Flutter base URL kết thúc bằng `/api`, endpoint tương đối không có `/api`.
   - Web và React Native base URL là origin Gateway, endpoint có `/api`.
   - WebSocket URL derive từ Gateway origin (`http→ws`, `https→wss`) và path
     `/ws/shipper-locations`.
3. Chuẩn hóa auth storage/refresh single-flight; không log access/refresh token;
   logout xóa local session ngay và chấp nhận bounded 15-minute server window.
4. Chuẩn hóa `BaseResponse` (`status`, `data`, `message`) và page envelope trước
   khi migrate từng feature.

### Wave 2 — `shipper_app2` fulfilment trước

1. Sửa `src/config/api.js` và `src/utils/constants/routes.js`: Gateway env,
   `/api/auth/social-login`, bỏ Google/Apple path riêng, bỏ token logging.
2. Gọi `GET /api/deliveries/offers/current` khi login/reconnect/foreground;
   durable Notification/FCM chỉ là wake-up, không phải nguồn offer duy nhất.
3. Dùng `POST /api/deliveries/accept` với canonical `action=ACCEPT`; offer hết
   hạn/conflict phải refresh current offer thay vì optimistic success.
4. Đổi bỏ đơn sau accept từ `PUT /api/orders/{id}/cancel` sang
   `POST /api/deliveries/cancel-assignment` với `orderId` và reason.
5. Khóa UI/API transition `ASSIGNED→PICKED_UP→DELIVERING→DELIVERED`; same-state
   retry được phép, skip/reverse bị chặn và lỗi backend phải hiển thị đúng.
6. Location publish dùng raw WebSocket Gateway có JWT; REST tracking update/
   offline là self fallback. Xóa STOMP notification; inbox REST + FCM là chuẩn.
7. Ẩn settlement balance/withdraw/deposit/payment vì không public trong COD MVP.

### Wave 3 — `delivery_app` customer journey

1. Sửa search datasource bỏ prefix `/api` lặp; dùng `/search/restaurants` và
   `/search/dishes`; loại customer shipper search.
2. Giữ address APIs dưới role USER, profile self, catalog/menu public và FCM
   token endpoints canonical.
3. Checkout chỉ COD: checkout-preview → create order; không gửi voucher, flash
   reservation hoặc payment-online khi capability đang disabled.
4. Order list/detail/cancel dùng `/orders/my-orders`, `/orders/{id}` và
   `/orders/{id}/cancel`; map đúng canonical status.
5. Thay Delivery STOMP `/ws/delivery-native` bằng REST detail/order refresh +
   durable notification cho status. Location map dùng raw Gateway WebSocket với
   `deliveryId` và JWT; không dùng direct `:8090`/`:8093`.
6. Ẩn shipper rating write, livestream/payment UI và promotion/flash checkout;
   public campaign/voucher wallet chỉ giữ khi đúng role/contract.

### Wave 4 — `delivery_web` restaurant/admin

1. Sửa endpoint registry: `PUT /api/users`, search `/api/search/dishes`,
   notification `mark-all-read`; loại path-ID/current-user và generic list path
   không tồn tại.
2. Restaurant order action chuyển generic Order status sang
   `POST /api/restaurants/orders/{orderId}/confirm|reject`; self list dùng
   `my-restaurants`, `my-menu-items`, `my-restaurant-orders` với SHOP_OWNER.
3. Admin chỉ giữ Auth/User/Order/Shipper read, rating moderation, Flash Sale
   admin, Promotion admin và Settlement admin GET surfaces đã freeze.
4. Ẩn settlement approve/reject/reverse, merchant promotion create, merchant
   flash item, analytics và livestream vì backend mặc định không route.
5. Loại STOMP/SockJS admin fleet tracking. MVP có online fleet list nhưng không
   có public arbitrary location stream; không dùng direct port để lách Gateway.
6. Xóa dependency STOMP/SockJS khi không còn caller và chạy bundle audit.

### Wave 5 — Cross-client journey và cleanup

1. Chạy journey canonical: customer login/COD order → owner confirm → shipper
   recover/accept → participant location tracking → delivered → notification/
   history.
2. Chạy failure journey: token refresh/logout, wrong role 403, expired offer,
   no shipper, restaurant reject, cancel-assignment/rematch, out-of-order status.
3. Search toàn polyrepo để chặn direct service ports, `/api/api`, STOMP/gRPC và
   calls tới hidden endpoint; chỉ fixture/docs lịch sử được allowlist.
4. Cập nhật feature docs, ghi kết quả và chuyển plan completed khi cả ba repo
   gates cùng UI E2E đều xanh.

## Risks And Recovery

- Response adapter chưa đồng nhất; mỗi wave phải thêm fixture test trước khi đổi
  caller để tránh lỗi UI âm thầm.
- Raw WebSocket trên mobile/web khác cách gửi Authorization. Dùng adapter theo
  platform và test handshake 401/authorized; không log JWT. Nếu browser không
  hỗ trợ custom header, cần product/backend authority cho handshake khác; không
  tự đưa token vào query string.
- Feature cũ có thể chiếm navigation chính. Disable bằng capability registry và
  thông báo “chưa hỗ trợ trong MVP”; không xóa hàng loạt trước zero-call-site.
- Mỗi repo là git repo riêng. Commit/rollback theo wave và repo; không trộn
  backend contract change với client migration commit.

## Progress

- [x] Backend contract freeze và actor/API classification.
- [x] Ghi nhận mismatch/call-site ban đầu của cả ba client.
- [x] `shipper_app2` network/auth foundation: một Gateway origin theo env, refresh
  single-flight không circular import, token không log, logout gọi server rồi
  clear local, Google dùng exact `/api/auth/social-login` + role `SHIPPER`, Apple
  và password-change unsupported bị ẩn/fail-closed.
- [x] `shipper_app2` fulfilment contract: durable current-offer recovery khi
  startup/foreground, accept/reject one-offer, cancel-assignment thay Order
  cancel, exact self history/active, raw WebSocket Gateway có Authorization.
  STOMP notification và toàn settlement/payment Redux/service/navigation graph
  đã xóa sau zero-call-site proof.
- [x] `shipper_app2` Android/iOS runtime smoke được hạ khỏi acceptance gate theo
  quyết định user. Android
  `assembleDebug` rerun 2026-07-29 PASS 411 task bằng Gradle cache trong
  workspace. Pixel_9a native launch + Metro bundle đã render login screen
  không FATAL/RedBox; debug APK cần Metro `:8081` khi cài trực tiếp.
  Authenticated fulfilment/background-foreground journey và iOS smoke được giữ
  làm final sanity/limitation, không phải acceptance gate tự động.
- [x] Wave 1 shared contract/config: cả ba client dùng Gateway origin theo env;
  web normalize bỏ trailing `/api` để không sinh `/api/api`.
- [x] Wave 2 shipper fulfilment: implementation/typecheck/test đã xanh; client
  lifecycle nay đóng location WebSocket khi background và reconnect qua Gateway
  khi foreground để Tracking lease/grace hội tụ offline/online. TypeScript và
  ESLint zero warning/error; 2026-07-29 final no-emulator rerun
  `npm run verify` PASS với `16/16` suite và `61/61` test. Offer wake-up đã chốt
  bằng bounded polling fallback; final device runtime chỉ là sanity/limitation.
- [x] Wave 2 shipper lifecycle action proof: delivery service and
  current-delivery slice now cover canonical status update endpoint, malformed
  lifecycle response rejection, active assignment convergence for
  `PICKED_UP -> DELIVERING`, and reject-offer state cleanup. Proof:
  `shipper_app2 npm run verify` PASS with `16/16` suites and `61/61` tests.
- [x] Wave 2 shipper production-truth closeout: drawer/profile không còn identity,
  tier, session duration, order count, earnings hay notification badge giả; dùng
  auth/shipper/notification Redux truth và online/offline state thật. Drawer logout
  không còn no-op; local auth state luôn thoát kể cả server revoke lỗi. Client
  không tự tính split tiền `85/15`, không dựng tip/customer/time/weekly goal và
  không giữ fake GPS route. Auth initializer dừng trước protected API khi local
  auth vắng. Self-register/forgot-password bị ẩn vì chưa có onboarding/recovery
  contract Auth→User→Shipper; operator/fixture-provisioned account vẫn là MVP path.
- [x] Wave 3 customer journey: implementation/static/test và Android debug APK
  build đã xanh; 2026-07-29 final no-emulator rerun `fvm flutter analyze` PASS
  và `fvm flutter test` PASS `135/135`; authenticated Android/iOS được giữ làm
  final sanity/limitation, không phải acceptance gate tự động.
  `Pixel_9a` boot, APK install và login UI sau Hive fix đều PASS. Trạng thái từng
  bị chẩn đoán là treo splash thực tế là Android notification permission dialog;
  sau khi xử lý quyền, app hội tụ unauthenticated → login, không FATAL. Desktop
  smoke path trên macOS đã vượt lỗi deployment target 10.14 -> 10.15 sau khi
  chỉnh Podfile/Xcode project, nhưng build native vẫn quá nặng để dùng làm gate
  thường xuyên; giữ như checkpoint cuối.
- [x] Wave 3 customer unauthorized/FCM closeout: native smoke lộ vòng lặp
  `401 -> clear token -> logout cleanup -> FCM unregister -> 401`. Auth
  interceptor nay chỉ refresh request đã gắn Bearer token, bỏ qua login/register/
  social/refresh và cho cleanup request opt-out; concurrent/retried 401 kết thúc
  session idempotent, không deadlock và callback unauthorized tối đa một lần.
  Session-expired chỉ xóa local auth state, không chạy authenticated cleanup;
  logout chủ động mới unregister FCM. FCM chỉ sync token khi authenticated và
  network logging không còn headers, query hay request/response body. Regression
  10/10, full Flutter 91/91, analyzer 0 issue, diff check và Android debug APK
  rebuild đều PASS ngày 2026-07-26. Native rerun từ clean data hội tụ qua
  notification permission tới login, không còn request storm, payload log hoặc
  biometric disposed exception sau khi notifier thêm mounted guard và login bỏ
  probe biometric unsupported. Pixel_9a vẫn phát ANR do cold-start debug/JIT/
  Mapbox rất chậm; theo quyết định user, authenticated native journey được gom
  về checkpoint cuối trên máy thật, không coi emulator là đường mặc định.
- [x] Wave 3 customer production-truth closeout: catalog/order API lỗi không còn
  fallback sang mock; fake shipper movement, map/ETA/courier/promo/rating/phí giao
  hàng giả và dead tracking screen đã loại. Order detail dùng canonical
  `subtotalPrice`, `discountAmount`, `shippingFee`, `totalPrice`; malformed socket
  location bị bỏ qua thay vì dựng tọa độ `0,0`. Home nối Search, Notification và
  Address route thật; forgot-password, biometric-login và action TODO chưa có
  contract bị ẩn. Flutter analyzer 0 issue, full suite 78/78 và Android debug APK
  rebuild PASS.
- [x] Wave 3 customer widget action proof: checkout bottom action, cart checkout
  action and address default/save actions now have widget tests under
  `test/features/cart/presentation/widgets/checkout_action_widgets_test.dart`.
  The slice fixed a real crash/overflow pair found by tests:
  `CheckoutBottomSection` displays server total independently from optional
  breakdown rows, and `AddressBottomActions` wraps the default-label text so the
  switch row fits a phone viewport. Proof: focused widget test PASS,
  `fvm flutter analyze` PASS and `fvm flutter test` PASS `135/135`.
- [x] Customer auth/session test cleanup (2026-07-28): `AuthNotifier` placeholder
  test đã thay bằng Riverpod provider test thật cho initial/checkAuthStatus/login
  success/failure/refresh/logout; hai file placeholder dư đã xóa. Login integration
  smoke đã bỏ credential hard-code và assertion forgot-password cũ; live backend
  login chỉ chạy khi explicit `RUN_LIVE_LOGIN_SMOKE=true` cùng
  `TEST_LOGIN_EMAIL`/`TEST_LOGIN_PASSWORD`. Focused auth test PASS, analyzer cho
  auth test + integration smoke 0 issue; không chạy app/emulator.
- [x] Wave 4 web restaurant/admin implementation + static gates: restaurant chỉ
  confirm/reject đơn `PENDING` bằng endpoint/payload canonical; admin/restaurant
  chỉ còn surface MVP đã freeze. Livestream, settlement self-service, analytics,
  merchant promotion/flash, withdrawal mutation, arbitrary realtime tracking và
  manual shipper assignment đã bị loại khỏi route/navigation/runtime graph.
- [x] Wave 4 web production-truth closeout: owner/admin login không còn log
  credential; forgot/social/remember/register UI không hoạt động đã bị loại khỏi
  portal. Auth bootstrap/login fail hội tụ về local logout. Restaurant profile
  dùng canonical `RestaurantForm`, không còn hard-code cuisine/city/postal, ghép
  lặp address hoặc hiển thị open-state giả. TypeScript, ESLint và production build
  PASS. Tailwind utility CSS nay được build local bằng PostCSS thay vì phụ thuộc
  CDN runtime; API/auth console logging và năm component layout không caller đã
  loại. Vite/PostCSS/ESLint cùng Axios/Firebase/React Router được nâng theo
  advisory hiện hành. ESLint nay cover cả TS/TSX (gate cũ chỉ đọc JS/JSX), 22 lỗi
  effect/type/adapter và 9 warning bị lộ đã sửa về zero. Production
  audit chỉ còn advisory React Router RSC/server-action không áp dụng cho Vite
  SPA. Rerun 2026-07-29: ESLint và production build PASS; Build Vite 8 còn cảnh
  báo main chunk khoảng 907 KB và browserslist data cũ.
- [x] Wave 4 browser runtime smoke: owner login/dashboard/order list và confirm
  qua Gateway PASS trên fixture cô lập. Menu/profile/reviews cũng PASS với đúng
  một menu item, VND, profile canonical, rating 0 từ backend và console không có
  app error/warning. Runtime ADMIN fixture không còn là blocker: ngày
  2026-07-29 one-shot operator runner tạo fixture tạm, Gateway login PASS,
  `GET /api/users` trả role `ADMIN`, và cleanup block PASS; thêm rerun
  `delivery_web` ESLint/build PASS và root polyrepo contract gate PASS. Browser
  đã render được `/admin/login`; attempt đầu tiên phát hiện Gateway/Compose CORS
  thiếu Vite preview origin `localhost|127.0.0.1:4173`. Đã sửa
  `api-gateway` + `docker-compose.yml` default CORS và guard trong compose
  verifier; proof: Gateway `mvn -q -pl api-gateway test` PASS,
  `mvn -q -pl api-gateway -DskipTests package` PASS, Gateway container
  rebuild/recreate, preflight `Origin: http://127.0.0.1:4173` trả `200 OK`,
  `backend_delivery/scripts/verify-compose-config.sh` PASS,
  `backend_delivery/scripts/verify-build-baseline.sh` PASS và root polyrepo
  contract gate PASS. Full authenticated Admin rerun sau CORS fix cũng PASS cho
  dashboard, orders, shippers, ratings, coupons và flash-sales; console sạch,
  fixture bị block, login replay trả `401`, active fixture còn `0` và temp file
  đã dọn. Zero-consumer Auth admin list bị gỡ sau khi runtime lộ cap 100 âm thầm;
  inventory/build/polyrepo gate PASS với 146 handler và removed route trả `404`.
- [x] Wave 4 web action-contract gate: added `scripts/verify-action-contracts.mjs`
  and `npm run verify` so lint, action contract and production build run
  together. The gate fails if key web actions stop calling canonical services/
  endpoints or if Chat/Firebase, Livestream, Settlement, arbitrary tracking,
  STOMP/SockJS, direct service ports or `/api/api` return. Proof:
  `delivery_web npm run verify` PASS and root
  `scripts/verify-mvp-polyrepo-contract.sh` PASS.
- [x] Wave 5 cross-client E2E và cleanup no-emulator closeout: backend COD/
  failure/runtime harness evidence, root polyrepo contract scan, web action
  contracts, shipper service/slice contracts and customer Flutter logic/widget
  tests cover the MVP action path without launching emulator/device. Browser
  web smoke remains regression-only; mobile real-device run remains final
  sanity only.
- [x] Current-state backend/runtime prerequisite rerun (2026-07-26): canonical
  stack dùng image mới cho toàn bộ service và giữ exact PostgreSQL/Kafka volume;
  đủ 17 app + Gateway public reads PASS. Authenticated backend COD journey tạo
  order/delivery `16`, durable offer recovery, raw WebSocket auth/participant,
  lifecycle, bốn settlement rows và duplicate replay bất biến; failure matrix
  đạt reject `17`, no-shipper `18`, cancel-assignment/rematch/completion `19`.
  Full backend reactor 613 test/180 suite và mọi static contract gate xanh.
- [x] Current client static rerun (2026-07-26): Flutter analyzer 0 issue và
  78/78 test; Web TypeScript, ESLint, production build PASS (chunk warning
  khoảng 916 KB sau Vite 8/React Router 7); Shipper TypeScript, ESLint quiet và
  23/23 Jest PASS. Diff
  hygiene bốn repo và production-source scan direct port, `/api/api`, gRPC,
  STOMP/SockJS sạch. UI-driven authenticated cross-client/native proof later
  superseded by the final no-emulator acceptance policy and action/contract
  gates recorded in Result.
- [x] Wave 3 customer fail-closed DTO/UI rerun (2026-07-26): Delivery mapper
  nhận đủ chín canonical status và reject identity/address/tọa độ/timestamp hỏng
  thay vì dựng dữ liệu. Order mapper reject status lạ, non-COD, total/item/
  restaurant identity thiếu và giữ `shipperId` backend. Reorder validate toàn
  payload trước khi xóa cart; order/tracking không gọi bằng ID `0`; nút gọi
  shipper no-op, fake order label/USD và legacy create-order graph đã loại.
  Notification mapper reject ID/user/timestamp/priority/status malformed và UI
  không echo exception. Checkout tự tải và bắt buộc server preview hợp lệ trước
  khi bật đặt đơn; không fallback local total hay gửi restaurant/menu facts giả.
  Create request chỉ gửi field client sở hữu. Focused checkout 8/8, analyzer 0
  issue, full Flutter 111/111, diff check và debug APK build PASS; không chạy
  emulator.
- [x] Shipper/web production-truth follow-up (2026-07-26): Shipper backend
  không còn default rating `5.0`; migration chỉ clear giá trị không có rating row
  sở hữu và ratings endpoint dùng `BaseResponse`. React Native parse strict
  notification/rating/profile response, không default missing payload/count/
  rating/shipping fee thành dữ liệu thật. Restaurant create bắt buộc pickup
  coordinate Việt Nam; owner form không còn khởi tạo `0,0`. Shipper TypeScript,
  ESLint và 13 suite/40 Jest PASS; Shipper backend 21/21, Restaurant backend
  94/94 JDK 17; web ESLint/build PASS. Không chạy emulator.
- [x] Focused canonical runtime proof cho follow-up (2026-07-26): package JAR
  hiện tại bằng JDK 17 trước khi rebuild riêng Shipper/Restaurant (Dockerfile chỉ
  copy artifact trong `target/`). Flyway Shipper V2 được apply và hiện có trong
  schema history; read-only invariant query trả `0` synthetic/unowned rating
  trên 21 profile. Gateway smoke chứng minh restaurant create thiếu tọa độ trả
  400, shipper self-ratings trả `BaseResponse`/200 và public catalog vẫn trả 200.
  Compose contract PASS, 21/21 container còn running, volume PostgreSQL/Kafka
  được giữ nguyên. Không chạy app/emulator.
- [x] Current-state contract rerun (2026-07-28): `scripts/verify-mvp-polyrepo-contract.sh`
  PASS sau analytics cleanup; inventory aligned với 154 mapped controller
  methods và hidden-route/transport scans sạch.
- [x] Polyrepo static contract/dead-graph closeout (2026-07-27): audit toàn bộ
  production endpoint literals lộ Flutter vẫn compile Livestream/IAP dù route đã
  ẩn, customer Delivery tracking gọi ADMIN-only `/shippers/{id}`, và Restaurant
  giữ `/restaurants/nearby|categories` không có controller/UI caller. Đã xóa hai
  feature graph Livestream/IAP/Promotion/Flash Sale/Admin/duplicate Location,
  Agora config, customer shipper profile/in-area stack, nearby/categories stack
  và direct dependency Agora/IAP/Socket.IO; tổng 180 file trong các graph xác
  định bị loại. Delivery tracking không còn biến
  delivery hợp lệ thành error do shipper-profile 404; chỉ giữ canonical
  `shipperId` + raw participant location. Web REST Chat implementation và
  `/api/chat/**` registry zero-call-site cũng bị xóa, còn Firebase Chat feature có
  caller được giữ nguyên. Gate mới `scripts/verify-mvp-polyrepo-contract.sh` khóa
  backend inventory, direct ports, `/api/api`, gRPC/STOMP/SockJS, hidden routes và
  removed graphs. Backend public campaign/voucher reads vẫn được giữ; chỉ client
  graph không có UI caller bị loại. Proof: inventory 161/161, Flutter analyzer 0 + 111/111 + debug
  APK, Web ESLint/build PASS; không chạy app/emulator/browser.
- [x] `shipper_app2` reachability/session/tracking closeout (2026-07-27): graph
  từ `index.js`/`App.tsx` chỉ còn hai `.d.ts` global cố ý không import; đã xóa
  Settings placeholder, Permissions route không caller, 15 DTO/barrel/utility
  mồ côi và dependency template/DI/legacy icon. Login chỉ mở authenticated graph
  sau user+shipper bootstrap; logout/refresh expiry reset toàn bộ state theo tài
  khoản. Cancel-assignment và active recovery không còn giữ delivery Redux cũ.
  Online toggle nay quyết định GPS/socket; publisher gửi ping 30 giây để giữ
  lease 120 giây và không reconnect close `1008` khi session mới supersede.
  Popup offer giữ đúng tới deadline thay vì bị auto-hide sau 15 giây. `npm run
  Delivery response không còn cast mù: parser khóa identity/status canonical,
  tọa độ, timestamp và money trước Redux/UI; stale Order types/commented DTO bị
  loại. `npm run verify` đạt TypeScript + ESLint zero warning/error + 16 suite/55
  test; dependency
  tree sạch, root contract gate 161/161 PASS. Android `assembleDebug` sau native
  dependency cleanup PASS 411 task, APK khoảng 207 MB; không chạy Metro/emulator.
- [x] `shipper_app2` foreground offer wake-up: backend có durable inbox + FCM
  optional, nhưng MVP đã chốt bounded current-offer polling fallback nên không
  cần `google-services.json`/iOS Firebase config hay RN messaging dependency/
  token registration để tiếp tục E2E. FCM wake-up vẫn là tối ưu dài hạn sau MVP.
- [x] Shared response/page contract closeout (2026-07-27): web Axios không còn
  chấp nhận raw payload, legacy `code` hay `handleResponse` fallback; refresh
  token fail-closed và không bắt 401 public/logout. Flash Sale/Promotion response
  backend đã đồng bộ `BaseResponse`; Web admin DTO được parse runtime. Toàn bộ 11
  endpoint phân trang Order/Shipper/Search và consumer Web/Flutter dùng stable
  `{items,page,size,totalItems,totalPages,hasNext}`, không còn Spring
  `.content/.number/.totalElements`. Flutter Search/Order reject legacy response;
  analyzer + full test PASS, Web lint/build PASS, backend focused suite tổng
  139 test PASS và polyrepo inventory/transport gate xanh. Không chạy emulator.
- [x] `delivery_web` reachability/Chat functional audit (2026-07-27): xóa CSS,
  asset, layout/module index mồ côi và dependency trực tiếp không caller; graph
  alias-aware sạch ngoài `vite-env.d.ts`. Firebase Chat nay subscribe/unsubscribe
  conversation/message thật, query support chat đúng loại, parse timestamp và
  bỏ unread/date/button giả. Tuy nhiên repo chưa có Firebase Auth/custom-token,
  Firestore rules hoặc deployment config nên role JWT portal chưa được chứng minh
  tại Firestore boundary. Không coi Chat đạt security gate; chờ quyết định thêm
  Firebase identity/rules hoặc ẩn feature khỏi MVP.
- [x] `delivery_web` Chat/Firebase MVP cleanup (2026-07-29): chọn loại feature
  khỏi MVP thay vì giữ caller/source graph chưa có security authority. Đã gỡ
  `ChatProvider`, `ChatWidget`, route `/admin/chat|/restaurant/chat`, nav entry,
  `src/modules/chat`, `AdminChatPage`, `src/config/firebase.ts`,
  `src/types/chat.types.ts` và dependency `firebase`. Root contract gate nay fail
  nếu runtime/navigation expose lại Chat hoặc graph Firebase Chat được khôi phục.
  `npm run lint && npm run build` PASS; `scripts/verify-mvp-polyrepo-contract.sh`
  PASS.
- [x] `delivery_app` support-chat hidden/fail-closed (2026-07-29): route
  `/support-chat`, `pushSupport()` và profile entry points đã bị gỡ; routing/
  profile analyzer PASS sau cleanup và contract gate giữ client transports/
  hidden-route scans sạch. Firebase chat code còn trên disk nhưng không còn là
  runtime path MVP.
- [x] Backend internal-response checkpoint (2026-07-27): các Web/Flutter parser
  không còn đứng trên giả định raw Boolean ở boundary nội bộ; sáu endpoint
  Order/Delivery/Restaurant/Settlement/Shipper liên quan đã hội tụ canonical
  `BaseResponse`, và năm typed client có 11 regression test cho success,
  `status=0`, false/null data và body rỗng. Reactor tám module cùng inventory
  161/161, JDK 17 baseline và polyrepo contract gate PASS. Không chạy emulator.
- [x] `delivery_web` owner DTO boundary (2026-07-27): service Restaurant/Menu/
  Order/Rating parse runtime payload trước khi cập nhật context; loại toàn bộ
  `return response.data` và nhánh status giả sau canonical interceptor. Parser
  khóa ID, enum, COD, price, timestamp, page và map chính xác backend
  `latitude/longitude` sang `addressLat/addressLng`. ESLint, Vite TypeScript build,
  diff check và polyrepo gate 161/161 PASS; chưa chạy browser theo checkpoint
  policy.
- [x] Backend unused-order-read follow-up (2026-07-27): ba arbitrary identity
  endpoint Order không có caller đã được gom vào compatibility controller và
  tắt mặc định; web/customer chỉ còn self-scoped page endpoint. Full Order
  75/75, Compose và inventory gate PASS; không ảnh hưởng app runtime.
- [x] Promotion response DTO follow-up (2026-07-27): public/admin responses đổi
  từ JPA `Voucher` sang stable `VoucherResponse` nhưng giữ nguyên field contract;
  Web admin parser không đổi. Promotion 27/27 PASS, không chạy app/browser.
- [x] Search DTO/dead Shipper discovery follow-up (2026-07-27): Restaurant/Dish
  dùng stable HTTP DTO, dish price là `BigDecimal`; Shipper Elasticsearch API,
  index và sync graph đã xóa vì zero caller và admin/matching có authority riêng.
  Search 17/17, Flutter Search contract 5/5 + analyzer thư mục sạch; không chạy app.
- [x] Backend response-envelope follow-up (2026-07-27): Auth/Settlement named
  factory khóa đúng `data/message`, Flash Sale error không còn phát
  `status=400|404` và unexpected exception không leak detail. Ba full backend
  suite lần lượt 38/38, 34/34, 24/24; inventory 160/160, baseline và root
  polyrepo contract gate PASS. Không cần chạy lại client vì JSON field contract
  giữ nguyên và root static parser/hidden-route scan đã xanh; không chạy app,
  browser hoặc emulator.
- [x] Notification reliability/security follow-up (2026-07-27): listener bỏ raw
  Kafka payload khỏi log; duplicate key khác immutable payload bị từ chối 409,
  không trả lại notification của account khác và không gọi FCM. Clean Notification
  suite 43/43 PASS; baseline có freshness guard cho Surefire report, inventory
  160/160 và polyrepo contract gate PASS. Không chạy app/emulator.
- [x] Restaurant decision actor/fingerprint backend follow-up (2026-07-27): web
  confirm/reject giữ nguyên endpoint/payload public; controller backend lấy
  authenticated user ID để phát `actorUserId`. Producer từ chối contradictory
  replay và Order dùng actor đó cho `cancelledBy`. Clean Restaurant 99/99, Order
  75/75; baseline, inventory 160/160, Compose và polyrepo contract gate PASS.
  Không cần chạy lại client hoặc emulator vì public HTTP contract không đổi.
  Runtime Kafka/PostgreSQL replay field mới còn ở final Gate B8 checkpoint.

## Decisions

- 2026-07-26: Client thích nghi với backend contract; không mở API hidden để giữ
  compatibility UI.
- 2026-07-26: Raw WebSocket chỉ cho location participant; status dùng REST +
  durable notification. gRPC/STOMP không thuộc MVP.
- 2026-07-26: COD-only; UI/payment/reservation chưa có recovery proof bị disable.
- 2026-07-26: Làm shipper trước customer/web vì current-offer recovery và
  lifecycle là dependency của customer tracking E2E.
- 2026-07-26: Flutter customer delivery status không có socket riêng; màn hình
  active refresh `GET /api/deliveries/order/{orderId}` mỗi 15 giây, còn raw
  WebSocket chỉ mang shipper location. Subscription gửi cả backend delivery ID
  và shipper ID để participant authorization không dựa trên order ID giả.
- 2026-07-26: Flutter mobile dùng `IOWebSocketChannel` để đặt Authorization ở
  handshake. Browser JWT query/subprotocol chưa có authority và không được tự
  thêm; web customer target không thuộc proof hiện tại.
- 2026-07-26: Web admin shipper là read-only. `ShipperResponse.userId` không có
  authority chứng minh là Auth account ID, nên bỏ block/unblock thay vì gửi nhầm
  ID; danh sách all dùng Spring Page `.content`, online dùng array canonical.
- 2026-07-26: Restaurant decision là eventual-consistency boundary. Sau
  confirm/reject, web bounded-refresh tối đa ba lần để không giữ nút `PENDING`
  trong lúc Order/Saga hội tụ; UI hiển thị đủ canonical matching states thay vì
  raw `WAIT_SHIPPER_CONFIRM`.
- 2026-07-26: Flutter Android phải bật core-library desugaring theo dependency
  `flutter_local_notifications 20.1.0`; dùng `desugar_jdk_libs 2.1.4` đúng README
  package local. Không hạ dependency để né native build failure.
- 2026-07-26: Unauthorized là local session-expiry boundary, không phải full
  logout. Interceptor phải clear token + phát một callback idempotent; không gọi
  FCM unregister/profile cleanup từ callback này. Chỉ logout do người dùng chủ
  động mới dùng authenticated cleanup trước khi xóa token. Auth/public và cleanup
  request không được khởi động refresh flow; log mạng chỉ giữ metadata đã bỏ query.
- 2026-07-26: Hạn chế chạy mobile emulator trong các wave còn lại vì Pixel_9a
  cold-start debug tạo ANR/performance noise. App work tiếp tục bằng analyzer,
  unit/widget/contract test và native build; chỉ dùng máy thật hoặc một phiên
  ngắn nếu checkpoint cuối thực sự cần runtime proof cho GPS/FCM/background
  socket.
- 2026-07-26: Customer profile không được giữ mock/fallback production. Màn main
  đã bỏ ảnh Unsplash, tên “Chef Amber”, brand/stats giả và payment entry rỗng;
  chỉ hiển thị identity backend. Profile update dùng canonical `PUT /api/users`;
  graph `/user/avatar` không tồn tại và không có caller đã bị xóa end-to-end.
- 2026-07-26: Auth mobile dùng persisted UUID làm device ID, mặc định
  `deviceType=MOBILE`, role social customer là `USER`, register truyền `fullName`;
  không gửi IP `127.0.0.1` hay device ID cố định. Hive adapter registry idempotent
  sau khi integration runner lộ double registration khi gọi `app.main()` lặp.
- 2026-07-26: Shipper refresh interceptor không bắt 401 của login/register/
  social-login. Auth bootstrap giữ lỗi gốc; chỉ protected request mới
  single-flight refresh. Device smoke lộ lỗi sai credential từng bị che thành
  `Missing refresh token`; regression policy test khóa hành vi mới.
- 2026-07-26: Shipper UI không được tự suy diễn financial policy hoặc hiển thị
  mock production. Khi canonical earnings thiếu, UI hiển thị chưa có dữ liệu;
  fake GPS và screen/type graph không caller bị loại sau zero-call-site proof.
  Self-registration chỉ mở lại khi có profile onboarding atomic/recoverable.
- 2026-07-26: Web không phụ thuộc Tailwind CDN trong production. Utility CSS,
  theme token và forms plugin được build cùng artifact bằng Tailwind 3/PostCSS;
  HTML chỉ còn Google Fonts/Material Symbols là public asset provider.
- 2026-07-26: Web lint gate phải cover `js/jsx/ts/tsx`. TypeScript ESLint
  recommended + React Hooks được bật; `no-explicit-any` và unused symbol là lỗi.
  Chỉ `set-state-in-effect` bị tắt vì portal chủ động fetch remote data trong
  effect; `exhaustive-deps`, purity và memoization vẫn giữ.
- 2026-07-26: Giữ React Router `7.18.1`. `npm audit --omit=dev` còn một advisory
  cho RSC/server-action processing (`>=7.12 <8.3`) nhưng portal là client-only
  `BrowserRouter`, không import RSC/data-router server API; downgrade `7.11`
  mở lại nhiều advisory XSS/RCE/redirect nên bị bác bỏ. Theo dõi bản vá upstream.

## Validation

- Backend invariant khi phát hiện nghi vấn contract: `mvn test`,
  `scripts/verify-build-baseline.sh`, `scripts/verify-http-api-inventory.sh`,
  `scripts/verify-compose-config.sh`.
- Flutter: `fvm flutter analyze`, `fvm flutter test`, generated-code consistency,
  service/adapter/widget action tests; device run is final sanity only.
- Web: `npm run verify` gồm lint, action-contract gate và production build; browser
  smoke is regression-only.
- React Native: `npm run verify` gồm TypeScript, ESLint và Jest service/slice/
  contract tests; Android/iOS runtime is final sanity only.
- Cross-client: root polyrepo contract scan, backend COD/failure harness evidence,
  web action contracts, customer Flutter logic/widget tests and shipper service/
  slice tests. Network source scan must only show Gateway/public providers, not
  direct backend ports.

## Result

Completed 2026-07-29 after final no-emulator validation.

Backend đã freeze cho MVP và ba client đã đồng bộ theo Gateway/canonical
contract. Unsupported hoặc chưa có authority surfaces đã bị ẩn/xóa khỏi runtime
navigation/source guard: gRPC, STOMP/SockJS, direct service ports, duplicated
`/api/api`, Firebase Chat, livestream, payment/VNPay, settlement self-service,
merchant promotion/flash checkout, arbitrary tracking và các mock/fake graph
không còn là MVP path.

Final no-emulator validation:

- root `scripts/verify-mvp-polyrepo-contract.sh` PASS.
- `backend_delivery/scripts/verify-build-baseline.sh` PASS.
- `backend_delivery/scripts/verify-compose-config.sh` PASS.
- `backend_delivery/scripts/verify-http-api-inventory.sh` PASS with 146 mapped
  controller methods.
- `delivery_web npm run verify` PASS; web action-contract gate covers owner/admin
  login/logout, restaurant confirm/reject, menu/profile mutations, admin rating/
  coupon/flash-sale actions, read-only shipper admin and hidden graph cleanup.
- `shipper_app2 npm run verify` PASS with `16/16` suites and `61/61` tests,
  covering auth/session, current offer recovery, accept/reject/cancel assignment,
  lifecycle status updates, notification and WebSocket contract.
- `delivery_app fvm flutter analyze` PASS and `delivery_app fvm flutter test`
  PASS with `135/135` tests, covering auth/session, Gateway contracts, order/
  checkout/address/rating/search/tracking adapters and widget action paths.

Per user decision, mobile emulator/device runtime is not an acceptance gate.
Android/iOS authenticated journey, GPS/background permission and real map/socket
behavior remain final device sanity only, not remaining MVP implementation work.
