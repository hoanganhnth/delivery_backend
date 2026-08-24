-- creator_id remains the legacy User-profile owner reference during migration.
ALTER TABLE restaurant ADD COLUMN IF NOT EXISTS owner_principal_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_restaurant_owner_principal
    ON restaurant (owner_principal_id, id);
