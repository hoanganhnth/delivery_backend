ALTER TABLE balances
    ADD COLUMN IF NOT EXISTS reserved_deposit_balance DECIMAL(12,2) DEFAULT 0.00 NOT NULL;

CREATE TABLE IF NOT EXISTS cod_capacity_holds (
    hold_id UUID PRIMARY KEY,
    offer_id UUID NOT NULL,
    order_id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    shipper_id BIGINT NOT NULL,
    matching_session_id UUID NOT NULL,
    wave_id UUID NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    event_id UUID NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    committed_at TIMESTAMP NULL,
    released_at TIMESTAMP NULL,
    consumed_at TIMESTAMP NULL,
    CONSTRAINT uk_cod_capacity_hold_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_cod_capacity_hold_status
        CHECK (status IN ('HELD', 'COMMITTED', 'RELEASED', 'EXPIRED', 'CONSUMED')),
    CONSTRAINT ck_cod_capacity_hold_amount CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_cod_capacity_holds_shipper_status
    ON cod_capacity_holds (shipper_id, status, expires_at);
CREATE INDEX IF NOT EXISTS idx_cod_capacity_holds_delivery
    ON cod_capacity_holds (delivery_id, status);
