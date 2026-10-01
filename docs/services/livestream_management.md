# Livestream HTTP contract

This document is the canonical contract for the first livestream delivery
slice. The service is disabled unless `app.livestream.api-enabled=true` (the
workspace feature flag remains off by default). All routes require an
authenticated Bearer token unless noted otherwise. Responses use the existing
`BaseResponse<T>` envelope. Successful responses retain the original shape:

```json
{"status": 1, "message": "...", "data": {}}
```

`status=0` is an error envelope. Lifecycle and ownership failures add an
optional machine-readable error member while preserving the existing message
and data fields:

```json
{"status":0,"message":"...","data":null,"error":{"code":"ROOM_NOT_FOUND","details":null}}
```

`error.details` is optional and currently null for livestream lifecycle errors.
Error HTTP status and the envelope are both observable and must remain stable.
Timestamps are ISO-8601 date-times and IDs are UUIDs unless a field is
explicitly numeric.

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

`POST /api/livestreams/{id}/end` (owning ADMIN or SHOP_OWNER host)

No request body. Returns the ended `LivestreamResponse` (`status=ENDED`).
An ADMIN ending another host's room must use moderation with a reason below.

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

### Renew an Agora token

`POST /api/livestreams/{id}/token/renew` (authenticated caller)

No request body. The room must still be `LIVE`. The server derives the UID,
role, and fixed 3600-second TTL from the authenticated actor; callers cannot
request a role, UID, or TTL. The owning ADMIN or SHOP_OWNER host receives a
`HOST` token. An authenticated customer or an ADMIN monitoring another host's
room receives a `VIEWER` token. The response is:

```json
{"livestreamId":"00000000-0000-4000-8000-000000000001","channelName":"livestream-...","token":"<opaque Agora token>","uid":456,"role":"VIEWER","tokenExpiresAt":"2026-09-12T13:00:00Z"}
```

Clients use this boundary when Agora reports that token privilege will expire.
They must not use the legacy caller-controlled `POST /{id}/token` route.

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

Unpin and remove-product routes require ownership of the room even for ADMIN.
An ADMIN cannot remove another host's product to bypass the audited unpin route.
Owning ADMIN hosts retain their normal host controls; cross-owner intervention
uses moderation.

### Internal product authority boundary

Restaurant service exposes internal-only
`GET /api/restaurants/internal/{restaurantId}/livestream-products/{productId}`.
It requires the configured `Internal-Token`, queries a single menu item by ID,
and returns 404 when missing, not AVAILABLE, or scoped to another restaurant.
Successful `data` contains productId, restaurantId, productName, productImage,
restaurantName from restaurant-owned data. Client-supplied copied metadata is
not used. No public Gateway route is added. Livestream pin calls this boundary
before persistence through LivestreamProductAuthorityClient with bounded 3s
connect/read timeouts. Missing secret, unavailable authority, malformed response
or mismatched IDs reject pin without saving or emitting a success event. Host
priceAtLive is retained; name/image/restaurant name come from restaurant-service.

### Internal checkout price authority

Order service resolves live prices through internal-only
`POST /api/livestreams/internal/checkout-quote`. The route requires the
configured `Internal-Token` and is never exposed by Gateway.

```json
{"livestreamId":"00000000-0000-4000-8000-000000000001","restaurantId":42,"productIds":[9001,9002]}
```

The service requires a `LIVE` room whose restaurant matches the request. The
response repeats the validated room and restaurant IDs and returns only the
requested products that are currently pinned, each with its positive
server-stored `priceAtLive`. Requested products that are not pinned are omitted
so ordinary items in the same restaurant can retain canonical catalog pricing.
Malformed pinned prices or restaurant scope fail closed. The request accepts at
most 50 distinct positive product IDs. Clients never send live prices to Order.

Order checkout accepts an optional root-level `livestreamId` in both preview
and create requests. It is part of the versioned pricing/idempotency
fingerprints and is preserved during create-time repricing. With
`ORDER_LIVESTREAM_CHECKOUT_ENABLED=false` (the default), any request carrying
the field fails closed. When enabled, Order validates the internal response,
overrides only returned pinned products, and snapshots those prices into the
subtotal and `OrderItem.price`; other products keep restaurant-owned catalog
prices. Livestream pricing cannot be combined with Flash Sale. Client-supplied
item prices remain ignored.

### Internal checkout context receipt

Order obtains the immutable product context through
`POST /api/livestreams/internal/order-context`. It carries the same scoped
request as the quote endpoint plus `Internal-Token`, `X-Actor-Principal-Id`,
`X-Correlation-Id`, and `Idempotency-Key`. The route is internal-only and is
not routed through Gateway.

Livestream persists one receipt per `(actor principal ID, idempotency key)`.
The receipt stores a SHA-256 request fingerprint and the complete versioned
context, including the pinned-product and price-snapshot IDs and the accepted
`priceAtLive`. A matching retry returns that original snapshot after a restart,
even if a pin changes later. Reusing the key with a different stream,
restaurant, product sequence, or actor fails closed. Receipt retention and any
purge remain the separately authorized policy described by the soft-delete and
tombstone contract.

### Admin list

`GET /api/livestreams/admin?page=0&size=20` is ADMIN-only and returns the
normal envelope with `data={content: LivestreamResponse[], page, size,
totalElements, totalPages}`. Page starts at zero; size must be 1–100. Rooms
are ordered by createdAt DESC then id DESC so the Admin UI can page through
all lifecycle states instead of using fixture rooms. Both the service and
Gateway remain default-off. Sorting and pagination apply at the database.

### Admin moderation and audit

`POST /api/livestreams/{id}/moderation` (ADMIN only; same default-off API flag)

Gateway forwards this POST only when `app.livestream.client-api-enabled=true`;
the service independently enforces ADMIN authorization. Both gates remain off
by default. Product pin is POST-only; unpin/removal are DELETE-only.

```json
{"action":"UNPIN","reason":"Product violates content policy","productId":9001}
```

Request: `action: WARN|UNPIN|FORCE_END`, required non-blank `reason` (maximum
1000 characters before trimming), and positive `productId` required for UNPIN
and forbidden for other actions. Actor and time are never request inputs.

| Action | Preconditions and applied effect |
|---|---|
| `WARN` | Room exists; records the warning and reason in audit only. Does not change status or deliver a realtime notification. |
| `UNPIN` | Room is CREATED or LIVE and target product belongs to the room; sets `isPinned=false`, retaining the product row. An already-unpinned existing product remains unpinned and a new audit row is recorded. |
| `FORCE_END` | Room is LIVE; uses the existing LIVE → ENDED transition and sets `endedAt`. Already-ended or unstarted rooms return 400 without audit. |

Success (HTTP 200):

```json
{"status":1,"data":{"auditId":123,"livestreamId":"00000000-0000-4000-8000-000000000001","action":"UNPIN","productId":9001,"appliedAt":"2026-09-12T12:00:00Z"},"message":"Áp dụng kiểm duyệt thành công"}
```

`productId` is null for WARN/FORCE_END. `appliedAt` is an ISO-8601 UTC timestamp.
Missing room/product returns 404; invalid fields/action/status return 400;
non-ADMIN returns 403. Rejections do not create successful-action audit rows.
No idempotency key is accepted: clients must not automatically retry an unknown
outcome. Successful repeated WARN/UNPIN requests each produce a new audit row.

Flyway V2 creates `livestream_moderation_audits`: identity `id`, FK
`livestream_id` (no cascading deletion), authenticated `actor_principal_id`
(stable auth principal, not legacy profile ID), `action`, trimmed `reason`,
nullable `product_id`, and server-generated `applied_at TIMESTAMP WITH TIME
ZONE`. Check constraints enforce action/reason/product target validity; an
index supports `(livestream_id, applied_at DESC)` lookup. No HTTP audit
read/update/delete endpoint or retention purge is introduced.

Persistence decision: follow the repository's service-local JPA audit table
pattern, with action and audit in the **same transaction** (not independent
`REQUIRES_NEW`). Audit insert failure rolls back the room/product change.
The legacy `livestream_events` table is not reused: it has no actor/reason
contract and its realtime publisher is inactive. No acknowledgement from Agora,
Kafka, a host, or viewers is promised; FORCE_END changes application room state
and does not revoke an already-issued Agora token or eject connected clients.
Rollback keeps the additive migration/audit data and disables the existing API
flag. Do not drop the table or reverse applied V2 in a shared environment.

### Disabled capability and failure cases

When the feature flag is false, upstream routes are not registered. With the
Gateway client flag off, the Gateway returns HTTP 404 with
`error.code=LIVESTREAM_DISABLED` for the `/api/livestreams` namespace without
forwarding the request. The disabled-response filter is absent when the client
flag is enabled. Once enabled, the service uses these error mappings:

| Condition | HTTP | Envelope |
|---|---:|---|
| disabled Gateway surface | 404 | `status=0`, `error.code=LIVESTREAM_DISABLED` |
| malformed request | 400 | `status=0`, field map, `Dữ liệu không hợp lệ` |
| missing room | 404 | `status=0`, `error.code=ROOM_NOT_FOUND` |
| missing product | 404 | `status=0`, message; must not masquerade as a missing room |
| invalid status transition (start/end/join) | 400 | `status=0`, `error.code=INVALID_STATUS` |
| unauthenticated/unauthorized host or viewer | 403 | `status=0`, `error.code=OWNERSHIP_DENIED` |
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

## Stable client error codes

Web preserves `error.code` and `error.details` in `ApiClientError`. Flutter's
livestream Gateway preserves them in `LivestreamApiException`. Both clients can
therefore distinguish `LIVESTREAM_DISABLED`, `ROOM_NOT_FOUND`, `INVALID_STATUS`,
and `OWNERSHIP_DENIED` without parsing localized message text.
2. **Join/token policy:** `POST /{id}/join` remains the viewer token boundary;
   the currently exposed caller-controlled `POST /{id}/token` is deliberately
   disabled and must stay disabled unless a future contract replaces it.

Task 2 owns implementing and testing these additions. Task 3/4 may add typed
client methods only after the paths and response codes are finalized.
