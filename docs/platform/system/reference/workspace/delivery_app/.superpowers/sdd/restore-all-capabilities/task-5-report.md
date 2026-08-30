# Task 5 report — Livestream viewer and host controls

## Outcome

Restored the customer livestream viewer boundary in Flutter and the minimum
restaurant host lifecycle surface in `delivery_web`. All client traffic uses
Gateway routes. Production capability flags remain disabled by default.

## Commits

- `delivery_app`: `f125e1b` — `feat(livestream): restore customer viewer boundary`
- `backend_delivery`: `1add1e8` — `feat(livestream): secure host lifecycle and gateway rollout`
- `delivery_web`: `35b2448` — `feat(livestream): add restaurant host controls`

## Changed surfaces

- Flutter MVVM/Clean Architecture viewer, server-issued join session parsing,
  retryable media-unavailable state, UUID route validation, and lifecycle
  disposal.
- Gateway client rollout flag `LIVESTREAM_CLIENT_API_ENABLED=false` and
  backend livestream route/authorization proof.
- Backend host ownership verification fails closed; caller-controlled stream
  token issuance is disabled.
- Restaurant web page supports inspect/create/start/end through the Gateway.
- No customer-app Admin placeholder or direct Agora token/channel inputs were
  restored.

## Verification

- Flutter focused suite: 12 tests passed.
- Web livestream suite: 4 tests passed.
- Backend livestream suite: 4 tests passed.
- Backend Gateway suite: 14 tests passed.

## Limitations and rollback

- `LIVESTREAM_CLIENT_API_ENABLED` and backend livestream rollout remain
  default-off; no production media rollout is claimed.
- The Flutter page exposes a media-unavailable state until the native Agora
  adapter is configured; it does not fake video.
- Roll back by disabling the capability flags first, then reverting the three
  task commits if required.
