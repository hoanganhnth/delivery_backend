CREATE TABLE voucher_reservations (
    reservation_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    restaurant_id BIGINT NOT NULL,
    subtotal DECIMAL(38,2) NOT NULL,
    shipping_fee DECIMAL(38,2) NOT NULL,
    discount_amount DECIMAL(38,2) NOT NULL,
    state VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_voucher_reservation_order UNIQUE (order_id),
    CONSTRAINT fk_voucher_reservation_voucher FOREIGN KEY (voucher_id) REFERENCES vouchers (id),
    CONSTRAINT ck_voucher_reservation_state
        CHECK (state IN ('RESERVED', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_voucher_reservation_discount CHECK (discount_amount >= 0)
);

CREATE INDEX idx_voucher_reservation_expiry
    ON voucher_reservations (state, expires_at);
CREATE INDEX idx_voucher_reservation_voucher
    ON voucher_reservations (voucher_id, state);
