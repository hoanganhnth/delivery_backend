# Shipper mobile web preview

## Outcome and authority
DEV-only /preview/shipper routes, isolated in-memory fixtures. Native app remains
unchanged. Screen authority: shipper_app2/src/app/navigation/AppNavigator.tsx,
feature presentation contracts, deliveryTransitionPolicy, codPolicy,
batchRoutePolicy and proximityPolicy (strictly under 200m).

## Design
Mobile map and bottom task sheet, orange #ee4d2d and typography matching customer
preview. Native screens are re-expressed in DOM, not imported React Native UI.
Reference: https://driver.shopeefood.vn/tin-tuc/huong-dan-nhan-don-tren-ung-dung-shopeefood-driver/
and https://driver.shopeefood.vn/trung-tam-ho-tro/ung-dung-nowpartner/ .
Use offer route summary, distinct COD/earnings, readable pickup/drop-off steps.
No wallet/chat/new operational policies added from reference apps.

## Coverage
Splash, login; map offline/online/missing-map/GPS denied; single/batch offer,
expiry/reject/invalid COD; ASSIGNED/PICKED_UP/DELIVERING/DELIVERED, proximity and
network errors; cancellation reasons (ASSIGNED only); success; history/detail;
notifications read/delete; profile/logout; ratings; documents; debug.
Shared loading/error/empty scenarios exposed in workbench controls.

## Progress
- [x] Inspect native screens/contracts and official references.
- [x] Build scoped mobile preview, state machine, fixtures and screen controls.
- [x] Validate transitions, component flows, build and visual browser smoke.

## Recovery
Remove shipper preview module and DEV route branch. No database changes.

## Validation result — 2026-09-08
npm run verify:ci passed: 29 files / 148 tests; lint has only two pre-existing
warnings. Browser checked all 14 routes at 320px; fixed offer content overflow,
verified batch accept/proximity transition at 390px and desktop workbench.
No full E2E run. Source/route matrix and reference URLs are in the module README.
