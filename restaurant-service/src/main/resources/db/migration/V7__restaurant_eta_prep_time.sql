ALTER TABLE restaurant
    ADD COLUMN IF NOT EXISTS default_prep_time_minutes INTEGER NOT NULL DEFAULT 30;

UPDATE restaurant
SET default_prep_time_minutes = 30
WHERE default_prep_time_minutes IS NULL
   OR default_prep_time_minutes < 1
   OR default_prep_time_minutes > 240;

ALTER TABLE restaurant
    ADD CONSTRAINT ck_restaurant_default_prep_time
    CHECK (default_prep_time_minutes BETWEEN 1 AND 240);
