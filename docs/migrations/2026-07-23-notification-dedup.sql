-- Apply to notification_service_db after rehearsing on a PostgreSQL copy.

BEGIN;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS deduplication_key varchar(200);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_deduplication_key
    ON notifications (deduplication_key)
    WHERE deduplication_key IS NOT NULL;

COMMIT;
