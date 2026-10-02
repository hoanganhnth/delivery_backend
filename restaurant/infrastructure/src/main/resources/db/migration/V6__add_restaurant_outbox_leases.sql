ALTER TABLE restaurant_outbox_events
    DROP CONSTRAINT IF EXISTS ck_restaurant_outbox_status;

ALTER TABLE restaurant_outbox_events
    ADD COLUMN IF NOT EXISTS lease_token UUID NULL;
ALTER TABLE restaurant_outbox_events
    ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP NULL;

ALTER TABLE restaurant_outbox_events
    ADD CONSTRAINT ck_restaurant_outbox_status
    CHECK (status IN ('PENDING', 'IN_FLIGHT', 'SENT', 'DEAD'));

CREATE INDEX IF NOT EXISTS idx_restaurant_outbox_claimable
    ON restaurant_outbox_events (status, next_attempt_at, lease_until, created_at, id);
