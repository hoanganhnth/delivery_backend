# Shipper Location History

## Approved policy

Location history is audit/support data, not a product-facing feature. It is
retained for 90 days, rounded to five decimal coordinate precision, and sampled
at most once per ten seconds unless movement since an adjacent retained point is
at least 25 metres. Optional accuracy, speed, heading and source metadata are
retained when supplied.

Production PostgreSQL storage for `tracking_db` must use platform/KMS encrypted
volumes and TLS database connections; logical backups are encrypted by the data
backup runbook. Database credentials come from the mounted secret, not source or
Compose environment values. Raw coordinates were removed from application logs.

## Data flow and replay

The publisher hot path remains Redis GEO/detail plus Kafka and Redis Pub/Sub. It
does not call JPA or write PostgreSQL. Delivery's durable
`shipper.status-change` event maintains the active `deliveryId` projection in
Redis. New `shipper.location-updated` payloads retain all Match fields and add a
stable `eventId`, nullable `deliveryId`, optional telemetry and source.

The separate `tracking-location-history` Kafka consumer writes
`tracking_db.shipper_location_history` asynchronously. It checks both the prior
and next retained points, so an out-of-order record cannot bypass sampling.
`location_history_receipts.event_id` records PERSISTED, SAMPLED_OUT,
NO_DELIVERY, or OFFLINE_TOMBSTONE outcomes. Exact consumer restart/replay is a
no-op. A rolling legacy event receives a deterministic receipt ID but, because
it has no delivery association, is never guessed into a trip.

The daily cleanup deletes history and receipts older than the configured
90-day cutoff. The delivery/time and shipper/time indexes support investigation
and cleanup without adding an index to the realtime write path.

## Access and privacy

There is no Gateway/public/client history route. The only read endpoint is
`GET /internal/tracking/location-history/deliveries/{deliveryId}`. It requires
the internal secret, `ADMIN`, a positive support user identity, one delivery ID,
and a bounded result (default 100, maximum 500). Each successful read emits an
audit log containing support user, delivery and point count—but no coordinates.
There is no fleet-wide, shipper-wide, time-range export or arbitrary client
query.

## Validation

`LocationHistoryServiceIntegrationTest` covers precision, time/distance
sampling, out-of-order input, delivery query ordering, listener restart/replay,
legacy rollout, no-assignment rejection and retention cleanup. Publisher tests
prove enriched Kafka output uses only Redis assignment and contains no history
repository/database dependency. Controller tests reject wrong role or secret.
