# Settlement & COD finance flow

## MVP boundary

Settlement runs on port `8090`. The active MVP surface is deliberately small:

- Match calls the internal COD-eligibility endpoint with `Internal-Token`.
- Delivery publishes `delivery.completed` only after a COD delivery reaches
  `DELIVERED`.
- Settlement writes the durable receipt, four ledger entries and balance
  projections in one database transaction.
- Admin may read bounded balances, transactions, pending withdrawals and revenue.
- Refund compensation consumes `order.cancelled` and the dedicated
  `order.refund-eligible` no-shipper snapshot only behind its default-off flag;
  it creates a read-only case/outbox boundary and never calls a provider or
  reverses the ledger in the MVP.

Payment/VNPay, self-service balance/withdraw/deposit APIs and admin financial
mutations are disabled by default. They are not part of the COD MVP contract.

## Canonical flow

```mermaid
sequenceDiagram
    autonumber
    participant M as Match
    participant S as Settlement
    participant D as Delivery
    participant K as Kafka
    participant DB as Settlement DB

    M->>S: GET /api/settlement/internal/shippers/{id}/cod-eligibility?codAmount=totalPrice
    S->>DB: Read SHIPPER deposit balance
    S-->>M: depositBalance >= canonical totalPrice
    Note over M,D: Only one active offer/assignment per shipper in MVP
    D->>K: delivery.completed (stable eventId, exact COD amounts)
    K->>S: delivery.completed
    S->>S: Validate IDs, COD method, fees and commissions
    S->>DB: Insert unique receipt for eventId/orderId
    S->>DB: Lock balance rows and append ledger entries
    Note over S,DB: restaurant net credit; shipper fee credit;<br/>shipper COD deposit debit; platform commission credit
    alt deposit is still sufficient
        S->>DB: Commit receipt, ledger and projections
        S-->>K: ACK
    else deposit is insufficient or payload conflicts
        S->>DB: Roll back receipt and ledger
        S-->>K: Throw for retry/DLT
    end
```

## Financial invariants

- Eligibility uses the canonical order total supplied by Match; no fixed minimum
  deposit is invented by Settlement.
- A COD posting must never create a negative deposit balance. Insufficient funds
  fail closed and leave no receipt or partial ledger posting.
- Exact event replay is acknowledged without posting again. Reusing an event ID
  with different payload, or settling one order under another event ID, fails.
- Kafka acknowledgment is registered only after the Settlement database
  transaction commits; a rollback or process failure before commit leaves the
  record eligible for retry.
- Cửa sổ sau commit/trước ACK đã được fault-injection bằng JDI breakpoint tại
  `DeliveryCompletedEventListener$1.afterCommit`: process bị `SIGKILL` khi
  receipt và bốn ledger row đã commit nhưng consumer offset chưa tăng. Restart
  cùng database/topic/group redeliver exact event, giữ nguyên receipt, ledger,
  balance và đưa lag về `0`.
- The ledger business key is unique per order/entity/reason/wallet/direction.
- Balance mutations use pessimistic row locks. PostgreSQL concurrent replay đã
  được rehearsal bằng hai Settlement peer dùng chung database trên topic recovery
  hai partition; đây không phải bằng chứng topic canonical có hai partition.
- The legacy `delivery.picked-up` debit path is removed; COD is debited once at
  completion.

## Local MVP data

Because public deposit/top-up is disabled, local E2E uses
`scripts/seed-settlement.sql` to seed the test shipper deposit explicitly. That
script is test setup, not a production top-up contract.

## Runtime proof

- `scripts/verify-settlement-crash-window.sh` tạo database, topic một partition
  và consumer group cô lập; dùng `scripts/JdwpBreakpointProbe.java` dừng đúng
  `afterCommit`, xác nhận offset chưa commit, `SIGKILL` process, rồi restart cùng
  state. Run `20260726-auto-1` PASS: exact redelivery được idempotent-skip,
  receipt vẫn `1`, ledger vẫn `4`, shipper deposit vẫn `0`, COD collected vẫn
  `120000`, offset tới log end và lag `0`.
- Đây là true process-crash proof cho transaction/ACK boundary production code,
  dù topic/database được cô lập để không làm hỏng canonical runtime. Nó không
  tuyên bố topic canonical có nhiều partition.
- Topic canonical `delivery.completed` hiện có một partition; proof recovery hai
  partition chỉ chứng minh hai peer dùng chung PostgreSQL và không được mô tả là
  canonical two-partition proof.
- End-to-end COD happy path plus reject, cancel, rematch and shipper-not-found
  is now covered by the canonical runtime harness.
- Read-only balance/ledger reconciliation is repeatable with
  `scripts/verify-settlement-reconciliation.sh` and currently passes; automatic
  repair remains intentionally unsupported.
- PostgreSQL two-instance duplicate/replay, poison-message/DLT, read-only
  reconciliation và true process crash-window đều đã PASS trên recovery
  topic/database cô lập. Gate hệ thống vẫn cần canonical runtime/container và
  cross-client E2E; không suy rộng proof này thành toàn bộ Gate B8.
