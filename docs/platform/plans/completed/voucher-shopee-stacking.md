# Execution Plan: Voucher stacking kiểu Shopee

Date: 2026-08-22

## Status

Completed (code scope; infrastructure deferred)

## Scope adjustment

Theo yêu cầu hiện tại, phần hạ tầng/runtime rollout (Docker sandbox, Kubernetes
apply và canary traffic) được deferred, không phải điều kiện hoàn tất phần code.
Plan này kết thúc ở code contracts, client surfaces, release artifacts và
validation không Docker; production flags vẫn giữ off.

## Outcome

Mở checkout voucher theo mô hình một nhà hàng mỗi đơn với tối đa ba lớp:
`SHOP_DISCOUNT`, `PLATFORM_DISCOUNT` và `FREESHIP`. Server chọn/tính tổ hợp
canonical, giữ quota nguyên tử, ghi breakdown theo voucher và bên tài trợ, rồi
đồng bộ đúng số tiền qua Order, Delivery và Settlement. Flutter, Web customer,
Admin và SHOP_OWNER được rollout bằng canary; luồng cũ và dữ liệu legacy vẫn
được bảo toàn.

## Context

- Chính sách legacy một voucher: `docs/product/features/voucher-flashsale-checkout.md` và `docs/decisions/0003-voucher-flashsale-checkout-policy.md`; policy hiện hành cho stacking: `docs/decisions/0004-voucher-stacking-shopee-policy.md`.
- Cart hiện chỉ chứa một restaurant; Order/Promotion đang giữ một reservation ID.
- Các repo đều có thay đổi chưa commit của người dùng. Không reset/checkout file
  và chỉ chỉnh target surfaces của task.

## Scope

In scope:

- Voucher shop do SHOP_OWNER tạo, ADMIN duyệt; voucher sàn và freeship do ADMIN.
- Auto/manual stacking ba lớp, code collect vào wallet, usage limit theo user.
- Bulk quote/reserve/commit/release/expiry, replay-safe outbox/receipts.
- Discount attribution, platform subsidy, gross/net shipping và Settlement ledger.
- Gateway contracts, Flutter/Web customer UI, Admin/restaurant approval UI, canary
  capability và runbook.

Out of scope:

- Multi-restaurant checkout, online payment, flash-sale stacking và partial-item
  refund.
- Tự động backfill hoặc xóa voucher/wallet legacy.

## Approach

1. Freeze additive REST/Kafka contracts and add migration preflight. Keep legacy
   single-voucher handlers for old reservations while new flow is flagged off.
2. Extend Promotion domain/schema with voucher layers, approval, wallet usage
   counters, parent/line reservations and deterministic combination calculation.
3. Integrate Order quote/create with sorted voucher fingerprints and one atomic
   bulk reservation. Snapshot every discount line and gross/net shipping field.
4. Add versioned event fields through Delivery and Settlement. Validate merchant
   commission base, shipping subsidy and platform promotion expense before ledger
   mutation.
5. Expose role-protected Gateway/API surfaces and update Flutter, Web customer,
   Admin and restaurant portal. Keep internal reservation endpoints private.
6. Run focused/module/PostgreSQL/Kafka/client proof, then canary by stable
   principal allowlist. Roll back UI/Order first and drain Promotion recovery
   paths before disabling relays.

## Locked decisions

- Apply order: shop discount, then platform discount, then freeship.
- Min-order uses pre-discount item subtotal; rounding is two-decimal `HALF_UP`.
- Auto mode enumerates eligible combinations and maximizes total savings; ties
  prefer earlier expiry, then lower stable ID.
- Shop discount reduces restaurant commission base; platform/freeship discounts
  are platform-funded. Shipper/commission use gross shipping.
- Reservation state is `RESERVED -> COMMITTED | RELEASED | EXPIRED`; committed
  reservations may be compensated only before `PICKED_UP`.
- Code entry normalizes, validates, idempotently saves to wallet, then refreshes
  quote. Invalid code does not write wallet or reservation state.

## Progress

- [x] Product choices and stacking/accounting policy confirmed with user.
- [x] Current repositories, dirty surfaces, contracts and rollout gates inspected.
- [x] Promotion schema/domain and focused tests.
- [x] Order/Kafka/Delivery/Settlement integration and focused tests.
- [x] Gateway and Web customer/Admin API surfaces plus canary gate.
- [x] Flutter additive DTO/UI generation and restaurant SHOP_OWNER campaign page.
- [x] Kubernetes template/runtime-config preflight without Docker; generated
  inventory drift fixed in `scripts/verify-kubernetes-manifests.sh`.
- [x] Documentation, release artifact và code-level final validation.
- [x] Infrastructure/runtime rollout deferred by user; no Docker/Kubernetes
  apply performed.

## Risks and recovery

- Partial remote reservation: bulk transaction, stable identity, TTL and
  idempotent release; never decrement counters manually.
- Voucher-row deadlock: lock wallet/voucher rows in sorted voucher ID order.
- Event contract drift: additive `schemaVersion` and dual-read/dual-write until
  legacy reservations drain.
- Subsidy mismatch: Settlement fails closed, records DLT/manual reconciliation,
  and keeps new Order selection disabled.
- Dirty worktrees: inspect each target diff before patching and preserve all
  unrelated changes.

## Current result

Backend stacking, reservation, accounting, Gateway, Web customer checkout,
admin shop-review, restaurant SHOP_OWNER voucher creation, and Flutter checkout
surfaces are implemented behind a stable-principal canary. Flutter Freezed
artifacts were regenerated with the repository-compatible Flutter/Dart SDK.
Production flags remain disabled. Code, release artifacts, client surfaces and
non-Docker validation are complete; infrastructure/runtime rollout was deferred
by the user. No legacy voucher data was backfilled or deleted.

## Validation

- Promotion unit and Testcontainers proof for eligibility, optimizer, usage
  limits, all-or-nothing reserve, expiry, compensation, replay and DLT.
- Order contract/fingerprint/price reconciliation tests and Kafka duplicate
  event proof.
- Delivery/Settlement ledger tests for shop/platform/freeship attribution and
  gross shipping subsidy.
- Gateway security tests, Flutter analyze/tests, Web verify/typecheck/build,
  and retained-volume canary rehearsal adapted for three voucher lines.

Observed in this session:

- `mvn -pl promotion-service clean test`: 55 tests passed, 9 Docker-backed tests
  skipped because Docker sockets are unavailable.
- Current no-Docker re-run `mvn -pl promotion-service test`: 56 tests passed,
  9 Docker-backed tests skipped.
- `mvn -pl promotion-service -Dtest=PromotionControllerAuthorizationTest test`:
  11 tests passed.
- `mvn -pl order-service -am clean compile -DskipTests`: passed.
- Focused Order policy tests: 11 tests passed; canary gate tests: 2 passed.
- Gateway route security test: 13 tests passed; full Gateway run has one
  actuator bind error because the sandbox forbids Netty socket binding.
- Web `npm run typecheck`, `npm test`, and `npm run build`: typecheck passed,
  65 tests passed, production build passed.
- Flutter focused stacking/checkout tests passed; full suite: 263 tests passed.
- Flutter focused analyzer completed with no issues.
- `node deploy/kubernetes/generate.mjs --check`: passed (61 generated files).
- `bash scripts/verify-kubernetes-manifests.sh`: passed (20 private workloads,
  no public edge/Secret material, security probes, explicit-claim checks and
  fail-closed voucher stacking flags).
- Base/staging `kubectl kustomize` renders passed; stacking runtime flags were
  verified default-off.
- Authoritative backend system-contract inventory now describes the three-layer
  stacking contract and explicitly keeps stacking/checkout flags default-off.
- Runbook now includes a Docker-free release/manifest preflight and explicitly
  forbids `kubectl apply` during that preparation step.
- HTTP API inventory drift was fixed by adding the current capability, shop
  approval, bulk reservation and pagination handlers; verifier now passes with
  199 mapped controller methods.
- Gateway route/security regression suite re-run: 13 tests passed; actuator
  health/readiness configuration also passed.
- Focused re-run without Docker: Promotion authorization/calculator 14 tests
  passed; Order capability 2 tests passed; Web typecheck, action-contract and
  handbook checks passed.
- Web full suite re-run: 13 test files and 65 tests passed.
- Web `npm run verify`: passed after regenerating the 172-operation handbook
  snapshot.
- Direct Dart analyzer with telemetry suppressed: `No issues found!`.
- `mvn -pl api-gateway,promotion-service,order-service,delivery-service,settlement-service -am package -DskipTests`:
  release JAR artifacts for the affected service path and their shared modules
  packaged successfully without Docker.
- A Flutter focused re-run was not available in this turn because the FVM SDK
  attempted to write `/Users/a/fvm/versions/3.32.8/bin/cache/engine.stamp`,
  which is outside the workspace and not writable; the prior full suite remains
  recorded at 263 passed.
- Infrastructure/runtime canary was intentionally not run because the user
  deferred that scope and requested limiting Docker usage. Production rollout
  remains disabled.

## Result

Code-level implementation, client validation and release handoff are complete.
Infrastructure/runtime rollout is explicitly deferred by the user; no Docker or
Kubernetes apply is required for this code-scope completion. Production flags
remain off until a later operator-approved rollout.
