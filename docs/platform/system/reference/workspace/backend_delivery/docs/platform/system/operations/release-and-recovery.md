# Release, Rollback and Recovery

> Status: operator procedure derived from the current runbooks. Use this as a
> production gate only after the target platform has supplied the missing
> provider-specific controls in [deployment-foundation.md](./deployment-foundation.md).

## Pre-release record

Record, without secret values:

- Git revision and immutable image digest for every changed service.
- Config label/commit, secret version identifiers and migration IDs.
- Dependency compatibility statement: API/event schema, topic and database
  migration direction.
- Target environment, canary scope, rollback image/config reference and incident
  owner.
- Required smoke tests and approved latency/error/lag stop conditions.

## Safe service release

1. Verify image/artifact provenance, security scan and repository tests.
2. Apply compatible Flyway migrations by the owner-approved procedure. Preserve
   old code compatibility until the rollout completes.
3. Start a canary instance. It must mount required secrets, obtain selected
   config, register/discover dependencies as applicable and report readiness
   `UP`.
4. Route a small traffic share only after readiness. Drain existing HTTP and
   WebSocket work; give Kafka consumers time to stop cleanly/rebalance.
5. Observe Gateway errors, p95/p99, readiness, Kafka lag/DLT, database/Redis
   pool health and trace/correlation evidence. Run the Gateway COD smoke after
   the canary and after the rollout batch.
6. Promote only while the approved metrics remain within release gate. Record
   result and release metadata.

For Auth/JWKS changes, use the stricter Auth → cache/clock window → resource
services → Gateway sequence. The Compose runner is a rehearsal tool, not a
production controller.

## Stop and rollback

Stop promotion for a readiness regression, dependency loss, growing Kafka lag or
DLT, elevated error/latency, a failed auth/COD smoke, unexpected data migration
behavior or security exposure.

1. Remove the canary from traffic and preserve relevant logs/metrics/traces.
2. Roll back to the previous immutable image/config label/compatible secret
   version.
3. Confirm readiness and run a Gateway-only smoke.
4. Do not roll back a destructive schema blindly. Use a forward-compatible fix
   or the approved data recovery procedure.
5. If discovery is the incident, use only the private static-routes recovery
   overlay; do not publish direct upstream ports.

## COD smoke contract

A meaningful smoke checks more than `200 OK`:

1. Public/Gateway login/auth path works with current JWKS.
2. Customer creates a canonical COD order.
3. Restaurant confirms, Saga creates Delivery and Match makes an offer.
4. Shipper recovers current offer and accepts; lifecycle reaches `DELIVERED`.
5. Settlement stores exactly one receipt/four expected ledger entries and
   exact replay does not duplicate posting.
6. Tracking participant authorization and notification durable inbox behavior
   can be observed for the scenario where feasible.

Existing local script: `backend_delivery/scripts/verify-mvp-cod-flow.sh`. It is
not a substitute for staging or production test data/credentials/network policy.

## Data incident recovery

1. Assign incident owner/time, stop ingress/writers/relays/consumers.
2. Select a mutually consistent recovery point for Order, Delivery, Settlement,
   Saga and their outboxes/receipts.
3. Verify backup checksums; restore into isolated names first.
4. Run migrations, count/fingerprint reconciliation and service smoke on the
   isolated data.
5. Rebuild Elasticsearch; recreate Redis/current location from reconnecting
   publishers instead of importing stale cache/GEO/lease/offer state.
6. Compare Kafka offsets with restored receipt/outbox state, replay with a new
   controlled group or range only after proof; never reset blindly.
7. Switch/promote only with recorded security approval and measured RTO.

See the executable safeguards in
[backend data backup/restore runbook](../../../runbooks/data-backup-restore.md).

## DLT recovery

- Identify original topic, partition, offset, event ID, correlation ID and
  exception type.
- Classify transient vs validation/conflict error. Do not blindly replay poison
  or contradictory business payloads.
- Check owning consumer's durable receipt/fingerprint contract.
- Replay one record to its original key/partition and monitor effect/lag/DLT.
- Keep incident evidence. Exact replay should be harmless; a conflict should
  remain visible/fail closed.

## Source runbooks

- [Rolling/canary rollout and rollback](../../../runbooks/rollout-and-rollback.md)
- [Resilience and DLT operations](../../../runbooks/resilience-operations.md)
- [Data backup and restore](../../../runbooks/data-backup-restore.md)
- [JWKS/secret rotation](../../../runbooks/secrets-management.md)
