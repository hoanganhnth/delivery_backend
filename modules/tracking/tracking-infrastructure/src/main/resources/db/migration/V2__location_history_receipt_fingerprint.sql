ALTER TABLE location_history_receipts
    ADD COLUMN IF NOT EXISTS payload_fingerprint VARCHAR(64);

ALTER TABLE location_history_receipts
    DROP CONSTRAINT IF EXISTS ck_location_history_receipt_outcome;

ALTER TABLE location_history_receipts
    ADD CONSTRAINT ck_location_history_receipt_outcome CHECK (
        outcome IN ('PENDING', 'PERSISTED', 'SAMPLED_OUT', 'NO_DELIVERY', 'OFFLINE_TOMBSTONE')
    );
