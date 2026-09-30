ALTER TABLE analytics_events
    ADD COLUMN IF NOT EXISTS aggregate_version BIGINT;

ALTER TABLE analytics_events
    ADD CONSTRAINT IF NOT EXISTS ck_analytics_aggregate_version_positive
    CHECK (aggregate_version IS NULL OR aggregate_version > 0);

CREATE INDEX IF NOT EXISTS idx_analytics_event_aggregate_version
    ON analytics_events (event_type, aggregate_version);
