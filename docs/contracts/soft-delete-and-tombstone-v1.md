# Soft Delete and Tombstone Contract v1

## Purpose

Business records referenced by orders, payments, analytics, search projections, or audit trails are retired with a soft delete. Application commands must not physically delete these records during normal request handling.

## Required fields

Every soft-deletable aggregate stores `deleted_at`, `deleted_by`, `deletion_reason`, and a monotonically increasing `aggregate_version`. The deletion command validates the expected version, sets all deletion fields, increments the version, and emits one tombstone through the service outbox in the same transaction.

## Read and write rules

- Normal repositories and API reads filter `deleted_at IS NULL`.
- Audit, reconciliation, recovery, and migration tools may opt into deleted rows explicitly.
- Update, reserve, quote, publish, and checkout commands reject deleted aggregates with a stable retired/not-found response.
- Repeating a delete command is idempotent when the aggregate is already deleted; it must not emit a second business transition.
- Physical purge is a separate retention operation and is never executed from a request transaction.

## Event ordering and replay

Consumers persist the last applied `aggregate_version` or projection version. They ignore older events, apply an accepted event once by `event_id`, retain a deleted projection tombstone, and advance checkpoints only after persistence succeeds. An older update must never resurrect a deleted projection.

## Retention and purge

Purge requires an elapsed retention window, no open order/payment/dispute/audit reference, an operator-authorized bounded job, and an immutable purge audit record. Restores never clear deletion metadata implicitly; restore is an explicit audited command with a new version and restore event.

## Compatibility examples

| Scenario | Required result |
| --- | --- |
| Duplicate tombstone | No-op after the first applied event/version |
| Older update after tombstone | Ignore; deleted projection remains deleted |
| Tombstone retry after DB timeout | Retry safely; checkpoint advances after persistence |
| Replay from an earlier offset | Tombstone is applied before any older update becomes visible |
| Delete of an already deleted row | Idempotent response; no duplicate outbox transition |
