# Execution Plan: Admin Order Access & Reusable Shipper Locations

Date: 2026-08-21

## Outcome

- Giữ route `/admin/orders` chỉ cho `ADMIN` ở delivery_web và có regression
  proof cho owner không truy cập được.
- Thêm preset địa chỉ/vị trí shipper dùng lại trong `delivery_simulator_web`;
  preset lưu ở browser, không ghi vào hồ sơ shipper thật hay production.

## Decisions

- `/restaurant/orders` tiếp tục dành cho `SHOP_OWNER` để nhà hàng quản lý đơn
  của chính mình; thay đổi nó thành admin-only sẽ phá role boundary hiện tại.
- “Địa chỉ shipper” trong Scenario Lab là named location preset gồm địa chỉ và
  latitude/longitude; khi áp dụng preset, nó cập nhật `initialLat/initialLng`
  cho shipper trong scenario.

## Progress

- [x] Add regression coverage for admin-only `/admin/orders` and backend `/api/orders/status/{status}`.
- [x] Add browser-local reusable shipper location preset UI and scenario wiring.
- [x] Run frontend tests/typecheck/build and document the storage boundary.

## Validation

- `npm test` in `delivery_web`: 8 files / 40 tests passed.
- `node_modules/.bin/vitest run src/modules/auth/__tests__/auth-routing.test.tsx`:
  8 tests passed, including the owner denial for `/admin/orders`.
- `tsc --noEmit` and Vite production build in `delivery_simulator_web`: passed.
- `mvn -q -Dtest=OrderControllerAuthorizationTest test` in `order-service`:
  passed; non-admin access to global/status order lists is rejected before the
  service query.
- `git diff --check`: passed for the modified tracked frontend/backend files.

## Risks

- Browser-local presets are not shared between users/devices and are lost if
  site storage is cleared; a server-side catalog can be added later if shared
  team fixtures are required.
