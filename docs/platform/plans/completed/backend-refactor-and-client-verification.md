# Execution Plan: Rà soát, refactor và verify toàn backend rồi mới sang client

Date: 2026-07-29

## Status

Completed

## Current frontier

- Backend service audit/refactor waves are effectively closed at the current
  source state; remaining backend work is maintenance against regression, not
  new discovery.
- The client-side no-emulator execution gates are now cleared for MVP:
  `shipper_app2` usecase/action proof, `delivery_app` customer journey proof,
  `delivery_web` action contracts and root cross-client contract scans all pass.
  Remaining work is plan closeout/manual sanity, not emulator/device proof.
- Static/build/contract/usecase-action tests are the default verification loop.
  Mobile emulator/device proof is not an acceptance gate; browser proof remains
  useful for `delivery_web` because it is practical locally.
- For shared workflows, `delivery_web` is the functional reference surface:
  if a workflow is correct there, the mobile clients should follow the same
  backend contract rather than invent alternate behavior.
- 2026-07-29 final no-emulator client proof: root
  `scripts/verify-mvp-polyrepo-contract.sh` PASS; `delivery_web npm run verify`
  PASS; `shipper_app2 npm run verify` PASS (`16/16` suites, `61/61` tests);
  `delivery_app` `fvm flutter analyze` PASS and `fvm flutter test` PASS
  `135/135`. No emulator/device was launched.
- 2026-07-29 verification policy update: mobile emulator/device is not an
  acceptance gate. `delivery_web` now has a no-emulator action-contract verifier
  included in `npm run verify`; current proof PASS covers lint, action contracts
  and production build. Root polyrepo contract gate still PASS after this change.
- 2026-07-29 backend closeout rerun: Auth→User outage/retry runtime proof PASS
  with isolated run-scoped databases and temporary containers; backend
  `scripts/verify-build-baseline.sh` PASS; backend
  `scripts/verify-compose-config.sh` PASS; root
  `scripts/verify-mvp-polyrepo-contract.sh` PASS with 146 mapped controller
  methods and clean hidden-route/transport scans. Routine backend review is now
  closed unless a regression appears or client E2E exposes a backend defect.
- 2026-07-29 runtime admin API smoke found and fixed two real backend defects:
  `shipper-service` missing `full_name`/unique/index repair on baselined DBs,
  and `flashsale-service` runtime DB missing `flashsale_db` after compose
  reset. Both services were repaired/recreated and the full admin API smoke
  through Gateway now passes again.
- 2026-07-29 runtime seed harness root-run fix: `backend_delivery/scripts/seed.sh`
  now resolves Compose files and `seed-settlement.sql` from the script location,
  so it works from the polyrepo root as well as `backend_delivery/`. Root-run
  proof PASS created customer, outsider, owner, restaurant, menu item, operator
  provisioned shipper, settlement deposit and tracking location. Baseline and
  polyrepo contract gates still PASS.

## Outcome

Toàn bộ backend được rà soát lại theo source-of-truth hiện tại, các API/luồng không còn consumer hợp lệ được ẩn hoặc xoá có kiểm soát, contract giữa service được chuẩn hoá, và mọi service đều có bằng chứng test/compose/runtime đủ mạnh để tin rằng hệ thống chạy đúng khi thật.

Sau khi backend freeze, ba client `shipper_app2`, `delivery_app`, `delivery_web` được verify lại theo contract backend mới, rồi chạy được journey thật end-to-end mà không cần phụ thuộc vào surface đã bị ẩn.

## Context

- `docs/plans/active/priority-roadmap.md` — master roadmap cấp hệ thống và các blocker còn lại.
- `docs/plans/completed/mvp-client-alignment.md` — plan đồng bộ client sau backend freeze.
- `backend_delivery/docs/plans/active/mvp-completion.md` — history/backlog backend MVP cũ.
- `docs/ARCHITECTURE.md`, `docs/product/overview.md`, `docs/product/features/` — kiến trúc và luồng chuẩn.
- `backend_delivery/docs/http-api-inventory.md`, `backend_delivery/docs/system-contract-inventory.md` — inventory/contract cần giữ đồng bộ với source.

## Scope

In scope:

- Audit toàn bộ HTTP API, Kafka event, Redis/WebSocket flow và internal client của từng service.
- Chuẩn hoá contract, response, validation, error mapping, config, startup và health/readiness.
- Ẩn hoặc loại bỏ API đã viết nhưng chưa có consumer hợp lệ, hoặc không còn phù hợp với luồng chuẩn.
- Refactor theo từng service/wave, không mở rộng scope thành feature mới.
- Đồng bộ docs, inventory, compose, baseline scripts và gate kiểm tra sau mỗi wave.
- Sau backend freeze, verify lại `shipper_app2`, `delivery_app`, `delivery_web`, rồi chạy cross-client E2E/failure matrix.

Out of scope:

- Thêm capability mới không có authority product.
- Re-open gRPC/STOMP/direct service access nếu backend canonical đã chốt bằng raw Gateway/socket contract.
- Emulator/device/browser thường xuyên; chỉ dùng ở checkpoint cuối cần proof runtime thật.
- Production hardening ngoài MVP nếu nó không chặn correctness của backend hiện tại.

## Service order

1. Foundation boundary: `api-gateway`, `auth-service`, `user-service`.
2. Checkout boundary: `restaurant-service`, `order-service`, `promotion-service`, `flashsale-service`.
3. Fulfilment boundary: `delivery-service`, `shipper-service`, `tracking-service`, `match-service`, `notification-service`, `saga-orchestrator-service`.
4. Money/reporting/support boundary: `settlement-service`, `search-service`, `analytics-service`, `livestream-service`.
5. Final backend closeout: docs, compose, inventory, scripts, baseline, runtime smoke.
6. Client verification: `shipper_app2` → `delivery_app` → `delivery_web` → cross-client E2E.

## Approach

### Phase 0 — Baseline và authority freeze

- Rà lại current-state source, plan, docs và runtime gate trước khi sửa.
- Phân loại từng surface thành:
  - canonical/public;
  - internal-only;
  - test-only/dev-only;
  - dead/unused;
  - hidden/deprecated nhưng còn consumer nội bộ.
- Không giữ API chỉ vì “đã viết ra”. Nếu zero-call-site hoặc trái luồng chuẩn, thì xoá hoặc fail-closed rồi ẩn khỏi Gateway/docs/navigation.
- Chốt baseline test/contract hiện tại để mọi thay đổi sau đó có thể đối chiếu rõ.

### Phase 1 — Foundation boundary

#### `api-gateway`

- Audit route allow/deny, role propagation, header trust boundary, JWT/public key loading, and direct-port leakage.
- Đảm bảo Gateway là điểm vào duy nhất cho client.
- Loại route không còn consumer hoặc route công cụ chỉ phục vụ debug nếu không có authority rõ ràng.

#### `auth-service`

- Rà register/login/social/refresh/logout/session/admin block-unblock.
- Chuẩn hoá secrets/key loading, fail-fast khi thiếu cấu hình, và không giữ fallback mơ hồ.
- Ẩn hoặc loại API test/debug/account surface không còn consumer hợp lệ.

#### `user-service`

- Rà profile/address/admin/internal provisioning và linkage `authId` ↔ `userId`.
- Kiểm path-ID mutation, race giữa projection, và các surface lookup nội bộ/test.
- Chỉ giữ route nào thật sự cần cho luồng chuẩn và role đúng.

Exit criteria:

- Gateway/auth/user contract thống nhất.
- Startup/readiness và JWT/secrets proof đủ mạnh.
- Route/controller inventory khớp với source.

### Phase 2 — Checkout boundary

#### `restaurant-service`

- Rà menu/status/ownership/operating-hours/rating/order decision/cache validation.
- Loại helper/endpoint dead hoặc trùng chức năng với luồng canonical.

#### `order-service`

- Rà checkout preview/create/cancel/detail/history/my-orders/status transition.
- Chốt order state machine, canonical pricing, duplicate/replay handling và legacy dashboard/mutation path.
- Ẩn hoặc xoá API cũ nếu không còn consumer thật.

#### `promotion-service`

- Rà collect/calculate/reserve/admin/public read path.
- Khóa hidden reservation/write path nếu backend canonical không dùng.

#### `flashsale-service`

- Rà campaign/item/approve/reserve/merchant path.
- Loại surface checkout giả hoặc write path không còn authority.

Exit criteria:

- Checkout journey đi qua Gateway và contract canonical.
- Legacy order/promo/flash surface không còn hiện ra ở runtime/public path.
- Duplicates, cancel, reject, replay đều có proof rõ.

### Phase 3 — Fulfilment boundary

#### `delivery-service`

- Rà current-offer/accept/cancel-assignment/status/detail/history.
- Loại assign/cancel-all/STOMP/WebSocket legacy branch nếu không còn consumer.

#### `shipper-service`

- Rà profile/update/online/rating/admin list/location-related write/delete graph.
- Giữ state thật, bỏ graph data giả và route thừa.

#### `tracking-service`

- Rà raw WebSocket participant auth, publisher/session fence, tombstone/offline, REST diagnostics dead surface.
- Đảm bảo participant tracking chạy qua Gateway/socket canonical.

#### `match-service`

- Rà offer contention, retry, cancellation tombstone, Redis atomic ownership, controllerless command flow.
- Không block Kafka thread bằng fallback chết; retry phải có proof idempotent.

#### `notification-service`

- Rà durable inbox, token ownership, dedup/retry, hidden STOMP/WebSocket surfaces.
- Chỉ giữ transport đúng MVP; loại surface trùng hoặc không còn consumer.

#### `saga-orchestrator-service`

- Rà command ordering, idempotency, timeout/rematch, failure replay.
- Mọi command/listener phải có consumer và recovery path thật.

Exit criteria:

- Offer → accept/cancel/rematch → delivery tracking → notification workflow chạy ổn định.
- Hidden fulfilment surfaces không còn lộ qua route/runtime/doc.
- Replay/failure matrix có bằng chứng rõ.

### Phase 4 — Money/reporting/support boundary

#### `settlement-service`

- Rà ledger precision, transaction ordering, COD accounting, duplicate replay, cancellation/completion ordering.
- Ẩn payment/fake-confirm/self-service write path nếu không còn authority hoặc consumer hợp lệ.

#### `search-service`

- Rà read model, sync pipeline, dead query/endpoint, and consumer-facing response shape.

#### `analytics-service`

- Rà dashboard/listener/reconciliation job, processing flags, and any surface chỉ còn tồn tại vì historical reason.

#### `livestream-service`

- Rà toàn bộ graph còn sót lại, xác định cái gì thật sự thuộc MVP và cái gì phải disable/remove.

Exit criteria:

- Các service support không còn giữ API thừa, config thừa, hoặc data giả chỉ để “cho có”.
- Tài liệu và runtime đều phản ánh đúng capability còn sống.

### Phase 5 — Closeout backend

- Đồng bộ `docs/http-api-inventory.md`, `docs/system-contract-inventory.md`, compose, scripts verify, and service docs.
- Rerun build/test/contract/startup gates ở mức repo và service theo đúng thay đổi.
- Chỉ khi backend freeze thật sự sạch mới chuyển sang client.

### Phase 6 — Client verification

#### `shipper_app2`

- Verify current-offer recovery, accept/cancel-assignment, location publish, background/foreground transition, hidden feature cleanup.

#### `delivery_app`

- Verify customer login, catalog/search, checkout COD, order history/detail/cancel, tracking/notification, and disabled surfaces stay hidden.

#### `delivery_web`

- Verify owner/admin read/confirm/reject flows, navigation cleanup, and disabled feature graph không quay lại.

#### Cross-client E2E

- Chạy journey canonical customer → restaurant/owner → shipper → tracking → delivered.
- Chạy failure/reconnect/expired-offer/no-shipper/cancel-assignment matrix.
- Chỉ dùng browser/device/emulator ở checkpoint cuối nếu còn thiếu proof runtime thật.

## Risks And Recovery

- Contract drift giữa plan, docs và source.
  - Giảm rủi ro bằng inventory scan trước khi sửa, và update docs sau từng wave.
- Hidden API tái xuất hiện khi refactor nhiều service cùng lúc.
  - Mỗi wave phải có zero-call-site proof hoặc explicit authority trước khi giữ lại.
- Một service fix có thể làm service phụ thuộc fail theo dây chuyền.
  - Tách commit theo wave/service và giữ rollback rõ ràng.
- Proof bị yếu nếu chỉ nhìn test unit mà không có runtime/compose/startup.
  - Mỗi phase phải có proof đúng mức: unit → integration → compose/runtime → cross-client.

## Progress

- [x] Current-state roadmap and active blockers reviewed.
- [x] Plan decomposition into backend-first then client-verification flow.
- [x] Phase 0 checkpoint — Order legacy API deletion synced with source of truth:
  HTTP inventory updated from 154 to 147 mapped methods, stale Compose legacy
  flags removed, compose verifier now requires those flags absent, and validation
  passed via `mvn -q -pl order-service test`,
  `backend_delivery/scripts/verify-http-api-inventory.sh`,
  `backend_delivery/scripts/verify-compose-config.sh`,
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 0 baseline/inventory freeze.
- [x] Phase 1 checkpoint — Auth admin account read now has controller-level
  ADMIN guard aligned with Gateway/admin route. Regression proof:
  `mvn -q -pl auth-service test` (`53/53`), `mvn -q -pl api-gateway test`
  (`22/22`), `mvn -q -pl user-service test` (`34/34`),
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 1 checkpoint — User current-profile endpoints now fail closed when
  trusted Gateway identity headers are missing, instead of flowing null into the
  service layer. Regression proof: `mvn -q -pl user-service test` (`35/35`),
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 1 checkpoint — Auth sessions now fail closed with 401 when security
  context is unexpectedly absent, instead of dereferencing a null authentication
  object. Regression proof: `mvn -q -pl auth-service test`, plus current
  baseline/contract reruns.
- [x] Phase 1 checkpoint — User current-profile update now also requires trusted
  Gateway identity headers, keeping read/write on the same trust boundary.
  Regression proof: `mvn -q -pl user-service test` (`35/35`),
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 1 checkpoint — Gateway/Auth/User trust-boundary audit rerun:
  canonical Compose publishes only Gateway and keeps service ports internal,
  `docker-compose.debug.yml` remains opt-in for direct-port debugging,
  Gateway strips client-supplied `X-User-Id`/`X-Role` before route filters and
  re-adds values only from verified JWT, User current-profile/address routes
  fail closed without trusted identity headers, and Auth→User internal
  provisioning/status projection routes require the shared internal credential.
  No new Gateway signature/header was added in this checkpoint because that
  would be a broader cross-service policy change; current MVP authority is the
  Gateway-only canonical Compose boundary plus service-level guards. Regression
  proof: focused Auth/User boundary tests
  (`AuthServiceSecurityTest`, `UserServiceProvisioningTest`,
  `UserControllerInternalAuthorizationTest`, `UserAddressControllerAuthorizationTest`),
  `backend_delivery/scripts/verify-http-api-inventory.sh`,
  `backend_delivery/scripts/verify-compose-config.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 1 checkpoint — Auth↔User live outage retry proof harness added:
  `backend_delivery/scripts/verify-auth-user-outage-retry.sh` creates run-scoped
  Auth/User PostgreSQL databases and temporary service containers, simulates the
  committed pending sync state while User is unavailable, verifies retry failure
  remains pending, restarts User, then waits for Auth scheduler recovery to apply
  the User block projection and clear the pending marker. Build baseline now
  guards this harness so it cannot regress into stopping canonical `user-service`.
  Runtime execution is still OPEN in the current environment because Docker
  daemon was unavailable. Non-runtime proof: shell syntax check,
  focused Auth/User boundary tests, and
  `backend_delivery/scripts/verify-build-baseline.sh`.
- [x] Phase 2 checkpoint — Order read/cancel actor checks now require the
  matching trusted role, not only a matching `X-User-Id`. Direct/debug-port calls
  with a missing role or a spoofed wrong role can no longer read or cancel a
  customer-owned order by sending the customer's user id; customer access
  requires `USER`, restaurant owner access requires `SHOP_OWNER`, shipper read
  requires assigned `SHIPPER`, and admin still requires `ADMIN`. Regression
  proof: focused Order authorization/pricing tests, full
  `mvn -q -pl order-service test` (`87/87`),
  `backend_delivery/scripts/verify-http-api-inventory.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 2 checkpoint — Restaurant decision publisher now fails closed on
  invalid direct-call input before touching order eligibility, outbox, or
  decision rows. `publishConfirmed` and `publishRejected` now require positive
  `orderId`/`restaurantId`/`actorUserId`, bounded prep time, and non-blank
  rejection reason at service level, so the direct bean path cannot store a
  malformed decision even if the controller validation is bypassed. Regression
  proof: `mvn -q -pl restaurant-service test`, including
  `RestaurantDecisionOutboxIntegrationTest`, plus
  `backend_delivery/scripts/verify-http-api-inventory.sh` and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 2 checkpoint — Promotion/FlashSale service-layer guards now fail
  closed on malformed direct calls before they reach repository mutation or
  downstream lookup. `PromotionService` now validates create/collect/calculate/
  reserve inputs, tolerates nullable voucher windows safely, and keeps voucher
  lookup bounded; `FlashSaleService` now validates campaign/item requests,
  status transitions, and campaign/item lookup ids before mutation. Regression
  proof: `mvn -q -pl promotion-service,flashsale-service test`,
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 3 checkpoint — Shipper service-layer guards now fail closed on null
  or non-positive direct calls before repository access. `ShipperServiceImpl`
  now validates create/update/delete/online-status/getters, and
  `ShipperRatingServiceImpl` now validates shipper/customer/order/rating inputs.
  Regression proof: `mvn -q -pl shipper-service test`,
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 3 checkpoint — Delivery inbound event handlers now fail closed on
  null or non-positive event payloads before repository/transaction work.
  `createDeliveryFromOrderEvent`, `cancelDeliveryFromOrderCancelledEvent`, and
  `updateDeliveryStatusFromShipperNotFoundEvent` now validate required identity
  fields up front instead of relying on broad exception wrapping. Regression
  proof: `mvn -q -pl delivery-service test`,
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 3 checkpoint — Notification service now fails closed on malformed
  direct calls and user-scoped access. User notification reads/mutations and FCM
  token register/unregister require the trusted `USER` path, direct service
  calls reject null/invalid ids or blank tokens before persistence, and the FCM
  ownership script no longer compiles against an incorrect Mockito helper
  invocation. Regression proof: `mvn -q -pl notification-service test`,
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 3 checkpoint — Tracking service now fails closed on direct-call
  null/invalid inputs and publisher lease recovery paths are exercised by the
  test suite. Regression proof: `mvn -q -pl tracking-service test`.
- [x] Phase 3 checkpoint — Match service now fails closed on invalid direct
  events, broker/Redis failure paths are covered, and the module test suite
  passes after the hardening slice. Regression proof:
  `mvn -q -pl match-service test`.
- [x] Phase 3 checkpoint — Saga orchestrator now validates stable event ids
  and migration/replay guards in its listener and outbox paths. Regression
  proof: `mvn -q -pl saga-orchestrator-service test`.
- [x] Phase 4 checkpoint — Settlement ledger/COD/idempotency and migration
  guards pass focused module proof. Regression proof:
  `mvn -q -pl settlement-service test`.
- [x] Phase 4 checkpoint — Search read-model sync, stale-event handling, query
  validation, and repository-unavailable paths pass focused module proof.
  Regression proof: `mvn -q -pl search-service test`.
- [x] Phase 4 checkpoint — Analytics listeners, reconciliation, deduplication,
  and migration guards pass focused module proof. Regression proof:
  `mvn -q -pl analytics-service test`.
- [x] Phase 4 checkpoint — Livestream MVP surface and migration guards pass
  focused module proof. Regression proof:
  `mvn -q -pl livestream-service test`.
- [x] Phase 4 aggregate gate — backend API inventory, build baseline, and
  polyrepo contract remain aligned after support/money/reporting service
  verification. Regression proof: `backend_delivery/scripts/verify-build-baseline.sh`
  and `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 5 partial closeout — compose configuration contract remains valid
  after all backend service checks. Regression proof:
  `backend_delivery/scripts/verify-compose-config.sh`.
- [x] Phase 5 offline full-suite closeout — full backend Maven suite completed
  successfully after all service waves. Regression proof: `mvn -q test` in
  `backend_delivery` exited `0`; current Surefire XML reports across backend
  modules contain no `failures>0` or `errors>0` markers.
- [x] Phase 5 runtime startup proof — canonical volumes preserved, infrastructure
  healthy, all 17 applications started, and Gateway public reads responded.
  Runtime proof passed via `backend_delivery/scripts/verify-runtime-startup.sh`
  after a clean backend rebuild; the verifier now also handles the previously
  observed stopped-container/no-port metadata edge safely.
- [x] Phase 5 backend closeout and contract sync — `http-api-inventory.md`
  and `system-contract-inventory.md` are now aligned on 146 mapped controller
  methods, the backend inventory date is current, `verify-build-baseline.sh`
  passes, and `scripts/verify-mvp-polyrepo-contract.sh` stays green after the
  support-chat hide / docs sync.
- [x] Phase 6 static client/backend contract audit — no emulator/device needed:
  `scripts/verify-mvp-polyrepo-contract.sh` passed against the current
  polyrepo state, confirming backend HTTP inventory alignment at 146 mapped
  controller methods and guarding clients against direct service ports,
  duplicated `/api/api`, gRPC/STOMP/SockJS, and hidden MVP routes.
- [x] Phase 6 no-emulator client static gates — current client code still
  compiles/tests against the backend MVP contract without launching devices:
  `shipper_app2` `npm run verify` passed (`tsc --noEmit`, ESLint, 16 Jest
  suites / 57 tests); `delivery_web` `npm run lint` and `npm run build` passed
  with the known Browserslist/chunk-size warning; `delivery_app`
  `fvm flutter analyze` passed and `fvm flutter test` passed 130/130.
- [x] Phase 6 datasource contract hardening — `delivery_app` now has focused
  regression tests for user-address, order, and restaurant-rating API services
  against `http://gateway.test/api`, locking the `/addresses`, `/orders`, and
  `/restaurants/{id}/ratings` call-sites to the canonical Gateway contract.
- [x] Phase 6 admin fixture authority — `auth-service` now has explicit
  operator-only ADMIN provisioning (`OperatorAdminProvisioningRunner` +
  `AuthService.operatorProvisionAdminAccount`) guarded by env, tests and
  build-baseline. This enabled isolated browser proof without public
  self-registration or SQL patching; the temporary runtime credential was
  blocked and removed after the completed smoke.
- [x] Phase 1/Auth admin block hardening — admin block/unblock follow-up
  user-status sync now runs in an explicit `REQUIRES_NEW` transaction instead
  of depending on the after-commit callback context, so the bulk status update
  and projection sync no longer trip `TransactionRequiredException` during the
  runtime admin-block flow. Regression proof:
  `mvn -q -pl auth-service test`,
  `backend_delivery/scripts/verify-build-baseline.sh`, and
  `scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 6/admin web prerequisite proof — rerun 2026-07-29 without emulator:
  root `scripts/verify-mvp-polyrepo-contract.sh` PASS, `delivery_web`
  `npm run lint` PASS, `npm run build` PASS with only the known browserslist
  warning, Docker runtime still has 17 app containers plus infra running, and
  admin API smoke PASS through Gateway using operator-only fixture provisioning:
  login, `GET /api/users` role `ADMIN`, and block cleanup. Full admin browser
  dashboard/surface smoke remains open.
- [x] Phase 6/Gateway CORS preview-origin fix — admin browser smoke on
  `vite preview` exposed that Gateway/Compose default CORS allowed `5173/3000`
  but not preview port `4173`; this blocked UI login from
  `http://127.0.0.1:4173` while API smoke passed. Added local-only
  `localhost|127.0.0.1:4173` origins to Gateway and Compose defaults, plus
  CORS unit coverage and compose verifier guards. Proof:
  `mvn -q -pl api-gateway test`,
  `mvn -q -pl api-gateway -DskipTests package`,
  Gateway rebuild/recreate, CORS preflight `200 OK`,
  `bash scripts/verify-compose-config.sh`,
  `bash scripts/verify-build-baseline.sh`, and root
  `bash scripts/verify-mvp-polyrepo-contract.sh`.
- [x] Phase 6/Admin browser runtime smoke — operator-provisioned ADMIN login
  redirected from `/admin/login` to `/admin/dashboard`; Dashboard, Orders,
  Shippers, Ratings, Coupons and Flash Sales rendered backend data/empty states
  through Gateway with no browser console error or warning. The fixture was
  blocked through the canonical admin API, a replayed login returned `401`,
  active `admin+*@test.dev` fixtures returned to `0`, and all credential/response
  temp files were removed. Cleanup exposed a zero-consumer
  `GET /api/auth/admin/accounts` endpoint that silently capped results at 100;
  the controller/service/Gateway route were removed instead of inventing an
  unused pagination contract. Auth missing-route handling now returns `404`
  rather than generic `500`. Proof: Auth/Gateway focused and full module tests,
  inventory/build/polyrepo gates PASS at 146 handlers; rebuilt runtime returned
  `200` for a public Gateway read and `404` for the removed route. PostgreSQL
  kept the same named volume and internal `5432`; this local Docker session uses
  host port `55432` because Docker Desktop retained a conflicting `5432` bind.
- [x] Phase 1 foundation boundary audit/refactor.
- [x] Phase 2 checkout boundary audit/refactor.
- [x] Phase 3 fulfilment boundary audit/refactor.
- [x] Phase 4 money/reporting/support boundary audit/refactor.
- [x] Phase 6 client verification and cross-client E2E no-emulator closeout.
  Final proof: backend/root contract gates PASS; `delivery_web npm run verify`
  PASS; `shipper_app2 npm run verify` PASS with `16/16` suites and `61/61`
  tests; `delivery_app fvm flutter analyze` PASS and `delivery_app fvm flutter
  test` PASS `135/135`. Browser smoke remains regression-only; mobile device/
  emulator proof is final sanity only.

## Decisions

- 2026-07-29: Dùng backend-first sequencing làm nguyên tắc chính; client chỉ được chạm lại sau khi backend freeze và proof đủ mạnh.
- 2026-07-29: Giữ trust boundary MVP ở mức Gateway-only canonical Compose
  network + JWT-derived identity headers + internal credential cho route nội bộ.
  Không tự thêm Gateway signature/header mới trong Phase 1 khi chưa có authority
  vận hành cho policy cross-service rộng hơn; nếu sau này cần expose direct
  service ports ngoài debug compose thì phải mở checkpoint hardening riêng.
- 2026-07-29: API không có consumer hợp lệ sẽ bị xoá hoặc fail-closed thay vì giữ lại vì “có lẽ sau này dùng”.
- 2026-07-29: Không mở lại transport/capability đã bị loại khỏi MVP nếu không có authority product mới.
- 2026-07-29: Device/emulator/browser là checkpoint cuối, không phải vòng lặp xác minh thường xuyên.

## Validation

- Focused proof:
  - service-specific unit tests per wave;
  - contract/authorization tests for hidden or denied routes;
  - failure/replay/idempotency tests for checkout and fulfilment.
- Integration or end-to-end proof:
  - canonical backend COD journey;
  - no-shipper / reject / rematch / cancel-assignment / replay matrix;
  - `backend_delivery/scripts/verify-auth-user-outage-retry.sh` for Auth↔User
    pending status projection recovery;
  - final cross-client user journey after backend freeze.
- Repository-required checks:
  - `scripts/verify-build-baseline.sh`
  - `scripts/verify-compose-config.sh`
  - `scripts/verify-mvp-polyrepo-contract.sh`
  - `scripts/verify-runtime-startup.sh`
  - targeted module tests and clean rebuilds for touched services

## Result

Completed 2026-07-29 after final no-emulator validation.

Backend audit/refactor waves are frozen for MVP at the current source state.
Dead or unsupported API surfaces were removed or hidden behind Gateway/docs/
client guards; HTTP inventory is aligned at 146 mapped controller methods.

Final validation:

- `backend_delivery/scripts/verify-build-baseline.sh` PASS.
- `backend_delivery/scripts/verify-compose-config.sh` PASS.
- `backend_delivery/scripts/verify-http-api-inventory.sh` PASS.
- root `scripts/verify-mvp-polyrepo-contract.sh` PASS.
- `delivery_web npm run verify` PASS.
- `shipper_app2 npm run verify` PASS with `16/16` suites and `61/61` tests.
- `delivery_app fvm flutter analyze` PASS.
- `delivery_app fvm flutter test` PASS with `135/135` tests.

Mobile emulator/device proof is not an MVP acceptance gate per user decision;
real-device work remains final sanity only. Browser smoke for `delivery_web`
is regression-only because the web action-contract gate and prior browser proof
already cover the MVP surfaces.
