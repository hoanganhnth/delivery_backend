\set ON_ERROR_STOP on
SET synchronous_commit = off;

CREATE TABLE orders (
  id bigint PRIMARY KEY,
  user_id bigint NOT NULL,
  restaurant_id bigint NOT NULL,
  shipper_id bigint,
  creator_id bigint NOT NULL,
  status varchar(32) NOT NULL,
  created_at timestamp NOT NULL
);
INSERT INTO orders
SELECT n,
       1000 + (n % 2000),
       2000 + (n % 300),
       CASE WHEN n % 3 = 0 THEN 3000 + (n % 5000) END,
       4000 + (n % 300),
       (ARRAY['PENDING','CONFIRMED','DELIVERING','DELIVERED'])[1 + (n % 4)],
       timestamp '2026-01-01' + n * interval '1 second'
FROM generate_series(1, 150000) n;
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);
CREATE INDEX idx_orders_restaurant_created ON orders (restaurant_id, created_at);
CREATE INDEX idx_orders_restaurant_creator_created ON orders (restaurant_id, creator_id, created_at);
CREATE INDEX idx_orders_shipper_created ON orders (shipper_id, created_at);
CREATE INDEX idx_orders_status_created ON orders (status, created_at);
CREATE INDEX idx_orders_creator_created ON orders (creator_id, created_at);

CREATE TABLE deliveries (
  id bigint PRIMARY KEY,
  order_id bigint NOT NULL UNIQUE,
  shipper_id bigint,
  offered_shipper_id bigint,
  offer_expires_at timestamp,
  status varchar(32) NOT NULL,
  created_at timestamp NOT NULL
);
INSERT INTO deliveries
SELECT n, n,
       CASE WHEN n % 3 = 0 THEN 3000 + (n % 5000) END,
       CASE WHEN n % 20 = 0 THEN 3000 + (n % 5000) END,
       timestamp '2026-08-01' + n * interval '1 second',
       (ARRAY['PENDING','WAIT_SHIPPER_CONFIRM','ASSIGNED','DELIVERED'])[1 + (n % 4)],
       timestamp '2026-01-01' + n * interval '1 second'
FROM generate_series(1, 150000) n;
CREATE INDEX idx_deliveries_shipper_created ON deliveries (shipper_id, created_at DESC);
CREATE INDEX idx_deliveries_status_created ON deliveries (status, created_at DESC);
CREATE INDEX idx_deliveries_offered_shipper ON deliveries (offered_shipper_id, offer_expires_at);

CREATE TABLE transactions (
  id bigint PRIMARY KEY,
  entity_id bigint NOT NULL,
  entity_type varchar(20) NOT NULL,
  order_id bigint,
  status varchar(20) NOT NULL,
  reason varchar(30) NOT NULL,
  created_at timestamp NOT NULL
);
INSERT INTO transactions
SELECT n,
       3000 + (n % 5000),
       CASE WHEN n % 5 = 0 THEN 'RESTAURANT' ELSE 'SHIPPER' END,
       100000 + (n % 150000),
       CASE WHEN n % 40 = 0 THEN 'PENDING' ELSE 'COMPLETED' END,
       CASE WHEN n % 80 = 0 THEN 'WITHDRAW' ELSE 'DELIVERY_FEE' END,
       timestamp '2026-01-01' + n * interval '1 second'
FROM generate_series(1, 250000) n;
CREATE INDEX idx_transactions_entity ON transactions (entity_id, entity_type);
CREATE INDEX idx_transactions_order ON transactions (order_id);
CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_reason ON transactions (reason);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);
CREATE INDEX idx_transactions_entity_status ON transactions (entity_id, entity_type, status);

CREATE TABLE notifications (
  id bigint PRIMARY KEY,
  user_id bigint NOT NULL,
  is_read boolean NOT NULL,
  created_at timestamp NOT NULL
);
INSERT INTO notifications
SELECT n, 1000 + (n % 2000), n % 5 = 0,
       timestamp '2026-01-01' + n * interval '1 second'
FROM generate_series(1, 150000) n;
CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);
CREATE INDEX idx_notifications_user_read_created ON notifications (user_id, is_read, created_at);

CREATE TABLE shipper (
  id bigint PRIMARY KEY,
  is_online boolean NOT NULL,
  updated_at timestamp NOT NULL
);
INSERT INTO shipper
SELECT n, n % 100 = 0, timestamp '2026-01-01' + n * interval '1 second'
FROM generate_series(1, 100000) n;
CREATE INDEX idx_shipper_online ON shipper (is_online);

CREATE TABLE order_outbox_events (
  id bigint PRIMARY KEY,
  status varchar(16) NOT NULL,
  next_attempt_at timestamp NOT NULL,
  created_at timestamp NOT NULL
);
CREATE TABLE delivery_outbox_events (LIKE order_outbox_events INCLUDING ALL);
INSERT INTO order_outbox_events
SELECT n,
       CASE WHEN n % 10 = 0 THEN 'PENDING' ELSE 'SENT' END,
       timestamp '2026-07-30' + (n % 120) * interval '1 second',
       timestamp '2026-01-01' + n * interval '1 second'
FROM generate_series(1, 150000) n;
INSERT INTO delivery_outbox_events SELECT * FROM order_outbox_events;
CREATE INDEX idx_order_outbox_pending
  ON order_outbox_events (status, next_attempt_at, created_at);
CREATE INDEX idx_delivery_outbox_pending
  ON delivery_outbox_events (status, next_attempt_at, created_at);

ANALYZE;
