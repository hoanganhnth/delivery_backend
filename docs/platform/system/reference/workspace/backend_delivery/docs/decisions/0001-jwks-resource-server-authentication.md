# 0001 JWKS Resource-Server Authentication

Date: 2026-08-08

## Status

Accepted

## Context

Gateway JWT termination and `X-User-Id`/`X-Role` injection made every service
depend on a trusted network hop, concentrated key handling at the edge, and
left service-to-service `Internal-Token` calls vulnerable to Spring Security
rejecting them before their controller credential check.

## Decision

- Auth is the RS256 issuer and publishes public verification keys at
  `GET /.well-known/jwks.json`; keys carry a required `kid`.
- Each HTTP resource service uses the shared resource-server starter to validate
  issuer, audience, `kid`, `token_type=access`, and RS256 before constructing
  `AuthenticatedActor` from the token claims.
- Gateway only routes, applies peer-IP rate limits, and strips obsolete inbound
  identity headers. It does not mount public JWT keys, validate JWTs, or inject
  identity headers.
- Internal endpoints have exact method/path security allow-lists and still
  validate `Internal-Token` in their controller boundary. Auth-to-User block
  projection uses `POST /api/internal/users/{id}/block-status`; Order-to-
  Promotion checkout uses `/api/promotions/internal/**`.
- Key rotation requires the explicit previous `JWT_RETIRING_KID`; resource
  rollout follows the 15-minute access-token buffer plus skew before Gateway
  final cutover.

## Alternatives Considered

1. Keep Gateway as the sole JWT verifier and continue header injection.
2. Forward the client bearer token to every internal command instead of using
   an internal service credential.

## Consequences

Positive:

- Services can enforce authorization at their own boundary and independently
  refresh public verification keys through JWKS.
- Gateway no longer holds JWT public-key configuration or contains business
  authorization filters.
- Internal contracts are explicit and can be restricted without accidentally
  exposing a broad `/**/internal/**` bypass.

Tradeoffs:

- Each resource service needs JWKS reachability/cache availability.
- Deployment order matters: old access tokens without `kid` are intentionally
  rejected after the documented buffer rather than silently accepted.

## Follow-Up

- Operate rotation according to `docs/runbooks/secrets-management.md`.
- Keep analytics, settlement self-service, payment, and livestream endpoints
  disabled until their separate ownership policies are decided.
