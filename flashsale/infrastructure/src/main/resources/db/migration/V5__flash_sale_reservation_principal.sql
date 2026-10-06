ALTER TABLE flash_sale_reservations ADD COLUMN IF NOT EXISTS user_principal_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_flash_reservation_principal ON flash_sale_reservations (user_principal_id, order_id);
