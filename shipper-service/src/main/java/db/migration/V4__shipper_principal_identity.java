package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Connection;
import java.sql.Statement;

/** Additive principal lookup. user_id remains the User-profile domain reference. */
public class V4__shipper_principal_identity extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE shipper ADD COLUMN IF NOT EXISTS principal_id BIGINT");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_shipper_principal_id ON shipper (principal_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_shipper_user_principal ON shipper (user_id, principal_id)");
        }
    }
}
