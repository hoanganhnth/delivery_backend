# Search entity-sync contract

`search-contracts` owns the wire envelope for the existing `entity-sync` Kafka
topic. It is deliberately limited to transport fields:

- `eventId`: stable event identity used by the projection checkpoint;
- `occurredAt`: producer ordering/version input;
- `entityType`: currently `RESTAURANT` or `DISH`;
- `action`: currently `CREATE`, `UPDATE`, or `DELETE`;
- `entityId`: source aggregate identity;
- `payload`: entity-specific projection data, absent for delete tombstones.

The module does not own retries, DLTs, projection indexing, cache policy, or
business authorization. Restaurant still owns the transactional outbox and
Search still owns deduplication, stale-event ordering, and Elasticsearch writes.

Compatibility rule: moving the Java type does not change the JSON field names,
topic, key, action values, payload shape, or producer type-header behavior.
The Search consumer ignores producer type headers and deserializes this
contract explicitly.
