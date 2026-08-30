# SDD ledger — plan: /Users/a/Documents/private/delivery/docs/plans/active/restore-all-capabilities.md

## Preflight scan

| Task / pair | Shared output or interface | Finding / ruling |
|---|---|---|
| Task 1 | Baseline and authority inventory consumed by every later task | Agrees with the plan; it must remain read-only and must not turn prior dirty work into a rollback baseline. |
| Task 2 | Auth Gateway routes and Flutter auth repository/router | Agrees with the plan; recovery is public, exact-route, and must preserve the two existing dirty auth files. |
| Task 3 | Backend catalog/checkout contracts ↔ Flutter catalog/cart/preview/order creation | Agrees with the plan; `flashSaleItemId` must be server-authoritative and reservation remains gated. |
| Task 4 | Backend payment intent/return contracts ↔ Flutter checkout/deep-link handling | Agrees with the plan; payment flag-off must leave COD behavior unchanged and status refresh must be idempotent. |
| Task 5 | Backend livestream HTTP/media contracts ↔ Flutter viewer and web host controls | Agrees with the plan; clients use Gateway-only traffic and the web surface is host/operator-only. |
| Task 6 | Backend entitlement contract ↔ Flutter iOS/Android adapters | Agrees with the plan; only server-confirmed entitlements are exposed. |
| Task 7 | Backend support conversation contract ↔ Flutter customer UI and web operator UI | Agrees with the plan; both clients use backend-owned chat and neither uses direct Firebase chat. |
| Task 8 | Validation and rollout evidence consumes every task's contracts and flags | Agrees with the plan; completion requires executable proof and documented rollback, not only compilation. |
| Task 1 self-consistency | Status/history evidence against read-only scope | Agrees with the plan. |
| Task 2 self-consistency | Auth tests/contracts against auth repository/router changes | Agrees with the plan; the existing partial recovery implementation needs the login entry point and contract tests. |
| Task 3 self-consistency | Flash Sale tests/module against catalog/cart/checkout integration | Agrees with the plan; no UI can bypass existing ViewModels. |
| Task 4 self-consistency | VNPay tests/flags against Gateway return flow | Agrees with the plan; sandbox-only adapter is the first implementation boundary. |
| Task 5 self-consistency | Viewer/host tests against Gateway-only lifecycle | Agrees with the plan; no STOMP/SockJS or direct service port is allowed. |
| Task 6 self-consistency | Receipt/entitlement tests against platform adapters | Agrees with the plan; pending, restore, and revoked states need explicit coverage. |
| Task 7 self-consistency | Chat persistence/realtime tests against both client surfaces | Agrees with the plan; server remains the sole conversation authority. |
| Task 8 self-consistency | Cross-repo checks against all rollout gates | Agrees with the plan; stale-route/direct-port scans and sandbox E2E are required. |

## Progress

- Task 1: complete (baseline and history reconnaissance was completed before this continuation; current repo statuses were rechecked on 2026-08-27).
- Task 2: implementation complete, validation recorded (password recovery request/reset UI, login entry point, route helpers, Flutter contract/widget tests, Gateway route/security tests, and Auth endpoint/service tests are green; wave remains uncommitted because the checkout contains unrelated dirty work that must not be mixed blindly).
- Task 3: complete (commits 353733d..c728773, review clean after two scoped fix rounds). Flash Sale customer flow is restored with backend-aligned catalog parsing, explicit loading/error/empty states, all-active-campaign aggregation with partial-failure isolation, and server-authoritative `flashSaleItemId`; focused Flash Sale/catalog/cart validation passed (53 tests) and analyzer was clean. Reservation remains default-off pending sandbox proof.
- Task 4: complete (backend commits 716985b, c2efe02; app commits 3db0bac, 90a4ef8, 8f7b9a7). VNPay is restored only as a sandbox/return boundary: Gateway and service/client flags default off, COD/order behavior is unchanged, unsupported customer ownership returns explicit 409, and callbacks only trigger one canonical Gateway status refresh. Focused Gateway (14), Settlement (8), Flutter payment/checkout (26 plus 13 fix-round payment tests), analyzer, Compose config, generated HTTP contract check, and diff checks passed. Review found and fixed production deep-link wiring, stale generated contract, and shared WebView/app-link idempotency; final scoped re-review approved with no Critical/Important findings. Real online order payment remains blocked pending principal-owned payment/order lifecycle and provider reconciliation proof.
- Task 5: complete (backend commit 1add1e8; Flutter commit f125e1b; web commit 35b2448). Restored the Gateway-only customer livestream viewer boundary and minimum restaurant host controls. Backend host ownership verification fails closed, caller-controlled token issuance is disabled, and all livestream rollout flags remain default-off. Focused proof passed: Flutter 12, web 4, backend livestream 4, Gateway 14. Unrelated auth, simulator, and handbook dirty changes remain preserved. Independent reviewer was unavailable after repeated timeout; manual load-bearing review found no Critical/Important issue. Media remains an explicit retryable unavailable state until the native Agora adapter is configured.
- Task 6A: complete (Flutter commit e7cfe02). Added the client-first entitlement domain/coordinator, receipt validation, explicit iOS/Android adapter ports, unavailable repository, Riverpod providers, and `IAP_ENTITLEMENTS_ENABLED=false` rollout default. Purchase/restore cannot grant local access; only a backend-confirmed snapshot can be `active`. Focused proof passed: 6 tests, analyzer, and diff check. Task 6B remains pending until backend receipt verification and entitlement ownership contracts are implemented.
- Task 7A: complete (Flutter commit 607489b). Added a backend-owned support conversation domain/coordinator/provider with `SUPPORT_CHAT_ENABLED=false`; unavailable backend returns a closed boundary and only server-owned messages are exposed. No Firebase chat or direct realtime client was added. Focused proof passed: 2 tests and analyzer. Backend/Gateway persistence/realtime and web operator surface remain pending.

### Task 4 review trail

- Initial task review: ❌ Important deep-link integration was unreachable in production and generated HTTP contract was stale.
- Fix round 1: `915dd96` / `c2efe02` wired router deep links, regenerated HTTP contract/catalog, and added `PAYMENT_CLIENT_API_ENABLED=false` to `.env.example`; re-review found an Important duplicate-refresh risk because WebView used a separate coordinator.
- Fix round 2: `8f7b9a7` made `PaymentReturnPage` use the shared `paymentReturnCoordinatorProvider` and added concurrent/shared-state proof; scoped re-review verdict: all findings addressed, no new Critical/Important breakage.

### Task 3 review trail

- Initial task review: ❌ Important findings for missing visible empty state and silently ignoring active campaigns after the first.
- Fix round 1: `4abb271` added visible empty state and all-active-campaign aggregation; scoped re-review found a new Important all-or-nothing failure-mode regression.
- Fix round 2: `c728773` isolated per-campaign item failures and retained successful inventory; scoped re-review verdict: finding addressed, no new Critical/Important breakage.

### Validation evidence

- Flutter focused wave: 30 tests passed across routing, login/recovery views, datasource contract/support, and password-recovery use cases; focused analyzer reported 0 issues.
- Backend Gateway focused wave: 23 tests passed (`GatewayRouteSecurityTest`, `GatewayRateLimitFilterTest`).
- Backend Auth focused wave: 15 tests passed (`AuthEndpointSecurityTest`, `AccountSecurityServiceIntegrationTest`) using `mvn -pl auth-service -am ...` because the system has Maven but no repository `mvnw`.

## Decisions

- Ruling: Keep the implementation in the existing dirty polyrepo checkouts — the user explicitly authorized deployment, and creating fresh worktrees would omit the pending Auth/backend changes that must be preserved. Cost if wrong: changes from another in-flight effort remain co-located and require careful per-repo commits/review.
- Ruling: Use `delivery_app` as the SDD ledger anchor because the workspace root is not a Git repository while the plan spans four child repositories. Cost if wrong: ledger artifacts are physically under one child repo, though the plan path and cross-repo scope are explicit.
- Ruling: Pause before Task 6 implementation — the current backend authority has no IAP/entitlement endpoint, receipt schema, product catalog, provider verification policy, or rollout flag. Inventing those externally observable policies would risk exposing unverified entitlements or accepting forged receipts; resume only after the backend contract is specified and owned.
