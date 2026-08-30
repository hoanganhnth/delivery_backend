# Task 6A report — client-first entitlement boundary

## Outcome

Added the Flutter-side IAP entitlement boundary before backend implementation.
The client has explicit iOS and Android adapter ports, but both remain
unavailable until native store wiring and the backend verification contract are
provided. Receipt submission cannot grant local access.

## Commit

- `delivery_app`: `e7cfe02` — `feat(entitlements): add fail-closed client boundary`

## Changed surfaces

- `EntitlementSnapshot` states: unavailable, pending, active, revoked.
- Receipt validation rejects empty product, transaction, or signed payload.
- `EntitlementCoordinator` returns pending when backend is unavailable and only
  accepts active access from the repository/server snapshot.
- Explicit `IosIapAdapter` and `AndroidIapAdapter` ports fail closed for now.
- `IAP_ENTITLEMENTS_ENABLED` defaults to `false`.

## Verification

- Entitlement and adapter tests: 6 passed.
- Dart analyzer: clean for entitlement files and runtime config.
- `git diff --check`: clean.

## Backend follow-up

Task 6B still needs an authoritative product catalog, Apple/Google receipt
verification policy, durable receipt idempotency, entitlement projection and
exact Gateway contract before this feature can be enabled.
