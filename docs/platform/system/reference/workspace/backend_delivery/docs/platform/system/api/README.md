# API Contract Guide

> Status: current contract guide, checked 2026-08-24. The exact controller
> method inventory is maintained by the backend at
> [`../../../backend_delivery/docs/http-api-inventory.md`](../../../http-api-inventory.md)
> and currently records 226 handlers. The checked-in
> [source-derived contract manifest](./http-contract.json) maps those routes to
> controller signatures, bindings and reachable DTO declarations. Controller,
> DTO and test source remain authoritative where any generated artefact differs.

## Machine-readable source contract

[`http-contract.json`](./http-contract.json) is a deterministic, inspectable
snapshot for client generators, review tools and other models. It includes every
annotated controller mapping—not only public MVP routes—and records:

- route, verb(s), service, controller, handler and source line;
- Java return type plus path/query/header/body/principal parameter bindings,
  defaults and validation annotations;
- source-declared fields for request/response DTOs reachable from those
signatures, including nested DTOs and enums where present.

[`http-contract-catalog.md`](./http-contract-catalog.md) is the generated
human-readable equivalent: it groups all 226 mappings by service and records
each verb/path, Java handler/source link, parameter binding/default/validation,
return type, Java signature and all 202 reachable source-declared DTO/enum
schemas. It intentionally includes internal, hidden, experimental and dev-only
controllers too; use the HTTP inventory/Gateway rules below to decide whether a
route is a supported public contract.

It is deliberately **not** called OpenAPI. The repository does not yet expose a
runtime SpringDoc/OpenAPI document, and a static extractor must not invent
Jackson naming, inheritance, polymorphism, examples or error semantics. Use the
manifest for exact source navigation; use the HTTP inventory for Gateway/public
classification and capability status; use controller/DTO tests for behavior.

Refresh/check it after a controller or DTO change:

```bash
node backend_delivery/docs/platform/system/api/generate-http-contract.mjs --write
node backend_delivery/docs/platform/system/api/generate-http-contract.mjs --check
```

## Edge rules

- All browser/mobile application HTTP paths enter through the Gateway origin and
  begin with `/api`. A client base URL must add this prefix exactly once.
- Client code never calls an individual service port and never supplies
  `Internal-Token`, `X-User-Id` or `X-Role` as proof of identity.
- Resource services decide authorization from their validated bearer token,
  `ROLE_USER`, `ROLE_SHOP_OWNER`, `ROLE_SHIPPER` or `ROLE_ADMIN`, plus ownership
  of the requested record.
- Internal paths are omitted from Gateway routing and require the service
  credential; they are not a convenient public API namespace.
- Unknown, disabled or hidden routes must return an appropriate denial/404, not
  be forwarded to a matching controller incidentally.

## Cross-service HTTP conventions

### Success and errors

Most current endpoints preserve this compatibility envelope:

```json
{
  "status": 1,
  "message": "Thành công",
  "data": {}
}
```

Error responses use the transport HTTP status as authority and return a safe
error payload:

```json
{
  "status": 0,
  "message": "Request không hợp lệ",
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "fieldErrors": {"field": "reason"},
    "traceId": "opaque-correlation-id"
  }
}
```

Never branch client behavior by parsing `message`. Use the HTTP code and
`error.code`; do not expose stack traces, database errors, secrets or raw token
claims.

### Pagination, money and time

- Page query: `page` is zero-based; default size is 20 and normal maximum is
  100. Public pagination uses `{items,page,size,totalItems,totalPages,hasNext}`.
- Money is VND represented with `BigDecimal`/`DECIMAL(19,0)` and JSON decimal
  integers; neither clients nor services use `double` for settlement amounts.
- Events and new public timestamps use UTC ISO-8601 with offset; Vietnam
  campaign/business days use `Asia/Ho_Chi_Minh` only at their business boundary.

### Idempotency and retries

Callers may retry only documented safe paths. Registration hand-off and several
internal transitions are intentionally idempotent. `POST /api/orders` is the
customer-facing exception with an explicit retry contract: after preview, send
the server-issued `quoteId` in the JSON body and a UUID `Idempotency-Key`
header. Within 24 hours, the same authenticated principal/key/effective command
returns the original order; the same key with different data returns HTTP 409
`IDEMPOTENCY_KEY_REUSED`. A current re-price that differs from the accepted
quote returns HTTP 409 `PRICE_CHANGED` and a replacement quote in
`error.details.quote`; an expired quote returns HTTP 409 `QUOTE_EXPIRED`.
Clients must create a fresh key after either quote conflict and obtain explicit
customer confirmation before submitting the replacement quote. During the
staged rollout, server enforcement is controlled by
`ORDER_QUOTE_ENFORCEMENT_ENABLED`; clients should nevertheless send both fields
now. For a client-visible command without an explicit idempotency contract, a
network timeout is not proof that the command did not succeed: first read the
canonical resource state rather than blindly resubmit a new
order/payment/transition.

## Public API families

| Family | Examples | Main actors | Notes |
| --- | --- | --- | --- |
| Auth and identity | `POST /api/auth/register`, login, refresh, logout, recovery, email verification, `GET /.well-known/jwks.json` | Anonymous, authenticated user, admin | Registration is followed by `POST /api/users/registrations`; JWKS is public verification metadata, never a key-management write API |
| Profiles and addresses | `/api/users`, `/api/addresses/**` | Current user, admin | Current profile routes derive actor from JWT; path IDs do not override ownership |
| Restaurant and menu | `/api/restaurants/**`, `/api/menu-items/**`, restaurant order confirm/reject, ratings | Anonymous read; shop owner/admin writes; user rating | Checkout validation is private; public catalog is bounded/read-only |
| Order | `POST /api/orders/checkout-preview`, `POST /api/orders`, my/restaurant/admin reads, cancellation | User, shop owner, admin | Preview issues a five-minute quote; create revalidates current canonical pricing and is retry-safe with `Idempotency-Key` |
| Delivery | current offer, accept, cancel assignment, state update, protected reads | Shipper, delivery participant, owner/admin as specified | Shipper recovers offer by `GET /api/deliveries/offers/current` before accept |
| Shipper | profile, online status, bounded fleet reads/ratings | Shipper, admin | Matching is not a public “nearby shipper” API |
| Tracking | shipper location update/offline plus raw `/ws/shipper-locations` | Shipper publisher; authorized delivery viewer | Viewer authorization is server-side; location read endpoints are intentionally limited |
| Notification | FCM token register/unregister, self inbox/read state | Authenticated owner | Manual send is private; FCM is not business truth |
| Search | `/api/search/restaurants`, `/api/search/dishes` | Anonymous | Bounded query/page; unavailable search is sanitized 503 rather than fabricated empty result |
| Settlement | limited admin reads, read-only refunds, private COD eligibility | Admin/internal | Payments, provider callbacks and user money mutations remain disabled/hidden in MVP |
| Promotion/Flash sale | wallet/read/admin campaign paths | User/owner/admin as documented | Route mappings exist, but local default COD Compose leaves these services in the `optional-capabilities` profile; reservation/checkout is feature-gated and not part of default COD checkout |

## API classification

| Classification | Gateway behavior | Service behavior |
| --- | --- | --- |
| Public anonymous read/auth | Exact allow-list | Validate inputs and product-specific policy |
| Protected client route | Exact allow-list, forward bearer | Validate JWT then role and ownership |
| Protected admin/owner route | Exact allow-list, forward bearer | Validate JWT, role and resource relationship |
| Internal route | No public route | Require exact service credential and validate request; do not weaken normal data checks |
| Dev-only | Never production Gateway route | Controller/bean exists only in explicit dev/test configuration |
| Hidden/disabled | No route | Controller/listener/feature is disabled; do not rely on it as contract |

## WebSocket contract

The only current realtime location transport is raw WebSocket:

```text
/ws/shipper-locations
```

The handshake carries a bearer token (or the supported bearer protocol fallback
for clients unable to set a header). JSON messages include a supported action
such as location update/ping; payload validation rejects non-finite telemetry.
The tracking service validates token/shipper identity, Delivery validates room
participant access, and clients receive latest/current location only for their
authorized delivery. STOMP and gRPC are not compatibility transports to revive.

## Exact implementation references

- [Machine-readable source-derived HTTP contract](./http-contract.json)
- [Generated human-readable operation and DTO catalog](./http-contract-catalog.md)
- [Deterministic contract extractor](./generate-http-contract.mjs)
- [Exact HTTP method/path/controller inventory](../../../http-api-inventory.md)
- [HTTP conventions ADR](../../decisions/0001-backend-contract-conventions.md)
- [Gateway routes and test proof](../../../../api-gateway/src/main/resources)
- [Client action-contract checks](../../../../../delivery_web/scripts/verify-action-contracts.mjs)

When reconstructing exact DTO schemas, start with the manifest's source line and
then controller request/response types and tests in the owning service. Do not
reverse-engineer a schema from an old client call site: the inventory explicitly
records stale client paths and hidden routes.
