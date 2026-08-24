# Execution Plan: Task 22 — FCM native và background wake-up

Date: 2026-07-30

## Status

Completed on 2026-07-31 after the reopened completion audit repaired the
token-refresh/logout race in both clients, customer bootstrap token churn,
shipper CocoaPods/native linking, backend high-priority/APNs wake configuration,
Spring constructor wiring and provider-error log hygiene. All executable gates
in scope pass; real-device delivery remains the optional deploy-environment
sanity check defined by the task.

## Outcome

Customer Flutter và shipper React Native đăng ký FCM token đúng theo authenticated
session, xử lý foreground/background/terminated wake-up qua platform adapter và
không dùng push payload làm source-of-truth. Shipper luôn recover offer bằng
`GET /api/deliveries/offers/current`; customer refresh durable inbox/order state.
Nếu mất hoặc trùng push, polling/startup/foreground recovery vẫn hội tụ đúng và
không tạo mutation nghiệp vụ lặp.

## Context

- Product authority: `docs/product/overview.md`,
  `docs/product/features/delivery-matching.md`, `docs/ARCHITECTURE.md`.
- Backend authority: `backend_delivery/docs/services/notification_service.md`,
  `backend_delivery/docs/http-api-inventory.md` và
  `backend_delivery/docs/system-contract-inventory.md`.
- Existing client proof: `docs/plans/completed/client-testability-refactor.md`.
- Backend hiện có durable inbox + FCM best-effort; push data gồm
  `notificationId`, `type`, optional `relatedEntityId/relatedEntityType`.
- Defect đã xác nhận: inventory ghi FCM self endpoint dành cho Flutter/shipper,
  nhưng `FirebaseController` chỉ allow role `USER`; phải allow exact set
  `{USER, SHIPPER}` và tiếp tục deny admin/shop-owner.
- Customer đã import Firebase Messaging nhưng service còn construct SDK/global
  trực tiếp, background handler chỉ log, dedup chưa có, token refresh không bỏ
  token cũ và permission/platform behavior chưa qua port.
- Shipper chưa có Firebase Messaging dependency/native composition hoặc token
  API; bounded current-offer polling và startup/foreground recovery đã xanh.

## Scope

In scope:

- Backend role repair cho exact FCM register/unregister routes; không đổi payload
  hay ownership model Redis.
- Native messaging/local-notification adapters cho Android/iOS và pure session/
  wake coordinator cho cả hai app.
- Register, refresh/replace, unregister và local token invalidation theo session.
- Persistent bounded dedup ledger theo positive `notificationId`; background
  handler chỉ ghi wake signal, application layer mới fetch canonical REST state.
- Shipper wake dispatch `fetchCurrentOffer`; customer wake refresh inbox/order
  providers. Startup, foreground và polling fallback giữ độc lập với push.
- Focused tests cho permission, token lifecycle, duplicate, foreground,
  background, expired offer, logout/relogin và restart recovery.

Out of scope:

- Dùng FCM payload để accept/reject/update delivery hoặc dựng offer/order.
- Thay durable inbox, current-offer endpoint, polling hay location WebSocket.
- Commit Firebase credentials (`google-services.json`,
  `GoogleService-Info.plist`, APNs key) hoặc dựng secret giả.
- Bắt emulator/device làm acceptance gate; real device chỉ sanity cuối khi có
  credential hợp lệ.

## Policy And Invariants

1. **Wake-only:** handler không parse giá/địa chỉ/expiry để mutate domain. Offer
   popup chỉ xuất hiện sau response canonical của `/offers/current`.
2. **Session ownership:** token chỉ POST khi access session hoạt động. Logout
   unregister token thiết bị hiện tại trước khi revoke/clear auth; session expiry
   không còn auth thì delete native token để Firebase invalidate nó.
3. **Token rotation:** khi SDK phát token mới, unregister last-synced token rồi
   register token mới; last-synced token nằm trong local adapter storage và
   không bao giờ log.
4. **Multiple devices:** mỗi thiết bị đăng ký token riêng trong backend set;
   logout một thiết bị không xóa token thiết bị khác.
5. **Reinstall:** local ledger/token state mất và token mới được register. Token
   cũ được backend dọn khi Firebase trả `UNREGISTERED`; app không có authority để
   unregister token cũ đã mất.
6. **Duplicate:** positive `notificationId` là dedup identity. Persistent bounded
   ledger chặn cùng notification ở foreground/background/open/restart. Missing
   hoặc malformed identity fail-closed; polling/foreground recovery vẫn chạy.
7. **No sensitive logging:** không log token, message data, notification body,
   auth headers hoặc raw provider error chứa payload.
8. **Fallback:** shipper polling/startup/app-foreground fetch không phụ thuộc
   permission, token registration hay push delivery.

## Approach

1. Sửa backend exact role set và thêm controller contract tests.
2. Tạo pure push contracts/core + fake native/backend/storage adapters trước;
   khóa token/session/dedup/wake semantics bằng unit tests.
3. Flutter: tách Firebase/local notification khỏi core service; background
   entrypoint chỉ enqueue wake; bind auth transition và Riverpod refresh.
4. Shipper: thêm RN Firebase native adapter, token REST client, persistent wake
   ledger; bind Redux session lifecycle và `fetchCurrentOffer` wake callback.
5. Test restart/current-offer recovery, expired/duplicate push và chứng minh
   polling/reconnect vẫn tồn tại khi push unavailable.
6. Chạy focused/full gates ba repo bị ảnh hưởng, root contract gate, log/global
   scan và cập nhật docs/plan.

## Risks And Recovery

- Backend worktree đang có nhiều thay đổi ngoài Task 22. Chỉ chạm
  `FirebaseController` và test trực tiếp; không format/rewrite module rộng.
- RN Firebase cần native package và credential ngoài repo. Package/native build
  wiring được verify tĩnh/build nếu môi trường cho phép; credential thiếu được
  fail-closed và ghi thành sanity prerequisite, không hạ test gate.
- Background isolate/headless JS không được dùng UI/store global. Chỉ enqueue
  bounded signal trong persistent adapter; foreground composition consume sau
  khi auth bootstrap hoàn tất.
- Nếu token unregister lỗi, logout vẫn phải xóa local session và native token;
  backend token cũ sẽ bị provider dọn qua `UNREGISTERED`. Không giữ auth secret
  chỉ để retry unregister.
- Rollback theo từng repo: bỏ client adapter/composition và backend exact role
  patch; polling/current-offer behavior không bị thay đổi.

## Progress

- [x] Đọc workflow, product matching và notification contract.
- [x] Audit backend role/token ownership/push payload và current-offer recovery.
- [x] Audit customer FCM implementation và xác nhận các global/lifecycle gap.
- [x] Audit shipper polling/session composition và xác nhận chưa có native FCM.
- [x] Backend exact role contract.
- [x] Customer token/session/background/foreground/dedup implementation + tests.
- [x] Shipper native adapter/token/session/wake implementation + tests.
- [x] Full validation và completion audit theo requirement matrix.
- [x] Serialize token refresh/logout and guard async wake work by authenticated
  session generation in both clients.
- [x] Preserve the customer installation token while auth bootstrap is still
  undecided; cleanup only on an explicit logout/session-expiry transition.
- [x] Repair shipper iOS target/linking, lock CocoaPods Firebase dependencies
  and prove the generated workspace with an iOS simulator build.
- [x] Add Android HIGH/APNs content-available wake policy, sanitize provider
  failures across logging boundaries and prove Spring application wiring.

## Decisions

- 2026-07-30: Giữ backend Redis model một user có nhiều token và reverse owner
  một token chỉ thuộc một account; đây là authority sẵn có cho multi-device.
- 2026-07-30: Dùng persisted `notificationId` làm dedup identity vì backend push
  contract đã phát field này; không invent dedup từ title/body/hash payload.
- 2026-07-30: Background native handler không gọi domain mutation và không dựng
  Redux/Riverpod graph; nó chỉ enqueue wake để consume sau auth bootstrap.
- 2026-07-30: Unregister thất bại không được giữ user trong app. Client luôn xóa
  native token/local session; backend stale membership được Firebase
  `UNREGISTERED` cleanup theo contract hiện có.
- 2026-07-30: Emulator/device không phải acceptance gate; SDK thật chỉ được claim
  sau optional real-device sanity với credential ngoài repo.
- 2026-07-30: Native credential thiếu phải fail-closed về REST/polling recovery;
  Gradle shipper chỉ apply Google Services plugin khi credential file tồn tại.
- 2026-07-30: Full Flutter APK gate phát hiện Riverpod create/cancel-order bị
  generator hạ thành sync notifier. `build()` được khóa bằng `FutureOr<T?>` và
  focused order journey chứng minh single-submit/failure/retry trước khi APK
  build lại. Retrofit logout dùng `BaseResponseDto<void>` để giữ generated code
  analyzer-clean. Đây là gate-support repair, không đổi product policy Task 22.
- 2026-07-30: Polyrepo gate phát hiện concurrent flash-sale source có thêm
  internal `quote` mapping; inventory được đồng bộ cơ học 156→157 method, không
  đổi runtime hay public client contract.
- 2026-07-31: Token operations are serialized and tagged with a session
  generation. Logout invalidates the session first, waits behind an in-flight
  refresh, then unregisters every token that completed registration during the
  race; live wake callbacks re-check the same generation after async dedup.
- 2026-07-31: Customer startup `authenticated: false` is an undecided bootstrap
  state, not logout authority. It preserves the native/persisted token until
  auth bootstrap emits an explicit transition.
- 2026-07-31: Shipper iOS uses the actual Xcode target `test1`, static Firebase
  frameworks and a committed Podfile lock. RN 0.80's pinned fmt 11.0.2 is
  patched only inside generated Pods for the Apple Clang/Xcode 26.4 consteval
  incompatibility, pending a React Native fmt upgrade.
- 2026-07-31: Firebase delivery failures retain a stable retry exception but do
  not attach the raw provider exception; otherwise downstream Kafka/HTTP stack
  logging could expose provider request metadata. Error code and user ID remain
  available for safe diagnostics.

## Validation

- Backend focused PASS: 7 tests cover exact USER/SHIPPER authorization,
  ADMIN/SHOP_OWNER denial, reverse token ownership, multi-device behavior,
  Android HIGH priority, APNs priority/push type/content-available and sensitive
  log contracts. Full `mvn -q -pl notification-service test` PASS 59/59,
  including Spring context and Flyway/JPA validation after constructor wiring.
- Customer focused push service PASS 11/11, including the in-flight token
  refresh/logout race and undecided-auth bootstrap preservation. Supporting
  adapter, native config, persistent dedup and Riverpod wake coordinator tests
  also pass.
- Customer full PASS: `fvm flutter analyze` has no issues;
  `fvm flutter test --coverage` passes 216/216 tests; coverage policy PASS at
  3487/11841 reachable handwritten lines (29.45%, Wave 0 has no global floor).
  `fvm flutter build apk --debug` creates
  `build/app/outputs/flutter-apk/app-debug.apk`.
- Shipper full PASS: `npm run verify:coverage` passes typecheck, lint, 31/31
  suites and 117/117 tests; statements 79.29%, branches 69.52%, functions
  74.88%, lines 81.44%. The focused race proves logout removes both the old
  token and a rotated token whose registration completed in flight.
- Shipper Android PASS: `./gradlew assembleDebug`, 469 actionable tasks and
  `BUILD SUCCESSFUL`; RN Firebase App/Messaging 26.0.0 are autolinked and the
  build remains fail-closed when deploy credentials are absent.
- Shipper iOS native PASS: `pod install` resolves 86 dependencies/107 pods,
  including RNFBApp 26.0.0, RNFBMessaging 26.0.0 and FirebaseMessaging 12.15.0.
  `xcodebuild` against `ios/test1.xcworkspace`, scheme `test1`, generic iOS
  Simulator and `CODE_SIGNING_ALLOWED=NO` exits 0. Dependency warnings remain
  non-fatal; the fmt consteval failure is covered by the reproducible Podfile
  post-install patch.
- iOS config PASS: `plutil -lint` for customer and shipper Info.plist plus
  Debug/Release entitlements. Background remote notification and APNs
  entitlement are declared; no real delivery claim is made without device
  credentials.
- Root contract PASS: inventory aligned 157 mapped methods; client transport,
  hidden route và unsupported capability scans sạch.
- Hygiene PASS: `git diff --check` ở backend, customer và shipper; push source
  scan không có console/logger/print/debugPrint token/raw-payload logging, and
  provider delivery exceptions are sanitized before downstream stack logging.

## Requirement Matrix

| Requirement | Executable/observable proof | Result |
|---|---|---|
| FCM chỉ wake, không mutate domain từ payload | Pure parser + background ledger tests; shipper callback dispatch canonical fetch; customer coordinator chỉ invalidate REST providers | PASS |
| Shipper lấy offer canonical | Wake callback → `fetchCurrentOffer()` → `deliveryService.getCurrentOffer()`; null/expired response không mở popup | PASS |
| Polling/startup/foreground độc lập FCM | Existing initializer, foreground hook và bounded poll tests/full suite | PASS |
| Token register/refresh/remove + session boundary | Customer/shipper token backend/core tests; refresh/logout race tests; backend exact-role/ownership tests | PASS |
| Foreground/background/terminated/restart | Persistent dedup/pending tests ở hai client | PASS |
| Duplicate không lặp side effect | Positive `notificationId` ledger, foreground/background/restart cases | PASS |
| Android/iOS native capability | Hai Android debug builds; shipper CocoaPods + iOS workspace build; native config tests; plist/entitlement lint | PASS |
| HIGH/background wake policy | Firebase message factory test asserts Android HIGH, APNs priority 10/alert and content-available | PASS |
| Không lộ token/raw payload qua log | Static tests + final source scan + sanitized provider exception boundary | PASS |
| Không bắt emulator/device làm gate | Unit/integration/static/build proof; device sanity optional | PASS |

## Result

Hai client đã có testable push architecture theo ports/adapters thay vì SDK
global trong application core. Token lifecycle bám authenticated session, wake
được dedup bền vững qua restart, background handler chỉ enqueue identity tối
thiểu và UI luôn hội tụ từ REST source-of-truth. Shipper chỉ hiển thị offer sau
`GET /api/deliveries/offers/current`; customer refresh durable inbox/order
providers. Polling/startup/foreground fallback vẫn tồn tại và native credential
không được đưa vào repo.

The reopened audit additionally proves that a late token refresh cannot survive
logout, startup auth bootstrap does not churn the installation token, shipper
iOS really links Firebase through its CocoaPods workspace, and backend wake
messages carry explicit Android/APNs delivery priority without leaking raw
provider failures.

Unresolved operational sanity duy nhất: FCM/APNs delivery thật trên device cần
credential/project entitlement hợp lệ của môi trường deploy. Việc này không làm
yếu acceptance proof về application semantics và không phải gate bắt buộc theo
scope đã duyệt.
