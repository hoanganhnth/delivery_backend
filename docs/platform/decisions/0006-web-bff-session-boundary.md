# 0006 Web BFF Session Boundary

Date: 2026-09-15

## Status

Accepted

## Context

The React Web portals previously stored Auth access and refresh tokens in
browser local storage. That made bearer credentials readable by any script on
the origin and coupled every page to refresh-token rotation. The Flutter and
React Native apps still need bearer access for native APIs and sockets, but can
use operating-system protected storage.

## Decision

- Browser login, refresh, logout and protected API calls go through
  `/bff/session/**` and `/bff/api/**` at the API Gateway.
- The BFF stores encrypted access/refresh tokens in PostgreSQL. The browser gets
  only the opaque `__Host-delivery-session` cookie (`Secure`, `HttpOnly`,
  `SameSite=Lax`, `Path=/`) plus the readable `XSRF-TOKEN` double-submit cookie.
- Session IDs and CSRF values are stored only as SHA-256 hashes. Token ciphers
  use AES-256-GCM with key versioning and associated data; the encryption key is
  an operator-owned file-backed secret.
- Every BFF mutation requires an exact allowed Origin and valid CSRF value.
  Browser Authorization/Cookie/Host headers are never forwarded. Protected
  resources use a closed allowlist and all internal/credential endpoints are
  denied.
- Refresh is claimed with a database generation/lease so only one BFF instance
  rotates a token. Unknown refresh outcomes revoke the local session and require
  login.
- Web deployment and rollback treat BFF and Web as a compatible pair. Rollback
  must never restore plaintext browser token storage. Mobile API contracts stay
  bearer-based, with credentials in Keychain/Keystore-backed storage.

## Alternatives Considered

1. Keep browser tokens in local storage with shorter TTL: simpler, but scripts
   can still read refresh credentials and cross-tab refresh races remain.
2. Put only refresh tokens in an HttpOnly cookie: reduces exposure but leaves
   access-token lifecycle and CSRF/security policy split across browser code and
   Auth.
3. Change all clients to BFF sessions: unnecessary contract churn for native
   apps and incompatible with current native socket/API adapters.

## Consequences

Positive:

- Browser JavaScript can no longer read Delivery bearer credentials.
- Refresh concurrency, revocation and proxy policy have one server authority.
- Future Web features use the same stable session boundary without duplicating
  token storage or interceptors.

Tradeoffs:

- Web availability now depends on the BFF database and encryption key.
- A failed upstream logout still leaves the BFF session revoked, but the Auth
  refresh token can remain valid until expiry; HTTP error metrics/logs must be
  monitored and a durable revocation retry can be added if operational evidence
  warrants it.
- One-time re-login is required when migrating from the old browser release.

## Follow-Up

- Provision and rotate the AES key through the approved secret manager; retain
  old key versions until all corresponding sessions expire or are revoked.
- Run staging login/refresh/logout against a real Auth account before production
  rollout and alert on BFF `http.server.requests` 4xx/5xx rates by URI/status.
- Roll back Web and BFF together; keep the BFF database intact until rollback
  verification is complete.
