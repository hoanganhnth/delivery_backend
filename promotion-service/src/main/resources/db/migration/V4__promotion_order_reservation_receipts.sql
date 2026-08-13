CREATE TABLE promotion_order_reservation_receipts (
    event_id UUID PRIMARY KEY,
    source_topic VARCHAR(255) NOT NULL,
    action VARCHAR(16) NOT NULL,
    order_id BIGINT NOT NULL,
    reservation_id UUID,
    payload_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_promotion_order_reservation_receipt_action
        CHECK (action IN ('COMMIT', 'RELEASE'))
);

CREATE INDEX idx_promotion_order_reservation_receipt_order
    ON promotion_order_reservation_receipts (order_id, created_at);
