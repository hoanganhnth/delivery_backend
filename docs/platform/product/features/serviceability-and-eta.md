# Feature: Serviceability polygon và ETA window

> Trạng thái: code-gated, mặc định tắt (`RESTAURANT_SERVICEABILITY_ENABLED=false`,
> `ORDER_SERVICEABILITY_ENFORCEMENT_ENABLED=false`, `ORDER_ETA_WINDOW_ENABLED=false`)
> · Cập nhật: 2026-08-23

## Authority

- `restaurant-service` owns restaurant delivery zones and the restaurant-owned
  default preparation estimate (1–240 minutes, default 30 only at migration).
- `order-service` owns the checkout quote and must consume the internal
  serviceability decision; it never reads zone data or infers coverage locally.
- `routing-service` owns driving duration. Public ETA is an additive range:
  `ceil(drivingSeconds / 60) + prepMinutes` through that lower bound plus 10
  minutes. The routing source (`MAPBOX_DIRECTIONS` or
  `GEODESIC_FALLBACK`) is returned explicitly.

## Geometry contract

Admin writes one closed WGS84 GeoJSON `Polygon` outer ring in v1. Holes,
self-zero-area rings, non-finite values and vertices outside latitude `[8,24]`
and longitude `[102,110]` are rejected. A coordinate on an edge is included.
Overlapping active zones resolve by descending `priority`, then ascending zone
ID. Invalid persisted geometry makes the decision fail closed.

## HTTP surfaces

| Surface | Actor | Contract |
| --- | --- | --- |
| `GET/POST /api/restaurants/{restaurantId}/serviceability-zones` | `ADMIN` or owning `SHOP_OWNER` | list/create staged polygons |
| `PUT/DELETE /api/restaurants/{restaurantId}/serviceability-zones/{zoneId}` | `ADMIN` or owning `SHOP_OWNER` | optimistic-versioned zone mutation |
| `GET /api/restaurants/internal/{restaurantId}/serviceability` | Order credential | returns enabled/serviceable/zone revision; raw polygon is never returned |
| `POST /internal/routing/v1/eta-window` | internal credential | returns `minMinutes`, `maxMinutes`, and route source |

When both customer-facing flags are enabled, checkout sends delivery
coordinates to Restaurant validation, rejects an unavailable address, and
returns `etaMinMinutes`, `etaMaxMinutes`, `etaSource`, and the matched zone
revision. With either flag off, existing checkout behavior and response
semantics remain unchanged.

## Failure and rollout boundary

- Missing internal credential, malformed decision, missing canonical prep time
  or routing failure is retryable/fail-closed; no guessed zone or provider
  success is returned.
- Zone data can be staged while the capability is off. Enable evaluation in
  Restaurant first, then checkout enforcement and ETA after cache/route
  rehearsal. Disable the flags to roll back; retain additive rows and revisions.
- Current proof is pure geometry/service tests, Flyway/H2 schema validation,
  routing fallback unit proof and Gateway path/method coverage. PostGIS, Mapbox
  provider, load, staging and device evidence remain release follow-ups.
