CREATE TABLE IF NOT EXISTS refund_cases (
    refund_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    previous_order_status VARCHAR(32) NOT NULL,
    current_order_status VARCHAR(32) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    refund_trigger VARCHAR(32) NOT NULL,
    component VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    subtotal_amount DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,
    shipping_fee DECIMAL(12,2) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    captured_amount DECIMAL(12,2) NOT NULL,
    refund_amount DECIMAL(12,2) NOT NULL,
    actor_source VARCHAR(32) NOT NULL,
    actor_id BIGINT,
    reason TEXT NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(128),
    last_error VARCHAR(2000),
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    CONSTRAINT uk_refund_cases_event UNIQUE (event_id),
    CONSTRAINT uk_refund_cases_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_refund_cases_order_trigger_component
        UNIQUE (order_id, refund_trigger, component)
);

CREATE INDEX IF NOT EXISTS idx_refund_cases_order
    ON refund_cases (order_id, created_at);

CREATE INDEX IF NOT EXISTS idx_refund_cases_status_created
    ON refund_cases (status, created_at DESC, refund_id DESC);

CREATE TABLE IF NOT EXISTS refund_outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    last_error VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_refund_outbox_pending
    ON refund_outbox_events (status, next_attempt_at, created_at, event_id);

CREATE INDEX IF NOT EXISTS idx_refund_outbox_aggregate
    ON refund_outbox_events (aggregate_type, aggregate_id, created_at);
