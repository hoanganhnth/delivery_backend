ALTER TABLE flash_sale_items ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE flash_sale_items ADD COLUMN IF NOT EXISTS deleted_by_principal_id BIGINT;
ALTER TABLE flash_sale_items ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_flash_sale_items_active_deleted
    ON flash_sale_items (status, deleted_at, campaign_id);
