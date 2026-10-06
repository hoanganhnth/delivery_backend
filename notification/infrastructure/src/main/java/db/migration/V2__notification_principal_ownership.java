package db.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** user_id remains the legacy profile recipient reference until event producers carry principal IDs. */
public class V2__notification_principal_ownership extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE notifications ADD COLUMN IF NOT EXISTS user_principal_id BIGINT");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_notifications_user_principal_created "
                    + "ON notifications (user_principal_id, created_at)");
        }
    }
}
