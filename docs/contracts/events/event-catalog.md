# Kafka event catalog

Status: current source and contract inventory (2026-09-27)

This catalog is the test-facing index of the Kafka contracts currently present
in the backend. It is derived from the producer/consumer implementations, their
topic configuration, and the authoritative matrix in
[`docs/system-contract-inventory.md`](../../system-contract-inventory.md).
Retry topics and owner DLTs are recovery destinations, not additional domain
events, so they are listed once in the notes rather than repeated as event
rows.

The service-owned DTOs remain the wire authority. The records in
`platform/event-test-support` are deliberately independent fixtures; they are
not a replacement for any service DTO or contract module.

## Conventions

- Kafka keys are stated explicitly below. A key of `orderId` means the decimal
  string representation of that ID unless the producer code says otherwise.
- `eventId` is the idempotency identity where present. Consumers must not use a
  Kafka offset as the business identity.
- `Instant`, `LocalDateTime`, and `occurredAt`/`eventTimestamp` are JSON ISO-8601
  strings. Monetary values are JSON numbers representing VND; producers should
  preserve decimal precision even where an older consumer uses `Double`.
- `raw JSON` means the current listener parses a JSON string or `JsonNode`
  instead of binding to a published shared contract. The fields below are the
  observed contract and not a new type-extraction authorization.
- Service-local DTOs with the same wire shape are listed together. Consumers
  must keep their local mapping and unknown-field compatibility.

## Event index

### Identity bounded context

| Event class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| `IdentityProfileCreated` (`identity-contracts`) | `user-service` identity outbox | `auth-service` `IdentityProfileEventListener` | `identity.profile.created` | `principalId`, `profileId`, `eventId` | I1 |
| `IdentityStatusChanged` (`identity-contracts`) | `auth-service` identity status outbox | `user-service`, `shipper-service` | `identity.status.changed` | `principalId`, `lifecycleVersion`, `eventId` | I2 |
| `ShipperIdentityUpserted` (`identity-contracts`) | `shipper-service` identity outbox | `delivery-service`, `tracking-service` | `shipper.identity.upserted` | `principalId`, `shipperId`, `mappingVersion` | I3 |

### Order bounded context

| Event class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| `OrderCreatedEvent` (`order-service`) | `order-service` `OrderEventPublisher` through transactional outbox | `saga-orchestrator-service`, `notification-service`, `promotion-service`, `flashsale-service`, `restaurant-service` inventory (flagged), `analytics-service` (disabled) | `order.created` | `orderId`, `eventId`, `userId`, `restaurantId` | O1 |
| `OrderCancelledEvent` (`order-service`) | `order-service` `OrderEventPublisher` through transactional outbox | `saga-orchestrator-service`, `promotion-service`, `flashsale-service`, `restaurant-service` inventory (flagged), `settlement-service` refund boundary (flagged), `analytics-service` (disabled) | `order.cancelled` | `orderId`, `eventId`, `cancelledBySource`, `cancelReasonCode` | O2 |
| `OrderCancelledEvent` with `currentStatus=SHIPPER_NOT_FOUND` (refund-eligibility variant) | `order-service` `OrderEventPublisher.publishRefundEligibilityEvent` | `promotion-service`, `flashsale-service`, `restaurant-service` inventory (flagged), `settlement-service` refund boundary (flagged) | `order.refund-eligible` | `orderId`, `eventId`, reservation IDs | O2-R |
| `PaymentEvent` (`settlement-service`, consumer-compatible copy in `order-service`) | `settlement-service` payment graph (feature-gated off) | `order-service`, `analytics-service` (disabled) | `payment.completed` | `paymentRef`/`paymentId`, `orderId` | P1 |
| `PaymentEvent` (`settlement-service`, consumer-compatible copy in `order-service`) | `settlement-service` payment graph (feature-gated off) | `order-service`, `analytics-service`, `flashsale-service` (disabled) | `payment.failed` | `paymentRef`/`paymentId`, `orderId` | P1 |

### Restaurant and search bounded contexts

| Event class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| `RestaurantEvent` (`order-service` consumer DTO; producer emits the same JSON shape) | `restaurant-service` `RestaurantOrderEventPublisher` | `order-service`, `saga-orchestrator-service` | `restaurant.order-confirmed` | `orderId`, `restaurantId`, `actorUserId`, `eventId` | R1 |
| `RestaurantEvent` (`order-service` consumer DTO; producer emits the same JSON shape) | `restaurant-service` `RestaurantOrderEventPublisher` | `order-service` | `restaurant.order-rejected` | `orderId`, `restaurantId`, `actorUserId`, `eventId` | R1 |
| `EntitySyncEvent` (`search-contracts`; producer uses `RestaurantOutboxEvent`) | `restaurant-infrastructure` `RestaurantOutboxRelay` | `search-service` `ElasticsearchSyncConsumer` | `entity-sync` | `entityType`, `entityId`, `occurredAt`, `eventId`, `action` | S1 |

### Saga command and orchestration context

Saga commands are produced by `saga-orchestrator-service` and are consumed by
the service named in the table. Most command payloads intentionally reuse the
business snapshot that caused the command; this preserves the existing raw
payload fingerprint and retry/DLT behavior.

| Event/command class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| `OrderCreatedEvent` JSON command | `saga-orchestrator-service` | `delivery-service` | `saga.command.create-delivery` | `eventId`, `orderId`, `totalPrice`, `shippingFee`, `paymentMethod` | O1-C |
| `OrderCancelledEvent` JSON command | `saga-orchestrator-service` | `delivery-service` | `saga.command.cancel-delivery` | `eventId`, `orderId`, `deliveryId` | O2-C |
| `FindShipperEvent` (`match-service` DTO) | `saga-orchestrator-service` | `match-service` `FindShipperEventListener` | `saga.command.find-shipper` | `eventId`, `orderId`, `deliveryId`, `matchingSessionId`, `matchingDeadlineAt` | M1 |
| stop-matching JSON command (no dedicated published class) | `saga-orchestrator-service` | `match-service` `FindShipperEventListener` | `saga.command.stop-matching` | `eventId`, `orderId`, `deliveryId`, `matchingSessionId` | M2 |
| `ShipperFoundEvent` (`match-service` result DTO) | `match-service` `MatchCommandStore`/outbox | `saga-orchestrator-service` | `shipper.found` | `eventId`, `orderId`, `deliveryId`, `matchingSessionId`, selected `shipperId` | M3 |
| `ShipperNotFoundEvent` (`match-service` result DTO) | `match-service` `MatchCommandStore`/outbox | `saga-orchestrator-service` | `shipper.not-found` | `eventId`, `orderId`, `deliveryId`, `matchingSessionId`, `retryAttempts` | M4 |
| `ShipperFoundEvent` batch variant | `match-service` dispatch outbox | `saga-orchestrator-service`, `delivery-service` (when capability is enabled) | `shipper.found` | `batchOffer`, `batchId`, `batchItems`, `codHoldIds` | M3-B |
| `ShipperFoundEvent` JSON command | `saga-orchestrator-service` | `delivery-service` | `saga.command.cache-shipper-found` | `eventId`, `orderId`, `deliveryId`, selected shipper, `matchingSessionId` | M3-C |
| `ExpireShipperOfferCommand` (`delivery-service` DTO) | `saga-orchestrator-service` timeout scheduler | `delivery-service` | `saga.command.expire-shipper-offer` | `eventId`, `orderId`, `deliveryId`, `timedOutShipperId`, `expectedOfferExpiresAt`, `matchingSessionId` | M5 |
| `ShipperNotFoundEvent` JSON command | `saga-orchestrator-service` | `delivery-service` | `saga.command.mark-shipper-not-found` | `eventId`, `orderId`, `deliveryId`, `matchingSessionId` | M4-C |
| update-order-status JSON command (no dedicated published class) | `saga-orchestrator-service` | `order-service` `SagaCommandListener` | `saga.command.update-order-status` | `eventId`, `orderId`, `sagaStatus`, raw `originalEvent`, `orderStatusSequence` | M6 |
| delivery-created result JSON (no dedicated published class) | `delivery-service` `DeliverySagaCommandProcessor` | `saga-orchestrator-service` | `delivery.created.result` | `eventId`, `orderId`, `deliveryId`, `restaurantId`, `totalPrice`, `shippingFee`, `paymentMethod` | D1 |
| delivery-created failure JSON (no dedicated published class) | `delivery-service` `DeliverySagaCommandProcessor` | `saga-orchestrator-service` | `delivery.created.failed` | `eventId`, `orderId`, `reason` | D2 |
| delivery-cancel failure JSON (no dedicated published class) | `delivery-service` `DeliverySagaCommandProcessor` | `saga-orchestrator-service` | `delivery.cancel.failed` | `eventId`, `orderId`, `deliveryId`, `reason` | D3 |
| `ShipperAcceptedEvent` (`delivery-service` DTO) | `delivery-service` transactional outbox | `saga-orchestrator-service` | `delivery.shipper-accepted` | `orderId`, `deliveryId`, `shipperId` | D4 |
| `ShipperEvent` (`order-service`/`delivery-service` compatible JSON) | `delivery-service` rematch/assignment flow | `saga-orchestrator-service` | `delivery.shipper-rejected` | `eventId`, `orderId`, `deliveryId`, `shipperId`, `action`, `rejectReason` | D11 |
| `OfferPersistedEvent` (`delivery-service` DTO) | `delivery-service` transactional outbox | `saga-orchestrator-service` | `delivery.offer-persisted` | `eventId`, `sourceCommandEventId`, `orderId`, `deliveryId`, `matchingSessionId` | D5 |
| `OfferRetiredEvent` (`delivery-service` DTO) | `delivery-service` transactional outbox | `saga-orchestrator-service` | `delivery.offer-retired` | `eventId`, `sourceCommandEventId`, `orderId`, `deliveryId`, `matchingSessionId` | D6 |

### Delivery bounded context

| Event class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| persisted shipper offer (`ShipperFoundEvent`-compatible JSON; notification DTO is `notification-service.dto.event.ShipperFoundEvent`) | `delivery-service` after offer commit | `notification-service` | `delivery.shipper-offered` | `eventId`, `orderId`, `deliveryId`, exactly one selected `shipperId` | D7 |
| delivery status event (`DeliveryEvent` in Notification, `DeliveryStatusUpdatedEvent` in Order) | `delivery-service` `DeliveryEventPublisher` | `saga-orchestrator-service`, `notification-service` | `delivery.status-updated` | `deliveryId`, `orderId`, `userId`, `status`, `eventId` | D8 |
| `DeliveryCompletedEvent` (`delivery-service`; compatible copies in `settlement-service`, `shipper-service`, `restaurant-service`) | `delivery-service` transactional outbox | `settlement-service`, `match-service`, `shipper-service`/`restaurant-service` legacy projections, simulator observer | `delivery.completed` | `eventId`, `deliveryId`, `orderId`, `restaurantId`, `shipperId`, `totalPrice` | D9 |
| `DeliveryExceptionReportedEvent` (`delivery-service`; compatible copy in `settlement-service`) | `delivery-service` transactional outbox | `settlement-service` review bridge (flagged) | `delivery.exception.reported` | `eventId`, `exceptionId`, `deliveryId`, `orderId`, `exceptionStatus`, money snapshot | D10 |
| delivery batch accepted JSON (no dedicated class) | `delivery-service` batch outbox | `settlement-service` | `delivery.batch.accepted` | `eventId`, `batchId`, `holdIds`, `deliveryIds`, `target=COMMITTED` | B1 |
| delivery batch released JSON (no dedicated class) | `delivery-service` batch outbox | `settlement-service`, `match-service` | `delivery.batch.released` | `eventId`, `batchId`, aligned `holdIds`, `deliveryIds`, `matchingSessionIds` | B2 |
| delivery batch completed JSON (no dedicated class) | `delivery-service` batch outbox | `match-service` | `delivery.batch.completed` | `eventId`, `batchId`, `deliveryIds`, `matchingSessionIds` | B3 |

### Match bounded context

| Event class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| `MatchingDecisionTraceEvent` (`match-service` DTO) | `match-service` best-effort decision-trace outbox | `simulator-service` read-only observer | `matching.decision-trace` | `eventId`, `commandEventId`, `orderId`, `deliveryId`, algorithm/version, decision | M7 |

### Tracking bounded context

| Event class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| shipper status JSON (map payload; no dedicated class) | `delivery-service` `DeliveryEventPublisher` | `match-service`, `tracking-service` delivery-room projection | `shipper.status-change` | `eventId`, `shipperId`, `deliveryId`, `orderId`, `status`, `timestamp` | T1 |
| `ShipperLocationUpdatedEvent` (`tracking-service`) | `tracking-service` `ShipperLocationEventPublisher` | `match-service`, `tracking-service` `LocationHistoryEventListener` | `shipper.location-updated` | `eventId`, `shipperId`, `deliveryId`, `timestamp`, coordinates, `isOnline` | T2 |

### Settlement and compensation context

| Event class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| `PaymentEvent` (`settlement-service`) | Settlement payment graph (feature-gated off) | `order-service`, `analytics-service`, `flashsale-service` (disabled) | `payment.completed`, `payment.failed` | `paymentRef`, `paymentId`, `orderId`, `status`, `amount` | P1 |
| `OrderCancelledEvent` refund snapshot | `order-service` | Settlement refund boundary (flagged), Promotion/Flash-sale/inventory compensation | `order.cancelled` | `eventId`, `orderId`, reservation IDs, immutable monetary snapshot | O2 |
| `OrderCancelledEvent` no-shipper refund snapshot | `order-service` | Settlement refund boundary (flagged), Promotion/Flash-sale/inventory compensation | `order.refund-eligible` | `eventId`, `orderId`, `currentStatus=SHIPPER_NOT_FOUND`, reservation IDs | O2-R |

### Promotion and flash-sale contexts

| Event class | Producer | Consumer(s) | Topic | Key fields | Snapshot |
|---|---|---|---|---|---|
| `PromotionOutboxEvent` payload (reservation state event; no public DTO) | `promotion-service` transactional outbox | operations/audit consumers only | `voucher.reservation.events` | deterministic `eventId`, `reservationId`, `orderId`, `action`, state | C1 |
| `FlashSaleOutboxEvent` payload (reservation state event; no public DTO) | `flashsale-service` transactional outbox | operations/audit consumers only | `flashsale.reservation.events` | deterministic `eventId`, reservation/flash-sale IDs, `orderId`, action, state | C1 |

## Schema snapshots

These are compact, field-level snapshots of the observed JSON contracts. They
are intentionally not JSON Schema files: each service still owns validation,
optional-field compatibility, retry policy, and its Java binding. `number`
means a JSON number; `integer` means an integral JSON number; `string` includes
ISO-8601 timestamps and UUID text; `object` and `array` retain their JSON
shapes. Fields marked `?` are nullable/optional in the current compatibility
path.

### Identity

**I1 — `IdentityProfileCreated`**

```text
{ eventId:string, eventType:string="identity.profile.created", schemaVersion:integer,
  occurredAt:string, correlationId:string?, causationId:string?, principalId:integer,
  profileType:string, profileId:integer, profileVersion:integer }
```

**I2 — `IdentityStatusChanged`**

```text
{ eventId:string, eventType:string="identity.status.changed", schemaVersion:integer,
  occurredAt:string, correlationId:string?, causationId:string?, principalId:integer,
  status:string, lifecycleVersion:integer, reasonCode:string?, changedByPrincipalId:integer? }
```

**I3 — `ShipperIdentityUpserted`**

```text
{ eventId:string, eventType:string="shipper.identity.upserted", schemaVersion:integer,
  occurredAt:string, correlationId:string?, causationId:string?, principalId:integer,
  legacyUserId:integer?, shipperId:integer, mappingVersion:integer }
```

### Order and payment

**O1/O1-C — `OrderCreatedEvent` and create-delivery command**

```text
{ schemaVersion:integer, eventId:string?, orderId:integer, userId:integer,
  userPrincipalId:integer?, restaurantId:integer, status:string,
  subtotalPrice:number, discountAmount:number, shippingFee:number, totalPrice:number,
  itemDiscount:number?, shippingDiscount:number?, customerShippingFee:number?,
  grossShippingFee:number?, platformSubsidy:number?, shopDiscount:number?,
  paymentMethod:string, deliveryAddress:string, deliveryLat:number, deliveryLng:number,
  pickupLat:number, pickupLng:number, restaurantName:string, restaurantAddress:string,
  restaurantPhone:string?, customerName:string, customerPhone:string?, notes:string?,
  createdAt:string, creatorId:integer?, creatorPrincipalId:integer?,
  voucherReservationId:string?, promotionReservationId:string?, flashSaleReservationId:string?,
  inventoryReservationId:string?, items:array?, appliedVouchers:array?,
  eventType:string?, eventTimestamp:string?, simulationContext:object? }
```

**O2/O2-C — `OrderCancelledEvent` and cancel-delivery command**

```text
{ schemaVersion:integer, eventId:string, eventType:string?, occurredAt:string?,
  orderId:integer, userId:integer, userPrincipalId:integer?, restaurantId:integer,
  previousStatus:string, currentStatus:string, cancelReason:string?, cancelledBy:integer?,
  cancelledBySource:string, cancelReasonCode:string, cancelledAt:string,
  shipperId:integer?, hasActiveDelivery:boolean?, voucherReservationId:string?,
  promotionReservationId:string?, flashSaleReservationId:string?, inventoryReservationId:string?,
  subtotalPrice:number, discountAmount:number, shippingFee:number, totalPrice:number,
  itemDiscount:number?, shippingDiscount:number?, customerShippingFee:number?,
  grossShippingFee:number?, platformSubsidy:number?, shopDiscount:number?, paymentMethod:string,
  items:array?, appliedVouchers:array?, createdAt:string?, updatedAt:string? }
```

**O2-R — refund-eligibility variant**

```text
O2 with currentStatus="SHIPPER_NOT_FOUND", cancelledBySource="SYSTEM",
cancelReasonCode="SHIPPER_NOT_FOUND", and a stable outbox eventId.
```

**P1 — `PaymentEvent`**

```text
{ paymentId:integer, orderId:integer, userId:integer, status:string,
  amount:number, paymentMethod:string, transactionId:string?, processedAt:string,
  failureReason:string? }
```

### Restaurant and search

**R1 — `RestaurantEvent`**

```text
{ eventId:string, restaurantId:integer, actorUserId:integer, orderId:integer,
  status:string, action:string, estimatedPrepTime:integer?, rejectionReason:string?,
  processedAt:string, notes:string?, decisionFingerprint:string? }
```

**S1 — `EntitySyncEvent`**

```text
{ eventId:string, occurredAt:string, entityType:string, action:string,
  entityId:string, payload:object }
```

### Saga and matching

**M1 — `FindShipperEvent`**

```text
{ eventId:string, deliveryId:integer, orderId:integer, restaurantName:string,
  pickupAddress:string, pickupLat:number, pickupLng:number, deliveryAddress:string,
  deliveryLat:number, deliveryLng:number, estimatedDeliveryTime:string?, notes:string?,
  createdAt:string?, totalPrice:number, paymentMethod:string, eventType:string?,
  timestamp:string?, occurredAt:string?, maxRetryAttempts:integer?, initialDelaySeconds:integer?,
  maxDelaySeconds:integer?, backoffMultiplier:number?, matchingDeadlineAt:string?,
  matchingSessionId:string, excludedShipperIds:array?, batchOfferEnabled:boolean?,
  batchWave:integer?, simulationContext:object? }
```

**M2 — stop-matching command**

```text
{ eventId:string, orderId:integer, deliveryId:integer, matchingSessionId:string }
```

**M3/M3-C — `ShipperFoundEvent` and cache-offer command**

```text
{ eventId:string, deliveryId:integer, orderId:integer, availableShippers:array,
  foundAt:string, waitingTimeoutSeconds:integer, matchingSessionId:string,
  restaurantName:string, pickupAddress:string, deliveryAddress:string,
  pickupLat:number?, pickupLng:number?, deliveryLat:number?, deliveryLng:number?,
  totalPrice:number?, paymentMethod:string?, batchOffer:boolean?, batchId:string?,
  batchItems:array?, codHoldIds:array?, batchWave:integer?, simulationContext:object? }
candidate = { shipperId:integer, shipperName:string?, shipperPhone:string?,
  distanceKm:number, latitude:number?, longitude:number?, rating:number?, isOnline:boolean? }
```

**M3-B — batch addition**

```text
{ batchOffer:true, batchId:string, batchItems:[
  { deliveryId:integer, orderId:integer, pickupSequence:integer,
    dropoffSequence:integer, totalPrice:number, matchingSessionId:string }
], codHoldIds:[string], batchWave:integer }
```

**M4/M4-C — `ShipperNotFoundEvent` and mark command**

```text
{ eventId:string, deliveryId:integer, orderId:integer, matchingSessionId:string,
  reason:string, occurredAt:string, retryAttempts:integer, searchRadius:number?,
  pickupLat:number, pickupLng:number, deliveryLat:number, deliveryLng:number,
  simulationContext:object? }
```

**M5 — `ExpireShipperOfferCommand`**

```text
{ eventId:string, orderId:integer, deliveryId:integer, timedOutShipperId:integer,
  expectedOfferExpiresAt:string, matchingSessionId:string? }
```

**M6 — update-order-status command**

```text
{ eventId:string, orderId:integer, sagaStatus:string, originalEvent:string,
  orderStatusSequence:integer? }
```

**M7 — `MatchingDecisionTraceEvent`**

```text
{ eventId:string, commandEventId:string, matchingSessionId:string, orderId:integer,
  deliveryId:integer, eventType:string="MATCHING_DECISION_TRACE", eventVersion:integer,
  algorithmId:string, algorithmVersion:string, executionMode:string, mode:string,
  decision:string, pickupLat:number, pickupLng:number, radiusKm:number?,
  candidatePoolSize:integer, attempts:integer, latencyMs:integer, occurredAt:string,
  selectedShipperId:integer?, candidateViewIsPostGeoFilter:boolean, notes:array,
  stages:array, candidates:array }
```

### Delivery

**D1/D2/D3 — delivery result/failure facts**

```text
D1 = { eventId:string, orderId:integer, deliveryId:integer, restaurantId:integer,
       totalPrice:number, shippingFee:number, paymentMethod:string,
       pickupLat:number, pickupLng:number, deliveryLat:number, deliveryLng:number }
D2 = { eventId:string, orderId:integer, reason:string }
D3 = { eventId:string, orderId:integer, deliveryId:integer, reason:string }
```

**D4 — `ShipperAcceptedEvent`**

```text
{ orderId:integer, deliveryId:integer, shipperId:integer, notes:string?, simulationContext:object? }
```

**D5 — `OfferPersistedEvent`**

```text
{ eventId:string, sourceCommandEventId:string, orderId:integer, deliveryId:integer,
  matchingSessionId:string, offeredShipperId:integer, offerExpiresAt:string,
  status:string="WAIT_SHIPPER_CONFIRM", simulationContext:object? }
```

**D6 — `OfferRetiredEvent`**

```text
{ eventId:string, sourceCommandEventId:string, orderId:integer, deliveryId:integer,
  matchingSessionId:string, outcome:string, shipperId:integer? }
```

**D7 — persisted shipper offer**

```text
{ eventId:string, deliveryId:integer, orderId:integer, availableShippers:[candidate],
  matchingSessionId:string, foundAt:string, restaurantName:string, pickupAddress:string,
  deliveryAddress:string, pickupLat:number, pickupLng:number, deliveryLat:number,
  deliveryLng:number, simulationContext:object? }
```

**D8 — delivery status**

```text
{ eventId:string, deliveryId:integer, orderId:integer, userId:integer,
  userPrincipalId:integer?, shipperId:integer?, status:string, previousStatus:string?,
  newStatus:string?, oldStatus:string?, updatedAt:string?, timestamp:string?,
  eventType:string?, notes:string?, currentLat:number?, currentLng:number?,
  estimatedDeliveryTime:string?, simulationContext:object? }
```

**D9 — `DeliveryCompletedEvent`**

```text
{ eventId:string, eventType:string="DELIVERY_COMPLETED", simulationContext:object?,
  occurredAt:string?, deliveryId:integer, orderId:integer, restaurantId:integer,
  shipperId:integer, restaurantEarnings:number, shipperEarnings:number,
  restaurantCommission:number, shippingCommission:number, totalPlatformEarnings:number,
  shippingFee:number, grossShippingFee:number?, customerShippingFee:number?,
  subtotalPrice:number?, shopDiscount:number?, platformSubsidy:number?,
  shippingDiscount:number?, totalPrice:number, deliveredAt:string, deliveryAddress:string,
  paymentMethod:string="COD", restaurantName:string, customerName:string }
```

**D10 — `DeliveryExceptionReportedEvent`**

```text
{ eventId:string, eventType:string, occurredAt:string, exceptionId:string,
  deliveryId:integer, orderId:integer, userId:integer, userPrincipalId:integer?,
  restaurantId:integer, shipperId:integer, previousDeliveryStatus:string,
  currentDeliveryStatus:string, exceptionStatus:string, reason:string,
  paymentMethod:string, subtotalPrice:number?, discountAmount:number?,
  shippingFee:number?, totalPrice:number? }
```

**D11 — `ShipperEvent` rejection fact**

```text
{ eventId:string?, shipperId:integer, deliveryId:integer, orderId:integer,
  action:string="REJECTED", notes:string?, rejectReason:string,
  responseTime:string?, estimatedPickupTime:number?, currentLat:number?,
  currentLng:number?, simulationContext:object? }
```

**B1/B2/B3 — batch lifecycle facts**

```text
B1 = { eventId:string, batchId:string, target:string="COMMITTED", holdIds:[string], deliveryIds:[integer] }
B2 = { eventId:string, batchId:string, target:string="RELEASED", holdIds:[string],
       deliveryIds:[integer], matchingSessionIds:[string] }
B3 = { eventId:string, batchId:string, deliveryIds:[integer], matchingSessionIds:[string] }
```

### Tracking

**T1 — shipper status map payload**

```text
{ eventId:string, shipperId:integer, deliveryId:integer, orderId:integer,
  status:string, timestamp:integer, batchId:string?, simulationContext:object? }
```

**T2 — `ShipperLocationUpdatedEvent`**

```text
{ shipperId:integer, latitude:number?, longitude:number?, isOnline:boolean,
  timestamp:integer, eventId:string, deliveryId:integer?, accuracy:number?, speed:number?,
  heading:number?, source:string?, simulationContext:object? }
```

### Compensation outbox payloads

**C1 — promotion/flash-sale reservation outbox payload**

```text
{ eventId:string, eventType:string, reservationId:string, orderId:integer,
  source:string, action:string, state:string, occurredAt:string, payload:object? }
```

## Inactive and removed paths

These names still occur in source, tests, or historical documentation but are
not active domain contracts in the current runtime:

- `payment.completed` and `payment.failed` are feature-gated off for the COD
  MVP; the `PaymentEvent` shape remains cataloged for compatibility tests.
- `order.status-updated`, `delivery.cancelled`, `delivery.picked-up`,
  `delivery.find-shipper`, `shipper.matched`, and `no.shipper.available` have no
  active producer/consumer path. The canonical replacements are listed above.
- Livestream event DTOs (`LivestreamStartedEvent`, `LivestreamEndedEvent`,
  `ProductPinnedEvent`, `ProductUnpinnedEvent`) have publisher calls commented
  out and no Kafka consumer. They are experimental/inactive and intentionally
  have no topic contract here.
- The service-local `DeliveryCompletedEvent` classes in `shipper-service` and
  `restaurant-service` are compatibility bindings for the delivery completion
  payload; their current runtime Kafka listeners are not active owners of the
  settlement contract. `settlement-service` owns the financial consumer.

## Recovery and ownership notes

Each shared source topic has owner-isolated retry/DLT destinations in the
service configuration (for example `-retry-order-*`/`.order.DLT`,
`-retry-notification-*`/`.notification.DLT`, and the matching owner names for
identity, Saga, Promotion, Flash-sale, and Tracking). A retry record retains
the source event identity and raw payload; it does not define a second schema.

Outbox rows are the producer boundary for Order, Restaurant, Delivery, Match,
Saga, Identity, Promotion, Flash-sale, and refund flows. The producer owns the
Kafka key and immutable snapshot. Consumers ACK only after their local durable
receipt/projection/ledger boundary commits, with exact event-ID replay treated
as a no-op and contradictory reuse sent to the owner recovery path.
