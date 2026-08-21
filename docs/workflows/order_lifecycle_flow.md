# Order lifecycle: COD-first Saga

> Cập nhật từ source: 2026-08-08. Đây là luồng canonical của MVP hiện tại,
> không phải mô tả Payment Service/VNPay như một dependency đang hoạt động.

## Phạm vi và boundary

Khách tạo đơn COD qua Gateway. Order sở hữu checkout snapshot và trạng thái đơn;
Saga điều phối các transition phân tán; Delivery sở hữu offer/assignment/state
giao; Match chọn một shipper từ Redis GEO replica; Settlement chỉ ghi sổ sau khi
Delivery hoàn tất. Mỗi service ghi database của mình và truyền side effect qua
Kafka/outbox.

Promotion và Flash Sale có durable reservation/compensation boundary, nhưng
checkout/relay mặc định off. Online payment, payment provider và ví self-service
không là phần active của luồng này.

## Luồng canonical

~~~mermaid
sequenceDiagram
    autonumber
    participant C as Customer app
    participant RP as Restaurant portal
    participant G as API Gateway
    participant O as Order
    participant R as Restaurant
    participant K as Kafka
    participant S as Saga
    participant D as Delivery
    participant M as Match
    participant GEO as Redis GEO replica
    participant N as Notification
    participant A as Shipper app
    participant SET as Settlement

    C->>G: POST /api/orders/checkout-preview
    G->>O: Forward Authorization; Order calculates canonical total + quoteId (5 min)
    C->>G: POST /api/orders with quoteId + Idempotency-Key
    G->>O: Forward Authorization; Order validates JWT, quote and retry receipt
    O->>O: Re-price canonical facts; 409 PRICE_CHANGED/QUOTE_EXPIRED if confirmation is stale
    O->>R: Internal canonical menu/order validation
    R-->>O: Canonical catalog facts
    O->>O: Persist order snapshot and transactional outbox
    O-->>K: order.created
    K-->>S: Consume order.created idempotently
    S->>S: Persist saga transition and command outbox
    S-->>K: saga.command.create-delivery
    K-->>D: Create PENDING delivery
    D-->>K: delivery.created.result
    K-->>S: Record delivery created

    RP->>G: Restaurant owner confirms or rejects order
    G->>R: Forward Authorization; Restaurant validates owner role
    R->>R: Persist decision and transactional outbox
    R-->>K: restaurant.order-confirmed or rejected
    K-->>O: Apply canonical order decision idempotently
    K-->>S: Advance only after restaurant confirmation

    alt Restaurant confirms
        S-->>K: saga.command.find-shipper
        K-->>M: Find a candidate
        M->>GEO: Query fresh online shipper replica and reserve one
        alt Eligible shipper found
            M-->>K: shipper.found
            K-->>S: Record selected shipper
            S-->>K: saga.command.cache-shipper-found
            K-->>D: Persist offeredShipperId and expiry
            D-->>K: delivery.shipper-offered
            K-->>N: Persist durable inbox event
            N-->>A: FCM wake-up best effort
            A->>G: GET /api/deliveries/offers/current
            G->>D: Authenticated self offer recovery
            A->>G: POST /api/deliveries/accept
            G->>D: Validate current offer and SHIPPER actor
            D-->>K: delivery.shipper-accepted
        else Business retries exhaust
            M-->>K: shipper.not-found
            K-->>S: Record terminal matching result
            S-->>K: mark delivery no-shipper and update order status
            K-->>D: SHIPPER_NOT_FOUND
            K-->>O: Update order status to SHIPPER_NOT_FOUND
            O-->>K: order.refund-eligible snapshot
        end
    else Restaurant rejects
        S-->>K: cancel delivery and converge terminal state
        K-->>D: CANCELLED
        O-->>K: order.cancelled compensation snapshot
    end

    opt Current offer is accepted
    D->>D: ASSIGNED to PICKED_UP to DELIVERING to DELIVERED
    D-->>K: delivery.status-updated
    D-->>K: delivery.completed with canonical COD amounts
    K-->>SET: Validate and post COD ledger idempotently
    end
~~~

## State, consistency và compensation

~~~mermaid
stateDiagram-v2
    [*] --> PENDING: create-delivery command
    PENDING --> FINDING_SHIPPER: restaurant confirmed
    PENDING --> CANCELLED: restaurant rejected or order cancelled
    FINDING_SHIPPER --> WAIT_SHIPPER_CONFIRM: one offer persisted
    WAIT_SHIPPER_CONFIRM --> ASSIGNED: current shipper accepts
    WAIT_SHIPPER_CONFIRM --> FINDING_SHIPPER: reject or offer expires
    FINDING_SHIPPER --> SHIPPER_NOT_FOUND: matching retries exhaust
    ASSIGNED --> FINDING_SHIPPER: cancel assignment before pickup
    ASSIGNED --> PICKED_UP: pickup confirmed
    PICKED_UP --> DELIVERING: delivery starts
    DELIVERING --> DELIVERED: delivery completed
    FINDING_SHIPPER --> CANCELLED: permitted cancellation
    WAIT_SHIPPER_CONFIRM --> CANCELLED: permitted cancellation
    ASSIGNED --> CANCELLED: permitted cancellation boundary
    DELIVERED --> [*]
    SHIPPER_NOT_FOUND --> [*]
    CANCELLED --> [*]
~~~

- Order snapshots canonical price, restaurant/menu facts and COD monetary values
  before emitting order.created. Downstream services do not recreate price from
  mutable catalog data.
- Outbox write and source service state transition share one local database
  transaction. Relay publishes later; consumer receipt/idempotency makes an exact
  Kafka replay safe.
- Matching only starts after restaurant confirmation. One offer targets one
  shipper, and Delivery persists it before Notification wakes the device.
- Reject/timeout/cancel releases matching/reservation work through typed
  compensation events. SHIPPER_NOT_FOUND is its own terminal outcome, not a
  disguised cancellation; Order emits order.refund-eligible with immutable
  monetary snapshot.
- Settlement consumes delivery.completed only after DELIVERED. It either commits
  receipt, ledger and balance projection atomically, or throws for retry/DLT;
  it never posts a partial COD ledger.

## Recovery contract for clients

- FCM is only a wake-up. Shipper app polls/recover GET current offer and then
  accepts the exact persisted offer. A push payload is never authorization to
  accept an order.
- Protected REST requests pass through Gateway, but JWT verification, role and
  ownership decision execute in the resource service through Auth JWKS.
- Customer/restaurant cancellation and shipper cancellation remain state-machine
  commands. Clients must not mutate an arbitrary status or assign a shipper by
  ID.

## Related sources

- Delivery matching and raw location transport:
  [delivery_matching_tracking.md](delivery_matching_tracking.md)
- COD receipt/ledger invariants:
  [settlement_finance_flow.md](settlement_finance_flow.md)
- Exact topics, producer/consumer ownership and capability gates:
  [system-contract-inventory.md](../system-contract-inventory.md)
- System-level editable diagrams:
  [docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md)
