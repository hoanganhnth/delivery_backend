# Completed Plan: Refactor ba client để kiểm thử gần toàn bộ use case

Date: 2026-07-29 — completed 2026-07-30

## Status

Completed — mọi capability MVP reachable đã có proof tự động ở tầng public gần
người dùng nhất phù hợp; native SDK/OS giữ adapter-contract proof và device
sanity là regression tùy chọn, không phải acceptance gate.

## Outcome

Ba client `delivery_app`, `delivery_web` và `shipper_app2` giữ nguyên contract
MVP đã freeze nhưng có cấu trúc cho phép thay thế mọi I/O boundary bằng fake,
chạy automated test cho gần như toàn bộ hành động và nhánh quan trọng của từng
use case mà không cần emulator/device hay backend thật. Mỗi repo có một lệnh
verify ổn định gồm static checks, unit/controller/component tests, journey tests
và coverage policy tập trung vào code nghiệp vụ/điều phối.

"Gần toàn bộ use case" được hiểu là mọi capability đang reachable trong UI có
ít nhất happy path, lỗi backend/adapter và hành động chính được chứng minh ở
tầng gần người dùng nhất có thể; native rendering, Mapbox SDK, hệ điều hành,
push background và device permission chỉ cần adapter contract + manual sanity,
không dùng line coverage tổng làm đại diện cho use-case coverage.

## Context

- Product/system authority: `docs/product/overview.md`, `docs/ARCHITECTURE.md`,
  `docs/product/features/order-lifecycle.md`.
- Backend contract authority: `backend_delivery/docs/http-api-inventory.md`,
  `backend_delivery/docs/system-contract-inventory.md` và freeze được ghi trong
  `docs/plans/completed/mvp-client-alignment.md`.
- Workflow authority: `docs/WORKFLOW.md`.
- Baseline ngày 2026-07-29, worktree cả ba repo sạch:
  - `delivery_app`: `fvm flutter test` PASS `135/135`; coverage code Dart viết
    tay trong graph được instrument chỉ `1,144/11,259` line (`10.16%`). Test
    hiện tập trung auth/contract và một ít widget action; nhiều notifier,
    repository, routing, catalog/cart/order/address/profile/notification flow
    chưa có proof.
  - `delivery_web`: `npm run verify` PASS nhưng `test:actions` chỉ là static
    source-contract script; chưa có DOM/component/user-event runner. Provider
    import singleton service/storage trực tiếp nên khó fake theo journey.
  - `shipper_app2`: `npm run verify` PASS `16` suite/`61` test. Store đã dùng
    thunk `extraArgument`, nhưng singleton store/AppInitializer/native service
    vẫn là composition root cứng. Jest coverage hiện làm 4 suite fail vì
    `react-native-dotenv` transform `runtime.ts`; phần suite chạy được báo
    `53.42%` line nên chưa thể dùng làm gate tin cậy.

## Scope

In scope:

- Refactor dependency boundaries/composition root, không đổi UI policy hay API
  contract đã freeze.
- Fake/in-memory adapters cho HTTP, storage, clock/timer, WebSocket, GPS/native
  permission và navigation khi cần.
- Test helpers/builders/fixtures dùng canonical response/status.
- Unit test cho parser/entity/rule; controller/provider/reducer test cho state
  transition; component/widget test cho mọi action reachable; journey test cho
  chuỗi hành động chính của từng portal/app.
- Coverage collection chạy được ổn định và threshold theo vùng nghiệp vụ mới
  hoặc vùng đã refactor; use-case ledger là acceptance source chính.
- Giữ root polyrepo contract gate và các build/analyze/typecheck/lint gate hiện
  có.

Out of scope:

- Thay đổi backend/Kafka/API/state machine hoặc mở lại capability đang hidden.
- Pixel-perfect snapshot của toàn bộ màn hình, test implementation detail, hoặc
  đòi emulator/device cho mỗi lần CI.
- Tuyên bố SDK native thật (Mapbox, GPS, biometric, push background, social
  provider) hoạt động chỉ từ fake test; các phần này cần adapter contract và một
  checklist sanity thiết bị riêng.
- Theo đuổi 100% line coverage cho generated code, style-only widget, platform
  glue hoặc dead/hidden graph.

## Architecture And Test Policy

Áp dụng cùng nguyên tắc, không ép ba framework dùng cùng thư viện:

1. **Functional core**: validation, mapping, state transition và use-case
   orchestration không gọi global/native/network trực tiếp.
2. **Injected ports**: HTTP client, storage, clock/timer, socket, GPS/native,
   upload và navigation là interface/typed dependency có production adapter và
   fake adapter.
3. **One composition root**: production singleton chỉ được tạo ở entry point;
   provider/store/widget test có factory nhận overrides.
4. **State is observable**: action trả typed result và state thể hiện rõ
   idle/loading/success/error; không phụ thuộc toast/navigation để biết kết quả.
5. **Test through public behavior**: ưu tiên user event + rendered state; chỉ
   mock ở I/O boundary, không mock chi tiết nội bộ giữa các layer.
6. **Deterministic async**: poll/debounce/timeout dùng injected scheduler/clock
   hoặc fake timers; mỗi subscription có teardown được test.
7. **Coverage is a guard, not the goal**: đặt threshold cho core/usecase/state
   đã refactor và tăng dần; acceptance cuối dựa trên use-case matrix bên dưới.

## Current Design Diagnosis

### `delivery_web` — khoảng trống lớn nhất ở executable UI proof

- Khoảng `6,076` dòng TypeScript/TSX, 15 route owner/admin nhưng chỉ có một
  `scripts/verify-action-contracts.mjs` đọc source. Script này chặn route/path
  sai nhưng không chứng minh form submit, loading, error, redirect hay state
  update thật sự chạy.
- `AuthProvider`, `RestaurantProvider`, `MenuProvider`, `OrderProvider` import
  singleton service và `TokenStorage` trực tiếp. Admin page import
  `adminService`/`flashSaleAdminService` trực tiếp. Vì vậy test buộc module-mock
  global, dễ leak state giữa case và không thể lắp một fake backend cho journey.
- `apiClient.ts` vừa là Axios composition root, token refresh coordinator,
  storage caller và browser redirect handler. Đây là nhiều trách nhiệm trong
  một global; cần factory nhưng phải migrate sau provider seam để không đổi auth
  behavior cùng lúc với UI.
- Page/form lớn (`RestaurantForm` 353 dòng, `AdminFlashSalePage` 336,
  `RestaurantOrders` 314) đang giữ validation, payload mapping, async action và
  rendering trong cùng component. Chỉ tách khi test đầu tiên chỉ ra boundary;
  không chia file theo hình thức.

Target structure theo thứ tự migrate:

```text
src/app/
  dependencies.tsx       # typed ports + production registry + test overrides
  AppProviders.tsx       # một composition root cho app/router tests
src/services/api/
  createApiClient.ts     # factory nhận SessionStore + onSessionExpired
  apiClient.ts           # production instance, compatibility export tạm thời
src/test/
  renderApp.tsx          # MemoryRouter + dependency registry
  builders/              # canonical User/Restaurant/Menu/Order fixtures
  fakes/                 # in-memory service/storage ports
```

Public component/provider API giữ ổn định trong lúc migrate. Mỗi provider/page
đọc dependency qua `useAppDependencies`; production registry vẫn trỏ service
hiện tại. Sau khi caller cuối đã migrate mới cân nhắc bỏ singleton export.

### `shipper_app2` — core có DI một phần nhưng composition/native vẫn global

- Khoảng `8,291` dòng TypeScript/TSX. Redux thunk đã nhận `ServiceRegistry` qua
  `extraArgument`, là seam tốt cần giữ. Tuy nhiên `store.ts` tự tạo
  `WebSocketManager.getInstance`, listener middleware, store singleton và gắn
  session-expired handler ngay khi import; test store độc lập không tạo được.
- `AppNavigator` import `appInitializer` singleton và hard-code timer 1 giây;
  `App.tsx` import `gpsService` singleton. `gpsService`, `WebSocketManager`,
  AsyncStorage, Mapbox và `@env` là native/global boundary cần adapter/factory.
- Screen lớn (`OrderDetailScreen` 654 dòng, `DocumentsScreen` 498,
  `MatchFoundPopup` 456, `ShipperProfile` 449, `LoginScreen` 391) kết hợp
  formatting/validation/action/render. Trước tiên test thunk/reducer + các nút
  lifecycle; sau đó mới extract pure view-model/validation nào thực sự dùng lại.
- Normal Jest PASS `61` test nhưng coverage instrumentation fail khi Babel plugin
  `react-native-dotenv` xử lý `runtime.ts`. Env import phải cô lập vào production
  adapter để core runtime functions nhận plain config.

Target structure:

```text
src/app/
  createAppDependencies.ts # production service/native registry
  createAppStore.ts        # fresh middleware/listeners/store per invocation
  AppRuntimeContext.tsx    # initializer/scheduler/GPS lifecycle ports
src/config/
  env.ts                   # file duy nhất import @env
  runtime.ts               # pure normalize/parse/createRuntimeConfig
src/test/
  createTestStore.ts
  builders/
  fakes/
```

`store` default export được giữ tạm cho `App.tsx`; tests và navigator dùng
factory/context. `setSessionExpiredHandler` phải trả teardown hoặc được gắn tại
production composition root để nhiều test store không tranh global callback.

### `delivery_app` — nhiều layer/Provider nhưng graph không nhất quán

- Flutter có Riverpod/Clean Architecture ở auth/restaurant/order/address nhưng
  coverage code viết tay mới `10.16%` trong graph instrument. Nhiều provider,
  notifier, repository và widget reachable có `0` hit; 135 test hiện không phản
  ánh số feature/dòng code lớn.
- DI tốt ở các `*_di_providers.dart`, nhưng vẫn có runtime/global boundary phân
  tán: Firebase/push, Mapbox, Geolocator, deep link, image picker, Hive/
  SharedPreferences và logger. Một số provider cố ý throw để được override
  (`sharedPreferencesProvider`), pattern này cần chuẩn hóa thành app overrides.
- Cart/order có provider trùng/legacy (`providers.dart`, `providers_new.dart`,
  generated provider graph lớn) và cùng capability xuất hiện ở service,
  repository, usecase, notifier, widget mà thiếu test điều phối. Không xóa/tái
  cấu trúc hàng loạt trước khi route/caller test khóa hành vi.
- Hidden support/IAP/livestream/flash graph còn trên disk nhưng không reachable
  MVP. Coverage policy không được buộc test dead graph; reachability scan phải
  khóa để nó không quay lại runtime ngoài product authority.

Target structure/convention:

```text
lib/core/app_dependencies.dart        # tập hợp ProviderOverride production
test/support/
  app_harness.dart                    # MaterialApp/GoRouter/ProviderScope
  provider_overrides.dart             # fake network/storage/native ports
  builders/                           # canonical DTO/entity fixtures
test/journeys/                         # route/widget journeys theo customer flow
```

Không thêm DI package. Riverpod provider là port/composition mechanism; domain
usecase tiếp tục nhận repository interface qua constructor. Widget không được
khởi tạo Dio/Firebase/native object trực tiếp. Mỗi notifier cần test
loading→data/error, retry/double-submit và dispose/subscription khi có stream.

## Concrete Dependency Ports

| Boundary | Web | Shipper | Flutter | Deterministic fake |
|---|---|---|---|---|
| Session/token storage | `SessionStore` quanh localStorage | `AuthStoragePort` quanh AsyncStorage | existing token repository/provider | in-memory token/user map |
| HTTP | `ApiTransport`/Axios instance factory | existing service registry + injected Axios later | Dio provider override + fake repository | request recorder + queued response/error |
| Time/timer | `Clock` nếu form/time policy cần | `Scheduler` cho splash/poll/reconnect | provider/clock cho debounce/retry | fake timers/manual clock |
| Realtime | none cho MVP web | `LocationSocketPort` | `SocketClient`/location repository | controllable stream + connect/dispose counters |
| Native location | n/a | `GpsPort` | `LocationService` | permission/location sequence |
| Map/directions | n/a/currently hidden fleet | `DirectionsPort` | `IMapService` | fixed route/error |
| Push/deep link | n/a | inbox wake-up boundary | push/deep-link providers | event stream controller |
| Navigation | MemoryRouter | navigation container/context | GoRouter test config | route assertions, không mock widget internals |

Các port chỉ chứa method mà consumer thật dùng; không tạo `God interface` chung
cho mọi feature. Fake ghi call/payload và cho phép queue success/error để cùng
một journey chứng minh cả orchestration lẫn contract caller.

## Vertical-Slice Migration Order

Mỗi slice đi qua đúng chuỗi sau trước khi chuyển slice kế tiếp:

1. Khóa behavior hiện tại bằng characterization test ở public boundary.
2. Đưa I/O/global access ra typed port nhưng giữ production adapter cũ.
3. Di chuyển business rule/state transition thành pure function/usecase khi có
   lý do rõ từ test; không tạo layer rỗng chỉ để đủ tên Clean Architecture.
4. Thêm success, backend/native failure, invalid input, loading/double-submit và
   retry/teardown tùy use case.
5. Chạy focused test, static check và build repo đó; cập nhật progress/evidence
   trong plan.

Order cụ thể:

1. **Web auth/routing** → provider dependency root → login/restore/logout/role.
2. **Shipper store/runtime** → fresh store/env pure config → auth/init/session.
3. **Flutter app harness** → auth/router/profile characterization.
4. **Fulfilment spine**: web owner order; Flutter catalog→cart→checkout→order;
   shipper online→offer→accept→status.
5. Address/menu/profile mutations, notification/history/admin moderation.
6. GPS/socket/map/deep-link/native adapter contract và cleanup.

## Coverage Rollout

- Wave 0 chỉ yêu cầu coverage command chạy ổn định, không đặt threshold theo
  baseline thấp/khuyết để tránh incentive exclude code.
- Sau mỗi vertical slice, đặt **changed/core scope** tối thiểu: statements/lines
  `80%`, branches `70%`, functions `80%`; parser/state-machine/security/session
  rule mục tiêu branch `90%+`.
- Khi Wave 2 xong, đặt repository-wide floor chỉ trên reachable handwritten
  business/state/service code. Generated, style constants, platform generated
  và hidden unreachable graph được exclude bằng pattern công khai trong config.
- Không tăng threshold nếu use-case matrix còn action reachable chưa có test;
  ngược lại matrix đầy đủ không cho phép threshold đỏ bị bỏ qua.

## Use-Case Acceptance Matrix

| Client | Reachable use case/action | Required automated proof |
|---|---|---|
| Web auth/routing | restore session, login owner/admin, wrong role, logout, 401 refresh failure, protected/unauthorized redirect | service + provider + routed component tests |
| Web restaurant | load/select/create/update restaurant; load/create/update/delete menu | provider/component user-event journey, success/error/loading |
| Web restaurant orders | list/filter/page, inspect, confirm, reject, refresh | provider/page action tests with request assertions and failure state |
| Web admin | dashboard reads; order/shipper lists; rating moderate; coupon create/delete; flash campaign create/status/item approve | page/component action tests for every enabled button/form |
| Customer auth/profile | initialize, login/register/refresh/logout, biometric boundary, profile read/update | usecase/notifier/widget tests with fake storage/network/native port |
| Customer catalog/cart | restaurant list/detail/menu/search, add/update/remove/clear cart, cross-restaurant rule | notifier/repository + widget journey |
| Customer checkout/order | address select/create/update/default/delete, preview, COD place order, loading/error, confirmation | provider/widget journey with canonical fixtures |
| Customer orders | list/filter/detail, cancel, reorder, rate restaurant, delivery status/location tracking reconnect/error | notifier/widget + socket adapter tests |
| Customer notifications/settings | list/read/read-all, navigation, theme/settings actions | provider/widget action tests |
| Shipper auth/init | restore/login/social/logout/session expiry, profile bootstrap, role/error branches | initializer/thunk + navigator/screen tests |
| Shipper availability/offer | online/offline, poll foreground, current offer recover, accept/reject/expired/conflict | store/listener/component journey with fake timers/services |
| Shipper delivery | active delivery restore, pickup, delivering, delivered, cancel-assignment, illegal transition/error/retry | thunk/reducer + screen action journey |
| Shipper tracking | GPS permission/start/stop, location publish/socket reconnect/teardown, route adapter | middleware/service contract with fake native/socket/clock |
| Shipper history/inbox/profile | history/detail, notification read/read-all, profile/doc/rating read | thunk + screen action tests |
| Cross-client | customer COD order -> owner confirm -> shipper recover/accept/status -> customer tracking/history; reject/rematch and auth expiry variants | existing backend runtime harness + per-client journey/contract gates; no UI runner may invent backend state |

## Approach

### Wave 0 — Harness và measurable baseline

1. Tạo test runner DOM cho web (Vitest + Testing Library + user-event/jsdom),
   test setup/render helpers và coverage script.
2. Sửa shipper env/composition seam để Jest coverage chạy cùng số suite như
   normal Jest; thêm `createAppStore(dependencies, preloadedState)`.
3. Chuẩn hóa Flutter `ProviderScope` overrides, fake builders và coverage filter
   bỏ generated/platform-only code.
4. Ghi script/README ngắn trong từng repo mô tả test layers và lệnh verify.

### Wave 1 — Auth, session và routing

Refactor composition root và hoàn tất matrix auth/session/role/navigation cho
web, shipper, customer. Đây là nền cho mọi journey sau.

### Wave 2 — Core fulfilment journey

1. Web restaurant/profile/menu/order actions.
2. Customer catalog/cart/address/checkout/order.
3. Shipper availability/offer/delivery lifecycle.

Mỗi slice chỉ merge khi happy path, backend failure và double-submit/loading
đều có proof.

### Wave 3 — Secondary reachable surfaces

Admin moderation/growth pages, customer tracking/notification/profile/settings,
shipper GPS/socket/history/inbox/profile.

### Wave 4 — Cross-client gate và cleanup

1. Chạy toàn bộ repo verify + root contract gate.
2. Chạy backend canonical happy/failure harness nếu runtime sẵn sàng; nếu không,
   ghi rõ evidence gần nhất và blocker môi trường, không thay bằng mock claim.
3. Search global/singleton/native call ngoài composition/adapter allowlist;
   loại duplicate/dead test helpers và cập nhật use-case matrix kết quả.
4. Chốt threshold có ý nghĩa dựa trên coverage thật; ghi rõ limitation native
   device sanity nhưng không biến device/emulator thành acceptance gate.

## Risks And Recovery

- Refactor provider/store có thể gây thay đổi lifecycle hoặc duplicate request.
  Làm từng vertical slice, giữ public API tạm thời và chạy focused test + build
  sau mỗi slice.
- Thêm test dependencies có thể làm lockfile lớn hoặc xung đột React 19/RN 0.80.
  Chọn version tương thích lock hiện tại; rollback riêng package/lockfile nếu
  runner không ổn định, không bỏ static/build gates.
- Fake quá sâu có thể test sai contract. Canonical fixtures phải xuất phát từ
  backend inventory/runtime evidence và service adapter vẫn có request/response
  contract test.
- Coverage tổng thấp do UI/generated/dead graph. Không hạ chất lượng bằng ignore
  tuỳ tiện; threshold chỉ exclude generated/platform glue có lý do và use-case
  ledger phải đầy đủ.
- Recovery: mỗi repo là git repo riêng; thay đổi theo wave và không đụng backend.
  Nếu một repo gate đỏ, dừng wave đó ở seam tương thích cũ trong khi hai repo kia
  vẫn độc lập chạy được.

## Progress

- [x] Đọc workflow, system/client architecture và contract-alignment plan.
- [x] Xác nhận ba worktree sạch; chạy baseline verify cho cả ba client.
- [x] Đo baseline coverage Flutter và phát hiện coverage-instrumentation defect
  của shipper; xác nhận web chưa có executable UI test.
- [x] Wave 0: web test runner + injected dependency root + baseline tests.
  Vitest/Testing Library chạy `6/6` auth/role/DOM journey; `npm run verify` và
  coverage command PASS. Baseline executable coverage mới: `25.44%` line,
  `10.21%` branch trên toàn source reachable/included. Test phát hiện và sửa
  accessible label của login cùng accessible name của logout control.
- [x] Wave 0: shipper deterministic env/store/initializer + coverage gate.
  `@env` được cô lập khỏi pure runtime config; fresh Redux store nhận injected
  registry/listener; initializer, scheduler và GPS đi qua runtime context; GPS
  đọc đúng injected store thay vì singleton ngầm. `npm run verify` PASS `17`
  suite/`63` test; coverage chạy đủ suite và đạt `53.43%` line, `57.79%` branch.
- [x] Wave 0: Flutter test harness/coverage policy.
  Production pre-scope overrides tập trung ở `AppDependencies`; push dùng
  application-facing port nên test không construct Firebase; widget/router
  harness chuẩn hóa phone viewport, localization, ScreenUtil và Riverpod
  overrides. Analyzer PASS, `136/136` test PASS; filtered
  coverage checker PASS với baseline `1,144/11,261` line (`10.16%`) trên 276
  file handwritten reachable.
- [x] Wave 1: auth/session/routing matrix cả ba client.
  - Web dùng dependency context cho auth/service/session/notification/clock;
    DOM journey khóa restore/login/role/protected routing/logout và token refresh.
  - Shipper dùng fresh store + runtime context; login/init/session boundaries có
    test độc lập, không cần singleton/native runtime thật.
  - Customer dùng Riverpod overrides cho device/social/push/session boundaries;
    auth notifier và router được test qua public state/route.
- [x] Wave 2: fulfilment journey spine cả ba client.
  - Web owner: create/update restaurant; load/search/create/update/delete menu;
    order filter/page/confirm/reject; có validation, loading, single-submit,
    failure và retry.
  - Customer: catalog/search/detail/menu; cart add/update/note/remove/clear và
    cross-restaurant guard; address CRUD/default; preview + COD create/cancel;
    order list/page/detail/retry. Request order được build bằng pure function từ
    cart, address và canonical server preview.
  - Shipper: online/offline/recover offer; accept/reject/expired/conflict;
    pickup -> delivering -> delivered; illegal transition, cancel assignment,
    COD guard, double-submit và retry.
- [x] Wave 3: secondary server-backed actions đã migrate.
  - Web admin có proof cho orders, shipper filter, rating moderation,
    coupon create/delete và flash-sale create/activate/item approval.
  - Customer profile và notification load/read/read-all/delete/error/retry có
    provider/widget proof; failed delete không còn xóa row lạc quan sai trạng thái.
  - Shipper history, notification và profile/document/rating reads có
    success/error/retry proof.
- [x] Wave 3 native/read-only remainder: customer rating/tracking/map/settings,
  web dashboard/reviews, shipper GPS/socket/social identity đều có injected
  port, public action/error/retry hoặc lifecycle/teardown proof. Splash delay,
  theme storage và map canvas cũng không còn buộc host tests đụng wall clock,
  SharedPreferences hay Mapbox SDK.
- [x] Wave 4: full client gates và root contract gate xanh. Backend contract
  không đổi nên dùng canonical COD/failure/runtime proof gần nhất đã freeze
  trong `mvp-client-alignment.md` thay vì mutate/re-run backend ngoài scope.
  Device/emulator sanity vẫn là regression tùy chọn theo authority hiện hành,
  không phải điều kiện closeout và không được suy ra từ fake tests.

### Current executable evidence

| Client | Automated result | Reachable handwritten coverage | Main proof boundary |
|---|---:|---:|---|
| `delivery_web` | `7` files / `35` tests PASS | lines `66.90%`, branches `57.05%`, functions `59.53%`, statements `66.00%` | routed DOM/component journeys với fake services/session/clock/delay |
| `shipper_app2` | `26` suites / `97` tests PASS | lines `76.50%`, branches `66.49%`, functions `71.42%`, statements `75.25%` | fresh Redux store, thunk/reducer và screen actions với fake runtime/native/social ports |
| `delivery_app` | `192/192` tests PASS | `3,140/11,403` lines (`27.54%`) trên `280` file | Riverpod notifier/repository/widget proof với overrides và canonical builders |

Coverage Flutter vẫn thấp vì graph client lớn còn nhiều UI/platform/legacy code;
không được diễn giải `27.54%` thành mức hoàn tất use case. Mức hoàn tất hiện tại
đến từ matrix hành động phía trên và test public behavior, còn coverage dùng để
phát hiện slice chưa được instrument.

### Final use-case ledger

| Matrix row | Status | Executable evidence |
|---|---|---|
| Web auth/routing | PASS | routed restore/login/role/logout journeys + concurrent 401 refresh/fail-closed tests |
| Web restaurant | PASS | create/update profile; menu load/search/create/update/delete; validation/loading/failure/retry DOM tests |
| Web restaurant orders | PASS | filter/page/inspect/confirm/reject, injected bounded delay, single-submit and retry DOM tests |
| Web admin | PASS | all supported destinations plus order/shipper/rating/coupon/flash-sale read and mutation actions |
| Customer auth/profile | PASS | login/register/Google/cancel/refresh/logout/biometric lifecycle, form validation/single-submit, profile update/retry |
| Customer catalog/cart | PASS | catalog/detail/search plus add/update/note/remove/clear and cross-restaurant guard |
| Customer checkout/order | PASS | address CRUD/default, canonical preview/COD build, create/cancel/loading/failure/retry and route checks |
| Customer orders/tracking | PASS | list/page/detail/reorder/rating; REST/socket route tracking, late-response fence, map controls and teardown |
| Customer notifications/settings | PASS | load/read/read-all/delete/error/retry widget actions and injected theme persistence |
| Shipper auth/init | PASS | credential and Google UI actions, cancel/native/backend failure, profile bootstrap, restore/session expiry/logout |
| Shipper availability/offer | PASS | online/offline/poll/recover/accept/reject/expired/conflict with fake clock/scheduler |
| Shipper delivery | PASS | restore, pickup/delivering/delivered, illegal/COD guard, cancel failure and retry in store + screen tests |
| Shipper tracking | PASS | GPS permission/publish/cleanup, WebSocket JWT/heartbeat/bounded reconnect/generation fence, route policy |
| Shipper history/inbox/profile | PASS | history filter/retry, notification read/read-all/delete, profile/document/rating failure/retry |
| Cross-client contract | PASS at no-emulator acceptance level | unchanged canonical backend COD/failure runtime evidence + root gate aligned at `146` controller methods + per-client action suites |

## Decisions

- 2026-07-29: Giữ backend contract freeze làm product authority; refactor này
  chỉ đổi cấu trúc nội bộ/test proof.
- 2026-07-29: Không dùng emulator/device làm acceptance gate. Native SDK được
  bọc adapter; real-device là sanity/regression tùy chọn và fake test không được
  dùng để tuyên bố SDK/OS thật hoạt động.
- 2026-07-29: Dùng DI thủ công/framework-native (React context/factory, Redux
  thunk extra argument/store factory, Riverpod override) thay vì thêm service
  locator/DI framework mới; ít magic và fake dễ hơn.
- 2026-07-29: Use-case matrix là tiêu chí "gần full"; coverage threshold chỉ
  bảo vệ code nghiệp vụ đã refactor, không thay thế proof hành động.

## Validation

- Focused proof:
  - Web: Vitest service/provider/component tests with user events.
  - Shipper: Jest services/thunks/reducers/middleware/screens with fake timers.
  - Flutter: unit/notifier/widget tests through `ProviderScope` overrides.
- Integration or end-to-end proof:
  - In-process journey tests per client with fake adapters and canonical
    fixtures.
  - Existing backend COD/failure runtime harness for true cross-client state.
- Repository-required checks:
  - `delivery_web`: `npm run verify` PASS `35/35`; coverage PASS với `66.90%`
    line, `57.05%` branch.
  - `shipper_app2`: `npm run verify:coverage` PASS `26/26` suite, `97/97` test;
    coverage `76.50%` line, `66.49%` branch.
  - `delivery_app`: `fvm flutter analyze` PASS không issue;
    `fvm flutter test --coverage` PASS `192/192`;
    `fvm dart run tool/coverage_policy.dart` PASS ở `27.54%` reachable line.
  - Root: `scripts/verify-mvp-polyrepo-contract.sh` PASS; inventory khớp `146`
    controller method, client transport và hidden-route scan sạch.
  - `git diff --check` PASS ở cả ba client repo.

## Result

Ba client đã có framework-native composition root và deterministic fake seams
đủ để chạy tự động mọi capability MVP reachable ở tầng phù hợp mà không cần
backend/device thật: React context/service factory cho web, Redux store/runtime
factory cho shipper, và Riverpod production/test overrides cho Flutter.

Test mới đã tìm và khóa các lỗi thật: review failure bị hiển thị như empty
state, theme flash sai, rating widget tự tạo HTTP client, tracking trả về trước
polyline và bị late response hồi sinh, dispose dùng Riverpod ref không an toàn,
header/auth form overflow trên phone, register trả về trước auto-login, cùng
WebSocket/GPS singleton-timer leak. Native SDK imports reachable đã được cô lập
vào production adapter; splash/search/retry/polling dùng delay/scheduler fake.

Kết quả này là near-full use-case/action automation, không phải tuyên bố full
device E2E. Root gate và prior backend runtime evidence chứng minh contract/state
liên client; fake Mapbox/GPS/push/social/biometric không chứng minh SDK/OS thật.
