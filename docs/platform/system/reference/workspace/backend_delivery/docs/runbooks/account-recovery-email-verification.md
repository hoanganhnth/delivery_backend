# Account Recovery And Email Verification

## Policy and provider

Auth Service sends security email directly through SMTP. Production uses AWS
SES SMTP with TLS; `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`
and `SECURITY_EMAIL_FROM` come from the deployment secret/config store. Do not
put provider credentials in source, Compose defaults, support tickets or logs.

- Password-reset token lifetime: 15 minutes.
- Email-verification token lifetime: 24 hours.
- Tokens contain 256 random bits, are URL-safe and one-time.
- Only SHA-256 token digests are stored in `auth_security_token`.
- Existing accounts are migration-grandfathered as verified. New password
  accounts must verify before password login. A verified Google identity marks
  the exact matching Auth Account email as verified, preserving social login.
- Public Auth endpoints use the existing Gateway Redis fixed-window policy: 10
  POST requests per 60 seconds per direct peer IP, fail-closed.

## Public flow

All endpoints are POST-only and exposed only through the explicit Gateway Auth
route:

| Endpoint | Body | Result |
|---|---|---|
| `/api/auth/forgot-password` | `{"email":"..."}` | Always `202` with the same body for eligible, missing or inactive accounts. |
| `/api/auth/reset-password` | `{"token":"...","newPassword":"..."}` | Consumes an unexpired reset token and revokes all account refresh sessions. |
| `/api/auth/email-verification/request` | `{"email":"..."}` | Always the same `202`; resends only for an eligible unverified account. |
| `/api/auth/email-verification/confirm` | `{"token":"..."}` | Verifies only the Auth Account owned by the token row. |

Issuance invalidates older unconsumed tokens for the same account and purpose.
Email delivery is dispatched asynchronously only after the token transaction
commits. The raw token exists in request/event memory and the email body, never
in persistence or application logs. A process restart before async delivery can
lose that email; the user can request again and the next issuance invalidates
the old token.

## Threat model and controls

- Enumeration: request status/body are uniform and SMTP latency is removed from
  the request path. Gateway quota limits probing. Never add account-specific
  messages, metrics labels or response headers.
- Database theft: stored digests cannot be used directly as bearer tokens;
  expiry and consumption are checked under a pessimistic row lock.
- Replay/concurrency: token consumption and password/verification mutation are
  one transaction. Reuse, concurrent reuse, wrong purpose and expiry return the
  same invalid-token response.
- Cross-account substitution: consume endpoints accept no email/account ID;
  the non-null token foreign key is the only account authority.
- Compromised sessions: password reset updates the BCrypt password, marks all
  refresh-token-family records revoked and deactivates every Auth Session in the
  same transaction. Stateless access JWTs can remain valid only until their
  documented maximum 15-minute expiry.
- Secret leakage: never log request DTOs, reset URLs, raw tokens, passwords or
  SMTP credentials. Security audit rows store account ID where known and
  SHA-256 hashes of email/client IP, never their raw values.

## Deployment and verification

1. Verify the SES sender/domain, leave the SES sandbox when production recipient
   volume requires it, and grant SMTP credentials only to Auth Service.
2. Set the SMTP/TLS variables and public frontend reset/verification URLs.
3. Apply Flyway migrations before routing the four new endpoints. Existing
   accounts must show `email_verification_required=false` and a non-null
   `email_verified_at`.
4. Send test messages to controlled accounts. Confirm that application logs and
   traces contain neither URL token.
5. Verify ten public Auth requests pass in a fixed window and the eleventh gets
   the standard `429` envelope; Redis outage remains fail-closed with `503`.
6. Confirm password reset makes every previous refresh token unusable while
   ordinary login, Google login, refresh rotation and logout remain healthy.

## Operations and incidents

- Monitor Gateway `gateway.rate_limit.rejected{group="public_auth"}` and Auth
  audit outcomes `DELIVERY_FAILED`, `EXPIRED`, `REUSED` and `WRONG_PURPOSE`.
- For SES outage, keep request responses uniform. Restore provider access and
  ask affected users to request a new token; never extract hashes or fabricate
  raw tokens from the database.
- For suspected token disclosure, disable the public Auth route temporarily,
  mark outstanding tokens consumed, rotate SMTP credentials if relevant, and
  inspect token-free audit rows. Do not revoke unrelated sessions unless the
  incident scope requires it.
- Cleanup defaults: expired token rows after 30 days and security audit rows
  after 180 days. Change retention only through reviewed configuration.
- Rollback is additive: disable the new Gateway routes/email transport but do
  not roll back applied migrations or change grandfathered verification state.
