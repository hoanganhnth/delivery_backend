-- Canonicalize legacy order status values before relying on enum-based queries.
-- Rehearse against a backup first. The preflight aborts on unknown/null values.
BEGIN;

DO $$
DECLARE
    unknown_statuses text;
BEGIN
    SELECT string_agg(status_value, ', ' ORDER BY status_value)
    INTO unknown_statuses
    FROM (
        SELECT DISTINCT COALESCE(status, '<NULL>') AS status_value
        FROM orders
        WHERE status IS NULL OR status NOT IN (
            'PENDING', 'CONFIRMED', 'FINDING_SHIPPER', 'WAIT_SHIPPER_CONFIRM',
            'ASSIGNED', 'PICKED_UP', 'DELIVERING', 'DELIVERED', 'CANCELLED',
            'SHIPPER_NOT_FOUND', 'CONFIRMED_BY_RESTAURANT', 'READY',
            'ASSIGNED_TO_SHIPPER', 'IN_DELIVERY', 'IN_PROGRESS',
            'REJECTED_BY_RESTAURANT', 'PAYMENT_FAILED', 'PENDING_PAYMENT'
        )
    ) unknown;

    IF unknown_statuses IS NOT NULL THEN
        RAISE EXCEPTION 'Unknown order statuses: %', unknown_statuses;
    END IF;
END $$;

UPDATE orders
SET status = CASE status
    WHEN 'CONFIRMED_BY_RESTAURANT' THEN 'CONFIRMED'
    WHEN 'READY' THEN 'CONFIRMED'
    WHEN 'ASSIGNED_TO_SHIPPER' THEN 'ASSIGNED'
    WHEN 'IN_DELIVERY' THEN 'DELIVERING'
    WHEN 'IN_PROGRESS' THEN 'DELIVERING'
    WHEN 'REJECTED_BY_RESTAURANT' THEN 'CANCELLED'
    WHEN 'PAYMENT_FAILED' THEN 'CANCELLED'
    WHEN 'PENDING_PAYMENT' THEN 'PENDING'
    ELSE status
END
WHERE status IN (
    'CONFIRMED_BY_RESTAURANT', 'READY', 'ASSIGNED_TO_SHIPPER', 'IN_DELIVERY',
    'IN_PROGRESS', 'REJECTED_BY_RESTAURANT', 'PAYMENT_FAILED', 'PENDING_PAYMENT'
);

ALTER TABLE orders ALTER COLUMN status SET NOT NULL;

COMMIT;
