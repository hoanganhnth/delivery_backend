-- Replace single-column sort indexes with the smallest composites matching
-- bounded ledger/history paths. PostgreSQL can scan these indexes forward or
-- backward; DESC documents and optimizes the current newest-first contract.
DROP INDEX IF EXISTS idx_transactions_status;

CREATE INDEX IF NOT EXISTS idx_transactions_status_reason_created
    ON transactions (status, reason, created_at DESC, id DESC);
