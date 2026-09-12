# Livestream HTTP contract

This document is the canonical contract for the first livestream delivery
slice. The service is disabled unless `app.livestream.api-enabled=true` (the
workspace feature flag remains off by default). All routes require an
authenticated Bearer token unless noted otherwise. Responses use the existing
`BaseResponse<T>` envelope:

```json
{"status": 1, "message": "...", "data": {}}
```

`status=0` is an error envelope. Error HTTP status and the envelope are both
observable and must remain stable. Timestamps are ISO-8601 date-times and IDs
are UUIDs unless a field is explicitly numeric.

## Authority and roles

- `ADMIN` and `SHOP_OWNER` are the only host roles.
- A `SHOP_OWNER` may host only for a restaurant they own. The service must
  validate ownership and restaurant status; `restaurantId` in a request is not
  proof of ownership.
- Viewers must be authenticated customers/users. Anonymous join is not part of
  this contract.
- Agora is the only supported provider. `streamProvider` is `AGORA` on create
  and in every response. The Java enum currently contains `LIVEKIT` for
  legacy compatibility; lifecycle work must reject it rather than expose a
  second provider.
- A host supplies `priceAtLive` when pinning. Backend validation owns product
  ownership/status and must not trust copied product name/image/restaurant
  fields from the request.
- Multiple products may be pinned at once. `pinnedProducts` is an array and
  pin/unpin operations are per product.

## Current routes (implemented surface)

All paths below are rooted at `/api` through the gateway. The routes are
conditional on `app.livestream.api-enabled=true`.

### Create

`POST /api/livestreams` (ADMIN or SHOP_OWNER host)

Request:

```json
{"title":"Friday kitchen","description":"Optional description","restaurantId":42,"streamProvider":"AGORA"}
```

Required fields are non-blank `title`, positive `restaurantId`, and
`streamProvider=AGORA`. `description` is optional. The response data is a
`LivestreamResponse` (below) with `status=CREATED`.

### Start

`POST /api/livestreams/{id}/start` (ADMIN or owning SHOP_OWNER host)

No request body. Returns `StartLivestreamResponse`. The response includes the
host Agora token and is the only current host-token response:

```json
{"livestreamId":"00000000-0000-4000-8000-000000000001","channelName":"livestream-...","status":"LIVE","token":"<opaque Agora token>","uid":123,"role":"HOST","tokenExpiresAt":"2026-09-12T12:00:00Z","title":"Friday kitchen","restaurantId":42,"startedAt":"2026-09-12T11:00:00Z"}
```

### End

`POST /api/livestreams/{id}/end` (ADMIN or owning SHOP_OWNER host)

No request body. Returns the ended `LivestreamResponse` (`status=ENDED`).

### Inspect active and by identity

- `GET /api/livestreams/active` — authenticated viewer-facing list of active
  rooms; returns `LivestreamResponse[]`.
- `GET /api/livestreams/{id}` — room inspection; returns one
  `LivestreamResponse`.
- `GET /api/livestreams/seller/{sellerId}` — seller history/list;
  `LivestreamResponse[]`.
- `GET /api/livestreams/restaurant/{restaurantId}` — current implementation
  requires an authenticated host and ownership authorization, then returns
  `LivestreamResponse[]` for that restaurant.

### Join as viewer

`POST /api/livestreams/{id}/join` (authenticated viewer)

No request body. The room must be `LIVE`; the service returns
`JoinLivestreamResponse` with a viewer Agora token:

```json
{"livestreamId":"00000000-0000-4000-8000-000000000001","channelName":"livestream-...","title":"Friday kitchen","restaurantId":42,"token":"<opaque Agora token>","uid":456,"tokenExpiresAt":"2026-09-12T12:00:00Z","sellerId":7,"startedAt":"2026-09-12T11:00:00Z","currentViewers":0}
```

### Products

- `POST /api/livestreams/{id}/products/pin` (authenticated host)
- `DELETE /api/livestreams/{id}/products/{productId}/pin` (authenticated
  host)
- `DELETE /api/livestreams/{id}/products/{productId}` (authenticated host)
- `GET /api/livestreams/{id}/products` (authenticated caller)
- `GET /api/livestreams/{id}/products/pinned` (authenticated caller)

Pin request:

```json
{"productId":9001,"priceAtLive":99000,"productName":"optional legacy copy","productImage":"optional legacy copy","restaurantId":42,"restaurantName":"optional legacy copy"}
```

Only positive `productId` and positive `priceAtLive` are contract inputs. The
backend is authoritative for product existence, restaurant ownership, active
status, and canonical display fields; copied fields are not trusted. A
successful pin returns `LivestreamProductResponse`. Product lists may contain
more than one `isPinned=true` item.

### Disabled capability and failure cases

When the feature flag is false, routes are not registered (the gateway should
surface its normal disabled/not-found response). Once enabled, the service
uses these existing error mappings:

| Condition | HTTP | Envelope |
|---|---:|---|
| malformed request | 400 | `status=0`, field map, `Dữ liệu không hợp lệ` |
| missing room/product | 404 | `status=0`, message |
| invalid status transition (start/end/join) | 400 | `status=0`, message |
| unauthenticated/unauthorized host or viewer | 403 | `status=0`, message |
| duplicate pin | 409 | `status=0`, message |

## Schemas

### `LivestreamResponse`

`id: UUID`, `sellerId: long`, `restaurantId: long`, `title: string`,
`description: string|null`, `status: CREATED|LIVE|ENDED`,
`streamProvider: AGORA`, `roomId: string`, `channelName: string`,
`startedAt: datetime|null`, `endedAt: datetime|null`, `viewCount: long`,
`createdAt: datetime`, `updatedAt: datetime`,
`pinnedProducts: LivestreamProductResponse[]`.

### `LivestreamProductResponse`

`id: long`, `livestreamId: UUID`, `productId: long`, `productName: string`,
`productImage: string|null`, `restaurantId: long`, `restaurantName: string`,
`priceAtLive: decimal`, `isPinned: boolean`, `createdAt: datetime`,
`pinnedAt: datetime|null`.

## Explicit additions required before lifecycle/client work

These are not current routes and must not be called by clients yet:

1. **Admin moderation audit:** add one authenticated admin-only moderation
   mutation (the lifecycle task must choose the final path and action enum).
   Its request must carry `action` (`END` or `HIDE`), `reason`, and optional
   `targetUserId`; its response should be a normal `BaseResponse` containing
   `livestreamId`, `action`, `appliedAt`, and an audit identifier. The first
   pass writes an audit record and does not promise a realtime acknowledgement.
2. **Stable client error code:** lifecycle work should add a machine-readable
   error code alongside existing messages so web/Flutter can distinguish
   `LIVESTREAM_DISABLED`, `ROOM_NOT_FOUND`, `INVALID_STATUS`, and
   `OWNERSHIP_DENIED` without parsing Vietnamese text.
3. **Join/token policy:** `POST /{id}/join` remains the viewer token boundary;
   the currently exposed caller-controlled `POST /{id}/token` is deliberately
   disabled and must stay disabled unless a future contract replaces it.

Task 2 owns implementing and testing these additions. Task 3/4 may add typed
client methods only after the paths and response codes are finalized.
