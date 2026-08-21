ALTER TABLE voucher_reservations ADD COLUMN IF NOT EXISTS user_principal_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_voucher_reservation_principal ON voucher_reservations (user_principal_id, order_id);
