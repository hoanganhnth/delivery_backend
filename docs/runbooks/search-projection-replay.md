# Search projection replay

## Purpose

Rebuild the `restaurant` and `dish` Elasticsearch projections from `entity-sync`
without allowing an older upsert to recreate an entity that has already been
soft-deleted.

## Preconditions

- Confirm the producer emits an immutable `eventId`, `occurredAt`, and, for
  migrated producers, a positive `aggregateVersion`.
- Preserve the current consumer-group offsets before any change.
- Ensure `app.elasticsearch.enabled=true` only for the replay worker and that
  it points at the intended non-production or approved Elasticsearch cluster.
- Do not enable the normal consumer write path for the same replay group.

## Procedure

1. Create a new, uniquely named Kafka consumer group. Never reset the live
   `search-service` group to run a rebuild.
2. Set that group to the approved starting offsets for `entity-sync` (normally
   earliest for a complete rebuild) and start one Search replay worker.
3. Watch `delivery.search.tombstones.applied`,
   `delivery.search.stale_events.rejected`, and replay-failure metrics. Stop
   the worker if replay failures increase or Elasticsearch becomes unhealthy.
4. Compare document counts and a sampled set of active IDs with the source
   records. For deleted source entities, verify Elasticsearch returns `404`.
5. Stop the replay worker after all intended partitions have committed their
   terminal offsets. Retain its group offsets and the comparison evidence for
   audit/recovery.

## Safety properties

- Checkpoints reject stale events and detect contradictory reuse of an event
  identity.
- Elasticsearch receives `external_gte` versions. A migrated event uses the
  producer aggregate version; legacy events use an occurred-at-derived version
  only for compatibility.
- A later delete tombstone therefore fences an already-delayed older upsert;
  replaying the old record cannot resurrect its document.

## Rollback

Stop the replay worker, keep its offsets unchanged, and leave the live Search
consumer group running. Restore the previous Elasticsearch index only through
the approved snapshot/alias procedure; do not delete the checkpoint index as a
request-time recovery action.
