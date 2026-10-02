ALTER TABLE restaurant
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(16) DEFAULT 'ACTIVE';
UPDATE restaurant SET lifecycle_status = 'ACTIVE' WHERE lifecycle_status IS NULL;
ALTER TABLE restaurant ALTER COLUMN lifecycle_status SET DEFAULT 'ACTIVE';
ALTER TABLE restaurant ALTER COLUMN lifecycle_status SET NOT NULL;

ALTER TABLE restaurant
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
UPDATE restaurant SET version = 0 WHERE version IS NULL;
ALTER TABLE restaurant ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE restaurant ALTER COLUMN version SET NOT NULL;

ALTER TABLE restaurant
    ADD COLUMN IF NOT EXISTS time_zone VARCHAR(64) DEFAULT 'Asia/Ho_Chi_Minh';
UPDATE restaurant SET time_zone = 'Asia/Ho_Chi_Minh' WHERE time_zone IS NULL OR TRIM(time_zone) = '';
ALTER TABLE restaurant ALTER COLUMN time_zone SET DEFAULT 'Asia/Ho_Chi_Minh';
ALTER TABLE restaurant ALTER COLUMN time_zone SET NOT NULL;

ALTER TABLE menu_item
    ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
UPDATE menu_item SET version = 0 WHERE version IS NULL;
ALTER TABLE menu_item ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE menu_item ALTER COLUMN version SET NOT NULL;

ALTER TABLE restaurant
    ADD CONSTRAINT ck_restaurant_lifecycle_status
    CHECK (lifecycle_status IN ('ACTIVE', 'PAUSED', 'ARCHIVED'));
ALTER TABLE restaurant
    ADD CONSTRAINT ck_restaurant_version_non_negative CHECK (version >= 0);
ALTER TABLE restaurant
    ADD CONSTRAINT ck_restaurant_time_zone_not_blank CHECK (TRIM(time_zone) <> '');
ALTER TABLE menu_item
    ADD CONSTRAINT ck_menu_item_version_non_negative CHECK (version >= 0);

CREATE INDEX IF NOT EXISTS idx_restaurant_lifecycle_status
    ON restaurant (lifecycle_status, id);
