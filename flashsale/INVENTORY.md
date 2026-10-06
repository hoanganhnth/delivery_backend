# Flashsale inventory and extraction order

Scope: equivalence-only extraction from `flashsale-service`; artifact/DNS remains
`flashsale-service`. No flags, Kafka consumer configuration, messages, exception
types, topics, locking, SQL or transaction boundaries change in slice 1.
Authority: `docs/workflows/flash_sale_flow.md`,
`docs/platform/decisions/0003-voucher-flashsale-checkout-policy.md`, existing
implementation/tests, and Promotion's framework-free domain/build conventions.
References below are repository-relative; host line numbers describe the initial
inventory (the availability adapter changes locally in slice 1).

## Entrypoints

All Java paths below start at
`flashsale-service/src/main/java/com/delivery/flashsale_service/`.

| Transport | Route/topic | File:line (method) |
| --- | --- | --- |
| HTTP GET | `/api/flashsales/public/campaigns` | `controller/PublicFlashSaleController.java:17` |
| HTTP GET | `/api/flashsales/public/campaigns/{campaignId}/items` | `controller/PublicFlashSaleController.java:22` |
| HTTP POST | `/api/flashsales/admin/campaigns` | `controller/AdminFlashSaleController.java:24` |
| HTTP GET | `/api/flashsales/admin/campaigns` | `controller/AdminFlashSaleController.java:32` |
| HTTP GET | `/api/flashsales/admin/campaigns/{id}/items` | `controller/AdminFlashSaleController.java:39` |
| HTTP PUT | `/api/flashsales/admin/campaigns/{id}/status` | `controller/AdminFlashSaleController.java:47` |
| HTTP PUT | `/api/flashsales/admin/items/{id}/approve` | `controller/AdminFlashSaleController.java:57` |
| HTTP POST | `/api/flashsales/merchant/items` | `controller/MerchantFlashSaleController.java:25` |
| HTTP POST | `/api/flashsales/internal/reserve` | `controller/InternalFlashSaleController.java:34` |
| HTTP POST | `/api/flashsales/internal/quote` | `controller/InternalFlashSaleController.java:65` |
| HTTP POST | `/api/flashsales/internal/reservations/{reservationId}/commit` | `controller/InternalFlashSaleController.java:79` |
| HTTP POST | `/api/flashsales/internal/reservations/{reservationId}/release` | `controller/InternalFlashSaleController.java:89` |
| Kafka | `order.created`, `order.cancelled`, `order.refund-eligible` (configurable) | `listener/OrderReservationEventListener.java:32` |
| Scheduled | midnight recurring stock reset | `service/FlashSaleCronService.java:19` |
| Scheduled | expiry scan, default 30000 ms | `service/FlashSaleReservationExpiryJob.java:15` |
| Scheduled | outbox relay, default 500 ms | `service/FlashSaleOutboxRelay.java:25` |

## Decisions and authority boundaries

- `service/FlashSaleAvailabilityPolicy.java:12`: deleted -> restaurant ownership
  -> approval -> ACTIVE campaign/inclusive daily window -> remaining stock.
  Quote (`service/FlashSaleStockService.java:40`) and reserve (`:62`) share it.
  Slice 1 moves these decisions to `flashsale/domain`; a lazy host view retains
  short-circuit behavior, entity enums and persistence access in the host.
- `service/FlashSaleService.java:27`: campaign construction/UPCOMING;
  `:56` status updates; `:68` approval rejects deleted items as not-found;
  `:79` registration requires positive IDs/stock/prices and flash price below
  original; `:108` public catalog requires ACTIVE/APPROVED. Lists cap at 100.
  Request validation helpers start at `:123`, `:142`, `:158`.
- `service/FlashSaleStockService.java:105`: RESERVED commits only before expiry,
  COMMITTED replay is a no-op, RELEASED/EXPIRED commit fails closed. `:124`
  releases RESERVED or COMMITTED; `:134` expiry restores RESERVED capacity;
  `:177` exact replay checks reservation/order/user/principal/restaurant and
  item-quantity fingerprint; `:195` request/principal enforcement validation.
- `service/FlashSaleOrderReservationEventProcessor.java:56`: parses event IDs,
  source/action and raw-payload SHA-256; `:91` canonicalizes retry topic suffix;
  `:98` maps created to COMMIT and cancelled/refund-eligible to RELEASE;
  `:152` checks exact receipt replay.
- Admin owns campaigns and approval, not restaurant items. ADMIN enforced at
  `controller/AdminFlashSaleController.java:65`; SHOP_OWNER at
  `controller/MerchantFlashSaleController.java:28`. Registration verifies canonical
  restaurant ownership via `client/RestaurantOwnershipClient.java:27` (principal
  first, legacy fallback owned by Restaurant, not Flashsale).
- `security/SecurityConfig.java:19`: JWT resource server; public GET and internal
  routes permit at the filter layer. Internal controllers require a nonblank
  configured `Internal-Token`, then checkout capability, then validation/service
  availability. Gateway exposure is outside this tranche.

## Persistence, idempotency, locks, outbox and projections

- PostgreSQL, not Redis, owns stock. `repository/FlashSaleItemRepository.java:15`
  pessimistically locks nondeleted item rows, fetches campaign, orders by ID.
  Reserve validates every line before counters change; counters, reservation
  lines (server menu/price snapshots) and outbox persist in one transaction.
  Reservation TTL is 15 minutes (`service/FlashSaleStockService.java:84`).
- Reservation ID primary key and unique order ID (`entity/FlashSaleReservation.java`,
  migration `src/main/resources/db/migration/V2__flash_sale_reservations.sql`)
  fence identities. Sequential exact replay returns stored terminal state;
  concurrent uniqueness conflicts are not silently retried. Commit/release lock
  reservation at `repository/FlashSaleReservationRepository.java:16`; capacity
  release then locks sorted item IDs. Expiry scans oldest 100 and rechecks under lock.
- `repository/FlashSaleOrderReservationReceiptRepository.java:15`: PostgreSQL
  `ON CONFLICT DO NOTHING` atomically claims event ID. Receipt and stock mutation
  share `service/FlashSaleOrderReservationEventProcessor.java:55` transaction;
  mismatch fails closed, rollback permits replay. H2 fallback (`:33`) is test
  proof only, not PostgreSQL race authority. Missing reservation ID still records
  a receipt. Listener ACK follows successful processor return (`:35`). Existing
  retry suffix `-retry-flashsale`, owner DLT `.flashsale.DLT`, IllegalArgumentException
  exclusion and consumer configuration remain untouched.
- `service/FlashSaleOutboxService.java:30`: MANDATORY transaction, deterministic
  name UUID from reservation/state, one outbox row per transition, ordered payload,
  topic `flash-sale.reservation.events`, key order ID. Relay locks oldest 100 due
  events (`repository/FlashSaleOutboxEventRepository.java:16`), waits up to 10s for
  Kafka, marks SENT or backs off, DEAD after 12 attempts. Publish/DB commit is
  at-least-once, not exactly-once. Consumers must deduplicate event IDs.
- No local Kafka read projection or active Redis stock writer exists. Public
  catalog reads JPA rows; quote is read-only/nonlocking and is not a stock hold.
  Soft-delete columns/audit are mapped by `entity/FlashSaleItem.java`; V6 adds the
  deleted/status/campaign index. Midnight reset is a bulk database operation
  (`repository/FlashSaleItemRepository.java:27`), not a domain transition.

## Default-off capability flags

`flashsale-service/src/main/resources/application.properties:26` checkout
(`FLASHSALE_CHECKOUT_ENABLED=false`), `:29` relay
(`FLASHSALE_OUTBOX_RELAY_ENABLED=false`), `:39` merchant registration
(`FLASHSALE_MERCHANT_REGISTRATION_ENABLED=false`), `:40` principal enforcement
(`FLASHSALE_PRINCIPAL_OWNERSHIP_ENFORCED=false`). Checkout conditionally creates
stock service, event processor/listener and expiry job. Relay and merchant have
independent conditions. Workflow also requires Order/client checkout flags off;
those surfaces are outside scope. Discovery/config fail-fast/retry topic creation
also default false; no setting changes are authorized.

## Proposed ordered slices

1. **Availability domain (this task):** register `flashsale/domain` before host,
   depend from host, extract the shared pure availability decision behind a lazy
   read view; exhaustive ordering/boundary tests, 85% line and branch gates.
2. Extract campaign/item validation and lifecycle decisions, preserving DTO
   validation vs service-validation ordering and all compatibility errors.
3. Extract reservation replay/transition/expiry decisions with state and identity
   value types; retain locks, clock acquisition, ledger updates and transactions
   in host until application ports exist.
4. Extract order-event action/receipt replay and outbox retry decisions; keep
   Jackson, Kafka retry/ACK/configuration, native receipt SQL and serialization
   in host. Preserve raw-payload fingerprint and deterministic event identities.
5. Introduce `flashsale/application-api` contracts/ports and
   `flashsale/application` use cases; host supplies HTTP/Kafka/JPA/ownership/
   outbox/clock adapters. Keep existing artifact and default-off composition.
6. Relocate adapters/persistence/migrations to `flashsale/infrastructure` and
   entrypoint/configuration to `flashsale/boot`, ending at
   `flashsale/{domain,application-api,application,infrastructure,boot}` with boot
   artifact/DNS `flashsale-service`. Re-run host, migration, Docker race/replay
   and packaged-runtime proof before retiring the legacy host directory.

## Pre-existing risks/defects (not fixed)

- Midnight reset zeros sold quantity for recurring APPROVED items without
  reconciling durable reservation lines or filtering deleted items. A later
  release can fail the stock-ledger check (`service/FlashSaleStockService.java:158`)
  or counters may no longer represent holds. Checkout remains default-off.
- Public catalog checks campaign status, not its time window or remaining stock;
  catalog visibility is not quote/reserve eligibility. Preserved, not broadened.
- Availability assumes nonnull persisted restaurant, campaign, window and stock
  fields and uses Java int subtraction (including overflow); no new null handling,
  positive-quantity validation or arithmetic policy is introduced in extraction.
- PostgreSQL/Kafka Docker-only proof may be skipped when Docker is unavailable;
  H2/unit tests are not substitutes for those concurrency/recovery guarantees.
