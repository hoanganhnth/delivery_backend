# Execution Plan: Task 19 Refresh-Token Rotation And Revocation

Date: 2026-07-30

## Status

Completed

## Outcome

Every successful refresh rotates to a unique token, replay of any consumed token
revokes only its device session family, concurrent refresh cannot mint parallel
valid descendants, and Web, Flutter, and Shipper clients coordinate one refresh
and one retry without logout loops or request storms.

## Context

- User-provided Task 19 is product authority for rotation, reuse detection,
  family revocation, independent devices, race protection, and client behavior.
- `backend_delivery/docs/services/auth_and_users.md` and
  `backend_delivery/docs/http-api-inventory.md` own the existing public paths,
  BaseResponse envelope, 401 invalid-session contract, seven-day refresh session,
  and accepted 15-minute stateless access-token window.
- Auth currently overwrites `auth_session.refresh_token` under a pessimistic lock.
  Rotation occurs, but the old token row disappears, so reuse cannot be
  distinguished from an unknown token and cannot revoke a family.
- Web, Flutter, and Shipper already contain partial single-flight interceptors;
  their terminal-error, required rotated-token, storage ordering, and regression
  coverage are not yet aligned.

## Scope

In scope:

- Auth refresh-token family/history schema, hashed token lookup, rotation,
  replay detection, family/device revocation, and database race protection.
- Existing `/api/auth/refresh-token`, `/api/auth/logout`, and session-management
  contracts, plus authenticated per-device revocation.
- Single-flight refresh, one-time retry, rotated-token persistence, and one-shot
  session-expired behavior in Web, Flutter, and Shipper clients.
- Redaction/audit proving refresh and access tokens are not logged.

Out of scope:

- Immediate invalidation/introspection of already-issued access JWTs; the
  documented 15-minute bounded stateless window remains unchanged.
- Account-wide "log out everywhere", refresh cookies, or changing the public
  BaseResponse envelope.

## Approach

1. Add a Flyway migration for a per-session family ID and a refresh-token
   history table containing only SHA-256 token fingerprints and lifecycle state.
   Migrate existing session tokens into current history rows, then remove raw
   token values from the session table.
2. Issue refresh JWTs with unique `jti`, refresh token type, and family claim.
   Lock the history row during refresh; rotate current -> consumed and insert one
   successor. A consumed-token request revokes the session and every token row in
   that family in a transaction that commits before returning 401.
3. Keep device sessions independent. Login replaces only the same device;
   logout and authenticated device revoke terminate only the selected family.
4. Align clients on a required `{accessToken, refreshToken}` refresh response,
   one shared in-flight refresh, rotated-token-first/combined persistence, one
   retry per protected request, and one terminal session-expired notification.
5. Add focused migration, single/concurrent/reuse/logout/device/access-expiry
   tests and client concurrency/contract regressions, then run repository gates.

## Risks And Recovery

- Throwing a reuse exception normally rolls back revocation. Use a dedicated
  exception with explicit no-rollback transaction semantics and prove persisted
  family revocation after the 401 path.
- A migration bug could invalidate every active login. Migration proof must cover
  clean and legacy schemas and preserve a legacy active token as a current hash.
  Rollback uses the prior image plus database backup; the additive history table
  and session family column do not alter account identity data.
- Partial client storage can retain a consumed refresh token. Persist the rotated
  refresh token before exposing the new access token, or use the platform's
  combined storage primitive where available.
- Legitimate concurrent refresh is intentionally serialized; the first rotates,
  and replay of the consumed token revokes that device family. Client single-
  flight prevents ordinary clients from creating this security event.

## Progress

- [x] Audit current backend session persistence and all three client interceptors.
- [x] Implement migration, token history/family domain, rotation, reuse detection,
  logout/device revoke, and race-safe backend tests.
- [x] Align Web single-flight and terminal session behavior with rotated tokens.
- [x] Align Flutter single-flight and terminal session behavior with rotated tokens.
- [x] Align Shipper single-flight and terminal session behavior with rotated tokens.
- [x] Run focused and repository validation; audit all Task 19 requirements.

## Decisions

- 2026-07-30: Preserve existing HTTP paths, BaseResponse shape, invalid-token
  401, seven-day sliding session, and 15-minute stateless access-token window.
- 2026-07-30: A login/device session is the token-family boundary. Reuse revokes
  that family only, so a compromised phone cannot terminate another device.
- 2026-07-30: Persist only SHA-256 refresh-token fingerprints in token history;
  raw bearer tokens remain only in transit and client secure/session storage.
- 2026-07-30: Consumed-token replay, including a second concurrent refresh,
  revokes the family. Security takes precedence over returning parallel tokens;
  supported clients prevent legitimate duplicate refresh with single-flight.
- 2026-07-31: Mark every queued Web/Shipper request as already retried before it
  waits on the shared refresh. Terminal 401 handling is latched before storage
  cleanup, so simultaneous rejected retries clear/notify once; a later login
  resets the latch when the client observes a different access token.

## Validation

- Focused proof: Auth clean/legacy Flyway migration; single rotation; concurrent
  refresh; consumed-token reuse and committed family revocation; logout and
  per-device revocation; access-token expiry/refresh response; token-log scan.
- Integration or end-to-end proof: Auth persistence tests with real transactions
  and row locks; each client receives concurrent 401s, performs one refresh,
  stores both rotated tokens, retries each request once, and emits one terminal
  session-expired event on revoked refresh.
- Repository-required checks: backend Auth test plus baseline/inventory; Web
  `npm run verify`; Flutter focused test plus `fvm flutter analyze/test`; Shipper
  `npm run verify`.

Final evidence:

- Auth clean suite passed 79 tests with zero failures/errors/skips, including
  four real-transaction rotation tests
  for single refresh, consumed-token reuse with committed revocation, concurrent
  refresh serialization, and independent logout/device revoke. Clean/legacy
  Flyway and Hibernate schema validation passed; every Task 19 Auth route has a
  matching inventory row and documented policy.
- Web `npm run verify` passed 37 tests, lint, action contract, and production
  build. Its regressions prove one refresh for simultaneous 401s and exactly one
  retry/cleanup/session-expired notification when every queued retry is rejected.
- Flutter isolated Auth tests passed: interceptor/repository/data-source 28 tests
  plus Auth notifier 12 tests. They cover queued single-flight, rotated pair,
  retry termination, server logout before local cleanup, and no auth-route loop.
  Full `flutter test` passed 209 tests and `flutter analyze` reported no issues.
- Shipper `npm run verify` passed typecheck, lint, and 31 suites / 116 tests. Its
  refresh policy now has the same queued terminal-401 proof as Web.
- Source/log audit found no access/refresh bearer value in backend, Web, Flutter,
  or Shipper log calls. Flutter's network logger records only method, sanitized
  URI without query/fragment, status, and Dio error type; it never logs headers
  or bodies. Manual tracked-source credential scan found no key/API credential.
- Backend Compose configuration contract passed. Aggregate clean Maven execution
  passed Auth and every module except two unrelated pre-existing Saga integration
  fixtures that omit the now-required inbound `eventId`; all modules after Saga
  were resumed and passed cleanly.
- Repository-wide baseline/inventory/secret scripts are not claimed green:
  concurrently active Order changes made two Surefire reports stale, the newly
  added Flashsale `/internal/quote` mapping has no inventory row yet, and
  `verify-secrets.sh` matches its own literal `-----BEGIN` sentinel. These are
  outside Task 19 and do not weaken its focused Auth/client/security evidence.

## Result

Refresh tokens now rotate to unique hashed-history successors under a pessimistic
row lock. Reuse, including a second concurrent refresh, commits revocation of
only that device family before returning 401. Logout and authenticated device
revoke preserve other device families, while already-issued access tokens retain
the documented 15-minute stateless window.

Web, Flutter, and Shipper require and persist the rotated token pair, share one
refresh across concurrent failures, retry each protected request once, and emit
one terminal session-expired transition without recursive auth/logout requests.
Task 19 is complete with the evidence above; the unrelated repository-wide gate
drift remains explicitly recorded rather than being misreported as Task 19
validation.
