CREATE TABLE promotion_outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    last_error VARCHAR(2000),
    CONSTRAINT ck_promotion_outbox_status CHECK (status IN ('PENDING', 'SENT', 'DEAD'))
);

CREATE INDEX idx_promotion_outbox_pending
    ON promotion_outbox_events (status, next_attempt_at, created_at, event_id);
CREATE INDEX idx_promotion_outbox_aggregate
    ON promotion_outbox_events (aggregate_type, aggregate_id, created_at);
