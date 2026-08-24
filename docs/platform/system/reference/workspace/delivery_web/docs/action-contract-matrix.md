# Web Action-Contract Matrix

> Canonical reference for the visible customer, admin and restaurant-owner
> actions in the Web portal. The public boundary is the API Gateway; service
> ports and client-only legacy routes are not contracts.

Updated: 2026-08-23

## Contract authorities

The endpoint and actor mapping is authoritative in the backend inventory and
Gateway allow-list. The Web adapter is authoritative for envelope parsing and
client-visible error mapping:

- [Backend HTTP API inventory](../../backend_delivery/docs/http-api-inventory.md)
- [Gateway route allow-list](../../backend_delivery/api-gateway/src/main/java/com/delivery/api_gateway/config/GatewayRouteConfig.java)
- [Web endpoint constants](../src/services/api/endpoints.ts)
- [Web envelope and parser contract](../src/services/api/apiClient.ts)
- [Web typed response parsers](../src/services/api/contract.ts)

Every HTTP response is expected to have the canonical `BaseResponse<T>` envelope
`{ status: 0|1, message: string|null, data: T }`. `handleResponse` returns
`data` only for a success status. A non-success envelope, an HTTP failure, or
an invalid payload is surfaced as an `Error`; the Web UI keeps the failed action
retryable where the page exposes retry behavior.

Pagination is not Spring `Page` serialization. The client shape is:

```text
{ items, page, size, totalItems, totalPages, hasNext }
```

The `Proof` column names both the adapter/source and the focused UI test that
demonstrates the action or its failure/retry behavior.

## Authentication and session

| ID | Action/control | Actor | Canonical Gateway request | Request owner | Success `data` | Error behavior | Proof |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AUTH-01 | Restaurant-owner login form submit | `SHOP_OWNER` | `POST /api/auth/login` | `email`, `password`, `role=SHOP_OWNER`, `deviceType=WEB`, stable `deviceId` | `AuthResponse` (`accessToken`, rotated-capable `refreshToken`, `authId`, `email`, `role`) | Invalid envelope/role or HTTP failure becomes a visible login error; no portal navigation | `src/modules/auth/pages/LoginPageV2.tsx`; `src/modules/auth/__tests__/auth-routing.test.tsx`; `src/modules/auth/services/authService.ts` |
| AUTH-02 | Admin login form submit | `ADMIN` | `POST /api/auth/login` | Same login request with `role=ADMIN` | `AuthResponse` with `role=ADMIN` | A valid token for another role is rejected before admin navigation | `src/modules/auth/pages/AdminLoginPage.tsx`; `src/modules/auth/__tests__/auth-routing.test.tsx`; `src/modules/auth/services/authService.ts` |
| AUTH-03 | Session bootstrap/profile refresh | Authenticated `SHOP_OWNER` or `ADMIN` | `GET /api/users` | Bearer access token from the session store | `UserProfileResponse` mapped to the current `User` | Missing/invalid profile identity logs out the local session and returns to the portal login | `src/modules/auth/hooks/useAuth.tsx`; `src/modules/auth/services/authService.ts`; `src/modules/auth/__tests__/auth-routing.test.tsx` |
| AUTH-04 | Single-flight access-token refresh | Authenticated `SHOP_OWNER` or `ADMIN` | `POST /api/auth/refresh-token` | Current `refreshToken`; store both returned tokens before retrying queued requests | `{ accessToken, refreshToken }` | Missing/invalid rotated pair expires the local session; the failed request is not retried indefinitely | `src/services/api/apiClient.ts`; `src/services/api/__tests__/apiClient.test.ts` |
| AUTH-05 | Admin logout control | `ADMIN` | `POST /api/auth/logout` | Current `refreshToken` when present | Ignored/void; local session is always cleared | Network/API failure does not retain the local session; navigate to admin login | `src/components/layouts/AdminLayout.tsx`; `src/modules/auth/services/authService.ts`; `src/modules/auth/__tests__/auth-routing.test.tsx` |
| AUTH-06 | Restaurant-owner logout control | `SHOP_OWNER` | `POST /api/auth/logout` | Current `refreshToken` when present | Ignored/void; local session is always cleared | Network/API failure does not retain the local session; navigate to owner login | `src/components/layout/RestaurantHeader.tsx`; `src/modules/auth/services/authService.ts`; `src/modules/auth/__tests__/auth-routing.test.tsx` |
| AUTH-07 | Customer logout control | `USER` | `POST /api/auth/logout` | Current `refreshToken` when present | Ignored/void; local session is always cleared | Local customer session is cleared and public catalog remains accessible | `src/modules/customer/components/CustomerShell.tsx`; `e2e/web-journeys.spec.ts` |

## Customer ordering actions

| ID | Action/control | Actor | Canonical Gateway request | Request owner | Success `data` | Error behavior | Proof |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CUSTOMER-01 | Browse restaurant catalog | Anonymous or `USER` | `GET /api/restaurants` | No identity; server catalog owns restaurant facts | `Restaurant[]` | Loading, empty, invalid envelope and retry states are visible | `src/modules/customer/pages/CustomerHomePage.tsx`; `src/modules/restaurant/services/restaurantService.ts`; `src/modules/customer/__tests__/customer-ordering.test.tsx` |
| CUSTOMER-02 | Search restaurants | Anonymous or `USER` | `GET /api/restaurants/search?keyword={keyword}` | Trimmed search keyword | `Restaurant[]` | Empty result is distinct from request failure | `src/modules/customer/pages/CustomerHomePage.tsx`; `src/modules/restaurant/services/restaurantService.ts` |
| CUSTOMER-03 | Open restaurant menu | Anonymous or `USER` | `GET /api/restaurants/{id}` and `GET /api/menu-items/restaurant/{id}/available` | Restaurant ID from the public catalog | `Restaurant`, `MenuItem[]` | Invalid ID/API failure remains visible; no local menu fallback | `src/modules/customer/pages/CustomerRestaurantPage.tsx`; `src/modules/restaurant/services/menuService.ts` |
| CUSTOMER-04 | Customer login | `USER` | `POST /api/auth/login` | `email`, `password`, `role=USER`, `deviceType=WEB`, stable browser device ID | `AuthResponse` | Wrong role or invalid credentials remains on the customer login page | `src/modules/customer/pages/CustomerLoginPage.tsx`; `src/modules/auth/services/authService.ts` |
| CUSTOMER-05 | Customer registration | Anonymous | `POST /api/auth/register`, then `POST /api/users/registrations` | `role=USER`; short-lived provisioning token is used once and never stored | Registration/profile handoff result | Admission `503`, validation and interrupted handoff remain retryable; no false login | `src/modules/customer/pages/CustomerRegisterPage.tsx`; `src/modules/auth/services/authService.ts` |
| CUSTOMER-06 | Confirm email | Anonymous | `POST /api/auth/email-verification/confirm` | Token from verification link query string | Void | Invalid/expired token remains visible with a login link | `src/modules/customer/pages/VerifyEmailPage.tsx`; `src/modules/auth/services/authService.ts` |
| CUSTOMER-07 | Maintain local cart | Anonymous or `USER` | No HTTP request | One restaurant, quantity 1–99, versioned localStorage | Local `CustomerCart` | Malformed storage is discarded; changing restaurant requires explicit confirmation | `src/modules/customer/cart/cart.ts`; `src/modules/customer/__tests__/cart.test.ts` |
| CUSTOMER-08 | List customer addresses | `USER` | `GET /api/addresses/users/{userId}/addresses` | Profile ID from authenticated `GET /api/users`; backend rechecks ownership | `UserAddress[]` | Loading/error/retry states are visible | `src/modules/customer/pages/CustomerAddressesPage.tsx`; `src/services/api/addressService.ts` |
| CUSTOMER-09 | Create/update address | `USER` | `POST /api/addresses/users/{userId}/addresses` or `PUT /api/addresses/{id}` | Address fields plus Vietnam coordinates | `UserAddress` | Validation failure does not discard the form; coordinates are required for checkout | `src/modules/customer/pages/CustomerAddressesPage.tsx`; `src/services/api/addressService.ts` |
| CUSTOMER-10 | Delete/default address | `USER` | `DELETE /api/addresses/{id}` or `PATCH /api/addresses/{id}/default` | Selected address ID; backend owns authorization | Void or `UserAddress` | Confirmation cancel makes no request; failure keeps the row/action retryable | `src/modules/customer/pages/CustomerAddressesPage.tsx`; `src/services/api/addressService.ts` |
| CUSTOMER-11 | Checkout preview | `USER` | `POST /api/orders/checkout-preview` | Restaurant, item IDs/quantities and delivery coordinates; no client price authority | `CheckoutPreview` with quote, expiry and canonical totals | Quote/validation failure blocks create; price/unavailable changes are shown | `src/modules/customer/pages/CustomerCheckoutPage.tsx`; `src/modules/restaurant/services/orderService.ts` |
| CUSTOMER-12 | Create COD order | `USER` | `POST /api/orders` with UUID `Idempotency-Key` | Quote ID, COD command and exact cart/address snapshot | `Order` | `PRICE_CHANGED` requires explicit recheck; ambiguous retry keeps the same key; no duplicate order | `src/modules/customer/pages/CustomerCheckoutPage.tsx`; `src/modules/customer/__tests__/customer-ordering.test.tsx` |
| CUSTOMER-13 | Customer order history | `USER` | `GET /api/orders/my-orders?page={page}&size={size}` | Bounded page/size; ownership derives from JWT | `PageData<Order>` | Empty/error/retry/pagination states are explicit | `src/modules/customer/pages/CustomerOrdersPage.tsx`; `src/modules/restaurant/services/orderService.ts` |
| CUSTOMER-14 | Order detail and REST status refresh | `USER` | `GET /api/orders/{id}`; poll active orders every 10 seconds while visible | Order ID; backend enforces participant ownership | `Order` | Poll stops for hidden/terminal orders; transient failures remain retryable | `src/modules/customer/pages/CustomerOrderDetailPage.tsx`; `src/modules/customer/orderStatus.ts` |
| CUSTOMER-15 | Cancel pre-pickup order | `USER` | `PUT /api/orders/{id}/cancel` | Optional reason up to 500 characters | Updated `Order` | UI only offers pre-pickup statuses; backend remains final authority | `src/modules/customer/pages/CustomerOrderDetailPage.tsx`; `src/modules/restaurant/services/orderService.ts` |

## Restaurant-owner actions

| ID | Action/control | Actor | Canonical Gateway request | Request owner | Success `data` | Error behavior | Proof |
| --- | --- | --- | --- | --- | --- | --- | --- |
| OWNER-01 | Load own restaurants | `SHOP_OWNER` | `GET /api/restaurants/my-restaurants` | Identity derives from the authenticated session; no arbitrary creator ID | `Restaurant[]` | Page exposes loading/error state and can retry through the existing provider flow | `src/modules/restaurant/services/restaurantService.ts`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx`; `src/modules/restaurant/pages/RestaurantProfilePage.tsx` |
| OWNER-02 | Create restaurant | `SHOP_OWNER` | `POST /api/restaurants` | `CreateRestaurantRequest` from the form; server owns identity and validation | `Restaurant` | Validation/API error remains visible and does not navigate as success | `src/modules/restaurant/services/restaurantService.ts`; `src/modules/restaurant/components/RestaurantForm.tsx`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx` |
| OWNER-03 | Update own restaurant | `SHOP_OWNER` | `PUT /api/restaurants/{id}` | Restaurant ID selected from the owner-owned record plus `CreateRestaurantRequest` | `Restaurant` | Failed update keeps the form/action available for retry | `src/modules/restaurant/services/restaurantService.ts`; `src/modules/restaurant/components/RestaurantForm.tsx`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx` |
| OWNER-04 | Load own menu | `SHOP_OWNER` | `GET /api/menu-items/my-menu-items` | Identity derives from the authenticated session | `MenuItem[]` | Loading, empty, filtered-empty and retry states remain distinguishable | `src/modules/restaurant/services/menuService.ts`; `src/modules/restaurant/pages/MenuManagement.tsx`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx` |
| OWNER-05 | Create menu item | `SHOP_OWNER` | `POST /api/menu-items` | `restaurantId`, name, description, price and image from the form | `MenuItem` | Failed create is visible and does not silently add a local item | `src/modules/restaurant/services/menuService.ts`; `src/modules/restaurant/components/MenuItemForm.tsx`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx` |
| OWNER-06 | Update menu item | `SHOP_OWNER` | `PUT /api/menu-items/{id}` | Selected menu-item ID plus `CreateMenuItemRequest` | `MenuItem` | Failed update is visible and remains retryable | `src/modules/restaurant/services/menuService.ts`; `src/modules/restaurant/components/MenuItemForm.tsx`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx` |
| OWNER-07 | Delete menu item | `SHOP_OWNER` | `DELETE /api/menu-items/{id}` | Selected owner-owned menu-item ID after confirmation | Ignored/void | Cancel does not call the API; failure is visible and the item is not optimistically removed | `src/modules/restaurant/services/menuService.ts`; `src/modules/restaurant/pages/MenuManagement.tsx`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx` |
| OWNER-08 | Load own restaurant orders | `SHOP_OWNER` | `GET /api/orders/my-restaurant-orders?page={page}&size={size}` | Page number and bounded page size; ownership derives from the session | `PageData<Order>` | Loading, empty, error/retry and next/previous pagination are explicit | `src/modules/restaurant/services/orderService.ts`; `src/modules/restaurant/pages/RestaurantOrders.tsx`; `src/modules/restaurant/__tests__/restaurant-orders.test.tsx` |
| OWNER-09 | Confirm pending order | `SHOP_OWNER` | `POST /api/restaurants/orders/{orderId}/confirm` | `restaurantId`, `estimatedPrepTime` | Enum result `CONFIRMED` | Action is disabled while pending; failure leaves the order decision retryable; success reloads the page | `src/modules/restaurant/services/orderService.ts`; `src/modules/restaurant/pages/RestaurantOrders.tsx`; `src/modules/restaurant/__tests__/restaurant-orders.test.tsx` |
| OWNER-10 | Reject pending order | `SHOP_OWNER` | `POST /api/restaurants/orders/{orderId}/reject` | `restaurantId`, trimmed non-empty `reason` | Enum result `REJECTED` | Missing reason does not call the API; failure leaves the decision retryable | `src/modules/restaurant/services/orderService.ts`; `src/modules/restaurant/pages/RestaurantOrders.tsx`; `src/modules/restaurant/__tests__/restaurant-orders.test.tsx` |
| OWNER-11 | Load approved restaurant ratings | `SHOP_OWNER` | `GET /api/restaurants/{restaurantId}/ratings` | Owner-selected restaurant ID | `RestaurantRating[]` with `status=APPROVED` | Loading, empty and error states remain visible | `src/modules/restaurant/services/restaurantService.ts`; `src/modules/restaurant/pages/RestaurantReviewsPage.tsx`; `src/modules/restaurant/__tests__/secondary-routes.test.tsx` |
| OWNER-12 | Import restaurant and menu catalog | `SHOP_OWNER` | `POST /api/restaurants`, `PUT /api/restaurants/{id}`, `POST /api/menu-items` or `PUT /api/menu-items/{id}` | Canonical create/update payloads; `restaurantKey` is resolved to the ID returned by the restaurant request | `Restaurant` and `MenuItem` per successful record | Preview/validation runs before requests; records run sequentially with restaurant-first ordering; no file-wide transaction or name/address matching; per-record failures remain visible | `src/modules/restaurant/import/catalogImport.ts`; `src/modules/restaurant/pages/CatalogImportPage.tsx`; `src/modules/restaurant/__tests__/catalog-import.test.ts` |
| OWNER-13 | Load shop vouchers | `SHOP_OWNER` | `GET /api/promotions/shop` | Identity derives from the authenticated owner session | `CustomerVoucher[]` | Loading, empty and error states remain visible | `src/modules/restaurant/pages/RestaurantVouchersPage.tsx`; `e2e/web-journeys.spec.ts` |
| OWNER-14 | Submit shop voucher for approval | `SHOP_OWNER` | `POST /api/promotions/shop` | Voucher fields plus current owned restaurant ID | `CustomerVoucher` with pending approval | Validation/API failure keeps the form retryable and does not claim approval | `src/modules/restaurant/pages/RestaurantVouchersPage.tsx`; `e2e/web-journeys.spec.ts` |

## Admin actions

| ID | Action/control | Actor | Canonical Gateway request | Request owner | Success `data` | Error behavior | Proof |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ADMIN-01 | Load all orders | `ADMIN` | `GET /api/orders/all?page={page}&size={size}` | Admin page number and bounded size | `PageData<AdminOrder>` | Error notification; loading ends without fabricating rows; pagination remains explicit | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminOrdersPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-02 | Filter orders by status | `ADMIN` | `GET /api/orders/status/{status}?page={page}&size={size}` | Typed order status plus page/size; changing status resets page to zero | `PageData<AdminOrder>` | Unsupported/error response is surfaced; filter remains local to the admin page | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminOrdersPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-03 | Load all shippers | `ADMIN` | `GET /api/shippers?page={page}&size={size}` | Admin page number and bounded size | `PageData<AdminShipper>` | Error state is visible; no block/delete mutation is implied | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminShippersPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-04 | Load online shippers and filter tabs | `ADMIN` | `GET /api/shippers/online` for the online tab; the all list is `GET /api/shippers?page={page}&size={size}` and offline is filtered locally | No client-supplied arbitrary owner ID | `AdminShipper[]` | Failed load is visible; only the offline tab is a local filter over the all-list response | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminShippersPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-05 | Load ratings for moderation | `ADMIN` | `GET /api/restaurants/admin/ratings` | No body; admin identity is authenticated | `AdminRating[]` | Error state is visible and retryable | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminRatingsPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-06 | Approve rating | `ADMIN` | `PUT /api/restaurants/admin/ratings/{id}/status?status=APPROVED` | Rating ID plus typed status query | `AdminRating` | Failed mutation keeps the row/action available; no optimistic success | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminRatingsPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-07 | Reject rating | `ADMIN` | `PUT /api/restaurants/admin/ratings/{id}/status?status=REJECTED` | Rating ID plus typed status query | `AdminRating` | Failed mutation remains visible/retryable | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminRatingsPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-08 | Load platform coupons | `ADMIN` | `GET /api/promotions/admin` | No body; admin identity is authenticated | `AdminCoupon[]` | Loading, empty and error states are explicit | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminCouponsPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-09 | Create platform coupon | `ADMIN` | `POST /api/promotions/platform` | `CreateAdminCouponRequest`; scope identity must match `ALL`/`SHOP` rules | `AdminCoupon` | Validation/API failure is visible; modal remains usable for retry | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminCouponsPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-10 | Delete platform coupon | `ADMIN` | `DELETE /api/promotions/{id}` | Coupon ID after explicit confirmation | Ignored/void | Cancel does not call the API; failure is visible and does not silently remove the row | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminCouponsPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-11 | Load flash-sale campaigns | `ADMIN` | `GET /api/flashsales/admin/campaigns` | No body; admin identity is authenticated | `FlashSaleCampaign[]` | Loading, empty and error states are explicit | `src/services/api/flashSaleService.ts`; `src/modules/admin/pages/AdminFlashSalePage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-12 | Create flash-sale campaign | `ADMIN` | `POST /api/flashsales/admin/campaigns` | `name`, `isRecurring`, `startTime`, `endTime` | `FlashSaleCampaign` | Validation/API failure is visible; modal remains open/retryable | `src/services/api/flashSaleService.ts`; `src/modules/admin/pages/AdminFlashSalePage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-13 | Change flash-sale campaign status | `ADMIN` | `PUT /api/flashsales/admin/campaigns/{id}/status?status=ACTIVE or ENDED` | Campaign ID plus typed status query | Ignored/void | Failed transition is visible; UI does not claim the new status without success | `src/services/api/flashSaleService.ts`; `src/modules/admin/pages/AdminFlashSalePage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-14 | Load campaign items for review | `ADMIN` | `GET /api/flashsales/admin/campaigns/{id}/items` | Campaign ID selected by admin | `FlashSaleItem[]` | Loading, empty and error states are explicit | `src/services/api/flashSaleService.ts`; `src/modules/admin/pages/AdminFlashSalePage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-15 | Approve flash-sale item | `ADMIN` | `PUT /api/flashsales/admin/items/{id}/approve` | Item ID selected from the loaded campaign | Ignored/void | Failure remains visible/retryable; no local approval is assumed | `src/services/api/flashSaleService.ts`; `src/modules/admin/pages/AdminFlashSalePage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| ADMIN-16 | Import restaurant and menu catalog | `ADMIN` | `POST /api/restaurants`, `PUT /api/restaurants/{id}`, `POST /api/menu-items` or `PUT /api/menu-items/{id}` | Canonical create/update payloads; `restaurantKey` is resolved to the ID returned by the restaurant request | `Restaurant` and `MenuItem` per successful record | Preview/validation runs before requests; records run sequentially with restaurant-first ordering; no file-wide transaction or name/address matching; per-record failures remain visible | `src/modules/restaurant/import/catalogImport.ts`; `src/modules/restaurant/pages/CatalogImportPage.tsx`; `src/modules/restaurant/__tests__/catalog-import.test.ts` |
| ADMIN-17 | Load platform analytics dashboard | `ADMIN` | `GET /api/analytics/dashboard/admin?period=month,quarter,year` | Period plus optional bounded year; scope derives from JWT | `AdminDashboard` with overview, time series, status breakdown and top restaurants | Invalid period/year, disabled projection, invalid envelope or HTTP failure remains visible with retry; client never fabricates metrics | `src/services/api/adminService.ts`; `src/modules/admin/pages/AdminDashboard.tsx`; `src/modules/admin/__tests__/admin-dashboard.test.tsx` |
| ADMIN-18 | Load pending shop vouchers | `ADMIN` | `GET /api/promotions/admin/pending-shop` | No body; admin identity is authenticated | `AdminCoupon[]` pending shop vouchers | Loading, empty and error states are explicit | `src/modules/admin/pages/AdminCouponsPage.tsx`; `e2e/web-journeys.spec.ts` |
| ADMIN-19 | Approve shop voucher | `ADMIN` | `PUT /api/promotions/admin/{id}/approve` | Pending voucher ID | Updated `AdminCoupon` | Failure remains visible/retryable; no optimistic approval | `src/modules/admin/pages/AdminCouponsPage.tsx`; `e2e/web-journeys.spec.ts` |
| ADMIN-20 | Reject shop voucher | `ADMIN` | `PUT /api/promotions/admin/{id}/reject` | Pending voucher ID and optional reason | Updated `AdminCoupon` | Failure remains visible/retryable; no optimistic rejection | `src/modules/admin/pages/AdminCouponsPage.tsx`; `e2e/web-journeys.spec.ts` |

## UI-only actions

These controls do not create backend requests and therefore must not be turned
into guessed endpoints:

| Action | Observable contract | Proof |
| --- | --- | --- |
| Role guards and portal navigation | `SHOP_OWNER` reaches owner routes; `ADMIN` reaches admin routes; unauthorized role reaches the unauthorized/login route | `src/routes/RoleRoute.tsx`; `src/App.tsx`; `src/modules/auth/__tests__/auth-routing.test.tsx` |
| Local order search and status tabs | Filters already loaded rows; status change resets pagination; no hidden backend route is introduced | `src/modules/admin/pages/AdminOrdersPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| Local shipper offline tab | Filters the all-list response locally; the online tab uses the canonical `/api/shippers/online` read; no block/delete mutation exists in MVP | `src/modules/admin/pages/AdminShippersPage.tsx`; `src/modules/admin/__tests__/admin-actions.test.tsx` |
| Modal open/close and confirmation cancel | Cancel/close does not invoke a mutation; submit invokes exactly one action | `src/modules/admin/__tests__/admin-actions.test.tsx`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx` |
| Loading/empty/error/retry rendering | No fabricated success data; failed actions remain observable and retryable where the page supports retry | `src/services/api/__tests__/apiClient.test.ts`; `src/modules/admin/__tests__/admin-actions.test.tsx`; `src/modules/restaurant/__tests__/restaurant-orders.test.tsx`; `src/modules/restaurant/__tests__/restaurant-management.test.tsx` |

## Explicit exclusions

The following are intentionally not Web MVP actions and must remain absent from
the visible router, navigation and production API calls:

- Online payment, settlement, withdrawal and refund actions.
- Browser realtime shipper map, rating, reorder and notification actions.
- User administration CRUD not exposed by the current Web routes.
- Firebase/Chat, livestream and tracking pages.
- Direct service ports, `/api/api` paths, gRPC, STOMP and SockJS.
- Voucher/flash-sale checkout activation; related checkout flags remain
  disabled/default `false`.

Any proposed addition to this matrix needs a canonical Gateway route, an actor
and ownership rule in the backend inventory, a typed response/error contract,
and focused proof before it becomes a visible action.
