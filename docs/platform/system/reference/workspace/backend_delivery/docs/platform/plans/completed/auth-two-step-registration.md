# Execution Plan: Two-Step Client Registration

Date: 2026-07-31

## Status

Completed

## Outcome

Customer password registration is completed by two explicit client requests:
Auth creates/resumes the credential identity and returns a short-lived handoff;
User verifies that handoff and creates/resumes the profile. Auth no longer calls
User as part of the public `POST /api/auth/register` request.

## Context

- User request on 2026-07-31 is authority for splitting public registration into
  separate client calls to Auth and User.
- `backend_delivery/docs/services/auth_and_users.md` makes Auth authoritative for
  immutable `authId/email/role`, requires linked User identity before login, and
  limits public registration to `USER`/`SHOP_OWNER`.
- Existing `AuthService.register` synchronously provisions User through the
  internal `POST /api/users`; Flutter currently sees this as one boolean API.
- Existing User provisioning is idempotent by `authId` and rejects email/role
  rebinding. Existing Auth security tokens are random, digest-only, expiring,
  account-bound, and retained for audit/retry behavior.
- Only `delivery_app` currently exposes customer password registration;
  `delivery_web` and `shipper_app2` have no public sign-up call to align.

## Scope

In scope:

- Public Auth registration response with immutable identity plus an opaque
  one-time User-provisioning handoff.
- Public User registration endpoint that accepts the handoff and profile fields,
  resolves identity through an internal Auth boundary, creates idempotently, and
  completes the Auth-to-User link.
- Gateway allow-list/rate-limit contract and focused backend security, retry,
  routing, and persistence tests.
- Flutter customer registration orchestration as exactly two API calls and use
  of the entered full name in the User profile request.
- Current contract/product documentation and inventory updates.

Out of scope:

- Social login and operator provisioning; these remain server-orchestrated
  because they are not the public password-registration flow requested here.
- Public SHIPPER/ADMIN self-registration.
- Automatic login before required email verification.

## Approach

1. Refactor Auth public registration to create/resume only `auth_account`, issue
   a 15-minute digest-only `USER_PROVISIONING` token, and return identity + raw
   handoff exactly once to the caller.
2. Add internal resolve/complete endpoints in Auth. Resolve validates the
   handoff and returns Auth-owned identity; complete atomically links the exact
   User ID and consumes the token. A lost response can be retried idempotently.
3. Add public `POST /api/users/registrations` in User. It sends only the opaque
   handoff to Auth, creates/resumes the profile using resolved identity, then
   completes the Auth link. Existing internal provisioning stays available for
   social/operator flows.
4. Route and rate-limit only the exact public User registration path.
5. Update Flutter registration DTOs/repository so one use-case invokes Auth then
   User, never trusting or rewriting Auth-owned identity.
6. Run focused backend tests, Flutter tests/analyze, contract scans, and update
   this plan with observed proof.

## Risks And Recovery

- Auth can succeed while User is unavailable. Mitigation: same-credential Auth
  retry issues a fresh handoff; User create is idempotent; link completion is
  idempotent. Recovery: resubmit registration within the handoff TTL or restart
  from Auth after expiry.
- User persistence can succeed before Auth link completion. Mitigation: User
  returns failure and the same request resumes the existing profile then retries
  the link; Auth login remains unavailable until linkage completes.
- A handoff is a bearer secret. Mitigation: 256-bit random value, SHA-256 digest
  at rest, account/purpose binding, 15-minute expiry, one-time completion, no
  logging, and exact Gateway route allow-list.
- Rollback: restore Auth orchestration and remove the exact public User route.
  Existing linked accounts remain valid; unlinked Auth/Profile rows can be
  resumed by the pre-change idempotent Auth provisioning path.

## Progress

- [x] Read system/backend workflow, product overview, Auth/User authority, code,
  routes, clients, and existing proof.
- [x] Confirm only Flutter customer app exposes the affected password sign-up.
- [x] Implement Auth handoff issuance, resolution, completion, and tests.
- [x] Implement User public registration orchestration and tests.
- [x] Update Gateway exact route/rate-limit contract and tests.
- [x] Update Flutter two-call registration and tests.
- [x] Update seed/runtime client and current contract documentation.
- [x] Run focused, module-wide, baseline, inventory, and polyrepo proof.

## Decisions

- 2026-07-31: The second client call carries an opaque handoff, not raw
  `authId/email/role`; otherwise the new anonymous User API would trust
  forgeable identity fields.
- 2026-07-31: The handoff TTL is 15 minutes, matching the platform's existing
  short-lived access/password-reset security window. A retry after expiry starts
  again at Auth and does not require cleanup of a partially created profile.
- 2026-07-31: Social login and operator fixture flows keep internal provisioning;
  the requested split applies to explicit public password registration only.
- 2026-07-31: User handoff uses the public-auth limit/fail-closed settings but a
  separate Redis bucket, so one logical registration does not consume Auth quota
  twice while both anonymous calls remain peer-IP limited.

## Validation

- Focused proof:
  - Auth/User/Gateway focused reactor run passed 55 relevant tests before the
    Gateway context was rerun with Config Client disabled; final Gateway route +
    rate-limit suite passed 20/20.
  - `mvn -pl auth-service,user-service clean test` passed Auth 84/84 and User
    38/38, including H2 persistence/migration, token digest/expiry/replay,
    internal credential, immutable identity, create idempotency, and callback
    linkage behavior.
  - `fvm flutter test test/features/auth` passed 72/72. Repository tests prove
    Auth is called first, User receives the returned opaque handoff/full name,
    User is not called after Auth failure, and no automatic login follows the
    email-verification registration flow.
- Integration or end-to-end proof:
  - `mvn -pl auth-service,user-service,api-gateway -am -DskipTests compile`
    passed all selected modules and shared dependencies.
  - HTTP inventory verifier aligned 160 mapped controller methods; polyrepo MVP
    contract gate passed exact-route and hidden-transport scans.
  - No live Compose registration/email-delivery smoke was run; service boundary
    behavior is covered by persistence integration tests plus mock HTTP contract
    tests, not a deployed multi-process transaction.
- Repository-required checks:
  - `bash scripts/verify-build-baseline.sh` passed JDK/Boot/Cloud, Actuator,
    source, and current Surefire evidence checks.
  - Flutter analyzer passed all changed auth source/tests with no issues.
  - `bash -n backend_delivery/scripts/seed.sh` and both repo `git diff --check`
    passed.

## Result

Public customer password registration now has two explicit client requests.
Auth owns credentials and the immutable identity/handoff; User owns profile
persistence and completes the link back to Auth. The flow is secure against raw
identity forgery, idempotent across lost responses, rate-limited without double
charging one logical registration, and implemented by Flutter plus the runtime
seed client. Social/operator behavior remains intentionally unchanged. A live
Compose/email smoke remains future runtime proof, not a known code failure.
