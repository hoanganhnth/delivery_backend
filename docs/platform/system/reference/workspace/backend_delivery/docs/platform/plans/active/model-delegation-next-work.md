# Execution Plan: Công việc sau MVP để giao model nhỏ

Date: 2026-08-01

## Status

Active

## Outcome

Chỉ giao cho model nhỏ những lát cắt độc lập, có nguồn sự thật, ràng buộc và
tiêu chí pass/fail rõ ràng. Không làm lại các phase production đã hoàn thành,
không cần VM/Docker/emulator/device, và không vô tình bật capability chưa được
phê duyệt.

## Context

- Quy trình: [docs/WORKFLOW.md](../../WORKFLOW.md) và [AGENTS.md](../../../../../AGENTS.md).
- Luồng MVP và ranh giới transport: [product overview](../../product/overview.md)
  và [architecture](../../ARCHITECTURE.md). Tracking chỉ dùng raw WebSocket;
  gRPC/STOMP không thuộc scope.
- Trạng thái code hiện hành: `backend_delivery/main` và `delivery_app/main` đã
  đồng bộ `origin/main`; Web hơn 3 commit và Shipper hơn 2 commit. Không push
  Web/Shipper nếu chưa có uỷ quyền mới.
- Remediation đã đóng: [production-readiness-remediation.md](../completed/production-readiness-remediation.md).
- Bằng chứng phase đã hoàn tất:
  - [Actuator/health/readiness](../completed/backend-actuator-health-readiness.md),
    [metrics/Prometheus](../completed/production-metrics-prometheus.md) và
    [distributed tracing](../completed/distributed-order-tracing.md).
  - [resilience](../../../plans/completed/phase-2-resilience.md),
    [operations/deployment](../completed/phase-3-operations-deployment.md),
    [data/scale](../../../plans/completed/phase-4-data-scale.md).
  - [password reset + email verification](../../../plans/completed/task-18-password-reset-email-verification.md),
    [refresh-token rotation](../completed/task-19-refresh-token-rotation.md),
    [voucher/flash-sale checkout](../completed/task-21-voucher-flashsale-checkout.md)
    và [FCM native wake-up](../completed/task-22-fcm-native-wakeup.md).

## Locked Constraints

Áp dụng cho mọi task dưới đây. Prompt giao model phải lặp lại phần này.

1. Không chạy Docker Compose, VM, Testcontainers, emulator/device, browser
   manual, hay provider thật. Chỉ dùng unit/integration chạy in-process, static
   analysis, build/test repository và HTTP server nội bộ do test tự khởi tạo.
2. Không thêm gRPC, STOMP hay SockJS. Vị trí realtime vẫn là raw WebSocket qua
   Gateway.
3. Gateway là public boundary duy nhất. Không thêm direct service port hoặc route
   ẩn ra client.
4. Public registration chỉ có `USER` và `SHOP_OWNER`; `ADMIN`/`SHIPPER` không
   được public self-register.
5. Các cờ voucher/flash-sale checkout mặc định tiếp tục `false`; không bật
   payment, livestream, analytics hay capability hidden khác.
6. Không commit credential, token, Firebase/Apple/VNPay secret hay file signing.
7. Không `git reset`, `git checkout`, rebase, force-push, `git add -A` hoặc sửa
   file thuộc task khác. Stage path cụ thể; mỗi task một commit; không tự push
   trừ khi prompt ghi rõ.
8. Nếu cần một lựa chọn nghiệp vụ hoặc credential thật, dừng trước code và báo
   đúng câu hỏi/impact; không tự chọn default.

## Verified Baseline — Không giao làm lại

| Mảng | Trạng thái thực tế | Bằng chứng chính |
|---|---|---|
| Observability | Đã có Actuator private, liveness/readiness, Prometheus/Grafana, correlation ID và OTLP tracing | 3 completed plans ở Context |
| Resilience | Đã có Gateway rate limit, circuit breakers, Kafka retry/DLT và core-consumer idempotency | `phase-2-resilience.md` |
| Operations | Eureka, Config Server, secret-injection contract, CI và rollout/rollback runbook đã có | `phase-3-operations-deployment.md` |
| Data/scale | Backup/restore rehearsal, hot indexes, WebSocket fan-out/backpressure, location history đã có | `phase-4-data-scale.md` |
| Auth/push/checkout | Reset/verify email, token rotation, FCM native wake-only, voucher/flash-sale state machine đã hoàn tất | Task 18/19/21/22 plans |

`backend_delivery/ROADMAP_MVP_TO_PRODUCTION.md` và `docs/FEATURE_STATUS.md`
vẫn có các dòng lịch sử chưa được đồng bộ; chúng không được dùng một mình để
kết luận một phase chưa làm.

## Dependency Map

```text
T0 remediation/push (completed)
 └─ T1 documentation reconciliation
     ├─ T2 hermetic backend test contexts
     └─ T3 web action-contract matrix
         ├─ T4a Flutter action parity
         └─ T4b Shipper action parity

T5 refund/payment discovery (policy approved for T6)
 └─ T6 automatic-refund boundary (implemented; provider remains gated)
     └─ T7 online-payment activation (blocked until provider/deployment authority)

T8 external production operator checklist (human/operator; not a coding task)
```

Chạy tuần tự trong cùng worktree. T2 và T3 có thể song song **chỉ** khi mỗi
agent có worktree riêng; T4a/T4b chỉ bắt đầu sau T3 vì Web là action-contract
reference theo quyết định hiện tại.

## Task Cards

### T0 — Completed: close validated remediation and publish approved repositories

**Size:** S · **Owner:** model trung bình/cẩn thận · **Repositories:** root
docs, `backend_delivery`, `delivery_app`.

**Result:** Hoàn tất ngày 2026-08-01. Backend đã push `33b1e4b..37d3c01`; Customer
App đã push `4a431a1..8f061f3`. Không cần giao lại task này cho model nhỏ.

**Read first:**

- `docs/plans/completed/production-readiness-remediation.md`
- `backend_delivery` log từ `317b69a` đến `37d3c01`
- `delivery_app` log từ `4a431a1` đến `8f061f3`

**Do:**

1. Chạy lại hoặc xác nhận reactor no-runtime bằng report Surefire. Bản kiểm tra
   đầy đủ loại ba test tự mở HTTP management port:
   ```sh
   mvn -q -Dtest='!ActuatorProbeEndpointTest,!MatchReadinessDependencyTest,!PrometheusEndpointIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test
   ```
2. Kiểm tra report mới không có `failure`/`error`, rồi chạy `git diff --check`
   và `git status -sb` cho cả bốn repo.
3. Cập nhật plan remediation: mark focused/regression/polyrepo gates complete;
   ghi commit `36f539c`, `8f061f3`, `b3e0efc`, `37d3c01`; ghi rõ Docker/
   provider/device proof bị hoãn theo policy, không phải pass giả.
4. Chỉ sau khi các bước trên pass, push **chỉ** `backend_delivery/main` và
   `delivery_app/main`. Không push `delivery_web` hoặc `shipper_app2`.

**Do not:** sửa production code, rerun Compose, đổi checkout flags, bundle
commit mới không thuộc remediation.

**Acceptance:** Maven exit 0; Surefire report không failure/error; four-repo
diff hygiene sạch; plan cập nhật; hai remote branch được kiểm tra sau push.

**Suggested commit:** không tạo commit code mới; chỉ dùng các commit đã có.

### T1 — Reconcile stale status documentation

**Size:** S · **Owner:** model nhỏ · **Repository:** root docs và backend docs.

**Goal:** Tài liệu status không còn bảo Observability/Resilience/Operations/
Data-scale “chưa có” khi completed plans và source đã chứng minh chúng tồn tại.

**Read first:** completed plans trong phần Context, `docs/FEATURE_STATUS.md`,
`backend_delivery/ROADMAP_MVP_TO_PRODUCTION.md`, `docs/product/overview.md`.

**Do:**

1. Thêm section “verified implementation status, 2026-08-01” thay vì xoá lịch
   sử roadmap. Mỗi item phải link sang completed plan hoặc command evidence.
2. Cập nhật `FEATURE_STATUS.md` chỉ ở những dòng bị stale rõ ràng: không gọi
   Saga “sơ khai”, Match “block consumer”, hoặc toàn bộ production foundation
   “chưa có” khi chúng trái source hiện hành.
3. Giữ capability payment/VNPay, analytics, livestream và checkout ở trạng thái
   `hidden/disabled-until-approved` nếu source/contract inventory nói như vậy.
4. Bổ sung “Remaining, decision-gated” với: automatic refund, provider payment
   activation, shipper availability policy, analytics item pipeline,
   notification preferences/livestream chat. Không đánh dấu chúng là implemented.

**Do not:** chạm Java/Flutter/TypeScript; sửa commit history; suy diễn runtime
provider/device proof từ unit test; tự move active plan sang completed.

**Acceptance:** mọi claim mới truy được đến source/plan; Markdown links tồn tại;
`rg` không còn exact stale assertion trong section current-status; root
`scripts/verify-mvp-polyrepo-contract.sh` pass.

**Suggested commit:** `docs(status): reconcile verified post-MVP capabilities`.

### T2 — Make no-runtime backend tests hermetic

**Size:** M · **Owner:** model nhỏ có kinh nghiệm Spring Boot ·
**Repository:** `backend_delivery` only.

**Goal:** Test context không cố gọi DNS/HTTP tới `config-server`, Eureka, Kafka,
Redis hay provider ngoài process khi test đó không hề là integration test của
dependency đó. Đây tăng độ tin cậy khi không chạy VM, không đổi topology production.

**Read first:**

- `backend_delivery/AGENTS.md`, root workflow
- `src/test/resources` của 17 service, các `*ApplicationTests`, và config test
  mới của `shipper-service`
- `config-server`, `discovery-server`, `docker-compose.yml` chỉ để phân biệt
  runtime config với test config

**Do:**

1. Lập inventory: mỗi Spring context test đang dùng config file/profile nào,
   service external nào có thể bị boot client gọi ra ngoài, và test nào thật sự
   cần stub/in-process dependency.
2. Chỉ thêm test-scoped configuration hoặc annotation/profile cho từng module
   cần thiết. Tắt Config Client/Eureka/discovery trong test khi test không có
   mục tiêu kiểm thử chúng; giữ H2/Flyway behavior theo test hiện có.
3. Nếu có test cần HTTP management server cục bộ, để test đó explicit và giữ nó
   ngoài full no-runtime reactor như T0. Không disable production actuator,
   config/discovery hay Kafka retry/DLT.
4. Thêm regression assertion tối thiểu cho config isolation thay vì dựa vào log.

**Do not:** thay `src/main/resources` để test pass; convert tất cả test thành
mock; giảm assertion nghiệp vụ; add Docker/Testcontainers; suppress test failures.

**Acceptance:**

- module focused tests pass cho mỗi module đã đổi;
- full no-runtime Maven reactor ở T0 pass;
- report mới zero failure/error;
- test output không còn attempts đến `http://config-server:8888` cho module đã
  được cô lập;
- `bash scripts/verify-build-baseline.sh` và `git diff --check` pass.

**Suggested commit:** `test(backend): isolate no-runtime service test contexts`.

**Verified implementation checkpoint (2026-08-01):** Added test-only bootstrap
files for all 17 application modules, CI isolation verifier, and the missing
Settlement listener auto-startup seam. The Settlement production default remains
`true`; tests set it to `false` and use an empty Kafka bootstrap value, so no
Kafka endpoint is contacted. Full reactor evidence:

```text
mvn -q -Dspring.main.banner-mode=off \
  -Dtest='!ActuatorProbeEndpointTest,!MatchReadinessDependencyTest,!PrometheusEndpointIntegrationTest,!RedisFixedWindowRateLimitStoreIntegrationTest,!VoucherReservationPostgresConcurrencyTest,!FlashSaleReservationPostgresConcurrencyTest,!MatchRedisOfferIntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test  # exit 0
bash scripts/verify-test-context-isolation.sh                         # exit 0
bash scripts/verify-build-baseline.sh                                 # exit 0
bash scripts/verify-compose-config.sh                                 # exit 0
bash scripts/verify-http-api-inventory.sh                              # exit 0
git diff --check                                                       # exit 0
```

Commit `4cb3b05` (`test(backend): isolate no-runtime service test contexts`)
was pushed to `backend_delivery/main` on 2026-08-01. Web/Shipper branches were
not pushed.

### T3 — Make Web the explicit action-contract reference

**Size:** M · **Owner:** model nhỏ · **Repository:** `delivery_web` plus one
root documentation file if necessary.

**Goal:** Mỗi action Web đang hiển thị có mapping chứng minh được tới canonical
Gateway endpoint, actor role và expected success/error envelope. Đây là reference
để hai mobile clients follow, không cần browser/VM.

**Read first:**

- `delivery_web/scripts/verify-action-contracts.mjs`, tests, router/nav
- `backend_delivery/docs/http-api-inventory.md`, Gateway route config
- `docs/product/overview.md` và `docs/plans/completed/mvp-client-alignment.md`

**Do:**

1. Tạo một matrix ngắn (file docs gần test hoặc root docs) cho actions visible:
   login/logout, owner restaurant/menu/order confirm/reject, admin order/user/
   rating/coupon/flash-sale reads/mutations, pagination/error states.
2. Với mỗi action, ghi role, method/path canonical, request owner, response
   envelope và test file hiện có. Khi thiếu proof, thêm test action-contract
   nhỏ nhất; ưu tiên adapter/parser tests, không mock toàn UI tree vô ích.
3. Confirm exact exclusions: Chat/Firebase graph, STOMP/SockJS, direct service
   port, `/api/api`, hidden payment and hidden checkout capability không được
   quay lại Web MVP.

**Do not:** thêm feature mới; enable payment/checkout; thêm Firebase; test qua
browser thật; đổi backend contract để hợp với UI cũ.

**Acceptance:** `npm run verify` pass; matrix không có action visible chưa có
endpoint/role/proof; `scripts/verify-mvp-polyrepo-contract.sh` pass; one focused
commit only.

**Suggested commit:** `test(web): document and cover canonical action contracts`.

**Verified implementation checkpoint (2026-08-01):** Added
`delivery_web/docs/action-contract-matrix.md` covering 32 visible HTTP actions
(auth/session, restaurant-owner and admin surfaces), UI-only controls, response
envelopes, error/retry behavior and explicit MVP exclusions. Extended
`scripts/verify-action-contracts.mjs` to require the matrix rows, contract
columns and existing proof paths. Web `npm run verify` passed (8 test files,
39 tests, action verifier and production build); the root
`scripts/verify-mvp-polyrepo-contract.sh` also passed. Commit `956ae46` was
created locally on `delivery_web/main`; it has not been pushed.

### T4a — Align Flutter action contracts to the Web reference

**Size:** M · **Owner:** model nhỏ · **Repository:** `delivery_app` only.

**Precondition:** T3 merged/available. **Goal:** Customer app calls the same
canonical Gateway contract and makes user-visible failures deterministic without
emulator/device.

**Read first:** T3 matrix, generated API datasource/repository tests, auth
registration handoff docs, checkout capability tests.

**Do:**

1. Compare implemented customer actions (auth/register, catalog/cart/COD
checkout preview/create/cancel, order tracking/notification refresh, profile/
addresses) against matrix and backend inventory.
2. Add focused fake-adapter/use-case/notifier tests only for real uncovered
actions or status/error mappings. Preserve two-step registration and rotated
refresh-token contracts.
3. Keep voucher/flash-sale capability gating default off and raw WebSocket
tracking semantics unchanged.

**Do not:** run Flutter app/emulator; invent client-side totals; change generated
files manually when the generator owns them; alter server routes.

**Acceptance:** `fvm flutter analyze` and `fvm flutter test` pass; contract test
does not use external network; root polyrepo contract pass; one focused commit.

**Suggested commit:** `test(app): align customer actions with gateway contract`.

**Verified implementation checkpoint (2026-08-01):** Customer App audit found
one real contract mismatch in the legacy restaurant search adapter: the backend
expects `GET /api/restaurants/search?keyword=...`, while the DTO emitted
`query`. The DTO was corrected through the generator-owned Freezed output, and
HTTP adapter proof was added for auth/session handoff, restaurant catalog/detail/
menu/search, profile GET, notification actions and delivery lookup. Focused
contract tests passed; `fvm flutter analyze` passed; full `fvm flutter test`
passed with 225 tests; the polyrepo MVP contract gate passed. Commit `e17c360`
was created locally on `delivery_app/main` and has not been pushed.

### T4b — Align Shipper action contracts to the Web reference

**Size:** M · **Owner:** model nhỏ · **Repository:** `shipper_app2` only.

**Precondition:** T3 merged/available. **Goal:** Shipper flows retain canonical
offer recovery and delivery lifecycle semantics when no push/device is present.

**Read first:** T3 matrix, `docs/product/overview.md`, Task 22 plan, shipper
delivery/session/push tests, Gateway inventory.

**Do:**

1. Audit login/session refresh/logout, current-offer recovery, accept/reject,
   assignment cancellation, status transitions, location wake/reconnect and
   earnings reads against canonical role/path/error mapping.
2. Add narrow reducer/service tests for actual uncovered actions. Verify FCM is
   wake-only: it must fetch `/api/deliveries/offers/current`, not mutate delivery
   state from a push payload.
3. Keep polling/startup/foreground recovery intact when FCM token/permission is
   unavailable.

**Do not:** run emulator/device; add gRPC/STOMP; require Firebase credential;
enable hidden admin/payment routes; modify customer/web repositories.

**Acceptance:** `npm run verify` pass; action matrix has proof for every visible
shipper mutation; root polyrepo contract pass; one focused commit.

**Suggested commit:** `test(shipper): align lifecycle action contracts`.

**Verified implementation checkpoint (2026-08-01):** Added action-contract proof
for shipper auth/session, active-delivery recovery, profile/status/documents,
ratings, notification mutations and positive-ID validation. Existing proof also
covers offer recovery, accept/reject/cancel, delivery lifecycle, FCM wake-only,
polling/foreground recovery, raw WebSocket handshake/reconnect and token-race
behavior. `npm run verify` passed (33 suites, 124 tests) and the root polyrepo
contract gate passed. Commit `f1ad58e` is local on `shipper_app2/main`; it has
not been pushed.

### T5 — Refund and online-payment discovery packet (no implementation)

**Size:** S · **Owner:** model nhỏ/research · **Repository:** docs only.

**Goal:** Convert the only material financial gap — automatic refund after
cancel — into a decision request that is precise enough for a stronger model to
implement safely. `Transaction` enums and admin reversal are building blocks;
they do not authorize an automatic money flow.

**Read first:**

- `docs/FEATURE_STATUS.md` refund note
- `docs/product/features/settlement.md`
- `backend_delivery/docs/workflows/settlement_finance_flow.md`
- `settlement-service` payment/ledger entities and `order.cancelled`/
  `delivery.completed` event flows
- Task 21 policy (voucher/flash-sale monetary snapshot)

**Deliver:** a draft under `docs/plans/active/` (not a `docs/decisions/` final
decision) with exact answers the product owner must choose:

1. Which terminal statuses trigger a refund; does cancellation after pickup or
   delivered dispute qualify?
2. Who authorizes it: automatic rule, restaurant, customer, admin, or provider?
3. COD policy: release hold, collect/reverse COD, wallet credit, or no refund?
4. Online policy: provider API refund, wallet credit, partial/full amount,
   shipping fee, voucher/flash-sale discount and platform/restaurant split.
5. Idempotency identity, audit fields, event name/version, retry/DLT and manual
   reconciliation path.
6. Customer/admin notification and UI visibility requirements.

**Do not:** write migration/domain code; turn on VNPAY/payment routes; call a
provider; choose monetary rules; change checkout flags.

**Acceptance:** all proposed choices cite current source ownership; unresolved
questions are explicit; no product behavior changes; Markdown links valid.

**Suggested commit:** `docs(finance): prepare automatic-refund decision packet`.

**Verified implementation checkpoint (2026-08-01):** Created
[`refund-payment-decision-packet.md`](./refund-payment-decision-packet.md) from
the current Settlement, Order, payment and Task 21 authorities. It records
source facts, the current cancellation/payment boundaries, COD/online refund
questions, monetary allocation, state/idempotency, retry/DLQ, audit,
notification and rollback decisions. No production code, migration, route,
secret or feature flag changed. Relative-link validation and the root polyrepo
  contract gate passed. The user approved the conservative MVP policy in the
  decision packet on 2026-08-02; this opens T6 only. T7 still requires provider
  and operator authority.

### T6 — Automatic refund workflow (in progress; provider remains gated)

**Size:** L · **Owner:** stronger model + review · **Repositories:** Backend
first, then affected clients.

**Policy gate satisfied for the conservative MVP scope.** Implementation is split
into separate commits: (a) settlement state/migration/idempotency/outbox,
(b) Order/Delivery/Saga cancellation snapshot and compensation boundary, (c)
Gateway/admin read-only visibility. Customer money UI, admin mutation and
provider execution are not part of this phase.
Every step needs an executable test matrix for duplicate, conflicting replay,
cancel race, provider failure, manual reconciliation, and exact monetary
components. No real provider call is permitted until T7 authority exists.

**Progress checkpoint (2026-08-02):** Commits A `3ce5e54`, B `1379b19`, C
`49b489f` and inventory alignment `f529bff` delivered the refund case state,
canonical snapshot, feature-gated consumer, read-only admin projection/API and
Gateway ADMIN route/security proof. The follow-up implementation adds typed
cancellation source/reason and a separate `order.refund-eligible` trigger for
the terminal no-shipper path; Promotion/Flash-sale release that trigger without
changing `SHIPPER_NOT_FOUND` into `CANCELLED`. Provider/admin mutation/customer
money UI remain gated.

**Customer visibility checkpoint (2026-08-02):** Settlement now exposes the
provider-neutral, read-only `GET /api/settlement/refunds/my` route for `USER`.
It scopes cases by trusted customer identity and exposes only status-safe fields;
it does not create a dispute, approve/execute money movement, or expose provider
or audit internals. Gateway allows only this GET route, while all refund
mutations remain hidden. Flutter UI was a separate, contract-following task.

**Flutter visibility checkpoint (2026-08-02):** `delivery_app` now calls the
customer read route with bounded `limit=50`, shows a non-mutating refund-status
card in Order Detail and provides a separate `/refunds` history screen that
links back to the order. Loading, empty, retry and narrow-layout behavior are
covered by no-runtime tests. The UI never creates a dispute, approves a case or
calls a payment provider. Commit `bc008cc` (`feat(app): show customer refund
status history`) is pushed to `delivery_app/main`.

### T7 — Online payment provider activation (blocked by external authority)

**Size:** L · **Owner:** stronger model + operator.

Existing VNPay code is intentionally hidden/default-off. Before any implementation
or route enablement, user/operator must provide provider choice, sandbox versus
production environment, callback origin, credential secret store, accepted
status/retry/reconciliation behavior, and rollback owner. A model may add
offline contract tests only after those choices; it must never request or commit
the actual secret.

### T8 — External deployment prerequisites (not a coding task)

**Owner:** human/operator. Repo work already supplies Compose topology, CI,
runbooks, backup scripts, health checks and secret-injection contracts. Remaining
external proof needs a chosen cloud/KMS/secret manager, real backup bucket and
retention policy, alert receiver/on-call, provider credentials, and an approved
staging environment. This stays outside the no-VM acceptance policy.

## Handoff Protocol for Every Model

1. Read the task card and named authority files before editing.
2. State the exact files it will touch. If scope expands, stop and report.
3. Make the smallest coherent change, add/adjust focused tests, run only the
   task acceptance commands, then `git diff --check`.
4. Return: changed files, command output/exit result, remaining risks, and one
   suggested conventional commit. Do not push unless T0 explicitly says so.
5. Never claim real runtime/provider/device behavior from no-runtime tests.

## Progress

- [x] Audit which formerly proposed production tasks are already completed.
- [x] Record constraints and a dependency-safe delegation order.
- [x] T0 close/push the current remediation.
- [x] T1 reconcile stale documentation.
- [x] T2 hermetic no-runtime backend test contexts.
- [x] T3 Web action-contract reference.
- [x] T4a Flutter action parity against the reference.
- [x] T4b Shipper action parity against the reference.
- [x] T5 refund/payment decision packet (conservative MVP policy approved).
- [x] T6 automatic refund boundary (state/snapshot/source-reason/no-shipper trigger/read-only visibility; provider/admin mutation remain off).
- [x] Client clean-checkout configuration hardening (2026-08-02): customer CI
  now prepares a non-secret dotenv asset, Firebase Android Gradle plugins are
  conditional on injected native config, API base injection is documented, and
  shipper iOS location permission text is non-empty. Static checks, Flutter
  analyze/tests/debug build, clean-native-config Gradle build, Web verify,
  Shipper verify and the polyrepo contract gate passed; no credential was
  committed and no emulator/device was used. The validated client commits were
  subsequently pushed to their `main` branches.
- [x] Customer Mapbox credential hygiene (2026-08-02): removed the tracked
  Android token, wired the manifest placeholder to Gradle property/environment,
  documented the operator injection path, and proved debug builds with and
  without the token. Commit `0784174` is pushed to `delivery_app/main`.
- [x] Authority diagram/use-case reconciliation (2026-08-02): corrected current
  service ports and production follow-up labels in `docs/ARCHITECTURE.md`, and
  marked the legacy backend use-case snapshot so online payment is not claimed
  as active. Backend docs commit `b02bd53` is pushed to `backend_delivery/main`;
  authority-link and contract validation passed.
- [ ] T7 online payment provider activation (blocked by provider/operator authority).

## Validation

For every completed coding task, retain command output or CI evidence. The
minimum no-runtime final gate is:

```sh
# backend_delivery
mvn -q -Dtest='!ActuatorProbeEndpointTest,!MatchReadinessDependencyTest,!PrometheusEndpointIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false test
bash scripts/verify-build-baseline.sh
bash scripts/verify-compose-config.sh
bash scripts/verify-http-api-inventory.sh

# delivery_web
npm run verify

# delivery_app
fvm flutter analyze
fvm flutter test

# shipper_app2
npm run verify

# workspace root
bash scripts/verify-mvp-polyrepo-contract.sh
```

These are code/test/configuration proofs only. They neither replace nor claim
Docker, cloud, provider, browser, emulator, or real-device proof.

## Result

T0–T6 safe implementation/discovery work is complete. T6’s approved
conservative boundary is implemented: state/idempotency, cancellation and
no-shipper snapshots, typed eligibility, reservation compensation and read-only
admin visibility all keep money/provider flags off. Client clean-checkout
configuration is now explicit and proven without native Firebase files. T7–T8
remain operator-gated; no provider credential, real refund call or
device/runtime proof is implied by this plan.
