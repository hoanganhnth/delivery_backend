ALTER TABLE refund_cases ADD COLUMN IF NOT EXISTS user_principal_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_refund_cases_user_principal_created
    ON refund_cases (user_principal_id, created_at DESC);
