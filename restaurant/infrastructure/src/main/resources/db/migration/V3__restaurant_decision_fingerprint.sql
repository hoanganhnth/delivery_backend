ALTER TABLE restaurant_order_decisions
    ADD COLUMN IF NOT EXISTS payload_fingerprint VARCHAR(64);
