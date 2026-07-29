package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/** Adds durable retry state for Auth -> User block/unblock projection sync. */
public class V2__auth_user_status_sync extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String table = findTable(connection, "auth_account");
        if (table == null) {
            return;
        }

        addColumnIfMissing(connection, table, "user_status_sync_pending",
                "ALTER TABLE auth_account ADD COLUMN user_status_sync_pending BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing(connection, table, "user_status_sync_version",
                "ALTER TABLE auth_account ADD COLUMN user_status_sync_version BIGINT NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, table, "user_status_sync_admin_id",
                "ALTER TABLE auth_account ADD COLUMN user_status_sync_admin_id BIGINT");
        addColumnIfMissing(connection, table, "user_status_sync_block_reason",
                "ALTER TABLE auth_account ADD COLUMN user_status_sync_block_reason TEXT");
        addColumnIfMissing(connection, table, "user_status_sync_attempts",
                "ALTER TABLE auth_account ADD COLUMN user_status_sync_attempts INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing(connection, table, "user_status_sync_last_error",
                "ALTER TABLE auth_account ADD COLUMN user_status_sync_last_error TEXT");
        addColumnIfMissing(connection, table, "user_status_sync_updated_at",
                "ALTER TABLE auth_account ADD COLUMN user_status_sync_updated_at TIMESTAMP");

        execute(connection, """
                CREATE INDEX IF NOT EXISTS idx_auth_account_user_status_sync_pending
                ON auth_account (user_status_sync_pending, user_status_sync_updated_at, id)
                """);
    }

    private void addColumnIfMissing(Connection connection, String tableName, String columnName, String sql)
            throws SQLException {
        if (!hasColumn(connection, tableName, columnName)) {
            execute(connection, sql);
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String expectedColumn)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "%")) {
            while (columns.next()) {
                if (expectedColumn.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String findTable(Connection connection, String expectedName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                if (expectedName.equalsIgnoreCase(tableName)) {
                    return tableName.toLowerCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
