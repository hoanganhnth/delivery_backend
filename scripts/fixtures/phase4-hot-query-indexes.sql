\set ON_ERROR_STOP on
SET lock_timeout = '5s';
SET statement_timeout = '10min';

CREATE INDEX idx_orders_created_id ON orders (created_at DESC, id DESC);

DROP INDEX idx_order_outbox_pending;
CREATE INDEX idx_order_outbox_pending
  ON order_outbox_events (status, next_attempt_at, created_at, id);
DROP INDEX idx_delivery_outbox_pending;
CREATE INDEX idx_delivery_outbox_pending
  ON delivery_outbox_events (status, next_attempt_at, created_at, id);

DROP INDEX idx_transactions_status;
CREATE INDEX idx_transactions_status_reason_created
  ON transactions (status, reason, created_at DESC, id DESC);

ANALYZE;
