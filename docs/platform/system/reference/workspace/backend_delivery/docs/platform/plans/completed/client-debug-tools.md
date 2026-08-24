# Execution Plan: Client Debug Tools

Date: 2026-08-21

## Status

Completed

## Outcome

Trong build debug của `delivery_app` và `shipper_app2`, người phát triển có thể
mở màn hình Debug để xem log/API call gần đây (method, URL, status, thời gian;
header và payload không được ghi), xóa log, và đổi/reset Gateway backend URL.
URL tùy chỉnh được lưu cục bộ; thay đổi áp dụng cho HTTP client đang chạy và
URL WebSocket tracking của lần kết nối tiếp theo. Build release không hiển thị
điểm vào debug.

## Context

- System entry map: `AGENTS.md`.
- Workflow: `docs/WORKFLOW.md`.
- Client roles and Gateway boundary: `docs/product/overview.md`.
- Flutter HTTP composition: `delivery_app/lib/core/network/` and
  `delivery_app/lib/core/config/runtime_config.dart`.
- React Native HTTP/runtime composition: `shipper_app2/src/config/`,
  `shipper_app2/src/app/productionRuntimeDependencies.ts`, and
  `shipper_app2/src/app/store/`.

## Scope

In scope:

- Bounded in-memory debug log stores with no header/payload capture and
  redaction defense for any structured diagnostic value.
- HTTP request/response/error diagnostics in both clients.
- Persisted debug-only Gateway origin override with validation and reset.
- Customer Settings and shipper Drawer entry points and debug screens.
- Focused tests and repository checks for both clients.

Out of scope:

- Backend/API contract changes.
- Release-build debug UI or remote log upload.
- Persisting request/response logs or exposing raw authorization headers.

## Approach

1. Add client-local diagnostic ports/stores and connect existing HTTP adapters
   so every request is captured without changing feature repositories.
2. Add a persisted Gateway-origin controller/runtime. Existing HTTP clients
   observe the controller; the shipper runtime derives tracking WebSocket URLs
   from the current origin.
3. Add debug-only navigation entries and screens using each app's existing
   presentation/composition patterns.
4. Run focused tests first, then Flutter and React Native repository checks.

## Risks And Recovery

- Diagnostic payloads may contain personal or credential-like fields; recursive
  redaction, truncation, no headers, and bounded memory are required. Recovery:
  clear the in-memory log buffer or remove the feature files if validation
  reveals an unsafe path.
- A custom URL can make the current session unreachable. Recovery: use Reset to
  restore the build-time Gateway origin; the stored override can also be
  removed from app storage.
- Existing user changes in either repo must be preserved. Recovery: inspect
  each repo diff before applying overlapping edits and revert only task-owned
  hunks if needed.

## Progress

- [x] Inspect workflow, client composition roots, HTTP adapters, and navigation.
- [x] Add and verify `delivery_app` diagnostics/runtime URL/debug screen.
- [x] Add and verify `shipper_app2` diagnostics/runtime URL/debug screen.
- [x] Run repository validation and record evidence.

## Decisions

- 2026-08-21: Restrict entry points to debug builds; backend URL overrides are
  developer tooling, not a production policy.
- 2026-08-21: Store only a bounded, in-memory diagnostic history; do not persist
  request/response payloads.
- 2026-08-21: Treat Gateway origin as the editable value and derive `/api` and
  WebSocket paths from it, matching the existing client configuration contract.

## Validation

- Focused proof: bounded/redacted diagnostic store, URL normalization,
  persisted shipper runtime override, and API client base URL/metadata logging
  behavior in each app.
- Integration or end-to-end proof: debug entry visibility and screen actions in
  widget/component tests where practical.
- Repository-required checks: `fvm flutter analyze`, `fvm flutter test`,
  `fvm flutter test --coverage`, Flutter coverage policy, plus shipper
  `npm run verify`.

## Result

Implemented and verified on 2026-08-21.

- `delivery_app`: Debug entry in Settings, bounded in-memory API/app logs,
  persisted Gateway override wired into authenticated and public Dio clients.
- `shipper_app2`: Debug entry in the authenticated drawer, bounded API log
  store, persisted Gateway override hydrated before bootstrap, and dynamic
  tracking WebSocket origin.
- Security boundary preserved: neither client records HTTP headers or request /
  response payloads in network diagnostics.
- Validation: Flutter analyze passed; full Flutter suite passed (259 tests),
  coverage suite passed, and coverage policy passed. Shipper typecheck, lint,
  architecture verification, and full Jest suite passed (46 suites, 150 tests).
