# Difficult E2E case matrix

This matrix tracks browser scenarios beyond the happy-path action matrix. A
case is complete only when the browser observes the documented recovery or
guard behavior and the request invariant is asserted.

| Case ID | Tier | Runner | Action | Failure/state | Expected browser behavior |
| --- | --- | --- | --- | --- | --- |
| CASE-AUTH-001 | P0 | mock | AUTH-01 | Empty credentials | Client validation; no login request |
| CASE-AUTH-002 | P0 | mock | AUTH-01 | Login 401 | Error remains on login page; partial session cleared |
| CASE-AUTH-003 | P0 | mock | AUTH-02 | Login 500 | Admin error remains retryable |
| CASE-AUTH-004 | P0 | mock | AUTH-01 | Owner restaurants 500 | Dashboard navigation is blocked; retry stays available |
| CASE-AUTH-005 | P0 | mock | AUTH-02 | Bootstrap profile 503 then success | Recovery page exposes retry and succeeds once backend recovers |
| CASE-AUTH-006 | P0 | mock | AUTH-02 | Access 401 + refresh 401 | Session is cleared and admin login is shown |
| CASE-AUTH-007 | P0 | mock | AUTH-02 | Access 401 + refresh success | One refresh request rotates tokens and original page recovers |
| CASE-AUTH-008 | P0 | mock | AUTH-03 | Owner opens admin route | Unauthorized page; no admin data request |
| CASE-AUTH-009 | P0 | mock | AUTH-03 | Customer opens admin route | Unauthorized page; no admin data request |
| CASE-AUTH-010 | P1 | mock | AUTH-05 | Logout 500 | Local session clears and portal login is reached |
| CASE-CROSS-001 | P1 | mock | CUSTOMER-01 | Catalog 503 then success | Error/retry state, then restaurant list |
| CASE-CROSS-002 | P1 | mock | CUSTOMER-01 | Empty catalog | Empty state, no fabricated restaurant |
| CASE-CROSS-003 | P1 | mock | AUTH-02 | Malformed profile envelope | Recovery error; no fabricated identity |
| CASE-CUSTOMER-001 | P1 | mock | CUSTOMER-02 | Search 500 then success | Search error/retry state then result |
| CASE-CUSTOMER-002 | P1 | mock | CUSTOMER-08 | Empty address list | Empty address state |
| CASE-CUSTOMER-003 | P1 | mock | CUSTOMER-08 | Address list 500 | Error remains visible; no fake address |
| CASE-CUSTOMER-004 | P0 | mock | CUSTOMER-09 | Coordinates outside Vietnam | Validation blocks POST |
| CASE-CUSTOMER-005 | P1 | mock | CUSTOMER-09 | Geolocation denied | Manual-coordinate recovery message |
| CASE-CUSTOMER-006 | P1 | mock | CUSTOMER-09 | Address POST 500 | Form values remain and error is visible |
| CASE-CUSTOMER-007 | P1 | mock | CUSTOMER-10 | Delete confirmation cancelled | DELETE is not sent |
| CASE-CUSTOMER-008 | P1 | mock | CUSTOMER-10 | Delete 500 | Row remains and error is visible |
| CASE-CUSTOMER-009 | P1 | mock | CUSTOMER-10 | Set-default 500 | Existing default state remains visible |
| CASE-CUSTOMER-010 | P1 | mock | CUSTOMER-11 | No cart | Checkout shows empty-cart recovery |
| CASE-CUSTOMER-011 | P1 | mock | CUSTOMER-11 | Address GET 500 | Checkout cannot fabricate quote |
| CASE-CUSTOMER-012 | P0 | mock | CUSTOMER-11 | Preview 500 | Create button remains blocked |
| CASE-CUSTOMER-013 | P0 | mock | CUSTOMER-11 | Preview unavailable item | COD create remains disabled |
| CASE-CUSTOMER-014 | P0 | mock | CUSTOMER-12 | Create PRICE_CHANGED | Fresh preview plus explicit confirmation required |
| CASE-CUSTOMER-015 | P0 | mock | CUSTOMER-12 | Create QUOTE_EXPIRED | Fresh preview and confirmation message |
| CASE-CUSTOMER-016 | P0 | mock | CUSTOMER-12 | Create IDEMPOTENCY_IN_PROGRESS | Retry uses the same idempotency key |
| CASE-CUSTOMER-017 | P0 | mock | CUSTOMER-12 | Transport abort after create attempt | Retry remains safe and key is stable |
| CASE-CUSTOMER-018 | P1 | mock | CUSTOMER-12 | Voucher collect 409 | Error visible; code remains retryable |
| CASE-ORDER-001 | P1 | mock | CUSTOMER-13 | History 500 then success | Retry recovers list |
| CASE-ORDER-002 | P1 | mock | CUSTOMER-13 | Empty history | Empty state and no fake order |
| CASE-ORDER-003 | P1 | mock | CUSTOMER-14 | Detail 404 | Error and retry control; no detail shell |
| CASE-ORDER-004 | P1 | mock | CUSTOMER-15 | Terminal order | Cancel control is absent |
| CASE-ORDER-005 | P1 | mock | CUSTOMER-15 | Cancel 409 | Error keeps order and retry path |
| CASE-OWNER-001 | P1 | mock | OWNER-04 | Menu GET 503 then success | Retry recovers menu |
| CASE-OWNER-002 | P1 | mock | OWNER-04 | Empty menu | Empty state and disabled create without restaurant |
| CASE-OWNER-003 | P1 | mock | OWNER-05 | Empty menu form | Validation blocks POST |
| CASE-OWNER-004 | P1 | mock | OWNER-05 | Menu POST 500 | Modal/form remains usable |
| CASE-OWNER-005 | P1 | mock | OWNER-07 | Delete confirmation cancelled | DELETE is not sent |
| CASE-OWNER-006 | P1 | mock | OWNER-02 | Invalid restaurant form | Validation blocks POST |
| CASE-OWNER-007 | P0 | mock | OWNER-10 | Reject prompt blank/cancelled | Reject is not sent |
| CASE-OWNER-008 | P0 | mock | OWNER-09 | Confirm 409 | Order remains pending and action is retryable |
| CASE-ADMIN-001 | P1 | mock | ADMIN-01 | Orders GET 503 then success | Retry recovers table |
| CASE-ADMIN-002 | P1 | mock | ADMIN-05 | Ratings GET 500 | Error/retry state; no fabricated rating |
| CASE-ADMIN-003 | P1 | mock | ADMIN-09 | Invalid coupon dates/scope | Modal validation blocks POST |
| CASE-ADMIN-004 | P1 | mock | ADMIN-09 | Coupon POST 409 | Modal stays open and retryable |
| CASE-ADMIN-005 | P1 | mock | ADMIN-19 | Pending voucher approve 500 | Pending row/action remains |
| CASE-ADMIN-006 | P1 | mock | ADMIN-12 | Flash-sale POST 500 | Modal stays open |
| CASE-ADMIN-007 | P1 | mock | ADMIN-17 | Analytics GET 503 then success | Retry recovers dashboard without fake metrics |
| CASE-ADMIN-008 | P0 | mock | AUTH-03 | Owner enters admin portal | Unauthorized page and no admin read request |
| CASE-AUTH-011 | P0 | mock | AUTH-02 | Admin empty credentials | Client validation; no login request |
| CASE-AUTH-012 | P0 | mock | AUTH-02 | Admin login 401 | Login page and credentials remain usable |
| CASE-AUTH-013 | P1 | mock | AUTH-01 | Password visibility toggle | Input type changes without API side effect |
| CASE-AUTH-014 | P0 | mock | AUTH-02 | Malformed bootstrap profile | Recovery page; retry restores profile |
| CASE-AUTH-015 | P0 | mock | AUTH-01 | Malformed owner restaurant list | Login remains visible; retry reaches dashboard |
| CASE-AUTH-016 | P1 | mock | AUTH-05 | Logout transport abort | Portal still navigates to login |
| CASE-CROSS-004 | P1 | mock | CUSTOMER-01 | Malformed public catalog | No fabricated restaurant rows |
| CASE-CROSS-005 | P1 | mock | CUSTOMER-03 | Invalid restaurant route | No restaurant/menu API read |
| CASE-CROSS-006 | P1 | mock | CUSTOMER-03 | Restaurant detail 503 | Error surface; no menu shell |
| CASE-CROSS-007 | P1 | mock | CUSTOMER-03 | Malformed public menu | No fabricated menu item |
| CASE-CROSS-008 | P1 | mock | CUSTOMER-02 | Search transport abort | Retry recovers result |
| CASE-CROSS-009 | P1 | mock | CUSTOMER-13 | Order history transport abort | Error state; no fake success |
| CASE-CUSTOMER-019 | P1 | mock | CUSTOMER-09 | Malformed address POST | Form values remain; contract error visible |
| CASE-CUSTOMER-020 | P1 | mock | CUSTOMER-09 | Address PUT 500 | Edit mode and values remain |
| CASE-CUSTOMER-021 | P1 | mock | CUSTOMER-09 | Geolocation unavailable | Manual-coordinate fallback message |
| CASE-CUSTOMER-022 | P1 | mock | CUSTOMER-03 | Malformed restaurant detail | Error surface; no fabricated restaurant |
| CASE-CUSTOMER-023 | P1 | mock | CUSTOMER-03 | Malformed menu response | Restaurant shell stays hidden |
| CASE-CUSTOMER-024 | P1 | mock | CUSTOMER-07 | Cart replacement cancelled | Existing cart remains unchanged |
| CASE-CUSTOMER-025 | P1 | mock | CUSTOMER-11 | Malformed checkout addresses | Quote request is blocked |
| CASE-CUSTOMER-026 | P0 | mock | CUSTOMER-11 | Address missing coordinates | Quote/create remain blocked |
| CASE-CUSTOMER-027 | P1 | mock | CUSTOMER-11 | Malformed voucher capability | Voucher fails closed; checkout remains usable |
| CASE-CUSTOMER-028 | P0 | mock | CUSTOMER-12 | Duplicate COD clicks | Exactly one order POST |
| CASE-CUSTOMER-029 | P1 | mock | CUSTOMER-12 | Generic order 500 | Safe retry surface and key remain observable |
| CASE-CUSTOMER-030 | P1 | mock | CUSTOMER-13 | Malformed order history | No fabricated rows |
| CASE-CUSTOMER-031 | P1 | mock | CUSTOMER-14 | Invalid order id | Client rejects before API |
| CASE-CUSTOMER-032 | P1 | mock | CUSTOMER-15 | Cancel transport abort | Dialog/order remain retryable |
| CASE-CUSTOMER-033 | P1 | mock | CUSTOMER-14 | Detail 404 then success | Retry restores detail |
| CASE-ORDER-006 | P1 | mock | CUSTOMER-14 | Malformed order detail | No detail shell |
| CASE-ORDER-007 | P1 | mock | CUSTOMER-13 | Aborted history request | Retry restores history |
| CASE-ORDER-008 | P1 | mock | CUSTOMER-14 | Delayed detail response | Loading state precedes detail |
| CASE-ORDER-009 | P1 | mock | CUSTOMER-13 | Empty history page | Explicit empty state |
| CASE-ORDER-010 | P1 | mock | CUSTOMER-14 | Detail 500 then success | Retry restores detail |
| CASE-OWNER-009 | P1 | mock | OWNER-04 | Malformed owner menu list | No fabricated menu rows |
| CASE-OWNER-010 | P1 | mock | OWNER-02 | Profile GET 500 | Stale form remains with visible error |
| CASE-OWNER-011 | P1 | mock | OWNER-02 | Profile PUT 500 | Edited values and error remain |
| CASE-OWNER-012 | P1 | mock | OWNER-01 | Owner order list 503 | Retry recovers orders |
| CASE-OWNER-013 | P1 | mock | OWNER-09 | Confirm transport abort | Pending order remains actionable |
| CASE-OWNER-014 | P1 | mock | OWNER-10 | Reject 409 | Decision remains unresolved |
| CASE-OWNER-015 | P1 | mock | OWNER-13 | Voucher POST 500 | Entered code remains |
| CASE-ADMIN-009 | P1 | mock | ADMIN-03 | Malformed shipper list | No fabricated shipper rows |
| CASE-ADMIN-010 | P1 | mock | ADMIN-05 | Rating approve 500 | Pending rating row remains |
| CASE-ADMIN-011 | P1 | mock | ADMIN-08 | Coupon list 500 | Retry and empty state |
| CASE-ADMIN-012 | P1 | mock | ADMIN-08 | Coupon DELETE 500 | Coupon row remains |
| CASE-ADMIN-013 | P1 | mock | ADMIN-12 | Flash-sale items 500 | Review modal retry remains |
| CASE-AUTH-017 | P0 | mock | AUTH-02 | Customer access 401 then refresh | Original orders page recovers |
| CASE-AUTH-018 | P0 | mock | AUTH-01 | Owner access 401 + refresh 401 | Session clears to owner login |
| CASE-AUTH-019 | P0 | mock | AUTH-02 | Delayed refresh | Admin bootstrap remains recoverable |
| CASE-AUTH-020 | P0 | mock | AUTH-02 | Malformed login profile | Partial session is cleared |
| CASE-AUTH-021 | P0 | mock | AUTH-03 | Customer role spoof on admin route | No admin data read |
| CASE-CROSS-010 | P1 | mock | CUSTOMER-01 | Catalog 429 | Retry recovers catalog |
| CASE-CROSS-011 | P1 | mock | CUSTOMER-01 | Non-envelope catalog body | Fails closed without rows |
| CASE-CROSS-012 | P1 | mock | CUSTOMER-03 | Public menu abort | Connection recovery surface |
| CASE-CROSS-013 | P1 | mock | CUSTOMER-01 | Mobile viewport | Catalog remains usable |
| CASE-CROSS-014 | P1 | mock | CUSTOMER-01 | Reload after catalog failure | Queued recovery is deterministic |
| CASE-CUSTOMER-034 | P1 | mock | CUSTOMER-09 | Address POST 422 | Fields remain for correction |
| CASE-CUSTOMER-035 | P1 | mock | CUSTOMER-09 | Address transport abort | Form remains usable |
| CASE-CUSTOMER-036 | P0 | mock | CUSTOMER-11 | Preview 429 | COD create remains blocked |
| CASE-CUSTOMER-037 | P1 | mock | CUSTOMER-11 | Address selection change | New coordinates trigger new quote |
| CASE-CUSTOMER-038 | P1 | mock | CUSTOMER-12 | Voucher collect 500 | Code remains retryable |
| CASE-CUSTOMER-039 | P1 | mock | CUSTOMER-12 | Malformed create success | Checkout remains safe |
| CASE-CUSTOMER-040 | P0 | mock | CUSTOMER-12 | QUOTE_MISMATCH | Fresh quote is loaded |
| CASE-CUSTOMER-041 | P1 | mock | CUSTOMER-13 | Non-envelope history body | No fake empty state |
| CASE-ORDER-011 | P1 | mock | CUSTOMER-15 | SHIPPER_NOT_FOUND terminal state | Recovery message; no cancel |
| CASE-ORDER-012 | P1 | mock | CUSTOMER-15 | CANCELLED terminal state | No cancel control |
| CASE-ORDER-013 | P1 | mock | CUSTOMER-13 | History pagination | Next page requested once |
| CASE-ORDER-014 | P0 | mock | CUSTOMER-15 | Cancel 422 | Confirmation remains open |
| CASE-OWNER-016 | P1 | mock | OWNER-07 | Menu DELETE 500 | Row remains visible |
| CASE-OWNER-017 | P1 | mock | OWNER-08 | Reviews GET 500 | Retry remains available |
| CASE-OWNER-018 | P1 | mock | OWNER-12 | Invalid catalog JSON | Import API is not called |
| CASE-OWNER-019 | P1 | mock | OWNER-13 | Voucher list 500 | Form remains available |
| CASE-ADMIN-014 | P1 | mock | ADMIN-17 | Malformed analytics body | No fabricated metrics |
| CASE-ADMIN-015 | P1 | mock | ADMIN-17 | Analytics 429 | Retry recovers dashboard |
| CASE-ADMIN-016 | P1 | mock | ADMIN-03 | Offline filter empty | Explicit empty state |
| CASE-ADMIN-017 | P1 | mock | ADMIN-08 | Non-envelope coupon list | No fake coupon rows |
| CASE-ADMIN-018 | P1 | mock | ADMIN-12 | Flash campaign 503 | Retry recovers list |
| CASE-ADMIN-019 | P1 | mock | ADMIN-12 | Campaign status 409 | Existing row remains |
| CASE-AUTH-02A | P1 | mock | AUTH-02 | Admin login 400 | Login form remains |
| CASE-AUTH-02B | P1 | mock | AUTH-02 | Admin login 403 | Login form remains |
| CASE-AUTH-02C | P1 | mock | AUTH-02 | Admin login 409 | Login form remains |
| CASE-AUTH-02D | P1 | mock | AUTH-02 | Admin login 422 | Login form remains |
| CASE-AUTH-02E | P1 | mock | AUTH-02 | Admin login 503 | Login form remains |
| CASE-CROSS-01A | P1 | mock | CUSTOMER-01 | Catalog 400 | Retry remains |
| CASE-CROSS-01B | P1 | mock | CUSTOMER-01 | Catalog 403 | Retry remains |
| CASE-CROSS-01C | P1 | mock | CUSTOMER-01 | Catalog 409 | Retry remains |
| CASE-CROSS-01D | P1 | mock | CUSTOMER-01 | Catalog 422 | Retry remains |
| CASE-CROSS-01E | P1 | mock | CUSTOMER-01 | Catalog 503 | Retry remains |
| CASE-CROSS-020 | P1 | mock | ADMIN-17 | Mobile admin viewport | Primary heading remains visible |
| CASE-CUSTOMER-04A | P1 | mock | CUSTOMER-09 | Address POST 400 | Form remains |
| CASE-CUSTOMER-04B | P1 | mock | CUSTOMER-09 | Address POST 403 | Form remains |
| CASE-CUSTOMER-04C | P1 | mock | CUSTOMER-09 | Address POST 409 | Form remains |
| CASE-CUSTOMER-04D | P1 | mock | CUSTOMER-09 | Address POST 422 | Form remains |
| CASE-CUSTOMER-04E | P1 | mock | CUSTOMER-09 | Address POST 503 | Form remains |
| CASE-CUSTOMER-05A | P0 | mock | CUSTOMER-11 | Quote 400 | COD create blocked |
| CASE-CUSTOMER-05B | P0 | mock | CUSTOMER-11 | Quote 403 | COD create blocked |
| CASE-CUSTOMER-05C | P0 | mock | CUSTOMER-11 | Quote 409 | COD create blocked |
| CASE-CUSTOMER-05D | P0 | mock | CUSTOMER-11 | Quote 422 | COD create blocked |
| CASE-CUSTOMER-05E | P0 | mock | CUSTOMER-11 | Quote 503 | COD create blocked |
| CASE-CUSTOMER-06A | P1 | mock | CUSTOMER-12 | Order POST 400 | Safe retry remains |
| CASE-CUSTOMER-06B | P1 | mock | CUSTOMER-12 | Order POST 403 | Safe retry remains |
| CASE-CUSTOMER-06C | P1 | mock | CUSTOMER-12 | Order POST 409 | Safe retry remains |
| CASE-CUSTOMER-06D | P1 | mock | CUSTOMER-12 | Order POST 422 | Safe retry remains |
| CASE-CUSTOMER-06E | P1 | mock | CUSTOMER-12 | Order POST 503 | Safe retry remains |
| CASE-ORDER-01A | P1 | mock | CUSTOMER-13 | History 400 | Error state; no rows |
| CASE-ORDER-01B | P1 | mock | CUSTOMER-13 | History 403 | Error state; no rows |
| CASE-ORDER-01C | P1 | mock | CUSTOMER-13 | History 409 | Error state; no rows |
| CASE-ORDER-01D | P1 | mock | CUSTOMER-13 | History 422 | Error state; no rows |
| CASE-ORDER-01E | P1 | mock | CUSTOMER-13 | History 503 | Error state; no rows |
| CASE-OWNER-02A | P1 | mock | OWNER-05 | Menu POST 400 | Modal remains |
| CASE-OWNER-02B | P1 | mock | OWNER-05 | Menu POST 403 | Modal remains |
| CASE-OWNER-02C | P1 | mock | OWNER-05 | Menu POST 409 | Modal remains |
| CASE-OWNER-02D | P1 | mock | OWNER-05 | Menu POST 422 | Modal remains |
| CASE-OWNER-02E | P1 | mock | OWNER-05 | Menu POST 503 | Modal remains |
| CASE-ADMIN-02A | P1 | mock | ADMIN-03 | Shipper GET 400 | Error state; no rows |
| CASE-ADMIN-02B | P1 | mock | ADMIN-03 | Shipper GET 403 | Error state; no rows |
| CASE-ADMIN-02C | P1 | mock | ADMIN-03 | Shipper GET 409 | Error state; no rows |
| CASE-ADMIN-02D | P1 | mock | ADMIN-03 | Shipper GET 422 | Error state; no rows |
| CASE-ADMIN-02E | P1 | mock | ADMIN-03 | Shipper GET 503 | Error state; no rows |
| CASE-ORDER-020 | P1 | mock | CUSTOMER-14 | Detail 403 | Error surface; no items |
| CASE-OWNER-025 | P1 | mock | OWNER-02 | Malformed profile list | Stale profile guarded |
| CASE-ADMIN-025 | P1 | mock | ADMIN-12 | Flash list 429 | Retry remains; no campaigns |
| CASE-CUSTOMER-042 | P1 | mock | CUSTOMER-11 | Corrupt cart localStorage | Cart fails closed; checkout preview is not called |
| CASE-CUSTOMER-043 | P1 | mock | CUSTOMER-02 | Search returns an empty list | Explicit no-results state; no stale restaurant row |
| CASE-CUSTOMER-044 | P1 | mock | CUSTOMER-09 | Coordinate at lower Vietnam boundary | Boundary coordinate passes validation and is submitted |
| CASE-OWNER-026 | P1 | mock | OWNER-05 | Menu PUT 409 during edit | Edited values and modal remain retryable |
| CASE-OWNER-027 | P1 | mock | OWNER-04 | Local menu filter matches nothing | Filtered-empty state; no extra menu read |
| CASE-ADMIN-026 | P1 | mock | ADMIN-07 | Reject rating succeeds | Visible row changes to REJECTED after the canonical request |
| CASE-ADMIN-027 | P1 | mock | ADMIN-05 | Rating status filter has no rows | Explicit filtered-empty state; loaded row is hidden locally |
| CASE-CUSTOMER-045 | P1 | mock | CUSTOMER-07 | Valid JSON with invalid cart schema | Cart discarded before checkout reads |
| CASE-CUSTOMER-046 | P1 | mock | CUSTOMER-10 | Address delete conflict | Row and retryable error remain |
| CASE-CUSTOMER-047 | P1 | mock | CUSTOMER-10 | Default-address conflict | Existing default and action remain |
| CASE-OWNER-028 | P1 | mock | OWNER-12 | Catalog partial failure | Successful records are retained; failed record is reported |
| CASE-OWNER-029 | P1 | mock | OWNER-12 | Update-existing import mode | Existing IDs use PUT and avoid duplicate POSTs |
| CASE-OWNER-030 | P0 | mock | OWNER-05 | Same-tick menu create clicks | Exactly one menu POST |
| CASE-OWNER-031 | P1 | mock | OWNER-12 | Unresolved restaurant key | No import write before validation |
| CASE-ADMIN-028 | P1 | mock | ADMIN-10 | Coupon delete confirmation cancel | No DELETE request and row remains |
| CASE-ADMIN-029 | P1 | mock | ADMIN-19 | Shop voucher rejection success | Pending row is removed after refresh |
| CASE-ADMIN-030 | P1 | mock | ADMIN-09 | SHOP coupon missing scope identity | No create request; modal remains |
| CASE-ADMIN-031 | P0 | mock | ADMIN-12 | Same-tick campaign status clicks | Exactly one status PUT |
| CASE-ADMIN-032 | P1 | mock | ADMIN-12 | Flash item approval conflict | Review row and retry action remain |
