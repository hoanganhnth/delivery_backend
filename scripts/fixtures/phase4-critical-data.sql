\set ON_ERROR_STOP on

\connect order_db
CREATE TABLE orders (
  id bigint PRIMARY KEY,
  user_id bigint NOT NULL,
  restaurant_id bigint NOT NULL,
  shipper_id bigint,
  status varchar(32) NOT NULL,
  total_price numeric(12,2) NOT NULL,
  created_at timestamp NOT NULL
);
CREATE TABLE outbox_events (
  event_id uuid PRIMARY KEY,
  event_type varchar(80) NOT NULL,
  aggregate_id bigint NOT NULL,
  status varchar(20) NOT NULL,
  created_at timestamp NOT NULL
);
INSERT INTO orders VALUES
  (101, 1001, 2001, 3001, 'DELIVERED', 185000.00, '2026-07-30 01:00:00'),
  (102, 1002, 2001, NULL, 'PENDING', 92000.00, '2026-07-30 01:01:00');
INSERT INTO outbox_events VALUES
  ('00000000-0000-0000-0000-000000000101', 'ORDER_CREATED', 101, 'PUBLISHED', '2026-07-30 01:00:00'),
  ('00000000-0000-0000-0000-000000000102', 'ORDER_CREATED', 102, 'PENDING', '2026-07-30 01:01:00');

\connect delivery_db
CREATE TABLE deliveries (
  id bigint PRIMARY KEY,
  order_id bigint NOT NULL UNIQUE,
  shipper_id bigint,
  status varchar(32) NOT NULL,
  total_price numeric(12,2) NOT NULL,
  created_at timestamp NOT NULL
);
CREATE TABLE outbox_events (
  event_id uuid PRIMARY KEY,
  event_type varchar(80) NOT NULL,
  aggregate_id bigint NOT NULL,
  status varchar(20) NOT NULL,
  created_at timestamp NOT NULL
);
INSERT INTO deliveries VALUES
  (201, 101, 3001, 'DELIVERED', 185000.00, '2026-07-30 01:00:10'),
  (202, 102, NULL, 'PENDING', 92000.00, '2026-07-30 01:01:10');
INSERT INTO outbox_events VALUES
  ('00000000-0000-0000-0000-000000000201', 'DELIVERY_COMPLETED', 201, 'PUBLISHED', '2026-07-30 01:20:00'),
  ('00000000-0000-0000-0000-000000000202', 'DELIVERY_CREATED', 202, 'PENDING', '2026-07-30 01:01:10');

\connect settlement_db
CREATE TABLE settlement_receipts (
  event_id uuid PRIMARY KEY,
  order_id bigint NOT NULL UNIQUE,
  delivery_id bigint NOT NULL,
  payload_fingerprint varchar(64) NOT NULL,
  created_at timestamp NOT NULL
);
CREATE TABLE transactions (
  id bigint PRIMARY KEY,
  entity_id bigint NOT NULL,
  entity_type varchar(20) NOT NULL,
  order_id bigint NOT NULL,
  direction varchar(10) NOT NULL,
  reason varchar(30) NOT NULL,
  amount numeric(12,2) NOT NULL,
  status varchar(20) NOT NULL,
  wallet_type varchar(20) NOT NULL,
  created_at timestamp NOT NULL,
  UNIQUE(order_id, entity_id, entity_type, reason, wallet_type, direction)
);
INSERT INTO settlement_receipts VALUES
  ('00000000-0000-0000-0000-000000000201', 101, 201,
   'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
   '2026-07-30 01:20:01');
INSERT INTO transactions VALUES
  (301, 3001, 'SHIPPER', 101, 'CREDIT', 'DELIVERY_FEE', 25500.00, 'COMPLETED', 'EARNINGS', '2026-07-30 01:20:01'),
  (302, 3001, 'SHIPPER', 101, 'DEBIT', 'COD_SETTLEMENT', 185000.00, 'COMPLETED', 'DEPOSIT', '2026-07-30 01:20:01');

\connect notification_service_db
CREATE TABLE notifications (
  id bigint PRIMARY KEY,
  user_id bigint NOT NULL,
  type varchar(80) NOT NULL,
  status varchar(20) NOT NULL,
  is_read boolean NOT NULL,
  related_entity_id bigint,
  related_entity_type varchar(30),
  deduplication_key varchar(200) UNIQUE,
  created_at timestamp NOT NULL
);
INSERT INTO notifications VALUES
  (401, 1001, 'ORDER_DELIVERED', 'SENT', false, 101, 'ORDER',
   'delivery-completed:201:1001', '2026-07-30 01:20:02');
