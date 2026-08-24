# Plan: JWKS Authentication Migration & Resource Server Decoupling (Revised)

## Executive Summary
This document details the refined migration plan for the `delivery` platform authentication model from API Gateway JWT termination & header injection (`X-User-Id`, `X-Role`) to a decentralized Spring Security OAuth2 Resource Server pattern using JWKS (`RS256`).

This revised plan addresses 5 key architectural requirements:
1. **Auth Service Self-Migration**: Migrating `auth-service`'s own controllers & internal request producers away from identity headers.
2. **Modular Starter Design**: Providing reusable `JwtDecoder`, `Converter<Jwt, AbstractAuthenticationToken>`, and `AuthenticatedActor` without forcing a global `SecurityFilterChain` on services with custom route policies.
3. **3-Wave Rollout with Buffer Window**: 3 distinct waves with a mandatory 15-minute (+ skew) transition buffer for token `kid` adoption.
4. **Explicit Principal & Converter**: Implementing a custom `Converter<Jwt, AbstractAuthenticationToken>` mapping JWT claims to `AuthenticatedActor` and `ROLE_*` authorities.
5. **Concrete Internal Contract Refactoring**: Defining exact DTO payload & `Internal-Token` contracts for Auth->User block sync, Order->Promotion quotes, and Tracking support history.

---

## 1. Token & JWKS Contract

### 1.1 JWKS Endpoint
- **URL**: `GET /.well-known/jwks.json` (routed publicly by Gateway, reachable internally at `http://auth-service:8081/.well-known/jwks.json`).
- **Headers**: `Cache-Control: max-age=300`.
- **Response Format**:
  ```json
  {
    "keys": [
      {
        "kty": "RSA",
        "alg": "RS256",
        "use": "sig",
        "kid": "auth-key-2026-08",
        "n": "<base64url-modulus>",
        "e": "AQAB"
      }
    ]
  }
  ```
- Exposes active key and retiring keys (for rotation). Does NOT expose private key components (`d`, `p`, `q`, `dp`, `dq`, `qi`).

### 1.2 Access & Refresh Token Claims
- **Algorithm**: `RS256` with header `kid`.
- **Access Token Claims**:
  - `iss`: Configurable `$JWT_ISSUER` (default: `http://auth-service` or `delivery-auth`).
  - `aud`: `["delivery-api"]`.
  - `sub`: User ID (String).
  - `email`: User email (String).
  - `roles`: Canonical authorization array (e.g. `["USER"]`, `["ADMIN"]`, `["SHIPPER"]`, `["SHOP_OWNER"]`).
  - `role`: Legacy scalar string for compatibility phase (e.g. `"USER"`).
  - `token_type`: `"access"`.
  - `jti`: Unique token identifier (UUID).
  - `iat`: Issued-at timestamp.
  - `exp`: Expiry timestamp (15-minute TTL).
- **Refresh Token Claims**:
  - Header: `kid`.
  - Claims: `iss`, `sub`, `email`, `role`, `token_type="refresh"`, `jti`, `iat`, `exp` (7-day TTL).
  - Rejection: Business services reject tokens with `token_type != "access"`.

### 1.3 Key Rotation Procedure
1. **Publish**: Generate and add new public JWK to `auth-service` JWKS (`GET /.well-known/jwks.json`).
2. **Cache Wait**: Wait 5 minutes (JWKS `Cache-Control: max-age=300`) + clock skew.
3. **Switch Signer**: Switch `jwt.active-kid` in Auth Service to sign new tokens with the new private key.
4. **Access Overlap**: Retain old JWK in JWKS for 15 minutes (access token TTL) + 5 minutes skew.
5. **Refresh Overlap**: Retain old verification public key in `auth-service` for 7 days (refresh token TTL) to validate active refresh tokens.

---

## 2. Shared `auth-resource-server-starter` Module

- **Location**: `backend_delivery/auth-resource-server-starter`
- **Dependencies**: `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`.
- **Provided Components (No Forced Global SecurityFilterChain)**:
  1. `NimbusJwtDecoder` Bean Factory:
     - Configured with `${AUTH_JWKS_URI:http://auth-service:8081/.well-known/jwks.json}`.
     - Strict validators: RS256 algorithm enforcement, mandatory `kid`, issuer matching `${JWT_ISSUER}`, audience matching `delivery-api`, and `token_type` matching `access`.
  2. `JwtAuthenticationConverter` (`Converter<Jwt, AbstractAuthenticationToken>`):
     - Parses `sub` (userId), `email`, and `roles` claim list.
     - Constructs `AuthenticatedActor` as the principal.
     - Maps each role to `GrantedAuthority` with `ROLE_` prefix (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_SHIPPER`, `ROLE_SHOP_OWNER`).
     - Returns `JwtAuthenticationToken` / `AuthenticatedActorAuthenticationToken`.
  3. `AuthenticatedActor`:
     - Properties: `Long userId`, `String email`, `Set<String> roles`.
     - Helper methods: `hasRole(String role)`, `isAdmin()`, `isShipper()`, `isShopOwner()`, `isUser()`.

Each microservice imports these beans and configures its own explicit `SecurityFilterChain` declaring its anonymous, bearer-protected, and internal (`Internal-Token`) routes.

---

## 3. Microservice & Auth-Service Refactoring

### 3.1 `auth-service` Self-Migration
- **Controllers**: `AuthController.java` (lines 163, 182, 212) admin endpoints (`/api/auth/admin/accounts/{id}/block`, `/api/auth/admin/accounts/{id}/unblock`, `/api/auth/accounts/{id}`) currently read `@RequestHeader("X-Role")` and `@RequestHeader("X-User-Id")`. Convert them to authenticate via Bearer JWT principal (`AuthenticatedActor` / `Jwt`).
- **Internal Call Sync**: `AuthService.java` (line 637) `syncUserBlockState` currently injects `X-User-Id` and `X-Role` headers when calling `user-service`. Refactor to carry audited `adminId` and `reason` inside the JSON request body DTO payload secured by `Internal-Token` header.

### 3.2 Service Migration
For every service (`user-service`, `order-service`, `delivery-service`, `restaurant-service`, `shipper-service`, `promotion-service`, `settlement-service`, `flashsale-service`, `notification-service`, `tracking-service`, `analytics-service`, `livestream-service`):
- Add `auth-resource-server-starter` dependency to `pom.xml`.
- Replace `@RequestHeader("X-User-Id")` / `@RequestHeader("X-Role")` with `@AuthenticationPrincipal AuthenticatedActor actor`.
- Declare explicit `SecurityFilterChain` for service route classifications (Anonymous, Bearer, Internal).

### 3.3 Concrete Internal Call Contracts
- **Auth -> User block sync**: `POST /api/internal/users/{id}/block-status` with body `{"adminId": 123, "blocked": true, "reason": "..."}` and `Internal-Token` header. No `X-User-Id`/`X-Role` headers.
- **Order -> Promotion quote (`CheckoutReservationClient.java` line 46)**: Change `Order-Service` call to `POST /api/promotions/internal/calculate` carrying `userId` inside the request body DTO authenticated via `Internal-Token`, OR forward the client's `Authorization: Bearer <token>`.
- **Tracking Support History (`InternalLocationHistoryController.java` line 30)**: Update `/internal/tracking/location-history/deliveries/{deliveryId}` to authenticate via Bearer JWT (deriving ADMIN actor) and/or `Internal-Token` header. Eliminate `X-User-Id` and `X-Role` header checks.

### 3.4 Tracking WebSocket Handshake
- Update `WebSocketConfig` in `tracking-service`: Intercept handshake and validate `Authorization: Bearer <token>` via `JwtDecoder` (JWKS).
- Populate `authenticatedUserId` and `authenticatedRole` from the validated `AuthenticatedActor`.
- Update `TrackingPublisherProbe.java` and WebSocket unit/integration tests to pass `Authorization: Bearer <token>`.

---

## 4. Gateway Migration & Rate Limiting

### 4.1 Gateway Rate Limiting & Trusted Proxy
- Rewrite `GatewayRateLimitFilter` to remove all dependence on `X-User-Id` / `X-Role` and role-based policies (`admin`).
- Resolve rate-limiting keys using peer IP for ALL traffic categories: `public_auth`, `user_registration`, `public_catalog`, `websocket_connection`, `authenticated_read`, `mutation`.
- Support trusted proxy IP resolution (`X-Forwarded-For` with configured trusted proxy subnets) for production load balancer environments.

### 4.2 Gateway Route Cleanup
- Route `GET /.well-known/jwks.json` to `auth-service`.
- In Wave 3 (Final Cutover): Remove `JwtAuthenticationFilter`, `JwtPublicKeyProvider`, JJWT dependency, per-route role filters, and `X-User-Id`/`X-Role` header injection.
- Retain `TrustedIdentityHeaderFilter` at `HIGHEST_PRECEDENCE` to sanitize/strip any inbound `X-User-Id` / `X-Role` headers.
- Remove `X-User-Id` and `X-Role` from exposed CORS headers in `CorsConfig.java`.

---

## 5. 3-Wave Rollout Plan & Acceptance Gate

### Wave 1: Auth Issuer & JWKS Rollout
1. Update `auth-service` `TokenService` to emit `RS256` tokens with `kid` header and canonical `roles` claim.
2. Expose `GET /.well-known/jwks.json`.
3. Keep the previous Gateway release available during the buffer. The final
   Gateway implementation does not retain a JWT dual-validation fallback.

### Wave 2: Token TTL Buffer Window & Resource Server Service Rollout
1. **Mandatory Buffer Window**: Wait at least 15 minutes (Access Token TTL) + 5 minutes clock skew after Wave 1 deployment. This guarantees all client access tokens in circulation contain `kid`.
2. Deploy microservices with `auth-resource-server-starter` and migrated internal contracts.
3. Migrate `auth-service` controllers (`AuthController.java`) and `AuthService.java` internal block sync.

### Wave 3: Final Gateway Cutover & Policy Cleanup
1. Strip Gateway `JwtAuthenticationFilter` and JJWT dependencies.
2. Switch Gateway rate limiting to peer-IP key across all route categories.
3. Update CORS configuration.

### Acceptance Gate:
- Zero occurrences of `@RequestHeader("X-User-Id")` or `@RequestHeader("X-Role")` in any production controller or HTTP client producer across all services.
- Verification probe (`TrackingPublisherProbe`) and test suite pass clean.
- Update documentation (`ARCHITECTURE.md`, `SECURITY.md`, `http-api-inventory.md`).

---

## Execution Status (2026-08-08)

Completed for the local/staging Compose environment.

### Outcome

Complete the final JWKS cutover without Gateway JWT dependencies, while keeping
the public and internal route contracts executable and proving the common
resource-server security boundary.

### Scope

In scope:

- Fix Gateway compilation/tests, route `GET /.well-known/jwks.json`, and remove
  obsolete Gateway key dependencies/configuration.
- Use explicit, method-scoped anonymous/internal route allow-lists so existing
  `Internal-Token` calls reach their controller checks.
- Publish retiring keys under the operator-supplied prior `kid`; enforce RS256
  in the shared decoder; test JWKS, decoder validation, converter mapping, and
  multi-role WebSocket behavior.
- Update the build guard and the architecture/runbook material that still
  describes Gateway identity injection.

Out of scope:

- Enabling the already-disabled analytics, settlement self-service, payment, or
  livestream surfaces. Their required ownership rules remain explicitly open in
  `backend_delivery/docs/http-api-inventory.md`.
- Implementing a legacy-token fallback. The approved migration requires the
  Wave 1 token buffer before resource-server rollout.

### Risks And Recovery

- Deploying Wave 3 before all access tokens carry `kid` will reject active
  sessions. Recover by retaining the previous Gateway release until the
  15-minute access-token buffer plus skew has elapsed.
- A retiring key with the wrong `kid` makes still-valid tokens unverifiable.
  The Compose overlay therefore requires the old `kid` explicitly; remove the
  overlap only after the seven-day refresh-token window.
- Exact internal paths remain protected by controller-level `Internal-Token`
  checks. A failed deployment can roll back services independently because no
  database migration is introduced.

### Progress

- [x] Repair Gateway and legacy validation guard.
- [x] Replace broad security bypasses with exact route contracts.
- [x] Finish JWKS/rotation/WebSocket behavior and focused tests.
- [x] Run compilation, focused tests, and repository validation; record result.
- [x] Make trusted-proxy forwarding fail closed by default and prove that only a
  direct peer in the configured CIDR allow-list can supply X-Forwarded-For.
- [x] Publish/edit system architecture Mermaid source and align current product,
  workflow, decision and inventory material with the JWKS boundary.
- [x] Add a local/staging Compose rollout runner with separate Wave 1, fresh
  token verification, Wave 2 and Wave 3 gates. It refuses to shorten the
  15-minute access-token plus five-minute skew buffer and records no secret or
  token material.
- [x] Add a legacy-Compose bootstrap helper. It discovers the last pre-JWKS
  release, creates a separate Git worktree, symlinks only the local ignored
  secret paths, and refuses to overwrite an existing Compose project.
- [x] Execute the guarded local/staging Compose rollout through Wave 1, the
  mandatory token buffer, Wave 2 and the final Gateway cutover.

### Decisions

- 2026-08-08: Keep analytics and settlement self-service disabled rather than
  invent ownership policy. The system-of-record classifies those capabilities
  as hidden pending ownership proof.
- 2026-08-08: Follow the existing plan's internal contracts: Auth-to-User block
  sync uses `/api/internal/users/{id}/block-status`; Order-to-Promotion uses
  `/api/promotions/internal/**`.
- 2026-08-08: Gateway is now a routing/rate-limit boundary only. It strips
  legacy identity headers but does not validate JWTs, mount JWT public keys, or
  inject identity headers. This lasting choice is recorded in
  `backend_delivery/docs/decisions/0001-jwks-resource-server-authentication.md`.
- 2026-08-08: Trusted proxy use needs both `RATE_LIMIT_TRUSTED_PROXY=true` and a
  non-empty `RATE_LIMIT_TRUSTED_PROXY_CIDRS` allow-list. A private peer that is
  outside the configured CIDRs must not supply the rate-limit identity.
- 2026-08-08: The system-level editable diagram is `docs/ARCHITECTURE.md`; it
  documents client-only Gateway access, two-step registration, JWKS validation,
  event/data ownership and COD/realtime mechanisms. The stale Payment Service
  order workflow has been replaced with the current COD-first flow.

### Validation

- PASS: `mvn -q -DskipTests test-compile` (all backend modules).
- PASS: `mvn -q -pl auth-resource-server-starter -am test` (issuer, audience,
  access token type, `kid`, RS256 validator, rotation-kid and converter tests).
- PASS: focused Auth, User, Order/Promotion, Tracking and Gateway tests,
  including `TokenServiceKeyPreflightTest`, `AuthEndpointSecurityTest`,
  `AuthServiceSecurityTest`, `InternalUserBlockStatusControllerTest`,
  `CheckoutReservationClientContractTest`, `PromotionControllerAuthorizationTest`,
  `WebSocketConfigAuthenticationTest`, `GatewayRouteSecurityTest`, and
  `GatewayRateLimitFilterTest`.
- PASS: `mvn -q -pl api-gateway -am -Dtest=GatewayRateLimitFilterTest
  -Dsurefire.failIfNoSpecifiedTests=false test` — 10 tests, including configured
  CIDR acceptance, empty allow-list rejection, and an unlisted private-peer
  rejection of X-Forwarded-For.
- PASS: `JAVA_HOME=...openjdk@17... bash scripts/verify-build-baseline.sh`,
  `bash scripts/verify-http-api-inventory.sh`, `bash scripts/verify-secrets.sh`,
  `bash scripts/verify-compose-config.sh`, and `git diff --check`.
- PASS: documentation static checks for the Mermaid architecture/workflow source:
  balanced fence count and supported diagram type headers, existing local link
  targets, no obsolete current Gateway-JWT wording, no trailing whitespace, and
  scoped `git diff --check` for backend/client documentation files.
- PASS: `bash -n scripts/rollout-jwks-compose.sh`, `--help`, base64url decoder
  smoke, public-JWK overlap shape check, and a no-daemon negative gate. The
  runner intentionally fails before a Compose mutation when Docker is absent.
- PASS: `bash -n scripts/bootstrap-jwks-legacy-compose.sh`, `--help`, dynamic
  pre-JWKS ref discovery (`3418e47`), and no-daemon negative gate.
- Environment limitation: the full Gateway `ActuatorProbeEndpointTest` needs a
  loopback Netty port. This workspace sandbox rejects socket bind with
  `Operation not permitted`; the approval policy rejected escalation. The
  focused non-listening Gateway tests and static baseline passed. Re-run the
  full clean Gateway test in CI/a normal local runtime before deployment.

### Deployment Execution (2026-08-08)

- Wave 1 on revision `55ec56b` passed: Auth was healthy and exposed a public,
  private-material-free RS256 JWKS. The legacy Gateway remained on the
  pre-cutover image and returned `404` for the JWKS route as required.
- After the mandatory buffer, the first Wave 2 attempt stopped at the original
  180-second per-service deadline while `user-service` was still starting; it
  became healthy after roughly six minutes. Runtime inspection then found three
  actual pre-cutover blockers: Analytics and Livestream were missing the
  Config-Server/registry Compose environment, Flash Sale had lost its Boot
  repackage plugin, and Search's typed Elasticsearch health contributor could
  not deserialize a 7.17 cluster-health response using the Boot-managed 8.x
  client even though direct repository queries succeeded.
- Corrective revision `75a6e59` restores the Flash Sale executable artifact,
  adds the platform environment and direct-run Config-Server defaults to
  Analytics/Livestream, replaces only the incompatible Search health probe with
  a low-level `HEAD /` availability probe, and serializes Wave 2 replacement
  with a ten-minute readiness deadline. Focused module tests, Compose contract
  rendering, an Elasticsearch `HEAD` probe, and executable-JAR inspection pass.
- The failed state file was archived outside the repository. Wave 1 was then
  restarted on `75a6e59` and passed at 2026-08-08T08:42:29Z. A new access token
  was obtained through the still-legacy Gateway and passed `verify-token`.
  Gateway briefly selected the retired Docker hostname after Auth replacement;
  Eureka refreshed to the new `UP` instance without recreating or upgrading the
  Gateway.
- The subsequent Wave 2 on `75a6e59` rebuilt and sequentially brought User,
  Restaurant, Order, Delivery, Search, Shipper, Settlement, Notification,
  Match and Tracking to readiness. Search's replacement probe was healthy
  against Elasticsearch 7.17. Livestream then stopped fail-closed during its
  Flyway transaction because a pre-Flyway `livestreams` table was missing
  `view_count`.
- Database inspection proved the Livestream, product and event tables each had
  zero rows and Flyway remained at baseline version `0`; its failed transaction
  was rolled back. Revision `f99099c` adds only the safe repair for that exact
  empty-table case (`view_count BIGINT NOT NULL DEFAULT 0`), retains a
  fail-closed path for populated tables or any other missing column, proves both
  cases in `LivestreamFlywayMigrationTest`, and makes the runner see an exited
  container immediately rather than wait the full readiness timeout.
- Wave 1 was restarted on `f99099c`, passed, and held the full 1,200-second
  access-token TTL plus skew buffer. A post-buffer token was minted and
  verified through the legacy Gateway. The first final-revision Wave 2 proved
  the Livestream repair on PostgreSQL but stopped immediately when the existing
  volume lacked `analytics_db`.
- `analytics_db` is an expected empty database in `docker/postgres/init-db.sql`,
  the Compose contract and the backup inventory; its init script had run before
  that database was added to the long-lived local volume. It was created without
  modifying existing databases or volumes, Analytics migrated successfully, a
  fresh token was verified, and Wave 2 was rerun on the same pinned revision.
- The rerun completed all 14 resource-service readiness gates and the
  authenticated `/api/users` smoke through the still-legacy Gateway. Wave 3
  then completed successfully. The final runner state is `phase=wave3` on
  `f99099c`; Gateway and all 14 resource services are healthy, the public
  Gateway JWKS is RSA/RS256-only with no private-key fields, and the Gateway
  JWKS-authenticated user smoke passed.

### Deployment Result

The local/staging Compose JWKS cutover is complete. No legacy-token fallback was
added, and no existing volume or application data was deleted. Production still
requires its separate immutable-image, canary, metrics/SLO and rollback
procedure from the main runbook; this Compose rehearsal is not production
evidence.
