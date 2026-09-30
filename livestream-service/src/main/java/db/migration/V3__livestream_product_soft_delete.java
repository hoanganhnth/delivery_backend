package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** Adds an auditable tombstone instead of physically deleting pinned products. */
public class V3__livestream_product_soft_delete extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE livestream_products ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP");
            statement.execute("ALTER TABLE livestream_products ADD COLUMN IF NOT EXISTS deleted_by_principal_id BIGINT");
            statement.execute("ALTER TABLE livestream_products ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(255)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_livestream_product_active ON livestream_products (livestream_id, is_pinned, deleted_at)");
        }
    }
}
