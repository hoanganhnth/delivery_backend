# ADR 0003: Soft Delete for Referenced Business Aggregates

## Status

Accepted for Phase 8–9.

## Decision

Referenced business aggregates use soft delete as their normal retirement mechanism. Physical purge is a separate retention-controlled operation.

## Reasons

- Search, analytics, outbox, payment, dispute, and audit records may reference the aggregate after retirement.
- Event replay and consumer retries require a durable tombstone to prevent stale data resurrection.
- Soft delete preserves evidence needed for reconciliation and incident recovery.
- Hard delete in request handling creates races between database state and asynchronous projections.

## Consequences

- Queries must exclude deleted rows by default.
- Unique constraints must be designed around active rows where the database supports it.
- Storage grows until the retention job purges eligible records.
- Restore and purge become explicit audited workflows.

## Rejected alternative

Hard delete from application commands was rejected because it removes the source needed to resolve stale events, duplicate delivery, and financial/audit references.
