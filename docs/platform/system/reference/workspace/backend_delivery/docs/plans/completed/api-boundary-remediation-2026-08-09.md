# Execution Plan: API boundary remediation

Date: 2026-08-09

## Status

Completed — implementation, regression proof, and static contract validation
passed on 2026-08-09.

## Outcome

Restore the documented device-session revoke route, allow a restaurant owner to
subscribe to an owned active delivery's location, remove the dormant User
path-identity read handler, and make a shipper's explicit offline action reach
the Tracking/Match availability authority before its profile projection changes.

## Context

- `docs/http-api-inventory.md`
- `docs/services/auth_and_users.md`
- `docs/services/tracking_service.md`
- `../docs/plans/active/priority-roadmap.md` — Phase 4 explicitly makes
  Tracking/Redis heartbeat the matching authority; `shipper-service.isOnline`
  is only a profile/read-model.

## Scope

In scope:

- Exact Gateway route and regression test for `DELETE /api/auth/sessions/{deviceId}`.
- `SHOP_OWNER` participant authorization in Delivery's internal tracking check.
- Removal of the unreachable, misleading `GET /api/users/{id}` mapping and its
  API-inventory row.
- A credential-protected, non-Gateway Tracking offline command and Shipper
  caller for the existing status endpoint.
- Preventing generic profile updates from mutating `isOnline`.

Out of scope:

- Changing the Shipper mobile API path or its DTO response shape.
- Declaring a fresh GPS heartbeat to be available for matching. A true profile
  intent still requires Tracking to receive a valid location before Match can
  select the shipper.
- Reworking the client/admin read model for effective live availability.

## Approach

1. Restore exact Gateway and participant authorization contracts with focused
   tests.
2. Keep `PATCH /api/shippers/online-status` compatible. On `false`, Shipper
   calls Tracking internally with `Internal-Token`, which publishes an offline
   tombstone to Match before Shipper persists its profile projection. On `true`,
   Shipper records publisher intent; the next authenticated GPS heartbeat is
   still the only path that makes Match eligibility true.
3. Reject `isOnline` in the generic profile update DTO so it cannot bypass the
   dedicated status boundary.
4. Update the inventory/docs and run focused module tests plus static contract
   verification.

## Risks And Recovery

- The new internal call can fail while Tracking is unavailable. It is
  fail-closed: the profile projection is not saved as offline if its canonical
  tombstone could not be accepted. Retrying the existing status endpoint is
  safe.
- A Tracking tombstone can succeed before a subsequent profile database write
  fails. This is safe for matching (the shipper is not offered work); retrying
  the endpoint reconciles the profile projection.
- The removed User endpoint has no Gateway route or polyrepo consumer. Restore
  the single handler if a verified compatibility consumer emerges.

## Progress

- [x] Confirm authority, current call sites, and uncommitted-work boundaries.
- [x] Implement API routing, authorization, and legacy-handler corrections.
- [x] Implement offline convergence with Tracking.
- [x] Update docs and validate focused behavior.

## Decisions

- 2026-08-09: Preserve the existing Shipper status endpoint for the mobile
  client. `isOnline=true` is publisher intent, not an assertion that the
  shipper is matchable; Match continues to require a fresh Tracking heartbeat.
- 2026-08-09: `isOnline=false` must synchronously reach Tracking so Match gets
  a timestamped offline tombstone before the profile projection reports offline.

## Validation

- Boundary regression suite: Gateway route predicates; Delivery participant
  authorization; Tracking credential guard; Shipper offline-call ordering,
  client failure handling and generic profile-update rejection; User internal
  authorization; and Search security policy. `mvn -pl
  api-gateway,delivery-service,tracking-service,shipper-service,user-service,search-service
  -Dtest=... test` passed 50 tests with 0 failures/errors/skips.
- Saga clean compilation and suite: `mvn -pl saga-orchestrator-service clean
  test` passed 67 tests with 0 failures/errors/skips. This confirms the
  previously reported `modifiedEvent` compilation symptom is absent in a clean
  build.
- `bash scripts/verify-http-api-inventory.sh` — pass; 166 mapped controller
  methods are aligned with the inventory.
- `bash scripts/verify-compose-config.sh` — pass.
- `bash scripts/verify-kubernetes-manifests.sh` — pass; 58 generated manifests
  are current and both templates render successfully.
- `env JAVA_HOME=... bash scripts/verify-build-baseline.sh` — pass with JDK
  17, Spring Boot 3.5.15, and Spring Cloud 2025.0.3.
- `git diff --check` — pass.

## Result

Complete. The documented device-session Gateway route, restaurant-owner
participant check, and User self-only read boundary are restored. A shipper
offline action now converges through Tracking before its profile projection is
saved, while generic profile updates cannot bypass that authority.

The intentionally fail-closed offline call still requires a usable Tracking
service and `INTERNAL_SECRET`; retrying the existing status endpoint is the
recovery path. Marking a shipper online remains publisher intent and requires a
fresh authenticated location heartbeat before Match may select the shipper.
