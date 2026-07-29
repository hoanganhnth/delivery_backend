-- Operator recovery/reference script only. Flyway V2__saga_state_schema is the
-- runtime authority. Apply to saga_db only after rehearsing on a PostgreSQL copy.

BEGIN;

ALTER TABLE saga_instances
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (
        SELECT order_id
        FROM saga_instances
        GROUP BY order_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate saga_instances.order_id values must be resolved before migration';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_saga_instances_order_id'
    ) THEN
        ALTER TABLE saga_instances
            ADD CONSTRAINT uk_saga_instances_order_id UNIQUE (order_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_saga_order_id ON saga_instances (order_id);
CREATE INDEX IF NOT EXISTS idx_saga_status ON saga_instances (status);

COMMIT;
