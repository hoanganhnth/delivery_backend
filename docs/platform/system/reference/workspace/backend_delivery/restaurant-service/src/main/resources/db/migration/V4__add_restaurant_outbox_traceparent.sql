ALTER TABLE restaurant_outbox_events ADD COLUMN IF NOT EXISTS traceparent VARCHAR(55);
