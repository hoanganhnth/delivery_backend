-- Recovery step between Delivery Flyway V8 and V9.
-- Take a backup and stop delivery-service writers first.
-- Historic rows cannot recover the original Saga command UUID, so assign a unique
-- reconciliation marker. Any delayed historic command will then fail closed rather
-- than being accepted as an exact replay.
BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

UPDATE deliveries
SET create_event_id = gen_random_uuid()
WHERE create_event_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM deliveries WHERE create_event_id IS NULL) THEN
        RAISE EXCEPTION 'Delivery create-event reconciliation is incomplete';
    END IF;
    IF EXISTS (
        SELECT create_event_id
        FROM deliveries
        GROUP BY create_event_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate delivery create-event identities require manual repair';
    END IF;
END $$;

COMMIT;
