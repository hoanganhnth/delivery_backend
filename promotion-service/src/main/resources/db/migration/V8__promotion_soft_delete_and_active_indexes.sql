ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS deleted_by_principal_id BIGINT;
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_vouchers_active_deleted_scope
    ON vouchers (active, deleted_at, approval_status, end_time);
