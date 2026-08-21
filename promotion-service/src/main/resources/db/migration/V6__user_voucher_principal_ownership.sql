ALTER TABLE user_vouchers ADD COLUMN IF NOT EXISTS user_principal_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_user_vouchers_principal_status
    ON user_vouchers (user_principal_id, status);
