# Execution Plan: Task 18 — Password Reset And Email Verification

Date: 2026-07-30

## Status

Completed — implementation, migration, security audit and repository validation
passed on 2026-07-30.

## Outcome

Auth Service provides enumeration-resistant forgot/reset-password and
account-bound email verification flows using one-time expiring tokens whose raw
values are never persisted or logged. Password reset revokes every Auth Session
and refresh-token family, sensitive operations are auditable, and existing
password/social login contracts remain compatible with the approved
verification policy.

## Decisions And Authority

- Auth Service sends security email directly over SMTP; production uses AWS SES
  SMTP credentials from the deployment secret store.
- Password-reset tokens expire after 15 minutes. Email-verification tokens
  expire after 24 hours.
- New password accounts must verify before login. Migration-grandfathered
  accounts remain verified, while a Google identity with `verified_email=true`
  verifies the exact matching Auth Account.
- The four recovery/verification endpoints are POST-only and use the existing
  Gateway Redis fixed window: 10 requests per 60 seconds per direct peer IP,
  fail-closed.
- Password reset revokes all Auth Sessions and refresh-token-family records in
  the password-update transaction. Already-issued stateless access JWTs retain
  the existing maximum 15-minute lifetime.
- Expired token rows are retained for 30 days and security audit rows for 180
  days by default, with scheduled cleanup.

## Threat Model And Controls

- Account enumeration: request endpoints return the same status/body for
  existing, absent, inactive and already-verified/ineligible accounts. SMTP is
  dispatched asynchronously after commit, outside request latency.
- Token theft/database disclosure: each token has 256 random bits; only its
  SHA-256 digest, owning account, purpose, expiry and consumption metadata are
  persisted.
- Replay/race: consumption uses a pessimistic row lock and commits the consumed
  marker with the password/verification state change. Reuse, wrong purpose and
  expiry fail with the same public invalid-token response.
- Cross-account substitution: consume endpoints accept neither account ID nor
  email; the non-null token foreign key is the account authority.
- Reset abuse: Gateway applies the approved public-auth IP quota. Reissue marks
  every older unconsumed token for that account/purpose consumed.
- Session persistence after compromise: password update, refresh-family
  revocation and Auth Session deactivation are atomic.
- Secret leakage: request DTOs, reset URLs, raw tokens, passwords and SMTP
  credentials are not logged. Audit stores hashed email/IP. The in-memory email
  event also redacts all sensitive fields from its string representation.

## Implemented Surfaces

- Flyway V4 adds verification state, account-bound security tokens, token-free
  audit rows and cleanup/query indexes while grandfathering existing accounts.
- `AccountSecurityService` implements issuance, digest lookup, one-time consume,
  verification, password reset, account-wide revocation and retention cleanup.
- `SecurityEmailSender` and the SMTP implementation send reset/verification
  links through a transaction-after-commit async event listener.
- Auth Controller, Spring Security and Gateway expose only the four exact POST
  paths. Forgot/request-verification return the same `202` envelope.
- Compose and `.env.example` contain only environment-backed SES SMTP settings;
  no provider credential is hard-coded.
- The operator runbook and Auth/API/system inventories document rollout,
  incident response, session semantics and safe recovery.

## Recovery

- Endpoint rollout is additive. If email delivery is unhealthy, disable the
  public routes/transport while leaving login, registration, social login and
  refresh routes unchanged.
- Do not roll back an applied migration. Disable issuance, retain token/audit
  rows through their cleanup window and use a forward migration for correction.
- A process loss after commit but before async SMTP delivery can lose one email;
  the user requests another and issuance invalidates the earlier token.

## Progress

- [x] Approve provider, TTLs, verification enforcement and session semantics.
- [x] Implement persistence, service/API/provider and Gateway routing.
- [x] Add migration, service, endpoint, provider and rate-limit tests.
- [x] Add threat model, deployment/incident runbook and contract documentation.
- [x] Audit raw-token handling, Compose config and repository diff.

## Validation

- `auth-service`: `mvn clean test` — 78 tests, 0 failures/errors/skips.
- Security flow integration — 5 tests covering existing/missing email, expiry,
  replay, wrong purpose, account binding, password/session/refresh revocation
  and no raw token in persistence/audit.
- SMTP provider — 2 tests; Auth endpoint security — 7 tests; clean/legacy/JPA
  migration coverage is included in the full Auth suite.
- `api-gateway` with Config Server disabled: route-security and rate-limit suites
  — 19 tests, 0 failures/errors/skips.
- `scripts/verify-compose-config.sh` — pass.
- `git diff --check` — pass.
- Source audit found no application log statement that receives a recovery
  token, reset URL or password; event string redaction is executable-tested.

No real AWS SES message was sent because production SMTP credentials are not
present in the workspace. The runbook requires a controlled-recipient delivery
check and log/trace inspection during environment rollout.

## Result

Task 18 is complete in the repository. The security flow and migration are
executable-tested, Gateway quota/routing is verified, and the remaining SES
send is an environment deployment check rather than missing application logic.
