# Shipper mobile preview

Entry: /preview/shipper/map?view=workbench
Phone: /preview/shipper/map?view=phone
Use the tune button to open screen, scenario and delivery-status selectors.
All routes live under /preview/shipper and are DEV-only.

## Screen and source coverage

| Route | Native source | Preview behavior |
| --- | --- | --- |
| splash, login | auth presentation views | Bootstrap entry, local login, login error |
| map | MainMapScreen, MapCanvasContract | Online/offline, map configuration fallback, GPS denied |
| offer | MatchFoundView / OfferContract | Single/batch, 30-second sample expiry, accept/reject, invalid COD, busy/loading/error |
| active | ActiveDeliverySheetView | ASSIGNED → PICKED_UP → DELIVERING → DELIVERED; proximity under 200m |
| detail | OrderDetailView, CancelReasonView | Timeline, COD/earnings/commission, ASSIGNED-only cancellation and reasons |
| success | DeliverySuccessView | Earnings, return to map/history |
| history | DeliveryHistoryView | Status filters, detail navigation, loading/error/empty |
| notifications | NotificationsView | All/unread, mark read/all read, delete, empty |
| account, profile | ShipperProfileView / DrawerView | Identity, profile row, ratings/documents, logout entry |
| ratings | ShipperRatingView | Summary, feedback, loading/error/empty |
| documents | DocumentsView | Local form, image file selection, save feedback |
| debug | DebugToolsView | Local gateway draft/log clearing; preview theme control |

Scenario query: ?case=normal|loading|empty|error|gps|map|expired|cod|far|batch|busy
Combine with view=phone using &case=... .
Loading/empty/error list fixtures apply to history, ratings, notifications and
profile. Error also blocks login, offers and status updates. Other scenarios
apply to offers, maps and deliveries as labeled in the workbench.

## Trying a delivery

1. Turn online, choose “Mô phỏng đơn đến”, then accept.
2. Use “Mô phỏng đã đến điểm” to move from the fixture's 450m to 50m.
3. Confirm pickup, start delivery, simulate arrival again and complete.
4. Batch fixture follows an explicit sequential pickup/drop-off for each order;
   completion of the first does not finish the batch. This is a sample route,
   not a routing optimizer or backend batch snapshot.
5. Choose detail while ASSIGNED to exercise cancellation and required reasons.

## Boundaries

This is a DOM preview inspired by the native presentation contracts, not a
React Native renderer. Orange, typography, navigation rows and surfaces follow
the existing customer preview. Native code and backend are unchanged.
No real authentication, COD collection, push notifications, GPS permission,
gateway update, or document upload occurs. Document inputs and notification
changes last while their screen is mounted; delivery state lasts for the
preview mount. Reload resets fixtures.

Mapbox tiles use the existing web env token. The dotted line is illustrative
and is not a navigation instruction. Missing token/tile failures show an
explicit fallback. Browser and native map/permission behavior need separate
device validation.

## Visual references

- [Official ShopeeFood Driver overview](https://driver.shopeefood.vn/trung-tam-ho-tro/ung-dung-nowpartner/)
- [Official receiving-orders guide and screenshots](https://driver.shopeefood.vn/tin-tuc/huong-dan-nhan-don-tren-ung-dung-shopeefood-driver/)
- [Official auto-accept screenshots](https://driver.shopeefood.vn/tin-tuc/moi-cap-nhat-chuc-nang-tu-dong-nhan-don-ke-tiep/)

Observed 2026-09-08. Reference patterns: time-limited offer, pickup/drop-off
hierarchy, separate COD and earnings, bottom action. Native contracts retain
authority; reference-only wallet/chat/auto-assignment features are not added.

## Validation

npm run verify:ci: 29 test files / 148 tests passed, lint no errors
(two pre-existing warnings), typecheck, action contracts and build passed.
Browser: 320px all 14 routes, 390px batch offer/interaction, desktop workbench;
no console warning/error observed. No full E2E suite run.

