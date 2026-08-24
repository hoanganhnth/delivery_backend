# Execution Plan: Chuẩn hóa toàn hệ thống Delivery

Date: 2026-07-22

## Status

Active — post-MVP/history only; MVP backend + client acceptance completed
2026-07-29.

> Identity notes in this historical roadmap that say Gateway validates JWT or
> recreates X-User-Id/X-Role predate the 2026-08-08 JWKS migration. The current
> authority is `jwks-auth-migration.md` and backend ADR 0001: Gateway only routes,
> rate-limits and strips those headers; each resource service validates JWKS JWT.

### Current execution frontier (2026-07-29)

- Backend MVP contract/Gate B8: green trên 17 service; HTTP inventory aligned
  với 146 mapped controller methods sau khi xóa legacy Order read/mutation và
  zero-consumer Auth admin account-list
  controllers, COD happy/failure/replay, raw WebSocket
  participant + publisher convergence và settlement invariants đã có runtime
  proof. Production-scale hardening vẫn ở roadmap riêng, không chặn MVP.
- Hidden surface runtime proof bổ sung: `AnalyticsServiceApplicationTests`
  xác nhận `DashboardController`/listener/reconciliation job không được khởi tạo
  khi `app.analytics.processing-enabled=false`; `DashboardResponse` không còn
  giữ `topMenuItems` placeholder vì chưa có source-of-truth pipeline;
  `SettlementServiceApplicationTests` và `FakePaymentProfileIsolationTest`
  xác nhận `FakePaymentController`/provider chỉ sống trong `test|dev` profile và
  gateway đã deny route `/fake-confirm`.
- User block/unblock projection hardening: internal Auth→User block/unblock nay
  dùng transaction + pessimistic user row lock, giữ command idempotent nhưng
  không để concurrent projection mutation ghi đè nhau ngoài kiểm soát.
- Auth admin block/unblock hardening: Auth cũng dùng pessimistic account row
  lock trong transaction trước khi đổi `isActive`, revoke sessions và sync User
  projection, giảm race giữa hai lệnh admin ngược chiều.
- Auth admin account-read hardening: `/api/auth/accounts/{id}` now also enforces
  ADMIN at controller level (with SecurityContext fallback for authenticated
  direct calls), so Gateway/admin route and service boundary no longer disagree
  on who may read account details.
- User current-profile header hardening: `GET/PUT /api/users` now fail closed
  when trusted Gateway identity headers are missing, rather than pushing null
  identity into the service layer.
- User current-profile update hardening: `PUT /api/users` now also requires the
  trusted Gateway role header, so read/write stay on the same trust boundary.
- Auth sessions hardening: `GET /api/auth/sessions` now returns 401 when the
  security context is unexpectedly absent, instead of dereferencing a null
  authentication object.
- Polyrepo contract gate rerun sau analytics cleanup:
  `scripts/verify-mvp-polyrepo-contract.sh` PASS; inventory/transport/hidden-route
  scans sạch và controller inventory khớp current state.
- Order → Delivery lifecycle checkpoint: Delivery clean test `68/68` sau khi
  sửa `assignedAt` chỉ được gán tại transition `WAIT_SHIPPER_CONFIRM →
  ASSIGNED`; không còn gán timestamp khi row mới ở `FINDING_SHIPPER`. Thay đổi
  chỉ là lifecycle code, không cần migration; các row lịch sử có
  `assigned_at` sai không được tự động rewrite và cần được xử lý riêng nếu
  product/ops yêu cầu.
- Match/Saga convergence checkpoint: đã chặn delayed acceptance của shipper đã
  bị reject trong cả `FINDING_SHIPPER` và `SHIPPER_FOUND` (rematch đã tìm được
  offer mới). Saga clean test `50/50` pass, không failure/error. Clean compile
  trước đó lộ Lombok processor không được khóa ở Saga; POM nay khai báo
  `maven-compiler-plugin` + annotation processor rõ ràng để không phụ thuộc
  artifact incremental. Kafka/PostgreSQL concurrency rehearsal vẫn để ở Gate
  B8, chưa coi unit/H2 proof là runtime proof.
- [x] Match cancellation offer-release slice: reservation nay dùng Lua atomic
  với ownership hai chiều `shipper → delivery` và `delivery → shipper`, đồng
  thời kiểm cancellation tombstone trong cùng operation. `stop-matching` ghi
  tombstone trước rồi release exact ownership; cancellation sau reserve nhưng
  trước publish cũng release, stale release không xoá generation khác, và
  BUSY/AVAILABLE status cleanup cả hai chiều. Match clean suite chạy `46` test,
  thêm opt-in Redis integration `2/2` PASS với serializer thật; fixture key đã
  cleanup và Redis container test đã dừng. Không scan key, không cần migration.
- Auth→User provisioning checkpoint: Auth không còn tin riêng `user.id` mà bắt
  response phải echo đúng immutable `authId/email/role`; unique-email race của
  password/social registration resume account thắng thay vì tạo lỗi giả.
  User projection nay fail-closed ở cả service và DB migration nếu internal
  provisioning cố gắn cùng email cho authId khác, để giữ email/auth identity
  đồng bộ với Auth. Public
  registration/social không được tạo mới `SHIPPER` khi chưa có atomic Shipper
  profile onboarding, nhưng existing operator-provisioned SHIPPER vẫn social
  login được. Auth clean `44/44`, User focused provisioning/context/migration
  tests xanh. PostgreSQL concurrent registration vẫn OPEN; clean test không được
  dùng để tuyên bố distributed runtime proof.
- Auth↔User block/unblock recovery checkpoint: Auth admin block/unblock nay ghi
  source-of-truth state + durable pending projection marker trong Auth
  transaction, revoke sessions ngay, rồi chỉ sync User projection after-commit
  hoặc qua scheduled retry. Account chưa linked `userId` không bị tạo pending
  work vĩnh viễn; scheduler ghi lỗi từng record và tiếp tục batch; after-commit
  failure vẫn bubble để caller retry, còn pending marker giữ recovery. Version
  guard chặn stale retry clear nhầm khi admin đảo trạng thái liên tiếp; User
  command vẫn idempotent và internal-only. Evidence ngày 2026-07-28: Auth focused
  recovery/migration/context tests, Auth full test, User full test, Gateway full
  test, build-baseline, Compose config, polyrepo contract, `verify-runtime-startup.sh`
  và PostgreSQL query xác nhận đủ Auth V2 columns/index đều PASS. Live outage
  retry rehearsal còn OPEN vì cần tạo/chạm dữ liệu runtime test.
- JWT/secrets fail-fast checkpoint: auth-service và api-gateway không còn
  default implicit PEM classpath trong main config hoặc constructor injection;
  `jwt.*.path` mặc định blank và chỉ boot khi operator/test cung cấp path rõ
  ràng. Test context tự sinh RSA key tạm thay vì phụ thuộc PEM main resources.
  Evidence ngày 2026-07-28: Auth full test, Gateway full test,
  `scripts/verify-build-baseline.sh`, `scripts/verify-compose-config.sh`,
  `scripts/verify-mvp-polyrepo-contract.sh`, `verify-runtime-startup.sh` và
  focused no-fallback scan đều PASS.
- Constructor-injection cleanup checkpoint: production source không còn
  `@Autowired` field injection. Bốn class có hai constructor (một constructor
  production và một package-private test seam) giữ explicit `@Autowired` trên
  constructor production để Spring chọn đúng; clean Notification `44/44`, Auth
  `44/44`, Saga `50/50` và Order `75/75` đều xanh. Không thay đổi runtime
  contract hay persistence.
- Shipper-not-found notification checkpoint: Delivery nay ghi canonical
  `delivery.status-updated(SHIPPER_NOT_FOUND)` trong cùng transaction với status
  terminal, để Notification báo đúng customer; replay command không save hoặc
  phát event trùng. Delivery `70/70`, Notification `45/45`, Order `75/75` xanh;
  Kafka runtime replay của nhánh terminal này vẫn OPEN và chưa được suy ra từ
  unit/H2 proof.
- Order→Delivery pricing/location checkpoint: valid distance formula giữ nguyên,
  nhưng missing/NaN/out-of-Vietnam coordinate không còn âm thầm trả minimum fee;
  Delivery không còn tự điền phí `15.000`. Create command khóa positive fee,
  zero discount, exact total, COD và đủ coordinate canonical. Dead Order helper
  tính shipper earnings `80%` đã xóa vì zero-call-site và mâu thuẫn ledger
  `85/15`. Order `80/80`, Delivery `72/72` xanh.
- Order→Notification identity checkpoint: consumer `order.created` bắt buộc
  stable event ID, positive order/customer ID và canonical `restaurantName`;
  Notification không còn thay tên thiếu bằng chuỗi giả `"Nhà hàng"`. Payload
  malformed fail-closed trước dispatch và không ACK để retry/DLT xử lý.
  Notification clean `46/46`, không failure/error; Kafka runtime poison/replay
  của riêng invariant này vẫn OPEN.
- Restaurant operating-hours checkpoint: cache validation đọc đúng cặp field
  canonical `openingHour/closingHour` mà serializer ghi ra (trước đó đọc nhầm
  `openTime/closeTime`, khiến giờ mở cửa bị bỏ qua). Giữ nguyên policy MVP cũ:
  nhà hàng không cấu hình đủ cặp giờ vẫn được coi là mở; overnight-hours là
  policy chưa được chốt. Restaurant clean `103/103`, không failure/error.
- Restaurant menu-status checkpoint: internal checkout validation chỉ chấp nhận
  enum `AVAILABLE`; `SOLD_OUT`, `DISCONTINUED` và status thiếu đều fail-closed,
  không còn suy ra availability bằng cách loại vài chuỗi không tồn tại trong
  enum. Menu item cũng phải echo đúng restaurant ownership và có canonical
  price hữu hạn, dương; Order consumer kiểm lại giá dương trước khi tạo order.
  Restaurant clean `107/107`, Order `81/81`, không failure/error.
- Checkout-preview canonicalization checkpoint: `order-service` preview nay dùng
  cùng internal Restaurant validation endpoint + `Internal-Token` với create
  order, không còn tự lấy public restaurant/menu catalog để tính gần đúng.
  Thiếu pickup coordinate/menu fact canonical hoặc item unavailable đều
  fail-closed; không fallback phí ship `0`. Order clean `85/85`.
- Promotion calculate role checkpoint: route `POST /api/promotions/calculate`
  vẫn hidden khỏi Gateway và checkout flag off, nhưng controller nay cũng bắt
  `USER` trước khi override body `userId` nếu capability được bật sau này. Focused
  promotion authorization test PASS.
- Restaurant decision-row fingerprint checkpoint: confirm/reject producer nay
  lưu deterministic SHA-256 `payload_fingerprint` ngay trên
  `restaurant_order_decisions`, không chỉ trong outbox payload. Duplicate replay
  cùng decision nhưng đổi actor/prep-time/notes/reason bị reject kể cả khi outbox
  đã prune; exact replay vẫn idempotent. Restaurant clean `109/109`.
- Order cancel exact-replay checkpoint: public cancel vẫn loại `SHIPPER` và dùng
  row lock + outbox, nhưng retry cùng actor/reason sau khi order đã `CANCELLED`
  nay no-op trả state hiện tại thay vì lỗi/ghi event mới. Replay khác actor hoặc
  khác reason bị reject để không phá audit trail; cancel timestamp được set trước
  khi enqueue outbox. Order clean `87/87`.
- Restaurant decision eligibility race checkpoint: internal Order endpoint dùng
  pessimistic order row lock trong transaction trước khi trả pending eligibility,
  giảm stale `PENDING` khi customer/owner/admin cancel đang giữ lock. Đây không
  thêm API public mới; cross-service gap sau eligibility vẫn thuộc Kafka/runtime
  replay proof. Create-order idempotency key chưa có product/client contract nên
  giữ OPEN thay vì tự thêm header mới. Order clean `87/87`.
- Restaurant rating duplicate checkpoint: rating submit vẫn chỉ cho `USER` có
  delivered order eligibility, nhưng duplicate theo `order_id` nay fail ổn định
  bằng conflict `409/status=0` ở cả pre-check sequential và DB constraint race.
  Service dùng `saveAndFlush` để bắt duplicate trước khi cập nhật aggregate rating,
  không còn rơi về generic 500. Restaurant clean `112/112`.
- Promotion/FlashSale hidden checkout checkpoint: Wave C audit xác nhận voucher
  calculate/reserve và flash-sale reserve/merchant registration vẫn hidden hoặc
  disabled đúng MVP. Gateway không route promotion calculate/reserve/merchant
  create hay flashsale internal reserve/merchant item; service-level guard kiểm
  role/credential trước flag/validation, không gọi reserve service khi disabled.
  FlashSale stock/Redis và merchant controller không tạo bean mặc định. Evidence
  2026-07-29: Promotion `28/28`, Flashsale `24/24`, Gateway route-security
  `13/13` PASS.
- Delivery accepted-event/ETA checkpoint: bỏ ETA `30` phút tổng hợp khi tạo
  delivery và không dùng `estimatedPickupTime` để ghi nhầm thành delivery ETA.
  `delivery.shipper-accepted` được thu gọn còn các identity/notes mà Saga và
  Order thực sự dùng; stable `eventId/occurredAt` tiếp tục do outbox cấp.
  Delivery `75/75`, Saga `50/50`, Order `81/81`; toàn bộ gate build/API/
  Compose/polyrepo đều xanh.
- Delivery→Settlement pricing invariant checkpoint: bỏ `MIN_SHIPPER_EARNINGS`
  giả làm vỡ tổng phí; mọi phí dương dùng đúng split `85% + 15% = 100%`, phí
  thiếu/không dương fail-closed. Delivery `75/75`, Settlement `36/36`, không
  failure/error.
- Tracking location payload hardening checkpoint: thiếu `accuracy/speed/heading`
  giữ `null` thay vì bịa `0`; optional telemetry nếu được gửi phải hữu hạn ở cả
  REST fallback và raw WebSocket; `isOnline` null/sai kiểu bị reject trước khi
  ghi Redis/Kafka. Raw WebSocket không log body thô khi thiếu `action`; Redis
  publisher lease có regression proof rằng offline grace kiểm generation chứ
  không so nhầm active session value. Evidence 2026-07-29: focused Tracking
  boundary tests PASS, Tracking `34/34`, Gateway route-security `13/13`,
  build-baseline và polyrepo contract PASS. Match Redis integration cùng ngày
  thêm proof tombstone/freshness: offline xóa shipper khỏi candidate set, online
  replay cũ không resurrect, online update mới hơn khôi phục eligibility; Match
  report `53/53`, Redis opt-in `4/4`. Runtime Kafka broker replay/reorder proof
  `backend_delivery/scripts/verify-match-location-replay.sh` PASS: online event
  được Match consume vào Redis, offline tombstone mới hơn xóa GEO/online, online
  replay cũ không resurrect và online update mới hơn khôi phục projection.
- Saga canonical matching checkpoint: state `DELIVERY_CREATED` bắt buộc có
  persisted `delivery.created.result`; restaurant-confirmed không còn được dùng
  làm fallback cho delivery/location payload mà nó không sở hữu. Corrupt/legacy
  row fail-closed để transaction retry/recovery xử lý. Saga `51/51` xanh.
- Notification delivery-status display checkpoint: Delivery producer không sở
  hữu `shipperName` trong `delivery.status-updated`, nên Notification không còn
  tự điền placeholder `"Shipper"` khi field vắng. Listener truyền `null`, service
  dùng message generic không chứa `null`/tên giả; nếu producer thật sự gửi
  `shipperName` thì vẫn giữ để hiển thị. Manual send vẫn internal-only, inbox
  self-owned, FCM token ownership giữ bằng Redis Lua. Evidence 2026-07-29:
  focused boundary tests PASS, Notification `48/48`, Gateway route-security
  `13/13` PASS.
- Delivery reject/cancel-assignment replay checkpoint: Wave D audit phát hiện
  `REJECT` và `cancel-assignment` đã commit nhưng retry trước khi Saga rematch có
  thể trả lỗi vì `shipperId` đã được clear. Delivery nay giữ
  `offeredShipperId + offerExpiresAt=null` như last rejected/cancelled shipper
  marker khi status về `FINDING_SHIPPER`; exact retry cùng actor/reason trả state
  hiện tại và không phát duplicate `delivery.shipper-rejected`/status event. Gateway
  route-security cũng khóa rõ `POST /api/deliveries/cancel-assignment` là route
  SHIPPER và legacy assign/admin/internal delivery routes vẫn hidden. Evidence
  2026-07-29: focused Delivery boundary tests PASS, Delivery `77/77`, Gateway
  route-security `13/13`, build-baseline và polyrepo contract PASS.
- Runtime harness SHIPPER fixture closeout: public Auth registration tiếp tục cấm
  tạo mới `SHIPPER`, nhưng `scripts/seed.sh` và failure matrix nay dùng
  auth-service one-shot operator runner chỉ cho fixture SHIPPER, đi qua
  AuthService + User internal provisioning thay vì SQL/public self-register.
  Seed cũng đưa các shipper fixture cũ `shipper+*@test.dev` offline qua API trước
  khi tạo shipper mới để Match không offer nhầm. Evidence 2026-07-29: Auth full
  `51/51` PASS, seed standalone PASS, `scripts/verify-mvp-cod-flow.sh` PASS với
  order/delivery `21`, và `scripts/verify-mvp-failure-matrix.sh` PASS với
  rejected `24`, not-found `25`, rematched `26`. Build baseline nay guard rằng
  runtime harness không được quay lại public `/api/auth/register` cho role
  `SHIPPER`, và SHIPPER fixture runner không được mở rộng sang ADMIN.
- Restaurant dead-validation graph closeout: polyrepo search xác nhận legacy
  `ValidateOrderRequest/OrderValidationResponse/OrderValidationService` chỉ tự
  tham chiếu và không có controller/client/test consumer; graph đã xóa. Endpoint
  internal canonical `/api/restaurants/validate/order` vẫn dùng
  `OrderCacheValidationService` và Restaurant `107/107` xanh.
- Client implementation/static gates rerun 2026-07-29 không dùng emulator:
  Flutter analyzer + 120/120 test + debug APK build PASS, Web lint/build PASS
  với warning chunk lớn/browserslist cũ, Shipper typecheck/lint + 16 suite/55
  test + Android `assembleDebug` PASS; không chạy emulator thường xuyên theo
  quyết định user.
- Web Chat/Firebase MVP cleanup checkpoint: `delivery_web` không còn mount
  `ChatProvider`, `ChatWidget`, `/admin/chat` hoặc `/restaurant/chat` trong
  `App.tsx`, admin nav hay restaurant nav; toàn bộ `src/modules/chat`,
  `AdminChatPage`, `src/config/firebase.ts`, `src/types/chat.types.ts` và
  dependency `firebase` đã bị loại khỏi source/dependencies vì chưa có Firebase
  Auth/custom-token và Firestore rules proof. Root contract gate có guard mới
  chặn bật lại route/nav hoặc khôi phục graph Chat/Firebase trong MVP. Evidence
  2026-07-29: `delivery_web` `npm run lint && npm run build` PASS, production JS
  bundle còn `401.57 kB`, source scan không còn Chat/Firebase match và
  `scripts/verify-mvp-polyrepo-contract.sh` PASS; inventory count mới nhất được
  ghi ở Order legacy API closeout checkpoint bên dưới.
- Web usecase/action proof checkpoint: `delivery_web` now has
  `scripts/verify-action-contracts.mjs` wired into `npm run verify`. The gate
  checks owner/admin login/logout, restaurant confirm/reject, menu/profile
  mutations, admin rating/coupon/flash-sale actions, read-only shipper admin,
  hidden MVP graph cleanup, no STOMP/SockJS, no direct service ports and no
  `/api/api` endpoint literal. Evidence 2026-07-29: `delivery_web npm run verify`
  PASS and root `scripts/verify-mvp-polyrepo-contract.sh` PASS.
- Order legacy API closeout checkpoint: legacy Order read/mutation controllers,
  `UpdateOrderRequest` và capability flags không còn trong source/config; Compose
  không set `ORDER_LEGACY_MUTATION_API_ENABLED` hoặc
  `ORDER_LEGACY_READ_API_ENABLED`. HTTP inventory giảm từ 154 xuống 147 mapped
  controller methods. Evidence 2026-07-29: `mvn -q -pl order-service test`,
  `backend_delivery/scripts/verify-http-api-inventory.sh`,
  `backend_delivery/scripts/verify-compose-config.sh`,
  `backend_delivery/scripts/verify-build-baseline.sh` và
  `scripts/verify-mvp-polyrepo-contract.sh` đều PASS.
- Shipper reachability/state/tracking cleanup đã xanh; current-offer wake-up cho
  MVP đã được chốt bằng bounded polling fallback nên không cần Firebase native
  config/FCM SDK để tiếp tục system E2E. FCM native còn là tối ưu dài hạn sau
  MVP.
- No-emulator system acceptance rerun PASS: root
  `scripts/verify-mvp-polyrepo-contract.sh`, `delivery_web npm run verify`,
  `shipper_app2 npm run verify` (`16/16` suites, `61/61` tests),
  `delivery_app fvm flutter analyze`, and `delivery_app fvm flutter test`
  (`135/135`) all pass without launching emulator/device. Runtime ADMIN
  fixture/API proof and browser smoke already PASS; mobile device/iOS remains
  final sanity only, not an acceptance gate.

### MVP acceptance blocker ledger (current)

Các checkbox lịch sử bên dưới vẫn giữ để truy vết audit dài ngày; không dùng
chúng để đếm task MVP hiện tại nếu phần Current execution frontier đã có proof
mới hơn. Tính đến 2026-07-29, MVP no-emulator acceptance đã đạt; hai execution
plan chính đã chuyển sang `docs/plans/completed/`.

| Blocker | Status hiện tại | Authority/next action |
|---|---|---|
| ADMIN fixture/API proof | Public self-register ADMIN vẫn bị chặn đúng policy; operator-only ADMIN runner có code/test/baseline proof và runtime smoke đã PASS: Gateway login, `GET /api/users` role `ADMIN`, block cleanup. | Không còn blocker riêng; dùng fixture tạm mới cho UI smoke và cleanup bằng block. |
| Admin browser smoke | PASS; owner browser smoke PASS; authenticated dashboard/surface smoke PASS qua Gateway với fixture operator-only; browser console sạch. | Regression-only. |
| Cross-client usecase/action proof | PASS no-emulator: backend COD/failure/runtime harness, root contract scan, web action contracts/build, shipper service/slice contracts and customer Flutter logic/widget tests. | Regression-only unless backend/client contract changes. |
| Mobile device limitation | Android builds/static gates PASS; iOS authenticated smoke chưa có proof mới. | Not an acceptance gate; ghi limitation/final sanity path trong closeout. |
| Final closeout | PASS; `backend-refactor-and-client-verification.md` và `mvp-client-alignment.md` đã chuyển sang `docs/plans/completed/`. | Regression-only unless contract changes; không cần thêm emulator proof. |

### Remaining execution order

1. Keep backend freeze as regression-only unless a new client/runtime defect
   proves a backend regression.
2. Keep client handoff as regression-only in the documented order:
   `shipper_app2` -> `delivery_app` -> `delivery_web` -> cross-client E2E.
3. Keep dead-code and hidden-surface cleanup tied to client/runtime proof; do
   not reopen APIs unless a surviving consumer or explicit product authority
   exists.
4. Use browser proof for `delivery_web` when useful. Mobile emulator/device is
   final sanity only, not the default acceptance proof.

### Next backend waves

#### Wave A - Foundation closeout

- Complete the missing HTTP matrix entries for every controller surface still
  marked open.
- Lock Docker/config parity across ports, datasource, Kafka, Redis and internal
  URLs.
- Verify JWT/secrets bootstrapping fails fast with readable errors and no git
  fallback.
- Recheck Gateway allow/deny coverage against the inventory, including admin
  and internal routes.
- Normalize exception mapping, request validation, correlation and logs.
- Add the missing Testcontainers/startup/readiness proof so a clean environment
  boots without manual repair.

Exit: backend starts clean from config alone and the remaining inventory rows are
fully classified.

#### Wave B - Identity boundary

- Reaudit `auth-service` and `user-service` as one boundary, not two unrelated
  modules.
- Prove register/login/refresh/logout/session/social/admin transitions through
  Gateway.
- Verify path ownership and account linkage for profile/address/admin routes.
- Keep account-lookup/test surfaces hidden unless a real consumer survives.
- Audit order inside the wave:
  - `auth-service`: register/login/social/refresh/logout/session/admin block-unblock,
    RSA key loading, session rotation, and mounted-secrets fail-fast.
  - `user-service`: current profile/address/admin read, internal create/by-auth,
    block/unblock propagation, and any direct path-ID mutation that bypasses JWT.
- Exit proof: focused auth/user tests, Gateway route/security checks, and the
  crash-window / internal-linkage proof required by `system-contract-inventory`.

Exit: identity becomes a trusted contract for downstream checkout and delivery.

#### Wave C - Checkout boundary

- Reaudit `restaurant-service`, `order-service`, `promotion-service` and
  `flashsale-service` together because they share checkout state.
- Keep canonical order/menu/price/status data server-owned.
- Prove owner checks for restaurant confirm/reject and disable unsupported
  reserve/payment paths rather than reopening them.
- Validate normal checkout, voucher, flash sale, reject, cancel and duplicate
  request behavior.
- Audit order inside the wave:
  - `restaurant-service`: menu/status/ownership, operating-hours, order
    confirm/reject, validation, ratings, and cache/location dead surfaces.
  - `order-service`: checkout preview/create/cancel/my-orders/detail, legacy
    dashboard/mutation paths, and duplicate/replay handling.
  - `promotion-service`: collect/my/public/admin reads, calculate boundary, and
    hidden reservation/write paths.
  - `flashsale-service`: public/admin reads, merchant/internal hidden paths, and
    disabled checkout reservation.
- Exit proof: checkout-focused suites plus confirm/reject, cancel, duplicate,
  and disabled-path integration checks through Gateway.

Exit: checkout is canonical and no hidden capability is needed for MVP.

#### Wave D - Fulfilment boundary

- Reaudit `delivery-service`, `shipper-service`, `tracking-service`,
  `match-service`, `notification-service` and `saga-orchestrator` in one wave.
- Prove offer contention, expiry, retry, cancel-assignment, rematch and raw
  WebSocket participant behavior.
- Keep delivery/status transitions sequential and fail-closed.
- Remove orphan commands or listeners only after zero-call-site evidence.
- Audit order inside the wave:
  - `delivery-service`: current-offer, accept, cancel-assignment, status,
    detail/history, and legacy assign/cancel-all/STOMP branches.
  - `shipper-service`: profile/update/online, self ratings, admin list, and
    deleted write/delete/location graphs.
  - `tracking-service`: raw WebSocket participant auth, publisher/session fence,
    tombstone/offline, and dead REST diagnostics.
  - `match-service`: offer contention, retry, cancellation tombstones, Redis
    atomic ownership, and controllerless command flow.
  - `notification-service`: durable inbox, FCM token ownership, dedup/retry, and
    hidden STOMP/WebSocket surfaces.
  - `saga-orchestrator`: command ordering, idempotency, timeout/rematch, and
    failure replay.
- Exit proof: delivery/match/notification/saga focused suites plus Redis/Kafka/
  raw-WebSocket runtime rehearsal, including reject, no-shipper, cancel-assignment
  and replay cases.

Exit: delivery, tracking, notification and saga converge on the same terminal
state under replay and failure.

#### Wave E - Settlement boundary

- Reaudit `settlement-service` only after delivery completion contracts are
  frozen.
- Prove ledger precision, idempotency, duplicate replay and cancellation/
  completion ordering.
- Keep self-service money surfaces hidden until ownership and reconciliation are
  executable.
- Audit order inside the wave:
  - `settlement-service`: exact balances/transactions, withdrawal read/admin,
    hold/release/deposit, COD ledger, and all payment/VNPay/fake-confirm hidden
    surfaces.
- Exit proof: settlement replay/reconciliation suites plus duplicate and
  ordering rehearsals against the delivery completion contract.

Exit: COD accounting is stable and replay-safe.

#### Wave F - Closeout

- Remove remaining dead controllers, DTOs, topics, configs and query hazards.
- Refresh docs, OpenAPI/service notes and event schema inventories.
- Run final static, dependency/security and full backend test gates.

Exit: backend freeze evidence is current and client handoff can start with no
open backend ambiguity.

Các checkbox phase phía dưới là checklist intake chi tiết và có thể được đóng bởi
evidence trong `Progress` xuất hiện muộn hơn; frontier này là danh sách việc còn
lại hiện hành, không dùng checklist lịch sử để suy ra task còn mở.

## Outcome

Rà soát, sửa và kiểm chứng toàn bộ 17 backend service trước; sau đó đồng bộ lần
lượt `delivery_app`, `delivery_web` và `shipper_app2` với contract backend đã
chốt. Khi hoàn tất:

- mọi HTTP API, Kafka event và WebSocket endpoint đều có owner, consumer, quyền
  truy cập, contract và bằng chứng kiểm thử rõ ràng;
- API không còn dùng được phân loại và ẩn khỏi Gateway hoặc loại bỏ có kiểm soát;
- luồng COD cơ bản chạy thật từ đăng ký đến settlement, kể cả cancel, reject,
  timeout, rematch và retry;
- mỗi service build/test độc lập được và toàn cụm Docker có smoke/E2E proof;
- ba client không còn hard-code contract khác backend và các journey chính chạy
  được qua Gateway/socket public đã chuẩn hóa.

Plan này là nguồn điều phối duy nhất cho chương trình audit/refactor. Plan
`backend_delivery/docs/plans/active/mvp-completion.md` chỉ còn là nguồn lịch sử
đầu vào và không được dùng để mở một luồng triển khai song song.

## Context

- `docs/ARCHITECTURE.md`, `docs/product/overview.md`: kiến trúc và luồng cấp hệ thống.
- `backend_delivery/docs/product/overview.md`: service map và invariant backend.
- `backend_delivery/ROADMAP_MVP_TO_PRODUCTION.md`: backlog cũ cần kiểm chứng lại.
- `docs/product/features/`: product behavior đã verify một phần.
- Code, test, Docker Compose và runtime log là bằng chứng cuối cùng; tài liệu cũ
  không thắng source/runtime khi có mâu thuẫn.

Baseline ngày 2026-07-22: 17 module backend, 190 HTTP handler mapping, 38 Kafka
listener; phần lớn service chỉ có context test. Compile/package các module lõi
đã qua, nhưng test suite chưa xanh độc lập vì còn phụ thuộc PostgreSQL/Redis thật,
Mockito self-attach và cấu hình môi trường. Các blocker đã thấy gồm Kafka
`localhost` hard-code, Redis/container URL lệch, status/event/socket không đồng
bộ, command Saga không có consumer, và một số API admin/dev đang public sai.

## Scope

In scope:

- Audit và refactor cả 17 backend service, Gateway, Docker, database schema,
  Kafka/WebSocket contract, security, validation, error model và test.
- Kiểm kê mọi controller route; phân loại `public-client`, `public-admin`,
  `internal`, `dev-only`, `deprecated`, `dead`.
- Kiểm kê mọi Kafka topic theo producer/consumer/schema/idempotency/retry/terminal
  behavior; xử lý topic hoặc command không có đầu nhận.
- Chuẩn hóa luồng COD MVP trước, sau đó verify các capability phụ hiện có.
- Sau backend gate, audit và sửa cả ba client theo contract thật.
- Cập nhật product/service docs và decision record khi contract hoặc policy đổi.

Out of scope until the backend correctness gate passes:

- gRPC tracking; vị trí MVP dùng raw WebSocket.
- Scale/production platform như Kubernetes, Eureka, Prometheus/Grafana và tracing.
- Bổ sung feature mới chưa tồn tại. Feature hiện có vẫn phải được phân loại và
  chứng minh, ẩn hoặc loại bỏ đúng quy trình.

## Authority And Working Rules

1. Không sửa hàng loạt theo style trước khi biết behavior. Mỗi wave đi theo
   `inventory -> contract -> focused fix -> tests -> integration proof -> docs`.
2. Không xóa API chỉ vì chưa thấy client gọi. Chỉ xóa khi không có consumer,
   không thuộc product intent, không có internal/event dependency và đã search
   toàn polyrepo. Nếu chưa đủ bằng chứng, ẩn khỏi Gateway và ghi deprecated.
3. Public API chỉ đi qua Gateway. Internal API không có route public và phải có
   service-level authentication. Dev/test controller chỉ chạy dưới profile `dev`.
4. Không cho client tự gửi `X-User-Id`/`X-Role`; Gateway strip các header legacy
   và chuyển tiếp Bearer token nguyên trạng. Resource service tự validate JWT qua
   JWKS rồi dựng actor/role; Gateway không tái tạo hoặc inject identity header.
5. Database và event consumer phải idempotent ở boundary có retry. Không ack và
   nuốt lỗi trước khi có terminal handling rõ ràng.
6. Trạng thái order/delivery public dùng một vocabulary được tài liệu hóa;
   persistence dùng enum, JSON giữ string ổn định.
7. Mỗi wave giữ thay đổi nhỏ, có checkpoint riêng và rollback được. Không ghi đè
   thay đổi đang có trong dirty worktree nếu chưa xác định ownership.
8. Backend phải qua Gate B8 trước khi bắt đầu sửa contract client. Client chỉ
   được đọc/audit sớm để tìm dependency, không dùng client hiện tại làm authority.

## Master execution protocol (2026-07-26)

Đây là kế hoạch thực thi dài hạn cho toàn bộ chương trình. Không triển khai
client mới hoặc mở lại API legacy chỉ để làm cho UI hiện tại chạy được trước khi
backend contract tương ứng đã được chứng minh.

### Thứ tự bắt buộc cho mỗi service

1. **Snapshot:** ghi branch/diff, module build, cấu hình runtime, migration,
   controller, listener/producer, scheduled job, WebSocket và dependency gọi
   sang service khác.
2. **Contract map:** lập bảng `surface -> actor -> owner -> consumer ->
   classification -> source of truth -> existing proof`. Consumer không được
   suy ra chỉ từ tên method; phải có call-site polyrepo, event listener hoặc
   product/ops authority.
3. **Threat and correctness review:** kiểm tra auth/role/ownership/IDOR,
   validation, state transition, transaction/outbox, retry/idempotency, timeout,
   pagination/query bound, null/error handling và secret/config.
4. **Decision trước khi sửa:** `keep`, `restrict`, `hide-at-gateway`,
   `dev-only`, `deprecated` hoặc `delete`. API có consumer nhưng contract mơ hồ
   phải dừng ở đây để chốt authority; không dùng configurable default làm policy.
5. **Implementation slice:** sửa một boundary nhỏ, giữ migration/recovery
   rollback được, không trộn cleanup không liên quan.
6. **Proof:** focused unit/context test, persistence/concurrency test khi có DB,
   contract/authorization test, event replay/failure test và runtime smoke qua
   Gateway hoặc internal credential đúng boundary.
7. **Closeout:** cập nhật inventory, event/state matrix, docs, plan evidence và
   polyrepo zero-call-site search; chỉ sau đó mới chuyển service kế tiếp.

### Backend service execution order and exit criteria

| Wave | Service/boundary | Phạm vi phải verify/refactor | Exit evidence |
|---|---|---|---|
| B0 | Gateway, Compose, shared config | route allow-list/method, JWT identity stripping, internal secret, health/readiness, env/ports, dependency versions | clean render, route matrix, spoof/role/401/403 tests, clean startup |
| B1 | `auth-service` + `user-service` | registration provisioning, login/social, refresh rotation, logout/session revoke, block propagation, profile/address ownership, pagination | PostgreSQL integration, race/replay proof, identity E2E qua Gateway |
| B2 | `restaurant-service` | restaurant/menu ownership, public catalog, rating eligibility, confirm/reject, internal order validation, cache invariants | owner/IDOR/validation tests, order validation integration, decision outbox proof |
| B2 | `search-service` | index source/event sync, public search, stale/delete behavior, query bounds, shipper search classification | producer/listener matrix, rebuild/delete proof, public route contract |
| B3 | `order-service` | checkout canonical price/menu snapshot, COD-only policy, status enum/transition, cancel ownership/state, outbox, restaurant views | transaction rollback, duplicate/reordered event, IDOR, concurrent cancel/confirm |
| B3 | `promotion-service` + `flashsale-service` | classify unsupported checkout paths; retained admin/public reads; reserve/stock/expiry only if authoritative | hidden/deprecated proof for unsupported paths; otherwise atomic reserve/concurrency/compensation proof |
| B4 | `delivery-service` | one-offer accept/reject/current recovery, assignment ownership, cancel-assignment, sequential status, participant reads, outbox | two-accept race, expiry/rematch, same-state retry, invalid transition, COD lifecycle |
| B4 | `tracking-service` | Redis GEO/heartbeat, raw WS auth, participant subscription, shipper self-publish, disconnect/TTL cleanup, Kafka replication | 401/403/authorized WS, stale/offline convergence, reconnect and terminal cleanup |
| B4 | `shipper-service` | self profile, online state as read-model, active-delivery uniqueness, history/rating read, admin pagination | owner/role tests, duplicate active assignment, page bounds, availability event proof |
| B4 | `match-service` | availability source, nearest offer, exclusion, timeout, retry/DLT, no-shipper terminal state | no duplicate offer, stale heartbeat exclusion, Kafka retry/replay and terminal proof |
| B4 | `notification-service` | durable inbox, FCM token ownership, offer/status notification, unread/read/delete bounds, internal send | event→DB idempotency, restart recovery, self-only reads, no STOMP public dependency |
| B4 | `saga-orchestrator-service` | command/event graph, missing consumer detection, state convergence, compensation and replay | topic matrix zero orphan, restart/replay/failure matrix, order/delivery convergence |
| B5 | `settlement-service` | COD ledger only, delivery.completed identity, exactly-once logical entries, admin read surface; hide payment/self-service until proof | concurrent/reordered/duplicate completion, ledger reconciliation, money precision |
| B6 | `analytics-service`, `livestream-service` | classify product/operational authority and consumer; keep isolated from MVP if unproven | no public route/consumer leak, disabled-bean or explicit experimental proof |
| B7 | backend-wide closeout | remove dead controller/DTO/repository/topic/config, query bounds/N+1, common errors/logging, docs/schema | full inventory consistency, static/security/dependency gates, 17/17 build/test |

Mỗi dòng là một gate độc lập. “Service test xanh” không đủ để đóng dòng nếu
chưa có boundary proof; ngược lại, API không có consumer không bị xóa ngay nếu
chưa có zero-call-site + event/internal dependency + product authority proof.

### Backend gates

- **B0:** clean environment renders; only Gateway is public in base Compose;
  JWT/internal-token and readiness checks are observable.
- **B1:** identity/account linkage, role propagation and ownership are proven
  against real persistence, including failure recovery.
- **B2/B3:** catalog and COD order are server-owned; restaurant decision and
  cancellation cannot create orphaned order/resource reservations.
- **B4:** one canonical fulfilment state machine, one offer at a time, tracking
  participant boundary and notification recovery are proven under retry/restart.
- **B5:** `delivery.completed` produces stable COD ledger cardinality under
  duplicate, reorder and concurrent delivery completion.
- **B7:** every HTTP/Kafka/WebSocket surface is classified, every unresolved
  quarantine item has an owner, and full backend runtime harness is green.

### Client handoff rule

Only after B7/B8 is green do we migrate clients in this order:

1. `delivery_app`: auth/profile/address → public catalog/search → COD checkout →
   order history/cancel → REST status refresh + raw participant location.
2. `delivery_web`: owner restaurant/menu → confirm/reject; then admin read-only
   surfaces with a valid ADMIN fixture and no unsupported mutations.
3. `shipper_app2`: auth/online → durable current offer → accept/reject/cancel
   assignment → lifecycle → raw location → history/reconnect.
4. Cross-client UI E2E: customer order → owner decision → shipper recovery/accept
   → location → delivered → notification/history, followed by failure journeys.

For every client phase, first create a call-site/DTO/status matrix and contract
tests; then implement; then run device/browser E2E through Gateway. A green unit
suite or backend harness alone never closes the client or system gate.

### Definition of done for the whole objective

The program is complete only when all 17 backend service rows, B0–B7 gates and
the client handoff rule have executable/observable evidence; hidden/dead APIs are
classified with a recorded reason; app/web contract search is clean; and the
cross-client happy/failure journeys pass without direct service ports, gRPC or
STOMP. Until then this plan remains Active.

## Approach

### Phase 0 — Baseline và bản đồ contract

- [x] Ghi snapshot branch/status/diff cho từng repo; phân biệt thay đổi đã có với
  thay đổi của chương trình này và loại archive lớn khỏi phạm vi commit.
- [x] Lập inventory 17 service: port, DB, Redis/Kafka dependency, controller,
  Gateway route, producer/consumer, scheduled job, socket, external provider.
- [ ] Lập HTTP matrix cho toàn bộ endpoint: actor, auth, input, output, pagination,
  owner check, consumer, classification và test hiện có.
- [x] Lập event matrix: topic, key, producer, consumer group, schema, ordering,
  retry, idempotency, failure/DLQ behavior và event không có đầu nhận.
- [x] Lập state-machine matrix cho auth session, order, delivery, shipper
  availability, payment và settlement.
- [x] Chạy baseline package/test cho từng module, Docker config validation và
  lưu lỗi thực tế vào Progress; không coi context test là proof nghiệp vụ.
- [x] Chốt canonical error envelope, pagination envelope, timestamp/timezone,
  money precision, role names và ID semantics trước wave service đầu tiên.

Exit gate P0: không còn endpoint/topic/socket nào chưa xuất hiện trong inventory;
mọi lỗi baseline được phân loại code/config/environment/test-harness.

### Phase 1 — Runtime foundation và API edge

- [x] **Dependency baseline:** 17 module dùng Spring Boot `3.5.15`, Gateway dùng
  Spring Cloud `2025.0.3` + WebFlux server starter, JDK 17; verifier chặn version
  drift tại `backend_delivery/scripts/verify-build-baseline.sh`.
- [ ] **Docker/config:** thống nhất port, datasource, Kafka, Redis, internal URL và
  env override; loại `localhost` khỏi code chạy trong container.
- [ ] **JWT/secrets:** preflight key local, mount/env production-like, fail-fast
  có thông báo rõ; không đưa key/secret trở lại git.
- [ ] **Gateway:** đối chiếu route với controller inventory; bảo vệ toàn bộ admin
  route, route WebSocket chuẩn, không public internal/dev endpoint.
- [ ] **Cross-cutting:** chuẩn hóa exception mapping, request validation,
  correlation ID tối thiểu và log; loại `System.out`/stacktrace tùy tiện.
- [ ] **Test harness:** tạo test profile/Testcontainers cho PostgreSQL, Kafka và
  Redis; sửa Mockito/JDK setup để test chạy lặp lại không cần service local.
- [ ] Thêm startup/smoke proof cho từng container và dependency readiness; Compose
  không được báo healthy khi dependency quan trọng chưa dùng được.

Exit gate P1: toàn cụm khởi động từ môi trường sạch; Gateway, Kafka, Redis, DB và
JWT hoạt động qua config thật; test backend có thể chạy tự động.

### Phase 2 — Identity boundary: auth, user, Gateway

- [ ] **auth-service:** audit register/login/refresh/logout/session/social/admin;
  atomicity khi đồng bộ user, token rotation/revocation, block state, DTO và lỗi.
- [ ] **user-service:** audit profile/address/admin; ownership mọi path ID, default
  address invariant, pagination và auth-account linkage.
- [ ] **Gateway:** contract test JWT hợp lệ/hết hạn/sai chữ ký, role propagation,
  spoofed header, public allow-list và admin/internal deny-list.
- [ ] Ẩn hoặc loại endpoint account lookup/test không có consumer hợp lệ; internal
  lookup phải dùng shared secret/config đúng và không nằm trong public allow-list.
- [ ] Viết integration proof: đăng ký tạo đúng auth+user, rollback/compensate khi
  user-service lỗi, login/refresh/logout/block hoạt động qua Gateway.

Exit gate P2: identity và authorization trở thành boundary đáng tin cho mọi wave sau.

### Phase 3 — Catalog và checkout boundary: restaurant, order, promotion, flash sale

- [ ] **restaurant-service:** audit restaurant/menu/rating/location/cache/order
  validation/confirm-reject; ownership, giờ mở cửa, availability và endpoint dev.
- [ ] **order-service:** canonical `OrderStatus` enum và transition; server-owned
  restaurant/menu/price data; pagination/IDOR; cancel/update/delete rules.
- [ ] Chốt transaction boundary khi reserve flash-sale/voucher và lưu order;
  đảm bảo failure không tạo order mồ côi hoặc giữ stock/voucher vĩnh viễn.
- [ ] **promotion-service:** audit create/collect/calculate/reserve, actor/owner,
  expiry, usage limit, concurrent reservation và compensation.
- [ ] **flashsale-service:** audit campaign/item/approve/reserve, stock atomicity,
  duplicate reservation, internal endpoint auth và port conflict.
- [ ] Nhà hàng confirm/reject phải verify owner và restaurantId khớp order; event
  được Saga/order xử lý idempotent.
- [ ] Contract/integration proof cho checkout thường, voucher, flash sale, hết
  stock, voucher lỗi, duplicate request, restaurant reject và customer cancel.

Exit gate P3: order được tạo từ dữ liệu canonical, tài nguyên reserve nhất quán và
không bắt đầu delivery trước policy nhà hàng xác nhận.

### Phase 4 — Fulfilment core: delivery, shipper, tracking, match, notification, saga

- [ ] **delivery-service:** audit entity/state transition, customer/restaurant/
  shipper identity, accept/reject/cancel, status update, socket và event publish.
- [x] **Delivery shipper-status boundary:** `PUT /api/deliveries/{id}/status` chỉ
  dành cho chính shipper đã được assign và chỉ nhận bước kế tiếp trong chuỗi
  `ASSIGNED -> PICKED_UP -> DELIVERING -> DELIVERED`. Bỏ quyền ADMIN vì không có
  call-site trong polyrepo và không có audit/recovery authority; không cho endpoint
  generic tạo `ASSIGNED`, rematch hoặc `CANCELLED`. Exact same-state retry phải trả
  state hiện tại mà không ghi thêm outbox/settlement event. Shipper bỏ đơn trước
  pickup phải đi qua `POST /api/deliveries/cancel-assignment`; customer/restaurant
  cancellation tiếp tục đi qua Order/Saga command chuyên biệt.
- [ ] **shipper-service:** audit profile/document/rating/online/availability,
  pagination và ownership; một shipper không nhận hai delivery active.
- [x] **tracking-service:** chỉ giữ một nguồn realtime Redis GEO + raw WebSocket;
  xác thực shipper update chính mình, busy/offline TTL và subscriber cleanup.
- [x] **Shipper availability convergence:** Match chỉ được xem shipper
  khả dụng khi Tracking/Redis còn heartbeat online mới; `shipper-service.isOnline`
  chỉ là profile/read-model, không được tự quyết matching. Chuẩn hóa offline
  tombstone Tracking -> Kafka -> Match, chốt policy disconnect grace và
  multi-device/session; runtime đã chứng minh toggle,
  explicit offline, reconnect, stale event và mất kết nối không để shipper tiếp
  tục nhận offer ngoài policy.
- [ ] **match-service:** mỗi lượt offer một shipper gần nhất; retry không giữ Kafka
  consumer offset lâu; exclusion, timeout, cancellation và terminal not-found.
- [ ] **notification-service:** audit DB/FCM/STOMP contract, token ownership,
  reconnect/offline behavior và duplicate notification.
- [x] **Shipper offer delivery:** backend có self-only
  `GET /api/deliveries/offers/current`, durable Notification inbox và FCM
  best-effort wake-up; STOMP `/topic/user/{id}` đã bị loại. Shipper app fetch
  contract này khi startup/foreground/push; live Kafka→DB→inbox/recovery,
  expiry/reconnect đã PASS trong Gate B8 và client static contract tests.
- [ ] **saga-orchestrator:** mọi command có consumer; transition/idempotency và
  compensation khớp order/delivery; bỏ command chết hoặc listener trùng.
- [ ] Persist offered shipper + expiry, khóa cạnh tranh khi accept; reject/timeout
  và cancel-after-accept giải phóng availability rồi rematch.
- [ ] Chuẩn hóa public states: `PENDING`, `CONFIRMED`, `FINDING_SHIPPER`,
  `WAIT_SHIPPER_CONFIRM`, `ASSIGNED`, `PICKED_UP`, `DELIVERING`, `DELIVERED`,
  `CANCELLED`, `SHIPPER_NOT_FOUND`.
- [ ] Proof cho happy path, không có shipper, shipper reject, offer timeout, hai
  accept đồng thời, shipper cancel, customer/restaurant cancel và socket location.

Exit gate P4: không có đơn treo; chỉ shipper được offer mới nhận được; order,
delivery, saga và availability hội tụ về cùng terminal state.

### Phase 5 — Money boundary: settlement và payment

- [ ] **settlement-service:** audit balance/transaction/withdraw/hold/release/
  deposit/recalculate/admin; ownership, precision, concurrency và audit trail.
- [ ] Kiểm chứng `delivery.completed` đủ restaurantId/shipperId/money fields và
  consumer idempotent theo từng ledger entry, không chỉ một check không atomic.
- [ ] Chốt COD accounting: shipper earnings, restaurant net, platform commission,
  COD deposit debit; duplicate/reordered event không đổi kết quả.
- [x] Audit payment endpoints/provider hiện có; toàn bộ payment/self-service money
  route bị ẩn khỏi Gateway trong COD-first MVP, gồm cả fake confirm/callback.
  Online payment chưa đạt proof được ẩn/deprecated, không quảng bá là MVP-ready.
- [ ] Proof transaction rollback, concurrent delivery completion, withdrawal
  approve/reject/reverse và balance recalculation từ ledger.

### Gate B8 runtime rehearsal

- [x] Startup harness `scripts/verify-runtime-startup.sh` kiểm tra Docker daemon,
  key parity, Compose contract, health PostgreSQL/Redis/Kafka/Elasticsearch,
  startup đủ 17 app và Gateway smoke reads.
- [x] COD harness `scripts/verify-mvp-cod-flow.sh` chạy fail-fast theo
  controller contract thật: tạo actor/catalog/shipper/deposit/location, customer
  checkout COD, restaurant confirm, quan sát durable Notification `MATCH_FOUND`,
  recover exact self-offer, shipper accept, raw-WebSocket participant proof và đi
  qua `PICKED_UP -> DELIVERING -> DELIVERED`; sau đó kiểm bốn ledger entries và
  phát lại chính payload `delivery.completed` để chứng minh cardinality không đổi.
- [x] COD harness chỉ cho shipper accept sau khi quan sát đúng notification public
  và `GET /api/deliveries/offers/current`; customer delivery polling fallback đã
  bị xóa. Java 17 probe khóa handshake 401, outsider 403, participant subscribe
  và publisher identity derive từ JWT. Clean runtime execution PASS ngày
  2026-07-26.
- [x] `scripts/seed.sh` không còn nuốt lỗi HTTP, bổ sung field shipper bắt buộc,
  dùng actor/identity unique theo run và có output JSON quyền riêng cho harness.
- [x] Chạy startup + COD harness trên Docker daemon thật: đủ 17 Spring app,
  dependency health/Gateway smoke PASS; COD order→delivery→bốn ledger entry và
  exact duplicate replay không đổi cardinality PASS. Đây là lần chạy harness cũ;
  notification/current-offer/raw-WebSocket assertions mới chưa được suy ra từ nó.
- [x] Bổ sung/chạy concurrent offer acceptance, PostgreSQL ledger replay race,
  Redis reservation/timeout và participant-authorized raw WebSocket rehearsal.
- [x] Chạy recovery/restart Kafka consumer và migration preflight trên dữ liệu có
  duplicate; chỉ freeze contract khi toàn bộ bằng chứng trên xanh.

Exit gate P5: có thể tái tính số dư từ ledger và phát lại event mà không cộng/trừ trùng.

### Phase 6 — Supporting capabilities

- [x] **search-service:** index ownership, entity-sync schema, delete/update,
  unavailable Elasticsearch và public query validation.
- [x] **analytics-service:** phân loại experimental; REST/listener/reconciliation
  off mặc định cho tới khi có idempotency, retry và dashboard ownership proof.
- [x] **livestream-service:** phân loại experimental, ẩn toàn bộ Gateway; sửa
  startup mapper nhưng chưa mở seller/restaurant/Agora boundary.
- [x] Rà soát notification/search/analytics/livestream event orphan và schema
  drift; các topic livestream inactive và Elasticsearch enabled-mode còn gate riêng.
- [x] Feature không đạt product intent hoặc không có client/operational proof được
  đánh dấu experimental, ẩn khỏi Gateway; không để route nửa hoạt động.

Exit gate P6: mọi capability phụ được chứng minh, ẩn có chủ đích hoặc loại bỏ;
không còn API “có vẻ dùng được” nhưng không có contract/consumer.

### Phase 7 — Backend-wide cleanup và contract freeze

- [ ] Refactor trùng lặp có bằng chứng: response/error types, constants, role/status
  naming, ObjectMapper/Kafka config, pagination và validation patterns.
- [ ] Loại controller/service/repository/DTO/topic/config dead sau polyrepo search và
  test; dev endpoint chuyển profile; deprecated endpoint có migration note.
- [ ] Review query N+1/unbounded `findAll`, transaction scope, null handling,
  money/time mapping, indexes và database constraints cho tất cả service.
- [ ] Cập nhật OpenAPI/service docs và JSON event schema; Gateway route inventory
  phải khớp contract freeze.
- [ ] Chạy static checks, dependency/security scan khả dụng và full backend tests.

### P1 execution slice — Transactional outbox trước Gate B8

Thứ tự này bám theo đường đi của một đơn COD và ưu tiên nơi hiện đang nuốt lỗi
publish. Không mở rộng sang voucher/flash-sale hoặc capability experimental.

1. [x] **Order outbox:** thêm migration `outbox_events`, event id/aggregate id/
   topic/key/payload/status/attempt/nextAttemptAt; ghi `order.created` và
   `order.cancelled` trong cùng transaction với Order. Publisher không được catch
   rồi chỉ log. Relay claim batch có lock an toàn nhiều instance, backoff và DLT/
   operator-visible terminal state.
2. [x] **Restaurant decision outbox:** persist confirm/reject command idempotent
   theo `orderId + decision`, verify owner/restaurant trước khi commit rồi relay
   event. Retry cùng request không được phát hai quyết định trái nhau.
3. [x] **Delivery outbox:** thay lớp outbox đang bị comment và publisher giả danh
   outbox bằng implementation thật; chuyển toàn bộ `delivery.created.result`,
   offer/accept/reject/status/cancel/picked-up/completed/shipper-availability sang
   write cùng transaction. Không còn `KafkaTemplate.send` trực tiếp trong service
   mutation path.
4. [x] **Saga command outbox:** ghi saga transition và command tiếp theo atomic;
   relay theo `orderId` để giữ ordering. Crash sau send/trước mark-sent phải tạo
   duplicate an toàn, không tạo state mới sai.
5. [ ] **Consumer proof:** mọi consumer lõi dedup bằng stable event/command id;
   Testcontainers/PostgreSQL+Kafka chứng minh DB rollback, broker down, process
   restart, duplicate delivery và relay nhiều instance. Sau đó mới chạy E2E COD
   và đánh dấu outbox gate hoàn tất.

### Gate B8 — Backend acceptance

- [x] Docker Compose dựng từ dữ liệu sạch và key local mới.
- [x] E2E COD: register ba actor -> catalog -> order -> restaurant confirm ->
  match -> notification -> accept -> location -> status -> delivered -> settlement.
- [x] E2E failure matrix: invalid auth/IDOR, reserve fail, reject/cancel/rematch,
  not-found, duplicate event, service restart và retry.
- [x] So sánh DB/order/delivery/saga/ledger/Kafka observable state sau mỗi scenario.
- [x] Không còn P0/P1 unresolved thuộc backend MVP, test quarantine không có
  owner, hoặc endpoint unclassified trong HTTP inventory. Backend contract
  freeze và Gate B8 đã được xác nhận lại ngày 2026-07-26; các mục production,
  capability phụ chưa có proof và client device E2E vẫn là backlog riêng.

### Phase 8 — Client inventory theo backend contract đã freeze

- [x] Pre-freeze read-only call-site evidence (không mở Phase 8): web vẫn gọi
  generic Order status/assign, hidden settlement self/admin mutations, payment,
  promotion merchant create, Flash Sale merchant register, Analytics và
  Livestream; Flutter vẫn gọi hidden shipper search, payment/fake-confirm và có
  Livestream feature; shipper app vẫn cancel qua Order thay vì Delivery
  `cancel-assignment`, đồng thời gọi hidden settlement/payment APIs. Giữ các mục
  này làm migration queue, chưa sửa client trước Gate B8/contract freeze. Refresh
  polyrepo ngày 2026-07-25 còn xác nhận web Tracking dùng SockJS/STOMP direct
  `localhost:8090`, shipper notification hard-code `8087`, Flutter còn REST
  location read đã bị backend loại; raw WebSocket/Gateway migration thuộc Phase
  9-11, không được dùng các mismatch này để mở lại backend legacy API.
- [x] Với từng client, map screen/use-case -> HTTP/socket endpoint -> DTO/status;
  tìm hard-code URL, mock/fallback che lỗi, API không tồn tại và feature orphan.
- [x] Chốt một shared contract checklist cho base URL, auth refresh, error envelope,
  pagination, roles, status, money/time và socket reconnect; cấm log access/refresh
  token (shipper app hiện đang log cả login và refresh response).
- [x] Xếp lỗi client theo journey/P0-P2; không refactor UI thuần style trong wave
  contract trừ khi cần cho behavior.

### Phase 9 — delivery_app

- [x] Verify auth/profile/address, catalog/search/cart/checkout, order history/detail,
  cancel, notification, delivery status và raw location socket.
- [x] Đồng bộ DTO/pagination/status/error; bỏ endpoint giả hoặc fallback im lặng;
  URL cấu hình được cho Android/iOS/device thật.
- [ ] Widget/unit/integration gate đạt 91/91, analyzer 0 issue và Android debug
  APK build PASS sau regression unauthorized/FCM/logging;
  còn authenticated smoke trên emulator/device với backend Gate B8.

### Phase 10 — delivery_web

- [x] Verify admin và restaurant route/role, catalog/menu/order confirm-reject,
  shipper/user/order admin, settlement, analytics, promotion/flashsale/livestream.
- [x] Không mở raw WebSocket arbitrary tracking cho web admin; capability này
  không có participant contract. Shipper admin list dùng Page `.content` và
  online array đúng backend.
- [x] Ẩn navigation/action của backend capability experimental/deprecated.
- [ ] TypeScript/build/lint/diff/contract-search đã xanh; còn browser E2E cho
  admin; restaurant login/dashboard/order list/confirm/menu/profile/reviews smoke
  qua Gateway đã PASS. Tailwind đã chuyển từ CDN sang local production build;
  app console logging đã loại. Vite/PostCSS/ESLint và production dependency đã
  nâng; lint nay cover TS/TSX và sửa 22 error + 9 warning bị gate cũ bỏ sót.
  Audit chỉ còn một React Router RSC-only advisory không reachable từ Vite SPA.

### Phase 11 — shipper_app2

- [x] Verify auth/profile/online, notification offer, accept/reject/timeout,
  active-delivery restore, location publish, status sequence, cancel và earnings.
- [x] Đổi action “Hủy nhận đơn” đang gọi nhầm
  `PUT /api/orders/{orderId}/cancel` sang Delivery
  `POST /api/deliveries/cancel-assignment` với DTO canonical; chỉ hiện khi
  delivery còn `ASSIGNED`, và refresh state từ response backend thay vì tự gán
  local `CANCELLED` (canonical result là rematch/FINDING_SHIPPER).
- [x] Đồng bộ history/active restore với contract Delivery đã freeze: không gửi
  `page/limit/status` nếu backend vẫn là cap-100 list, hoặc dùng pagination/filter
  contract thật nếu backend chốt bổ sung; xóa fallback
  `/api/deliveries?mine=true` không tồn tại.
- [x] Chuẩn hóa API response unwrap, env URL, bỏ STOMP notification và dùng raw location;
  loại mock/fallback che lỗi production.
- [x] Typecheck, 16 suite/55 Jest test, lint zero warning/error và Android
  `assembleDebug` đã xanh; foreground offer wake-up đã chốt bằng bounded polling
  fallback. Native device E2E ngắn với reject/rematch, background/foreground và
  reconnect socket vẫn là checkpoint cuối.

### Gate C12 — System acceptance

- [x] System acceptance đạt theo no-emulator MVP policy: không chạy journey thật
  qua mobile emulator/device làm gate; web browser smoke/action proof được dùng
  khi thực tế và mobile real-device là final sanity only.
- [x] Contract diff backend-client bằng 0 hoặc có compatibility adapter được test:
  root polyrepo contract scan, web action contracts, shipper service/slice tests
  và customer Flutter adapter/widget tests đều PASS.
- [x] Tài liệu architecture/product/service, seed/runbook và plan phản ánh đúng
  runtime MVP; execution plans chính đã ghi Result và chuyển sang completed.

Customer native P0 closeout ngày 2026-07-26: runtime phát hiện request storm
`401 -> logout cleanup -> FCM unregister -> 401` và logging làm lộ FCM payload.
Interceptor nay refresh single-flight chỉ khi request đã có Bearer token; auth
public paths và FCM unregister opt-out, retry 401 không deadlock, concurrent
session expiry clear/callback một lần. Session-expired không gọi full logout;
FCM token chỉ sync khi authenticated; network logger bỏ header/query/body.
Focused auth/security regression 10/10; thêm biometric-disposal regression đưa
full Flutter lên 91/91, analyzer 0 issue, diff hygiene và Android debug APK
rebuild PASS. Clean-data native rerun tới login không còn storm/leak/disposed
exception, nhưng Pixel_9a cold-start debug vẫn tạo ANR do JIT/Mapbox/emulator
resource pressure. Theo quyết định user, UI-authenticated journey được dời về
một checkpoint cuối ngắn, ưu tiên thiết bị thật; các wave còn lại không giữ máy
ảo chạy thường xuyên.

## Risks And Recovery

- Dirty worktree chứa thay đổi và archive lớn: luôn snapshot/status trước wave,
  patch theo vùng nhỏ, không reset/checkout thay đổi không thuộc task.
- Refactor contract làm gãy client: backend freeze trước, ghi migration mapping và
  giữ adapter tạm có expiry/test khi thực sự cần.
- Migration state/status/ledger khó rollback: backup DB, migration forward-only,
  rehearsal trên dữ liệu seed và script đối chiếu trước/sau.
- Event retry tạo side effect trùng: thêm unique constraint/idempotency trước khi
  bật retry; replay test bắt buộc.
- Phạm vi dài dễ mất context: cập nhật checkbox, evidence, decision và next action
  trong file này sau mỗi wave; không chỉ báo tiến độ trong chat.

## Progress

- [x] Đọc workflow, architecture, roadmap và plan hiện có.
- [x] Baseline sơ bộ cấu trúc 17 service/controller/listener/test.
- [x] Xác định các blocker đầu tiên của luồng order-delivery và socket/config.
- [x] Ghi runtime/Kafka/WebSocket inventory bước đầu tại
  `backend_delivery/docs/system-contract-inventory.md`; `docker compose config
  --quiet` đã qua ngày 2026-07-22.
- [x] Sinh exact HTTP inventory 190 method tại
  `backend_delivery/docs/http-api-inventory.md`; xác định các surface cần đổi ngay
  sang internal/admin/dev-only trước khi freeze.
- [x] Snapshot dirty worktree bốn repo; backend có thay đổi người dùng đang staged
  và hai archive lớn, web có thay đổi `HomePage.tsx`; không reset hoặc ghi đè.
- [x] Reactor package đủ 17/17 module bằng JDK 17; xác định Maven/JDK 25 làm Lombok
  fail và phải được xử lý như lỗi toolchain reproducibility.
- [x] Chạy test baseline theo reactor và từng module: reactor dừng tại auth do DB
  thật; toàn bộ module fail trong sandbox do external dependency hoặc Mockito
  self-attach. Gateway chạy ngoài sandbox pass 1/1, xác nhận một lỗi môi trường.
- [x] Chốt contract convention tại `docs/decisions/0001-backend-contract-conventions.md`:
  Gateway path, identity/role, response/error, pagination, VND, UTC, Kafka envelope
  và raw WebSocket cho location MVP.
- [x] Gateway edge wave đầu tiên: route admin order/delivery bắt buộc role `ADMIN`;
  bỏ public route của match; loại auth email lookup khỏi public allow-list; không
  public flash-sale internal reserve; service controller admin có role check dự phòng.
- [x] Validation sau edge waves: Gateway route 5/5 (full Gateway suite 8/8),
  order controller 1/1,
  delivery controller 1/1; reactor package JDK 17 xanh 17/17 module.
- [x] Phase 1 config wave đầu: custom Kafka config của order/delivery/match/saga/
  analytics đọc `spring.kafka.bootstrap-servers`; Compose wire đúng Redis cho sáu
  service, auth user URL, match tracking URL, promotion DB và livestream Kafka.
- [x] Chuẩn hóa memory option sang `JAVA_TOOL_OPTIONS`; thêm healthcheck cho
  PostgreSQL/Redis/Kafka/Elasticsearch và chờ `service_healthy` trước app startup.
- [x] Externalize credential Firebase/Agora; bỏ Firebase JSON khỏi source/JAR,
  notification vẫn chạy khi FCM tắt và fail-fast nếu đường dẫn credential sai.
- [x] Config proof: Kafka property 5/5, Firebase optional/fail-fast 3/3,
  `scripts/verify-compose-config.sh` xanh và reactor package JDK 17 tiếp tục xanh
  17/17 module. Docker daemon chưa chạy nên startup smoke còn mở.
- [x] API edge wave hai: notification client routes được khóa theo HTTP method,
  `POST /send` bị ẩn; tracking chỉ public shipper update/offline và numeric location
  read, fleet/busy API bị ẩn; restaurant/tracking test controller chỉ profile `dev`.
- [x] Base Compose chỉ publish Gateway; `docker-compose.debug.yml` là opt-in cho
  direct service ports. Route contract 4/4 và profile tests 2/2 xanh; local runbook
  được ghi tại `backend_delivery/docs/runbook-local.md`.
- [x] Phase 2 auth security wave đầu: xóa đường social-login parse token không
  verify; Google bắt buộc signature + configured audience + verified email;
  self-register không thể tạo `ADMIN`; account chưa provision user không được cấp JWT.
- [x] Refresh/logout dùng pessimistic lock, kiểm server-side expiry/account state
  và revoke expiry; auth request có Bean Validation; focused auth security tests
  xanh. Google social login fail-closed khi chưa có client ID.
- [x] Phase 2 identity edge wave hai: social login được route public có chủ đích;
  auth admin/account-by-id bắt `ADMIN` tại Gateway và Spring Security;
  `/sessions` bắt authentication, internal email lookup giữ shared-secret boundary;
  bearer/header không còn bị ghi log. Security MVC 4/4 và Gateway 8/8 xanh.
- [x] Phase 2 user boundary wave đầu: API auth→user linkage đã ẩn khỏi
  Gateway và bắt `Internal-Token`; profile route được thu hẹp theo method;
  address read/mutation đối chiếu owner hoặc `ADMIN` trước thao tác.
- [x] Validation identity wave: focused Auth 16/16, User 12/12, full Gateway 8/8
  và reactor package JDK 17 xanh đủ 17/17 service ngày 2026-07-23.
- [x] Dependency baseline đã normalize từ Boot 3.5.4/3.5.9/4.0.6 về
  Boot `3.5.15`; Gateway từ Cloud `2023.0.1` sang `2025.0.3` và starter
  `gateway-server-webflux`. Eureka placeholder được bỏ khỏi flashsale,
  analytics, promotion vì registry thuộc post-MVP và Compose không có Eureka.
  Full reactor package 17/17, Gateway 8/8, Auth 16/16, User 12/12 đều xanh.
- [x] Phase 2 provisioning/profile wave: auth account được persist trước remote
  user provisioning và có thể resume idempotently khi user-service lỗi; account
  chưa link user không được login. User provisioning idempotent theo `authId`,
  từ chối rebinding email/role và xử lý race unique constraint. Profile mutation
  canonical là `PUT /api/users`, trong đó User Service lấy actor từ JWT đã validate
  qua JWKS; path-ID update/delete bị ẩn khỏi edge. Default-address mutation khóa
  owner row để serialize.
- [x] Phase 3 checkout edge wave đầu: order route đổi từ `/api/orders/**` sang
  exact create/read/cancel/admin surfaces; generic update/delete và arbitrary
  user/restaurant/shipper list bị ẩn. Service khóa IDOR/direct-call fallback,
  phí ship dùng pickup canonical từ restaurant-service và MVP chỉ nhận COD.
- [x] Restaurant confirm/reject kiểm `SHOP_OWNER` thực sự sở hữu restaurant;
  order consumer đối chiếu restaurantId, chỉ transition từ `PENDING`, bỏ duplicate
  idempotently và rethrow event lỗi để Kafka không ack như thành công.
- [x] Promotion route tách user/merchant/admin theo role; calculate/reserve bị ẩn,
  reserve bắt shared internal secret. Voucher và flash-sale checkout fail-closed
  vì code hiện chưa áp discount và reservation chưa atomic/idempotent/compensatable.
  Proof: Gateway 12/12, full Order 17/17 trên isolated H2, Restaurant 3/3,
  Promotion 3/3;
  Compose verifier, diff check và reactor package 17/17 xanh ngày 2026-07-23.
- [x] Restaurant edge được hoàn thiện thêm: catalog/menu GET public theo exact
  allow-list; creator/validation/cache/location legacy không lọt qua Gateway;
  rating moderation bắt `ADMIN` ở Gateway lẫn controller. Order→restaurant
  validation fail-closed bằng shared `Internal-Token`, Compose wire cùng
  `INTERNAL_SECRET`. Focused restaurant hiện 9/9; Gateway 12/12, Order 14/14.
- [x] Checkout canonical item wave: restaurant-service trả tên/giá món từ
  cache/DB; order-service dùng đúng dữ liệu đó cho subtotal và `order_items`, từ
  chối menu ID trùng/thiếu canonical data. Clean focused Order 14/14, restaurant
  validation 3/3, build baseline + Compose verifier + diff check và reactor
  package 17/17 đều xanh.
- [x] Order state-machine wave: entity chuyển sang canonical `OrderStatus`, có
  transition table và converter alias legacy; repository/dashboard dùng enum.
  Saga command cập nhật finding/wait và không ack khi parse/transition/service
  lỗi. Generic admin status/assign bị ẩn khỏi Gateway. Migration SQL có preflight
  được thêm nhưng PostgreSQL rehearsal còn mở. Proof: Order 23/23, Gateway 12/12,
  reactor package 17/17, baseline/Compose/diff xanh.
- [x] Fulfilment offer-one wave: match chỉ lấy shipper gần nhất và phát đúng một
  candidate; delivery persist `offeredShipperId` + expiry 180 giây trước khi phát
  `delivery.shipper-offered` cho notification. Accept/reject khóa delivery row và
  chỉ shipper được offer mới thao tác được; cancel compensation bao phủ toàn bộ
  trạng thái matching. Proof: Match 6/6 và Delivery 9/9 trên isolated H2.
- [x] Delivery/Tracking edge wave: bỏ broad `/api/deliveries/**`, ẩn legacy
  `/assign`, route action/read theo method + role; USER chỉ đọc delivery có
  `creatorId` của mình. Raw location WebSocket bắt JWT tại Gateway, derive
  shipperId từ session identity, giới hạn area subscription cho ADMIN và bỏ toàn
  bộ dependency/build hook gRPC khỏi tracking-service. Proof: Gateway route 9/9,
  tracking focused 5/5; quyền subscriber USER theo đúng active order vẫn OPEN.
- [x] Fulfilment contention/timeout wave: Match reserve offer bằng Redis `SETNX`
  với TTL và loại shipper đã có offer khỏi GEO result; status BUSY/AVAILABLE release
  reservation theo delivery. PostgreSQL migration có preflight + partial unique
  index bảo đảm một active delivery/shipper. Offer timeout không còn hủy ngay mà
  rematch với exclusion, dùng chung giới hạn failure trước compensation. Proof:
  Match 10/10, Saga focused 2/2. DB guard đã được wire qua startup `schema.sql`,
  accept `saveAndFlush` trước BUSY/event và Delivery đạt 10/10; PostgreSQL
  startup/concurrency rehearsal vẫn OPEN.
- [x] Match failure-classification wave: invalid command và lỗi hạ tầng không còn
  bị ack hoặc biến thành `shipper.not-found`; chỉ typed no-candidate sau retry mới
  phát terminal business event rồi ack. Full Match 10/10. Kafka delayed retry,
  DLQ và durable scheduling vẫn thuộc phần còn lại của Phase 4.
- [x] Shipper edge wave: broad `/api/shippers/**` đổi thành self/admin exact routes;
  legacy PostgreSQL location và rating write/read chưa chứng minh ownership bị ẩn.
  Hồ sơ self bắt role `SHIPPER`, admin fleet bắt `ADMIN`, create/update có validation,
  fleet page bị cap 100 và identity/document columns có unique constraint metadata.
  Full shipper suite 5/5 trên H2.
- [x] Promotion/flash-sale safety edge: flash routes tách anonymous GET,
  `SHOP_OWNER`, `ADMIN`; internal reserve dùng shared secret. Cả voucher và
  flash-sale reserve cùng compensation listener mặc định disabled bằng explicit
  feature flag vì hiện chưa có order-scoped idempotency/partial rollback. Proof:
  Promotion 4/4, Flash-sale 4/4, Gateway 12/12, Compose/diff và reactor 17/17.
- [x] Phase 3 ownership/eligibility closeout: flash merchant `restaurantId` được
  kiểm qua internal restaurant endpoint bằng JWT owner; restaurant rating được
  kiểm qua internal order endpoint cho đúng customer+restaurant+`DELIVERED`, rồi
  mới mở transaction lưu rating. Hai endpoint fail-closed và không public.
  Proof: Restaurant 11/11, Flash-sale 5/5, Order 25/25, Gateway 14/14, Compose
  verifier, diff check và reactor package 17/17.
- [x] Settlement COD safety edge: ngắt legacy deduction ở `PICKED_UP` để tránh
  double-debit; `delivery.completed` ghi restaurant net, shipper earnings, COD
  deposit debit và SYSTEM commission trong cùng transaction, lỗi được rethrow để
  rollback thay vì commit dở. SYSTEM commission cuối là marker replay tuần tự.
  Self-service balance/withdraw/deposit/payment/fake callback đều bị ẩn, chỉ giữ
  exact admin GET-only route; VNPay credential chuyển sang env và default không
  còn FAKE. Ledger entry có DB unique business key, balance mutation khóa row và
  migration preflight không tự xóa duplicate. Proof: Settlement 9/9 gồm H2 replay
  integration, Gateway 15/15. PostgreSQL
  concurrent ledger rehearsal vẫn OPEN.
- [x] `delivery.completed` contract validation: producer chỉ phát khi delivery,
  order, restaurant, shipper IDs dương, payment method exact `COD`, shipping fee
  và total canonical hợp lệ; không còn fallback tự bịa fee/`ONLINE`. Consumer
  kiểm lại identity, COD, fee/earnings/commissions và đối chiếu platform total
  trước mọi ledger write. Clean proof: Delivery 26/26, Settlement 12/12 trên
  JDK 17. Atomic concurrent replay trên PostgreSQL thật vẫn thuộc Gate B8 nên
  checklist idempotency tổng thể phía trên chưa đóng.
- [x] Notification/Saga convergence wave: notification list/read/mark/delete scope
  theo JWT owner tới repository; event có DB unique dedup key, failure không ack và
  retry hai lần trước DLT. FCM token có Redis Lua reverse-owner atomic. Mapper
  startup lỗi được thay bằng manual component. Saga khóa row theo order, có version
  + unique orderId, transition tuần tự, duplicate reject guard; Kafka publish lỗi
  rollback/retry và failure hội tụ Order về `CANCELLED` hoặc `SHIPPER_NOT_FOUND`
  thay vì status lạ. Notification STOMP handshake bị ẩn mặc định vì MVP chỉ dùng
  raw socket cho vị trí. Proof: Notification 14/14, Saga 9/9, baseline/Compose/diff và
  reactor package 17/17. PostgreSQL/Redis/Kafka integration và transactional
  outbox DB→Kafka/WebSocket/FCM vẫn OPEN; migration tách theo database tại
  `backend_delivery/docs/migrations/2026-07-23-notification-dedup.sql` và
  `backend_delivery/docs/migrations/2026-07-23-saga-concurrency.sql`.
- [x] COD eligibility-before-offer wave: Delivery và Saga giữ canonical
  `totalPrice`/`paymentMethod=COD` qua initial match, reject và timeout rematch;
  Match duyệt nearest candidate pool rồi gọi Settlement internal API có shared
  secret, reserve/publish đúng một shipper đủ deposit. Redis/Settlement lỗi không
  bị biến thành no-candidate. Gateway không route internal endpoint. Clean proof:
  Match 14/14, Saga 9/9, Settlement 9/9, Delivery 11/11 và Gateway 15/15. Clean
  Delivery test đồng thời lộ và sửa wiring MapStruct khiến service trước đó không
  boot từ artifact sạch.
- [x] Local COD fixture không mở rộng production API: `scripts/seed.sh` lấy
  canonical shipper userId rồi append ledger entry `LOCAL_SEED_DEPOSIT` và cập
  nhật balance projection trong cùng PostgreSQL transaction, idempotent theo
  shipper. Gateway vẫn không có route fake/manual deposit; script yêu cầu cụm
  local và có thể đổi amount bằng `SHIPPER_DEPOSIT`.
- [x] Tracking participant boundary: raw WebSocket subscribe bắt `deliveryId`,
  Delivery internal endpoint có shared secret kiểm active status, assigned
  shipper và actor là customer của đơn/assigned shipper/ADMIN. Endpoint internal
  không được Gateway route; clean proof Delivery 14/14 và Tracking 8/8. Việc tự
  revoke subscription đang mở khi delivery chuyển terminal vẫn cần integration
  proof hoặc terminal-event cleanup.
- [x] Match retry lifecycle: Kafka listener trả `Mono` thay vì tự subscribe rồi
  giữ acknowledgment sau khi method return; container dùng async ack và
  `@RetryableTopic` (1s/2s/4s) đưa lỗi Redis/Settlement/parse sang retry topics
  rồi `saga.command.find-shipper.DLT`. Business no-candidate vẫn dùng bounded
  backoff và phát đúng một `shipper.not-found`. Clean Match 15/15; Kafka restart,
  ordering và DLT integration proof còn mở.
- [x] Phase 6 exposure closeout cho MVP: Search chỉ route anonymous GET nhà hàng/
  món, có query/page/size bounds; shipper search bị ẩn. Analytics và Livestream
  bị ẩn toàn bộ khỏi Gateway vì Analytics chưa enforce admin/restaurant ownership
  và còn ack-drop lỗi, còn Livestream chưa verify restaurant ownership/token
  boundary. Gateway clean 17/17 xác nhận deny-list; hai capability này là
  experimental và không chặn luồng COD cơ bản.
- [x] Supporting-service clean gate: Search 6/6, Livestream 1/1, Analytics 2/2.
  Analytics context test từng chạy DDL vào PostgreSQL local thật; đã chuyển sang
  H2 in-memory `create-drop`; REST/listener/reconciliation off mặc định bằng một
  feature flag. Livestream thay generated mapper lỗi startup bằng Spring mapper
  tường minh. Kết quả xanh chỉ là build/context proof, không mở lại route.
- [x] Order transactional outbox: create/cancel ghi event cùng transaction với
  Order, payload và Kafka header mang stable `eventId`; Flyway tạo
  `outbox_events`, relay claim batch bằng `FOR UPDATE SKIP LOCKED`, bounded
  backoff và terminal `DEAD`. Flyway V2 hỗ trợ baseline database hiện hữu; test
  migration-only tắt Hibernate DDL cùng transaction/relay proof nâng Order lên 30/30;
  PostgreSQL + Kafka crash/restart/duplicate rehearsal vẫn thuộc Gate B8.
- [x] Restaurant decision outbox: confirm/reject kiểm internal Order eligibility,
  serialize decision đầu tiên theo `orderId` bằng PostgreSQL advisory transaction
  lock, duplicate cùng decision no-op và decision đối nghịch trả conflict. Decision
  + stable-event outbox commit/rollback atomic; relay có SKIP LOCKED/backoff/DEAD.
  Full Restaurant 69/69 và Gateway 17/17; PostgreSQL/Kafka restart proof còn mở.
- [x] Delivery transactional outbox: mọi mutation event lõi (create result,
  offer/accept/reject/status/cancel/pickup/completion/shipper availability) được
  ghi cùng transaction với Delivery; mutation/listener path không còn gửi Kafka
  trực tiếp. Flyway V6 tạo schema, relay claim bằng `FOR UPDATE SKIP LOCKED`, gắn
  stable metadata, backoff và terminal `DEAD`. H2 chứng minh commit/rollback atomic,
  migration-only và relay; full Delivery 21/21 sau khi gộp test relay trùng,
  PostgreSQL/Kafka crash-window rehearsal vẫn thuộc Gate B8.
- [x] Saga command transactional outbox: mọi command kế tiếp được ghi cùng DB
  transaction với Saga transition và mang stable `eventId`. Relay chỉ claim
  command sớm nhất chưa hoàn tất trên mỗi `orderId`; retry/`DEAD` trước đó chặn
  command sau để không đảo thứ tự giữa nhiều instance. Flyway V1, migration-only,
  commit/rollback và relay metadata/backoff có proof; full Saga 14/14.
  PostgreSQL multi-relay và Kafka crash-window rehearsal vẫn thuộc Gate B8.
- [x] Core replay/contract wave: Delivery ownership nay lấy canonical
  `Order.userId` thay vì nhầm `Order.creatorId` của chủ nhà hàng; status event có
  đủ customer/shipper identity và canonical `status` cho Notification. Match
  result gắn stable `eventId`, dùng Kafka key `orderId` và propagate broker error
  để retry thay vì chỉ log; Order bỏ qua assignment replay cùng shipper. Proof:
  Delivery 21/21, Match 17/17, Order 33/33, Notification 15/15, Saga 14/14.
  Durable cross-service replay trên PostgreSQL/Kafka thật vẫn chưa đủ để tick
  consumer proof item 5.
- [x] API boundary closeout cho tracking/match/notification: bỏ public Gateway
  route đọc tùy ý vị trí shipper; REST fleet/distance/busy của Tracking chuyển
  thành internal controller off mặc định và bắt shared secret khi bật. Notification
  manual-send fail-closed bằng `Internal-Token`; Match legacy orphan-event pipeline
  off mặc định. Tracking test harness dùng subclass mock-maker để không phụ thuộc
  JVM self-attach. Proof: Gateway 17/17, Notification 17/17, Match 17/17,
  Tracking 9/9.
- [x] Order dashboard dead-route closeout: polyrepo search không có client consumer,
  Gateway không route và Analytics cũng đang experimental/off. Giữ code để migration
  nhưng controller Order chỉ tạo bean khi explicit bật
  `app.order.legacy-dashboard-enabled`; mặc định `false` có context proof.
- [x] COD-only payment exposure closeout: `PaymentController` (create/query,
  VNPay callback/IPN và fake-confirm) không tạo bean mặc định, Compose giữ
  `PAYMENT_PROCESSING_ENABLED=false`; code được giữ cho post-MVP. Polyrepo search
  xác nhận cả ba client còn reference online/fake payment, nên Phase 8-11 phải
  ẩn các hành động này và chỉ gửi `paymentMethod=COD`. Settlement clean 9/9;
  test harness dùng subclass mock-maker để không phụ thuộc JVM self-attach.
- [x] Restaurant unused-surface closeout: polyrepo search không có consumer cho
  cache warmup/mutation hoặc geocode thử nghiệm. `CacheController` và
  `LocationController` không tạo bean mặc định; Compose giữ hai capability flag
  `false`, code còn lại để ops/debug và chỉ xóa sau freeze. Boundary test đồng
  thời lộ và sửa unknown route bị đổi thành HTTP 500 thay vì 404. Hai mapper
  MapStruct không ổn định trên clean build được thay bằng Spring component tường
  minh; create/update Restaurant nay giữ đúng `image`. Clean Restaurant 72/72.
- [x] Delivery legacy assignment closeout: polyrepo search không có consumer cho
  `POST /api/deliveries/assign`; method cũ bỏ qua role và tạo delivery ngoài
  Saga/outbox. Mapping được chuyển sang compatibility controller off mặc định;
  Compose giữ flag `false`, canonical MVP chỉ tạo/offer qua Kafka Saga command.
- [x] Build-JDK verifier closeout: Homebrew `mvn` từng tự dùng JDK 25 dù shell
  `java` là 17, làm Lombok fail giữa compile trong khi baseline script vẫn xanh.
  Verifier nay kiểm cả effective Java của Maven và yêu cầu `JAVA_HOME` JDK 17;
  Delivery clean 21/21 trên Java 17.
- [x] Admin list memory-safety slice: Auth account, User admin và Shipper online
  list không còn gọi repository unbounded; compatibility response shape vẫn là
  list nhưng query cap 100. Paginated envelope đầy đủ được hoãn tới client
  contract migration để không đổi response giữa backend freeze.
- [x] Identity/shipper test-isolation slice: Auth và User context chuyển sang H2
  test profile; Auth/User/Shipper dùng subclass mock-maker để không phụ thuộc JVM
  self-attach. Legacy PostgreSQL shipper-location controller off mặc định và
  Compose explicit flag false; Tracking Redis/raw WebSocket vẫn là canonical.
  Clean JDK 17 proof: Auth 18/18, User 14/14, Shipper 6/6.
- [x] Settlement query-safety slice: admin balances/all-transactions/pending và
  hidden entity-history compatibility lists cap repository query ở 100; revenue
  vẫn tính bằng SQL aggregate nên không tải ledger vào memory. Recalculation giữ
  full-history semantics và cần tối ưu DB-side riêng, không được cap làm sai tiền.
  Clean Settlement 11/11 trên JDK 17.
- [x] Restaurant catalog query-safety slice: public catalog/search/menu/rating,
  owner and admin compatibility lists cap DB query at 100. Rating aggregate no
  longer loads every approved row; count/average are calculated in SQL so the
  displayed restaurant rating remains exact beyond the list cap. Clean
  Restaurant 74/74 trên JDK 17.
- [x] Notification query-safety slice: owned list và unread list cap repository
  query ở 100; unread-count tiếp tục dùng SQL count chính xác. Không đổi ownership,
  dedup hoặc mutation semantics. Clean Notification 18/18 trên JDK 17.
- [x] Promotion/Flash Sale query-safety và test-isolation slice: admin/merchant
  voucher lists, campaign list và campaign-item list cap repository query ở 100;
  hai context test dùng H2 profile và Mockito subclass mock-maker. Flash Sale
  mapper chuyển sang Spring component tường minh để không phụ thuộc generated
  MapStruct bean. Clean JDK 17 proof: Promotion 6/6, Flash Sale 7/7.
- [x] Auth/Delivery compatibility query closeout: `/auth/sessions` giờ đúng tên
  contract, chỉ trả active + unexpired session theo last-login và cap 100; shipper
  delivery history/active lists cũng cap repository query ở 100 mà không đổi
  response envelope. Clean JDK 17 proof: Auth 19/19, Delivery 22/22.
- [x] Shipper rating query-safety slice: rating average chuyển từ load toàn bộ
  lịch sử sang SQL aggregate; public/self compatibility lists cap 100. Unsafe
  submit/public-read route vẫn bị Gateway ẩn cho tới khi có delivered-order
  ownership proof. Clean Shipper 8/8 trên JDK 17.
- [x] Promotion wallet/User address query closeout: voucher wallet cap 100 và
  `calculate` batch-load voucher thay vì N+1; address list cap 100 và default
  replacement dùng single-row query. Clean Promotion 7/7, User 15/15.
- [x] Promotion route-method closeout: collect/my/admin CRUD được tách exact HTTP
  method. Merchant create bị ẩn vì code hiện nhầm ownerId thành restaurantId;
  chỉ giữ GET merchant list cho tới khi request có restaurantId và internal
  restaurant ownership proof. Gateway clean 17/17.
- [x] Order checkout-preview boundary: Bean Validation chặn missing/out-of-range
  restaurant, Vietnam coordinates, item và quantity trước khi gọi downstream;
  nested item validation có proof. Mapper đánh dấu cancellation metadata là
  server-owned explicit ignore, không còn generated-mapper warning. Follow-up
  preview dùng internal restaurant validation + shared secret giống create-order
  và fail-closed khi thiếu canonical pickup/menu facts; Order 85/85.
- [x] Catalog/flash-sale mutation validation wave: restaurant/menu create chặn
  blank name, invalid restaurant/price và out-of-country coordinates trước JPA;
  Order/Restaurant validation errors trả 400 thay vì catch-all 500. Flash item
  bắt positive IDs/stock/prices và `flashSalePrice < originalPrice`. Clean proof:
  Restaurant 77/77, Order 36/36, Flash Sale 8/8.
- [x] Delivery command boundary: accept/reject và cancel-assignment dùng DTO tách
  riêng, Bean Validation + HTTP 400 handler; orderId positive, canonical action,
  bounded notes/reason, pickup estimate và Vietnam coordinates. Không ép action
  giả vào cancel flow. Clean Delivery 25/25 trên JDK 17.
- [x] Delivery status transition concurrency: mutation đọc delivery bằng
  `PESSIMISTIC_WRITE` trước permission/state-machine check và outbox write, chặn
  concurrent transition cùng vượt qua stale state. PostgreSQL lock rehearsal vẫn
  thuộc Gate B8. Clean Delivery 26/26 trên JDK 17.
- [x] Order lifecycle concurrency: customer/admin cancellation, generic hidden
  mutations và Saga/event status handlers lấy `PESSIMISTIC_WRITE` order row trước
  transition; bulk admin cancellation lock selected rows. Cancellation lock→save→
  outbox ordering có focused proof. Clean Order 39/39; PostgreSQL rehearsal OPEN.
- [x] Promotion wallet collect race: reject trước `startTime`, positive actor,
  null-safe active check và `saveAndFlush` để unique `(user,voucher)` race được
  chuyển thành HTTP 409 thay vì commit-time 500. Bad input/Bean Validation có
  standardized 400 handler. Clean Promotion 12/12; PostgreSQL race proof OPEN.
- [x] Flash Sale merchant exposure closeout: polyrepo có UI consumer nhưng
  registration vẫn tin menu/original price từ client trong khi checkout disabled.
  Gateway route bị ẩn và controller off mặc định bằng explicit Compose flag;
  public GET và ADMIN campaign surface giữ nguyên. Mở lại cần canonical menu/price
  ownership proof + reservation recovery. Proof: Gateway 19/19, Flash Sale 10/10,
  Compose verifier PASS.
- [x] Gateway orphan/overlap closeout: bỏ `/api/orchestrator/**` vì Saga không có
  HTTP controller; auth public, auth protected/admin và Firebase routes chuyển
  sang exact path + method. Catch-all auth từng làm `GET /login` vẫn match route
  protected nay đã bị loại. Gateway clean 17/17.
- [x] Runtime startup rehearsal được đóng gói tại
  `backend_delivery/scripts/verify-runtime-startup.sh`: yêu cầu Docker daemon và
  non-blank `INTERNAL_SECRET`, build/up cụm, chờ 4 infra healthy + 17 Spring app
  started, rồi smoke public reads qua Gateway. Script đã qua `bash -n` và
  fail-closed đúng khi daemon vắng; chưa có runtime PASS nên không tính Gate B8.
- [x] Compose bỏ env Saga HTTP URI mồ côi sau khi Gateway route bị xóa; verifier
  chặn env này quay lại. Compose contract và diff check PASS.
- [x] Repository dead-query closeout: Notification bỏ sáu unbounded/unused query
  declarations, chỉ giữ bounded owned lists và mutation/dedup queries đang dùng;
  Auth block-account query thẳng active sessions thay vì load cả lịch sử rồi
  filter trong JVM. Clean Notification 18/18, Auth 19/19.
- [x] Tracking legacy client closeout: xóa Delivery `TrackingServiceClient` không
  có consumer, gọi sai port 8090 và nhắm API busy đã bị ẩn; Delivery dùng
  Kafka/Redis canonical flow. Match cũng bỏ tracking WebClient bean/env dead vì
  matching đọc Redis GEO replica. Clean Delivery 22/22, Match 17/17; Compose
  verifier được cập nhật.
- [x] Xóa Tracking `WebSocketTestController` dev-only không có consumer: page
  hard-code port 8090, thiếu JWT và thiếu deliveryId bắt buộc nên không còn phản
  ánh canonical socket contract. Tracking còn 8/8 behavior tests xanh.
- [x] Flash Sale datasource convergence: source/local/Compose đều dùng PostgreSQL,
  bỏ MySQL runtime driver và comment override cũ; H2 vẫn chỉ là test profile.
  Clean Flash Sale 7/7.
- [x] Gateway trusted-identity closeout: global highest-precedence filter strip
  client `X-User-Id`/`X-Role` kể cả public routes; resource service tự validate
  Bearer JWT qua JWKS và không nhận identity từ các header này. Signed token thiếu
  `sub` hoặc `role` trả 401. CORS origins
  chuyển sang env allow-list non-empty thay vì phải sửa code khi deploy. Gateway
  clean 19/19; Compose verifier kiểm CORS config.
- [x] Delivery command validation/error closeout: accept có positive order,
  canonical action và coordinate bounds; cancel-assignment dùng DTO riêng nên
  không giả action; invalid body trả 400, generic error dùng structured logging
  thay `printStackTrace`. Clean Delivery 24/24.
- [x] Order pagination boundary: mọi PageRequest public/admin reject page âm,
  size ngoài 1..100 trước service/repository. Clean Order 37/37.
- [x] Restaurant decision request closeout: confirm/reject bỏ untyped Map parsing,
  dùng DTO validate positive restaurant/order IDs, prep 1..240 và bounded
  notes/reason; ownership/outbox semantics giữ nguyên. Clean Restaurant 77/77.
- [x] Notification internal command/error closeout: manual-send DTO bắt positive
  userId, required/bounded content, canonical priority, non-null channel flags;
  service null-safe với flags. Runtime exception không còn leak message nội bộ và
  trả 500, validation/input vẫn 400. Clean Notification 19/19.
- [x] Hoàn tất Phase 0 inventory và baseline executable. HTTP/system inventories
  đã có, build baseline xác nhận Java/Maven cùng JDK 17, Spring Boot 3.5.15 và
  Spring Cloud 2025.0.3; Compose contract và reactor package 17/17 xanh.
- [x] Phase 1 runtime foundation theo tiêu chuẩn MVP đã đủ; production-grade
  hardening không còn là blocker cho MVP và tiếp tục ở post-MVP backlog.
- [x] Hoàn tất các service/boundary MVP thuộc Phase 2-7 và đóng contract freeze;
  các capability phụ/production hardening chưa được coi là MVP complete.
- [x] Qua Gate B8 backend.
- [x] Hoàn tất Phase 8-11 client audit/refactor theo no-emulator MVP acceptance.
  Static/build/action/contract gates xanh; owner/admin browser surfaces đã xanh;
  ADMIN fixture/API runtime proof đã xanh bằng operator-only runner; Gateway
  CORS cho Vite preview `4173` đã fix; full admin browser dashboard/surface smoke
  đã PASS và mobile device proof là final sanity only.
- [x] Customer order/tracking/notification contract hardening (2026-07-26):
  `CurrentDeliveryDto` không còn dựng delivery/order ID `0`, trạng thái lạ thành
  `PENDING`, địa chỉ placeholder hoặc tọa độ `0,0`; mapper nhận đủ chín canonical
  Delivery status và bắt identity/address/tọa độ Việt Nam/timestamp hợp lệ.
  `OrderDto` không còn mặc định status lạ thành pending, payment lạ thành COD hay
  total thiếu thành `0`; response phải có identity nhà hàng, COD, server-owned
  totals, timestamp, menu item hợp lệ và giữ `shipperId` backend. Hai luồng
  reorder validate trước khi xóa cart; tracking không gọi bằng order ID `0`; nút
  gọi shipper no-op và POST order legacy zero-call-site đã xóa. Notification
  mapper không còn tạo ID/user `0`, timestamp hiện tại hay priority giả; UI không
  echo exception. Checkout sau đó được khóa server-owned: tự tải preview khi mở,
  disable đặt đơn nếu preview thiếu/malformed, không fallback giá local và không
  gửi tên/giá món hoặc thông tin nhà hàng placeholder; create request chỉ còn
  restaurant/menu identity, quantity, địa chỉ khách và COD. Focused checkout
  8/8, Flutter analyzer 0 issue, full suite 111/111, diff hygiene và Android
  debug APK build PASS, không mở emulator.
- [x] Shipper rating/notification và restaurant-coordinate truth closeout
  (2026-07-26): backend Shipper bỏ synthetic rating `5.0`; Flyway V2 chỉ null
  rating không có row sở hữu, giữ aggregate thật, self-ratings trả canonical
  `BaseResponse`, aggregate thiếu sau insert fail-closed. Shipper app parse strict
  notification envelope/count/entity, shipper profile và rating rows; không còn
  fallback response thiếu thành `[]`, `0` hoặc `0.0`. Restaurant create bắt buộc
  tọa độ pickup Việt Nam; web form giữ trống và reject thiếu/out-of-bound thay vì
  gửi `0,0`. Proof: Shipper backend 21/21, Restaurant backend 94/94 trên JDK 17;
  shipper TypeScript + ESLint + 13 Jest suite/40 test PASS; web ESLint + Vite
  production build PASS; diff hygiene sạch, không chạy simulator.
- [x] Qua Gate C12 system acceptance theo no-emulator MVP policy.

## Decisions

- 2026-07-22: Backend được chuẩn hóa và freeze trước khi sửa contract client.
- 2026-07-22: MVP tracking dùng raw WebSocket; gRPC ngoài phạm vi hiện tại.
- 2026-07-22: COD là financial happy path đầu tiên; online payment chỉ public khi
  có callback/reconciliation proof.
- 2026-07-22: Nhà hàng phải confirm trước khi match; mỗi lượt offer một shipper.
- 2026-07-22: API chưa chứng minh được không bị xóa vội; trước hết phân loại và ẩn
  khỏi public edge để giữ khả năng recovery.
- 2026-07-23: Checkout MVP chỉ mở order thường + COD. Voucher/flash-sale vẫn giữ
  code và catalog/admin surface nhưng không được reserve từ checkout cho tới khi
  discount, concurrency, idempotency và compensation có executable proof.
- 2026-07-23: COD chỉ post ledger một lần tại `delivery.completed`; pickup không
  còn tạo financial side effect. Online/self-service money APIs bị ẩn cho đến khi
  ownership, provider callback và reconciliation có proof.

## Current Evidence And Next Action

- Baseline config từng sai promotion DB, auth user-service URL, Redis wiring và
  custom Kafka broker. Config wave 2026-07-22 đã sửa và có render/unit proof;
  container startup proof còn thiếu vì Docker daemon không hoạt động.
- Kafka matrix từng xác nhận orphan command `saga.command.cache-shipper-found`;
  delivery-service hiện đã consume command này và phát notification event sau
  khi lưu offer. Vẫn còn orphan listener `order.events` và pipeline legacy `delivery.find-shipper`,
  `shipper.matched`, `no.shipper.available` không có đầu nhận hữu hiệu.
- Baseline từng xác nhận `/api/orders/admin/**`, `/api/deliveries/admin/**` bỏ JWT
  và auth email lookup nằm trong public allow-list; edge wave 2026-07-22 đã đóng
  ba lỗ hổng này và thêm role check ở Gateway lẫn controller. Raw tracking WS đã
  có JWT/session identity và participant-level active-delivery authorization;
  terminal subscription cleanup còn mở.
- HTTP inventory từng xác nhận route rộng public cả match, tracking state mutation,
  notification send và flash-sale internal reserve. Các route đã bị ẩn; tracking
  internal REST off mặc định + shared secret, notification manual-send có shared
  secret, Match legacy pipeline off mặc định. Flash-sale reserve đã fail-closed;
  Order dashboard conflict đã đóng bằng feature flag off mặc định sau polyrepo search.
- Auth service từng `permitAll` toàn bộ `/api/auth/**` và ghi bearer/header ra
  log; wave 2026-07-23 đã chuyển sang allow-list public, role-bound admin,
  authenticated sessions và bỏ log credential. Auth→user dùng cùng
  `INTERNAL_SECRET`; provisioning hiện recoverable/idempotent nhưng chưa có proof
  bằng PostgreSQL thật cho migration/race và không phải distributed transaction.
- Checkout audit xác nhận client có thể giả pickup để giảm shipping fee, owner có
  thể confirm restaurant khác, generic order route lộ mutation/path-ID list, voucher
  reserve không tạo discount và flash-sale có partial-reserve window. Edge/service
  wave đã đóng các đường này hoặc fail-closed; status enum, outbox và DB/Kafka
  replay proof vẫn OPEN.
- Latest focused structural gate: Match clean 17/17, Delivery clean 26/26,
  Tracking clean 8/8, Settlement 12/12, Gateway 19/19, Notification 19/19,
  Order 39/39, Restaurant 79/79 và Saga 14/14.
  Build baseline, Compose contract, diff check và reactor package JDK 17 đều xanh
  đủ 17/17 service sau các wave này.
- [x] Flash Sale reserve boundary validation: internal reserve chỉ nhận item id,
  quantity và price dương; checkout recovery flag vẫn được kiểm tra trước body
  semantics nên capability mặc định đóng tiếp tục trả 503. Clean JDK 17 proof:
  Flash Sale 10/10.
- [x] Promotion request boundary validation: calculate bắt buộc shop dương và
  monetary values không âm; reserve bắt buộc user/order/voucher ids dương. Internal
  credential và checkout recovery gate chạy trước domain validation để feature
  mặc định đóng giữ nguyên 503. Clean JDK 17 proof: Promotion 10/10.
- [x] Order legacy mutation isolation: generic update/delete/status/assign được
  chuyển khỏi canonical controller sang compatibility controller mặc định tắt ở
  application và Compose; public cancel reason được bound 500 ký tự. Clean JDK 17
  proof: Order 39/39.
- [x] Shipper rating write isolation: customer submit endpoint được tách khỏi
  self-read controller và mặc định không tạo bean ở application/Compose cho tới
  khi có delivered-order/customer/shipper relationship proof. DTO đã bound order,
  score 1..5 và comment 500. Flutter vẫn gọi endpoint này nên ghi nhận contract
  migration cho client phase sau B8. Clean JDK 17 proof: Shipper 9/9.
- [x] Restaurant catalog partial-update validation: restaurant/menu update giữ
  nullable partial semantics nhưng reject blank name, text/phone overflow, ngoài
  Vietnam coordinates và price/restaurantId sai trước persistence. Ownership và
  mapper semantics không đổi. Clean JDK 17 proof: Restaurant 79/79.
- [x] Auth/User admin block boundary: role check vẫn chạy trước payload semantics;
  block reason được bound 500, User bắt reason hiện diện và Auth dùng typed numeric
  admin identity thay parse String thủ công. Clean JDK 17 proof: Auth 20/20, User
  16/16.
- [x] Match debug HTTP isolation: `/api/match/nearby-shippers` không có polyrepo
  consumer, Gateway không route và controller giờ mặc định không tạo bean qua
  `MATCH_DEBUG_API_ENABLED=false`; exception response nếu bật cũng được sanitize.
  Kafka/Saga one-shipper pipeline không đổi. Clean JDK 17 proof: Match 17/17.
- [x] Search shipper-discovery isolation: restaurant/dish public search giữ nguyên;
  `/api/search/shippers` được tách sang controller mặc định không tạo bean và
  Compose khóa `SEARCH_SHIPPER_API_ENABLED=false`. Flutter call được ghi nhận cho
  client migration. Search test isolation dùng Mockito subclass mock-maker; clean
  JDK 17 proof: Search 6/6.
- [x] Livestream HTTP capability isolation: cả lifecycle, product và token
  controllers mặc định không tạo bean, Compose khóa `LIVESTREAM_API_ENABLED=false`.
  Flutter/web references được ghi nhận cho client migration; Agora ownership/token
  policy phải có proof trước khi mở. Clean JDK 17 proof: Livestream 1/1.
- [x] Settlement self-service isolation: arbitrary entity balance/history cùng
  withdraw/hold/release/deposit/recalculate controllers mặc định không tạo bean;
  Compose khóa `SETTLEMENT_SELF_SERVICE_API_ENABLED=false`. Internal COD eligibility,
  delivery-completed ledger và admin read-only surfaces giữ nguyên. Web/shipper
  references là client migration items. Clean JDK 17 proof: Settlement 11/11.
- [x] Settlement admin mutation isolation: public AdminController chỉ còn GET
  balances/transactions/pending/revenue; approve/reject/reverse chuyển sang
  compatibility controller mặc định tắt và reason bound 500. Compose khóa
  `SETTLEMENT_ADMIN_MUTATION_API_ENABLED=false`. Settlement vẫn 11/11 clean.
- [x] Flash Sale retained-surface boundary: public active campaigns/items và admin
  management được giữ theo inventory; campaign name bound 255, status mutation
  nhận typed `CampaignStatus` thay String/valueOf để malformed input không thành
  500. Public campaign không tồn tại trả typed 404; admin có endpoint item list
  riêng để không dùng public moderation data. Merchant/reserve vẫn disabled.
  Clean JDK 17 proof: Flash Sale 13/13.
- [x] Promotion merchant-create isolation: POST merchant được tách khỏi canonical
  controller sang compatibility controller mặc định tắt; Compose khóa
  `PROMOTION_MERCHANT_CREATE_API_ENABLED=false` cho tới khi request có explicit
  restaurantId + ownership proof. Server-derived creatorType không còn bị DTO
  validation bắt client cung cấp. Clean JDK 17 proof: Promotion 12/12.
- [x] Shipper destructive profile boundary: DELETE self được tách sang legacy
  controller mặc định tắt cho tới khi có active-delivery/deactivation policy;
  Compose khóa `SHIPPER_LEGACY_DELETE_API_ENABLED=false`. Canonical create/profile/
  update/online dùng typed Long identity thay parse String. Shipper giữ 9/9 clean.
- [x] User destructive lifecycle isolation: DELETE path-ID được tách khỏi canonical
  controller sang legacy controller mặc định tắt; Compose khóa
  `USER_LEGACY_DELETE_API_ENABLED=false` cho tới khi chốt deactivate/hard-delete.
  Current-user read dùng typed trusted identity. User giữ 16/16 clean.
- [x] Matching topic orphan removal: dead Delivery `FindShipperEvent`, private
  publisher và Delivery/Match `delivery.find-shipper` constants được loại sau
  zero-call-site proof. Product overview/production roadmap dùng canonical
  `delivery.shipper-rejected` → Saga → `saga.command.find-shipper`. Clean JDK 17:
  Delivery 26/26, Match 17/17.
- [x] Kafka constant dead-code pass: xóa module-local symbols chỉ có definition
  mà không có call site ở Order/Delivery/Match/Notification; xóa toàn bộ dead
  Restaurant `delivery-completed` (sai canonical separator) và Shipper topic
  constant classes. Active producer/listener constants giữ nguyên.
- [x] Logging/error-envelope cleanup: loại `printStackTrace`/`System.out` khỏi
  code chạy của Settlement, Match, Restaurant geocoding và Agora helper; lỗi 500
  được log có stack trace nhưng không trả exception detail cho client. Sửa
  Settlement handler trước đó đảo `message` vào `data`, thêm regression proof.
  Clean proof: Settlement 14/14, Match 20/20, Restaurant 79/79, Livestream 1/1
  và Shipper 9/9; Shipper generic 500 cũng đã được che detail.
- [x] gRPC Tracking dead-surface closeout: sau khi product authority chốt raw
  WebSocket là canonical MVP transport, đã xóa Java skeleton, dependency/build
  hook, `.proto` và guide vận hành gRPC; polyrepo không có runtime/client call
  site. Build-baseline verifier chặn gRPC/protobuf artifact quay lại Tracking.
  Clean Tracking 20/20; build baseline, HTTP inventory 180/180, Compose contract
  và diff hygiene đều PASS trên JDK 17 ngày 2026-07-25.
- [x] Tracking Redis mutation fail-open được đóng: location cache, remove và
  BUSY/AVAILABLE trước đây catch rồi return khiến HTTP/Kafka có thể báo thành công
  giả. Các write/read-busy canonical nay propagate infrastructure failure để API
  lỗi hoặc Kafka retry; regression proof kiểm location và BUSY. Tracking clean
  14/14 trên JDK 17.
- [x] COD-only event boundary closeout: Order `payment.completed/payment.failed`
  listener không còn tạo bean mặc định; explicit
  `ORDER_PAYMENT_EVENT_PROCESSING_ENABLED=false` được khóa trong Compose/verifier.
  Vì payment producer/API đều disabled, event ngoài ý muốn không còn đổi state
  order COD. Order clean giữ 39/39 trên JDK 17.
- [x] Xóa hoàn toàn legacy `delivery.picked-up` financial pipeline sau zero-call-
  site proof: Delivery producer/method/DTO/topic constant và Settlement listener/
  DTO/topic constant đều được loại. COD ledger chỉ còn một canonical write point
  ở `delivery.completed`; không còn code dormant có thể gây double debit khi vô
  tình thêm component annotation về sau.
- [x] Production logging baseline: main properties của Auth/User/Order/Delivery/
  Restaurant/Shipper/Settlement/Notification/Match/Tracking/Livestream/
  Flashsale/Analytics/Saga không còn hard-code SQL logging, Security/WebSocket
  DEBUG hoặc Hibernate bind TRACE. Mặc định là `false/INFO`, có env override khi
  debug; baseline verifier fail nếu literal verbose logging quay lại.
- [x] Full backend reactor `mvn test` trên JDK 17 đạt 17/17 module, tổng 333 test,
  sau COD listener cleanup, Tracking fail-fast và logging normalization; build
  baseline, Compose contract và `git diff --check` cùng PASS ngày 2026-07-23.
- [x] JPA transaction boundary: `spring.jpa.open-in-view=false` được đặt cho đủ
  13 JPA service và baseline verifier chặn regression; full reactor 333 test vẫn
  PASS nên controller/mapper hiện không phụ thuộc lazy query sau transaction.
  Shipper test resource từng shadow main config cũng được khóa OSIV=false và
  clean 9/9 không còn warning.
- [x] User dependency cleanup: `SecurityConfig` chỉ `permitAll` và Spring Security
  cùng ba JJWT dependency không có call site đã bị xóa; service tiếp tục dựa vào
  Gateway trusted identity + internal-secret controller checks. User clean 16/16,
  reactor package 17/17 và baseline/Compose/diff đều PASS.
- [x] Database secret baseline: xóa fallback `123456` khỏi 13 JPA service và
  Compose; direct runtime dùng `DB_PASSWORD`, Compose bắt buộc một
  `POSTGRES_PASSWORD` dùng chung. `gen-keys.sh` sinh local secret ngẫu nhiên,
  startup proof fail-fast nếu thiếu; Compose verifier kiểm non-blank/đồng nhất và
  baseline chặn legacy password quay lại. Không tự xóa volume cũ; recovery/rotate
  đã ghi trong `SECURITY.md`. Reactor package 17/17 và gates đều PASS.
- [x] JWT/local secret bootstrap không còn rotate keypair mỗi lần chạy: generator
  giữ keypair hợp lệ, chỉ rotate với `ROTATE_JWT_KEYS=true`, đồng thời bổ sung
  INTERNAL/DB secret còn thiếu. Runtime preflight parse đúng hai key từ `.env`
  mà không source/execute file. PEM/Firebase staged deletion và git-history scrub
  thuộc user/operator state, không bị agent reset hay rewrite.
- [x] Payment bean isolation: khi `PAYMENT_PROCESSING_ENABLED=false`, Settlement
  không tạo controller, service, event publisher, provider registry, Fake hay
  VNPay provider. Fake confirm tách thành controller riêng và cần thêm explicit
  `FAKE_PAYMENT_PROVIDER_ENABLED=true`; Compose/verifier giữ false. VNPay bỏ
  DEMO credential fallback và fail-closed cả create/callback nếu env thiếu.
  Settlement clean 14/14; capability vẫn chưa được mở.
- [x] Restaurant `/api/test/protected` dev controller không có Gateway route,
  polyrepo consumer hay operational contract đã bị xóa thay vì giữ endpoint giả
  danh “protected”. HTTP inventory được cập nhật; Restaurant compile/test proof
  được chạy lại.
- [x] Static gate rerun sau Flash Sale contract split: build baseline, Compose
  verifier, `git diff --check` và reactor `mvn -DskipTests package` đều PASS đủ
  17/17 service trên JDK 17 (2026-07-23). Docker CLI có mặt nhưng daemon socket
  không tồn tại, nên runtime startup/replay vẫn chưa thể chạy và không tính B8.
- [x] Analytics sequential replay guard: raw event có unique dedup key ưu tiên
  producer `eventId`, fallback `eventType + orderId`; duplicate không tăng raw log
  hay daily aggregates. Listener không còn ACK parse/processing failure để Kafka
  error handler nhận lỗi. H2 persistence + listener proof đạt 7/7 trên JDK 17.
- [x] Order business-listener acknowledgment boundary: payment và restaurant
  consumers dùng manual ack đúng contract, chỉ ACK sau DB mutation thành công;
  payment exception không còn bị nuốt và cả hai listener rethrow để Kafka error
  handler retry. Clean JDK 17 proof: Order 43/43.
- [x] Match replica-listener reliability + legacy cleanup: location/status events
  chỉ ACK sau Redis mutation; malformed/unknown events được rethrow cho configured
  retry handler. Clean build phát hiện legacy `MatchEventServiceImpl` còn tham chiếu
  orphan topic đã xóa; zero-call-site pipeline, interface và DTO đã được loại cùng
  flag Compose thay vì khôi phục một contract sai. Clean JDK 17 proof: Match 20/20.
- [x] Tracking realtime replication reliability: producer location chờ Kafka broker
  ACK tối đa 5 giây với idempotent/all-ISR settings thay vì nuốt async failure;
  WebSocket shipper nhận sanitized error để retry latest-state update. Status
  consumer rethrow parse/unknown/Redis failure cho Kafka retry. Raw WebSocket vẫn
  là contract canonical, không thêm gRPC. Clean JDK 17 proof: Tracking 12/12.
- [x] Notification durable delivery boundary: Kafka-derived notification được
  `saveAndFlush` ở trạng thái PENDING trước WebSocket/FCM I/O; provider failure giữ
  stable DB notification ID để Kafka retry dispatch lại, còn duplicate SENT được
  skip. Chỉ sau khi các kênh thành công mới cập nhật SENT và invalidate cache.
  Listener manual ACK/DLT contract giữ nguyên. Core private WebSocket send không
  còn nuốt broker exception nên PENDING retry thực sự hoạt động. Clean JDK 17
  proof: Notification 22/22.
- [x] Settlement poison-event + dead-flow closeout: canonical `delivery.completed`
  validation/JSON lỗi không còn ACK-discard mà được retry hai lần rồi publish cùng
  partition sang `.DLT`; DLT producer dùng acks=all + idempotence. Deprecated
  `delivery.picked-up` listener/DTO/topic không có bean/producer đã bị xóa vì trái
  COD single-settlement-at-completion contract. Clean JDK 17 proof: Settlement 14/14.
- [x] Saga listener identity/ack boundary: tất cả order/delivery/shipper IDs phải
  dương trước SagaManager; success mới manual-ACK, manager/validation failure đi
  configured retry + same-partition DLT. Pessimistic state lock + transactional
  command outbox giữ nguyên. Clean JDK 17 proof: Saga 16/16.
- [x] Delivery command validation/DLT boundary: bỏ minimum-fields fallback từng cho
  phép invalid status/payment/price/address/coordinates đi vào create-delivery.
  Mọi validation failure có orderId tạo correlated failure outbox rồi ACK; JSON
  không correlate được retry + DLT. Clean JDK 17 proof: Delivery 28/28.
- [x] Disabled checkout consumer cleanup: Promotion orphan `order.events` listener
  không có producer đã xóa; Flash Sale bỏ `payment.failed` no-op và order-cancelled
  recovery không còn nuốt exception khi flag được bật. Hai checkout flag vẫn false.
- [x] Static Kafka consumer closeout: rà đủ 16 listener class / 36 handler methods.
  Core paths đều success-after-mutation ACK hoặc propagate tới configured retry/DLT;
  disabled experimental consumers có explicit flag, orphan/no-op consumers đã xóa.
  Runtime duplicate/crash-window proof vẫn là Gate B8 riêng, không suy ra từ static.
- [x] Gateway exact-route closeout: thay toàn bộ wildcard route còn lại của user
  admin/address, order/delivery admin, restaurant decision/rating moderation,
  Firebase token, raw tracking WebSocket và Flash Sale public/admin bằng allow-list
  path + HTTP method khớp exact controller/socket inventory. Regression test chặn
  endpoint tương lai tự động lọt qua edge; full Gateway 19/19, build baseline,
  Compose contract và `git diff --check` xanh ngày 2026-07-23. Mismatch quyền
  restaurant/menu được đóng ở wave kế tiếp theo product authority.
- [x] Restaurant/menu mutation authority closeout: product overview xác định
  `ADMIN` quản trị restaurant/menu và `SHOP_OWNER` chỉ quản lý entity mình sở hữu.
  Update/delete controller nay truyền trusted role; service fail-closed khi thiếu/
  sai role, cho owner đúng identity hoặc admin, thay vì Gateway cho admin nhưng
  service từ chối. Focused controller/service proof 34/34 và full Restaurant
  83/83 xanh trên JDK 17; full run gồm H2 migration/outbox proof, chưa thay thế
  PostgreSQL/Kafka Gate B8.
- [x] Delivery restaurant-participant read ownership: `order.created` đã mang
  canonical restaurant `creatorId`, Delivery nay persist riêng
  `restaurant_owner_id` thay vì tái dùng customer `creator_id`; detail/order lookup
  cho `SHOP_OWNER` chỉ khi identity khớp, legacy row null fail-closed. V7 thêm
  nullable column/index để migration không đoán owner cho dữ liệu cũ; event thiếu
  owner ID bị reject. Focused Delivery ownership/listener 13/13 và full Delivery
  30/30 xanh; PostgreSQL
  migration và end-to-end restaurant read vẫn thuộc Gate B8.
- [x] Notification offer replay/rematch identity: dedup key cũ
  `orderId + shipperId` làm lần rematch hợp lệ tới cùng shipper bị nuốt như Kafka
  replay. Canonical `delivery.shipper-offered` nay bắt producer outbox `eventId`;
  notification dedup theo `eventId + shipperId`, nên cùng event replay chỉ gửi một
  lần còn offer attempt mới vẫn gửi được. Missing eventId không ACK/dispatch.
  Full Notification 24/24 xanh; PostgreSQL unique race và Kafka restart vẫn OPEN.
- [x] Delivery late-cancel convergence: Saga cancel command trước đây vẫn publish
  stop-matching rồi ACK thành công khi delivery đã `PICKED_UP`/`DELIVERING`, khiến
  Order/Saga có thể CANCELLED trong khi Delivery tiếp tục giao. Service nay chỉ
  ghi stop-matching outbox trong transaction của một cancellation hợp lệ; late/
  terminal cancel fail rõ ràng, listener ghi `delivery.cancel.failed` rồi ACK
  correlated command. Duplicate `CANCELLED` vẫn idempotent. Full Delivery 32/32
  xanh; cross-service failure convergence còn thuộc Gate B8.
- [x] Order cancel actor boundary: dead service branch từng mô tả SHIPPER cancel
  Order nhưng permission thực tế không bao giờ cho qua và canonical rematch thuộc
  Delivery `cancel-assignment`. Gateway Order cancel nay chỉ nhận `USER`,
  `SHOP_OWNER`, `ADMIN`; service bỏ branch gây hiểu nhầm và regression proof xác
  nhận shipper không tạo order-cancel outbox. Exact cancel replay cùng actor/reason
  no-op không ghi outbox lần hai; replay khác actor/reason bị reject. Full Order
  87/87, Gateway 19/19 xanh ở checkpoint gần nhất.
  Shipper app đang gọi nhầm Order cancel vẫn là client migration item sau B8.
- [x] Unsafe bulk-cancel edge isolation: polyrepo zero-call-site search xác nhận
  Order/Delivery `cancel-all-pending` không có consumer. Hai implementation không
  đảm bảo canonical per-aggregate outbox/convergence và Delivery còn quét cả
  pickup/delivering, nên Gateway routes đã bị loại trước và controller/service/
  repository query nay đã xóa hoàn toàn thay vì giữ maintenance API không audit.
  Gateway regression proof tiếp tục bắt exact POST không match.
- [x] Dead Order shipper-list closeout: `/api/orders/shipper/{shipperId}` không có
  polyrepo consumer, Gateway đã deny và trùng canonical Delivery history/active
  API; controller, service contract/implementation và repository query đã xóa.
- [x] Dead Order/Delivery HTTP cleanup checkpoint: zero-call-site proof xác nhận
  Order shipper-list cùng hai bulk-cancel admin surface không có consumer; ba
  endpoint và controller/service/repository implementation đã được xóa, HTTP
  inventory giảm từ 180 xuống 177 method. Clean JDK 17 proof ngày 2026-07-25:
  Order 66/66, Delivery 42/42, HTTP inventory 177/177, reactor package 17/17,
  build baseline, Compose contract và diff hygiene đều PASS. Generic Order
  update/delete/status/assign vẫn là compatibility controller tắt mặc định vì
  còn client reference; migration chỉ thực hiện sau backend freeze.
- [x] Shipper cancel-assignment contention boundary: canonical Delivery endpoint
  trước đây đọc order không lock, có thể race với status `PICKED_UP` rồi reset một
  delivery đã lấy hàng. Mutation nay dùng pessimistic order-row lock; reset shipper,
  AVAILABLE event và rejected/rematch command cùng transaction/outbox. Focused
  proof kiểm lock-before-mutation và cả hai convergence event; full Delivery
  33/33 xanh; PostgreSQL race
  rehearsal vẫn thuộc Gate B8.
- [x] Saga post-accept rematch convergence: Saga trước đây chỉ nhận rejection ở
  `SHIPPER_FOUND/FINDING_SHIPPER`, nên cancel-assignment sau accept bị ACK-skip ở
  `SHIPPER_ASSIGNED`; Delivery đã reset nhưng Saga/Order vẫn assigned và luồng
  treo. Saga nay chấp nhận assigned cancellation chỉ khi rejected shipper khớp,
  clear shipper, giữ exclusions/limit, phát find-shipper và Order
  `FINDING_SHIPPER`. Full Saga 18/18 xanh; Kafka cross-service rehearsal OPEN.
- [x] Order Saga-command fail-closed boundary: `orderId` phải dương và
  `sagaStatus` lạ không còn bị log rồi ACK-discard; validation hoặc mutation lỗi
  được propagate vào retry/DLT, chỉ command hợp lệ thành công mới ACK. Rà lại toàn
  bộ lớp `*Listener` không còn nhánh `default` log/return-null rồi ACK. Full Order
  46/46 xanh; bằng chứng Kafka duplicate/crash-after-commit vẫn OPEN tại Gate B8.
- [x] Settlement durable replay identity: thay marker “có platform commission theo
  order” vốn ACK cả payload mâu thuẫn bằng receipt unique `eventId`/order, lưu
  delivery ID và SHA-256 payload trong cùng transaction ledger. Exact replay mới
  được ACK-skip; reused event ID hoặc event khác cho order đã settle đi retry/DLT.
  Migration preflight dừng nếu ledger cũ chưa có receipt để buộc đối soát thủ công.
  Full Settlement 16/16 xanh tại checkpoint listener; PostgreSQL concurrent insert/crash proof vẫn OPEN.
- [x] Settlement executable migration authority: thêm Flyway V1 Java migration với
  baseline 0 cho schema Hibernate hiện hữu. Schema sạch tạo `settlement_receipts`;
  schema cũ được preflight duplicate ledger business key và platform settlement thiếu
  receipt trước khi thêm unique constraint. Migration không xóa/gộp ledger hoặc suy đoán
  event ID. Năm scenario clean/upgrade/fail-closed cùng full Settlement 21/21 xanh;
  PostgreSQL DDL transaction/concurrent replay vẫn thuộc Gate B8. SQL recovery trong
  `backend_delivery/docs/migrations/2026-07-23-settlement-ledger-idempotency.sql`
  được đồng bộ nhưng Flyway là authority runtime.
- [x] Saga delivery-created contradiction guard: replay sau khi Saga đã tiến trạng
  thái chỉ được skip khi `deliveryId` khớp delivery đã lưu và có step creation;
  event thứ hai mang delivery ID khác không còn bị ACK như duplicate mà fail vào
  retry/DLT. Full Saga 20/20 xanh; Kafka reorder/restart proof vẫn thuộc B8.
- [x] Order rematch/replay convergence: `ASSIGNED -> FINDING_SHIPPER` xóa shipper
  cũ; acceptance khác shipper không thể overwrite assignment hiện hữu; replay cùng
  shipper sau pickup/delivery được skip. Restaurant rejection chỉ skip khi đúng
  canonical cancellation đã lưu, delayed confirmation sau matching là replay, và
  unknown delivery status fail-closed. Xóa dead `handleShipperRejected` local sau
  polyrepo zero-call-site proof. Full Order 51/51 xanh; runtime races vẫn OPEN.
- [x] Delivery not-found race boundary: dùng pessimistic delivery-row lock trước
  transition; assignment/in-flight/terminal thắng stale not-found, còn not-found ở
  PENDING/WAIT bất khả thi không còn log rồi ACK mà fail vào retry/DLT. Full
  Delivery 35/35 xanh; PostgreSQL accept-vs-timeout race rehearsal vẫn OPEN.
- [x] Saga aggregate identity guard: mọi found/not-found, accept/reject và status
  event phải mang `deliveryId` dương khớp delivery do Saga sở hữu; event chéo
  delivery bị retry/DLT. Acceptance khác shipper không thể overwrite assignment,
  exact replay cùng shipper sau khi tiến trạng thái vẫn idempotent. Saga 22/22 xanh.
- [x] Delivery create-command replay identity: bắt buộc stable `eventId`, persist
  unique `create_event_id`, và đối chiếu customer/restaurant owner, location,
  shipping fee, total COD cùng payment method trước khi trả existing delivery.
  Event mới hoặc payload khác cho cùng order fail-closed thay vì ACK như duplicate.
  V8 thêm nullable column, recovery SQL backfill marker UUID cho historic rows rồi
  V9 preflight mới enforce non-null/unique. Delivery 37/37 xanh;
  PostgreSQL unique/concurrent replay rehearsal vẫn OPEN.
- [x] Saga order-created replay identity: listener bắt buộc UUID `eventId`; Saga
  dùng row lock khi kiểm tra order đã tồn tại và chỉ skip khi JSON payload bằng
  payload gốc đã lưu. Event khác giá/identity nhưng cùng `orderId` fail vào
  retry/DLT. Saga 25/25 xanh; concurrent first-insert proof vẫn OPEN ở PostgreSQL.
- [x] Restaurant-decision consumer metadata: Order DTO/listener/service bắt buộc
  UUID `eventId` do Restaurant decision outbox phát; missing ID không delegate và
  không ACK. Full Order 52/52 xanh tại checkpoint metadata.
- [x] Order restaurant-decision durable receipt: Flyway V3 tạo receipt unique theo
  `eventId` và `orderId`, lưu restaurant/decision cùng SHA-256 payload trong transaction
  Order. Exact replay mới được skip; event ID/payload hoặc event khác trên cùng Order
  fail vào retry/DLT. Rejection, canonical cancellation và `order.cancelled` outbox
  commit atomic; invalid state rollback receipt. Full Order 57/57 xanh gồm migration-
  only H2 và transaction proof. PostgreSQL concurrent insert/crash-window vẫn thuộc
  Gate B8; event lịch sử trước V3 không có receipt phải fail-closed và operator đối soát,
  không tự backfill một event ID suy đoán.
- [x] Static checkpoint sau durable receipt: clean Order 57/57; full backend reactor
  17/17 module đạt 386 test, không failure/error/skip. Build-baseline, Compose contract,
  `git diff --check` và reactor `-DskipTests package` cùng PASS trên JDK 17 ngày
  2026-07-23. Đây vẫn là H2/static proof, không thay PostgreSQL/Kafka Gate B8.
- [x] Static checkpoint sau Settlement Flyway V1: full backend reactor 17/17 module
  đạt 391 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17. Gate B8 vẫn mở vì
  chưa chạy migration/row-lock/replay trên PostgreSQL/Kafka thật.
- [x] Notification core-event identity validation: `order.created` và
  `delivery.status-updated` bắt buộc stable UUID event,
  positive aggregate/user IDs và status hợp lệ trước durable notification dispatch.
  Invalid identity không ACK/không gửi sai user. Notification 27/27 xanh; provider
  crash/restart proof vẫn thuộc B8.
- [x] Notification orphan order-status cleanup: polyrepo search xác nhận không có
  producer `order.status-updated`; xóa listener, topic constant, service method và
  status mapping giả. Customer lifecycle notification canonical đi từ
  `delivery.status-updated`; Notification vẫn 27/27 xanh.
- [x] Notification delivery-status vocabulary: bỏ legacy
  `STARTED/IN_PROGRESS/COMPLETED`, map đầy đủ canonical Delivery statuses sang
  title/message/type cụ thể; unknown legacy status không ACK-dispatch generic.
  Notification 27/27 xanh; live client rendering proof để sau backend freeze.
- [x] Notification executable migration authority: thay Hibernate
  `ddl-auto=update` bằng Flyway V1 + Hibernate `validate`, baseline schema hiện hữu
  ở version 0. Migration tạo clean schema, thêm hai compatibility column an toàn,
  preflight core columns và duplicate non-null dedup key trước unique constraint,
  đồng thời tạo index cho owned/unread timeline. Bốn clean/upgrade/fail-closed
  scenario và schema-validation test đều xanh; full Notification 32/32.
  PostgreSQL transactional DDL/concurrent unique và Kafka/provider crash/restart
  vẫn thuộc Gate B8.
- [x] Saga executable migration authority: production chuyển
  `ddl-auto=update` sang `validate`; Flyway V2 sở hữu aggregate + step history bên
  cạnh V1 outbox. Clean schema, legacy row preservation/version backfill,
  duplicate order, missing core column, incomplete table pair và JPA schema
  validation đều có executable proof. Recovery SQL concurrency được đồng bộ và
  ghi rõ không phải runtime authority. Full Saga 31/31; PostgreSQL DDL/row-lock/
  first-insert race và Kafka crash-window vẫn thuộc B8.
- [x] Order executable core-schema authority: production chuyển
  `ddl-auto=update` sang `validate`; Flyway V4 tạo/validate `orders` và
  `order_items` bên cạnh V2 outbox + V3 decision receipt. Clean schema, legacy
  aggregate/item preservation, missing core column, incomplete table pair và JPA
  schema validation đều xanh. Query indexes bám repository timeline/ownership
  access được tạo rõ ràng. Full Order 62/62; PostgreSQL DDL/row-lock và canonical
  status migration rehearsal vẫn thuộc B8.
- [x] Restaurant executable core-schema authority: production chuyển
  `ddl-auto=update` sang `validate`; Flyway V2 sở hữu catalog/menu/rating bên cạnh
  V1 decision/outbox. Clean schema, legacy catalog/rating preservation, missing
  core column, incomplete table set và JPA schema validation đều xanh. Indexes
  bám owner, menu status và rating aggregate queries. Full Restaurant 88/88;
  PostgreSQL DDL/advisory-lock/outbox restart vẫn thuộc B8.
- [x] Settlement complete schema authority: V1 tiếp tục sở hữu durable receipt và
  ledger business-key preflight; V2 sở hữu balance/immutable transaction/payment
  persistence và production chuyển `ddl-auto=update` sang `validate`. Clean schema,
  legacy financial row preservation, missing core column, incomplete table set và
  JPA validation đều xanh. Payment capability vẫn off; schema compatibility không
  phải authority để mở provider/API. Full Settlement 26/26; PostgreSQL DDL/row-lock/
  concurrent replay vẫn thuộc B8.
- [x] Saga stable metadata + terminal cancellation: cả 11 active listeners bắt
  buộc UUID event trước mutation. CANCELLED chỉ idempotent với exact stored event;
  cancellation khác payload hoặc sau COMPLETED không còn ACK-discard, còn event hệ
  quả của compensation sau FAILED được skip có chủ đích. Saga 25/25 xanh.
- [x] Match cancellation monotonicity: find command bắt buộc UUID + positive IDs;
  không còn clear Redis cancellation tombstone khi nhận find command, nên delayed
  retry không thể hồi sinh matching sau cancel. Redis read/write lỗi fail-closed
  thay vì tiếp tục offer; xóa dead wrapper từng swallow stop failure. Match 24/24
  xanh; live Redis delayed-command race vẫn thuộc B8.
- [x] Match Geo freshness/fail-closed replica: TTL 5 phút giờ được thực thi bằng
  timestamp key, stale location không overwrite mới và expired shipper không vào
  candidate set. Location/status/offer-release Redis failure không còn bị swallow
  rồi ACK; status event bắt buộc UUID + positive IDs. Match 24/24 xanh; live Redis
  expiry/out-of-order proof vẫn thuộc B8.
- [x] Tracking shipper-status identity: delivery status replica event bắt buộc
  stable UUID cùng positive shipper/delivery/order/timestamp trước Redis mutation;
  malformed/unknown/Redis failure tiếp tục propagate cho Kafka retry. Tracking
  14/14 xanh; live duplicate/restart proof vẫn thuộc B8.
- [x] Remaining core command metadata: Delivery cancel/cache-offer commands và
  Order Saga status command bắt buộc stable UUID cùng positive aggregate IDs trước
  service mutation. Full Delivery 37/37 và Order 52/52 xanh; durable inbox/runtime
  replay vẫn thuộc consumer closeout/B8.
- [x] Restaurant→Search transactional outbox: restaurant/menu mutation methods now
  own DB transactions; entity sync UUID + occurredAt is stored in the existing
  Restaurant outbox in that transaction. Existing relay owns broker ACK/backoff/DEAD;
  Search validates metadata before write/cache eviction and stores a per-entity
  occurredAt/eventId checkpoint so stale DLT replay cannot overwrite a newer
  update/delete. Restaurant 83/83, Search 6/6 xanh; live relay crash/restart/
  Elasticsearch recovery proof remains OPEN.
- [x] Shipper search-sync isolation: because shipper search API is hidden and its
  legacy producer has no outbox, default/application/Compose now pin
  `SHIPPER_SEARCH_SYNC_ENABLED=false`; do not advertise it as active until a durable
  contract and public search use case are approved.
- [x] Static checkpoint sau Notification Flyway V1: full backend reactor 17/17
  module đạt 396 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-23.
  Đây là H2/static proof; không thay PostgreSQL/Kafka/Redis/raw-WebSocket Gate B8.
- [x] Static checkpoint sau Saga Flyway V2: full backend reactor 17/17 module đạt
  402 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-23.
  Gate B8 vẫn mở; chưa có PostgreSQL/Kafka/Redis/raw-WebSocket runtime proof.
- [x] Static checkpoint sau Order Flyway V4: full backend reactor 17/17 module đạt
  407 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-23.
  Đây vẫn là H2/static proof, không thay PostgreSQL/Kafka/Redis/raw-WebSocket B8.
- [x] Static checkpoint sau Restaurant Flyway V2: full backend reactor 17/17
  module đạt 412 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-23.
  Gate B8 vẫn mở; Delivery V5/V9 cần PostgreSQL-specific migration rehearsal.
- [x] Static checkpoint sau Settlement Flyway V2: full backend reactor 17/17
  module đạt 417 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-23.
  Đây vẫn là H2/static proof; PostgreSQL/Kafka/Redis/raw-WebSocket B8 chưa đạt.
- [x] Flyway authority regression gate: `verify-build-baseline.sh` bắt buộc
  Notification/Order/Restaurant/Saga/Settlement giữ `ddl-auto=validate` và
  `spring.flyway.enabled=true`; verifier/Compose/diff tiếp tục PASS.
- [x] Auth executable schema authority: thêm Flyway V1 cho account/session và
  production chuyển `ddl-auto=update` sang `validate`. Clean/legacy schema,
  duplicate email, duplicate refresh token, missing core column và JPA validation
  đều xanh. Refresh-token unique constraint khớp single-session row-lock lookup;
  active-expiry/device indexes khớp repository query. Test profile create-drop tắt
  Flyway để không mixed authority. Full Auth 26/26; PostgreSQL registration/token
  rotation race vẫn thuộc B8.
- [x] Static checkpoint sau Auth Flyway V1: full backend reactor 17/17 module đạt
  423 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-23.
  Flyway verifier khóa thêm Auth; Gate B8 vẫn OPEN.
- [x] User executable schema authority: Flyway V1 sở hữu `users`/`user_addresses`,
  production dùng Hibernate `validate` và test create-drop tắt Flyway. Migration
  preflight duplicate `auth_id`, multiple default addresses, missing columns;
  current-schema metadata tránh va chạm `INFORMATION_SCHEMA.USERS`. Clean/legacy/
  fail-closed/JPA proof đưa User lên 22/22. Default-address service lock giữ nguyên;
  PostgreSQL race/partial unique proof vẫn thuộc B8.
- [x] Static checkpoint sau User Flyway V1: full backend reactor 17/17 module đạt
  429 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-24.
  Flyway verifier khóa bảy service; Gate B8 vẫn OPEN.
- [x] Shipper executable schema authority: Flyway V1 sở hữu fleet/current-location/
  rating tables, production dùng Hibernate `validate`; test create-drop tắt Flyway
  và dedicated schema test bật lại tường minh. Legacy preservation cùng duplicate
  user/license, location, rating-order và missing-column proof đều xanh. Legacy
  write/delete/location capability flags vẫn false. Full Shipper 16/16;
  PostgreSQL concurrency proof vẫn thuộc B8.
- [x] Static checkpoint sau Shipper Flyway V1: full backend reactor 17/17 module
  đạt 436 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-24.
  Flyway verifier khóa tám service; Gate B8 vẫn OPEN.
- [x] Flash Sale executable schema authority: Flyway V1 sở hữu campaign/item
  persistence và query indexes; production Hibernate `validate`, test create-drop
  tắt Flyway. Clean/legacy/missing-column/incomplete-pair/JPA validation đưa suite
  lên 18/18. Không thêm business uniqueness chưa được product phê duyệt; checkout
  và merchant registration tiếp tục off.
- [x] Static checkpoint sau Flash Sale Flyway V1: full backend reactor 17/17 module
  đạt 441 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-24.
  Flyway verifier khóa chín service; Gate B8 vẫn OPEN.
- [x] Analytics executable schema authority: Flyway V1 sở hữu raw event và hai
  daily projection table; production Hibernate chuyển sang `validate`, còn test
  create-drop tắt Flyway. Migration giữ legacy rows, fail-closed với missing core
  column/incomplete table set/duplicate dedup hoặc daily scope. Daily scope dùng
  PostgreSQL 16 `UNIQUE NULLS NOT DISTINCT` để chỉ cho một platform row mỗi ngày
  khi `restaurant_id IS NULL`, khớp repository `Optional` contract. Analytics
  clean 14/14; processing/controller/reconciliation tiếp tục off mặc định.
- [x] Static checkpoint sau Analytics Flyway V1: full backend reactor 17/17 module
  đạt 448 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-24.
  Flyway verifier khóa mười service; đây vẫn là H2/static proof và Gate B8 OPEN.
- [x] Livestream executable schema authority: Flyway V1 sở hữu stream/product/
  event persistence và query indexes; production Hibernate chuyển sang `validate`,
  test create-drop tắt Flyway. Migration giữ legacy rows và fail-closed với
  missing/incomplete schema, duplicate room/channel hoặc duplicate stream-product
  scope. Full Livestream 7/7; ba controller và Agora/Kafka capability vẫn off.
- [x] Static checkpoint sau Livestream Flyway V1: full backend reactor 17/17
  module đạt 454 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-24.
  Flyway verifier khóa mười một service. Chỉ còn Delivery dùng `ddl-auto=update`;
  V5/V9 phải được rehearsal bằng PostgreSQL thật. Gate B8 vẫn OPEN.
- [x] Delivery mixed-schema authority closeout: production Hibernate chuyển sang
  `validate`; xóa deferred SQL init và `schema.sql` duplicate outbox; thay
  `DatabaseFixConfig` startup DDL bằng Flyway V10 drop legacy status constraint
  và thêm shipper/status timeline indexes. V9-compatible migration + JPA schema
  proof trên H2 đưa Delivery lên 39/39. Verifier được sửa để không còn duplicate
  module entry, thực sự khóa Livestream/Delivery, đồng thời chặn Delivery
  `schema.sql` và runtime Java DDL quay lại.
- [x] Static checkpoint sau Delivery V10: full backend reactor 17/17 module đạt
  456 test, không failure/error/skip; build baseline, Compose contract,
  `git diff --check` và reactor package cùng PASS trên JDK 17 ngày 2026-07-24.
  Cả 12 JPA service production dùng Flyway + Hibernate `validate`. PostgreSQL
  clean V1→V10, V5 partial unique và V9 reconciliation proof vẫn OPEN trong B8.
- [x] HTTP inventory drift gate: inventory được đối chiếu lại từ controller source,
  sửa ownership của 13 handler đã tách sang legacy/feature-flag controller và bổ
  sung ba internal eligibility/ownership endpoint bị thiếu. Inventory hiện có
  đúng 194 mapped method. `verify-http-api-inventory.sh` được gọi bởi build
  baseline, kiểm số row, controller tồn tại và handler thuộc đúng controller.
- [x] Gateway notification path hardening: thay generic
  `/api/notifications/{id}` và `/{id}/read` bằng numeric path variables ở read,
  update và delete routes. Regression proof xác nhận numeric IDs vẫn route nhưng
  `future-endpoint` GET/PUT/DELETE không tự động lọt qua public edge. Gateway
  clean 19/19; backend package 17/17 và static gates tiếp tục xanh.
- [x] Architecture authority refresh: backend product overview và root
  `docs/ARCHITECTURE.md` dùng đúng port Compose, PostgreSQL/Kafka KRaft, raw
  WebSocket location (không gRPC), restaurant-confirm-before-match, Saga command,
  Match Redis GEO replica và COD-only disabled promotion/flash checkout. Base
  Compose chỉ publish Gateway cho application traffic; service ports chỉ expose
  trong internal network.
- [x] Kafka orphan cancellation closeout: `delivery.cancelled` không có consumer
  và trùng trách nhiệm với Saga `saga.command.stop-matching`; sau polyrepo
  zero-call-site proof đã xóa Delivery producer/constant/DTO và Match DTO chết.
  Cancellation trước assignment có regression test không phát orphan topic.
  Payment completed/failed được phân loại đúng inactive/feature-gated trong COD
  MVP thay vì active. Focused Delivery 40/40, Match 20/20.
- [x] Static checkpoint sau Kafka orphan cleanup: full backend reactor 17/17 đạt
  457 test, không failure/error/skip; build baseline, HTTP inventory, Compose,
  diff hygiene và package đều PASS trên JDK 17 ngày 2026-07-24. Kafka runtime
  duplicate/restart/crash-window proof vẫn thuộc Gate B8.
- [x] WebSocket MVP transport closeout: raw Tracking `/ws/shipper-locations` qua
  Gateway vẫn là canonical. Delivery STOMP `/ws/delivery-native` và Notification
  STOMP `/ws-native` không có public auth/routing contract nên hidden mặc định;
  base Compose khóa cả `DELIVERY_WEBSOCKET_ENABLED=false` và
  `NOTIFICATION_WEBSOCKET_ENABLED=false`. Delivery disabled handshake trả 404,
  origin không còn wildcard. Client direct-port/STOMP URL cũ được giữ trong
  Phase 8-11 migration queue và chưa sửa trước backend freeze. Focused Delivery
  41/41.
- [x] Static checkpoint sau WebSocket legacy lock: full backend reactor 17/17 đạt
  458 test, không failure/error/skip; build baseline, HTTP inventory, Compose,
  diff hygiene và package đều PASS trên JDK 17 ngày 2026-07-24. PostgreSQL/Kafka/
  Redis/raw-WebSocket runtime rehearsal vẫn thuộc Gate B8.
- [x] JWT external-key/preflight implementation: Auth/Gateway không còn đọc
  `System.getenv` ngoài Spring config; `jwt.*.path` bridge từ `JWT_*_KEY_PATH`
  hỗ trợ classpath local và external filesystem mount. Auth fail-fast khi thiếu,
  sai format hoặc private/public không cùng cặp; Gateway fail-fast khi public key
  không đọc/parse được. `docker-compose.secrets.yml` mount key read-only dưới
  `/run/secrets`, Compose verifier khóa env/path/read-only contract và runtime
  startup harness dùng override này. Focused Auth 4/4, Gateway 7/7.
- [x] Static checkpoint sau JWT external-key/preflight wave: full backend reactor
  17/17 đạt 463 test, không failure/error/skip; build baseline, HTTP inventory,
  base + secret-mount Compose contract, diff hygiene và package đều PASS trên
  JDK 17 ngày 2026-07-24. Mounted-container startup và signed-token handshake
  vẫn thuộc Gate B8 vì Docker daemon chưa hoạt động.
- [x] Runtime dependency URL audit đủ 17 service: `localhost` còn lại trong main
  config chỉ là direct-run fallback, local CORS hoặc payment-provider code đang
  hidden; base Compose override datasource/Kafka/Redis/Elasticsearch và mọi
  cross-service URL bằng Docker DNS. Compose verifier giờ fail tổng quát nếu
  application runtime dependency env quay về `localhost`/`127.0.0.1`; Auth bridge
  tường minh `USER_SERVICE_URL`. Build/HTTP/Compose/diff gates PASS. Online
  payment callback topology tiếp tục OPEN, không được suy ra từ COD MVP.
- [x] Auth/User block-state convergence: polyrepo call-site proof xác nhận web
  chỉ dùng Auth admin block/unblock; User status mutation không có client consumer
  nên bị bỏ khỏi Gateway, giữ làm internal projection endpoint với shared secret
  + ADMIN identity. User command idempotent để retry. Auth block không còn catch/
  log rồi báo thành công; unblock cũng sync User, và sync failure propagate để
  rollback Auth transaction. Focused Auth 18/18, User 11/11, Gateway 13/13.
  Crash sau User commit/trước Auth commit vẫn thuộc runtime recovery Gate B8.
- [x] Static checkpoint sau Auth/User status convergence: full backend reactor
  17/17 đạt 469 test, không failure/error/skip; build baseline, HTTP inventory,
  base + secret-mount Compose contract, diff hygiene và package đều PASS trên
  JDK 17 ngày 2026-07-24. Distributed commit/recovery vẫn chưa được suy ra từ
  focused/H2/static proof.
- [x] Restaurant→Order checkout HTTP closeout: checkout-preview/create bắt role
  USER tại Gateway và Order service, chặn SHIPPER/SHOP_OWNER trước external
  validation/DB/outbox. Dead `RestaurantClient` duplicate và dead Order distance
  helper đã xóa sau zero-call-site proof. Restaurant chỉ giữ atomic internal
  `POST /api/restaurants/validate/order`; ba helper HTTP menu-item/total/hours
  không có consumer đã xóa, item helper thu private. HTTP inventory giảm đúng
  194→191. Focused Order 11/11, Restaurant decision/authorization 7/7,
  Gateway 17/17.
- [x] Static checkpoint sau checkout role/dead-surface closeout: full backend
  reactor 17/17 đạt 468 test source hiện hữu, không failure/error/skip; HTTP
  inventory 191/191, build baseline, base + secret-mount Compose, diff hygiene và
  package đều PASS trên JDK 17 ngày 2026-07-24. Đây là authoritative total của
  full run hiện tại, không suy diễn từ checkpoint trước.
- [x] Fulfilment dead-surface closeout: xóa legacy PostgreSQL shipper-location
  controller/service/entity/repository/DTO/mapper, Match debug controller và
  Tracking diagnostics/fleet/distance/busy REST sau polyrepo zero-call-site proof.
  Legacy Shipper table/migration được giữ, không drop dữ liệu. Canonical flow là
  Tracking raw WebSocket + Redis GEO → Kafka replica → Match one-shipper offer →
  Delivery accept/reject/cancel/rematch. HTTP inventory giảm 191→180; focused
  clean proof Shipper 17/17, Match 24/24, Tracking 13/13.
- [x] Clean-build determinism closeout: full `mvn clean test` lộ MapStruct
  generated class ở Delivery rồi Order bị IDE/JDT ghi problem bytecode dù compile
  báo success. Delivery/Order/Shipper chuyển sang source mapper tường minh có
  parity tests; Match bỏ dependency MapStruct không dùng; build baseline chặn
  MapStruct quay lại. Clean Delivery 43/43, Order 63/63, Shipper 17/17.
- [x] Static checkpoint sau fulfilment + clean-build closeout: full backend
  reactor 17/17 đạt 472 test source hiện hữu, không failure/error/skip; package,
  build baseline, HTTP inventory 180/180, base + secret-mount Compose contract và
  diff hygiene đều PASS trên JDK 17 ngày 2026-07-25. Gate B8 vẫn OPEN vì chưa có
  PostgreSQL/Kafka/Redis/raw-WebSocket runtime rehearsal.
- [x] Cross-service dead-artifact closeout: xóa Restaurant menu-catalog contract/
  DTO không implementation/caller và implementation 350+ dòng đã comment; Auth
  DTO rỗng/sai tên, Shipper balance DTO không caller, bốn Livestream duplicate
  empty config file và hai Restaurant empty mapper. Delivery REST tracking
  response/service/mapper + location DTO chỉ còn sau commented endpoints cũng bị
  xóa; raw Tracking WebSocket vẫn là canonical. Verifier mới chặn Java source
  thiếu package declaration. HTTP inventory không đổi 180.
- [x] Static checkpoint sau dead-artifact closeout: full backend reactor 17/17
  vẫn đạt 472 test source hiện hữu, không failure/error/skip; package, build
  baseline, HTTP inventory 180/180, base + secret-mount Compose contract và diff
  hygiene đều PASS trên JDK 17 ngày 2026-07-25. Gate B8 runtime vẫn OPEN.
- [x] Flash Sale false-compensation closeout: canonical `order.cancelled` không
  có item list nhưng listener cũ giả định có và chỉ warning/no-op khi bật checkout.
  Xóa listener cùng release Lua/method chỉ nó gọi; checkout vẫn false. Workflow và
  Kafka inventory nay ghi đúng target: reservation record + stable identity +
  outbox/replay proof phải tồn tại trước khi bổ sung compensation/mở feature.
- [x] Kafka/product documentation convergence: source còn 15 listener class/34
  handler sau false-consumer cleanup. Viết lại Saga design và sửa root overview,
  backend MVP context, Kafka inventory theo canonical restaurant-confirm-before-
  match → `saga.command.find-shipper` → `shipper.found/not-found` → persisted
  offer → `delivery.shipper-offered`; raw WebSocket là location transport duy nhất.
  `shipper.matched`/`no.shipper.available` nay ghi đúng removed.
- [x] Shipper-offer reachability closeout: Delivery persist
  `offeredShipperId + offerExpiresAt`, phát `delivery.shipper-offered` qua outbox;
  Notification lưu durable `MATCH_FOUND` inbox và FCM chỉ là optional wake-up.
  `shipper_app2` recover self-offer qua authenticated
  `GET /api/deliveries/offers/current` lúc startup/foreground, không dùng active
  assignment list hay STOMP. Canonical COD harness đã bắt buộc quan sát inbox,
  recover exact offer rồi mới accept; `/ws-native` broker graph nay đã xóa hoàn
  toàn. Native device E2E vẫn được theo dõi riêng ở Phase 11, không làm mở lại
  backend offer contract.
- [x] Saga→Order correlation closeout: `SHIPPER_NOT_FOUND` consumer trước đây
  nuốt lỗi parse `originalEvent`, có thể ACK command thiếu `deliveryId`; mọi Saga
  order-status command nay bắt JSON object và từ chối inner `orderId` mâu thuẫn.
  `SHIPPER_NOT_FOUND` bắt positive delivery identity, còn generic Saga
  compensation enrich `deliveryId` đã persist trước khi ghi Order command.
  Clean proof: Order 67/67, Saga 32/32; malformed/missing/contradictory payload
  không ACK, payload correlated ACK đúng một lần.
- [x] Static checkpoint sau Saga→Order correlation và Flash Sale false-consumer
  cleanup: full backend reactor clean test đạt 17/17 module, 477 test trong 154
  suite, không failure/error/skip; build baseline, HTTP inventory 180/180, base +
  secret-mount Compose contract và diff hygiene đều PASS trên JDK 17 ngày
  2026-07-25. Gate B8 vẫn OPEN vì đây là H2/static proof.
- [x] Notification dead/unsafe surface closeout: giữ nguyên 10 HTTP handler đang
  dùng và exact Gateway routes; xóa bảy NotificationService helper, broadcast/
  topic push, typing/status WebSocket helper, cache/session helpers và
  `WebSocketController` cho client tự ghi session bằng arbitrary `{userId}` sau
  polyrepo zero-call-site proof. Constants mồ côi và service README quảng bá
  `/ws`/MapStruct/listener legacy cũng được dọn. Clean Notification 32/32; HTTP
  inventory không đổi 180 vì message mappings không phải HTTP controller. Full
  reactor package 17/17, build baseline, HTTP inventory, Compose và diff hygiene
  đều PASS trên JDK 17.
- [x] Notification configured-provider failure closeout: Firebase optional/no
  credential vẫn không chặn inbox; nhưng khi đã cấu hình, Redis lookup hoặc FCM
  error khác `UNREGISTERED` nay propagate thay vì bị nuốt rồi đánh dấu SENT.
  NotificationService proof xác nhận push failure giữ durable row PENDING và
  không ACK qua listener boundary. Clean Notification 36/36; full reactor package
  17/17, build/HTTP/Compose/diff gates PASS. Multi-token/channel partial success
  còn at-least-once, phải mang stable notification ID và kiểm client dedup trong
  authenticated offer/reconnect rehearsal ở Gate B8.
- [x] Search immutable replay closeout: checkpoint `entity-sync` trước đây không
  fingerprint payload, nên cùng `eventId` với payload bị đổi có thể overwrite
  document. Checkpoint nay giữ canonical SHA-256 của identity/action/time/payload;
  exact crash retry được reapply, contradictory same-ID metadata/payload bị reject,
  stale ID tiếp tục fenced và checkpoint cũ được upgrade chỉ khi metadata khớp.
  Clean Search 9/9, focused Restaurant outbox producer 1/1; Elasticsearch live
  refresh/multi-instance/recovery vẫn thuộc Gate B8. Full reactor package 17/17,
  build baseline, HTTP inventory 180/180, Compose và diff hygiene đều PASS.
- [x] Shipper explicit-offline convergence closeout: canonical matching đọc
  Tracking raw WebSocket/Redis -> `shipper.location-updated` -> Match Redis, dùng
  JWT `userId` làm fulfilment shipper identity; khóa `shipper.id` chỉ là profile
  record ID. Tracking explicit offline giờ cập nhật/xóa Redis fail-closed rồi
  publish tombstone không bắt tọa độ; Match remove GEO/online theo timestamp và
  không ACK Redis failure. Tracking Redis cũng không giữ offline shipper trong
  GEO, kể cả cached record thiếu một tọa độ; Redis read failure không bị hiểu nhầm
  là cache miss. Clean proof cuối: Tracking 20/20, Match 28/28 ngày 2026-07-25.
- [x] Static checkpoint sau Notification/Search và explicit-offline closeout:
  full backend reactor clean test đạt 17/17 service, 492 test trong 155 suite,
  không failure/error/skip; reactor package 17/17, build baseline, HTTP inventory
  180/180, Compose contract và diff hygiene đều PASS trên JDK 17 ngày 2026-07-25.
  Đây vẫn là H2/static proof; PostgreSQL/Kafka/Redis/raw-WebSocket runtime Gate B8
  tiếp tục OPEN. Ba Tracking edge regression bổ sung sau reactor checkpoint đạt
  focused clean 20/20; reactor tổng mới sẽ được làm mới ở checkpoint kế tiếp.
- [x] Authenticated offer recovery + timeout convergence wave: backend expose
  exact self-only `GET /api/deliveries/offers/current` qua Gateway `SHIPPER`, chỉ
  trả một offer chưa hết hạn và fail-closed nếu one-offer invariant bị vỡ.
  Delivery persist offer nay chuyển canonical `FINDING_SHIPPER ->
  WAIT_SHIPPER_CONFIRM`; accept chỉ từ WAIT và exact replay idempotent. Saga
  timeout phát `saga.command.expire-shipper-offer` mang exact
  delivery/shipper/deadline generation; Delivery lock row, clear đúng offer cũ
  rồi về FINDING, stale timeout không thể xóa offer mới/assignment. Notification
  giữ durable inbox và đổi wake-up từ STOMP hidden sang FCM best-effort. Client
  migration và live Kafka/PostgreSQL/FCM/reconnect proof vẫn OPEN ở Phase 11/B8.
  Clean focused proof: Gateway 21/21, Delivery 52/52, Saga 32/32, Notification
  36/36. Full backend reactor clean checkpoint ngày 2026-07-25 đạt 17/17 service,
  503 test trong 156 suite, không failure/error/skip; package 17/17, HTTP inventory
  178/178, build baseline, Compose contract và diff hygiene đều PASS trên JDK 17.
  Đây vẫn là static/H2 proof, không thay Gate B8 runtime.
- [x] Shipper disconnect/read-model convergence: backend có raw WebSocket
  publisher lease/generation, disconnect grace và expiry sweeper; `shipper_app2`
  đã dùng Gateway `8079` và từ wave client này đóng location socket khi app vào
  background, reconnect khi foreground để kích hoạt lease/grace offline/online.
  Không gửi tombstone từ client; Tracking vẫn là authority phát tombstone sang
  Match. Live proof cũ đã đóng same/cross-instance, restart/crash, reorder,
  reconnect, multi-session và expiry. Current canonical rerun ngày 2026-07-27
  chứng minh generation `1→2→3`, publisher cũ đóng policy `1008`, reconnect giữ
  online quá grace 35 giây; sau final disconnect shipper fixture `75` còn online
  trong grace rồi bị tombstone xóa khỏi cả Tracking GEO/online và Match GEO/online;
  log hậu kiểm không có consumer/DLT/reconcile error.
- [x] Delivery status/admin mutation closeout: polyrepo call-site search ngày
  2026-07-25 xác nhận chỉ `shipper_app2` gọi
  `PUT /api/deliveries/{id}/status`, theo đúng ba bước `PICKED_UP`, `DELIVERING`,
  `DELIVERED`; `delivery_web` chỉ còn constant không caller và Flutter không có
  mutation. Gateway/service đã thu về exact SHIPPER+self command; ADMIN, shortcut
  assign/rematch và `CANCELLED` bị chặn. Exact same-state retry trả persisted
  response mà không save, socket push hoặc tạo lại status/completion outbox;
  cancellation giữ command `cancel-assignment` riêng để giải phóng availability
  và rematch. HTTP inventory vẫn 178 method. Clean proof: Delivery 56/56, Gateway
  21/21 ngày 2026-07-25; PostgreSQL row-lock và Kafka relay rehearsal còn ở B8.
- [x] Static checkpoint sau shipper-status boundary: full clean backend reactor
  đạt 17/17 service, 507 test trong 156 suite, không failure/error/skip; package
  17/17, HTTP inventory 178/178, build baseline, Compose contract và diff hygiene
  đều PASS trên JDK 17 ngày 2026-07-25. Gate B8 vẫn OPEN vì đây là H2/static
  proof, chưa phải runtime PostgreSQL/Kafka/Redis/raw-WebSocket/COD.
- [x] Delivery read/current-offer route closeout: Gateway từng gộp
  `/offers/current` với history/active dưới `SHIPPER,ADMIN`, dù service offer chỉ
  cho SHIPPER. Đã tách exact current-offer SHIPPER-only; history/active giữ
  SHIPPER-self hoặc ADMIN support. Service list nay bắt cả role và path identity,
  chặn USER/SHOP_OWNER có numeric ID trùng và shipper xem chéo. Owned ID/order
  reads giữ creator/assigned shipper/restaurant owner/ADMIN contract. Focused
  clean proof Delivery 58/58, Gateway 21/21 ngày 2026-07-25; HTTP count không đổi.
- [x] Phase 11 shipper history contract closeout: client không còn gửi `page`/
  `limit` giả hoặc fallback `/api/deliveries?mine=true`; chỉ gọi exact scoped
  `/api/deliveries/shipper/{shipperId}` rồi lọc status trên bounded response tối
  đa 100 row. Contract test khóa một request canonical duy nhất.
- [x] Legacy Delivery manual-assignment removal: polyrepo không có consumer cho
  `POST /api/deliveries/assign`; Gateway đã deny và canonical assignment chỉ đi
  qua restaurant-confirm -> Saga -> one-offer -> shipper accept. Hidden code cũ
  còn comment role check, không validate DTO, tạo thẳng `ASSIGNED` ngoài lock/
  outbox. Đã xóa controller, DTO, service/mapper branch, repository query và
  feature flag/Compose env; Gateway regression tiếp tục bắt route false. HTTP
  inventory 178 -> 177; focused clean Delivery 58/58, Gateway 21/21, Compose và
  inventory verifiers PASS ngày 2026-07-25.
- [x] Delivery dead Redis dependency closeout: module không có Redis caller ngoài
  config tự tạo template/listener nhưng vẫn ép startup kết nối và best-effort sửa
  global keyspace notification. Canonical offer timeout đã thuộc Saga exact
  expire-generation command, nên đã xóa Redis config/starter/properties/test
  toggles cùng Compose host/dependency; verifier khóa Redis không quay lại Delivery.
  Legacy Delivery STOMP vẫn hidden và được tách thành contract-removal/client-
  migration decision riêng vì Flutter còn direct-port subscription reference.
- [x] Delivery hidden-STOMP no-op closeout: flag false trước đây vẫn khởi động
  simple broker và mutation vẫn publish vào topic không subscriber. Config nay
  conditional toàn phần; notifier dùng optional template và no-op khi disabled;
  context proof khóa không có broker/config/template ở runtime mặc định. README
  Delivery stale đã viết lại theo REST/Kafka/Tracking ownership hiện tại. Không
  mở STOMP trước auth/subscription ownership và migration authority.
- [x] Static checkpoint sau Delivery read/assignment/Redis/STOMP closeout: full
  clean backend reactor đạt 17/17 service, 509 test trong 156 suite, không
  failure/error/skip; build baseline, HTTP inventory 177/177, Compose contract
  và diff hygiene đều PASS trên JDK 17 ngày 2026-07-25. Đây vẫn là H2/static
  proof; Docker daemon không hoạt động nên không thay thế PostgreSQL/Kafka/Redis/
  raw-WebSocket/COD runtime Gate B8.
- [x] Shipper-not-found convergence closeout: Saga trước đây gửi raw
  `shipper.not-found` vào `saga.command.cancel-delivery`, nên Delivery listener
  parse như order cancellation và có thể ghi `CANCELLED` trong khi Order ghi
  `SHIPPER_NOT_FOUND`; method Delivery terminal đúng lại không có listener gọi.
  Tách command `saga.command.mark-shipper-not-found`, bắt stable event ID + positive
  order/delivery identity, khóa row và chuyển đúng terminal state; cancellation
  command chỉ còn cho cancellation/failure trước matching. Đồng thời xóa ba
  repository query unbounded không caller và dùng query cap 1 cho accept guard.
  Focused clean proof: Delivery 60/60, Saga 33/33 ngày 2026-07-25; runtime
  cross-topic replay/order vẫn thuộc Gate B8. Full clean checkpoint sau wave đạt
  17/17 service, 512 test trong 156 suite, không failure/error/skip; build
  baseline, HTTP inventory 177/177, Compose contract và diff hygiene đều PASS.
- [x] Saga timeout query-safety closeout: scheduler trước đây load toàn bộ saga
  stuck cho bốn status mỗi 30 giây và một aggregate lỗi làm dừng phần còn lại.
  Query nay deterministic, cap cấu hình 1..500 (mặc định 100), poll dùng fixed
  delay cấu hình được và cô lập lỗi theo từng saga để retry vòng sau. Xóa bốn
  repository finder không có caller. Clean Saga proof 34/34; PostgreSQL
  multi-instance lock/restart rehearsal vẫn thuộc Gate B8.
- [x] Order dashboard dead-graph removal: `/api/dashboard/**` của Order không có
  Gateway/client consumer, web chỉ tham chiếu Analytics dashboard và capability
  đó đang hidden riêng. Xóa controller, service, response DTO, feature flag/
  Compose env, toàn bộ dashboard aggregate query cùng năm repository query
  unbounded/không caller. HTTP inventory 177 -> 174; clean Order proof 66/66.
  Analytics vẫn không được mở trước idempotency/runtime/ownership gate.
- [x] Restaurant hidden ops/location dead-graph removal: polyrepo không có caller
  cho sáu `/api/cache/**` warmup/arbitrary-availability route hoặc hai
  `/api/location/**` geocoding route. Xóa hai controller, warmup/location service,
  Mapbox/Gson backend dependency/config, capability flags, availability helper
  chỉ chúng gọi và các repository finder unbounded/dead; repository tests chuyển
  sang bounded overload. Canonical create/update/delete vẫn đồng bộ Redis/Search,
  checkout validation giữ cache + DB fallback. HTTP inventory 174 -> 166; clean
  Restaurant proof 87/87. Full clean checkpoint sau Saga/Order/Restaurant wave
  đạt 17/17 service, 512 test trong 157 suite, không failure/error/skip; build
  baseline, HTTP inventory 166/166, Compose contract và diff hygiene đều PASS
  trên JDK 17 ngày 2026-07-25.
- [x] Shipper rating read identity closeout: `GET /api/shippers/{shipperId}/ratings`
  không có Gateway/client caller và nhận profile DB ID trong khi fulfilment dùng
  auth user ID, nên đã xóa route/service branch cùng unbounded repository overload.
  Self `GET /api/shippers/me/ratings` nay bắt service-level SHIPPER trước lookup,
  resolve trusted userId -> profileId và cap 100. Rating write tiếp tục hidden vì
  Flutter còn reference nhưng chưa có delivered-order/customer/shipper proof.
  HTTP inventory 166 -> 165; clean Shipper proof 19/19.
- [x] User request/error boundary closeout: xác minh lại identity chain và bác bỏ
  giả thuyết drift — Auth phát JWT bằng `AuthAccount.userId`, chính là `User.id`,
  không dùng `AuthAccount.id`; regression cố ý dùng accountId=3/profileId=17 khóa
  contract này. Profile/address DTO nay enforce positive/valid identity metadata,
  schema-length, required checkout address fields, DOB và coordinate bounds;
  validation/404/500 dùng `BaseResponse`, lỗi bất ngờ không leak message nội bộ.
  Xóa ba repository finder không caller sau polyrepo search. Clean User 30/30,
  Auth identity suite 13/13. Full clean reactor checkpoint ngày 2026-07-25 đạt
  17/17 service, 520 test trong 160 suite, không failure/error/skip; build baseline,
  HTTP inventory 165/165, Compose contract và diff hygiene đều PASS.
- [x] Auth session/dead-lookup closeout: login cùng device và admin block không
  còn load danh sách session active không giới hạn rồi save từng row; repository
  bulk-update theo account/device, có JPA proof cho selective revoke và revoke-all.
  Xóa ba finder không caller (`existsByEmail`, refresh-token finder không lock và
  hai session-list finder được bulk update thay thế). Internal
  `GET /api/auth/accounts/email/{email}` không có backend/client caller nên xóa
  controller/service/security allow-list; web constant chết được giữ cho client
  migration sau freeze. Social login không còn catch mọi lỗi rồi biến User
  provisioning outage thành 401 invalid-credentials; lỗi provider vẫn 401, lỗi
  dependency đi đúng 500 sanitized. Clean Auth 35/35; reactor package 17/17,
  HTTP inventory 164/164, build baseline, Compose contract và diff hygiene PASS.
  Full clean reactor gần nhất vẫn là checkpoint 520/160 ngay trước wave Auth này;
  không cộng ghép focused test thành full-reactor total mới.
- [x] Notification cache/ownership/offer closeout: xóa graph Redis cache
  notification write-only khiến mutation DB phụ thuộc Redis dù không có read
  path; mark-read nay idempotent, chỉ update bản ghi owned chưa đọc và không ghi
  đè `readAt` khi retry. Offer wake-up không còn tự suy giá/thời gian từ khoảng
  cách mà chỉ báo shipper mở canonical current-offer endpoint; listener bắt
  positive identity, finite/nonnegative distance, đúng một shipper và stable event
  ID. FCM registration Lua bỏ TTL lệch giữa owner key và user-token membership,
  tránh token có thể được gán lại trong khi membership cũ còn tồn tại; token được
  giữ đến explicit unregister hoặc Firebase `UNREGISTERED`. Đồng thời xóa DTO,
  mapper/constructor/helper và notification cache constants không caller; STOMP
  Notification tiếp tục hidden mặc định, không thay raw WebSocket tracking.
  Clean Notification 39/39; reactor package 17/17, HTTP inventory 164/164, build
  baseline, Compose contract và diff hygiene PASS trên JDK 17 ngày 2026-07-25.
  Full clean reactor gần nhất vẫn là checkpoint 520 test/160 suite trước hai wave
  Auth/Notification; package/focused proof không được cộng thành full-clean total.
- [x] Settlement COD integrity/dead-surface closeout: ledger completion trước đây
  cho phép debit vượt deposit và integration test còn khóa số dư âm `-120000`;
  nay COD debit fail-closed khi thiếu ký quỹ, rollback receipt + toàn ledger và
  không ACK để record đi retry/DLT. Replay test seed đúng deposit canonical và
  regression thiếu một đồng xác nhận không có partial posting. Xóa hai endpoint
  debug `recalculate` không có caller cùng thuật toán rebuild sai trạng thái
  pending/failed/reversed; xóa tám repository query, một service helper và fixed
  minimum constant không caller. Sửa VNPay return URL mặc định từ port stale 8095
  sang port service 8090 nhưng payment vẫn disabled. Tài liệu Settlement/root
  architecture nay phản ánh internal eligibility -> one-offer -> completion-only
  COD ledger; inventory giảm 164 -> 162. Clean Settlement 27/27; reactor package
  17/17, HTTP inventory 162/162, build baseline, Compose contract và diff hygiene
  PASS trên JDK 17 ngày 2026-07-25. PostgreSQL concurrent replay/Kafka crash-window
  vẫn OPEN ở Gate B8; full clean reactor gần nhất vẫn là 520 test/160 suite trước
  Auth/Notification/Settlement waves.
- [x] Promotion/Flash Sale hidden-capability closeout: phát hiện Promotion là JPA
  service duy nhất còn `ddl-auto=update` dù checkpoint cũ từng ghi nhầm chỉ còn
  Delivery. Flyway V1 nay sở hữu voucher/user-wallet/group/exclusion schema,
  preflight clean/legacy/missing/incomplete/duplicate identity, tạo exact unique/
  query indexes và Hibernate chỉ `validate`; build verifier khóa cả YAML config.
  Promotion `calculate` trả 503 khi checkout tắt thay vì trả discount 0 gây hiểu
  nhầm; create voucher chặn time window đảo và map concurrent code unique race
  sang 409; xóa năm finder không caller. Flash Sale runtime mặc định không còn
  tạo custom Redis config/stock service khi checkout off, internal reserve vẫn
  fail-closed 503 nếu graph vắng; recurring stock reset dùng một DB bulk update
  thay vì load toàn bộ item, đồng thời xóa finder unbounded không caller. Public
  campaign/admin catalog và voucher collect/wallet retained surface không đổi;
  reserve algorithms vẫn không đủ authority để bật checkout. Clean Promotion
  21/21 và Flash Sale 20/20. Full clean reactor checkpoint mới đạt 17/17 service,
  537 test trong 164 suite, không failure/error/skip; reactor package 17/17,
  HTTP inventory 162/162, build baseline, Compose contract và diff hygiene PASS
  trên JDK 17 ngày 2026-07-25. PostgreSQL migration/concurrent collect và Redis/
  Kafka reservation recovery vẫn thuộc disabled-feature runtime gate.
- [x] Search availability/dead-cache closeout: xóa Redis cache graph không có
  external contract, loại deserialize `Page` từ `Object` không type-safe và
  blocking `KEYS` eviction; Compose/verifier khóa Search chỉ còn Kafka +
  Elasticsearch. Restaurant/dish query trả sanitized 503 khi Elasticsearch tắt
  hoặc lỗi thay vì `200 []`. Consumer fail-closed nếu repository vắng để Kafka
  retry/DLT, và chặn shipper projection phía nhận trước checkpoint/document khi
  capability hidden. Clean Search 15/15 trên JDK 17; live relay recovery và
  multi-instance checkpoint CAS vẫn thuộc Gate B8. Full clean reactor checkpoint
  mới đạt 17/17 service, 543 test trong 165 suite, không failure/error/skip.
- [x] Shipper-status consumer replay/dead-graph closeout: Tracking consumer
  `shipper.status-change` chỉ ghi busy key không có read path trong toàn polyrepo,
  nên listener/DTO/service/repository graph đã xóa; Match là consumer duy nhất cần
  availability. Match validate stable eventId + positive shipper/delivery/order/
  timestamp và áp dụng release đúng offer, BUSY/AVAILABLE, version fence trong
  một Redis Lua operation. Exact/stale replay no-op; cùng timestamp khác event ID
  fail-closed. Source giảm còn 14 listener class/35 handler; clean Tracking 17/17
  và Match 32/32. Redis live serialization/reorder/restart proof vẫn thuộc B8.
  Full clean reactor checkpoint đạt 17/17 service, 544 test trong 164 suite,
  không failure/error/skip.
- [x] Order consumer retry/DLT closeout: audit đủ 5 handler xác nhận hai payment
  handler không tạo bean trong COD-only runtime; ba handler active dùng row lock,
  transition guard, stable identity và durable restaurant-decision receipt nhưng
  container còn `DefaultErrorHandler()` không recoverer rõ ràng. Order nay retry
  hai lần rồi publish cùng key/partition sang `<source-topic>.DLT`, DLT send lỗi
  tiếp tục fail-closed; config proof và build verifier khóa policy. Clean Order
  68/68; full clean reactor đạt 17/17 service, 546 test trong 164 suite, không
  failure/error/skip. Kafka/PostgreSQL crash-window rehearsal vẫn thuộc Gate B8.
- [x] Delivery command ACK/failure-idempotency closeout: audit 5 Saga command
  handler phát hiện create/cancel đặt ACK trong broad catch; ACK lỗi sau commit có
  thể bị biến thành `delivery.created.failed`/`delivery.cancel.failed` giả và kích
  hoạt compensation sai. ACK nay tách khỏi business-failure mapping. Failure
  outbox dùng source command UUID, exact replay không tạo duplicate và cùng ID
  khác payload fail-closed. Kafka config chuẩn hóa finite retry + same-partition
  DLT thành bean có config proof; DLT send fail-closed và build verifier khóa
  policy. Clean Delivery 67/67; full clean reactor đạt 17/17 service, 553 test
  trong 165 suite, không failure/error/skip; reactor package, build baseline,
  HTTP inventory 162/162, Compose contract và diff hygiene đều PASS trên JDK 17
  ngày 2026-07-25. PostgreSQL/Kafka crash-window vẫn thuộc Gate B8.
- [x] Notification consumer retry/DLT closeout: ba listener active đã validate
  stable aggregate/event identity, không ACK khi persistence/provider lỗi và dùng
  durable unique dedup key; Kafka recoverer trước đây chỉ được tạo inline, chưa
  có boundary để test/khóa. Recoverer và error handler nay là bean riêng, retry
  hai lần rồi giữ nguyên topic source/key/value và partition sang
  `<source-topic>.DLT`; DLT send lỗi fail-closed, build verifier khóa policy.
  Clean Notification 42/42; PostgreSQL/Kafka concurrent replay và multi-token FCM
  partial-delivery rehearsal vẫn thuộc Gate B8.
- [x] Match projection retry/DLT closeout: `shipper.location-updated` và đặc biệt
  `shipper.status-change` trước đây dùng finite retry nhưng không có recoverer;
  record hết retry có thể bị bỏ, khiến Redis projection không nhận BUSY và tạo
  nguy cơ shipper tiếp tục được match. Common handler nay giữ retry hiện hữu rồi
  chuyển nguyên topic source/key/value sang cùng partition `.DLT`; DLT send lỗi
  fail-closed, config proof và build verifier khóa policy. `find-shipper` tiếp tục
  dùng retry-topic/DLT riêng. Clean Match 34/34; live Redis/Kafka DLT replay và
  reorder/restart vẫn thuộc Gate B8. Full clean reactor sau Notification/Match
  wave đạt 17/17 service, 558 test trong 166 suite, không failure/error/skip;
  reactor package, build baseline, HTTP inventory 162/162, Compose contract và
  diff hygiene đều PASS trên JDK 17 ngày 2026-07-25.
- [x] Saga/Settlement consumer config closeout: listener business boundaries đã
  fail-closed và Settlement tiếp tục giữ `MANUAL` ACK trong transaction tài chính;
  hai recoverer inline nay được tách thành bean có exact topic/partition/key/value
  DLT proof. Cả hai retry hai lần, publish cùng partition `.DLT`, DLT send lỗi
  fail-closed và build verifier khóa policy. Clean Saga 36/36, Settlement 30/30;
  live PostgreSQL/Kafka crash-after-commit và DLT operational replay vẫn thuộc B8.
  Full clean reactor đạt 17/17 service, 563 test trong 167 suite, không
  failure/error/skip; reactor package, build baseline, HTTP inventory 162/162,
  Compose contract và diff hygiene đều PASS trên JDK 17 ngày 2026-07-25.
- [x] Hidden-capability query/dead-graph closeout: Livestream vẫn hidden nhưng ba
  livestream list và hai product list trước đây tải không giới hạn; nay cap 100,
  xóa ba legacy repository query cùng JPA event entity/repository/enum không caller
  (giữ bảng migration lịch sử để upgrade không bị phá). Settlement bỏ payment-order
  list không caller. Analytics reconciliation disabled trước đây tải toàn bộ raw
  event một ngày; nay page 500 theo ID và cộng dồn platform/per-restaurant aggregate
  không làm sai số liệu. Clean Livestream 9/9, Analytics 15/15; build verifier khóa
  bounded/paged boundary. Các capability này vẫn không được mở trong MVP. Full
  clean reactor đạt 17/17 service, 566 test trong 170 suite, không
  failure/error/skip; reactor package, build baseline, HTTP inventory 162/162,
  Compose contract và diff hygiene đều PASS trên JDK 17 ngày 2026-07-25.
- [x] Residual dead-method closeout: declaration/reference scan trên toàn bộ
  backend tìm được Delivery active-count query và Livestream room lookup chỉ tồn
  tại declaration; polyrepo search không có caller nên đã xóa. Tracking orphan
  `getShipperLocation` wrapper không còn controller/caller, nuốt Redis failure
  thành empty và mâu thuẫn raw-WebSocket source-of-truth nên cũng đã xóa; internal
  cached-location read cho offline tombstone vẫn giữ. Build verifier khóa không
  phục hồi các graph này; focused Delivery 67/67 và Livestream 9/9 xanh.
- [x] Hidden capability fail-safe default closeout: inventory toàn bộ
  `@ConditionalOnProperty` xác nhận Analytics processing/dashboard, Delivery
  STOMP, payment/fake provider, Settlement self/admin mutation, Livestream,
  voucher/Flash checkout, shipper search/sync và legacy mutation controllers đều
  không `matchIfMissing`; config dùng explicit `${ENV:false}`. Build verifier nay
  khóa từng default và chỉ allow `matchIfMissing=true` cho bốn transactional
  outbox relay core. Compose vẫn khóa false lần hai; không route hidden nào được mở.
- [x] Shipper legacy delete/rating controller closeout (2026-07-28): deleted
  `LegacyShipperDeleteController` và `LegacyShipperRatingWriteController` sau
  zero-call-site proof; shipper app chỉ còn `create/profile/update/online` và
  read-only ratings surface. Shipper application test đổi sang assert bean-name
  absence, `docs/http-api-inventory.md` giảm 160→158 methods và đổi classification
  cho shipper delete/rating thành `dead/deleted`. Proof: `shipper-service` `mvn
  test`, `scripts/verify-http-api-inventory.sh`, `scripts/verify-mvp-polyrepo-contract.sh`,
  `git diff --check` PASS. Dead feature flags remain intentionally in
  `shipper-service/src/main/resources/application.properties` and compose/env for
  now so the baseline scripts continue to reflect the current config contract.
- [x] Auth refresh-expiry consistency closeout: refresh JWT trước đây nhân thêm
  `* 10` thành 70 ngày dù durable `AuthSession.expiresAt` ở login/social/rotation
  đều 7 ngày và comment cũng ghi 7. JWT claim nay dùng `Duration.ofDays(7)` nên
  khớp effective session authority; test decode `iat/exp` khóa đúng bảy ngày và
  build verifier chặn debug multiplier quay lại. Tại checkpoint này access JWT
  100 ngày còn OPEN; authority 15 phút đã được user chốt và triển khai ở wave kế
  tiếp bên dưới.
  Full clean reactor hiện tại đạt 17/17 service, 567 test trong 170 suite, không
  failure/error/skip; reactor package, build baseline, HTTP inventory 162/162,
  Compose contract và diff hygiene đều PASS trên JDK 17 ngày 2026-07-25.
- [x] Confirmed auth/account lifecycle authority: access JWT mặc định 15 phút
  (`JWT_ACCESS_TOKEN_TTL_SECONDS=900`), refresh/session 7 ngày. User profile không
  được hard-delete: controller DELETE path-ID, service/repository delete branch,
  flag/config/test graph không caller đã xóa; HTTP inventory 162 -> 161. Canonical
  admin block tiếp tục deactivate Auth + User projection và revoke sessions;
  self-deactivate tương lai phải do Auth orchestration sở hữu, không mở direct
  User mutation. Access/refresh `iat`/`exp`, Compose config và build guard đã khóa
  policy này.
- [x] Confirmed Tracking publisher-session implementation: một shipper có đúng
  một publisher generation trong Redis; connection mới `INCR` generation và
  supersede/fence connection cũ, mọi `ping`/`update_location` phải refresh exact
  `generation:sessionId`. Current close atomically xóa active lease và lưu
  deadline grace 30 giây trong Redis; reconciler chỉ phát offline tombstone nếu
  generation không đổi và chưa có active lease;
  reconnect trong grace hoặc close của session cũ không thể làm session mới
  offline. Lease TTL 120 giây được refresh để fence publisher treo. Clean Tracking
  22/22; Redis Lua serialization, cross-instance reconnect và restart/crash proof
  tiếp tục thuộc runtime Gate B8. Full clean backend reactor sau cả Auth/User và
  Tracking wave đạt 17/17 service, 573 test trong 172 suite, không
  failure/error/skip; reactor package, build baseline, HTTP inventory 161/161,
  Compose contract và diff hygiene đều PASS trên JDK 17 ngày 2026-07-25.
- [x] Tracking runtime recovery closeout: raw WebSocket same-instance proof PASS
  (`generation 1→2→3`, old close `1008`, reconnect giữ online quá grace 35s rồi
  disconnect phát tombstone). Cross-instance proof dùng hai container chung
  Redis/Kafka PASS (`generation 8→9`, publisher cũ instance A bị fence `1008`).
  Hard SIGKILL giữ active lease tới TTL rồi instance restart claim deadline và
  xóa Tracking/Match GEO. Race clean-disconnect cũng được đóng: deadline gen9
  được lưu Redis, peer bị SIGKILL ngay trong grace khi cả hai GEO vẫn online;
  main instance claim deadline và phát offline tombstone. Release/claim/complete
  Lua dùng score-CAS để refresh hoặc generation mới không bị claim cũ xóa nhầm.
  Focused Tracking clean package đạt 25/25. Full clean backend reactor sau wave
  đạt 17/17 service, 578 test trong 174 suite, 0 failure/error/skip; reactor
  package, build baseline, HTTP inventory 161/161, Compose contract và diff
  hygiene đều PASS trên JDK 17 ngày 2026-07-26.
- [x] Kafka storage persistence closeout: base Compose mount named
  `kafka_data` tại `/var/lib/kafka/data`, tên mặc định
  `backend_delivery_kafka_data` và hỗ trợ `KAFKA_VOLUME_NAME` override; Compose
  guard khóa type/target/default name. Rehearsal tạo topic/keyed marker, stop và
  xóa hoàn toàn broker container, recreate bằng cùng named volume rồi đọc lại
  đúng marker; topic ID không đổi trên volume B8 cô lập. Lặp lại trên default
  volume cũng PASS (`default-key:default-volume-message-20260726`). Kafka broker
  healthy sau recreate; crash-window/duplicate/poison behavior của từng consumer
  vẫn là gate riêng, không suy ra từ storage proof này.
- [x] Delivery PostgreSQL migration/race closeout: thêm isolated runtime harness
  `scripts/verify-delivery-postgres-migrations.sh`, chỉ sở hữu và cleanup ba
  schema `b8_delivery_*`. PostgreSQL 16 proof PASS cho clean V1→V10; V5 từ chối
  schema có hai active delivery cùng shipper, sau recovery partial unique index
  cho đúng một trong hai transaction accept đồng thời commit; V9 từ chối historic
  row thiếu `create_event_id`, documented recovery backfill marker rồi V9/V10
  hoàn tất, và unique constraint cho đúng một trong hai concurrent insert cùng
  event UUID commit. `pgcrypto` được cài/giữ tại canonical `public`, không bị
  cleanup schema test sở hữu. Delivery accept-vs-timeout/service-state race và
  Kafka consumer crash-window vẫn là proof riêng.
- [x] Core consumer replay closeout: poison/restart đã có runtime proof cho Order,
  Delivery, Saga, Match, Notification và Settlement; Settlement duplicate/
  post-commit replay, Restaurant producer decision/outbox và Order consumer
  restaurant-decision crash-window đều đã đóng. Saga transition và Delivery
  status có multi-instance row-lock, exact replay, relay restart và contradictory
  terminal-event DLT proof. Notification order-created inbox duplicate/provider
  recovery đã đóng;
  multi-token partial provider success vẫn là at-least-once limitation. Analytics
  vẫn disabled cho tới khi PostgreSQL
  concurrent insert/migration và retry-DLT proof xanh.
- [x] Core Kafka fresh-group/poison restart closeout: Delivery, Saga và Match đổi
  `auto.offset.reset` từ `latest` sang `earliest`, vì group metadata mất không
  được bỏ qua command đã durable. Match thêm wall-clock freshness fence 300 giây:
  expired online replay được ACK nhưng không ghi GEO/online (runtime shipper
  999999 không bị resurrect). Audit runtime phát hiện `asyncAcks=true` trên shared
  Match factory làm projection poison đứng lag vô hạn; đã tách sync projection
  factory khỏi reactive `Mono` factory. Audit tiếp phát hiện năm
  manual-immediate error handler publish DLT nhưng không commit source offset;
  thêm `setCommitRecovered(true)` cho Order, Delivery, Saga, Match, Notification
  và build guard chống regression. Runtime record-before-start proof đưa poison
  vào đúng DLT; source offsets lần lượt hội tụ lag 0. Sau restart, DLT end offsets
  giữ nguyên (`create-delivery=2`, `order.created=3`, `location=2`,
  `restaurant-rejected=1`, `shipper-offered=1`), chứng minh không redelivery/DLT
  loop. Focused config/listener suites xanh; full reactor checkpoint còn chờ cuối
  wave.
- [x] Settlement PostgreSQL/Kafka replay closeout: listener không còn hard-code
  group/topic; factory property là group authority và topic có production default
  `delivery.completed` với recovery override. Clone `settlement_db` sang database
  B8 cô lập, dựng hai Settlement peer cùng group trên topic hai partition. Cùng
  canonical COD event được route đồng thời vào hai peer: PostgreSQL chỉ giữ 1
  receipt, đúng 4 ledger entry; restaurant `+80000`, shipper earnings `+17000`,
  deposit `-120000`, system `+23000`, cả hai source lag 0. Stop peers, reset cả
  hai offsets về 0 rồi restart mô phỏng redelivery sau DB commit/trước offset
  commit: receipt/ledger/balances không đổi. Poison đi đúng same-partition DLT,
  source lag 0; restart giữ DLT end offset 1 và ledger `1/4`, không lặp vô hạn.
  Settlement clean 30/30. Hai peer, clone DB, test topics và group đã cleanup;
  production database/topic không nhận synthetic money event.
- [x] Delivery PostgreSQL multi-instance accept/reject/cancel rehearsal: custom
  consumer factory nay tôn trọng `spring.kafka.listener.auto-startup`, cho phép
  recovery peer tắt listener thật thay vì vô tình join group production. Clone
  `delivery_db`, dựng hai HTTP peer với Kafka listener/outbox relay/WebSocket off;
  hai offer khác nhau cùng shipper được accept đồng thời qua hai instance: đúng
  một request 200, request kia 400, DB chỉ có một `ASSIGNED`, outbox đúng một
  `SHIPPER_ACCEPTED` và một `BUSY`. Exact accept retry 200 không tăng outbox.
  Reject offer còn lại và cancel assignment đã accept đều đưa delivery về
  `FINDING_SHIPPER`; final outbox cardinality đúng 1 accepted, 2 rejected và 3
  status-change (BUSY + hai AVAILABLE). Peer/database clone đã cleanup;
  production không nhận event test. Offer-expiry command vs accept boundary
  được đóng bằng runtime topic-isolated rehearsal ở checkpoint kế tiếp.
- [x] Delivery PostgreSQL/Kafka accept-vs-timeout rehearsal: clone `delivery_db`
  sang database cô lập, dựng hai Delivery peer cùng consumer group trên năm
  command topic B8. Với offer đã hết hạn, timeout và HTTP accept chạy đồng thời:
  timeout thắng row lock, accept trả 400 và delivery hội tụ `FINDING_SHIPPER`
  với `shipper_id`, `offered_shipper_id`, `offer_expires_at` đều null. Timeout
  đến sau một accept thành công là stale no-op, không xóa assignment. Exact
  timeout replay trên state đã clear tiếp tục no-op; source offset hội tụ 4/4,
  lag 0 và DLT end offset giữ nguyên 1. Một intentionally early timeout đi DLT
  vì deadline chưa tới, xác nhận invalid command fail-closed. Trong lần đọc
  offset cuối, broker bị host OOM kill (`exit 137`); restart cùng named volume
  giữ nguyên source/DLT/group offsets và consumer tiếp tục lag 0, nên persistence
  recovery PASS nhưng memory sizing/CLI concurrency vẫn là operational risk cần
  ghi nhận trước production. Hai peer, clone DB và sáu topic B8 đã cleanup;
  consumer group không còn tồn tại sau topic/peer removal. Production Delivery,
  PostgreSQL và Kafka vẫn running/healthy; cross-service Saga timeout/rematch
  vẫn OPEN.
- [x] Cross-service Saga timeout/rematch rehearsal: Saga command destinations
  nay có env/property override cho recovery harness, trong khi cả tám production
  defaults giữ nguyên. Clean Saga đạt 37/37. Clone `saga_db` và `delivery_db`,
  chạy một Saga peer (input listener off) và một Delivery peer cùng heap cap
  192 MiB trên bảy topic B8. Scheduler chọn đúng expired `SHIPPER_FOUND`, commit
  atomic state `FINDING_SHIPPER` + một timeout step + đúng ba outbox command:
  expire exact offer, rematch và Order `FINDING_SHIPPER`. Relay gửi cả ba; payload
  rematch giữ canonical COD/location và `excludedShipperIds=[9300]`. Delivery
  consume timeout, clear offer/deadline và hội tụ `FINDING_SHIPPER`; source lag 0,
  không có DLT. Restart cả hai peer giữ đúng 1 timeout step, 3 outbox row, mỗi
  output topic end offset 1 và Delivery state bất biến, không duplicate command.
  Một bootstrap thử nghiệm dùng nhầm host listener `delivery-kafka:29092` đã
  fail publish và tăng attempts của command đầu; restart với canonical internal
  listener `kafka:9092` relay lại thành công, đồng thời chứng minh pending outbox
  recovery. Hai peer, hai clone DB và namespace topic `b8.rematch.*` đã cleanup;
  group không còn source topic để giữ metadata. Production topic/data không nhận
  fixture, production PostgreSQL/Kafka/Delivery/Saga tiếp tục running.
- [x] Restaurant PostgreSQL/Kafka decision-outbox closeout: hai destination
  confirm/reject có env override cho recovery harness, production default không
  đổi. Order custom Kafka factory nay tôn trọng configured group và
  `spring.kafka.listener.auto-startup`, nên HTTP eligibility peer thực sự không
  consume topic. Trên clone `order_db`/`restaurant_db`, hai Restaurant instance
  cùng confirm trả 200 nhưng chỉ giữ một decision/một outbox; confirm-vs-reject
  cho đúng một bên 200, bên kia 409 và vẫn chỉ một row/event. Wrong-restaurant và
  non-PENDING không ghi gì; rehearsal phát hiện catch-all trả 500 nên đã bổ sung
  typed `IllegalArgumentException` handler, runtime nay trả 400 và test khóa
  contract. Relay đưa đúng một message lên mỗi topic B8; restart không tăng
  offset. Khi Kafka bị host OOM-kill `137`, event mới giữ `PENDING`, attempts tăng
  `0→5` cùng backoff; restart broker bằng cùng named volume và relay peer mới gửi
  thành công, final cardinality 3 decision/3 outbox, topic offsets confirmed=2,
  rejected=1. Clean Order 68/68 và Restaurant 90/90. Toàn bộ peer, clone DB và
  topic `b8.restaurant.*` đã cleanup; production stack healthy. OOM khi chạy đủ
  stack + peer + Kafka CLI song song tiếp tục là memory-sizing risk, không phải
  bằng chứng Gate B8 đã đóng; downstream Order consumer business crash-window
  được đóng ở checkpoint Order kế tiếp. Build baseline JDK 17, HTTP inventory
  161/161, Compose contract và
  `git diff --check` đều PASS sau wave.
- [x] Notification PostgreSQL/Kafka inbox/provider recovery closeout: ba input
  topic có env override cho recovery harness, canonical defaults không đổi; class
  constants chỉ còn zero caller đã xóa. Hai peer cùng group nhận exact
  `order.created` duplicate trên hai partition. Runtime đầu tiên lộ race: peer thứ
  hai thấy cùng row `PENDING` và cũng gọi provider, có thể gửi push hai lần. Delivery
  bước external I/O nay nằm sau pessimistic row lock trong transaction riêng;
  durable PENDING insert vẫn commit trước I/O. True simultaneous proof sau fix:
  row mới cho đúng một insert/provider call, loser unique-conflict retry rồi skip;
  row PENDING có sẵn cho cả hai peer log retry nhưng chỉ một peer gọi provider,
  final stable row chuyển SENT. Configured Firebase peer dùng synthetic credential
  và Redis host lỗi giữ cùng row ID 9 PENDING; finite retry đưa đúng một DLT,
  source lag 0. Sau operator reset/replay với provider optional + Redis healthy,
  row 9 hội tụ SENT; restart giữ 1 row/1 DLT. Clean Notification 44/44. Peer,
  credential tạm, clone DB, group và namespace `b8.notification.*` đã cleanup.
  Trong wave broker tiếp tục OOM `137` vì Compose limit 768 MiB nhưng không cap
  JVM heap; thêm `KAFKA_HEAP_OPTS=-Xms256m -Xmx384m`, Compose regression check và
  recreate cùng named volume. Broker hiện healthy, heap env đúng và offsets/topic
  production còn nguyên. Multi-token/multi-channel partial success vẫn at-least-once
  và cần client dedup theo stable notification ID; Gate B8 tổng vẫn OPEN.
- [x] Delivery status -> Saga transition runtime closeout: ba Delivery status
  destination có env/property override cho recovery harness, canonical defaults
  giữ nguyên; cả 11 Saga listener input topic cũng có override và custom factory
  tôn trọng configured group/listener auto-startup. Hai Delivery HTTP peer dùng
  chung PostgreSQL clone chứng minh out-of-order `ASSIGNED -> DELIVERED` trả 400
  trong khi concurrent `PICKED_UP` thắng và chỉ tạo một status outbox. Hai peer
  cùng update `DELIVERING` đều 200 nhưng row-lock + same-state retry chỉ tạo một
  outbox; exact retry `DELIVERING`/`DELIVERED` không tăng cardinality. Final
  Delivery hội tụ `DELIVERED` với đúng 3 status, 1 completed và 1 shipper
  AVAILABLE outbox, toàn bộ trên topic override. Relay gửi offsets `3/1/1`; restart
  giữ nguyên. Saga clone từ `SHIPPER_ASSIGNED` consume ba status theo thứ tự, hội
  tụ `COMPLETED`, đúng ba step và ba update-order outbox. Reset source offset về
  0 rồi restart chỉ log exact replay, không đổi DB/outbox và lag về 0. Một event
  `DELIVERED` cùng semantic step nhưng eventId mới bị retry hai lần, vào đúng một
  same-partition DLT, source lag 0 và Saga state bất biến. Saga relay gửi đúng ba
  update-order command; restart không duplicate. Clean Delivery 68/68 và Saga
  42/42 trên JDK 17. Năm peer, hai clone DB, group và toàn bộ namespace
  `b8.status.*` đã cleanup; production PostgreSQL/Kafka/Delivery/Saga vẫn running,
  Kafka healthy. Build baseline JDK 17, HTTP inventory 161/161, Compose contract
  và `git diff --check` đều PASS sau wave. Gate B8 tổng vẫn OPEN vì
  access-revocation policy và clean Compose E2E/failure matrix.
- [x] Order Restaurant-decision consumer runtime closeout: hai Restaurant input,
  Saga status input và hai Order output topic có env/property override cho
  isolated recovery, canonical defaults giữ nguyên. Saga listener không còn
  hard-code `groupId=order-service`; custom factory là group authority. Hai Order
  peer dùng hai consumer group độc lập cùng nhận exact confirm/reject record trên
  PostgreSQL clone, mô phỏng concurrent duplicate delivery: Order 970001 chỉ có
  một receipt và hội tụ `CONFIRMED`; Order 970002 chỉ có một receipt, hội tụ
  `CANCELLED` và tạo đúng một cancellation outbox. Stop cả hai, reset source về
  offset 0 rồi restart giữ nguyên 2 receipt/1 outbox và chỉ log exact replay.
  Sau khi dừng shadow group, confirmation cùng Order nhưng eventId mới retry hai
  lần rồi vào đúng một same-partition DLT; main source lag 0, DB bất biến. Order
  relay phát đúng một `b8.order.output.cancelled`; relay restart giữ offset 1.
  Consumer restart sau poison giữ DLT offset 1 và source lag 0. Full clean Order
  70/70 trên JDK 17. Ba peer, clone DB, hai group và namespace `b8.order.*` đã
  cleanup; production PostgreSQL/Kafka/Order vẫn running và Kafka healthy. Core
  Restaurant producer -> Order consumer crash/replay boundary đã đóng; Gate B8
  tổng vẫn OPEN vì access-revocation policy và clean Compose E2E/failure matrix.
  Build baseline, HTTP inventory 161/161, Compose contract và `git diff --check`
  đều PASS sau wave.
- [x] Clean Compose E2E harness hardening: `verify-mvp-cod-flow.sh` không còn dùng
  customer polling để suy ra shipper offer; nó bắt buộc durable `MATCH_FOUND`
  notification có đúng order/recovery endpoint, sau đó fetch exact self-offer.
  Seed tạo thêm outsider actor; Java 17 raw-WebSocket probe bắt missing JWT=401,
  outsider không subscribe được active delivery, customer participant subscribe
  thành công và location broadcast dùng shipper ID từ JWT dù payload spoof ID.
  `verify-clean-compose-e2e.sh` dùng project + PostgreSQL/Kafka volume unique theo
  run, fail nếu volume/project đã tồn tại, chỉ dừng canonical containers khi có
  `ALLOW_CANONICAL_DOWNTIME=true`, không xóa canonical volume và có EXIT/INT/TERM
  recovery trap để cleanup run volume rồi restore canonical stack. Bash syntax,
  Java `-Xlint:all`, build baseline/HTTP inventory đều PASS. Harness mới chưa chạy;
  không được dùng static proof này để đóng clean E2E hay Gate B8. Legacy
  `test-order-flow.sh` còn doc caller được giữ làm compatibility wrapper sang
  harness canonical; implementation cũ polling/soft-settlement đã bị loại và ba
  tài liệu local/testing được cập nhật.
- [x] Auth/Gateway runtime invalid-token và IDOR matrix: hai account test có sẵn
  đăng nhập qua Gateway; access/refresh claims xác nhận chính xác 900/604800 giây.
  Missing, malformed và tampered JWT đều 401; client spoof `X-User-Id=24` và
  `X-Role=ADMIN` bị Gateway strip, response vẫn là self user 27; USER gọi admin
  route và cross-user address lần lượt 403, self address 200, internal by-auth
  route không public (404). Invalid refresh 401; rotation làm refresh cũ 401;
  logout 200 và refresh đã logout 401. Hai session fixture được logout sau test.
  Access JWT đã cấp vẫn hợp lệ 200 sau logout cho tới TTL 15 phút vì Gateway là
  stateless và token chưa có session/version claim. Đây là effective contract
  hiện tại, không phải immediate access revocation. Trước backend freeze phải
  chốt rõ admin block/logout có yêu cầu invalidate access ngay hay chấp nhận
  bounded 15-minute window; nếu yêu cầu immediate, cần authority cho introspection,
  Redis denylist hay token/session version thay vì tự chọn topology.
- [x] Static checkpoint sau Delivery timeout, Saga rematch và Auth runtime wave:
  full clean backend reactor đạt 17/17 service, 580 test trong 174 suite, không
  failure/error/skip; reactor package 17/17 PASS. Build baseline (JDK 17, Spring
  Boot 3.5.15, Spring Cloud 2025.0.3), HTTP inventory 161/161, Compose contract
  và `git diff --check` đều PASS ngày 2026-07-26. Cài local `ripgrep` 15.2.0 để
  chạy nguyên verifier canonical; không sửa/hạ chuẩn script. Checkpoint này không
  tự đóng Gate B8: còn policy immediate access revocation, runtime gates chưa
  rehearsal và clean Compose E2E/failure matrix tổng.
- [x] Clean Compose Gate B8 execution ngày 2026-07-26: runner introspect và bảo vệ
  canonical PostgreSQL volume `backend_delivery_b8_20260725_postgres_data`, Kafka
  volume `backend_delivery_kafka_data`, host port `15432`; dùng volume riêng theo
  run rồi cleanup và restore canonical stack. Clean startup đạt infrastructure
  healthy + 17 app + Gateway reads. COD flow đạt durable notification/current
  offer, raw WebSocket 401/participant authorization/JWT shipper identity,
  `DELIVERED`, bốn settlement rows và exact replay bất biến. Failure matrix đạt
  restaurant rejection (`CANCELLED`, zero settlement), no-online-shipper
  (`SHIPPER_NOT_FOUND`) và cancel-assignment rematch sang shipper thứ hai, gồm
  out-of-order status/role mutation fail-closed và final bốn settlement rows.
- [x] Clean gate phát hiện và đóng bốn integration gap thay vì hạ assertion:
  Saga phát Order `FINDING_SHIPPER` khi bắt đầu match; Order đọc lazy items trong
  read-only transaction; Order Saga listener nhận `JsonNode` qua Kafka converter
  thay vì `String`; Saga dựng find/rematch payload bằng canonical allowlist để
  epoch-millis metadata không làm hỏng DTO `LocalDateTime`. Cross-topic
  restaurant-confirm/order-command reorder hội tụ qua hai domain transition hợp
  lệ trong một transaction và late confirmation vẫn ghi durable receipt. Saga
  cancellation/create race ghi identity muộn rồi reissue compensation idempotent.
- [x] Final repository proof sau clean gate: full backend reactor đạt 595 test
  trong 179 suite, zero failure/error/skip; build baseline JDK 17, HTTP inventory
  161/161, Compose contract và `git diff --check` PASS. Canonical restore cuối giữ
  đúng PostgreSQL/Kafka volume trên, cổng `15432`, đủ 21 container running; clean
  run-scoped volumes đã xóa.
- [x] Product authority ngày 2026-07-26 chấp nhận access-token policy MVP:
  logout/admin block revoke refresh/session ngay; access JWT đã phát vẫn có thể
  hợp lệ tối đa 15 phút. Immediate revocation bằng introspection/Redis denylist/
  session-version không thuộc MVP. Gate B8 không còn policy blocker; chưa mở
  client phase cho tới khi API surface classification cuối hoàn tất và backend
  contract được freeze.
- [x] Promotion voucher-wallet role boundary: audit Gateway/controller/client
  call-site xác nhận collect/my-vouchers là capability của `USER`, nhưng edge và
  service trước đây chỉ cần một JWT bất kỳ. Hai route nay bắt exact role `USER`
  ở Gateway và controller; SHOP_OWNER/SHIPPER/ADMIN bị 403. Focused Gateway +
  Promotion đạt 44 test, zero failure/error; HTTP inventory 161/161, build
  baseline JDK 17 và diff hygiene PASS.
- [x] Public self-route actor closeout: audit route gộp phát hiện Restaurant
  `my-restaurants`/`my-menu-items`/customer ratings, Order `my-orders`/
  `my-restaurant-orders` và User address wallet chỉ kiểm JWT hoặc numeric self,
  rộng hơn actor trong inventory. Gateway nay tách exact route theo `USER`,
  `SHOP_OWNER` và `USER|ADMIN`; controller lặp role check trước query/mutation.
  Order detail, Delivery participant reads, notification/FCM, current profile,
  auth sessions và raw WebSocket tiếp tục any-authenticated vì ownership/
  participant check là contract đúng. Focused full suites của Gateway, User,
  Order, Restaurant và Promotion đạt 241 test, zero failure/error/skip.
- [x] Backend MVP contract freeze ngày 2026-07-26: full reactor đạt 602 test
  trong 179 suite, zero failure/error/skip; HTTP inventory 161/161, build baseline
  JDK 17/Spring Boot 3.5.15/Spring Cloud 2025.0.3, Compose contract và diff
  hygiene đều PASS. Clean Gate B8 runtime evidence giữ nguyên vì role closeout
  chỉ thu hẹp read/self edge và không đổi Kafka/DB/WebSocket payload hay state
  machine. Client migration authority chuyển sang
  `docs/plans/completed/mvp-client-alignment.md`.
- Client alignment đã hoàn thành theo plan đã freeze, theo thứ tự shared
  contract/config → shipper fulfilment → customer order/tracking → web
  restaurant/admin;
- [x] Client checkpoint `shipper_app2` foundation: Gateway/auth/offer/lifecycle
  contract đã align; STOMP + hidden settlement/payment graph đã loại. Typecheck,
  lint zero error và 15 Jest test PASS; native Android assemble còn thiếu vì
  sandbox không cho Gradle ghi cache ngoài workspace. Chi tiết completion ở
  `docs/plans/completed/mvp-client-alignment.md`;
  Search/Livestream/Analytics đã được phân loại public-vs-hidden cho MVP;
  voucher/flash-sale reservation vẫn là gate riêng trước khi bật hai feature và
  cả hai tiếp tục disabled trong MVP. Focused/static proof không được coi là
  clean integration proof.
- [x] Client checkpoint `delivery_app`: một Gateway origin bằng
  `API_BASE_URL`, search không còn `/api/api`/customer shipper route; status giao
  hàng bỏ STOMP và refresh REST, raw location WebSocket derive Gateway, gửi JWT
  handshake và `{deliveryId, shipperId}` participant subscription. Checkout chỉ
  COD, payment/VNPay, voucher calculate/reservation, flash checkout entry,
  shipper-rating write và livestream navigation đã loại khỏi runtime graph.
  Flutter full suite 72/72 PASS, analyzer 0 issue và diff hygiene PASS. Native
  mobile + Compose E2E note này đã được supersede bởi no-emulator acceptance
  policy; device run là final sanity only.
- [x] Client checkpoint `delivery_web`: default Gateway `:8079`, normalization
  ngăn `/api/api`; profile/notification/search và owner confirm/reject đúng
  contract. Đã loại runtime/navigation/dependency của STOMP/SockJS tracking,
  Agora/livestream, settlement self-service, analytics, merchant promotion/flash,
  admin withdrawal mutation, manual assign và fleet tracking tùy ý. Admin shipper
  dùng Page `.content`, bỏ block/unblock bằng `userId` chưa có Auth-ID authority.
  TypeScript, production build, ESLint, contract search và diff hygiene PASS;
  chỉ còn warning chunk khoảng 857 KB. Owner browser smoke qua Gateway PASS với
  fixture COD: confirm UI không còn giữ stale `PENDING`, hiển thị canonical
  matching state. Admin fixture/API runtime proof sau đó đã PASS bằng
  operator-only runner; backend vẫn từ chối self-register ADMIN đúng policy và
  không bypass bằng DB mutation. Admin browser attempt phát hiện CORS preview
  origin `4173` thiếu trong Gateway/Compose default; đã sửa và khóa bằng test/
  verifier. Full admin browser dashboard/surface smoke sau đó đã PASS và Gate
  C12 được đóng bằng no-emulator client/action contract acceptance.
- [x] Native Android build checkpoint: `delivery_app` ban đầu fail đúng tại
  `:app:checkDebugAarMetadata` vì `flutter_local_notifications` yêu cầu core
  library desugaring. Đã bật desugaring + `desugar_jdk_libs 2.1.4`; Flutter debug
  APK PASS. `shipper_app2` dùng workspace Gradle cache và `assembleDebug` PASS
  442 tasks. Pixel_9a AVD đã boot và customer APK launch/login UI smoke đã quan sát
  được; integration runner lần verify sau Hive fix bị treo ở ADB attach nên
  runtime mobile proof được hạ khỏi acceptance gate và giữ làm final sanity only.
- [x] Customer profile closeout: runtime main profile đã bỏ identity/ảnh/stats
  mock và payment action rỗng. Retrofit update đổi `/user/profile` sai sang
  canonical `PUT /users`, thêm focused contract test và live Gateway smoke PASS;
  `/user/avatar` không có backend/caller đã bị loại khỏi datasource→repository→
  usecase→provider graph. Flutter full suite nay 74/74, analyzer và APK PASS.
- [x] Auth/mobile client closeout static: login/register/social login đã bỏ
  device ID/IP hard-code, dùng persisted UUID + `MOBILE`, role social `USER`,
  register truyền fullName. Hive adapter registry idempotent sau khi emulator
  integration runner phát hiện double registration; analyzer và auth suite PASS.
  Pixel_9a APK launch/login UI smoke đã quan sát được, nhưng integration runner
  attach lần verify sau fix treo và không chạy assertion; device E2E vẫn mở.
- [x] Current-state COD rehearsal sau client alignment PASS: order/delivery `9`,
  durable notification/current-offer, accept, raw WebSocket auth/participant,
  `PICKED_UP→DELIVERING→DELIVERED`, bốn ledger rows và duplicate replay không đổi.
- [x] Backend closeout slice `order-service` (2026-07-26): checkout preview
  trước đây nhận `couponCode` nhưng chỉ echo lại với `discountAmount=0`, tạo
  contract giả cho voucher trong COD MVP. Boundary nay fail-closed bằng
  `ValidationException` trước mọi restaurant/fee lookup; focused policy test
  chứng minh downstream không bị gọi và cùng policy với create-order voucher/
  flash-sale rejection. Follow-up preview canonicalization đã chuyển preview
  sang cùng internal Restaurant validation endpoint + `Internal-Token` với
  create-order, chặn public-catalog approximation và shipping-fee `0` fallback
  khi thiếu pickup coordinate. Proof: `CheckoutPreviewMvpPolicyTest` +
  `OrderValidationMvpPolicyTest` 4/4 PASS trước đó; follow-up focused preview
  test và full `order-service` `85/85` PASS.
- [x] B3 dead-graph closeout `order-service` (2026-07-26): sau khi chứng minh
  voucher/flash-sale checkout bị reject ở validation boundary và hai internal
  reservation route đang disabled, đã bỏ `PromotionClient`, `FlashSaleClient`,
  các nhánh gọi reservation và hai URL môi trường không còn consumer khỏi
  order-service. Create-order COD không còn giữ side-effect tới capability đã
  tắt. Zero-call-site search sạch; full order-service 74/74 PASS trên JDK 17.
- [x] B4 Tracking WebSocket exposure closeout (2026-07-26): polyrepo search xác
  nhận không có consumer cho `subscribe_area`/`area_location_update`, trong khi
  handler vẫn cho ADMIN subscribe vị trí fleet tùy ý trái participant-only MVP.
  Action này nay luôn fail-closed; toàn area subscription/broadcast graph và
  service call-site đã bị loại. Raw `subscribe_shipper` tiếp tục bắt
  `{deliveryId, shipperId}` và Delivery internal authorization. Focused 12/12 và
  full tracking-service 26/26 PASS trên JDK 17; zero-call-site search sạch.
- [x] B4 Match canonical-coordinate boundary (2026-07-26): find-shipper từng
  fallback từ pickup thiếu sang delivery address hoặc tọa độ trung tâm TP.HCM,
  có thể offer shipper sai khu vực thay vì retry/DLT. Listener nay bắt pickup
  canonical hữu hạn trong biên Việt Nam trước mọi Redis/Settlement call và
  `createFindShippersRequest` chỉ dùng pickup; hai focused failure tests chứng
  minh không query/publish khi thiếu hoặc ngoài biên. Focused 13/13 và full
  match-service 37/37 PASS trên JDK 17.
- [x] B4 Saga offer-deadline convergence (2026-07-26): scheduler trước đây
  chỉ chọn `SHIPPER_FOUND` cũ hơn ba phút dù event contract chấp nhận
  `waitingTimeoutSeconds` ngắn hơn, nên offer có thể hết hạn nhưng Saga
  vẫn treo. Scheduler nay quét từ timeout tối thiểu một giây; `SagaManager`
  giữ pessimistic aggregate lock và chỉ mutate/rematch khi exact
  `foundAt + waitingTimeoutSeconds` đã tới. Poll sớm là no-op; offer payload
  không có duy nhất một shipper ID dương fail-closed sang terminal
  compensation, không queue expire/rematch malformed. Audit xác nhận hai
  scheduler node được serialize bởi `findByOrderIdForUpdate`, còn exact-expire
  → rematch → order-status được lưu cùng transaction và relay theo thứ tự
  aggregate. Focused 6/6, full Saga 49/49 và Delivery 68/68 PASS trên JDK 17;
  HTTP inventory 161/161, fixed-threshold search và diff hygiene PASS.
- [x] B4 Saga rejection/accept reorder closeout (2026-07-26): acceptance event
  đến trễ sau REJECT/cancel-assignment không còn resurrect shipper đã bị
  exclude khi Saga đang `FINDING_SHIPPER`; acceptance hợp lệ của Delivery
  commit sát timeout vẫn được chấp nhận (timeout history không bị coi là
  rejection). Hai convergence test mới, full Saga convergence 21/21 PASS.
- [x] B5 Settlement money-boundary invariant (2026-07-26): listener
  `delivery.completed` nay fail-closed trước receipt/ledger nếu split phí không
  khớp (`shipperEarnings + shippingCommission = shippingFee`), ngăn producer lỗi
  ghi earnings shipper sai dù tổng commission vẫn đúng. Focused listener 6/6 và
  full settlement-service 33/33 PASS trên JDK 17; migration/unique receipt,
  concurrent duplicate/reordered completion và DLT unit/integration proof hiện
  vẫn xanh.
  PostgreSQL crash-after-commit và reconciliation vận hành vẫn là proof mở ở Gate
  B8, chưa tuyên bố B5 runtime-complete.
- [x] B4/B5 canonical runtime failure rehearsal (2026-07-26): COD happy-path
  rehearsal qua Gateway/Kafka/PostgreSQL PASS với order `10`, delivery `10`, raw
  WebSocket participant 401/403 proof, lifecycle hoàn tất, đúng bốn ledger rows
  và replay `delivery.completed` không đổi cardinality. Failure matrix PASS với
  rejected order `13` (zero settlement), no-online-shipper order `14` hội tụ
  `SHIPPER_NOT_FOUND`, và cancel-assignment rematch order `15`/delivery `15`
  sang shipper thứ hai rồi ghi đủ ledger. Lần chạy đầu của failure matrix timeout
  ở 180 giây do không khớp Saga `FINDING_SHIPPER` compensation guard 5 phút;
  verifier đã đổi default thành 420 giây (5 phút + scheduler/Kafka margin),
  không thay đổi service policy. Concurrent two-instance completion,
  crash-after-commit và reconciliation vẫn là proof mở.
- [x] B5 Settlement projection reconciliation (2026-07-26): thêm
  `scripts/verify-settlement-reconciliation.sh` dạng read-only, đối chiếu từng
  balance với transaction ledger cho deposit/earnings/pending/holding,
  `total_deposited` và `total_cod_collected`, đồng thời bắt cả entity chỉ tồn tại
  một phía. Runtime canonical hiện PASS; script không tự sửa dữ liệu. Crash sau
  commit Kafka và PostgreSQL two-instance operational rehearsal vẫn mở.
- [x] B5 Settlement ACK ordering closeout (2026-07-26): Kafka
  `Acknowledgment` không còn được gọi trước DB commit; listener đăng ký ACK trong
  `afterCommit`, còn rollback/invalid/insufficient-deposit không ACK để record
  retry/DLT. Unit + integration focused và full settlement 33/33 PASS trên JDK
  17. Crash-after-commit runtime vẫn cần rehearsal Kafka thật, nhưng boundary
  code hiện đã giữ đúng thứ tự DB commit trước offset acknowledgement.
- [x] Notification STOMP dead-graph closeout (2026-07-26): full reactor checkpoint
  lộ `notification-service` vẫn khởi động simple broker dù `/ws-native` bị 404.
  Polyrepo zero-call-site xác nhận ba client đã bỏ STOMP; đã xóa endpoint/config,
  broker service/DTO, `sendWebSocket`, starter dependency, properties và Compose
  flag. Notification còn durable inbox + FCM optional; raw WebSocket duy nhất là
  Tracking location. Focused Notification 41/41 PASS; baseline/Compose verifier
  khóa graph không quay lại.
- [x] Delivery STOMP dead-graph closeout (2026-07-26): client migration đã đổi
  status sang REST refresh/durable notification và chỉ dùng raw Tracking socket
  cho location; polyrepo không còn subscriber `/ws/delivery-native`. Đã xóa
  conditional broker config, notifier cùng ba mutation call-site, WebSocket
  starter/properties/Compose flag và test compatibility. Delivery lifecycle vẫn
  publish Kafka/outbox như cũ; focused Delivery 67/67 PASS, baseline guard khóa
  graph không quay lại.
- [x] Post-STOMP backend checkpoint (2026-07-26): full reactor 17/17 module PASS,
  tổng Surefire 613 test, zero failure/error/skip. HTTP inventory 161/161,
  JDK 17/Spring build baseline và Compose contract PASS; polyrepo source search
  không còn `/ws-native`, `/ws/delivery-native`, STOMP/SockJS hay Delivery/
  Notification broker service. Tracking raw `/ws/shipper-locations` là WebSocket
  duy nhất được giữ cho MVP.
- [x] Settlement crash-window proof boundary (2026-07-26): thêm isolated
  database/topic một partition/group và JDI breakpoint tại
  `DeliveryCompletedEventListener$1.afterCommit`. Run `20260726-auto-1` xác nhận
  receipt `1`, ledger `4`, deposit `0`, COD collected `120000` đã commit trong
  khi consumer offset chưa tăng; `SIGKILL` process rồi restart cùng state nhận
  exact redelivery, idempotent-skip, cardinality/balance bất biến và lag về `0`.
  Đây là true process crash sau DB commit/trước ACK trên production listener,
  không phải offset-reset mô phỏng. Topic/database cô lập để bảo vệ canonical
  runtime và không được suy diễn thành canonical multi-partition proof. Harness
  `scripts/verify-settlement-crash-window.sh` tự cleanup database/topic/container.
- [x] Post-crash canonical runtime checkpoint (2026-07-26): canonical
  `settlement-service` từng chạy dangling image `0e523c...` cũ trong khi image
  mới là `397521...`; đã force-recreate riêng service, sau đó full Compose build
  đưa canonical image tới `d84661...`. Startup/Flyway v2, read-only settlement
  reconciliation, COD order/delivery `16`, raw WebSocket 401/participant proof,
  lifecycle, bốn ledger rows và exact `delivery.completed` replay đều PASS.
  Failure matrix PASS với rejected `17`, no-shipper `18` và rematch/completion
  `19`. Runtime startup verifier lộ bug có thể recreate PostgreSQL bằng default
  volume/cổng `5432`; đã sửa tự phát hiện và khóa exact mounted PostgreSQL/Kafka
  volumes + host port trước Compose reconcile. Canonical được khôi phục đúng
  `backend_delivery_b8_20260725_postgres_data`, Kafka
  `backend_delivery_kafka_data`, PostgreSQL host `15432`; đủ 17 app và Gateway
  catalog/search PASS. Full reactor hiện đạt 613 test/180 suite, zero
  failure/error/skip; build baseline, HTTP inventory 161/161, Compose contract
  và diff hygiene bốn repo PASS.
- [x] Android native launch checkpoint (2026-07-26): ADB ngoài sandbox boot được
  Pixel_9a. Flutter customer APK không treo splash; Android notification permission
  dialog từng che màn hình, sau khi xử lý quyền app render login sau Hive fix,
  không FATAL. Shipper debug APK cần Metro đúng cổng `8081`; sau bundle app render
  login không RedBox/FATAL. Device attempt lộ interceptor bắt cả login 401 rồi che
  lỗi gốc bằng `Missing refresh token`; đã loại login/register/social-login khỏi
  refresh policy và thêm regression test. Shipper TypeScript, ESLint quiet và full
  Jest 21/21 PASS. Authenticated mobile/cross-client journey và iOS vẫn OPEN.
- [x] Shipper client production-truth closeout (2026-07-26): audit sau native
  launch phát hiện drawer logout no-op, online/tier/count/identity/notification
  mock, profile earnings giả, Delivery UI tự fallback split tiền `85/15`, success
  screen dựng customer/tip/time/weekly goal và fake GPS route 500 dòng. Toàn graph
  đã đổi sang backend/Redux truth hoặc fail-closed, dead fake GPS/component types
  đã xóa. Startup không gọi protected Shipper API khi local auth vắng; auth 401
  bootstrap giữ lỗi gốc. Self-registration/forgot-password bị ẩn vì Auth register
  không tạo Shipper profile và chưa có onboarding/recovery authority. TypeScript,
  ESLint quiet, diff hygiene và full Jest 23/23 PASS.
- [x] Shipper-rating/Restaurant-coordinate canonical runtime proof (2026-07-26):
  lần Docker build đầu chỉ copy JAR cũ trong `target/`, nên startup log Shipper
  chỉ validate Flyway V1. Đã package lại đúng hai module bằng JDK 17, xác nhận
  JAR có Java migration V2/DTO mới, rồi rebuild và force-recreate riêng
  `shipper-service` + `restaurant-service`; PostgreSQL/Kafka và volume không bị
  restart. Shipper startup áp dụng V2 thành công, `flyway_schema_history` có V1+
  V2, và truy vấn read-only trên 21 shipper cho `0` rating khác null không có
  `shipper_ratings` sở hữu. Qua Gateway `:8079`, public restaurant catalog trả
  200; SHOP_OWNER create thiếu cả tọa độ trả 400 với hai lỗi `must not be null`;
  SHIPPER `GET /api/shippers/me/ratings` trả canonical
  `{status:1,message,data:[]}`/200. Compose contract PASS và 21/21 container
  (17 app + 4 infra) vẫn running; trong nhóm app chỉ Gateway publish port. Không
  chạy emulator. Full runtime-startup verifier không chạy lại vì verifier sẽ
  rebuild/reconcile cả 17 app; focused runtime proof trên hai boundary đã thay
  đổi được dùng để tránh tác động ngoài phạm vi.
- [x] Backend-wide Docker stale-artifact hardening (2026-07-27): runtime proof
  trên Shipper đã lộ `docker compose build` có thể lấy JAR cũ trong `target/` nếu
  operator quên package. Shared Dockerfile nay có stage fail-closed so sánh root/
  module Maven metadata và toàn bộ `src/` với đúng một packaged JAR; runtime image
  vẫn chỉ chứa JRE + JAR. Regression harness cô lập chứng minh fresh PASS và
  source mới hơn artifact FAIL với hướng dẫn `run Maven package first`; actual
  Shipper image build PASS. Baseline guard khóa Dockerfile/harness, JDK 17 build
  baseline + HTTP inventory 161/161 và diff check PASS. Canonical runbook được
  cập nhật; `DOCKER_GUIDE.md` không còn 14-service/password `123` hay cú pháp
  Compose v1 lỗi thời. Không restart container và không chạy emulator.
- [x] Cross-client hidden-route/dead native graph closeout (2026-07-27): manual
  scans trước đây chỉ bắt route/navigation nên bỏ sót Flutter Livestream/IAP vẫn
  compile Agora/In-App Purchase, customer tracking gọi ADMIN-only
  `/shippers/{id}`, và Restaurant giữ `nearby/categories` không có backend route.
  Zero-call-site audit đã xóa 180 file thuộc Livestream/IAP/Promotion/Flash Sale,
  customer Admin/duplicate Location, Agora config, customer Shipper profile/in-area,
  Restaurant nearby/categories và web REST Chat
  `/api/chat/**`; Firebase Chat đang có caller được giữ. Flutter bỏ ba direct
  native dependency Agora/IAP/Socket.IO và regenerate lock/plugin/build outputs.
  Root `scripts/verify-mvp-polyrepo-contract.sh` nay fail-closed nếu ba client
  quay lại direct backend port, `/api/api`, gRPC/STOMP/SockJS, hidden route/dead
  graph; đồng thời gọi HTTP inventory verifier. Backend public campaign/voucher
  reads vẫn được giữ; chỉ client graph không có UI caller bị loại. Current proof: backend 161/161,
  Flutter analyzer 0 + 111/111 + Android debug APK, Web ESLint + Vite production
  build PASS; không chạy app/emulator/browser.
- [x] Shipper deep reachability/runtime-state closeout (2026-07-27): static import
  graph từ entry không còn source mồ côi ngoài hai global declaration; xóa
  Settings/Permissions không reachable, 15 stale DTO/barrel/utility và bốn direct
  dependency template/DI/icon. Auth bootstrap không expose AppDrawer trước khi
  user+shipper ready; logout hoặc refresh expiry reset mọi account-scoped Redux.
  Cancel-assignment/active-null hội tụ state, offer không auto-hide sai 15 giây,
  offline không mở GPS/socket. Raw publisher heartbeat 30 giây giữ lease backend
  120 giây; close policy `1008` không reconnect tranh publisher mới. `npm run
  Delivery response parser khóa malformed identity/status/coordinate/timestamp/
  money trước Redux/UI và bỏ stale Order type graph. `npm run verify` PASS 16
  suite/55 test, TypeScript và ESLint zero warning/error; root
  inventory/transport/hidden-route gate 161/161 PASS. Android `assembleDebug`
  sau dependency cleanup PASS 411 task, APK khoảng 207 MB; không chạy emulator.
- [x] Shipper offer wake-up decision (2026-07-27): MVP đã chốt bounded
  current-offer polling fallback; FCM native credential/SDK/token registration
  là tối ưu dài hạn sau MVP, không chặn system E2E.
- [x] Operator ADMIN fixture authority/runtime proof (2026-07-29): `auth-service` có
  `OperatorAdminProvisioningRunner` tách riêng với explicit
  `APP_OPERATOR_ADMIN_PROVISIONING_*` env, gọi
  `AuthService.operatorProvisionAdminAccount`, dùng BCrypt/Auth repository +
  User internal provisioning, không endpoint, không SQL fixture trực tiếp,
  không credential log và không password rotation. Public self-register vẫn
  reject `ADMIN`; existing non-ADMIN/inactive/mismatched password hoặc User
  identity conflict fail-closed. Evidence: `mvn -q -pl auth-service test`,
  `backend_delivery/scripts/verify-build-baseline.sh`, và
  `scripts/verify-mvp-polyrepo-contract.sh` PASS. Runtime API smoke cũng PASS
  lại ngày 2026-07-29: operator runner tạo fixture tạm, Gateway login,
  `GET /api/users` role `ADMIN`, block cleanup; credential tạm không được ghi
  vào repo. Full admin browser dashboard/surface smoke vẫn là blocker riêng.
- [x] Gateway CORS preview-origin closeout (2026-07-29): `vite preview` chạy
  trên `4173`, trong khi Gateway/Compose default chỉ allow `5173/3000`, khiến
  admin browser login trên preview không redirect dù API smoke qua Gateway PASS.
  Đã thêm `http://localhost:4173` và `http://127.0.0.1:4173` vào default CORS
  của `api-gateway` và `docker-compose.yml`, thêm `CorsConfigTest`, cập nhật
  compose verifier để guard hai origin này. Evidence: Gateway test/package PASS,
  Gateway container rebuild/recreate, CORS preflight `Origin:
  http://127.0.0.1:4173` trả `200 OK`, compose verifier PASS, build baseline
  PASS và root polyrepo contract gate PASS. Sau các smoke attempt, cleanup qua
  Gateway admin block API đưa active `admin+*@test.dev` fixtures về `0` và xóa
  temp credential files khỏi `/private/tmp`.
- [x] Admin browser + Auth dead-list closeout (2026-07-29): authenticated ADMIN
  login redirect và Dashboard/Orders/Shippers/Ratings/Coupons/Flash Sales đều
  render qua Gateway, console không error/warning. Fixture tạm được block, login
  replay trả `401`, active test ADMIN trở về `0` và temp files được dọn. Cleanup
  lộ `GET /api/auth/admin/accounts` không có consumer và cap âm thầm 100 rows;
  controller/service/Gateway route đã gỡ, Auth missing route trả `404` thay vì
  generic `500`. Auth/Gateway full tests, inventory/build/polyrepo gates PASS với
  146 handlers; rebuilt Gateway runtime trả public read `200`, removed route
  `404`. Named PostgreSQL volume được giữ nguyên; local host mapping tạm dùng
  `55432:5432` do Docker Desktop giữ xung đột host port `5432`.
- [x] Public response/pagination convergence (2026-07-27): audit web fail-closed
  adapter lộ Flash Sale success từng dùng `status=200`, Promotion trả raw
  `Voucher/List/String/Void`, Search trả raw Spring `Page`/`ProblemDetail`, và
  Order/Shipper/Search pagination còn phụ thuộc `.content/.number/totalElements`
  trái decision 0001. Flash Sale nay dùng canonical `status=1|0`; Promotion và
  Search success/error dùng `BaseResponse`; 11 endpoint Page của Order, Shipper,
  Search trả stable `{items,page,size,totalItems,totalPages,hasNext}`. Web và
  Flutter consumer migrate đồng thời, reject legacy/raw response. Proof hiện
  tại: Flash Sale 21/21, Promotion 26/26, Order 75/75, Shipper 22/22, Search
  16/16 trên JDK 17; Flutter analyzer + full test PASS; Web lint/build PASS;
  inventory 161/161 và polyrepo contract gate PASS. Không chạy app/emulator.
- [x] Web API/reachability closeout follow-up (2026-07-27): Axios adapter chỉ
  nhận exact `BaseResponse(status=1|0,message,data)`, refresh chỉ chạy cho request
  có Bearer và kiểm tra token quay vòng trước lưu; login/profile khóa identity và
  role portal. Admin Order/Shipper/Rating/Promotion và Flash Sale có runtime DTO
  parser thay vì cast/fallback mảng rỗng. Import graph từ `main.tsx` có 48 source,
  47 reachable và chỉ `vite-env.d.ts` global ngoài graph; không còn source/service
  mồ côi. Chat Firebase đã phục hồi listener nhưng vẫn chưa production-safe vì
  chưa có Firebase Auth/custom-token + Firestore rules; chờ authority chọn cấu
  hình bảo mật hoặc ẩn Chat khỏi MVP.
- [x] Web Chat/Firebase MVP cleanup follow-up (2026-07-29): đã chọn hướng an
  toàn cho MVP là loại Chat khi chưa có Firestore identity/rules proof.
  `ChatProvider`, `ChatWidget`, `RestaurantChatPage`, `AdminChatPage`,
  `ROUTES.ADMIN_CHAT`, `ROUTES.RESTAURANT_CHAT`, `src/modules/chat`,
  `src/config/firebase.ts`, `src/types/chat.types.ts` và dependency `firebase`
  bị gỡ khỏi source/dependencies; guard root contract scan chặn tái expose hoặc
  khôi phục graph trong MVP. Web lint/build PASS; root polyrepo contract PASS.
- [x] Internal HTTP response convergence (2026-07-27): sáu boundary Boolean nội
  bộ của Order rating/restaurant-decision, Delivery tracking access, Restaurant
  ownership, Settlement COD eligibility và Shipper rating write không còn trả
  hoặc đọc raw Boolean. Producer dùng canonical `BaseResponse`; typed consumer
  chỉ nhận `status=1` cùng `data` hợp lệ và fail-closed với status lỗi, null hoặc
  body rỗng. Reactor Delivery/Order/Restaurant/Shipper/Tracking/Match/Settlement/
  Flash Sale xanh lần lượt 67/75/94/22/26/37/33/21 test; thêm 11 test trực tiếp
  cho năm HTTP client. Build baseline JDK 17, HTTP inventory 161/161, root
  polyrepo contract gate và diff hygiene đều PASS. Không chạy app/emulator.
- [x] Web owner runtime DTO convergence (2026-07-27): Restaurant, Menu, Order và
  public Rating service không còn trả Axios envelope đã cast thẳng vào React
  state. Parser mới kiểm tra identity, canonical enum/status, COD, page, money,
  timestamp và nullable text; riêng Restaurant ánh xạ đúng backend
  `latitude/longitude` sang form `addressLat/addressLng`, sửa lỗi tọa độ từng bị
  `undefined` khi edit. Hook chỉ nhận domain object đã parse và interceptor tiếp
  tục sở hữu success/error envelope. Web ESLint, TypeScript/Vite production
  build, diff hygiene và root inventory/transport gate 161/161 PASS; không chạy
  browser/emulator.
- [x] Order arbitrary-read quarantine (2026-07-27): zero-call-site scan xác nhận
  `GET /api/orders/user/{id}`, `/restaurant/{id}` và
  `/restaurant-owner/{id}` không có client/internal consumer; Gateway đã deny
  nhưng bean trước đây vẫn active trong private network. Tại checkpoint đó các
  handler sống trong `LegacyOrderReadController` và bị tắt mặc định bằng
  `ORDER_LEGACY_READ_API_ENABLED=false`; self endpoints `my-orders` và
  `my-restaurant-orders` giữ nguyên. Closeout 2026-07-29 sau đó đã xóa hẳn
  legacy controller và inventory hiện tại là 147. Context test chứng minh
  legacy bean vắng; full Order 75/75, Compose contract, inventory 161/161 và
  diff hygiene PASS.
- [x] Promotion HTTP DTO boundary (2026-07-27): controller không còn serialize
  trực tiếp JPA `Voucher`; `VoucherResponse` giữ stable wire fields và mapper test
  khóa contract, ngăn entity/persistence refactor tự làm đổi public JSON. Admin,
  wallet và compatibility merchant response đều dùng DTO; checkout/reserve vẫn
  tắt như cũ. Full Promotion 27/27 PASS và baseline guard chặn quay lại entity.
- [x] Search persistence-boundary/dead-graph closeout (2026-07-27): public
  Restaurant/Dish response không còn serialize Elasticsearch document; stable
  DTO mapper giữ wire fields. Dish VND price đổi `Double` sang `BigDecimal`.
  Zero-call-site xác nhận Shipper search không có product consumer, trong khi
  publisher thiếu canonical event metadata nếu bật; đã xóa trọn HTTP/document/
  repository/consumer/publisher/config graph. Inventory giảm 161→160; Search
  17/17, Flutter Search 5/5 + focused analyzer PASS, không chạy emulator.
- [x] Canonical response-constructor/error checkpoint (2026-07-27): source scan
  phát hiện Auth và Settlement là hai module còn đảo thứ tự Java constructor
  `BaseResponse(status,message,data)`, trong khi contract chung và các module còn
  lại dùng `status,data,message`; Flash Sale exception handler còn đặt HTTP
  `400/404` vào field nghiệp vụ `status`. Auth/Settlement nay dùng named factory
  `success/failure`, constructor ba tham số cùng một thứ tự canonical; business
  boolean `eligible=false` không còn bị biểu diễn như transport failure. Flash
  Sale 4xx/5xx trả `status=0`, `data=null`, giữ HTTP code thật và sanitize lỗi
  bất ngờ. Guard baseline chặn cả legacy constructor order lẫn HTTP code trong
  `BaseResponse.status`. Full Auth 38/38, Settlement 34/34 và Flash Sale 24/24
  PASS; HTTP inventory 160/160, JDK 17 baseline, polyrepo contract gate và diff
  hygiene PASS. Ba module thay đổi có 96 test/32 suite, 0 failure/error; không
  dùng aggregate report cũ để tuyên bố full reactor vì đã phát hiện stale
  Surefire report từ test bị xóa. Baseline nay bắt report không còn tương ứng
  test source hoặc cũ hơn source; full reactor sạch sẽ được chạy lại ở closeout.
  Không chạy app hoặc emulator.
- [x] Reactor-duration anomaly triage (2026-07-27): lần full reactor trước ghi
  Analytics khoảng 48 phút và Settlement lock test khoảng 303 giây. Rerun độc
  lập không tái hiện deadlock/resource leak: full Analytics khoảng 19 giây và
  full Settlement trước response change khoảng 20 giây; sau change Settlement
  vẫn PASS khoảng 30 giây trên host đang tải. Không thay production lock/query
  từ tín hiệu nhiễu không tái hiện được; tiếp tục theo dõi ở lần full-reactor
  closeout kế tiếp.
- [x] Notification durable-boundary follow-up (2026-07-27): Kafka listener không
  còn ghi raw event payload vào log; baseline guard chặn raw payload log quay lại.
  Replay cùng `deduplicationKey` nay phải khớp user và immutable notification
  fields; key bị tái sử dụng cho payload khác trả HTTP `409/status=0` và không
  chạy FCM/delivery lần hai. Clean Notification test chạy 43/43, 0 failure/error;
  inventory 160/160, build baseline, Compose và polyrepo contract gate PASS.
  Không chạy app/emulator.
- [x] Restaurant→Order decision identity/fingerprint follow-up (2026-07-27):
  Restaurant decision event mang authenticated `actorUserId`; deterministic
  producer fingerprint khóa actor, prep-time, notes/reason; fingerprint nay được
  lưu trên decision row nên replay cùng decision nhưng đổi payload fail-closed
  ngay cả khi outbox đã prune. Legacy decision row null fingerprint fallback sang
  retained outbox payload khi còn tồn tại để giữ compatibility. Order bắt positive actor và restaurant rejection phát
  `OrderCancelledEvent.cancelledBy=actorUserId`, không còn nhầm `restaurantId`.
  Regression kiểm tra cancellation outbox identity và decision-row fingerprint;
  clean Restaurant 109/109 và Order 75/75 trên JDK 17. Build baseline, HTTP
  inventory 160/160, Compose, polyrepo contract gate và diff hygiene PASS ở
  checkpoint trước; gate hiện hành sẽ được rerun sau docs closeout. Runtime
  Kafka/PostgreSQL replay cho payload mới sẽ được lặp lại ở final Gate B8
  checkpoint. Không chạy app/emulator.
- [x] Match output identity/truth + stale MVP backlog closeout (2026-07-27):
  `shipper.found`/`shipper.not-found` ID được dẫn xuất deterministic từ Saga find
  command UUID và outcome; publisher bắt stable UUID + positive aggregate IDs,
  không tự sinh random ID khi replay. GEO projection không còn bịa shipper name,
  phone hoặc rating `5.0`; matching session khóa theo command generation. Clean
  Match đạt 48 executed test, 2 opt-in Redis test skipped; replay Kafka thật còn
  OPEN theo inventory. Đối chiếu code xác nhận Shipper pagination, non-blocking
  reactive retry, Redis-only location hot path, explicit secret, Order enum,
  Delivery `restaurant_id` migration và bulk session deactivation đã hoàn tất;
  roadmap cũ được cập nhật. Hai Restaurant/Menu controller cuối cùng chuyển sang
  constructor injection. Restaurant clean 99/99; JDK 17 baseline, HTTP inventory
  160/160, Compose, polyrepo contract gate và diff hygiene PASS. Không chạy
  app/emulator.
- [x] Production-truth placeholder closeout (2026-07-28): `delivery.completed`
  không còn phát restaurant/customer name giả; Match từ chối find command thiếu
  canonical restaurant/address text trước khi reserve; Notification cũng
  fail-closed thay vì dùng `Restaurant`/`Pickup location`/`Delivery location`.
  Restaurant order-validation trả `null` cho dữ liệu thiếu và thêm
  `RESTAURANT_NAME_MISSING`, thay vì `Unknown Restaurant`/`N/A`; catalog delete
  log chỉ dùng restaurant ID. Clean Match 51 (49 executed + 2 opt-in skipped),
  Delivery 69/69, Notification 44/44, Restaurant 101/101 trên JDK 17. Baseline,
  HTTP inventory 160/160, Compose, polyrepo contract gate và diff hygiene PASS.
  Không chạy app/emulator.
- [x] Restaurant random catalog dead-graph removal (2026-07-28): polyrepo scan
  xác nhận `RestaurantCatalogService`/impl/DTO không có controller, client,
  internal hay event consumer; chỉ là side effect create/update/delete. Graph
  từng sinh `Math.random()` cho coordinates/rating/review/avg price/delivery fee/
  popularity và fake featured item URL. Đã xóa trọn graph cùng injection/calls;
  public restaurant read vẫn dùng canonical repository/cache, search thuộc
  search-service. Baseline guard chặn graph/synthetic marker quay lại. Clean
  Restaurant 101/101 trên JDK 17; baseline, HTTP inventory 160/160, Compose,
  polyrepo contract gate và diff hygiene PASS. Không chạy app/emulator.
- [x] Fake-payment profile isolation (2026-07-28): fake confirm controller/provider
  vốn hidden khỏi Gateway và cần hai explicit flags, nhưng trước đây vẫn có thể
  được bật dưới profile production. Cả hai bean nay bắt thêm `dev|test`; context
  regression chứng minh `prod` không tạo bean dù flags true và `test` chỉ tạo khi
  flags explicit. Baseline guard khóa profile isolation. Clean Settlement 36/36
  trên JDK 17; baseline, HTTP inventory 160/160, Compose, polyrepo contract gate
  và diff hygiene PASS. COD MVP không đổi và không chạy app/emulator.
- [x] Constructor-injection baseline closeout (2026-07-28): baseline nay cấm
  đúng `@Autowired` field injection thay vì cấm constructor annotation. Khi thử
  bỏ annotation khỏi `TokenService`, Saga/Order `KafkaConfig` và
  `NotificationServiceImpl`, clean context test chứng minh Spring không thể chọn
  giữa hai constructor (`No default constructor found`); đã khôi phục annotation
  chỉ trên constructor production. Clean Notification 44/44, Auth 44/44, Saga
  50/50 và Order 75/75, tổng 213 test không failure/error. Build baseline JDK 17,
  HTTP inventory 160/160, Compose, root polyrepo contract gate và diff hygiene
  đều PASS. Không chạy app/emulator.
- [x] Shipper-not-found customer notification + dead checkout placeholder
  closeout (2026-07-28): Notification đã sở hữu message/type canonical cho
  `SHIPPER_NOT_FOUND` và consume duy nhất `delivery.status-updated`, nhưng
  Delivery trước đây chỉ đổi row rồi bỏ TODO nên khách không nhận được event.
  Transition nay lưu status event transactional với canonical customer ID;
  exact command replay skip trước mutation/publish. Contract test chứng minh
  Notification chấp nhận terminal payload không có assigned shipper và ACK sau
  dispatch. Order không phát notification thứ hai. Nhánh coupon placeholder
  không thể chạy vì checkout preview đã reject coupon trước mọi I/O; đã xóa dead
  branch/TODO nhưng giữ response contract với discount `0`. Clean Delivery
  70/70, Notification 45/45 và Order 75/75, không failure/error. Kafka runtime
  replay cho terminal branch còn OPEN. Không chạy app/emulator.
- [x] Saga no-shipper terminal echo closeout (2026-07-29): audit Wave D phát hiện
  Delivery đã publish `delivery.status-updated(SHIPPER_NOT_FOUND)` cho
  Notification nhưng Saga cũng subscribe topic này và trước đó coi status này là
  unsupported, có thể đẩy event hợp lệ vào retry/DLT sau khi no-shipper đã hội tụ.
  Saga nay chỉ ACK/record event này như terminal echo sau step `shipper.not-found`,
  không phát duplicate `saga.command.update-order-status`; nếu event đến trước
  matching terminal thì fail-closed để retry. Focused Saga manager/listener
  regression `31/31`, full Saga `54/54`, Delivery `75/75`, Notification `46/46`
  đều PASS; Kafka runtime replay thật cho nhánh này vẫn OPEN.
- [x] Order→Delivery canonical pricing/location fail-closed (2026-07-28): Order
  từng trả minimum shipping fee khi tọa độ null hoặc khi calculation ném lỗi;
  Delivery còn điền phí `15.000` cho create/replay command thiếu dữ liệu. Điều
  này che lỗi canonical restaurant/customer location và có thể offer/settle sai.
  Order validation nay bắt name/address/owner/pickup coordinate canonical từ
  Restaurant; calculator chỉ nhận coordinate hữu hạn trong Việt Nam và giữ
  nguyên công thức hợp lệ. Delivery event boundary bắt positive shipping fee,
  zero discount, exact arithmetic, COD và đủ pickup/delivery coordinate, sau đó
  persist đúng phí từ Order. Polyrepo zero-call-site chứng minh
  `estimateShipperEarnings` chỉ tồn tại trong Order interface/impl và dùng split
  `80/20` mâu thuẫn settlement `85/15`; đã xóa graph này mà không đổi API. Full
  Order 80/80 và Delivery 72/72, Notification vẫn 45/45; baseline guard chặn
  fallback/dead policy quay lại. Runtime Kafka malformed/replay proof còn OPEN.
  Không chạy app/emulator.
- [x] Runtime internal-URL fail-fast slice (2026-07-28): removed container-time
  `localhost` fallbacks from order/flashsale/restaurant/tracking service client
  URLs and moved them to required env-backed placeholders. Updated constructor
  injection and test profiles so direct client tests and the Spring migration/
  outbox contexts still load explicitly. Proof: `OrderValidationCanonicalRestaurantTest`,
  `OrderValidationMvpPolicyTest`, `CheckoutPreviewMvpPolicyTest`,
  `RestaurantOwnershipClientTest`, `OrderInternalClientsTest`,
  `DeliveryTrackingAccessClientTest`, `OrderOutboxMigrationTest`,
  `OrderMigrationSchemaValidationTest`, `RestaurantDecisionOutboxIntegrationTest`,
  `RestaurantMigrationSchemaValidationTest`, `FlashSaleMigrationSchemaValidationTest`
  all PASS; `scripts/verify-compose-config.sh`, `scripts/verify-build-baseline.sh`,
  `scripts/verify-http-api-inventory.sh`, `git diff --check`, and
  `scripts/verify-mvp-polyrepo-contract.sh` PASS. Docker runtime startup proof
  is still blocked by an unavailable local daemon, so that final readiness gate
  remains OPEN.
- [x] Gateway route URI fail-fast slice (2026-07-28): `GatewayRouteConfig` now
  requires explicit env-backed URIs for all backend routes instead of defaulting
  to `localhost`, including the tracking WebSocket URI. Added explicit
  `api-gateway/src/test/resources/application.properties` overrides so route
  tests keep loading deterministically. Proof: `api-gateway` `mvn test` PASS;
  `scripts/verify-compose-config.sh`, `scripts/verify-build-baseline.sh`,
  `scripts/verify-http-api-inventory.sh`, `git diff --check`, and
  `scripts/verify-mvp-polyrepo-contract.sh` PASS. Compose already supplies the
  required `APP_*_SERVICE_URI` values, so no runtime env gap was introduced.
- [x] Promotion/settlement dead-surface cleanup slice (2026-07-28): removed the
  zero-call-site legacy merchant-create and settlement admin mutation
  controllers, kept the remaining promotion/admin and settlement read surfaces
  as documented, and updated HTTP inventory/classification to 154 mapped
  controller methods. Proof: `PromotionServiceApplicationTests` and
  `SettlementServiceApplicationTests` PASS; `scripts/verify-http-api-inventory.sh`
  PASS; `../scripts/verify-mvp-polyrepo-contract.sh` PASS; `git diff --check`
  PASS.
- [x] Backend runtime startup proof (2026-07-28): `scripts/verify-runtime-startup.sh`
  passed after a local `5432` host-port collision was avoided with
  `POSTGRES_HOST_PORT=15432`; canonical volumes were preserved, infrastructure
  became healthy, all 17 applications started, and Gateway public reads
  responded. The proof also exposed a preflight bug when the postgres container
  had no published port metadata while exited; the verifier now handles that
  state safely.
- [x] Auth/User provisioning identity hardening (2026-07-28): `user-service`
  internal create now rejects an email already linked to a different `authId`
  before insert and after a possible write race, and Flyway adds a case-insensitive
  unique email guard so concurrent inserts cannot bypass the service precheck.
  This keeps the User projection aligned with Auth's unique email authority.
  Proof:
  `mvn -q -pl user-service test`,
  `mvn -q -pl user-service -Dtest=UserFlywayMigrationTest test`,
  `mvn -q -pl user-service -Dtest=UserServiceProvisioningTest test`,
  `mvn -q -pl user-service -Dtest=UserServiceApplicationTests test`,
  `mvn -q -pl auth-service -Dtest=AuthServiceApplicationTests test`,
  `mvn -q -pl api-gateway -Dtest=GatewayRouteSecurityTest test`, and root
  `scripts/verify-mvp-polyrepo-contract.sh` PASS.
- [x] User block/unblock projection row-lock hardening (2026-07-28): internal
  block/unblock now reads the target user with `findByIdForUpdate` inside a
  transaction, preserving idempotency while serializing concurrent Auth
  projection commands. Proof: `mvn -q -pl user-service -Dtest=UserServiceProvisioningTest test`,
  full `mvn -q -pl user-service test`, and root
  `scripts/verify-mvp-polyrepo-contract.sh` PASS.
- [x] Auth admin block/unblock row-lock hardening (2026-07-28): Auth account
  status mutations now use `findByIdForUpdate` in the existing transaction before
  toggling `isActive`, revoking sessions and calling the internal User projection
  endpoint. Proof: `mvn -q -pl auth-service -Dtest=AuthServiceSecurityTest test`,
  `mvn -q -pl auth-service -Dtest=AuthServiceApplicationTests test`, and full
  `mvn -q -pl auth-service test` PASS.

## Validation

- Focused proof: unit/controller/repository tests theo rule của từng service.
- Contract proof: OpenAPI examples, Kafka JSON schema/compatibility tests và socket
  message tests cho mỗi public/internal boundary.
- Integration proof: Testcontainers cho DB/Kafka/Redis, restart/retry/replay và
  migration rehearsal.
- End-to-end proof: Docker Compose sạch, scripts failure matrix, sau đó UI-driven
  journeys qua Flutter, React web và React Native.
- Repository checks: Maven package/test từng module và toàn parent; Flutter
  analyze/test; web build/lint/test; React Native lint/test/build phù hợp.

## Result

Điền sau khi Gate C12 đạt. Phải ghi rõ endpoint/feature đã giữ, ẩn, deprecated hoặc
loại bỏ; migrations; test evidence; limitation và follow-up production trước khi
chuyển plan sang `docs/plans/completed/`.
